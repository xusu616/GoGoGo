package com.suku616miao.meow.miaowuwu.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.elvishew.xlog.XLog;
import com.suku616miao.meow.miaowuwu.MainActivity;
import com.suku616miao.meow.miaowuwu.R;
import com.suku616miao.meow.miaowuwu.joystick.JoyStick;
import com.suku616miao.meow.miaowuwu.route.RoutePath;
import com.suku616miao.meow.miaowuwu.route.RoutePlayer;
import com.suku616miao.meow.miaowuwu.route.RoutePoint;

import java.util.ArrayList;
import java.util.List;

public class ServiceGo extends Service {
    // 定位相关变量
    public static final double DEFAULT_LAT = 36.667662;
    public static final double DEFAULT_LNG = 117.027707;
    public static final double DEFAULT_ALT = 55.0D;
    public static final float DEFAULT_BEA = 0.0F;
    private double mCurLat = DEFAULT_LAT;
    private double mCurLng = DEFAULT_LNG;
    private double mCurAlt = DEFAULT_ALT;
    private float mCurBea = DEFAULT_BEA;
    private double mSpeed = 1.2;        /* 默认的速度，单位 m/s */
    private static final int HANDLER_MSG_ID = 1;
    private static final String SERVICE_GO_HANDLER_NAME = "ServiceGoLocation";
    private LocationManager mLocManager;
    private HandlerThread mLocHandlerThread;
    private Handler mLocHandler;
    private volatile boolean isStop = false;
    public static final int ROUTE_NONE = 0;
    public static final int ROUTE_PLAYING = 1;
    public static final int ROUTE_PAUSED = 2;
    public static final int ROUTE_FINISHED = 3;
    private volatile int mRouteState = ROUTE_NONE;
    private RoutePlayer mRoutePlayer;
    private double mRouteSpeed;
    private long mLastRouteTick;
    private JoyStick mJoyStick;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private volatile PositionSnapshot mJoyStickPosition;
    private final Runnable mUpdateJoyStickPosition = () -> {
        if (!isStop) {
            PositionSnapshot position = mJoyStickPosition;
            mJoyStick.updateCurrentPosition(position.longitude, position.latitude, position.altitude);
        }
    };
    // 通知栏消息
    private static final int SERVICE_GO_NOTE_ID = 1;
    private static final String SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW = "ShowJoyStick";
    private static final String SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE = "HideJoyStick";
    private static final String SERVICE_GO_NOTE_CHANNEL_ID = "SERVICE_GO_NOTE";
    private static final String SERVICE_GO_NOTE_CHANNEL_NAME = "SERVICE_GO_NOTE";
    private NoteActionReceiver mActReceiver;
    // 摇杆相关

    private final ServiceGoBinder mBinder = new ServiceGoBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mLocManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        removeTestProviderNetwork();
        addTestProviderNetwork();

        removeTestProviderGPS();
        addTestProviderGPS();

        initGoLocation();

        initNotification();

        initJoyStick();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        double longitude = intent.getDoubleExtra(MainActivity.LNG_MSG_ID, DEFAULT_LNG);
        double latitude = intent.getDoubleExtra(MainActivity.LAT_MSG_ID, DEFAULT_LAT);
        double altitude = intent.getDoubleExtra(MainActivity.ALT_MSG_ID, DEFAULT_ALT);
        mLocHandler.post(() -> {
            if (mRouteState == ROUTE_NONE) {
                mCurLng = longitude;
                mCurLat = latitude;
                mCurAlt = altitude;
                mSpeed = 0.0;
                publishJoyStickPosition();
            }
            mLocHandler.removeMessages(HANDLER_MSG_ID);
            mLocHandler.sendEmptyMessage(HANDLER_MSG_ID);
        });

        // A route lives in this service instance; restart must not invent a new default position.
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        isStop = true;
        mLocHandler.removeMessages(HANDLER_MSG_ID);
        mMainHandler.removeCallbacksAndMessages(null);
        mLocHandlerThread.quitSafely();

        mJoyStick.destroy();

        removeTestProviderNetwork();
        removeTestProviderGPS();

        unregisterReceiver(mActReceiver);
        stopForeground(STOP_FOREGROUND_REMOVE);

        super.onDestroy();
    }

    private void initNotification() {
        mActReceiver = new NoteActionReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW);
        filter.addAction(SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE);
        registerReceiver(mActReceiver, filter);

        NotificationChannel mChannel = new NotificationChannel(SERVICE_GO_NOTE_CHANNEL_ID, SERVICE_GO_NOTE_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.createNotificationChannel(mChannel);
        }

        //准备intent
        Intent clickIntent = new Intent(this, MainActivity.class);
        PendingIntent clickPI = PendingIntent.getActivity(this, 1, clickIntent, PendingIntent.FLAG_IMMUTABLE);
        Intent showIntent = new Intent(SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW);
        PendingIntent showPendingPI = PendingIntent.getBroadcast(this, 0, showIntent, PendingIntent.FLAG_IMMUTABLE);
        Intent hideIntent = new Intent(SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE);
        PendingIntent hidePendingPI = PendingIntent.getBroadcast(this, 0, hideIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, SERVICE_GO_NOTE_CHANNEL_ID)
                .setChannelId(SERVICE_GO_NOTE_CHANNEL_ID)
                .setContentTitle(getResources().getString(R.string.app_name))
                .setContentText(getResources().getString(R.string.app_service_tips))
                .setContentIntent(clickPI)
                .addAction(new NotificationCompat.Action(null, getResources().getString(R.string.note_show), showPendingPI))
                .addAction(new NotificationCompat.Action(null, getResources().getString(R.string.note_hide), hidePendingPI))
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();

        startForeground(SERVICE_GO_NOTE_ID, notification);
    }

    private void initJoyStick() {
        mJoyStick = new JoyStick(this);
        mJoyStick.setListener(new JoyStick.JoyStickClickListener() {
            @Override
            public void onMoveInfo(double speed, double disLng, double disLat, double angle) {
                mLocHandler.post(() -> {
                    if (mRouteState == ROUTE_PLAYING || mRouteState == ROUTE_PAUSED) {
                        return;
                    }
                    clearFinishedRoute();
                    mSpeed = speed;
                    // 根据当前的经纬度和距离，计算下一个经纬度。
                    // 纬度每度约 110.574 km，经度每度约 111.320*cos(latitude) km。
                    mCurLng += disLng / (111.320 * Math.cos(Math.abs(mCurLat) * Math.PI / 180));
                    mCurLat += disLat / 110.574;
                    mCurBea = (float) angle;
                });
            }

            @Override
            public void onPositionInfo(double lng, double lat, double alt) {
                mLocHandler.post(() -> {
                    if (mRouteState == ROUTE_PLAYING || mRouteState == ROUTE_PAUSED) {
                        return;
                    }
                    clearFinishedRoute();
                    mCurLng = lng;
                    mCurLat = lat;
                    mCurAlt = alt;
                    mSpeed = 0.0;
                });
            }
        });
        mJoyStick.show();
    }

    private void initGoLocation() {
        // 创建 HandlerThread 实例，第一个参数是线程的名字
        mLocHandlerThread = new HandlerThread(SERVICE_GO_HANDLER_NAME, Process.THREAD_PRIORITY_FOREGROUND);
        // 启动 HandlerThread 线程
        mLocHandlerThread.start();
        // Handler 对象与 HandlerThread 的 Looper 对象的绑定
        mLocHandler = new Handler(mLocHandlerThread.getLooper()) {
            // 这里的Handler对象可以看作是绑定在HandlerThread子线程中，所以handlerMessage里的操作是在子线程中运行的
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (isStop) {
                    return;
                }
                updateRoutePosition();
                setLocationNetwork();
                setLocationGPS();
                publishJoyStickPosition();
                sendEmptyMessageDelayed(HANDLER_MSG_ID, 100);
            }
        };
    }

    private void updateRoutePosition() {
        if (mRouteState != ROUTE_PLAYING) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        mRoutePlayer.advance((now - mLastRouteTick) / 1000.0);
        mLastRouteTick = now;
        RoutePoint position = mRoutePlayer.getPosition();
        mCurLat = position.getLatitude();
        mCurLng = position.getLongitude();
        mCurBea = (float) mRoutePlayer.getBearingDegrees();
        if (mRoutePlayer.isComplete()) {
            mSpeed = 0.0;
            mRouteState = ROUTE_FINISHED;
            setJoyStickRoutePlaybackActive(false);
        }
    }

    private void publishJoyStickPosition() {
        mJoyStickPosition = new PositionSnapshot(mCurLng, mCurLat, mCurAlt);
        mMainHandler.removeCallbacks(mUpdateJoyStickPosition);
        mMainHandler.post(mUpdateJoyStickPosition);
    }

    private void setJoyStickRoutePlaybackActive(boolean active) {
        mMainHandler.post(() -> {
            if (!isStop) {
                mJoyStick.setRoutePlaybackActive(active);
            }
        });
    }

    private void clearFinishedRoute() {
        if (mRouteState == ROUTE_FINISHED) {
            mRoutePlayer = null;
            mRouteState = ROUTE_NONE;
        }
    }

    private static final class PositionSnapshot {
        final double longitude;
        final double latitude;
        final double altitude;

        PositionSnapshot(double longitude, double latitude, double altitude) {
            this.longitude = longitude;
            this.latitude = latitude;
            this.altitude = altitude;
        }
    }

    private void removeTestProviderGPS() {
        try {
            if (mLocManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false);
                mLocManager.removeTestProvider(LocationManager.GPS_PROVIDER);
            }
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - removeTestProviderGPS");
        }
    }

    // 注意下面临时添加 @SuppressLint("wrongconstant") 以处理 addTestProvider 参数值的 lint 错误
    @SuppressLint("wrongconstant")
    private void addTestProviderGPS() {
        try {
            // 注意，由于 android api 问题，下面的参数会提示错误(以下参数是通过相关API获取的真实GPS参数，不是随便写的)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mLocManager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false,
                        false, true, true, true, ProviderProperties.POWER_USAGE_HIGH, ProviderProperties.ACCURACY_FINE);
            } else {
                mLocManager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false,
                        false, true, true, true, Criteria.POWER_HIGH, Criteria.ACCURACY_FINE);
            }
            if (!mLocManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
            }
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - addTestProviderGPS");
        }
    }

    private void setLocationGPS() {
        try {
            // 尽可能模拟真实的 GPS 数据
            Location loc = new Location(LocationManager.GPS_PROVIDER);
            loc.setAccuracy(Criteria.ACCURACY_FINE);    // 设定此位置的估计水平精度，以米为单位。
            loc.setAltitude(mCurAlt);                     // 设置高度，在 WGS 84 参考坐标系中的米
            loc.setBearing(mCurBea);                       // 方向（度）
            loc.setLatitude(mCurLat);                   // 纬度（度）
            loc.setLongitude(mCurLng);                  // 经度（度）
            loc.setTime(System.currentTimeMillis());    // 本地时间
            loc.setSpeed((float) mSpeed);
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            Bundle bundle = new Bundle();
            bundle.putInt("satellites", 7);
            loc.setExtras(bundle);

            mLocManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, loc);
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - setLocationGPS");
        }
    }

    private void removeTestProviderNetwork() {
        try {
            if (mLocManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, false);
                mLocManager.removeTestProvider(LocationManager.NETWORK_PROVIDER);
            }
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - removeTestProviderNetwork");
        }
    }

    // 注意下面临时添加 @SuppressLint("wrongconstant") 以处理 addTestProvider 参数值的 lint 错误
    @SuppressLint("wrongconstant")
    private void addTestProviderNetwork() {
        try {
            // 注意，由于 android api 问题，下面的参数会提示错误(以下参数是通过相关API获取的真实NETWORK参数，不是随便写的)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mLocManager.addTestProvider(LocationManager.NETWORK_PROVIDER, true, false,
                        true, true, true, true,
                        true, ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_COARSE);
            } else {
                mLocManager.addTestProvider(LocationManager.NETWORK_PROVIDER, true, false,
                        true, true, true, true,
                        true, Criteria.POWER_LOW, Criteria.ACCURACY_COARSE);
            }
            if (!mLocManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
            }
        } catch (SecurityException e) {
            XLog.e("SERVICEGO: ERROR - addTestProviderNetwork");
        }
    }

    private void setLocationNetwork() {
        try {
            // 尽可能模拟真实的 NETWORK 数据
            Location loc = new Location(LocationManager.NETWORK_PROVIDER);
            loc.setAccuracy(Criteria.ACCURACY_COARSE);  // 设定此位置的估计水平精度，以米为单位。
            loc.setAltitude(mCurAlt);                     // 设置高度，在 WGS 84 参考坐标系中的米
            loc.setBearing(mCurBea);                       // 方向（度）
            loc.setLatitude(mCurLat);                   // 纬度（度）
            loc.setLongitude(mCurLng);                  // 经度（度）
            loc.setTime(System.currentTimeMillis());    // 本地时间
            loc.setSpeed((float) mSpeed);
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

            mLocManager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, loc);
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - setLocationNetwork");
        }
    }

    public class NoteActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                if (action.equals(SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW)) {
                    mJoyStick.show();
                }

                if (action.equals(SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE)) {
                    mJoyStick.hide();
                }
            }
        }
    }

    public class ServiceGoBinder extends Binder {
        public void setPosition(double lng, double lat, double alt) {
            mLocHandler.post(() -> {
                if (mRouteState == ROUTE_PLAYING || mRouteState == ROUTE_PAUSED) {
                    return;
                }
                clearFinishedRoute();
                mCurLng = lng;
                mCurLat = lat;
                mCurAlt = alt;
                mSpeed = 0.0;
                publishJoyStickPosition();
            });
        }

        public void startRoute(List<RoutePoint> points, double speed, double altitude) {
            RoutePlayer player = new RoutePlayer(new RoutePath(new ArrayList<>(points)), speed);
            mLocHandler.post(() -> {
                mRoutePlayer = player;
                mRouteSpeed = speed;
                mSpeed = speed;
                mCurAlt = altitude;
                RoutePoint position = player.getPosition();
                mCurLng = position.getLongitude();
                mCurLat = position.getLatitude();
                mCurBea = (float) player.getBearingDegrees();
                mLastRouteTick = SystemClock.elapsedRealtime();
                mRouteState = ROUTE_PLAYING;
                setJoyStickRoutePlaybackActive(true);
                publishJoyStickPosition();
            });
        }

        public void pauseRoute() {
            mLocHandler.post(() -> {
                if (mRouteState == ROUTE_PLAYING) {
                    updateRoutePosition();
                    if (mRouteState == ROUTE_PLAYING) {
                        mSpeed = 0.0;
                        mRouteState = ROUTE_PAUSED;
                    }
                }
            });
        }

        public void resumeRoute() {
            mLocHandler.post(() -> {
                if (mRouteState == ROUTE_PAUSED) {
                    mLastRouteTick = SystemClock.elapsedRealtime();
                    mSpeed = mRouteSpeed;
                    mRouteState = ROUTE_PLAYING;
                }
            });
        }

        public void stopRoute() {
            mLocHandler.post(() -> {
                mRoutePlayer = null;
                mRouteState = ROUTE_NONE;
                mSpeed = 0.0;
                setJoyStickRoutePlaybackActive(false);
            });
        }

        public int getRouteState() {
            return mRouteState;
        }
    }
}


