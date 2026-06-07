package org.ndroi.easy163.vpn.util;

import java.nio.ByteBuffer;

/**
 * 字节缓冲区池，提供固定大小的 DirectByteBuffer 分配
 *
 * @author ndroi
 */
public class ByteBufferPool
{
    /** 缓冲区大小（16KB） */
    public static final int BUFFER_SIZE = 16384; // XXX: Is this ideal?

    /**
     * 获取一个 16KB 的 DirectByteBuffer 实例
     *
     * @return 新分配的 DirectByteBuffer 实例
     */
    public static ByteBuffer acquire()
    {
        //return ByteBuffer.allocate(BUFFER_SIZE);
        return ByteBuffer.allocateDirect(BUFFER_SIZE);
    }
}

