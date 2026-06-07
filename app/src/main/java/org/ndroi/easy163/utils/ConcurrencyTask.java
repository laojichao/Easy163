package org.ndroi.easy163.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * 并发任务管理器，支持批量添加线程任务并等待全部完成。
 * 提供简单的并发执行控制能力。
 *
 * @author ndroi
 */
public class ConcurrencyTask
{
    private List<Thread> threads = new ArrayList<>();

    /**
     * 添加并启动一个线程任务。
     * 线程会在添加后立即启动执行。
     *
     * @param thread 待执行的线程任务
     */
    public void addTask(Thread thread)
    {
        threads.add(thread);
        thread.start();
    }

    /**
     * 检查所有已添加的任务是否已全部执行完成。
     *
     * @return 若所有线程均已结束返回 true，否则返回 false
     */
    public boolean isAllFinished()
    {
        for (Thread thread : threads)
        {
           if(thread.isAlive())
           {
               return false;
           }
        }
        return true;
    }

    /**
     * 阻塞当前线程，等待所有已添加的任务执行完成。
     * 若等待过程中被中断，将打印堆栈信息。
     */
    public void waitAll()
    {
        try
        {
            for (Thread thread : threads)
            {
                thread.join();
            }
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }
}
