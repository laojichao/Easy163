package org.ndroi.easy163.vpn.bio;

import org.ndroi.easy163.vpn.config.Config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * BIO 网络工具类，提供 SocketChannel 的读写操作和字节数组十六进制转换。
 *
 * @author ndroi
 */
public class BioUtil
{

    private static final String TAG = BioUtil.class.getSimpleName();

    /**
     * 向 SocketChannel 写入数据
     *
     * @param channel    目标 SocketChannel
     * @param byteBuffer 待写入的数据缓冲区
     * @return 实际写入的字节数
     * @throws IOException 写入失败时抛出
     */
    public static int write(SocketChannel channel, ByteBuffer byteBuffer) throws IOException
    {
        int len = channel.write(byteBuffer);
        //Log.i(TAG, String.format("write %d %s ", len, channel.toString()));
        return len;
    }

    /**
     * 从 SocketChannel 读取数据
     *
     * @param channel    数据源 SocketChannel
     * @param byteBuffer 读取数据的目标缓冲区
     * @return 实际读取的字节数，-1 表示连接关闭
     * @throws IOException 读取失败时抛出
     */
    public static int read(SocketChannel channel, ByteBuffer byteBuffer) throws IOException
    {
        int len = channel.read(byteBuffer);
        if (Config.logRW)
        {
            //Log.d(TAG, String.format("read %d %s ", len, channel.toString()));
        }
        return len;
    }

    /**
     * 将字节数组转换为十六进制字符串（最多 128 字节），用于调试日志输出
     *
     * @param data 字节数组
     * @param off  起始偏移量
     * @param len  转换长度，超过 128 时自动截断
     * @return 十六进制字符串，每个字节用空格分隔
     */
    public static String byteToString(byte[] data, int off, int len)
    {
        len = Math.min(128, len);
        StringBuilder sb = new StringBuilder();
        for (int i = off; i < off + len; i++)
        {
            sb.append(String.format("%02x ", data[i]));
        }
        return sb.toString();
    }

}
