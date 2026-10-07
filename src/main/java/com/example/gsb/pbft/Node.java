package com.example.gsb.pbft;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * PBFT 副本节点。三阶段状态机：
 *
 * <ol>
 *   <li>PRE_PREPARE：仅接受当前视图主节点发出的预准备，随后广播 PREPARE；</li>
 *   <li>PREPARE：集齐 2f 个来自不同节点且摘要匹配的准备消息后广播 COMMIT；</li>
 *   <li>COMMIT：集齐 2f+1 个提交消息后执行请求（每个摘要只执行一次）。</li>
 * </ol>
 *
 * <p>主节点失效时通过 {@link #onPrimaryTimeout()} 触发视图更换，
 * 新主节点收集 2f+1 个 VIEW_CHANGE 后广播 NEW_VIEW，
 * 其中携带未完成请求的新预准备，使请求在新视图中继续达成一致。
 */
public class Node {

    private final int id;
    private final Cluster cluster;
    private int view;
    private boolean crashed;
    private long nextSeq = 1L;
    private final Deque<String> pendingRequests = new ArrayDeque<>();
    private final Map<String, Round> rounds = new HashMap<>();
    private final Map<Integer, Map<Integer, Message>> viewChangeLog = new HashMap<>();
    private final Set<Integer> newViewSent = new LinkedHashSet<>();
    private final Set<String> executedDigests = new LinkedHashSet<>();
    private final List<String> executedRequests = new ArrayList<>();

    public Node(int id, Cluster cluster) {
        this.id = id;
        this.cluster = cluster;
    }

    public int id() {
        return id;
    }

    public int view() {
        return view;
    }

    public boolean isCrashed() {
        return crashed;
    }

    public List<String> executedRequests() {
        return List.copyOf(executedRequests);
    }

    public boolean hasExecuted(String request) {
        return executedDigests.contains(Digests.sha256(request));
    }

    public void crash() {
        crashed = true;
    }

    void enqueue(String request) {
        pendingRequests.add(request);
    }

    /** 若本节点是当前视图主节点，则为排队中的客户端请求分配序号并广播预准备。 */
    public void maybePropose() {
        if (crashed || cluster.primaryOf(view) != id) {
            return;
        }
        while (!pendingRequests.isEmpty()) {
            String request = pendingRequests.poll();
            emit(Message.prePrepare(view, nextSeq++, Digests.sha256(request), id, request));
        }
    }

    /** 主节点超时：进入下一视图并广播 VIEW_CHANGE（携带本节点已知的未完成请求）。 */
    public void onPrimaryTimeout() {
        if (crashed) {
            return;
        }
        view++;
        emit(Message.viewChange(view, id, preparedSetPayload()));
    }

    public void receive(Message m) {
        if (crashed) {
            return;
        }
        if (!Signer.verify(m.senderId(), m.content(), m.signature())) {
            cluster.stats().invalidSignatureDrop();
            return;
        }
        switch (m.type()) {
            case PRE_PREPARE -> onPrePrepare(m);
            case PREPARE -> onPrepare(m);
            case COMMIT -> onCommit(m);
            case VIEW_CHANGE -> onViewChange(m);
            case NEW_VIEW -> onNewView(m);
        }
    }

    /** 发送钩子：拜占庭节点可重写以伪造、延迟或复制消息。 */
    protected void emit(Message m) {
        cluster.broadcast(m);
    }

    private void onPrePrepare(Message m) {
        if (!currentView(m)) {
            return;
        }
        if (m.senderId() != cluster.primaryOf(m.view())) {
            cluster.stats().conflictDrop();
            return;
        }
        Round round = round(m.view(), m.seq());
        if (round.prePrepare != null) {
            cluster.stats().dedupDrop();
            return;
        }
        round.prePrepare = m;
        nextSeq = Math.max(nextSeq, m.seq() + 1);
        emit(Message.prepare(m.view(), m.seq(), m.digest(), id));
        maybeCommit(round);
    }

    private void onPrepare(Message m) {
        if (!currentView(m)) {
            return;
        }
        Round round = round(m.view(), m.seq());
        if (round.prePrepare != null && !round.prePrepare.digest().equals(m.digest())) {
            cluster.stats().conflictDrop();
            return;
        }
        if (round.prepares.containsKey(m.senderId())) {
            cluster.stats().dedupDrop();
            return;
        }
        round.prepares.put(m.senderId(), m);
        maybeCommit(round);
    }

    private void maybeCommit(Round round) {
        if (round.commitSent || round.prePrepare == null) {
            return;
        }
        if (round.prepares.size() >= 2 * cluster.f()) {
            round.commitSent = true;
            emit(Message.commit(round.prePrepare.view(), round.prePrepare.seq(),
                    round.prePrepare.digest(), id));
        }
    }

    private void onCommit(Message m) {
        if (!currentView(m)) {
            return;
        }
        Round round = round(m.view(), m.seq());
        if (round.prePrepare != null && !round.prePrepare.digest().equals(m.digest())) {
            cluster.stats().conflictDrop();
            return;
        }
        if (round.commits.containsKey(m.senderId())) {
            cluster.stats().dedupDrop();
            return;
        }
        round.commits.put(m.senderId(), m);
        tryExecute(round);
    }

    private void tryExecute(Round round) {
        if (round.executed || round.prePrepare == null) {
            return;
        }
        String digest = round.prePrepare.digest();
        long matching = round.commits.values().stream()
                .filter(c -> c.digest().equals(digest))
                .count();
        if (matching >= 2L * cluster.f() + 1 && executedDigests.add(digest)) {
            round.executed = true;
            String request = round.prePrepare.payload();
            executedRequests.add(request);
            pendingRequests.remove(request);
            cluster.onExecuted(digest, id);
        }
    }

    private void onViewChange(Message m) {
        if (m.view() != view) {
            return;
        }
        Map<Integer, Message> log = viewChangeLog.computeIfAbsent(m.view(), v -> new HashMap<>());
        if (log.containsKey(m.senderId())) {
            cluster.stats().dedupDrop();
            return;
        }
        log.put(m.senderId(), m);
        if (cluster.primaryOf(m.view()) == id
                && log.size() >= 2 * cluster.f() + 1
                && newViewSent.add(m.view())) {
            broadcastNewView(m.view(), log.values());
        }
    }

    private void broadcastNewView(int newView, Collection<Message> viewChanges) {
        Map<Long, Message> repropose = new TreeMap<>();
        for (Round round : rounds.values()) {
            if (round.prePrepare != null && !executedDigests.contains(round.prePrepare.digest())) {
                repropose.putIfAbsent(round.prePrepare.seq(), round.prePrepare);
            }
        }
        for (Message vc : viewChanges) {
            for (String entry : vc.payload().split(";")) {
                if (entry.isBlank()) {
                    continue;
                }
                String[] parts = entry.split(":", 3);
                long seq = Long.parseLong(parts[0]);
                String digest = parts[1];
                String request = parts[2];
                if (!executedDigests.contains(digest)) {
                    repropose.putIfAbsent(seq,
                            Message.prePrepare(newView, seq, digest, id, request));
                }
            }
        }
        List<Message> prePrepares = new ArrayList<>();
        for (Map.Entry<Long, Message> e : repropose.entrySet()) {
            Message old = e.getValue();
            prePrepares.add(Message.prePrepare(newView, e.getKey(), old.digest(), id, old.payload()));
            nextSeq = Math.max(nextSeq, e.getKey() + 1);
        }
        while (!pendingRequests.isEmpty()) {
            String request = pendingRequests.poll();
            prePrepares.add(Message.prePrepare(newView, nextSeq++, Digests.sha256(request), id, request));
        }
        emit(Message.newView(newView, id, List.copyOf(viewChanges), prePrepares));
    }

    private void onNewView(Message m) {
        if (m.view() != view) {
            return;
        }
        if (m.senderId() != cluster.primaryOf(m.view())
                || m.viewChanges().size() < 2 * cluster.f() + 1) {
            cluster.stats().conflictDrop();
            return;
        }
        for (Message pp : m.prePrepares()) {
            if (!Signer.verify(pp.senderId(), pp.content(), pp.signature())) {
                cluster.stats().invalidSignatureDrop();
                return;
            }
        }
        cluster.recordViewChange(m.view());
        for (Message pp : m.prePrepares()) {
            if (!executedDigests.contains(pp.digest())) {
                onPrePrepare(pp);
            }
        }
    }

    private boolean currentView(Message m) {
        if (m.view() == view) {
            return true;
        }
        if (m.view() < view) {
            cluster.stats().staleDrop();
        }
        return false;
    }

    private Round round(int roundView, long seq) {
        return rounds.computeIfAbsent(roundView + ":" + seq, k -> new Round());
    }

    private String preparedSetPayload() {
        StringBuilder sb = new StringBuilder();
        for (Round round : rounds.values()) {
            if (round.prePrepare != null && !executedDigests.contains(round.prePrepare.digest())) {
                if (sb.length() > 0) {
                    sb.append(';');
                }
                sb.append(round.prePrepare.seq()).append(':')
                        .append(round.prePrepare.digest()).append(':')
                        .append(round.prePrepare.payload());
            }
        }
        return sb.toString();
    }

    /** 单个 (view, seq) 的共识轮次状态。 */
    private static final class Round {
        private Message prePrepare;
        private final Map<Integer, Message> prepares = new HashMap<>();
        private final Map<Integer, Message> commits = new HashMap<>();
        private boolean commitSent;
        private boolean executed;
    }
}
