package com.example.gsb.pbft;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 签名（HMAC 摘要）校验：签名不匹配、身份伪造的消息必须被丢弃。 */
class SignatureTest {

    @Test
    void prepareWithInvalidSignatureIsDiscarded() {
        Cluster cluster = new Cluster(4);
        Message forged = Message.prepare(0, 1, Digests.sha256("tx"), 1).withSignature("00ff");

        cluster.broadcast(forged);

        assertThat(cluster.stats().invalidSignatureDrops()).isEqualTo(4);
        assertThat(cluster.stats().prepareSent()).isEqualTo(1);
        assertThat(cluster.stats().commitSent()).isZero();
        assertThat(cluster.stats().finalizedRequests()).isZero();
    }

    @Test
    void tamperedPrePrepareIsRejected() {
        Cluster cluster = new Cluster(4);
        Message tampered = Message.prePrepare(0, 1, Digests.sha256("evil"), 0, "evil")
                .withSignature("bad-signature");

        cluster.broadcast(tampered);

        assertThat(cluster.stats().invalidSignatureDrops()).isEqualTo(4);
        assertThat(cluster.stats().prepareSent()).isZero();
        assertThat(cluster.stats().finalizedRequests()).isZero();
    }

    @Test
    void identitySpoofingIsDetected() {
        // 攻击者节点 3 伪造一条声称来自节点 1 的 COMMIT，但只能用自己的密钥签名
        Message original = Message.commit(0, 1, Digests.sha256("tx"), 1);
        Message spoofed = original.withSignature(Signer.sign(3, original.content()));

        Cluster cluster = new Cluster(4);
        cluster.broadcast(spoofed);

        assertThat(cluster.stats().invalidSignatureDrops()).isEqualTo(4);
        assertThat(cluster.stats().finalizedRequests()).isZero();
    }

    @Test
    void validSignaturesAreAccepted() {
        Cluster cluster = new Cluster(4);
        cluster.submitRequest("tx-signed");

        assertThat(cluster.isFinalized("tx-signed")).isTrue();
        assertThat(cluster.stats().invalidSignatureDrops()).isZero();
    }
}
