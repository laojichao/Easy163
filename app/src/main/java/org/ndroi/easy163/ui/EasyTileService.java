package org.ndroi.easy163.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.net.VpnService;
import android.os.IBinder;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import org.ndroi.easy163.vpn.LocalVPNService;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * 快速设置磁贴服务，提供通知栏快捷开关控制 VPN 服务的启停。
 * 用户可通过下拉通知栏中的磁贴一键切换 VPN 连接状态。
 *
 * @author ndroi
 */
public class EasyTileService extends TileService
{
    /**
     * 磁贴可见时回调，同步显示 VPN 当前运行状态。
     * 根据 {@link LocalVPNService#getIsRunning()} 的返回值
     * 将磁贴设置为激活或未激活状态。
     */
    @Override
    public void onStartListening()
    {
        super.onStartListening();
        Log.d("EasyTileService", "onStartListening");
        Tile tile = getQsTile();
        if (tile == null)
        {
            return;
        }
        if(LocalVPNService.getIsRunning())
        {
            tile.setState(Tile.STATE_ACTIVE);
            tile.updateTile();
        }else
        {
            tile.setState(Tile.STATE_INACTIVE);
            tile.updateTile();
        }
    }

    /**
     * 绑定时回调，请求系统监听磁贴状态变化。
     *
     * @param intent 绑定意图
     * @return 返回父类提供的 IBinder 对象
     */
    @Override
    public IBinder onBind(Intent intent)
    {
        TileService.requestListeningState(this, new ComponentName(this, EasyTileService.class));
        return super.onBind(intent);
    }

    /**
     * 点击磁贴时回调，根据当前磁贴状态切换 VPN 的运行与停止。
     * 激活状态点击则停止 VPN，未激活状态点击则启动 VPN。
     */
    @Override
    public void onClick()
    {
        super.onClick();
        Tile tile = getQsTile();
        if (tile == null)
        {
            return;
        }
        switch (tile.getState())
        {
            case Tile.STATE_ACTIVE:
            {
                stopVPN();
                tile.setState(Tile.STATE_INACTIVE);
                tile.updateTile();
                break;
            }
            case Tile.STATE_INACTIVE:
            {
                startVPN();
                tile.setState(Tile.STATE_ACTIVE);
                tile.updateTile();
                break;
            }
            default:break;
        }
    }

    /**
     * 启动 VPN 服务。
     * 若用户已授权 VPN 权限，则直接启动 {@link LocalVPNService}；
     * 否则跳转至 {@link MainActivity} 请求 VPN 授权。
     */
    private void startVPN()
    {
        if (VpnService.prepare(this) == null)
        {
            Intent intent = new Intent(this, LocalVPNService.class);
            startService(intent);
        } else
        {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityAndCollapse(intent);
        }
    }

    /**
     * 通过本地广播发送停止命令来停止 VPN 服务。
     */
    private void stopVPN()
    {
        Intent intent = new Intent("control");
        intent.putExtra("cmd", "stop");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        Log.d("stopVPN", "try to stopVPN");
    }
}
