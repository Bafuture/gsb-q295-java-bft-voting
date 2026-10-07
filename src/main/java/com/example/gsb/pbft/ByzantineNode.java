package com.example.gsb.pbft;

import java.util.ArrayList;
import java.util.List;

/**
 * 拜占庭/故障节点，通过重写发送钩子 {@link #emit(Message)} 构造三类恶意行为：
 *
 * <ul>
 *   <li>{@link Behavior#FORGER}：发出签名被篡改的伪造消息；</li>
 *   <li>{@link Behavior#DELAYER}：扣押所有出站消息，稍后可通过
 *       {@link #releaseDelayed()} 补发（延迟响应）；</li>
 *   <li>{@link Behavior#EQUIVOCATOR}：对同一序号额外广播摘要不同的准备消息（矛盾消息）。</li>
 * </ul>
 *
 * <p>另外 {@link Behavior#SILENT} 表示彻底沉默（模拟崩溃主节点）。
 */
public final class ByzantineNode extends Node {

    public enum Behavior {
        SILENT, FORGER, DELAYER, EQUIVOCATOR
    }

    private final Behavior behavior;
    private final List<Message> delayed = new ArrayList<>();

    public ByzantineNode(int id, Cluster cluster, Behavior behavior) {
        super(id, cluster);
        this.behavior = behavior;
    }

    public Behavior behavior() {
        return behavior;
    }

    public int delayedCount() {
        return delayed.size();
    }

    public void releaseDelayed() {
        List<Message> copy = List.copyOf(delayed);
        delayed.clear();
        for (Message m : copy) {
            super.emit(m);
        }
    }

    @Override
    protected void emit(Message m) {
        switch (behavior) {
            case SILENT -> {
            }
            case FORGER -> super.emit(m.withSignature("forged-signature-" + id()));
            case DELAYER -> delayed.add(m);
            case EQUIVOCATOR -> {
                super.emit(m);
                if (m.type() == Message.Type.PREPARE) {
                    super.emit(Message.prepare(m.view(), m.seq(),
                            "conflicting-" + m.digest(), id()));
                }
            }
        }
    }
}
