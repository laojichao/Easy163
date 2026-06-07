package org.ndroi.easy163.core;

import com.alibaba.fastjson.JSONObject;
import org.ndroi.easy163.utils.ReadStream;
import org.ndroi.easy163.utils.Keyword;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 网易云音乐歌曲关键字查找器。
 * <p>
 * 根据网易云音乐歌曲 ID，通过网易云 API 获取歌曲名称与歌手信息，
 * 封装为 {@link Keyword} 对象供后续搜索使用。
 * </p>
 *
 * @author ndroi
 * @see Keyword
 * @see Search
 */
public class Find
{
    /**
     * 根据网易云音乐歌曲 ID 查询歌曲关键字信息。
     *
     * @param id 网易云音乐歌曲 ID
     * @return 包含歌曲名称和歌手列表的 {@link Keyword} 对象；查询失败时返回 {@code null}
     */
    public static Keyword find(String id)
    {
        Keyword keyword = null;
        String url = "http://music.163.com/api/song/detail?ids=[" + id + "]";
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
                JSONObject songObj = jsonObject.getJSONArray("songs")
                        .getJSONObject(0);
                keyword = new Keyword();
                keyword.id = id;
                keyword.applyRawSongName(songObj.getString("name"));
                for (Object singerObj : songObj.getJSONArray("artists"))
                {
                    JSONObject singer = (JSONObject) singerObj;
                    keyword.singers.add(singer.getString("name"));
                }
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        return keyword;
    }
}
