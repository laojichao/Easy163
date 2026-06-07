package org.ndroi.easy163.providers;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.ndroi.easy163.core.Local;
import org.ndroi.easy163.utils.ReadStream;
import org.ndroi.easy163.utils.Keyword;
import org.ndroi.easy163.utils.Song;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * QQ 音乐音源提供者，通过 QQ 音乐 API 搜索并获取歌曲播放地址。
 * <p>
 * 搜索接口返回候选列表后，通过 vkey 机制获取实际播放 URL，自动跳过付费歌曲和不可用歌曲。
 *
 * @author ndroi
 */
public class QQMusic extends Provider
{
    /**
     * 构造 QQ 音乐提供者实例。
     *
     * @param targetKeyword 目标歌曲关键字
     */
    public QQMusic(Keyword targetKeyword)
    {
        super("qq", targetKeyword);
    }

    /**
     * 通过 QQ 音乐搜索 API 收集候选关键字。
     * <p>
     * 解析搜索结果，自动跳过付费歌曲（pay_play != 0）、不可用歌曲（fnote == 4002）
     * 以及无有效音频文件的歌曲。
     */
    @Override
    public void collectCandidateKeywords()
    {
        String query = keyword2Query(targetKeyword);
        String url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?" +
                "ct=24&qqmusic_ver=1298&new_json=1&remoteplace=txt.yqq.center&" +
                "searchid=46343560494538174&t=0&aggr=1&cr=1&catZhida=1&lossless=0&" +
                "flag_qc=0&p=1&n=10&w=" + query + "&" +
                "g_tk_new_20200303=5381&g_tk=5381&loginUin=0&hostUin=0&" +
                "format=json&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0";
        try
        {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK)
            {
                byte[] content = ReadStream.read(connection.getInputStream());
                String str = new String(content);
                JSONObject jsonObject = JSONObject.parseObject(str);
                if (jsonObject.getIntValue("code") == 0)
                {
                    JSONArray candidates = jsonObject.getJSONObject("data")
                            .getJSONObject("song")
                            .getJSONArray("list");
                    for (Object infoObj : candidates)
                    {
                        JSONObject songJsonObject = (JSONObject) infoObj;
                        int pay = songJsonObject.getJSONObject("pay").getIntValue("pay_play");
                        if (pay != 0)
                        {
                            continue;
                        }
                        int fnote = songJsonObject.getIntValue("fnote");
                        if (fnote == 4002)
                        {
                            continue;
                        }
                        JSONObject files = songJsonObject.getJSONObject("file");
                        if(files.getIntValue("size_128") == 0 && files.getIntValue("size_320") == 0)
                        {
                            continue;
                        }
                        String songName = songJsonObject.getString("title");
                        Keyword candidateKeyword = new Keyword();
                        candidateKeyword.songName = songName;
                        JSONArray singersObj = songJsonObject.getJSONArray("singer");
                        for (Object singerObj : singersObj)
                        {
                            String singer = ((JSONObject) singerObj).getString("name");
                            candidateKeyword.singers.add(singer);
                        }
                        songJsonObjects.add(songJsonObject);
                        candidateKeywords.add(candidateKeyword);
                    }
                }
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * 获取选中歌曲的播放信息并缓存到本地。
     * <p>
     * 从候选 JSON 中提取 mid 和 media_mid，通过 vkey 机制获取实际播放 URL。
     *
     * @return 包含播放信息的 {@link Song} 对象，获取失败时返回 {@code null}
     */
    @Override
    public Song fetchSelectedSong()
    {
        if(selectedIndex == -1)
        {
            return null;
        }
        JSONObject songJsonObject = songJsonObjects.get(selectedIndex);
        String mId = songJsonObject.getString("mid");
        String mediaMId = songJsonObject.getJSONObject("file").getString("media_mid");
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("mid", mId);
        jsonObject.put("media_mid", mediaMId);
        Song song = fetchSongByJson(jsonObject);
        if(song != null)
        {
            Local.put(targetKeyword.id, providerName, jsonObject);
        }
        return song;
    }

    /**
     * 根据音乐 ID 通过 QQ 音乐 vkey 机制获取播放地址。
     * <p>
     * 使用 mid 和 media_mid 构建文件名，通过 musicu.fcg 接口获取 vkey，
     * 拼接完整的 CDN 播放 URL 后调用 {@code generateSong} 获取元信息。
     *
     * @param jsonObject 包含 "mid" 和 "media_mid" 的 JSON 对象
     * @return 包含播放信息的 {@link Song} 对象，获取失败时返回 {@code null}
     */
    @Override
    public Song fetchSongByJson(JSONObject jsonObject)
    {
        String mId = jsonObject.getString("mid");
        String mediaMId = jsonObject.getString("media_mid");
        if(mId == null || mediaMId == null)
        {
            return null;
        }
        String filename = "M500" + mediaMId + ".mp3";
        String url = "https://u.y.qq.com/cgi-bin/musicu.fcg?data=" +
                "{\"req_0\":{\"module\":\"vkey.GetVkeyServer\"," +
                "\"method\":\"CgiGetVkey\",\"param\":{\"guid\":\"7332953645\"," +
                "\"loginflag\":1,\"filename\":[\"" +
                filename +
                "\"],\"songmid\":[\"" +
                mId +
                "\"],\"songtype\":[0],\"uin\":\"0\",\"platform\":\"20\"}}}";
        Song song = null;
        try
        {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK)
            {
                byte[] content = ReadStream.read(connection.getInputStream());
                String str = new String(content);
                JSONObject jo = JSONObject.parseObject(str);
                if (jo.getIntValue("code") == 0)
                {
                    String vkey = jo.getJSONObject("req_0")
                            .getJSONObject("data")
                            .getJSONArray("midurlinfo")
                            .getJSONObject(0)
                            .getString("vkey");
                    if (!vkey.isEmpty())
                    {
                        String songUrl = "http://dl.stream.qqmusic.qq.com/" + filename +
                                "?vkey=" + vkey + "&uin=0&fromtag=8&guid=7332953645";
                        song = generateSong(songUrl);
                    }
                }
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        return song;
    }
}