package com.example.gsb.pbft;

import java.util.List;

/**
 * 内存消息对象，覆盖 PBFT 三阶段及视图更换：
 * PRE_PREPARE -> PREPARE -> COMMIT，以及 VIEW_CHANGE / NEW_VIEW。
 *
 * <p>签名在消息创建时用发送者节点的密钥自动生成；测试可通过
 * {@link #withSignature(String)} 构造签名不匹配的伪造消息。
 */
public final class Message {

    public enum Type {
        PRE_PREPARE, PREPARE, COMMIT, VIEW_CHANGE, NEW_VIEW
    }

    private final Type type;
    private final int view;
    private final long seq;
    private final String digest;
    private final int senderId;
    private final String payload;
    private final List<Message> viewChanges;
    private final List<Message> prePrepares;
    private final String signature;

    private Message(Type type, int view, long seq, String digest, int senderId, String payload,
                    List<Message> viewChanges, List<Message> prePrepares, String signature) {
        this.type = type;
        this.view = view;
        this.seq = seq;
        this.digest = digest == null ? "" : digest;
        this.senderId = senderId;
        this.payload = payload == null ? "" : payload;
        this.viewChanges = List.copyOf(viewChanges);
        this.prePrepares = List.copyOf(prePrepares);
        this.signature = signature;
    }

    public static Message prePrepare(int view, long seq, String digest, int senderId, String request) {
        return new Message(Type.PRE_PREPARE, view, seq, digest, senderId, request,
                List.of(), List.of(), null).signed();
    }

    public static Message prepare(int view, long seq, String digest, int senderId) {
        return new Message(Type.PREPARE, view, seq, digest, senderId, "",
                List.of(), List.of(), null).signed();
    }

    public static Message commit(int view, long seq, String digest, int senderId) {
        return new Message(Type.COMMIT, view, seq, digest, senderId, "",
                List.of(), List.of(), null).signed();
    }

    /** payload 携带该节点已知的未完成请求，格式为 "seq:digest:request;..."。 */
    public static Message viewChange(int view, int senderId, String preparedSetPayload) {
        return new Message(Type.VIEW_CHANGE, view, 0L, "", senderId, preparedSetPayload,
                List.of(), List.of(), null).signed();
    }

    public static Message newView(int view, int senderId,
                                  List<Message> viewChanges, List<Message> prePrepares) {
        return new Message(Type.NEW_VIEW, view, 0L, "", senderId, "",
                viewChanges, prePrepares, null).signed();
    }

    /** 返回一个用指定签名替换后的副本，用于模拟伪造/篡改消息。 */
    public Message withSignature(String forgedSignature) {
        return new Message(type, view, seq, digest, senderId, payload,
                viewChanges, prePrepares, forgedSignature);
    }

    public Type type() {
        return type;
    }

    public int view() {
        return view;
    }

    public long seq() {
        return seq;
    }

    public String digest() {
        return digest;
    }

    public int senderId() {
        return senderId;
    }

    public String payload() {
        return payload;
    }

    public List<Message> viewChanges() {
        return viewChanges;
    }

    public List<Message> prePrepares() {
        return prePrepares;
    }

    public String signature() {
        return signature;
    }

    /** 被签名的规范内容；携带的子消息各自带有自己的签名。 */
    public String content() {
        return type + "|" + view + "|" + seq + "|" + digest + "|" + senderId + "|" + payload;
    }

    private Message signed() {
        return new Message(type, view, seq, digest, senderId, payload,
                viewChanges, prePrepares, Signer.sign(senderId, content()));
    }
}
