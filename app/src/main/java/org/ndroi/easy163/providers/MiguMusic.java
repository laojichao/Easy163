package org.ndroi.easy163.providers;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import org.ndroi.easy163.core.Local;
import org.ndroi.easy163.providers.utils.MiguCrypto;
import org.ndroi.easy163.utils.ReadStream;
import org.ndroi.easy163.utils.ConcurrencyTask;
import org.ndroi.easy163.utils.Keyword;
import org.ndroi.easy163.utils.Song;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 咪咕音乐音源提供者，通过咪咕音乐 API 搜索并获取歌曲播放地址，支持并发请求不同音质。
 * <p>
 * 搜索接口使用加密参数请求，并发获取 type=2（高品质）和 type=1（标准品质）两种音质，
 * 优先返回高品质音源。
 *
 * @author ndroi
 */
public class MiguMusic extends Provider
{
    /**
     * 构造咪咕音乐提供者实例。
     *
     * @param targetKeyword 目标歌曲关键字
     */
    public MiguMusic(Keyword targetKeyword)
    {
        super("migu", targetKeyword);
    }

    /**
     * 设置咪咕音乐 API 请求所需的 HTTP 头。
     *
     * @param connection 待设置请求头的 HTTP 连接
     */
    private void setHttpHeader(HttpURLConnection connection)
    {
        connection.setRequestProperty("origin", "https://music.migu.cn/");
        connection.setRequestProperty("referer", "https://music.migu.cn/");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/84.0.4147.89 Safari/537.36");
    }

    /**
     * 通过咪咕搜索 API 收集候选关键字。
     * <p>
     * 设置咪咕特有的 HTTP 头后请求搜索接口，解析结果中的歌曲名和歌手名填充候选列表。
     */
    @Override
    public void collectCandidateKeywords()
    {
        String query = keyword2Query(targetKeyword);
        String url = "https://m.music.migu.cn/migu/remoting/scr_search_tag?keyword=" +
                query + "&type=2&rows=20&pgc=1";
        try
        {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            setHttpHeader(connection);
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK)
            {
                byte[] content = ReadStream.read(connection.getInputStream());
                String str = new String(content);
                JSONObject jsonObject = JSONObject.parseObject(str);
                JSONArray candidates = jsonObject.getJSONArray("musics");
                if(candidates == null)
                {
                    return;
                }
                for (Object infoObj : candidates)
                {
                    JSONObject songJSONObject = (JSONObject) infoObj;
                    String songName = songJSONObject.getString("songName");
                    Keyword candidateKeyword = new Keyword();
                    candidateKeyword.songName = songName;
                    candidateKeyword.singers = Arrays.asList(songJSONObject.getString("singerName").split(", "));
                    songJsonObjects.add(songJSONObject);
                    candidateKeywords.add(candidateKeyword);
                }
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * 并发请求指定音质类型的播放地址。
     * <p>
     * 使用 {@link MiguCrypto} 加密请求参数，成功获取播放 URL 后存入 results 映射表。
     *
     * @param mId 歌曲版权 ID（copyrightId）
     * @param type 音质类型（"1" 为标准品质，"2" 为高品质）
     * @param results 线程安全的结果映射表，键为音质类型，值为播放 URL
     */
    private void requestSongUrl(String mId, String type, Map<String, String> results)
    {
        String url = "https://music.migu.cn/v3/api/music/audioPlayer/getPlayInfo?dataType=2&";
        String param = "{\"copyrightId\":\"" + mId + "\",\"type\":" + type + "}";
        String req = url + MiguCrypto.Encrypt(param);
        try
        {
            HttpURLConnection connection = (HttpURLConnection) new URL(req).openConnection();
            connection.setRequestMethod("GET");
            setHttpHeader(connection);
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK)
            {
                byte[] content = ReadStream.read(connection.getInputStream());
                String str = new String(content);
                JSONObject jsonObject = JSONObject.parseObject(str);
                String code = jsonObject.getString("returnCode");
                if (code.equals("000000"))
                {
                    String songUrl = jsonObject.getJSONObject("data").getString("playUrl");
                    if(songUrl == null || songUrl.isEmpty())
                    {
                        return;
                    }
                    if (!songUrl.startsWith("http:"))
                    {
                        songUrl = "http:" + songUrl;
                    }
                    synchronized(results)
                    {
                        results.put(type, songUrl);
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
     * 从候选 JSON 中提取 copyrightId，通过并发请求获取不同音质的播放地址。
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
        String mId = songJsonObject.getString("copyrightId");
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
     * 并发请求不同音质，优先返回高品质音源。
     * <p>
     * 同时请求 type=2（高品质）和 type=1（标准品质），优先使用 type=2 的结果。
     *
     * @param jsonObject 包含 "mid"（copyrightId）的 JSON 对象
     * @return 包含播放信息的 {@link Song} 对象，所有音质均获取失败时返回 {@code null}
     */
    @Override
    public Song fetchSongByJson(JSONObject jsonObject)
    {
        String mId = jsonObject.getString("mid");
        ConcurrencyTask concurrencyTask = new ConcurrencyTask();
        Map<String, String> typeSongUrls = new HashMap<>();
        for (String type : new String[]{"1", "2"})
        {
            concurrencyTask.addTask(new Thread(){
                @Override
                public void run()
                {
                    super.run();
                    requestSongUrl(mId, type, typeSongUrls);
                }
            });
        }
        concurrencyTask.waitAll();
        if(typeSongUrls.containsKey("2"))
        {
            return generateSong(typeSongUrls.get("2"));
        }
        if(typeSongUrls.containsKey("1"))
        {
            return generateSong(typeSongUrls.get("1"));
        }
        return null;
    }
}
