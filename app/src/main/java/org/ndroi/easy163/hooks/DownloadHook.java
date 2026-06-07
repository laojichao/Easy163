package org.ndroi.easy163.hooks;

import android.util.Log;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;

import org.ndroi.easy163.core.Cache;
import org.ndroi.easy163.utils.Crypto;
import org.ndroi.easy163.utils.Song;
import org.ndroi.easy163.vpn.hookhttp.Request;
import org.ndroi.easy163.vpn.hookhttp.Response;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 歌曲下载接口的 HTTP 钩子。
 * 将网易云音乐的下载请求重定向为播放请求，并在响应中将付费歌曲的
 * 下载 URL 替换为第三方音源，同时计算音频文件的 MD5 校验值。
 *
 * @author ndroi
 */
public class DownloadHook extends BaseHook
{
    /**
     * 判断请求是否为歌曲下载接口。
     * 匹配条件：POST 方法、Host 以 music.163.com 结尾、路径以 /song/enhance/download/url 结尾。
     *
     * @param request 当前 HTTP 请求对象
     * @return 如果是下载接口请求则返回 {@code true}，否则返回 {@code false}
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
        return path.endsWith("/song/enhance/download/url");
    }

    /**
     * 将下载请求转换为播放请求。
     * 解密请求体，将路径从 download/url 改为 player/url，
     * 并将 id 参数转换为 ids 数组格式，使请求指向播放接口。
     *
     * @param request 当前 HTTP 请求对象
     */
    @Override
    public void hookRequest(Request request)
    {
        super.hookRequest(request);
        Crypto.Request cryptoRequest = Crypto.decryptRequestBody(new String(request.getContent()));
        cryptoRequest.path = "/api/song/enhance/player/url";
        String id = cryptoRequest.json.getString("id");
        cryptoRequest.json.put("ids", "[\"" + id + "\"]");
        cryptoRequest.json.remove("id");
        byte[] bytes = Crypto.encryptRequestBody(cryptoRequest).getBytes();
        request.setUri("http://music.163.com/eapi/song/enhance/player/url");
        request.setContent(bytes);
    }

    /**
     * 预下载音频文件并计算 MD5 校验值。
     * 通过 HTTP GET 请求下载指定 URL 的音频内容，使用 MD5 消息摘要算法
     * 逐块计算哈希值，用于替换下载响应中的文件校验信息。
     *
     * @param url 第三方音源的下载 URL
     * @return 音频文件的 MD5 哈希字符串（小写十六进制），下载失败时返回空字符串
     */
    private String preDownloadForMd5(String url)
    {
        String md5 = "";
        try
        {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK)
            {
                MessageDigest messageDigest = MessageDigest.getInstance("md5");
                InputStream inputStream = connection.getInputStream();
                byte[] bytes = new byte[4096*2];
                while (true)
                {
                    int readLen = inputStream.read(bytes);
                    if (readLen == -1)
                    {
                        break;
                    }
                    messageDigest.update(bytes, 0, readLen);
                }
                for (byte b : messageDigest.digest())
                {
                    String temp = Integer.toHexString(b & 0xff);
                    if (temp.length() == 1)
                    {
                        temp = "0" + temp;
                    }
                    md5 += temp;
                }
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e)
        {
            e.printStackTrace();
        }
        return md5;
    }

    /**
     * 处理下载响应，替换付费歌曲的音源信息。
     * 检测歌曲是否需要付费或试听受限（url 为空、code 非 200、存在 freeTrialInfo），
     * 若是则从缓存中查找第三方音源并替换 url、md5、br、size 等字段，
     * 同时清除付费标记和试听限制信息。
     *
     * @param jsonObject 解密后的响应 JSON 对象，包含歌曲下载信息
     */
    private void handleDownload(JSONObject jsonObject)
    {
        JSONObject songObject = null;
        Object object = jsonObject.get("data");
        if (object.getClass().equals(JSONArray.class))
        {
            songObject = ((JSONArray) object).getJSONObject(0);
            jsonObject.put("data", songObject);
        } else
        {
            songObject = (JSONObject) object;
        }
        if (songObject.getString("url") == null ||
                songObject.getIntValue("code") != 200 ||
                songObject.getJSONObject("freeTrialInfo") != null)
        {
            String id = songObject.getString("id");
            Song providerSong = (Song) Cache.providerSongs.get(id);
            if(providerSong == null)
            {
                Log.d("DownloadHook", "no provider found");
                return;
            }
            if (providerSong.md5.equals("unknown"))
            {
                providerSong.md5 = preDownloadForMd5(providerSong.url);
            }
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
        songObject.put("fee", 0);
        songObject.put("flag", 0);
    }

    /**
     * 钩子响应处理方法。
     * 解密响应体，调用 {@link #handleDownload} 替换第三方音源，
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
        handleDownload(jsonObject);
        bytes = JSONObject.toJSONString(jsonObject, SerializerFeature.WriteMapNullValue).getBytes();
        bytes = Crypto.aesEncrypt(bytes);
        response.setContent(bytes);
    }
}
