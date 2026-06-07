package org.ndroi.easy163.providers.utils;

import org.ndroi.easy163.utils.Keyword;

/**
 * 歌曲关键字模糊匹配工具，支持歌曲名称和歌手名称的容错匹配。
 * <p>
 * 匹配前会统一转小写并进行全角到半角的转换，支持子串包含关系的模糊判断。
 *
 * @author ndroi
 */
public class KeywordMatch
{
    /**
     * 预处理匹配字符串，转小写、去首尾空格，全角标点转半角。
     *
     * @param str 待预处理的字符串
     * @return 预处理后的字符串
     */
    private static String matchStrPreProcess(String str)
    {
        str = str.toLowerCase().trim();
        str = str.replace('，', ',').replace('？', '?');
        str = str.replace(", ", ",");
        return str;
    }

    /**
     * 判断两个字符串是否模糊匹配。
     * <p>
     * 先检查直接子串包含关系；若不匹配则取空格前的第一部分再做包含判断。
     *
     * @param a 第一个字符串
     * @param b 第二个字符串
     * @return 两个字符串模糊匹配返回 {@code true}，否则返回 {@code false}
     */
    public static boolean match(String a, String b)
    {
        if(a == null || b == null || a.isEmpty() || b.isEmpty())
        {
            return false;
        }
        a = matchStrPreProcess(a);
        b = matchStrPreProcess(b);
        boolean isContain = a.contains(b) || b.contains(a);
        if(isContain)
        {
            return true;
        }
        if(a.contains(" "))
        {
            a = a.split(" ")[0];
        }
        if(b.contains(" "))
        {
            b = b.split(" ")[0];
        }
        return a.contains(b) || b.contains(a);
    }

    /**
     * 判断两个关键字对象的歌曲名和歌手是否匹配。
     * <p>
     * 先匹配歌曲名，再检查双方歌手列表中是否存在匹配项。
     *
     * @param a 第一个关键字对象
     * @param b 第二个关键字对象
     * @return 歌曲名和至少一位歌手均匹配时返回 {@code true}，否则返回 {@code false}
     */
    public static boolean match(Keyword a, Keyword b)
    {
        boolean nameMatch = match(a.songName, b.songName);
        if (!nameMatch)
        {
            return false;
        }
        for (String aSinger : a.singers)
        {
            for (String bSinger : b.singers)
            {
                if (match(aSinger, bSinger))
                {
                    return true;
                }
            }
        }
        return false;
    }
}
