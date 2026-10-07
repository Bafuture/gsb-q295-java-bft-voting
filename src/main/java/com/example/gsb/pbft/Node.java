package com.example.gsb.pbft;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * PBFT 节点（简化版，内存对象模拟）。
 *
 * <p>三阶段协议：</p>
 * <ol>
 *   <li>预准备：当前视图主节点为请求编号并广播 PRE_PREPARE；</li>
 *   <li>准备：节点收到与预准备匹配的、来自 {@code 2f} 个不同节点的 PREPARE 后进入 prepared；</li>
 *   <li>提交：prepared 后广播 COMMIT，收到 {@code 2f+1} 个不同节点对同一值的 COMMIT 后达成一致。</li>
 * </ol>
 *
 * <p>视图更换：任何节点可发起 VIEW_CHANGE（模拟主节点超时）；
 * 某节点收集到 {@code 2f+1} 个指向同一新视图的 VIEW_CHANGE 后切换视图，
 * 新主节点广播 NEW_VIEW 并为所有未完成的请求重新发起预准备。</p>
 */
public final class Node {

    /** 单个请求在某个节点上的协议状态。 */
    private static final class RequestState {
        String prePrepareValue;
        final Map<Integer, String> prepares = new HashMap<>();
        final Map<Integer, String> commits = new HashMap<>();
        boolean prepared;
        String committedValue;

        long countMatching(Map<Integer, String> votes, String value) {
            return votes.values().stream().filter(value::equals).count();
        }
    }

    private final int id;
    private final String secretKey;
    private final int n;
    private final int f;
    private final Network network;
    private final Stats stats = new Stats();

    private int view;
    private final Map<Long, String> pendingRequests = new LinkedHashMap<>();
    private final Map<Long, RequestState> states = new HashMap<>();
    private final Map<Integer, Set<Integer>> viewChangeVotes = new HashMap<>();

    public Node(int id, int n, int f, Network network) {
        this.id = id;
        this.n = n;
        this.f = f;
        this.network = network;
        this.secretKey = "secret-key-of-node-" + id;
    }

    public int id() { return id; }
    public String secretKey() { return secretKey; }
    public int view() { return view; }
    public Stats stats() { return stats; }

    public int primaryOf(int view) { return view % n; }
    public boolean isPrimary() { return primaryOf(view) == id; }

    /** 准备阶段法定人数：2f 个来自不同节点的 PREPARE。 */
    public int prepareQuorum() { return 2 * f; }

    /** 提交阶段法定人数：2f+1 个来自不同节点的 COMMIT。 */
    public int commitQuorum() { return 2 * f + 1; }

    /** 客户端提交请求（客户端在模拟中是可信的，直接登记）。 */
    public void receiveClientRequest(long sequence, String value) {
        pendingRequests.putIfAbsent(sequence, value);
        if (isPrimary() && !isCommitted(sequence)) {
            broadcast(MessageType.PRE_PREPARE, view, sequence, value);
        }
    }

    /** 发起视图更换（模拟主节点超时后的指控）。 */
    public void triggerViewChange() {
        broadcast(MessageType.VIEW_CHANGE, view + 1, -1, null);
    }

    public void handle(Message message) {
        if (!message.hasValidSignature(network.keyStore())) {
            stats.incSignatureDropped();
            return;
        }
        switch (message.type()) {
            case PRE_PREPARE -> onPrePrepare(message);
            case PREPARE -> onPrepare(message);
            case COMMIT -> onCommit(message);
            case VIEW_CHANGE -> onViewChange(message);
            case NEW_VIEW -> onNewView(message);
            case REQUEST -> { /* 客户端请求走 receiveClientRequest，不经过网络消息 */ }
        }
    }

    private void onPrePrepare(Message m) {
        if (m.view() != view || m.senderId() != primaryOf(view)) {
            return;
        }
        RequestState state = stateOf(m.sequence());
        if (state.prePrepareValue != null || state.committedValue != null) {
            stats.incDedupDropped();
            return;
        }
        state.prePrepareValue = m.value();
        stats.incPrePrepare();
        broadcast(MessageType.PREPARE, view, m.sequence(), m.value());
        // PREPARE 可能先于预准备到达，此时补判 prepared 条件
        tryPrepare(m.sequence(), state);
    }

    private void onPrepare(Message m) {
        if (m.view() != view) {
            return;
        }
        RequestState state = stateOf(m.sequence());
        if (state.prepares.containsKey(m.senderId())) {
            stats.incDedupDropped();
            return;
        }
        state.prepares.put(m.senderId(), m.value());
        stats.incPrepare();
        tryPrepare(m.sequence(), state);
    }

    private void tryPrepare(long sequence, RequestState state) {
        if (!state.prepared
                && state.prePrepareValue != null
                && state.countMatching(state.prepares, state.prePrepareValue) >= prepareQuorum()) {
            state.prepared = true;
            broadcast(MessageType.COMMIT, view, sequence, state.prePrepareValue);
            // 先到的 COMMIT 已登记，进入 prepared 后补判一次提交法定人数
            tryCommit(sequence, state, state.prePrepareValue);
        }
    }

    private void onCommit(Message m) {
        if (m.view() != view) {
            return;
        }
        RequestState state = stateOf(m.sequence());
        if (state.commits.containsKey(m.senderId())) {
            stats.incDedupDropped();
            return;
        }
        state.commits.put(m.senderId(), m.value());
        stats.incCommit();
        // 标准 PBFT 安全约束：只有自身已 prepared 的节点才能凭 COMMIT 法定人数提交，
        // 防止节点跳过预准备/准备阶段被 COMMIT 消息直接“带出结果”。
        tryCommit(m.sequence(), state, m.value());
    }

    private void tryCommit(long sequence, RequestState state, String value) {
        if (state.prepared && state.committedValue == null
                && state.countMatching(state.commits, value) >= commitQuorum()) {
            state.committedValue = value;
            pendingRequests.remove(sequence);
            stats.incCommitted();
        }
    }

    private void onViewChange(Message m) {
        int targetView = m.view();
        if (targetView <= view) {
            return;
        }
        Set<Integer> voters = viewChangeVotes.computeIfAbsent(targetView, k -> new HashSet<>());
        if (!voters.add(m.senderId())) {
            stats.incDedupDropped();
            return;
        }
        stats.incViewChangeMsg();
        if (voters.size() >= commitQuorum()) {
            enterView(targetView);
        }
    }

    private void onNewView(Message m) {
        if (m.view() <= view || m.senderId() != primaryOf(m.view())) {
            return;
        }
        stats.incNewViewMsg();
        enterView(m.view());
    }

    private void enterView(int newView) {
        if (newView <= view) {
            return;
        }
        view = newView;
        stats.incViewChanges();
        // 简化版状态迁移：未完成请求的三阶段进度清零，由新主节点重新驱动；
        // 已达成一致的 committedValue 保留，保证安全性。
        states.values().forEach(state -> {
            if (state.committedValue == null) {
                state.prePrepareValue = null;
                state.prepares.clear();
                state.commits.clear();
                state.prepared = false;
            }
        });
        if (isPrimary()) {
            network.broadcast(Message.signed(MessageType.NEW_VIEW, view, -1, null, id, network.keyStore()));
            for (Map.Entry<Long, String> pending : pendingRequests.entrySet()) {
                if (!isCommitted(pending.getKey())) {
                    broadcast(MessageType.PRE_PREPARE, view, pending.getKey(), pending.getValue());
                }
            }
        }
    }

    private void broadcast(MessageType type, int view, long sequence, String value) {
        network.broadcast(Message.signed(type, view, sequence, value, id, network.keyStore()));
    }

    private RequestState stateOf(long sequence) {
        return states.computeIfAbsent(sequence, k -> new RequestState());
    }

    public boolean isPrepared(long sequence) {
        RequestState state = states.get(sequence);
        return state != null && state.prepared;
    }

    public boolean isCommitted(long sequence) {
        RequestState state = states.get(sequence);
        return state != null && state.committedValue != null;
    }

    public String committedValue(long sequence) {
        RequestState state = states.get(sequence);
        return state == null ? null : state.committedValue;
    }
}
