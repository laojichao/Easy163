package org.ndroi.easy163.vpn.hookhttp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * HTTP 响应解析器，支持流式解析 HTTP 响应的状态行、头部字段、响应体，支持 chunked 传输编码和 GZIP 解压
 *
 * @author ndroi
 */
public class Response
{
    /**
     * chunked 传输编码的分块解析器
     *
     * @author ndroi
     */
    private static class Chunks
    {
        /**
         * 单个数据块
         *
         * @author ndroi
         */
        private static class Chunk
        {
            private byte[] data;

            /**
         * 构造指定大小的数据块
         *
         * @param size 数据块大小（字节）
         */
        public Chunk(int size)
            {
                data = new byte[size];
            }

            /**
         * 设置数据块内容
         *
         * @param bytes 源字节数组
         */
        public void setData(byte[] bytes)
            {
                setData(bytes, 0, bytes.length);
            }

            /**
         * 设置数据块内容（指定范围）
         *
         * @param bytes  源字节数组
         * @param offset 起始偏移量
         * @param length 复制长度
         */
        public void setData(byte[] bytes, int offset, int length)
            {
                System.arraycopy(bytes, offset, data, 0, length);
            }

            /**
         * 获取数据块内容
         *
         * @return 数据块的字节数组
         */
        public byte[] getData()
            {
                return data;
            }

            /**
         * 获取数据块大小
         *
         * @return 数据块字节数
         */
        public int getSize()
            {
                return data.length;
            }
        }

        private enum Status
        {
            RECV_LENGTH,
            RECV_CONTENT,
            RECV_OVER,
        }

        private ByteArrayOutputStream remainingStream = new ByteArrayOutputStream();
        private List<Chunk> chunks = new ArrayList<>();
        private Status status = Status.RECV_LENGTH;

        private Chunk getCurrent()
        {
            if (chunks.isEmpty())
            {
                return null;
            }
            return chunks.get(chunks.size() - 1);
        }

        private int checkCRLF(byte[] bytes)
        {
            for (int i = 0; i < bytes.length - 3; i++)
            {
                if (bytes[i] == 13 && bytes[i + 1] == 10)
                {
                    return i;
                }
            }
            return -1;
        }

        private void addNew(int length)
        {
            chunks.add(new Chunk(length));
        }

        /**
         * 向 chunked 解析器追加原始字节数据
         *
         * @param bytes 待追加的字节数组
         */
        public void putBytes(byte[] bytes)
        {
            remainingStream.write(bytes, 0, bytes.length);
            while (true)
            {
                if (status == Status.RECV_LENGTH)
                {
                    bytes = remainingStream.toByteArray();
                    int crlf = checkCRLF(bytes);
                    if (crlf != -1)
                    {
                        int length = Integer.parseInt(new String(bytes, 0, crlf), 16);
                        addNew(length);
                        remainingStream.reset();
                        int offset = crlf + 2;
                        remainingStream.write(bytes, offset, bytes.length - offset);
                        status = Status.RECV_CONTENT;
                    } else
                    {
                        break;
                    }
                }
                if (status == Status.RECV_CONTENT)
                {
                    Chunk curChunk = getCurrent();
                    int chunkSize = curChunk.getSize();
                    if (remainingStream.size() >= chunkSize + 2)
                    {
                        bytes = remainingStream.toByteArray();
                        curChunk.setData(bytes, 0, chunkSize);
                        remainingStream.reset();
                        int offset = chunkSize + 2;
                        remainingStream.write(bytes, offset, bytes.length - offset);
                        if (chunkSize == 0)
                        {
                            status = Status.RECV_OVER;
                            break;
                        } else
                        {
                            status = Status.RECV_LENGTH;
                        }
                    } else
                    {
                        break;
                    }
                }
            }
        }

        /**
         * 判断 chunked 数据是否已全部接收完毕
         *
         * @return 接收完毕返回 true，否则返回 false
         */
        public boolean finished()
        {
            return status == Status.RECV_OVER;
        }

        /**
         * 输出所有已接收数据块的合并字节数组
         *
         * @return 合并后的完整响应体字节数组
         */
        public byte[] dump()
        {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            for (Chunk chunk : chunks)
            {
                byteArrayOutputStream.write(chunk.getData(), 0, chunk.getSize());
            }
            return byteArrayOutputStream.toByteArray();
        }
    }

    private ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    private Map<String, String> headerFields = new LinkedHashMap<>();
    private int headerLen = 0;
    private String version;
    private String code;
    private String desc;
    private byte[] content = null;
    private Chunks chunks = null;

    private boolean headerReceived()
    {
        return headerLen != 0;
    }

    /**
     * 判断响应是否已完整接收（头部和响应体均已接收完毕）
     *
     * @return 响应完整接收返回 true，否则返回 false
     */
    public boolean finished()
    {
        if (headerLen == 0)
        {
            return false;
        }
        if (chunks == null)
        {
            int contentLen = (content == null ? 0 : content.length);
            return byteArrayOutputStream.size() >= contentLen;
        }
        return chunks.finished();
    }

    /**
     * 向解析器追加原始字节数据
     *
     * @param bytes 待追加的字节数组
     */
    public void putBytes(byte[] bytes)
    {
        putBytes(bytes, 0, bytes.length);
    }

    /**
     * 获取响应头字段映射
     *
     * @return 头部字段的键值对映射
     */
    public Map<String, String> getHeaderFields()
    {
        return headerFields;
    }

    /**
     * 获取 HTTP 版本
     *
     * @return HTTP 版本字符串（如 HTTP/1.1）
     */
    public String getVersion()
    {
        return version;
    }

    /**
     * 设置 HTTP 版本
     *
     * @param version 新的 HTTP 版本
     */
    public void setVersion(String version)
    {
        this.version = version;
    }

    /**
     * 获取状态码
     *
     * @return 状态码字符串（如 200）
     */
    public String getCode()
    {
        return code;
    }

    /**
     * 设置状态码
     *
     * @param code 新的状态码（如 200、404）
     */
    public void setCode(String code)
    {
        this.code = code;
    }

    /**
     * 获取状态描述
     *
     * @return 状态描述字符串（如 OK、Not Found）
     */
    public String getDesc()
    {
        return desc;
    }

    /**
     * 设置状态描述
     *
     * @param desc 新的状态描述（如 OK、Not Found）
     */
    public void setDesc(String desc)
    {
        this.desc = desc;
    }

    /**
     * 获取响应体内容
     *
     * @return 响应体字节数组，无响应体时返回 null
     */
    public byte[] getContent()
    {
        return content;
    }

    /**
     * 设置响应体内容并更新 Content-Length 头
     *
     * @param content 新的响应体字节数组
     */
    public void setContent(byte[] content)
    {
        this.content = content;
        headerFields.put("Content-Length", "" + content.length);
    }

    /**
     * 向解析器追加指定范围的原始字节数据
     *
     * @param bytes  待追加的字节数组
     * @param offset 起始偏移量
     * @param length 追加长度
     */
    public void putBytes(byte[] bytes, int offset, int length)
    {
        if (finished()) return;
        byteArrayOutputStream.write(bytes, offset, length);
        if (!headerReceived())
        {
            tryDecode();
            if (headerReceived())
            {
                bytes = byteArrayOutputStream.toByteArray();
                byteArrayOutputStream.reset();
                byteArrayOutputStream.write(bytes, headerLen, bytes.length - headerLen);
                bytes = byteArrayOutputStream.toByteArray();
            }
        }
        if (chunks != null && !chunks.finished())
        {
            chunks.putBytes(bytes);
        }
        if (finished())
        {
            if (content != null)
            {
                content = byteArrayOutputStream.toByteArray();
            }
            if (chunks != null)
            {
                content = chunks.dump();
                headerFields.remove("Transfer-Encoding");
                if (headerFields.containsKey("Content-Encoding"))
                {
                    content = unzip(content);
                    headerFields.remove("Content-Encoding");
                }
                headerFields.put("Content-Length", "" + content.length);
            }
        }
    }

    /**
     * 对 GZIP 压缩的数据进行解压
     *
     * @param bytes GZIP 压缩的字节数组
     * @return 解压后的字节数组，解压失败返回 null
     */
    private byte[] unzip(byte[] bytes)
    {
        byte[] result = null;
        try
        {
            GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(bytes));
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            while (true)
            {
                int len = gzipInputStream.read(buffer);
                if (len == -1) break;
                byteArrayOutputStream.write(buffer, 0, len);
            }
            gzipInputStream.close();
            byteArrayOutputStream.close();
            result = byteArrayOutputStream.toByteArray();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        return result;
    }


    /**
     * 尝试解码已接收的数据，提取状态行和头部字段，确定传输编码方式
     */
    private void tryDecode()
    {
        int crlf = checkCRLF();
        if (crlf != -1)
        {
            headerLen = crlf + 4;
            decode();
            if (headerFields.containsKey("Content-Length"))
            {
                int contentLen = Integer.parseInt(headerFields.get("Content-Length"));
                content = new byte[contentLen];
            } else if (headerFields.containsKey("Transfer-Encoding"))
            {
                chunks = new Chunks();
            }
        }
    }

    /**
     * 检测头部结束标记（连续的 CRLF）
     *
     * @return 头部结束标记的位置索引，未找到则返回 -1
     */
    private int checkCRLF()
    {
        byte[] bytes = byteArrayOutputStream.toByteArray();
        for (int i = 0; i < bytes.length - 3; i++)
        {
            if (bytes[i] == 13 && bytes[i + 1] == 10 &&
                    bytes[i + 2] == 13 && bytes[i + 3] == 10)
            {
                return i;
            }
        }
        return -1;
    }

    /**
     * 解析状态行和头部字段
     */
    private void decode()
    {
        byte[] bytes = byteArrayOutputStream.toByteArray();
        String headerStr = new String(bytes, 0, headerLen - 4);
        String[] lines = headerStr.split("\r\n");
        String requestLine = lines[0];
        int _p = requestLine.indexOf(' ');
        version = requestLine.substring(0, _p);
        requestLine = requestLine.substring(_p + 1);
        _p = requestLine.indexOf(' ');
        code = requestLine.substring(0, _p);
        desc = requestLine.substring(_p + 1);
        for (int i = 1; i < lines.length; i++)
        {
            String line = lines[i];
            _p = line.indexOf(':');
            String key = line.substring(0, _p).trim();
            String value = line.substring(_p + 1).trim();
            headerFields.put(key, value);
        }
    }

    /**
     * 将当前响应编码为 HTTP 报文字节，写入内部缓冲区
     */
    private void encode()
    {
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append(version + " " + code + " " + desc + "\r\n");
        for (String key : headerFields.keySet())
        {
            String value = headerFields.get(key);
            stringBuffer.append(key + ": " + value + "\r\n");
        }
        stringBuffer.append("\r\n");
        try
        {
            byteArrayOutputStream.reset();
            byteArrayOutputStream.write(stringBuffer.toString().getBytes());
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * 输出完整的 HTTP 响应字节数组（包含状态行、头部和响应体）
     *
     * @return 完整的 HTTP 响应字节数组
     */
    public byte[] dump()
    {
        encode();
        if (content != null)
        {
            try
            {
                byteArrayOutputStream.write(content);
                byteArrayOutputStream.close();
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        return byteArrayOutputStream.toByteArray();
    }
}
