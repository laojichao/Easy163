package org.ndroi.easy163.vpn.block;

import android.util.Log;

import org.ndroi.easy163.vpn.hookhttp.Request;

import java.util.HashSet;
import java.util.Set;

/**
 * HTTP 请求拦截器（单例），根据域名黑名单拦截指定 HTTP 请求。
 * 用于拦截网易云音乐的广告和验证等 HTTP 请求。
 *
 * @author ndroi
 */
public class BlockHttp
{
    private static BlockHttp instance = new BlockHttp();

    /**
     * 获取单例实例
     *
     * @return BlockHttp 单例
     */
    public static BlockHttp getInstance()
    {
        return instance;
    }

    private Set<String> hosts = new HashSet<>();

    /**
     * 添加需要拦截的域名到黑名单
     *
     * @param host 需要拦截的域名
     */
    public void addHost(String host)
    {
        hosts.add(host);
    }

    /**
     * 检查请求的 Host 是否在拦截列表中
     *
     * @param request 待检查的 HTTP 请求
     * @return 如果 Host 在黑名单中则返回 true
     */
    public boolean check(Request request)
    {
        String host = request.getHeaderFields().get("Host");
        if(hosts.contains(host))
        {
            Log.d("BlockHttp", host);
            return true;
        }
        return false;
    }
}
