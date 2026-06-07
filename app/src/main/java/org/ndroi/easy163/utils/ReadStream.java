package org.ndroi.easy163.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 输入流读取工具，将 {@link InputStream} 内容完整读取为字节数组。
 * 使用 4096 字节缓冲区分块读取，适用于网络响应和文件流的读取场景。
 *
 * @author ndroi
 */
public class ReadStream
{
    /**
     * 从输入流读取全部数据并返回字节数组。
     * 读取完成后会自动关闭输出流。若发生 IO 异常，将打印堆栈信息并返回已读取的数据。
     *
     * @param is 待读取的输入流
     * @return 读取到的字节数组，若读取失败可能返回部分数据或空数组
     */
    public static byte[] read(InputStream is)
    {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        try
        {
            while (true)
            {
                int len = is.read(buffer);
                if (len == -1) break;
                outputStream.write(buffer, 0, len);
            }
            outputStream.close();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        return outputStream.toByteArray();
    }
}
