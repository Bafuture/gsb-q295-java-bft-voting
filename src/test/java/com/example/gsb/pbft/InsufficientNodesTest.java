package com.example.gsb.pbft;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 节点数不足 3f+1 时无法容忍 f 个拜占庭节点。
 *
 * <p>n = 3, f = 1（3 &lt; 3*1+1 = 4）：提交法定人数 2f+1 = 3 要求全部节点参与，
 * 任意 1 个节点失效都会使系统永远无法提交（丧失可用性）。</p>
 */
class InsufficientNodesTest {

    @Test
    void clusterOfThreeCannotTolerateOneFaultyNode() {
        Cluster cluster = Cluster.ofSize(3, 1); // 3 < 3f+1 = 4
        cluster.network().isolate(2); // 1 个节点故障

        long seq = cluster.submit("X");

        // 准备阶段尚可完成（2f = 2 张 PREPARE），但提交阶段凑不齐 2f+1 = 3 张 COMMIT
        assertThat(cluster.node(0).isPrepared(seq)).isTrue();
        assertThat(cluster.node(1).isPrepared(seq)).isTrue();
        assertThat(cluster.isCommitted(seq)).isFalse();
        assertThat(cluster.committedValue(seq)).isNull();
        assertThat(cluster.stats().committedRequests()).isZero();
    }

    @Test
    void clusterOfThreeCannotSurviveViewChangeEither() {
        Cluster cluster = Cluster.ofSize(3, 1);
        cluster.network().isolate(0); // 主节点失效

        long seq = cluster.submit("X");
        cluster.node(1).triggerViewChange();
        cluster.node(2).triggerViewChange();

        // 视图更换本身需要 2f+1 = 3 张选票，只剩 2 个诚实节点，永远凑不齐
        assertThat(cluster.node(1).view()).isZero();
        assertThat(cluster.isCommitted(seq)).isFalse();
    }

    @Test
    void addingOneMoreNodeRestoresFaultTolerance() {
        // 对照组：n = 4 = 3f+1，同样 1 个节点失效，系统正常达成一致
        Cluster cluster = Cluster.forFaultTolerance(1);
        cluster.network().isolate(3);

        long seq = cluster.submit("X");

        assertThat(cluster.isCommitted(seq)).isTrue();
        assertThat(cluster.committedValue(seq)).isEqualTo("X");
    }
}
