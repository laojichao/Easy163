package org.ndroi.easy163.vpn.block;

import java.util.HashSet;
import java.util.Set;

/**
 * HTTPS 请求拦截器（单例），预留用于 HTTPS 流量拦截，当前未实现具体拦截逻辑。
 * TODO: 实现 HTTP-DNS 拦截
 *
 * @author ndroi
 */
public class BlockHttps
{
    private static BlockHttps instance = new BlockHttps();

    /**
     * 获取单例实例
     *
     * @return BlockHttps 单例
     */
    public static BlockHttps getInstance()
    {
        return instance;
    }

    private Set<String> hosts = new HashSet<>();

    /**
     * 添加需要拦截的域名
     *
     * @param host 需要拦截的域名
     */
    public void addHost(String host)
    {
        hosts.add(host);
    }

    /**
     * 检查 IP 是否需要拦截（当前固定返回 false，尚未实现具体拦截逻辑）
     *
     * @param ip 待检查的目标 IP 地址
     * @return 当前始终返回 false
     */
    public boolean check(String ip)
    {
        return false;
    }
}
