package org.ndroi.easy163.core;

import org.ndroi.easy163.hooks.CollectHook;
import org.ndroi.easy163.hooks.DownloadHook;
import org.ndroi.easy163.hooks.PlaylistHook;
import org.ndroi.easy163.hooks.SongPlayHook;
import org.ndroi.easy163.vpn.block.BlockHttp;
import org.ndroi.easy163.vpn.block.BlockHttps;
import org.ndroi.easy163.vpn.hookhttp.HookHttp;

/**
 * 核心服务管理器，负责注册各类 HTTP/HTTPS Hook 和域名拦截规则。
 * <p>
 * 采用单例模式，通过 {@link #start()} 初始化播放列表 Hook、歌曲播放 Hook、
 * 收藏 Hook、下载 Hook，以及网易云音乐相关域名的拦截配置。
 * </p>
 *
 * @author ndroi
 * @see org.ndroi.easy163.hooks.PlaylistHook
 * @see org.ndroi.easy163.hooks.SongPlayHook
 * @see org.ndroi.easy163.vpn.block.BlockHttp
 * @see org.ndroi.easy163.vpn.block.BlockHttps
 */
public class Server
{
    private static Server instance = new Server();

    /**
     * 获取 Server 单例实例。
     *
     * @return Server 单例对象
     */
    public static Server getInstance()
    {
        return instance;
    }

    private void setHooks()
    {
        HookHttp.getInstance().addHook(new PlaylistHook());
        HookHttp.getInstance().addHook(new SongPlayHook());
        HookHttp.getInstance().addHook(new CollectHook());
        HookHttp.getInstance().addHook(new DownloadHook());
    }

    private void setHttpsBlock()
    {
        /*not used yet*/
        BlockHttps.getInstance().addHost("music.163.com");
        BlockHttps.getInstance().addHost("interface3.music.163.com");
        BlockHttps.getInstance().addHost("interface.music.163.com");
        BlockHttps.getInstance().addHost("apm.music.163.com");
        BlockHttps.getInstance().addHost("apm3.music.163.com");
        BlockHttps.getInstance().addHost("clientlog3.music.163.com");
        BlockHttps.getInstance().addHost("clientlog.music.163.com");
    }

    private void setHttpBlock()
    {
        BlockHttp.getInstance().addHost("apm.music.163.com");
        BlockHttp.getInstance().addHost("apm3.music.163.com");
        BlockHttp.getInstance().addHost("clientlog3.music.163.com");
        BlockHttp.getInstance().addHost("clientlog.music.163.com");
    }

    /**
     * 启动核心服务，注册所有 Hook 和域名拦截规则。
     * <p>
     * 依次设置 HTTP Hook（播放列表、歌曲播放、收藏、下载）、
     * HTTPS 拦截域名列表和 HTTP 拦截域名列表。
     * </p>
     */
    public void start()
    {
        setHooks();
        setHttpsBlock();
        setHttpBlock();
    }
}
