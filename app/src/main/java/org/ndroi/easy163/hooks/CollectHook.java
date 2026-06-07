package org.ndroi.easy163.hooks;

import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;

import org.ndroi.easy163.utils.Crypto;
import org.ndroi.easy163.vpn.hookhttp.Request;
import org.ndroi.easy163.vpn.hookhttp.Response;

/**
 * 收藏歌单操作的 HTTP 钩子。
 * 拦截网易云音乐的歌曲收藏/取消收藏请求，修改请求参数并使响应始终返回成功，
 * 绕过因版权或付费限制导致的收藏失败问题。
 *
 * @author ndroi
 */
public class CollectHook extends BaseHook
{
    /**
     * 判断请求是否为歌单收藏操作接口。
     * 匹配条件：POST 方法、Host 以 music.163.com 结尾、路径以 /playlist/manipulate/tracks 结尾。
     *
     * @param request 当前 HTTP 请求对象
     * @return 如果是歌单收藏操作接口则返回 {@code true}，否则返回 {@code false}
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
        return path.endsWith("/playlist/manipulate/tracks");
    }

    /**
     * 修改收藏请求参数。
     * 解密请求体，提取 trackIds、pid、op 参数，构造明文 POST 数据，
     * 并将请求 URI 重定向到 /api/playlist/manipulate/tracks 接口。
     *
     * @param request 当前 HTTP 请求对象
     */
    public void hookRequest(Request request)
    {
        super.hookRequest(request);
        Crypto.Request cryptoRequest = Crypto.decryptRequestBody(new String(request.getContent()));
        String trackId = cryptoRequest.json.getString("trackIds");
        trackId = trackId.substring(2, trackId.length() - 2);
        String pid = cryptoRequest.json.getString("pid");
        String op = cryptoRequest.json.getString("op");
        String postData = "trackIds=[" + trackId + "," + trackId +"]&pid=" + pid + "&op=" + op;
        request.setUri("http://music.163.com/api/playlist/manipulate/tracks");
        request.setContent(postData.getBytes());
    }

    /**
     * 修改收藏响应，使操作始终返回成功。
     * 解析响应 JSON，将 code 设为 200 并移除 message；
     * 若缺少 trackIds 字段则填充默认值，确保客户端认为收藏操作成功。
     *
     * @param response 当前 HTTP 响应对象
     */
    @Override
    public void hookResponse(Response response)
    {
        super.hookResponse(response);
        byte[] bytes = response.getContent();
        JSONObject jsonObject = JSONObject.parseObject(new String(bytes));
        jsonObject.put("code", 200);
        jsonObject.remove("message");
        if (jsonObject.getString("trackIds") == null)
        {
            jsonObject.put("trackIds", "[999999]");
            jsonObject.put("count", 999);
            jsonObject.put("cloudCount", 0);
        }
        bytes = JSONObject.toJSONString(jsonObject, SerializerFeature.WriteMapNullValue).getBytes();
        bytes = Crypto.aesEncrypt(bytes);
        response.setContent(bytes);
    }
}