package com.example.gsb.pbft;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 签名（带钥摘要）校验：任何与内容不匹配的签名都必须被丢弃。 */
class SignatureVerificationTest {

    @Test
    void tamperedMessageFailsDigestCheck() {
        Cluster cluster = Cluster.forFaultTolerance(1);
        Network network = cluster.network();
        long seq = cluster.submit("X");

        // 攻击者截获节点 1 的真实 PREPARE("X")，把内容篡改为 "Y" 但保留原签名
        Message genuine = Message.signed(
                MessageType.PREPARE, 0, seq, "X", 1, network.keyStore());
        Message tampered = Message.withSignature(
                MessageType.PREPARE, 0, seq, "Y", 1, genuine.signature());

        assertThat(genuine.hasValidSignature(network.keyStore())).isTrue();
        assertThat(tampered.hasValidSignature(network.keyStore())).isFalse();

        long droppedBefore = cluster.stats().signatureDropped();
        network.injectToAll(tampered);

        // 所有节点验签失败并丢弃，共识结果不受影响
        assertThat(cluster.stats().signatureDropped()).isEqualTo(droppedBefore + 4);
        for (Node node : cluster.nodes()) {
            assertThat(node.committedValue(seq)).isEqualTo("X");
        }
    }

    @Test
    void messageSignedWithWrongKeyIsRejected() {
        Cluster cluster = Cluster.forFaultTolerance(1);
        Network network = cluster.network();

        Message forged = Message.signedWithKey(
                MessageType.COMMIT, 0, 0L, "X", 2, "not-node-2-key");
        assertThat(forged.hasValidSignature(network.keyStore())).isFalse();

        network.injectToAll(forged);
        assertThat(cluster.stats().signatureDropped()).isEqualTo(4);
        assertThat(cluster.stats().commitAccepted()).isZero();
    }
}
