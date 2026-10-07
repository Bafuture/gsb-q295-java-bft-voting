package com.example.gsb.pbft;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 简化密钥库：每个节点持有一个对称密钥，用于模拟消息签名。
 *
 * <p>真实 PBFT 使用非对称签名（如 RSA/ECDSA），这里用
 * “摘要 = hash(密钥, 消息内容)” 的带钥摘要来模拟：
 * 诚实节点用自己的密钥签名，验证方用发送者登记的密钥重算摘要比对；
 * 恶意节点不知道别人的密钥，因此伪造他人消息时摘要不匹配，会被丢弃。</p>
 */
public final class KeyStore {

    private final Map<Integer, String> keys = new HashMap<>();

    public void register(int nodeId, String key) {
        keys.put(nodeId, Objects.requireNonNull(key));
    }

    public String keyOf(int nodeId) {
        String key = keys.get(nodeId);
        if (key == null) {
            throw new IllegalArgumentException("unknown node id: " + nodeId);
        }
        return key;
    }

    /** 计算带钥摘要（模拟签名）。 */
    public static String digest(String key, MessageType type, int view, long sequence, String value, int senderId) {
        return Integer.toHexString(Objects.hash(key, type, view, sequence, value, senderId));
    }
}
