package org.ndroi.easy163.vpn.tcpip;

/**
 * TCP 连接状态枚举，对应 TCP 状态机的各个阶段
 *
 * @author ndroi
 */
public enum TCBStatus
{
    /** 已发送 SYN 连接请求 */
    SYN_SENT,
    /** 已收到对端 SYN */
    SYN_RECEIVED,
    /** 连接已建立 */
    ESTABLISHED,
    /** 被动关闭等待 */
    CLOSE_WAIT,
    /** 最后确认等待 */
    LAST_ACK,
    /** 连接已关闭 */
    //new
    CLOSED,
}
