package org.ndroi.easy163.vpn.hookhttp;

import org.ndroi.easy163.vpn.bio.BioTcpHandler;
import org.ndroi.easy163.vpn.block.BlockHttp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 钩子管理器（单例），维护 HTTP 会话与钩子列表，协调请求/响应的拦截与修改
 *
 * @author ndroi
 */
public class HookHttp
{
    private static HookHttp instance = new HookHttp();

    /**
     * 获取单例实例
     *
     * @return HookHttp 单例对象
     */
    public static HookHttp getInstance()
    {
        return instance;
    }

    /**
     * HTTP 会话封装，关联 TCP 隧道、请求、响应和匹配的钩子
     *
     * @author ndroi
     */
    class Session
    {
        /** 关联的 TCP 隧道 */
        public BioTcpHandler.TcpTunnel tcpTunnel;
        /** 当前会话的 HTTP 请求 */
        public Request request = new Request();
        /** 当前会话的 HTTP 响应 */
        public Response response = new Response();
        /** 匹配到的钩子，未匹配时为 null */
        public Hook hook = null;

        /**
         * 构造会话，绑定到指定的 TCP 隧道
         *
         * @param tcpTunnel 关联的 TCP 隧道
         */
        public Session(BioTcpHandler.TcpTunnel tcpTunnel)
        {
            this.tcpTunnel = tcpTunnel;
        }
    }

    private List<Hook> hooks = new ArrayList<>();
    private Map<BioTcpHandler.TcpTunnel, Session> sessions = new HashMap();

    /**
     * 遍历钩子列表，查找第一个匹配请求的钩子
     *
     * @param request 待匹配的 HTTP 请求
     * @return 匹配的钩子，未找到则返回 null
     */
    private Hook findHook(Request request)
    {
        for (Hook hook : hooks)
        {
            if (hook.rule(request))
            {
                return hook;
            }
        }
        return null;
    }

    /**
     * 注册一个新的 HTTP 钩子
     *
     * @param hook 待注册的钩子实例
     */
    public void addHook(Hook hook)
    {
        hooks.add(hook);
    }

    /**
     * 检查并拦截 HTTP 请求，匹配钩子后执行请求修改
     *
     * @param tcpTunnel  请求所属的 TCP 隧道
     * @param byteBuffer 请求的原始字节缓冲区
     * @return 处理后的字节缓冲区，若请求未完成则原样返回
     */
    public ByteBuffer checkAndHookRequest(BioTcpHandler.TcpTunnel tcpTunnel, ByteBuffer byteBuffer)
    {
        Session session = sessions.get(tcpTunnel);
        if (session == null)
        {
            session = new Session(tcpTunnel);
            sessions.put(tcpTunnel, session);
        }
        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        session.request.putBytes(bytes);
        if (session.request.finished())
        {
            if (BlockHttp.getInstance().check(session.request))
            {
                sessions.remove(session.tcpTunnel);
                return byteBuffer;
            }
            session.hook = findHook(session.request);
            if (session.hook != null)
            {
                session.hook.hookRequest(session.request);
            } else
            {
                sessions.remove(session.tcpTunnel);
            }
            byte[] requestBytes = session.request.dump();
            byteBuffer = ByteBuffer.wrap(requestBytes);
        }
        return byteBuffer;
    }

    /**
     * 检查并拦截 HTTP 响应，匹配钩子后执行响应修改
     *
     * @param tcpTunnel  响应所属的 TCP 隧道
     * @param byteBuffer 响应的原始字节缓冲区
     * @return 处理后的字节缓冲区，若无关联会话或响应未完成则原样返回
     */
    public ByteBuffer checkAndHookResponse(BioTcpHandler.TcpTunnel tcpTunnel, ByteBuffer byteBuffer)
    {
        Session session = sessions.get(tcpTunnel);
        if (session == null)
        {
            return byteBuffer;
        }
        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        session.response.putBytes(bytes);
        if (session.response.finished())
        {
            session.hook.hookResponse(session.response);
            byte[] responseBytes = session.response.dump();
            sessions.remove(session.tcpTunnel);
            byteBuffer = ByteBuffer.wrap(responseBytes);
        }
        return byteBuffer;
    }
}
