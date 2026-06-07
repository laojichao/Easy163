package org.ndroi.easy163.utils;

import com.alibaba.fastjson.JSONObject;

import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

/**
 * 网易云音乐 eapi 加解密工具，提供 AES 加解密与请求体编解码功能。
 * 使用 AES/ECB/PKCS7Padding 模式对网易云音乐 API 的请求数据进行加解密处理。
 *
 * @author ndroi
 */
public class Crypto
{
    private static String aes_key = "e82ckenh8dichen8";
    private static SecretKeySpec key = new SecretKeySpec(aes_key.getBytes(), "AES");
    private static Cipher decryptCipher = null;
    private static Cipher encryptCipher = null;

    static
    {
        try
        {
            decryptCipher = Cipher.getInstance("AES/ECB/PKCS7Padding");
            decryptCipher.init(Cipher.DECRYPT_MODE, key);
            encryptCipher = Cipher.getInstance("AES/ECB/PKCS7Padding");
            encryptCipher.init(Cipher.ENCRYPT_MODE, key);
        } catch (NoSuchAlgorithmException e)
        {
            e.printStackTrace();
        } catch (NoSuchPaddingException e)
        {
            e.printStackTrace();
        } catch (InvalidKeyException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * 使用 AES/ECB 模式对字节数组进行解密。
     *
     * @param bytes 待解密的字节数组
     * @return 解密后的字节数组，解密失败时返回 null
     */
    public static byte[] aesDecrypt(byte[] bytes)
    {
        byte[] result = null;
        try
        {
            result = decryptCipher.doFinal(bytes);
        } catch (BadPaddingException e)
        {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e)
        {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 使用 AES/ECB 模式对字节数组进行加密。
     *
     * @param bytes 待加密的字节数组
     * @return 加密后的字节数组，加密失败时返回 null
     */
    public static byte[] aesEncrypt(byte[] bytes)
    {
        byte[] result = null;
        try
        {
            result = encryptCipher.doFinal(bytes);
        } catch (BadPaddingException e)
        {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e)
        {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 将十六进制字符串转换为字节数组。
     *
     * @param hexString 十六进制字符串，长度必须为偶数
     * @return 转换后的字节数组
     */
    private static byte[] hexStringToByteArray(String hexString)
    {
        int len = hexString.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
        {
            bytes[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4) +
                    Character.digit(hexString.charAt(i + 1), 16));
        }
        return bytes;
    }

    /**
     * 将字节数组转换为大写十六进制字符串。
     *
     * @param bytes 待转换的字节数组
     * @return 大写十六进制字符串
     */
    private static String ByteArrayToHexString(byte[] bytes)
    {
        String hexStr = "";
        for (int i = 0; i < bytes.length; i++)
        {
            String hex = Integer.toHexString(bytes[i] & 0xFF).toUpperCase();
            if (hex.length() == 1)
            {
                hex = "0" + hex;
            }
            hexStr += hex;
        }
        return hexStr;
    }

    /**
     * 请求数据封装类，包含解密后的请求路径和 JSON 数据。
     */
    public static class Request
    {
        /** 请求路径 */
        public String path;
        /** 请求的 JSON 数据体 */
        public JSONObject json;
    }

    /**
     * 解密网易云 eapi 请求体。
     * 将十六进制编码的加密数据解密，并按分隔符拆分为路径和 JSON 数据。
     *
     * @param body 以 "params=" 开头的加密请求体字符串
     * @return 解密后的 {@link Request} 对象，包含路径和 JSON 数据
     */
    public static Request decryptRequestBody(String body)
    {
        Request request = new Request();
        byte[] encryptedBytes = hexStringToByteArray(body.substring(7));
        byte[] rawBytes = aesDecrypt(encryptedBytes);
        String text = new String(rawBytes);
        String[] parts = text.split("-36cd479b6b5-");
        request.path = parts[0];
        request.json = JSONObject.parseObject(parts[1]);
        return request;
    }

    /**
     * 将请求数据加密为网易云 eapi 格式。
     * 对路径和 JSON 数据进行拼接、MD5 签名后，AES 加密并转为十六进制编码。
     *
     * @param request 待加密的 {@link Request} 对象
     * @return 格式为 "params=加密十六进制字符串" 的请求体
     */
    public static String encryptRequestBody(Request request)
    {
        String jsonText = request.json.toString();
        String message = "nobody" + request.path + "use" + jsonText + "md5forencrypt";
        String digest = "";
        try
        {
            MessageDigest messageDigest = MessageDigest.getInstance("md5");
            messageDigest.update(message.getBytes());
            for (byte b : messageDigest.digest())
            {
                String temp = Integer.toHexString(b & 0xff);
                if (temp.length() == 1)
                {
                    temp = "0" + temp;
                }
                digest += temp;
            }
        } catch (NoSuchAlgorithmException e)
        {
            e.printStackTrace();
        }
        String text = request.path + "-36cd479b6b5-" + jsonText + "-36cd479b6b5-" + digest;
        String body = ByteArrayToHexString(aesEncrypt(text.getBytes()));
        body = "params=" + body;
        return body;
    }
}
