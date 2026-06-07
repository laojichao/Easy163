package org.ndroi.easy163.core;

import android.util.Log;
import org.ndroi.easy163.providers.Provider;
import org.ndroi.easy163.utils.ConcurrencyTask;
import org.ndroi.easy163.utils.EasyLog;
import org.ndroi.easy163.utils.Keyword;
import org.ndroi.easy163.utils.Song;
import java.util.List;

/**
 * 全网搜索管理器，负责从多个音源提供者中并发搜索并选出最佳音源。
 * <p>
 * 搜索流程：遍历所有可用 {@link Provider}，并发收集候选关键词（超时 8 秒），
 * 通过 {@link Provider#selectCandidateKeywords} 选出最佳音源后获取歌曲信息。
 * </p>
 *
 * @author ndroi
 * @see Provider
 * @see Keyword
 * @see Song
 */
public class Search
{
    /**
     * 从多个音源提供者中搜索指定关键词对应的歌曲。
     * <p>
     * 并发调用所有 Provider 收集候选关键词，超时时间为 8 秒。
     * 完成后选择最佳 Provider 获取歌曲。
     * </p>
     *
     * @param targetKeyword 目标搜索关键词
     * @return 搜索到的歌曲对象，未找到时返回 {@code null}
     */
    public static Song search(Keyword targetKeyword)
    {
        List<Provider> providers = Provider.getProviders(targetKeyword);
        Log.d("search", "start to search: " + targetKeyword.toString());
        EasyLog.log("开始全网搜索：" + targetKeyword.toString());
        ConcurrencyTask concurrencyTask = new ConcurrencyTask();
        for (Provider provider : providers)
        {
            concurrencyTask.addTask(new Thread(){
                @Override
                public void run()
                {
                    super.run();
                    provider.collectCandidateKeywords();
                }
            });
        }
        long startTime = System.currentTimeMillis();
        while (true)
        {
            if(concurrencyTask.isAllFinished())
            {
                Log.d("search", "all providers finish collect");
                break;
            }
            long endTime = System.currentTimeMillis();
            if (endTime - startTime > 8 * 1000)
            {
                Log.d("search", "collect candidateKeywords timeout");
                break;
            }
            try
            {
                Thread.sleep(100);
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
        Provider bestProvider = Provider.selectCandidateKeywords(providers);
        if (bestProvider != null)
        {
            Log.d("search", "bestProvider " + bestProvider.toString());
            Song song = null;
            try
            {
                song = bestProvider.fetchSelectedSong();
            }catch (Exception e)
            {
                Log.d("search", "fetchSelectedSong failed");
                e.printStackTrace();
            }
            if(song != null)
            {
                Log.d("search", "from provider:\n" + song.toString());
                EasyLog.log("搜索到音源：" +  "[" + bestProvider.getProviderName() + "] " +
                        bestProvider.getSelectedKeyword().toString());
            }else
            {
                Log.d("search", "fetchSelectedSong failed");
                EasyLog.log("未搜索到音源：" + targetKeyword.toString());
            }
            return song;
        }
        EasyLog.log("未搜索到音源：" + targetKeyword.toString());
        return null;
    }
}