package org.ndroi.easy163.core;

import android.util.Log;
import com.alibaba.fastjson.JSONObject;
import org.ndroi.easy163.providers.Provider;
import org.ndroi.easy163.utils.EasyLog;
import org.ndroi.easy163.utils.ReadStream;
import org.ndroi.easy163.utils.Song;
import org.ndroi.easy163.vpn.LocalVPNService;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地歌曲缓存管理器，负责音源匹配结果的持久化存储与读取。
 * <p>
 * 缓存数据以文本行格式存储于应用缓存目录，每行包含歌曲 ID、音源名称和 JSON 数据。
 * 在应用启动时加载至内存，供 {@link Cache} 快速查询使用。
 * </p>
 *
 * @author ndroi
 * @see Cache
 * @see Provider
 */
public class Local
{
    /**
     * 本地缓存条目，封装音源名称与对应的 JSON 数据。
     */
    static class Item
    {
        public String providerName;
        public JSONObject jsonObject;
    }

    private static Map<String, Item> items = new HashMap<>();
    private static String diskFilename = "easy163_id_mid";

    /**
     * 获取本地缓存文件的引用。
     *
     * @return 缓存文件对象
     */
    private static File getCacheFile()
    {
        File cacheDir = LocalVPNService.getContext().getCacheDir();
        return new File(cacheDir, diskFilename);
    }

    /**
     * 从缓存文件加载本地缓存数据至内存。
     * <p>
     * 读取缓存文件中的每一行，解析为歌曲 ID、音源名称和 JSON 数据，
     * 存入内存映射表。若缓存文件不存在或读取失败则跳过。
     * </p>
     */
    public static void load()
    {
        items.clear();
        String data = "";
        try
        {
            FileInputStream inputStream = new FileInputStream(getCacheFile());
            byte[] bytes = ReadStream.read(inputStream);
            data = new String(bytes);
            inputStream.close();
        } catch (FileNotFoundException e)
        {
            EasyLog.log("未发现本地缓存");
            Log.d("Local", "未发现本地缓存");
            return;
        } catch (IOException e)
        {
            e.printStackTrace();
            EasyLog.log("本地缓存读取失败");
            Log.d("Local", "本地缓存读取失败");
            return;
        }
        String[] lines = data.split("\n");
        for (String line : lines)
        {
            int p1 = line.indexOf(' ');
            String id = line.substring(0, p1);
            int p2 = line.indexOf(' ', p1 + 1);
            Item item = new Item();
            item.providerName = line.substring(p1 + 1, p2);
            item.jsonObject = JSONObject.parseObject(line.substring(p2 + 1));
            items.put(id, item);
        }
        EasyLog.log("本地缓存加载完毕");
        Log.d("Local", "本地缓存加载完毕");
    }

    /**
     * 根据歌曲 ID 从本地缓存中获取对应的 {@link Song} 对象。
     * <p>
     * 命中缓存后，通过对应的 {@link Provider} 解析 JSON 数据获取歌曲信息。
     * 若缓存条目已失效（Provider 无法获取歌曲），则自动移除该条目。
     * </p>
     *
     * @param id 歌曲 ID
     * @return 对应的歌曲对象，未命中或失效时返回 {@code null}
     */
    public static Song get(String id)
    {
        Item item = items.get(id);
        if(item == null)
        {
            return null;
        }
        EasyLog.log("本地缓存命中：" + "[" + item.providerName + "] " + id);
        List<Provider> providers = Provider.getProviders(null);
        Provider targetProvider = null;
        for (Provider provider : providers)
        {
            if(provider.getProviderName().equals(item.providerName))
            {
                targetProvider = provider;
                break;
            }
        }
        Song song = null;
        if(targetProvider != null)
        {
            song = targetProvider.fetchSongByJson(item.jsonObject);
            if(song == null)
            {
                items.remove(id);
                EasyLog.log("本地缓存失效：" + "[" + item.providerName + "] " + id);
            }
        }
        return song;
    }

    /**
     * 将音源匹配结果写入本地缓存。
     * <p>
     * 若该歌曲 ID 已存在于缓存中则忽略。数据以追加方式写入缓存文件，
     * 格式为：{@code id providerName jsonObject\n}。
     * </p>
     *
     * @param id           歌曲 ID
     * @param providerName 音源提供者名称
     * @param jsonObject   音源匹配的 JSON 数据
     */
    public static void put(String id, String providerName, JSONObject jsonObject)
    {
        if(items.containsKey(id))
        {
            return;
        }
        try
        {
            FileOutputStream outputStream = new FileOutputStream(getCacheFile(), true);
            String line = id + " " + providerName + " " + jsonObject.toString() + "\n";
            outputStream.write(line.getBytes());
            outputStream.close();
        } catch (FileNotFoundException e)
        {
            e.printStackTrace();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * 清空内存缓存并删除本地缓存文件。
     */
    public static void clear()
    {
        items.clear();
        getCacheFile().delete();
    }
}
