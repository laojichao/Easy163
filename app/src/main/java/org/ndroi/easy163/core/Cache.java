package org.ndroi.easy163.core;

import org.ndroi.easy163.utils.Keyword;
import org.ndroi.easy163.utils.Song;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用缓存管理器，基于 {@link LinkedHashMap} 实现带懒加载策略的键值缓存。
 * <p>
 * 当缓存未命中时，可通过注册的 {@link AddAction} 回调自动从外部数据源获取并填充缓存项。
 * 本类同时维护网易云音乐关键字缓存与第三方音源匹配结果缓存两个静态实例。
 * </p>
 *
 * @author ndroi
 * @see Find
 * @see Search
 */
public class Cache
{
    /**
     * 缓存项懒加载回调接口。
     * <p>当缓存未命中时，由该接口的实现负责从外部数据源获取对应值。</p>
     */
    interface AddAction
    {
        /**
         * 根据指定 ID 从外部数据源加载对应的缓存值。
         *
         * @param id 缓存键，通常为网易云音乐歌曲 ID
         * @return 加载成功时返回缓存值，加载失败返回 {@code null}
         */
        Object add(String id);
    }

    private Map<String, Object> items = new LinkedHashMap<>();
    private AddAction addAction;

    /**
     * 构造一个缓存实例。
     *
     * @param addAction 缓存未命中时的懒加载回调，可为 {@code null} 表示不启用自动加载
     */
    public Cache(AddAction addAction)
    {
        this.addAction = addAction;
    }

    /**
     * 将指定键值对存入缓存。此方法线程安全。
     *
     * @param id    缓存键
     * @param value 缓存值
     */
    public void add(String id, Object value)
    {
        synchronized (items)
        {
            items.put(id, value);
        }
    }

    /**
     * 获取缓存值。若缓存未命中且已注册 {@link AddAction}，则自动调用回调加载并缓存结果。
     * 此方法线程安全。
     *
     * @param id 缓存键
     * @return 缓存值，未命中且无法加载时返回 {@code null}
     */
    public Object get(String id)
    {
        synchronized (items)
        {
            if (items.containsKey(id))
            {
                Object value = items.get(id);
                return value;
            }
            if (addAction == null)
            {
                return null;
            }
            Object value = addAction.add(id);
            if (value != null)
            {
                add(id, value);
            }
            return value;
        }
    }

    /** 网易云音乐关键字缓存：键为歌曲 ID，值为对应的 {@link Keyword} 对象 */
    public static Cache neteaseKeywords = null;

    /** 第三方音源匹配结果缓存：键为歌曲 ID，值为对应的 {@link Song} 对象 */
    public static Cache providerSongs = null;

    /**
     * 初始化所有静态缓存实例，配置各自的懒加载回调逻辑。
     * <p>
     * {@code neteaseKeywords} 通过 {@link Find#find(String)} 从网易云服务器获取关键字；
     * {@code providerSongs} 先尝试本地缓存 {@link Local}，未命中则走全网搜索流程。
     * </p>
     */
    public static void init()
    {
        neteaseKeywords = new Cache(new AddAction()
        {
            @Override
            public Object add(String id)
            {
                return Find.find(id);
            }
        });

        providerSongs = new Cache(new AddAction()
        {
            @Override
            public Object add(String id)
            {
                Song song = Local.get(id);
                if(song != null)
                {
                    return song;
                }
                Keyword keyword = (Keyword) neteaseKeywords.get(id);
                return Search.search(keyword);
            }
        });
    }

    /**
     * 清除第三方音源匹配结果缓存。
     */
    public static void clear()
    {
        providerSongs.items.clear();
    }
}
