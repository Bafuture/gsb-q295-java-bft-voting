package com.example.gsb.pbft;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * PBFT 集群：管理 {@code n} 个节点与内存网络。
 *
 * <p>容错公式：当集群规模为 {@code n = 3f + 1} 时可容忍 {@code f} 个拜占庭节点。
 * 推导见 README：提交法定人数 {@code 2f+1} 保证任意两个法定人数至少有一个
 * 诚实节点交集，从而恶意节点无法让两个不同值同时达到法定人数。</p>
 */
public final class Cluster {

    private final int n;
    private final int f;
    private final Network network = new Network();
    private final List<Node> nodes = new ArrayList<>();
    private long nextSequence;

    /** 标准构造：容忍 f 个拜占庭节点，n = 3f + 1。 */
    public static Cluster forFaultTolerance(int f) {
        return new Cluster(3 * f + 1, f);
    }

    /** 自定义规模（用于演示 n &lt; 3f+1 时无法容错）。 */
    public static Cluster ofSize(int n, int f) {
        return new Cluster(n, f);
    }

    private Cluster(int n, int f) {
        this.n = n;
        this.f = f;
        for (int id = 0; id < n; id++) {
            Node node = new Node(id, n, f, network);
            network.register(node);
            nodes.add(node);
        }
    }

    public int size() { return n; }
    public int faultTolerance() { return f; }
    public Network network() { return network; }
    public Node node(int id) { return nodes.get(id); }
    public List<Node> nodes() { return List.copyOf(nodes); }

    /** 提交一个客户端请求，返回请求序号。 */
    public long submit(String value) {
        long sequence = nextSequence++;
        for (Node node : nodes) {
            node.receiveClientRequest(sequence, value);
        }
        return sequence;
    }

    /** 是否有节点已对该序号达成一致。 */
    public boolean isCommitted(long sequence) {
        return nodes.stream().anyMatch(node -> node.isCommitted(sequence));
    }

    /** 已达成一致的值（未达成时为 null）；所有已提交节点的值必然相同。 */
    public String committedValue(long sequence) {
        return nodes.stream()
                .map(node -> node.committedValue(sequence))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /** 已提交该请求的节点数。 */
    public long committedNodeCount(long sequence) {
        return nodes.stream().filter(node -> node.isCommitted(sequence)).count();
    }

    /** 集群级聚合统计。 */
    public Stats stats() {
        Stats total = new Stats();
        for (Node node : nodes) {
            total = total.merge(node.stats());
        }
        return total;
    }
}
