package com.example.gsb.pbft;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** 三类恶意行为：伪造消息、延迟响应、矛盾消息；系统仍应达成一致。 */
class ByzantineBehaviorTest {

    @Test
    void forgedMessagesAreDroppedAndConsensusStillHolds() {
        Cluster cluster = new Cluster(4, 1, Map.of(3, ByzantineNode.Behavior.FORGER));
        cluster.submitRequest("tx-forge");

        assertThat(cluster.isFinalized("tx-forge")).isTrue();
        assertThat(cluster.stats().invalidSignatureDrops()).isGreaterThan(0);
        assertThat(cluster.stats().finalizedRequests()).isEqualTo(1);
        for (int i = 0; i < 3; i++) {
            assertThat(cluster.node(i).executedRequests()).containsExactly("tx-forge");
        }
    }

    @Test
    void delayedResponsesDoNotPreventConsensus() {
        Cluster cluster = new Cluster(4, 1, Map.of(3, ByzantineNode.Behavior.DELAYER));
        cluster.submitRequest("tx-delay");

        // 延迟节点不参与，3 个诚实节点仍满足 2f 准备 / 2f+1 提交法定人数
        assertThat(cluster.isFinalized("tx-delay")).isTrue();

        ByzantineNode delayer = (ByzantineNode) cluster.node(3);
        assertThat(delayer.delayedCount()).isGreaterThan(0);
        delayer.releaseDelayed();

        // 迟到的消息被安全吸收：请求不会被重复执行
        assertThat(cluster.stats().finalizedRequests()).isEqualTo(1);
        for (int i = 0; i < 4; i++) {
            assertThat(cluster.node(i).executedRequests()).containsExactly("tx-delay");
        }
    }

    @Test
    void equivocatingNodeCannotSplitHonestMajority() {
        Cluster cluster = new Cluster(4, 1, Map.of(3, ByzantineNode.Behavior.EQUIVOCATOR));
        cluster.submitRequest("tx-equivocate");

        assertThat(cluster.isFinalized("tx-equivocate")).isTrue();
        assertThat(cluster.stats().conflictDrops()).isGreaterThan(0);
        for (int i = 0; i < 3; i++) {
            assertThat(cluster.node(i).executedRequests()).containsExactly("tx-equivocate");
        }
        // 矛盾摘要对应的请求从未被任何节点执行
        for (Node node : cluster.nodes()) {
            assertThat(node.executedRequests()).doesNotContain("conflicting-tx-equivocate");
        }
    }
}
