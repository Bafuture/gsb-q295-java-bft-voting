package com.example.gsb.pbft;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 简化的签名校验：每个节点持有一个预共享密钥，消息认证码采用 HmacSHA256。
 * 所有节点都知道彼此的密钥（相当于 PBFT 论文中的 MAC 向量），
 * 因此可以校验任意节点发出的消息；攻击者不知道密钥，伪造的签名无法通过校验。
 */
public final class Signer {

    private Signer() {
    }

    public static String sign(int nodeId, String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes(nodeId), "HmacSHA256"));
            return bytesToHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean verify(int nodeId, String content, String signature) {
        if (signature == null) {
            return false;
        }
        String expected = sign(nodeId, content);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] keyBytes(int nodeId) {
        return ("pbft-node-secret-" + nodeId).getBytes(StandardCharsets.UTF_8);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
