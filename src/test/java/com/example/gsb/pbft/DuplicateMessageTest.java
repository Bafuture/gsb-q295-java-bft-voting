package com.example.gsb.pbft;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 同一节点对同一阶段重复发送的消息必须去重，且不影响达成一致。 */
class DuplicateMessageTest {

    @Test
    void duplicatePrepareCommitAndPrePrepareAreDeduplicated() {
        Cluster cluster = Cluster.forFaultTolerance(1);
        Network network = cluster.network();
        long seq = cluster.submit("X");
        assertThat(cluster.isCommitted(seq)).isTrue();

        long beforeDedup = cluster.stats().dedupDropped();

        // 节点 1 的 PREPARE 已被节点 0 记录，重发一条内容完全相同的
        Message duplicatePrepare = Message.signed(
                MessageType.PREPARE, 0, seq, "X", 1, network.keyStore());
        network.inject(0, duplicatePrepare);

        // 节点 2 的 COMMIT 同样重复
        Message duplicateCommit = Message.signed(
                MessageType.COMMIT, 0, seq, "X", 2, network.keyStore());
        network.inject(0, duplicateCommit);

        // 主节点 0 的 PRE_PREPARE 对节点 1 重发
        Message duplicatePrePrepare = Message.signed(
                MessageType.PRE_PREPARE, 0, seq, "X", 0, network.keyStore());
        network.inject(1, duplicatePrePrepare);

        assertThat(cluster.stats().dedupDropped()).isEqualTo(beforeDedup + 3);

        // 去重只丢弃消息，不重复计数、不破坏结果：每个节点只提交一次
        for (Node node : cluster.nodes()) {
            assertThat(node.committedValue(seq)).isEqualTo("X");
            assertThat(node.stats().committedRequests()).isEqualTo(1);
        }
    }
}
