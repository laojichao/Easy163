package org.ndroi.easy163.utils;

/**
 * 歌曲元数据模型，封装音频文件的 URL、大小、比特率和 MD5 校验值。
 * 用于描述从网易云音乐获取的替换歌曲信息。
 * 注意：Android 客户端播放需要正确的 size，下载功能需要正确的 md5。
 *
 * @author ndroi
 */
public class Song
{
    /** 音频文件下载地址 */
    public String url = "unknown";
    /** 音频文件大小（字节），默认约 10MB */
    public int size = 10 * 1000 * 1000;
    /** 音频比特率（bps），默认 192kbps */
    public int br = 192000;
    /** 音频文件的 MD5 校验值 */
    public String md5 = "unknown";

    /**
     * 返回格式化的歌曲信息字符串，包含 url、size、br 和 md5。
     *
     * @return 包含所有元数据字段的多行信息字符串
     */
    @Override
    public String toString()
    {
        String info = "url: " + url + "\nsize: " + size + "\nbr: " + br + "\nmd5: " + md5 + "\n";
        return info;
    }
}
