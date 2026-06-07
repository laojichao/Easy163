package org.ndroi.easy163.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * 歌曲关键字模型，封装歌曲 ID、名称、歌手列表及附加信息。
 * 用于搜索匹配时描述一首歌曲的关键属性。
 *
 * @author ndroi
 */
public class Keyword
{
    /** 歌曲 ID */
    public String id;
    /** 歌曲名称 */
    public String songName;
    /** 歌手列表 */
    public List<String> singers = new ArrayList<>();
    /** 歌曲名称括号内的附加信息，如版本、语种等 */
    public String extra = null;

    /**
     * 解析原始歌曲名称，提取歌名和括号内的附加信息。
     * 支持半角括号 () 和全角括号 （）。
     * 例如："晴天(Live版)" 将解析为 songName="晴天", extra="Live版"。
     *
     * @param rawSongName 原始歌曲名称字符串
     */
    public void applyRawSongName(String rawSongName)
    {
        int p = rawSongName.indexOf('(');
        if(p == -1)
        {
            p = rawSongName.indexOf('（');
        }
        if (p != -1)
        {
            songName = rawSongName.substring(0, p).trim();
            int q = rawSongName.indexOf(')', p);
            if(q == -1)
            {
                q = rawSongName.indexOf('）', p);
            }
            if(q != -1)
            {
                extra = rawSongName.substring(p + 1, q);
            }
        }else
        {
            songName = rawSongName.trim();
            extra = null;
        }
    }

    /**
     * 返回格式为 "歌名-歌手1/歌手2" 的字符串表示。
     *
     * @return 歌曲名称与歌手列表的格式化字符串
     */
    @Override
    public String toString()
    {
        String str = songName + "-";
        for (String singer : singers)
        {
            str += singer + '/';
        }
        str = str.substring(0, str.length() - 1);
        return str;
    }
}
