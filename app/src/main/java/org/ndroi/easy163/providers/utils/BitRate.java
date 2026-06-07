package org.ndroi.easy163.providers.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * MP3/FLAC 音频比特率检测工具，通过解析音频帧头信息识别码率。
 * <p>
 * 支持识别 FLAC 无损格式（返回 999）和 MP3 各版本/层的比特率表。
 *
 * @author ndroi
 */
public class BitRate
{
    private static Map<Integer, Map<Integer, int[]>> table = new HashMap<>();

    static
    {
        int[] arr_3_3 = {0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448, -1};
        int[] arr_3_2 = {0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, -1};
        int[] arr_3_1 = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, -1};
        int[] arr_2_3 = {0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256, -1};
        int[] arr_2_2 = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, -1};
        Map<Integer, int[]> map_3 = new HashMap<>();
        map_3.put(3, arr_3_3);
        map_3.put(2, arr_3_2);
        map_3.put(1, arr_3_1);
        table.put(3, map_3);
        Map<Integer, int[]> map_2 = new HashMap<>();
        map_2.put(3, arr_2_3);
        map_2.put(2, arr_2_2);
        map_2.put(1, arr_2_2);
        table.put(2, map_2);
        table.put(0, map_2);
    }

    /**
     * 检测音频数据的比特率，支持 MP3 和 FLAC 格式。
     * <p>
     * FLAC 格式直接返回 999，MP3 格式跳过 ID3v2 标签后解析帧头，
     * 通过版本号和层信息查表获取比特率（单位 kbps）。
     *
     * @param bytes 音频文件的二进制数据（至少需包含文件头部分）
     * @return 比特率（kbps），FLAC 返回 999，解析失败可能返回 -1
     */
    public static int detect(byte[] bytes)
    {
        int ptr = 0;
        if (bytes[0] == 'f' && bytes[1] == 'L' && bytes[2] == 'a' && bytes[3] == 'C')
        {
            return 999;
        }
        if (bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3')
        {
            ptr = 6;
            int size = 0;
            for (int i = 0; i < 4; i++)
            {
                size += (bytes[ptr + i] & 0x7f) << (7 * (3 - i));
            }
            ptr = 10 + size;
        }
        int version = ((bytes[ptr + 1] & 0xff) >> 3) & 0x3;
        int layer = ((bytes[ptr + 1] & 0xff) >> 1) & 0x3;
        int bitrate = (bytes[ptr + 2] & 0xff) >> 4;
        int result = table.get(version).get(layer)[bitrate];
        return result;
    }
}
