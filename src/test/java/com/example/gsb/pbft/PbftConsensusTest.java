package com.example.gsb.pbft;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 三阶段共识达成、重复消息去重与统计计数。 */
class PbftConsensusTest {

    @Test
    void reachesConsensusThroughThreePhases() {
        Cluster cluster = new Cluster(4); // n = 4 = 3f+1, f = 1
        cluster.submitRequest("transfer-100");

        assertThat(cluster.isFinalized("transfer-100")).isTrue();
        assertThat(cluster.stats().prePrepareSent()).isEqualTo(1);
        assertThat(cluster.stats().prepareSent()).isEqualTo(4);
        assertThat(cluster.stats().commitSent()).isEqualTo(4);
        assertThat(cluster.stats().finalizedRequests()).isEqualTo(1);
        for (Node node : cluster.nodes()) {
            assertThat(node.executedRequests()).containsExactly("transfer-100");
        }
    }

    @Test
    void multipleRequestsAreFinalizedInOrder() {
        Cluster cluster = new Cluster(4);
        cluster.submitRequest("tx-1");
        cluster.submitRequest("tx-2");
        cluster.submitRequest("tx-3");

        assertThat(cluster.stats().finalizedRequests()).isEqualTo(3);
        for (Node node : cluster.nodes()) {
            assertThat(node.executedRequests()).containsExactly("tx-1", "tx-2", "tx-3");
        }
    }

    @Test
    void duplicatePrepareMessagesAreDeduplicated() {
        Cluster cluster = new Cluster(4);
        cluster.submitRequest("tx-dup");
        String digest = Digests.sha256("tx-dup");

        // 节点 1 重复发送同一准备消息（模拟重放/重传），每个接收方都应去重丢弃
        Message duplicate = Message.prepare(0, 1, digest, 1);
        cluster.broadcast(duplicate);
        cluster.broadcast(duplicate);

        assertThat(cluster.stats().dedupDrops()).isGreaterThanOrEqualTo(8);
        assertThat(cluster.stats().finalizedRequests()).isEqualTo(1);
        for (Node node : cluster.nodes()) {
            assertThat(node.executedRequests()).containsExactly("tx-dup");
        }
    }

    @Test
    void duplicateCommitMessagesAreDeduplicated() {
        Cluster cluster = new Cluster(4);
        cluster.submitRequest("tx-dup-commit");
        String digest = Digests.sha256("tx-dup-commit");

        Message duplicate = Message.commit(0, 1, digest, 2);
        cluster.broadcast(duplicate);

        assertThat(cluster.stats().dedupDrops()).isGreaterThanOrEqualTo(4);
        assertThat(cluster.stats().finalizedRequests()).isEqualTo(1);
    }
}
