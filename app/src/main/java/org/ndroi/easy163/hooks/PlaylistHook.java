package org.ndroi.easy163.hooks;

import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;

import org.ndroi.easy163.core.Cache;
import org.ndroi.easy163.hooks.utils.JsonUtil;
import org.ndroi.easy163.utils.Crypto;
import org.ndroi.easy163.utils.Keyword;
import org.ndroi.easy163.vpn.hookhttp.Request;
import org.ndroi.easy163.vpn.hookhttp.Response;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;

/**
 * 歌单/搜索结果接口的 HTTP 钩子。
 * 拦截网易云音乐的歌单详情、搜索结果等接口响应，缓存歌曲关键字信息
 * （歌曲名、歌手名）用于后续音源匹配，并修改付费权限字段解锁播放和下载。
 *
 * @author ndroi
 */
public class PlaylistHook extends BaseHook
{
    /**
     * 需要拦截的请求路径列表，涵盖歌单详情、专辑、搜索、播放记录等接口。
     */
    private List<String> paths = Arrays.asList(
            "/playlist/detail",
            "/eapi/playlist/v4/detail",
            "/eapi/play-record/playlist/list",
            "/eapi/album/v3/detail",
            "/discovery/recommend/songs",
            "/eapi/album/privilege",
            "/eapi/playlist/privilege",
            "/eapi/batch",
            "/artist/privilege",
            "/eapi/artist/top/song",
            "/eapi/v3/song/detail",
            "/eapi/song/enhance/privilege",
            "/eapi/song/enhance/info/get",
            "/search/song/get",
            "/search/complex/get/",
            "/eapi/v1/artist/songs",
            "/eapi/v1/search/get"
    );

    /**
     * 判断请求路径是否匹配已知的歌单/搜索接口列表。
     * 匹配条件：POST 方法、Host 以 music.163.com 结尾、路径包含列表中任一路径片段。
     *
     * @param request 当前 HTTP 请求对象
     * @return 如果路径匹配则返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean rule(Request request)
    {
        String method = request.getMethod();
        String host = request.getHeaderFields().get("Host");
        Log.d("check rule", host + "" + getPath(request));
        if (!method.equals("POST") || !host.endsWith("music.163.com"))
        {
            return false;
        }
        String path = getPath(request);
        for (String p : paths)
        {
            if (path.contains(p))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 钩子响应处理方法。
     * 解密响应体后依次执行关键字缓存和权限修改，然后重新加密响应。
     *
     * @param response 当前 HTTP 响应对象
     */
    @Override
    public void hookResponse(Response response)
    {
        super.hookResponse(response);
        byte[] bytes = Crypto.aesDecrypt(response.getContent());
        JSONObject jsonObject = JSONObject.parseObject(new String(bytes));
        cacheKeywords(jsonObject);
        modifyPrivileges(jsonObject);
        bytes = JSONObject.toJSONString(jsonObject, SerializerFeature.WriteMapNullValue).getBytes();
        bytes = Crypto.aesEncrypt(bytes);
        response.setContent(bytes);
    }

    /**
     * 遍历 JSON 缓存歌曲关键字到 {@link Cache#neteaseKeywords}。
     * 查找同时包含 id、name、ar 字段的 JSONObject 节点，
     * 提取歌曲 ID、歌曲名和歌手列表，构建 {@link Keyword} 对象并缓存。
     *
     * @param jsonObject 解密后的响应 JSON 对象
     */
    private void cacheKeywords(JSONObject jsonObject)
    {
        JsonUtil.traverse(jsonObject, new JsonUtil.Rule()
        {
            @Override
            public void apply(JSONObject object)
            {
                if (object.containsKey("id") &&
                        object.containsKey("name") &&
                        object.containsKey("ar"))
                {
                    String songId = object.getString("id");
                    Keyword keyword = new Keyword();
                    keyword.id = songId;
                    keyword.applyRawSongName(object.getString("name"));
                    for (Object singerObj : object.getJSONArray("ar"))
                    {
                        JSONObject singer = (JSONObject) singerObj;
                        keyword.singers.add(singer.getString("name"));
                    }
                    Cache.neteaseKeywords.add(songId, keyword);
                }
            }
        });
    }

    /**
     * 遍历 JSON 将付费字段置零，解锁播放和下载权限。
     * 将 fee 设为 0 解除付费标记；将 st、subp、pl、dl 等权限字段
     * 修改为可播放/可下载状态（pl 和 dl 为 0 时设为 320000）。
     *
     * @param jsonObject 解密后的响应 JSON 对象
     */
    private void modifyPrivileges(JSONObject jsonObject)
    {
        JsonUtil.traverse(jsonObject, new JsonUtil.Rule()
        {
            @Override
            public void apply(JSONObject object)
            {
                if(object.containsKey("fee"))
                {
                    object.put("fee", 0);
                }
                if (object.containsKey("st") &&
                        object.containsKey("subp") &&
                        object.containsKey("pl") &&
                        object.containsKey("dl"))
                {
                    object.put("st", 0);
                    object.put("subp", 1);
                    if (object.getIntValue("pl") == 0)
                    {
                        object.put("pl", 320000);
                    }
                    if (object.getIntValue("dl") == 0)
                    {
                        object.put("dl", 320000);
                    }
                }
            }
        });
    }
}
