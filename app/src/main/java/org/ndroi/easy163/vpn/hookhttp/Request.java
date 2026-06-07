package org.ndroi.easy163.vpn.hookhttp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP 请求解析器，支持流式解析 HTTP 请求的请求行、头部字段和请求体
 *
 * @author ndroi
 */
public class Request
{
    private ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    private Map<String, String> headerFields = new LinkedHashMap<>();
    private int headerLen = 0;
    private String method;
    private String uri;
    private String version;
    private byte[] content = null;

    /**
     * 获取请求头字段映射
     *
     * @return 头部字段的键值对映射
     */
    public Map<String, String> getHeaderFields()
    {
        return headerFields;
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
        }
        if (finished() && content != null)
        {
            byte[] data = byteArrayOutputStream.toByteArray();
            System.arraycopy(data, headerLen, content, 0, content.length);
        }
    }

    /**
     * 判断请求是否已完整接收（头部和请求体均已接收完毕）
     *
     * @return 请求完整接收返回 true，否则返回 false
     */
    public boolean finished()
    {
        if (headerLen == 0)
        {
            return false;
        }
        int contentLen = (content == null ? 0 : content.length);
        return byteArrayOutputStream.size() >= headerLen + contentLen;
    }

    /**
     * 获取请求体内容
     *
     * @return 请求体字节数组，无请求体时返回 null
     */
    public byte[] getContent()
    {
        return content;
    }

    /**
     * 设置请求体内容并更新 Content-Length 头
     *
     * @param content 新的请求体字节数组
     */
    public void setContent(byte[] content)
    {
        headerFields.put("Content-Length", content.length + "");
        this.content = content;
    }

    private boolean headerReceived()
    {
        return headerLen != 0;
    }

    /**
     * 获取请求方法（如 GET、POST）
     *
     * @return 请求方法字符串
     */
    public String getMethod()
    {
        return method;
    }

    /**
     * 设置请求方法
     *
     * @param method 新的请求方法（如 GET、POST）
     */
    public void setMethod(String method)
    {
        this.method = method;
    }

    /**
     * 设置请求 URI
     *
     * @param uri 新的请求 URI
     */
    public void setUri(String uri)
    {
        this.uri = uri;
    }

    /**
     * 设置 HTTP 版本
     *
     * @param version 新的 HTTP 版本（如 HTTP/1.1）
     */
    public void setVersion(String version)
    {
        this.version = version;
    }

    /**
     * 获取请求 URI
     *
     * @return 请求 URI 字符串
     */
    public String getUri()
    {
        return uri;
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
     * 尝试解码已接收的数据，提取头部和请求体
     */
    private void tryDecode()
    {
        int crlf = checkCRLF();
        if (crlf != -1)
        {
            headerLen = crlf + 4;
            decode();
            if (method.equals("POST"))
            {
                int contentLen = Integer.parseInt(headerFields.get("Content-Length"));
                content = new byte[contentLen];
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
     * 解析请求行和头部字段
     */
    private void decode()
    {
        byte[] bytes = byteArrayOutputStream.toByteArray();
        String headerStr = new String(bytes, 0, headerLen - 4);
        String[] lines = headerStr.split("\r\n");
        String requestLine = lines[0];
        int _p = requestLine.indexOf(' ');
        method = requestLine.substring(0, _p);
        requestLine = requestLine.substring(_p + 1);
        _p = requestLine.indexOf(' ');
        uri = requestLine.substring(0, _p);
        version = requestLine.substring(_p + 1);
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
     * 将当前请求编码为 HTTP 报文字节，写入内部缓冲区
     */
    private void encode()
    {
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append(method + " " + uri + " " + version + "\r\n");
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
     * 输出完整的 HTTP 请求字节数组（包含请求行、头部和请求体）
     *
     * @return 完整的 HTTP 请求字节数组
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
