package com.example.gsb.pbft;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 三阶段（预准备/准备/提交）正常达成一致，并校验法定人数与统计。 */
class ThreePhaseConsensusTest {

    @Test
    void reachesConsensusThroughThreePhases() {
        Cluster cluster = Cluster.forFaultTolerance(1); // n = 3*1+1 = 4
        assertThat(cluster.size()).isEqualTo(4);
        assertThat(cluster.node(0).prepareQuorum()).isEqualTo(2);  // 2f
        assertThat(cluster.node(0).commitQuorum()).isEqualTo(3);   // 2f+1

        long seq = cluster.submit("X");

        for (Node node : cluster.nodes()) {
            assertThat(node.isPrepared(seq)).as("node %d prepared", node.id()).isTrue();
            assertThat(node.isCommitted(seq)).as("node %d committed", node.id()).isTrue();
            assertThat(node.committedValue(seq)).isEqualTo("X");
        }
        assertThat(cluster.committedValue(seq)).isEqualTo("X");
        assertThat(cluster.committedNodeCount(seq)).isEqualTo(4);
    }

    @Test
    void collectsStatsForEachPhase() {
        Cluster cluster = Cluster.forFaultTolerance(1);
        long seq = cluster.submit("X");
        assertThat(cluster.isCommitted(seq)).isTrue();

        Stats stats = cluster.stats();
        // 4 个节点各接受 1 条预准备
        assertThat(stats.prePrepareAccepted()).isEqualTo(4);
        // 4 个节点各广播 1 条准备，投递给 4 个节点（含自己）
        assertThat(stats.prepareAccepted()).isEqualTo(16);
        // 提交阶段同理
        assertThat(stats.commitAccepted()).isEqualTo(16);
        // 4 个节点各自对该请求达成一致
        assertThat(stats.committedRequests()).isEqualTo(4);
        // 正常流程无去重丢弃、无验签失败、无视图更换
        assertThat(stats.dedupDropped()).isZero();
        assertThat(stats.signatureDropped()).isZero();
        assertThat(stats.viewChanges()).isZero();
    }

    @Test
    void reachesConsensusOnMultipleRequests() {
        Cluster cluster = Cluster.forFaultTolerance(1);
        long first = cluster.submit("A");
        long second = cluster.submit("B");

        assertThat(cluster.committedValue(first)).isEqualTo("A");
        assertThat(cluster.committedValue(second)).isEqualTo("B");
        assertThat(cluster.stats().committedRequests()).isEqualTo(8); // 4 节点 × 2 请求
    }
}
