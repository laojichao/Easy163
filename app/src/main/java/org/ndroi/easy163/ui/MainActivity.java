package org.ndroi.easy163.ui;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import com.google.android.material.navigation.NavigationView;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.ToggleButton;
import org.ndroi.easy163.BuildConfig;
import org.ndroi.easy163.R;
import org.ndroi.easy163.core.Cache;
import org.ndroi.easy163.core.Local;
import org.ndroi.easy163.utils.EasyLog;
import org.ndroi.easy163.vpn.LocalVPNService;
import static androidx.appcompat.app.AlertDialog.Builder;

/**
 * 应用主界面 Activity，提供 VPN 开关、侧边栏导航、日志显示等功能。
 * 实现了侧边栏菜单项点击监听和 VPN 开关按钮状态变化回调。
 *
 * @author ndroi
 */
public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, ToggleButton.OnCheckedChangeListener
{
    private static final int VPN_REQUEST_CODE = 0x0F;
    private ToggleButton toggleButton = null;
    private static boolean isBroadcastReceived = false; // workaround for multi-receive

    /**
     * 重置广播接收状态标记，允许下一次广播被正常处理。
     * 用于解决广播多次接收的问题。
     */
    public static void resetBroadcastReceivedState()
    {
        isBroadcastReceived = false;
    }

    private BroadcastReceiver serviceReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (isBroadcastReceived) return;
            isBroadcastReceived = true;
            boolean isServiceRunning = intent.getBooleanExtra("isRunning", false);
            Log.d("MainActivity", "BroadcastReceiver service isRunning: " + isServiceRunning);
            toggleButton.setChecked(isServiceRunning);
            if(isServiceRunning)
            {
                EasyLog.log("Easy163 VPN 正在运行");
                EasyLog.log("版本更新关注 Github Release");
            }else
            {
                EasyLog.log("Easy163 VPN 停止运行");
            }
        }
    };

    /**
     * 初始化界面组件、注册本地广播接收器、同步 VPN 服务状态。
     * 设置工具栏、侧边栏、VPN 开关按钮及日志显示区域。
     *
     * @param savedInstanceState 保存的实例状态，可为 null
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        LocalBroadcastManager.getInstance(this).registerReceiver(serviceReceiver, new IntentFilter("service"));
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        toggleButton = findViewById(R.id.bt_start);
        toggleButton.setOnCheckedChangeListener(this);
        EasyLog.setTextView(findViewById(R.id.log));
        syncServiceState();
    }

    /**
     * Activity 销毁时注销本地广播接收器，防止内存泄漏。
     */
    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceReceiver);
    }

    /**
     * 处理返回键事件。
     * 若侧边栏处于打开状态则关闭侧边栏；
     * 否则将应用最小化到后台而非退出。
     */
    @Override
    public void onBackPressed()
    {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START))
        {
            drawer.closeDrawer(GravityCompat.START);
        } else
        {
            //super.onBackPressed();
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addCategory(Intent.CATEGORY_HOME);
            startActivity(intent);
        }
    }

    /**
     * 处理侧边栏菜单项点击事件。
     * 支持的菜单项包括：GitHub 链接、使用说明、免责声明、清除缓存、关于。
     *
     * @param item 被点击的菜单项
     * @return 始终返回 true
     */
    @Override
    public boolean onNavigationItemSelected(MenuItem item)
    {
        int id = item.getItemId();
        if (id == R.id.nav_github)
        {
            Uri uri = Uri.parse("https://github.com/ccclao/easy163");
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        } else if (id == R.id.nav_usage)
        {
            Builder builder = new Builder(this);
            builder.setTitle("使用说明");
            builder.setMessage("开启本软件 VPN 服务后即可使用\n" +
                    "如无法启动 VPN 尝试重启手机\n" +
                    "出现异常问题尝试情况软件缓存\n" +
                    "更多问题请查阅 Github");
            builder.setNegativeButton("取消", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    dialog.dismiss();
                }
            });
            builder.show();
        } else if (id == R.id.nav_statement)
        {
            Builder builder = new Builder(this);
            builder.setTitle("免责声明");
            builder.setMessage("本软件为实验性项目\n" +
                    "仅提供技术研究使用\n" +
                    "本软件完全免费\n" +
                    "作者不承担用户因软件造成的一切责任");
            builder.setNegativeButton("取消", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    dialog.dismiss();
                }
            });
            builder.show();
        } else if (id == R.id.nav_clear_cache)
        {
            Cache.clear();
            Local.clear();
            Toast.makeText(this, "缓存已清除", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.nav_about)
        {
            Builder builder = new Builder(this);
            builder.setTitle("关于");
            builder.setMessage("当前版本 " + BuildConfig.VERSION_NAME + "\n" +
                    "版本更新关注 Github Release");
            builder.setNegativeButton("取消", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    dialog.dismiss();
                }
            });
            builder.show();
        }
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * VPN 开关按钮状态变化回调。
     * 选中时启动 VPN 服务，取消选中时停止 VPN 服务。
     *
     * @param buttonView 状态发生变化的按钮视图
     * @param isChecked  按钮是否被选中
     */
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
    {
        if (isChecked)
        {
            startVPN();
        } else
        {
            stopVPN();
        }
    }

    /**
     * 通过本地广播向 VPN 服务发送查询命令，同步当前运行状态到 UI。
     */
    private void syncServiceState()
    {
        Intent intent = new Intent("control");
        intent.putExtra("cmd", "check");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    /**
     * 请求 VPN 权限并启动 VPN 服务。
     * 若用户尚未授权，则发起授权请求；已授权则直接启动服务。
     */
    private void startVPN()
    {
        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent != null)
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
        else
            onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
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

    /**
     * 处理 Activity 返回的结果。
     * 当 VPN 权限请求通过时，启动 {@link LocalVPNService}。
     *
     * @param requestCode 请求码
     * @param resultCode  结果码
     * @param data        返回数据，可为 null
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK)
        {
            Intent intent = new Intent(this, LocalVPNService.class);
            startService(intent);
        }
    }
}