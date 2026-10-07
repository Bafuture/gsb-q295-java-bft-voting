package com.example.gsb.pbft;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 内存消息网络：同步投递，支持三类故障注入——
 * <ul>
 *   <li>隔离节点（主节点失效模拟）：该节点收不到任何消息，其发出的消息也被丢弃；</li>
 *   <li>延迟节点：该节点发出的消息先进队列，{@link #releaseHeld()} 后统一投递；</li>
 *   <li>伪造/矛盾消息由测试代码直接 {@link #injectToAll(Message)} 或
 *       {@link #inject(int, Message)} 投递给目标节点。</li>
 * </ul>
 */
public final class Network {

    private final KeyStore keyStore = new KeyStore();
    private final Map<Integer, Node> nodes = new HashMap<>();
    private final Set<Integer> isolated = new HashSet<>();
    private final Set<Integer> heldSenders = new HashSet<>();
    private final Deque<Runnable> held = new ArrayDeque<>();

    public KeyStore keyStore() {
        return keyStore;
    }

    public void register(Node node) {
        nodes.put(node.id(), node);
        keyStore.register(node.id(), node.secretKey());
    }

    public Node node(int id) {
        return nodes.get(id);
    }

    public List<Node> allNodes() {
        return new ArrayList<>(nodes.values());
    }

    /** 广播（含发送者自己，模拟收到自己的消息）。 */
    public void broadcast(Message message) {
        for (Node node : nodes.values()) {
            deliver(node.id(), message);
        }
    }

    /** 投递给单个节点。 */
    public void deliver(int toId, Message message) {
        if (isolated.contains(message.senderId()) || isolated.contains(toId)) {
            return;
        }
        if (heldSenders.contains(message.senderId())) {
            held.addLast(() -> doDeliver(toId, message));
        } else {
            doDeliver(toId, message);
        }
    }

    private void doDeliver(int toId, Message message) {
        Node node = nodes.get(toId);
        if (node != null) {
            node.handle(message);
        }
    }

    /** 测试辅助：把一条（可能伪造的）消息投递给所有未隔离节点。 */
    public void injectToAll(Message message) {
        for (Node node : nodes.values()) {
            inject(node.id(), message);
        }
    }

    /** 测试辅助：把一条（可能伪造的）消息投递给指定节点，不做隔离判断以外的检查。 */
    public void inject(int toId, Message message) {
        if (!isolated.contains(toId)) {
            doDeliver(toId, message);
        }
    }

    public void isolate(int nodeId) {
        isolated.add(nodeId);
    }

    public void heal(int nodeId) {
        isolated.remove(nodeId);
    }

    /** 该节点之后发出的所有消息进入延迟队列。 */
    public void holdFrom(int nodeId) {
        heldSenders.add(nodeId);
    }

    public void stopHoldingFrom(int nodeId) {
        heldSenders.remove(nodeId);
    }

    /** 投递全部积压消息（延迟恢复）。 */
    public int releaseHeld() {
        int count = 0;
        while (!held.isEmpty()) {
            held.removeFirst().run();
            count++;
        }
        return count;
    }

    public int heldCount() {
        return held.size();
    }

    public boolean isIsolated(int nodeId) {
        return isolated.contains(nodeId);
    }
}
