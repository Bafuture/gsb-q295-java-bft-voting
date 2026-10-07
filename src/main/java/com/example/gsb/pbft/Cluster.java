package com.example.gsb.pbft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 内存集群：节点注册表 + 同步广播网络。
 *
 * <p>主节点编号为 {@code view mod n}。默认容错阈值 {@code f = (n-1)/3}，
 * 也可显式传入 f（用于构造节点数不足 3f+1 的反例）。
 * 当一个请求摘要被不少于 2f+1 个节点执行时，集群视其为“达成一致”。
 */
public final class Cluster {

    private final List<Node> nodes = new ArrayList<>();
    private final int f;
    private final Stats stats = new Stats();
    private final Map<String, Set<Integer>> executions = new HashMap<>();
    private final Set<String> finalizedDigests = new LinkedHashSet<>();
    private final Set<Integer> completedViewChanges = new HashSet<>();

    public Cluster(int nodeCount) {
        this(nodeCount, (nodeCount - 1) / 3, Map.of());
    }

    public Cluster(int nodeCount, int f) {
        this(nodeCount, f, Map.of());
    }

    public Cluster(int nodeCount, int f, Map<Integer, ByzantineNode.Behavior> byzantine) {
        if (nodeCount < 1 || f < 0) {
            throw new IllegalArgumentException("nodeCount >= 1 and f >= 0 required");
        }
        this.f = f;
        for (int i = 0; i < nodeCount; i++) {
            ByzantineNode.Behavior behavior = byzantine.get(i);
            nodes.add(behavior == null ? new Node(i, this) : new ByzantineNode(i, this, behavior));
        }
    }

    public int size() {
        return nodes.size();
    }

    public int f() {
        return f;
    }

    public Stats stats() {
        return stats;
    }

    public Node node(int id) {
        return nodes.get(id);
    }

    public List<Node> nodes() {
        return List.copyOf(nodes);
    }

    /** PBFT 主节点轮换规则：p = v mod n。 */
    public int primaryOf(int view) {
        return Math.floorMod(view, nodes.size());
    }

    public void broadcast(Message m) {
        stats.recordSent(m.type());
        for (Node node : nodes) {
            node.receive(m);
        }
    }

    /** 客户端提交请求：所有节点入队，当前视图的主节点负责发起预准备。 */
    public void submitRequest(String request) {
        for (Node node : nodes) {
            node.enqueue(request);
        }
        for (Node node : nodes) {
            node.maybePropose();
        }
    }

    public void crash(int nodeId) {
        nodes.get(nodeId).crash();
    }

    /** 模拟所有非崩溃节点检测到主节点超时并发起视图更换。 */
    public void triggerViewChange() {
        for (Node node : nodes) {
            node.onPrimaryTimeout();
        }
    }

    void onExecuted(String digest, int nodeId) {
        Set<Integer> executors = executions.computeIfAbsent(digest, d -> new HashSet<>());
        executors.add(nodeId);
        if (executors.size() >= 2 * f + 1 && finalizedDigests.add(digest)) {
            stats.requestFinalized();
        }
    }

    void recordViewChange(int newView) {
        if (completedViewChanges.add(newView)) {
            stats.viewChangeCompleted();
        }
    }

    public boolean isFinalized(String request) {
        return finalizedDigests.contains(Digests.sha256(request));
    }
}
