package com.example.gsb.pbft;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 节点数不足 3f+1 的反例：n=3、f=1（要求 n >= 4）。
 * 提交法定人数为 2f+1=3，一个节点沉默后只剩 2 个提交，系统无法达成一致。
 */
class InsufficientNodesTest {

    @Test
    void threeNodesCannotTolerateOneFaultyNode() {
        Cluster cluster = new Cluster(3, 1);
        cluster.crash(2);
        cluster.submitRequest("tx-stuck");

        assertThat(cluster.stats().prePrepareSent()).isEqualTo(1);
        assertThat(cluster.stats().prepareSent()).isEqualTo(2); // 2f=2，准备证书可凑齐
        assertThat(cluster.stats().commitSent()).isEqualTo(2);  // 2f+1=3，永远凑不齐
        assertThat(cluster.stats().finalizedRequests()).isZero();
        assertThat(cluster.node(0).executedRequests()).isEmpty();
        assertThat(cluster.node(1).executedRequests()).isEmpty();
    }

    @Test
    void fourNodesDoTolerateOneFaultyNode() {
        Cluster cluster = new Cluster(4, 1); // n = 3f+1
        cluster.crash(3);
        cluster.submitRequest("tx-ok");

        assertThat(cluster.stats().commitSent()).isEqualTo(3);
        assertThat(cluster.isFinalized("tx-ok")).isTrue();
    }
}
