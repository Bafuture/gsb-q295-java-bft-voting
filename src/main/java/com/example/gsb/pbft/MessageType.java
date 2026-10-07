package com.example.gsb.pbft;

/**
 * PBFT 消息类型（简化版）。
 *
 * <ul>
 *   <li>{@link #REQUEST}     —— 客户端请求，仅用于在节点内存中登记待处理请求；</li>
 *   <li>{@link #PRE_PREPARE} —— 预准备：主节点把请求指派到某个视图/序号；</li>
 *   <li>{@link #PREPARE}    —— 准备：备份节点确认预准备；</li>
 *   <li>{@link #COMMIT}     —— 提交：节点在本地满足 prepared 条件后广播；</li>
 *   <li>{@link #VIEW_CHANGE}—— 视图更换：指控主节点失效，推选新视图；</li>
 *   <li>{@link #NEW_VIEW}   —— 新视图：新主节点宣布视图成立并重发未决预准备。</li>
 * </ul>
 */
public enum MessageType {
    REQUEST,
    PRE_PREPARE,
    PREPARE,
    COMMIT,
    VIEW_CHANGE,
    NEW_VIEW
}
