package org.ndroi.easy163.providers;

import android.util.Log;
import com.alibaba.fastjson.JSONObject;
import org.ndroi.easy163.providers.utils.BitRate;
import org.ndroi.easy163.providers.utils.KeywordMatch;
import org.ndroi.easy163.utils.ReadStream;
import org.ndroi.easy163.utils.Keyword;
import org.ndroi.easy163.utils.Song;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 第三方音乐源提供者的抽象基类，定义歌曲搜索、候选匹配与音源获取的标准流程。
 * <p>
 * 所有具体音源（酷狗、酷我、QQ 音乐等）均继承此类，并实现候选关键字收集、
 * 选中歌曲获取以及按 JSON 数据获取歌曲的抽象方法。
 *
 * @author ndroi
 */
public abstract class Provider
{
    protected String providerName;
    protected Keyword targetKeyword;
    protected int selectedIndex = -1;
    protected List<Keyword> candidateKeywords = new ArrayList<>();
    protected List<JSONObject> songJsonObjects = new ArrayList<>();

    /**
     * 构造方法，初始化提供者名称和目标关键字。
     *
     * @param providerName 音源提供者名称（如 "kuwo"、"migu"）
     * @param targetKeyword 目标歌曲关键字，用于搜索和匹配
     */
    public Provider(String providerName, Keyword targetKeyword)
    {
        this.providerName = providerName;
        this.targetKeyword = targetKeyword;
    }

    /**
     * 获取音源提供者名称。
     *
     * @return 提供者名称字符串
     */
    public String getProviderName()
    {
        return providerName;
    };

    /**
     * 获取已选中的候选关键字。
     *
     * @return 已选中的候选关键字，若未选中则返回 {@code null}
     */
    public Keyword getSelectedKeyword()
    {
        if(selectedIndex == -1)
        {
            return null;
        }
        return candidateKeywords.get(selectedIndex);
    };

    /**
     * 返回提供者类的简单名称（不含包名）。
     *
     * @return 类的简单名称
     */
    @Override
    public String toString()
    {
        return getClass().getSimpleName();
    }

    /**
     * 将关键字转换为 URL 编码的搜索查询字符串。
     * <p>
     * 歌曲名超过 20 个字符时截断，歌手名总长度超过 15 个字符时截断。
     *
     * @param keyword 待转换的关键字
     * @return URL 编码后的查询字符串
     */
    static protected String keyword2Query(Keyword keyword)
    {
        String songName = keyword.songName;
        if(songName.length() > 20)
        {
            songName = songName.substring(0, 20);
            Log.d("keyword2Query", "too long songName string, truncated");
        }
        String singers = "";
        for (String singer : keyword.singers)
        {
            String tmp = singers + singer + " ";
            if(tmp.length() > 15)
            {
                Log.d("keyword2Query", "too long singers string, truncated");
                break;
            }
            singers = tmp;
        }
        String queryStr = songName + " " + singers;
        try
        {
            queryStr = URLEncoder.encode(queryStr, "UTF-8");
        } catch (UnsupportedEncodingException e)
        {
            e.printStackTrace();
        }
        return queryStr;
    }

    /**
     * 计算候选关键字与目标关键字的匹配得分。
     * <p>
     * 得分考虑歌曲名长度差异、特殊关键词（live/remix 等）匹配以及歌手匹配等因素。
     * 不匹配时返回负分，匹配时返回正分。
     *
     * @param candidateKeyword 候选关键字
     * @param targetKeyword 目标关键字
     * @param index 候选在列表中的索引位置，越靠前得分越高
     * @return 匹配得分
     */
    static private int calculateScore(Keyword candidateKeyword, Keyword targetKeyword, int index)
    {
        if(!KeywordMatch.match(candidateKeyword, targetKeyword))
        {
            return -(50 + 3*index);
        }
        int score = 5 - 3*index;
        String targetName = targetKeyword.songName.toLowerCase();
        String candidateSongName = candidateKeyword.songName.toLowerCase();
        int candidateLen = candidateSongName.length();
        int targetLen = targetName.length();
        score -= Math.abs(candidateLen - targetLen);
        String leftName = candidateSongName.replace(targetName, "");
        List<String> words = Arrays.asList(
                "live", "dj", "remix", "cover", "instrumental", "伴奏", "翻唱", "翻自"
        );
        for (String word : words)
        {
            if(KeywordMatch.match(word, leftName))
            {
                if(KeywordMatch.match(word, targetKeyword.extra))
                {
                    score = 7;
                }else
                {
                    score -= 2;
                }
            }
        }
        score -= Math.abs(targetKeyword.singers.size() - candidateKeyword.singers.size());
        for (String targetSinger : targetKeyword.singers)
        {
            for (String candidateSinger : candidateKeyword.singers)
            {
                if (KeywordMatch.match(targetSinger, candidateSinger))
                {
                    score += 3;
                    score -= Math.abs(targetSinger.length() - candidateSinger.length());
                }
            }
        }
        return score;
    }

    /**
     * 从所有提供者的候选列表中选择最佳匹配。
     * <p>
     * 遍历每个提供者的候选关键字，计算匹配得分，选出得分最高的提供者及其候选索引。
     *
     * @param providers 提供者列表，每个提供者需已完成候选关键字收集
     * @return 最佳匹配的提供者，若无匹配则返回 {@code null}
     */
    static public Provider selectCandidateKeywords(List<Provider> providers)
    {
        Provider bestProvider = null;
        int maxScore = -999;
        int selectIndex = -1;
        for (Provider provider : providers)
        {
            for (int i = 0; i < provider.candidateKeywords.size(); i++)
            {
                Keyword candidateKeyword = provider.candidateKeywords.get(i);
                int score = calculateScore(candidateKeyword, provider.targetKeyword, i);
                Log.d("calculateScore", provider.providerName + "|" + candidateKeyword.toString() + '|' + provider.targetKeyword.toString() + "|" + score);
                if(score > maxScore)
                {
                    maxScore = score;
                    selectIndex = i;
                    bestProvider = provider;
                }
            }
        }
        if(bestProvider != null)
        {
            bestProvider.selectedIndex = selectIndex;
        }
        return bestProvider;
    }

    /**
     * 通过 HTTP HEAD 请求获取音频文件元信息并生成 Song 对象。
     * <p>
     * 发送带 Range 头的 GET 请求，读取前 8192 字节以检测比特率，
     * 同时从响应头中解析文件大小和 MD5 等信息。
     *
     * @param url 音频文件的播放地址
     * @return 包含 URL、大小、码率、MD5 信息的 {@link Song} 对象，请求失败时返回 {@code null}
     */
    static protected Song generateSong(String url)
    {
        Song song = null;
        try
        {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("range", "bytes=0-8191");
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK ||
                    responseCode == HttpURLConnection.HTTP_PARTIAL)
            {
                song = new Song();
                song.url = url;
                String content_range = connection.getHeaderField("Content-Range");
                if (content_range != null)
                {
                    int p = content_range.indexOf('/');
                    song.size = Integer.parseInt(content_range.substring(p + 1));
                } else
                {
                    song.size = connection.getContentLength();
                }
                String qqMusicMd5 = connection.getHeaderField("Server-Md5");
                if (qqMusicMd5 != null)
                {
                    song.md5 = qqMusicMd5;
                }
                byte[] mp3Data = ReadStream.read(connection.getInputStream());
                song.br = BitRate.detect(mp3Data);
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        return song;
    }

    /**
     * 收集候选关键字，通过搜索 API 获取候选歌曲列表并填充到 {@code candidateKeywords}。
     */
    abstract public void collectCandidateKeywords();

    /**
     * 获取已选中歌曲的播放信息。
     * <p>
     * 根据 {@code selectedIndex} 从候选列表中取出歌曲，请求播放地址并缓存到本地。
     *
     * @return 包含播放信息的 {@link Song} 对象，获取失败时返回 {@code null}
     */
    abstract public Song fetchSelectedSong();

    /**
     * 根据 JSON 数据获取歌曲播放信息。
     * <p>
     * 用于从本地缓存恢复播放地址，JSON 中需包含 "mid" 等标识字段。
     *
     * @param jsonObject 包含歌曲标识信息的 JSON 对象
     * @return 包含播放信息的 {@link Song} 对象，获取失败时返回 {@code null}
     */
    abstract public Song fetchSongByJson(JSONObject jsonObject);

    /**
     * 获取所有可用的音源提供者实例列表。
     *
     * @param targetKeyword 目标歌曲关键字
     * @return 包含酷我、咪咕、QQ 音乐提供者的列表
     */
    public static List<Provider> getProviders(Keyword targetKeyword)
    {
        List<Provider> providers = Arrays.asList(
                new KuwoMusic(targetKeyword),
                new MiguMusic(targetKeyword),
                new QQMusic(targetKeyword)
                //new KugouMusic(targetKeyword)
        );
        return providers;
    }
}
