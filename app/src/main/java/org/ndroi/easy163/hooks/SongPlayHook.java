package org.ndroi.easy163.hooks;

import android.util.Log;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;

import org.ndroi.easy163.core.Cache;
import org.ndroi.easy163.utils.ConcurrencyTask;
import org.ndroi.easy163.utils.Crypto;
import org.ndroi.easy163.utils.Song;
import org.ndroi.easy163.vpn.hookhttp.Request;
import org.ndroi.easy163.vpn.hookhttp.Response;

/**
 * 歌曲播放接口的 HTTP 钩子。
 * 拦截网易云音乐的歌曲播放请求，对付费或试听受限的歌曲，
 * 将播放 URL 替换为从第三方音源获取的免费音源地址。
 *
 * @author ndroi
 */
public class SongPlayHook extends BaseHook
{
    /**
     * 判断请求是否为歌曲播放接口。
     * 匹配条件：POST 方法、Host 以 music.163.com 结尾、路径包含 /song/enhance/player/url。
     *
     * @param request 当前 HTTP 请求对象
     * @return 如果是播放接口请求则返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean rule(Request request)
    {
        String method = request.getMethod();
        String host = request.getHeaderFields().get("Host");
        if (!method.equals("POST") || !host.endsWith("music.163.com"))
        {
            return false;
        }
        String path = getPath(request);
        return path.contains("/song/enhance/player/url");
    }

    /**
     * 并发处理多首歌曲，为付费或试听受限的歌曲替换第三方音源。
     * 遍历响应中的歌曲列表，对 code 非 200 或存在 freeTrialInfo 的歌曲，
     * 从 {@link Cache#providerSongs} 缓存中查找第三方音源并替换 url、md5、br、size 等字段。
     * 使用 {@link ConcurrencyTask} 并发处理以提高效率。
     *
     * @param jsonObject 解密后的响应 JSON 对象，包含歌曲播放信息列表
     */
    private void handleNoFreeSong(JSONObject jsonObject)
    {
        ConcurrencyTask concurrencyTask = new ConcurrencyTask();
        JSONArray songObjects = jsonObject.getJSONArray("data");
        for (Object obj : songObjects)
        {
            JSONObject songObject = (JSONObject) obj;
            if (songObject.getJSONObject("freeTrialInfo") != null || songObject.getIntValue("code") != 200)
            {
                concurrencyTask.addTask(new Thread()
                {
                    @Override
                    public void run()
                    {
                        super.run();
                        String id = songObject.getString("id");
                        Song providerSong = (Song) Cache.providerSongs.get(id);
                        if (providerSong == null)
                        {
                            Log.d("SongPlayHook", "no provider found");
                            return;
                        }
                        songObject.put("fee", 0);
                        songObject.put("code", 200);
                        songObject.put("url", providerSong.url);
                        songObject.put("md5", providerSong.md5);
                        songObject.put("br", providerSong.br);
                        songObject.put("size", providerSong.size);
                        songObject.put("freeTrialInfo", null);
                        songObject.put("level", "standard");
                        songObject.put("type", "mp3");
                        songObject.put("encodeType", "mp3");
                    }
                });
            }
        }
        concurrencyTask.waitAll();
    }

    /**
     * 钩子响应处理方法。
     * 解密响应体，调用 {@link #handleNoFreeSong} 替换付费歌曲的音源，
     * 然后重新加密响应内容返回给客户端。
     *
     * @param response 当前 HTTP 响应对象
     */
    @Override
    public void hookResponse(Response response)
    {
        super.hookResponse(response);
        byte[] bytes = Crypto.aesDecrypt(response.getContent());
        JSONObject jsonObject = JSONObject.parseObject(new String(bytes));
        handleNoFreeSong(jsonObject);
        bytes = JSONObject.toJSONString(jsonObject, SerializerFeature.WriteMapNullValue).getBytes();
        bytes = Crypto.aesEncrypt(bytes);
        response.setContent(bytes);
    }
}
