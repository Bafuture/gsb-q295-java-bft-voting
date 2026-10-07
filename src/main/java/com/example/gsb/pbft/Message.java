package com.example.gsb.pbft;

import java.util.Objects;

/**
 * 不可变消息对象。签名为对 (类型, 视图, 序号, 值, 发送者) 的带钥摘要。
 */
public final class Message {

    private final MessageType type;
    private final int view;
    private final long sequence;
    private final String value;
    private final int senderId;
    private final String signature;

    private Message(MessageType type, int view, long sequence, String value, int senderId, String signature) {
        this.type = type;
        this.view = view;
        this.sequence = sequence;
        this.value = value;
        this.senderId = senderId;
        this.signature = signature;
    }

    /** 用发送者自己的密钥正确签名（诚实节点的构造方式）。 */
    public static Message signed(MessageType type, int view, long sequence, String value, int senderId, KeyStore keys) {
        String signature = KeyStore.digest(keys.keyOf(senderId), type, view, sequence, value, senderId);
        return new Message(type, view, sequence, value, senderId, signature);
    }

    /**
     * 用显式给定的签名构造（仅供故障注入测试：把内容与签名不匹配的消息
     * 投递给集群，模拟“截获后篡改”的报文）。
     */
    public static Message withSignature(MessageType type, int view, long sequence, String value, int senderId, String signature) {
        return new Message(type, view, sequence, value, senderId, signature);
    }

    /**
     * 用任意给定密钥签名（测试用来构造伪造消息：
     * 恶意节点不知道目标节点的密钥，只能猜一个，验签必然失败）。
     */
    public static Message signedWithKey(MessageType type, int view, long sequence, String value, int senderId, String key) {
        return new Message(type, view, sequence, value, senderId,
                KeyStore.digest(key, type, view, sequence, value, senderId));
    }

    /** 验签：用发送者登记的密钥重算摘要并比对。 */
    public boolean hasValidSignature(KeyStore keys) {
        return Objects.equals(signature,
                KeyStore.digest(keys.keyOf(senderId), type, view, sequence, value, senderId));
    }

    public MessageType type() { return type; }
    public int view() { return view; }
    public long sequence() { return sequence; }
    public String value() { return value; }
    public int senderId() { return senderId; }
    public String signature() { return signature; }

    @Override
    public String toString() {
        return type + "{v=" + view + ", seq=" + sequence + ", value=" + value + ", from=" + senderId + '}';
    }
}
