package org.ndroi.easy163.hooks;

import org.ndroi.easy163.vpn.hookhttp.Hook;
import org.ndroi.easy163.vpn.hookhttp.Request;

/**
 * 网易云音乐 HTTP 请求钩子基类。
 * 移除请求头中的重试标记（X-NAPM-RETRY），绕过网易云音乐的重试机制。
 * 所有具体钩子类均继承此类。
 *
 * @author ndroi
 */
public abstract class BaseHook extends Hook
{
    /**
     * 钩子请求处理方法。
     * 移除请求头中的 {@code X-NAPM-RETRY} 字段，防止网易云音乐客户端
     * 因 VPN 代理修改响应后触发重试逻辑。
     *
     * @param request 当前 HTTP 请求对象
     */
    @Override
    public void hookRequest(Request request)
    {
        request.getHeaderFields().remove("X-NAPM-RETRY");
    }
}