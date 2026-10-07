package com.example.gsb.pbft;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** 视图更换：主节点崩溃/沉默后选出新主节点，未完成请求继续达成一致。 */
class ViewChangeTest {

    @Test
    void pendingRequestIsFinalizedAfterPrimaryCrash() {
        Cluster cluster = new Cluster(4);
        cluster.crash(0); // 主节点（view 0 的 p = 0）崩溃，请求无人预准备
        cluster.submitRequest("tx-during-outage");
        assertThat(cluster.stats().finalizedRequests()).isZero();

        cluster.triggerViewChange();

        assertThat(cluster.primaryOf(1)).isEqualTo(1);
        assertThat(cluster.stats().viewChangeSent()).isEqualTo(3);
        assertThat(cluster.stats().newViewSent()).isEqualTo(1);
        assertThat(cluster.stats().viewChanges()).isEqualTo(1);
        assertThat(cluster.isFinalized("tx-during-outage")).isTrue();
        for (int i = 1; i < 4; i++) {
            Node node = cluster.node(i);
            assertThat(node.view()).isEqualTo(1);
            assertThat(node.executedRequests()).containsExactly("tx-during-outage");
        }
    }

    @Test
    void silentByzantinePrimaryIsReplaced() {
        Cluster cluster = new Cluster(4, 1, Map.of(0, ByzantineNode.Behavior.SILENT));
        cluster.submitRequest("tx-silent-primary");
        assertThat(cluster.stats().finalizedRequests()).isZero();

        cluster.triggerViewChange();

        assertThat(cluster.stats().viewChanges()).isEqualTo(1);
        assertThat(cluster.isFinalized("tx-silent-primary")).isTrue();
        for (int i = 1; i < 4; i++) {
            assertThat(cluster.node(i).executedRequests())
                    .containsExactly("tx-silent-primary");
        }
    }

    @Test
    void preparedButUnfinishedRequestIsReproposedInNewView() {
        // 预准备已广播，但所有副本的 PREPARE/COMMIT 被延迟：请求停在准备阶段
        Map<Integer, ByzantineNode.Behavior> delayed = Map.of(
                1, ByzantineNode.Behavior.DELAYER,
                2, ByzantineNode.Behavior.DELAYER,
                3, ByzantineNode.Behavior.DELAYER);
        Cluster cluster = new Cluster(4, 1, delayed);
        cluster.submitRequest("tx-inflight");
        assertThat(cluster.stats().finalizedRequests()).isZero();

        // 主节点随后崩溃并触发视图更换：节点 1 成为新主节点，
        // 未完成请求通过 VIEW_CHANGE/NEW_VIEW 携带的预准备在新视图继续推进
        cluster.crash(0);
        cluster.triggerViewChange();

        // VIEW_CHANGE、NEW_VIEW、新视图的 PREPARE、COMMIT 依次解除延迟
        for (int round = 0; round < 5; round++) {
            for (Node node : cluster.nodes()) {
                if (node instanceof ByzantineNode byzantine) {
                    byzantine.releaseDelayed();
                }
            }
        }

        assertThat(cluster.stats().viewChanges()).isEqualTo(1);
        assertThat(cluster.stats().staleDrops()).isGreaterThan(0);
        assertThat(cluster.isFinalized("tx-inflight")).isTrue();
        for (int i = 1; i < 4; i++) {
            Node node = cluster.node(i);
            assertThat(node.view()).isEqualTo(1);
            assertThat(node.executedRequests()).containsExactly("tx-inflight");
        }
    }
}
