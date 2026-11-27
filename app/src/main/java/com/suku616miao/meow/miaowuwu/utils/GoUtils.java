package com.suku616miao.meow.miaowuwu.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Toast;

import androidx.core.location.LocationManagerCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GoUtils {
    public static boolean isDeveloperOptionsEnabled(Context context) {
        return Settings.Global.getInt(
                context.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
        ) == 1;
    }

    // WIFI是否可用
    public static boolean isWifiConnected(Context context) {
        // 从 API 29 开始，NetworkInfo 被标记为过时，这里更换新方法
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network nw = connectivityManager.getActiveNetwork();
        if (nw == null) {
            return false;
        }
        NetworkCapabilities actNw = connectivityManager.getNetworkCapabilities(nw);
        return actNw != null && (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
    }

    public static boolean isWifiEnabled(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        return wifiManager.isWifiEnabled();
    }

    //MOBILE网络是否可用
    public static boolean isMobileConnected(Context context) {
        // 从 API 29 开始，NetworkInfo 被标记为过时，这里更换新方法
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network nw = connectivityManager.getActiveNetwork();
        if (nw == null) {
            return false;
        }
        NetworkCapabilities actNw = connectivityManager.getNetworkCapabilities(nw);
        return actNw != null && (actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
    }

    // 断是否有网络连接，但是如果该连接的网络无法上网，也会返回true
    public static boolean isNetworkConnected(Context context) {
        // 从 API 29 开始，NetworkInfo 被标记为过时，这里更换新方法
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network nw = connectivityManager.getActiveNetwork();
        if (nw == null) return false;
        NetworkCapabilities actNw = connectivityManager.getNetworkCapabilities(nw);
        return actNw != null && (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) || actNw.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH));
    }

    //网络是否可用
    public static boolean isNetworkAvailable(Context context) {
        return ((isWifiConnected(context) || isMobileConnected(context)) && isNetworkConnected(context));
    }

    // 检查系统位置总开关；部分 ROM 可通过 network/fused 定位而不启用 GPS provider
    public static  boolean isGpsOpened(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return LocationManagerCompat.isLocationEnabled(locationManager);
    }

    // 读取模拟位置 AppOps，不通过添加和删除 provider 测试权限
    public static boolean isAllowMockLocation(Context context) {
        android.app.AppOpsManager appOps = context.getSystemService(android.app.AppOpsManager.class);
        return appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_MOCK_LOCATION,
                android.os.Process.myUid(), context.getPackageName()) == android.app.AppOpsManager.MODE_ALLOWED;
    }
    /**
     * [获取应用程序版本名称]
     * @param context context
     * @return 当前应用的版本名称
     */
    public static synchronized String getVersionName(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();

            PackageInfo packageInfo = packageManager.getPackageInfo(
                    context.getPackageName(), 0);

            return packageInfo.versionName;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * 获取App的名称
     * @param context 上下文
     * @return 名称
     */
    public static String getAppName(Context context) {
        PackageManager pm = context.getPackageManager();
        //获取包信息
        try {
            PackageInfo packageInfo = pm.getPackageInfo(context.getPackageName(), 0);
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
            int labelRes = applicationInfo.labelRes;
            return context.getResources().getString(labelRes);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static  String timeStamp2Date(String seconds) {
        if (seconds == null || seconds.isEmpty() || seconds.equals("null")) {
            return "";
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        return sdf.format(new Date(Long.parseLong(seconds + "000")));
    }

    //提醒开启位置模拟的弹框
    public static  void showEnableMockLocationDialog(Context context) {
        new MaterialAlertDialogBuilder(context)
                .setTitle("启用位置模拟")//这里是表头的内容
                .setMessage("请在\"开发者选项→选择模拟位置信息应用\"中进行设置")//这里是中间显示的具体信息
                .setPositiveButton("设置",(dialog, which) -> {
                    SystemSettingsNavigator.openDeveloperOptions(context);
                })
                .setNegativeButton("取消",(dialog, which) -> {
                })
                .show();
    }

    //提醒开启悬浮窗的弹框
    public static  void showEnableFloatWindowDialog(Context context) {
        new MaterialAlertDialogBuilder(context)
                .setTitle("启用悬浮窗")//这里是表头的内容
                .setMessage("为了模拟定位的稳定性，建议开启\"显示悬浮窗\"选项")//这里是中间显示的具体信息
                .setPositiveButton("设置",(dialog, which) -> {
                    SystemSettingsNavigator.openOverlayPermission(context);
                })
                .setNegativeButton("取消", (dialog, which) -> {

                })
                .show();
    }

    //显示开启GPS的提示
    public static  void showEnableGpsDialog(Context context) {
        new MaterialAlertDialogBuilder(context)
                .setTitle("启用定位服务")//这里是表头的内容
                .setMessage("请在系统设置中开启定位服务")//这里是中间显示的具体信息
                .setPositiveButton("确定",(dialog, which) -> {
                    SystemSettingsNavigator.openLocation(context);
                })
                .setNegativeButton("取消",(dialog, which) -> {

                })
                .show();
    }

    // 提醒开启位置模拟的弹框
    public static  void showDisableWifiDialog(Context context) {
        new MaterialAlertDialogBuilder(context)
                .setTitle("警告")
                .setMessage("开启 WIFI 后（即使没有连接热点）将导致定位闪回真实位置。建议关闭 WIFI，使用移动流量进行游戏！")
                .setPositiveButton("去关闭",(dialog, which) -> {
                    SystemSettingsNavigator.openWifi(context);
                })
                .setNegativeButton("忽略",(dialog, which) -> {
                })
                .show();
    }

    public static  void DisplayToast(Context context, String str) {
        Toast toast = Toast.makeText(context, str, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.TOP, 0, 100);
        toast.show();
    }

    /* 计数器类 */
    public static class TimeCount extends CountDownTimer {
        private TimeCountListener mListener;

        public TimeCount(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);//参数依次为总时长,和计时的时间间隔
        }

        @Override
        public void onFinish() {//计时完毕时触发
            mListener.onFinish();
        }

        @Override
        public void onTick(long millisUntilFinished) { //计时过程显示
            mListener.onTick(millisUntilFinished);
        }

        public void setListener(TimeCountListener mListener) {
            this.mListener = mListener;
        }

        public interface TimeCountListener {
            void onTick(long millisUntilFinished);
            void onFinish();
        }
    }
}
