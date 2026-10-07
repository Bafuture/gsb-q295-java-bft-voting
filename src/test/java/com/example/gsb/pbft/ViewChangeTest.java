package com.example.gsb.pbft;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 主节点失效触发视图更换，新主节点接管后未完成的请求继续达成一致。 */
class ViewChangeTest {

    @Test
    void pendingRequestCommitsAfterViewChange() {
        Cluster cluster = Cluster.forFaultTolerance(1);
        Network network = cluster.network();

        // 视图 0 的主节点是节点 0，将其隔离模拟失效
        network.isolate(0);

        long seq = cluster.submit("X");
        // 主节点的预准备发不出去，请求无法完成
        assertThat(cluster.isCommitted(seq)).isFalse();

        // 诚实节点超时后发起视图更换（目标视图 1，新主节点为节点 1）
        cluster.node(1).triggerViewChange();
        cluster.node(2).triggerViewChange();
        cluster.node(3).triggerViewChange();

        // 2f+1 = 3 张 VIEW_CHANGE 选票达成，进入视图 1
        assertThat(cluster.node(1).view()).isEqualTo(1);
        assertThat(cluster.node(2).view()).isEqualTo(1);
        assertThat(cluster.node(3).view()).isEqualTo(1);

        // 新主节点重发预准备，未完成的请求在新视图达成一致
        assertThat(cluster.isCommitted(seq)).isTrue();
        assertThat(cluster.committedValue(seq)).isEqualTo("X");
        assertThat(cluster.node(1).committedValue(seq)).isEqualTo("X");
        assertThat(cluster.node(2).committedValue(seq)).isEqualTo("X");
        assertThat(cluster.node(3).committedValue(seq)).isEqualTo("X");

        // 统计：3 个诚实节点各经历 1 次视图更换
        Stats stats = cluster.stats();
        assertThat(stats.viewChanges()).isEqualTo(3);
        assertThat(stats.viewChangeAccepted()).isGreaterThanOrEqualTo(3);
        assertThat(stats.newViewAccepted()).isGreaterThanOrEqualTo(1);
        assertThat(stats.committedRequests()).isEqualTo(3);
    }

    @Test
    void viewChangeVotesFromSameNodeAreDeduplicated() {
        Cluster cluster = Cluster.forFaultTolerance(1);
        Network network = cluster.network();
        network.isolate(0);

        cluster.submit("X");
        cluster.node(1).triggerViewChange();

        // 同一节点重复发送 VIEW_CHANGE 不会重复计票
        Message duplicate = Message.signed(
                MessageType.VIEW_CHANGE, 1, -1, null, 1, network.keyStore());
        network.injectToAll(duplicate);

        // 只有 1 张有效选票，未达到 2f+1，不能切换视图
        assertThat(cluster.node(2).view()).isZero();
        assertThat(cluster.stats().dedupDropped()).isGreaterThan(0);
    }
}
