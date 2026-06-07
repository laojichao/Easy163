package org.ndroi.easy163.vpn.config;

/**
 * VPN 运行时配置，包含日志开关和 DNS 服务器地址等全局配置项。
 *
 * @author ndroi
 */
public class Config
{
    /** 是否启用读写日志 */
    public static boolean logRW = false;
    /** 是否启用 ACK 日志 */
    public static boolean logAck = false;
    /** DNS 服务器地址 */
    public static String dns = "114.114.114.114";
}

