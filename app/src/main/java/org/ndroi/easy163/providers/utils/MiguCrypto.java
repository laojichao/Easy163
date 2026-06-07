package org.ndroi.easy163.providers.utils;

import android.util.Base64;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 咪咕音乐 API 加密工具，基于 AES-256-CBC 加密请求参数。
 * <p>
 * 通过密码和盐值派生 AES 密钥与 IV，加密后的数据以 Base64 编码拼接为查询字符串。
 *
 * @author ndroi
 */
public class MiguCrypto
{
    private static String password = "00000000000000000000000000000000";
    private static String salt = "00000000";
    private static String skeyB64 = "OMYm0ulbQZgEd21abq1wQI7CnLeAY5CT4RPRLBmAzSUdBWgPHq3n" +
            "KTYBNJe9EJMMs2l2aOKPHQCl05QDDfO4wJpzwwL4IFag5u%2FAWY81MZ6SJJpD1gUEw6fVqENIQowg" +
            "0bSjZwkY61kY0EIvDNsEZ9TbqFCiy25RXb%2BaLWgcRGE%3D";
    private static Cipher aesCipher = null;

    /**
     * 通过密码和盐值派生 AES-256 密钥和 IV 并初始化加密器。
     * <p>
     * 使用 MD5 迭代哈希（类似 OpenSSL EVP_BytesToKey）从 password + salt
     * 派生 32 字节密钥和 16 字节 IV，初始化 AES/CBC/PKCS5Padding 加密器。
     */
    private static void initAes()
    {
        int keySize = 256 / 8;
        int ivSize = 16;
        int repeat = (keySize + ivSize) / 16;
        List<byte[]> byteList = new ArrayList<>();
        byte[] ps = (password + salt).getBytes();
        try
        {
            MessageDigest messageDigest = MessageDigest.getInstance("md5");
            for (int i = 0; i < repeat; i++)
            {
                if (byteList.isEmpty())
                {
                    messageDigest.update(ps);
                    byteList.add(messageDigest.digest());
                } else
                {
                    byte[] last = byteList.get(byteList.size() - 1);
                    byte[] buffer = new byte[last.length + ps.length];
                    System.arraycopy(last, 0, buffer, 0, last.length);
                    System.arraycopy(ps, 0, buffer, last.length, ps.length);
                    messageDigest.update(buffer);
                    byteList.add(messageDigest.digest());
                }
            }
        } catch (NoSuchAlgorithmException e)
        {
            e.printStackTrace();
        }
        byte[] skey = new byte[16 * 2];
        System.arraycopy(byteList.get(0), 0, skey, 0, 16);
        System.arraycopy(byteList.get(1), 0, skey, 16, 16);
        byte[] sIv = byteList.get(2);
        SecretKeySpec keySpec = new SecretKeySpec(skey, "AES");
        try
        {
            aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            aesCipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(sIv));
        } catch (InvalidKeyException e)
        {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e)
        {
            e.printStackTrace();
        } catch (NoSuchPaddingException e)
        {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * 加密请求参数并返回包含密文和密钥的查询字符串。
     * <p>
     * 使用 AES-256-CBC 加密输入文本，密文前拼接 "Salted__" + salt 头部，
     * Base64 编码后与预置密钥拼接为查询字符串。
     *
     * @param text 待加密的请求参数文本
     * @return 格式为 "data={密文}&secKey={密钥}" 的查询字符串，加密失败时返回 {@code null}
     */
    public static String Encrypt(String text)
    {
        if (aesCipher == null)
        {
            initAes();
        }
        String result = null;
        try
        {
            byte[] header = ("Salted__" + salt).getBytes();
            byte[] aseEnc = aesCipher.doFinal(text.getBytes());
            byte[] data = new byte[header.length + aseEnc.length];
            System.arraycopy(header, 0, data, 0, header.length);
            System.arraycopy(aseEnc, 0, data, header.length, aseEnc.length);
            String dataB64 = Base64.encodeToString(data, Base64.NO_WRAP);
            result = "data=" + URLEncoder.encode(dataB64, "UTF-8") + "&secKey=" + skeyB64;
        } catch (IllegalBlockSizeException e)
        {
            e.printStackTrace();
        } catch (BadPaddingException e)
        {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e)
        {
            e.printStackTrace();
        }
        return result;
    }
}