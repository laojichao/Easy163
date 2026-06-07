package org.ndroi.easy163.vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.service.quicksettings.TileService;
import android.util.Log;
import org.ndroi.easy163.R;
import org.ndroi.easy163.core.Cache;
import org.ndroi.easy163.core.Local;
import org.ndroi.easy163.core.Server;
import org.ndroi.easy163.ui.EasyTileService;
import org.ndroi.easy163.ui.MainActivity;
import org.ndroi.easy163.utils.EasyLog;
import org.ndroi.easy163.vpn.bio.BioTcpHandler;
import org.ndroi.easy163.vpn.bio.BioUdpHandler;
import org.ndroi.easy163.vpn.tcpip.Packet;
import org.ndroi.easy163.vpn.util.ByteBufferPool;
import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 本地 VPN 服务，基于 Android VpnService 实现网络流量拦截与转发，
 * 仅代理网易云音乐相关流量（com.netease.cloudmusic 及极速版）。
 * 通过 VPN 接口读取 IP 包，分发至 UDP/TCP 处理器，再将响应写回设备。
 *
 * @author ndroi
 */
public class LocalVPNService extends VpnService
{
    private static final String TAG = LocalVPNService.class.getSimpleName();
    private static final String VPN_ADDRESS = "10.0.0.2"; // Only IPv4 support for now
    private static final String VPN_ROUTE = "0.0.0.0"; // Intercept everything
    private ParcelFileDescriptor vpnInterface = null;
    private BlockingQueue<Packet> deviceToNetworkUDPQueue;
    private BlockingQueue<Packet> deviceToNetworkTCPQueue;
    private BlockingQueue<ByteBuffer> networkToDeviceQueue;
    private ExecutorService executorService;
    private static Boolean isRunning = false;
    private static Context context = null;

    /**
     * 获取应用全局上下文
     *
     * @return 应用的 Context 对象
     */
    public static Context getContext()
    {
        return context;
    }

    /**
     * 获取 VPN 服务运行状态
     *
     * @return VPN 服务是否正在运行
     */
    public static Boolean getIsRunning()
    {
        return isRunning;
    }

    private BroadcastReceiver stopReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String cmd = intent.getStringExtra("cmd");
            if(cmd.equals("stop"))
            {
                executorService.shutdownNow();
                cleanup();
                LocalVPNService.this.stopSelf();
            }else if(cmd.equals("check"))
            {
                Log.i(TAG, "checkServiceState received");
                sendState();
            }
        }
    };

    /**
     * 初始化 VPN 接口、网络队列、线程池，启动 UDP/TCP 处理器和 VPN 主循环，
     * 加载 DNS 缓存和本地配置，显示前台通知并广播运行状态。
     */
    @Override
    public void onCreate()
    {
        super.onCreate();
        context = getApplicationContext();
        setupVPN();
        LocalBroadcastManager.getInstance(this).registerReceiver(stopReceiver, new IntentFilter("control"));
        deviceToNetworkUDPQueue = new ArrayBlockingQueue<Packet>(1000);
        deviceToNetworkTCPQueue = new ArrayBlockingQueue<Packet>(1000);
        networkToDeviceQueue = new ArrayBlockingQueue<>(1000);
        executorService = Executors.newFixedThreadPool(3);
        executorService.submit(new BioUdpHandler(deviceToNetworkUDPQueue, networkToDeviceQueue, this));
        executorService.submit(new BioTcpHandler(deviceToNetworkTCPQueue, networkToDeviceQueue, this));
        executorService.submit(new VPNRunnable(vpnInterface.getFileDescriptor(),
                deviceToNetworkUDPQueue, deviceToNetworkTCPQueue, networkToDeviceQueue));
        startNotification();
        Server.getInstance().start();
        Cache.init();
        Local.load();
        isRunning = true;
        sendState();
        TileService.requestListeningState(this, new ComponentName(this, EasyTileService.class));
        Log.i(TAG, "Easy163 VPN 启动");
    }

    /**
     * 创建并显示前台服务通知，点击通知跳转至 MainActivity
     */
    private void startNotification()
    {
        String notificationId = "easy163";
        String notificationName = "easy163";
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            NotificationChannel channel = new NotificationChannel(notificationId, notificationName, NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 100, intent, 0);
        Notification.Builder builder = new Notification.Builder(this)
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.mipmap.icon)
                .setContentTitle("Easy163")
                .setContentText("正在运行...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            builder.setChannelId(notificationId);
        }
        Notification notification = builder.build();
        startForeground(1, notification);
    }

    /**
     * 配置 VPN 接口参数，设置虚拟 IP 地址和全局路由，
     * 并通过 {@code addAllowedApplication} 限制仅代理网易云音乐应用流量。
     * 若配置失败则记录错误日志并退出进程。
     */
    private void setupVPN()
    {
        try
        {
            if (vpnInterface == null)
            {
                Builder builder = new Builder();
                builder.addAddress(VPN_ADDRESS, 32);
                builder.addRoute(VPN_ROUTE, 0);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                {
                    try
                    {
                        builder.addAllowedApplication("com.netease.cloudmusic");
                    }catch (PackageManager.NameNotFoundException e)
                    {
                        Log.d(TAG, "未检测到网易云音乐");
                    }
                    try
                    {
                        builder.addAllowedApplication("com.netease.cloudmusic.lite");
                    }catch (PackageManager.NameNotFoundException e)
                    {
                        Log.d(TAG, "未检测到网易云音乐极速版");
                    }
                }
                vpnInterface = builder.setSession(getString(R.string.app_name)).establish();
            }
        } catch (Exception e)
        {
            Log.e(TAG, "Easy163 VPN 启动失败");
            EasyLog.log("Easy163 VPN 启动失败");
            System.exit(0);
        }
    }

    /**
     * 返回 START_STICKY 保证服务被系统杀死后自动重启
     *
     * @param intent  启动 Intent
     * @param flags   启动标志
     * @param startId 启动 ID
     * @return START_STICKY，确保服务被系统回收后自动重启
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        return START_STICKY;
    }

    /**
     * 停止线程池、清理资源、更新运行状态，
     * 并通知 TileService 更新快捷设置磁贴状态。
     */
    @Override
    public void onDestroy()
    {
        super.onDestroy();
        executorService.shutdownNow();
        cleanup();
        isRunning = false;
        sendState();
        TileService.requestListeningState(this, new ComponentName(this, EasyTileService.class));
        Log.i(TAG, "Stopped");
    }

    /**
     * 清空队列引用并关闭 VPN 接口
     */
    private void cleanup()
    {
        deviceToNetworkTCPQueue = null;
        deviceToNetworkUDPQueue = null;
        networkToDeviceQueue = null;
        closeResources(vpnInterface);
    }

    /**
     * 通过本地广播发送 VPN 服务运行状态，通知 MainActivity 更新 UI
     */
    private void sendState()
    {
        MainActivity.resetBroadcastReceivedState();
        Intent replyIntent=  new Intent("service");
        replyIntent.putExtra("isRunning", isRunning);
        LocalBroadcastManager.getInstance(this).sendBroadcast(replyIntent);
        Log.i(TAG, "sendState");
    }

    /**
     * 关闭可关闭资源，忽略 IO 异常
     *
     * @param resources 待关闭的 Closeable 资源数组
     */
    private static void closeResources(Closeable... resources)
    {
        for (Closeable resource : resources)
        {
            try
            {
                resource.close();
            } catch (IOException e)
            {
                // Ignore
            }
        }
    }

    /**
     * VPN 主循环线程，持续从 VPN 接口读取 IP 包，
     * 根据协议类型分发到 UDP 或 TCP 队列，同时启动 WriteVpnThread 处理下行数据。
     */
    private static class VPNRunnable implements Runnable
    {
        private static final String TAG = VPNRunnable.class.getSimpleName();

        private FileDescriptor vpnFileDescriptor;

        private BlockingQueue<Packet> deviceToNetworkUDPQueue;
        private BlockingQueue<Packet> deviceToNetworkTCPQueue;
        private BlockingQueue<ByteBuffer> networkToDeviceQueue;

        public VPNRunnable(FileDescriptor vpnFileDescriptor,
                           BlockingQueue<Packet> deviceToNetworkUDPQueue,
                           BlockingQueue<Packet> deviceToNetworkTCPQueue,
                           BlockingQueue<ByteBuffer> networkToDeviceQueue)
        {
            this.vpnFileDescriptor = vpnFileDescriptor;
            this.deviceToNetworkUDPQueue = deviceToNetworkUDPQueue;
            this.deviceToNetworkTCPQueue = deviceToNetworkTCPQueue;
            this.networkToDeviceQueue = networkToDeviceQueue;
        }

        /**
         * 从网络响应队列取数据写回 VPN 接口的线程
         */
        static class WriteVpnThread implements Runnable
        {
            FileChannel vpnOutput;
            private BlockingQueue<ByteBuffer> networkToDeviceQueue;

            WriteVpnThread(FileChannel vpnOutput, BlockingQueue<ByteBuffer> networkToDeviceQueue)
            {
                this.vpnOutput = vpnOutput;
                this.networkToDeviceQueue = networkToDeviceQueue;
            }

            @Override
            public void run()
            {
                while (true)
                {
                    try
                    {
                        ByteBuffer bufferFromNetwork = networkToDeviceQueue.take();
                        bufferFromNetwork.flip();
                        while (bufferFromNetwork.hasRemaining())
                        {
                            int w = vpnOutput.write(bufferFromNetwork);
                        }
                    } catch (Exception e)
                    {
                        Log.i(TAG, "WriteVpnThread fail", e);
                    }
                }
            }
        }

        @Override
        public void run()
        {
            FileChannel vpnInput = new FileInputStream(vpnFileDescriptor).getChannel();
            FileChannel vpnOutput = new FileOutputStream(vpnFileDescriptor).getChannel();
            Thread t = new Thread(new WriteVpnThread(vpnOutput, networkToDeviceQueue));
            t.start();
            try
            {
                while (!Thread.interrupted())
                {
                    ByteBuffer bufferToNetwork = ByteBufferPool.acquire();
                    int readBytes = vpnInput.read(bufferToNetwork);
                    if (readBytes > 0)
                    {
                        bufferToNetwork.flip();
                        Packet packet = new Packet(bufferToNetwork);
                        if (packet.isUDP())
                        {
                            deviceToNetworkUDPQueue.offer(packet);
                        } else if (packet.isTCP())
                        {
                            deviceToNetworkTCPQueue.offer(packet);
                        } else
                        {
                            //Log.w(TAG, String.format("Unknown packet protocol type %d", packet.ip4Header.protocolNum));
                        }
                    } else
                    {
                        try
                        {
                            Thread.sleep(50);
                        } catch (InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (IOException e)
            {
                Log.w(TAG, e.toString(), e);
            } finally
            {
                closeResources(vpnInput, vpnOutput);
            }
        }
    }
}
