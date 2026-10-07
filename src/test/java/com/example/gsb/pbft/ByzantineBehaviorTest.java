package com.example.gsb.pbft;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 三类拜占庭行为：伪造消息、延迟响应、发出矛盾消息。
 * 在 n = 3f+1 = 4（f=1）的集群中，系统仍应对相同请求值达成一致。
 */
class ByzantineBehaviorTest {

    @Test
    void forgedMessageWithWrongKeyIsDropped() {
        Cluster cluster = Cluster.forFaultTolerance(1);
        Network network = cluster.network();

        // 恶意节点 3 冒充节点 0 签名（猜的密钥），在共识开始前注入伪造 PREPARE
        Message forged = Message.signedWithKey(
                MessageType.PREPARE, 0, 0L, "EVIL", 0, "guessed-key-by-attacker");
        network.injectToAll(forged);

        long seq = cluster.submit("X");

        assertThat(cluster.committedValue(seq)).isEqualTo("X");
        // 4 个节点验签全部失败并丢弃
        assertThat(cluster.stats().signatureDropped()).isEqualTo(4);
    }

    @Test
    void delayedResponsesStillAllowConsensusWithoutTheSlowNode() {
        Cluster cluster = Cluster.forFaultTolerance(1);
        Network network = cluster.network();

        network.holdFrom(3); // 节点 3 响应延迟，发出的消息全部积压

        long seq = cluster.submit("X");

        // 仅靠其余 3 个诚实节点（2f+1）即可达成一致
        assertThat(cluster.committedValue(seq)).isEqualTo("X");
        assertThat(cluster.committedNodeCount(seq)).isGreaterThanOrEqualTo(3);
        assertThat(network.heldCount()).isGreaterThan(0);

        // 延迟恢复后，积压消息投递完毕，慢节点也补齐一致状态
        network.stopHoldingFrom(3);
        network.releaseHeld();
        assertThat(cluster.node(3).isCommitted(seq)).isTrue();
        assertThat(cluster.node(3).committedValue(seq)).isEqualTo("X");
    }

    @Test
    void equivocatingMessagesCannotSplitHonestNodes() {
        Cluster cluster = Cluster.forFaultTolerance(1);
        Network network = cluster.network();

        // 恶意节点 3 在共识开始前对未来请求 seq=0 发出矛盾的 PREPARE/COMMIT（用自己的真密钥签名，
        // 签名合法但内容与主节点预准备冲突）
        network.injectToAll(Message.signed(
                MessageType.PREPARE, 0, 0L, "EVIL", 3, network.keyStore()));
        network.injectToAll(Message.signed(
                MessageType.COMMIT, 0, 0L, "EVIL", 3, network.keyStore()));

        long seq = cluster.submit("X");

        // 矛盾值凑不齐 2f 个不同节点的 PREPARE，也凑不齐 2f+1 个 COMMIT；
        // 所有节点对主节点提议的 "X" 达成一致
        for (Node node : cluster.nodes()) {
            assertThat(node.committedValue(seq)).isEqualTo("X");
        }
        assertThat(cluster.committedValue(seq)).isEqualTo("X");
        // 恶意节点随后为 "X" 发送的真实 PREPARE/COMMIT 与先到的 "EVIL" 冲突，按同节点去重丢弃
        assertThat(cluster.stats().dedupDropped()).isGreaterThan(0);
    }
}
