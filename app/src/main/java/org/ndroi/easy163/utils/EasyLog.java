package org.ndroi.easy163.utils;

import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 应用日志工具，将带时间戳的日志信息输出到指定 TextView。
 * 通过 UI 线程安全地追加日志内容，并支持滚动显示。
 *
 * @author ndroi
 */
public class EasyLog
{
    private static Logger logger = null;

    /**
     * 记录一条带时间戳的日志到 UI。
     * 若未设置 TextView，则日志将被忽略。
     *
     * @param info 日志信息内容
     */
    public static void log(String info)
    {
        if(logger != null)
        {
            logger.log(info);
        }
    }

    /**
     * 设置日志输出的目标 TextView 并启用滚动功能。
     *
     * @param textView 用于显示日志的 TextView 控件
     */
    public static void setTextView(TextView textView)
    {
        textView.setMovementMethod(ScrollingMovementMethod.getInstance());
        logger = new Logger(textView);
    }

    private static class Logger
    {
        private TextView textView;
        public Logger(TextView textView)
        {
            this.textView = textView;
        }

        private String genTime()
        {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("[HH:mm:ss]");
            String time = simpleDateFormat.format(new Date());
            return time;
        }

        private void log(String info)
        {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(genTime() + " " + info + "\n");
            textView.post(new Runnable()
            {
                @Override
                public void run()
                {
                    textView.append(stringBuilder);
                }
            });
        }
    }
}
