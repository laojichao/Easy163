package org.ndroi.easy163.providers;

import android.util.Log;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import org.ndroi.easy163.core.Local;
import org.ndroi.easy163.utils.ReadStream;
import org.ndroi.easy163.utils.Keyword;
import org.ndroi.easy163.utils.Song;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;

/**
 * 酷我音乐音源提供者，通过酷我音乐 API 搜索并获取歌曲播放地址。
 * <p>
 * 搜索接口返回候选列表后，通过 antiServer 接口将 musicrid 转换为实际播放 URL。
 *
 * @author ndroi
 */
public class KuwoMusic extends Provider
{
    /**
     * 构造酷我音乐提供者实例。
     *
     * @param targetKeyword 目标歌曲关键字
     */
    public KuwoMusic(Keyword targetKeyword)
    {
        super("kuwo", targetKeyword);
    }

    /**
     * 通过酷我搜索 API 收集候选关键字。
     * <p>
     * 设置 Referer、csrf 和 Cookie 头以通过酷我的 CSRF 验证，
     * 解析搜索结果中的歌曲名和歌手名填充候选列表。
     */
    @Override
    public void collectCandidateKeywords()
    {
        String query = keyword2Query(targetKeyword);
        String token = "1234567890";
        String url = "http://www.kuwo.cn/api/www/search/searchMusicBykeyWord?key=" +
                query + "&pn=1&rn=30";
        try
        {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Referer", "http://kuwo.cn/search/list?key=" + query);
            connection.setRequestProperty("csrf", token);
            connection.setRequestProperty("Cookie", "kw_token=" + token);
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK)
            {
                byte[] content = ReadStream.read(connection.getInputStream());
                String str = new String(content);
                JSONObject jsonObject = JSONObject.parseObject(str);
                if (jsonObject.getIntValue("code") == 200)
                {
                    JSONArray candidates = jsonObject.getJSONObject("data").getJSONArray("list");
                    for (Object obj : candidates)
                    {
                        JSONObject songJsonObject = (JSONObject) obj;
                        Keyword candidateKeyword = new Keyword();
                        candidateKeyword.songName = songJsonObject.getString("name");
                        candidateKeyword.singers = Arrays.asList(songJsonObject.getString("artist").split("&"));
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
     * 从候选 JSON 中提取 musicrid，通过 antiServer 接口转换为播放 URL。
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
        String mId = songJsonObject.getString("musicrid");
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("mid", mId);
        Song song = fetchSongByJson(jsonObject);
        if(song != null)
        {
            Local.put(targetKeyword.id, providerName, jsonObject);
        }
        return song;
    }

    /**
     * 根据音乐 ID 通过酷我 antiServer 接口获取播放地址。
     * <p>
     * 将 musicrid 转换为 MP3 格式的实际播放 URL，返回的 URL 需以 "http" 开头才有效。
     *
     * @param jsonObject 包含 "mid"（musicrid）的 JSON 对象
     * @return 包含播放信息的 {@link Song} 对象，获取失败时返回 {@code null}
     */
    @Override
    public Song fetchSongByJson(JSONObject jsonObject)
    {
        String mId = jsonObject.getString("mid");
        if(mId == null)
        {
            return null;
        }
        Song song = null;
        String url = "http://antiserver.kuwo.cn/anti.s?type=convert_url&format=mp3&response=url&rid=" + mId;
        try
        {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK)
            {
                byte[] content = ReadStream.read(connection.getInputStream());
                String songUrl = new String(content);
                Log.d("Kuwo", songUrl);
                if (songUrl.startsWith("http"))
                {
                    song = generateSong(songUrl);
                }
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        return song;
    }
}
