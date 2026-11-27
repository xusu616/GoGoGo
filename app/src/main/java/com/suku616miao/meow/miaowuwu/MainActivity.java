package com.suku616miao.meow.miaowuwu;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;

import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.InfoWindow;
import com.baidu.mapapi.map.MapPoi;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.geocode.GeoCodeResult;
import com.baidu.mapapi.search.geocode.GeoCoder;
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult;
import com.baidu.mapapi.search.sug.SuggestionResult;
import com.baidu.mapapi.search.sug.SuggestionSearch;
import com.baidu.mapapi.search.sug.SuggestionSearchOption;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.suku616miao.meow.miaowuwu.service.ServiceGo;
import com.suku616miao.meow.miaowuwu.database.DataBaseHistoryLocation;
import com.suku616miao.meow.miaowuwu.database.DataBaseHistorySearch;
import com.suku616miao.meow.miaowuwu.utils.ShareUtils;
import com.suku616miao.meow.miaowuwu.utils.GoUtils;
import com.suku616miao.meow.miaowuwu.utils.MapUtils;
import com.suku616miao.meow.miaowuwu.route.RouteEditor;
import com.suku616miao.meow.miaowuwu.route.RoutePoint;
import com.suku616miao.meow.miaowuwu.update.ReleaseVersion;
import com.suku616miao.meow.miaowuwu.ui.AppNavigationController;
import com.suku616miao.meow.miaowuwu.ui.ConsentFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.elvishew.xlog.XLog;

import io.noties.markwon.Markwon;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends BaseActivity implements SensorEventListener {
    /* 对外 */
    public static final String LAT_MSG_ID = "LAT_VALUE";
    public static final String LNG_MSG_ID = "LNG_VALUE";
    public static final String ALT_MSG_ID = "ALT_VALUE";

    public static final String POI_NAME = "POI_NAME";
    public static final String POI_ADDRESS = "POI_ADDRESS";
    public static final String POI_LONGITUDE = "POI_LONGITUDE";
    public static final String POI_LATITUDE = "POI_LATITUDE";

    private OkHttpClient mOkHttpClient;
    private SharedPreferences sharedPreferences;

    /*============================== 主界面地图 相关 ==============================*/
    /************** 地图 *****************/
    private BitmapDescriptor mMapIndicator;
    public static String mCurrentCity = null;
    private MapView mMapView;
    private BaiduMap mBaiduMap;
    private static final LatLng DEFAULT_MAP_CENTER = new LatLng(36.547743718042415, 117.07018449827267);
    private LatLng mMarkLatLngMap;
    private String mMarkName;
    private GeoCoder mGeoCoder;
    private GeoCoder mLocationGeoCoder;
    private Location mLastCityLookupAttempt;
    private Location mLastRealLocation;
    private boolean mCityLookupPending;
    private boolean mCityLookupFailed;
    private static final float CITY_REFRESH_DISTANCE_METERS = 1000f;
    private SensorManager mSensorManager;
    private Sensor mSensorAccelerometer;
    private Sensor mSensorMagnetic;
    private float[] mAccValues = new float[3];//加速度传感器数据
    private float[] mMagValues = new float[3];//地磁传感器数据
    private final float[] mR = new float[9];//旋转矩阵，用来保存磁场和加速度的数据
    private final float[] mDirectionValues = new float[3];//模拟方向传感器的数据（原始数据为弧度）
    /************** 定位 *****************/
    private SystemLocationSource mRealLocationSource;
    private double mCurrentLat = 0.0;       // 当前位置的百度纬度
    private double mCurrentLon = 0.0;       // 当前位置的百度经度
    private float mCurrentDirection = 0.0f;
    private boolean isFirstLoc = true; // 是否首次定位
    private boolean mHasLocation;
    private boolean isMockServStart = false;
    private boolean mIsServiceBound = false;
    private ServiceGo.ServiceGoBinder mServiceBinder;
    private RouteEditor mRouteEditor;
    private Marker mSelectedMarker;
    private final Handler mRouteUiHandler = new Handler(Looper.getMainLooper());
    private List<RoutePoint> mPendingRoute;
    private double mPendingRouteSpeed;
    private boolean mPendingRouteRepeat;
    private double[] mPendingPosition;
    private boolean mInitialized;
    private boolean mActivityResumed;
    private AppNavigationController mNavigation;
    private boolean mMapMenuVisible = true;
    private final Runnable mRouteStatusPoll = new Runnable() {
        @Override
        public void run() {
            if (mServiceBinder != null) {
                if (handleSimulationFailure()) return;
                mRouteEditor.updatePlayback(mServiceBinder.getRouteSnapshot());
            }
            mRouteUiHandler.postDelayed(this, 100);
        }
    };
    private ServiceConnection mConnection;
    private FloatingActionButton mButtonStart;
    /*============================== 历史记录 相关 ==============================*/
    private SQLiteDatabase mLocationHistoryDB;
    private SQLiteDatabase mSearchHistoryDB;
    /*============================== SearchView 相关 ==============================*/
    private SearchView searchView;
    private ListView mSearchList;
    private LinearLayout mSearchLayout;
    private ListView mSearchHistoryList;
    private LinearLayout mHistoryLayout;
    private MenuItem searchItem;
    private SuggestionSearch mSuggestionSearch;
    /*============================== 更新 相关 ==============================*/
    private DownloadManager mDownloadManager = null;
    private long mDownloadId;
    private BroadcastReceiver mDownloadBdRcv;
    private String mUpdateFilename;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceManager.setDefaultValues(this, R.xml.preferences_main, false);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (!sharedPreferences.getBoolean("KEY_ACCEPT_AGREEMENT", false) || !sharedPreferences.getBoolean("KEY_ACCEPT_PRIVACY", false)) {
            setContentView(R.layout.consent_host);
            if (savedInstanceState == null) getSupportFragmentManager().beginTransaction()
                    .replace(R.id.consent_container, new ConsentFragment()).commit();
            return;
        }
        initializeMain(savedInstanceState);
    }

    public void completeConsent() {
        getSupportFragmentManager().beginTransaction().remove(getSupportFragmentManager().findFragmentById(R.id.consent_container)).commitNow();
        initializeMain(null);
    }

    private void initializeMain(Bundle savedInstanceState) {
        MockProviderLifecycle.restoreAfterProcessDeath(getApplicationContext());
        SDKInitializer.setAgreePrivacy(getApplicationContext(), true);
        SDKInitializer.setApiKey(BuildConfig.MAPS_API_KEY);
        SDKInitializer.initialize(getApplicationContext());
        SDKInitializer.setCoordType(com.baidu.mapapi.CoordType.BD09LL);
        setContentView(R.layout.activity_main);
        mMapIndicator = BitmapDescriptorFactory.fromResource(R.drawable.icon_gcoding);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);

        XLog.i("MainActivity: onCreate");

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mOkHttpClient = new OkHttpClient();

        initNavigationView();

        initMap();

        initMapLocation();

        initMapButton();

        initGoBtn();

        initRouteEditor();
        mNavigation = new AppNavigationController(this);
        mInitialized = true;
        findViewById(R.id.route_button).setOnClickListener(v -> toggleRouteEditor());

        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mServiceBinder = (ServiceGo.ServiceGoBinder)service;
                if (handleSimulationFailure()) return;
                if (mPendingRoute != null) {
                    mServiceBinder.startRoute(mPendingRoute, mPendingRouteSpeed, getConfiguredAltitude(), mPendingRouteRepeat);
                    mPendingRoute = null;
                } else {
                    mRouteEditor.setPlaybackState(mServiceBinder.getRouteState());
                    mRouteEditor.updatePlayback(mServiceBinder.getRouteSnapshot());
                }
                if (mPendingPosition != null) applyPendingPosition();
                mRouteUiHandler.removeCallbacks(mRouteStatusPoll);
                mRouteUiHandler.post(mRouteStatusPoll);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mServiceBinder = null;
                mPendingRoute = null;
                mPendingPosition = null;
                mRouteUiHandler.removeCallbacks(mRouteStatusPoll);
                mRouteEditor.setPlaybackState(ServiceGo.ROUTE_NONE);
                isMockServStart = false;
                if (mIsServiceBound) {
                    unbindService(mConnection);
                    mIsServiceBound = false;
                }
                mButtonStart.setImageResource(R.drawable.ic_position);
            }
        };

        if (savedInstanceState != null) {
            double[] selected = savedInstanceState.getDoubleArray("selected_position");
            if (selected != null) {
                mMarkLatLngMap = new LatLng(selected[0], selected[1]);
                mMarkName = savedInstanceState.getString("selected_position_name");
                updateSelectedMarker();
            }
            mPendingPosition = savedInstanceState.getDoubleArray("pending_position");
            double[] center = savedInstanceState.getDoubleArray("map_center");
            if (center != null) {
                isFirstLoc = false;
                mBaiduMap.setMapStatus(MapStatusUpdateFactory.newMapStatus(new MapStatus.Builder()
                        .target(new LatLng(center[0], center[1]))
                        .zoom(savedInstanceState.getFloat("map_zoom")).build()));
            }
            if (savedInstanceState.getBoolean("route_editor_open")) mRouteEditor.toggle();
            double[] pending = savedInstanceState.getDoubleArray("pending_route");
            if (pending != null) {
                mPendingRoute = new ArrayList<>();
                for (int index = 0; index < pending.length; index += 2) {
                    mPendingRoute.add(new RoutePoint(pending[index], pending[index + 1]));
                }
                mPendingRouteSpeed = savedInstanceState.getDouble("pending_route_speed");
                mPendingRouteRepeat = savedInstanceState.getBoolean("pending_route_repeat");
            }
            if (savedInstanceState.getBoolean("mock_running")) {
                // Reconnect to the existing service; never resend its starting coordinate on rotation.
                mIsServiceBound = bindService(new Intent(this, ServiceGo.class), mConnection, 0);
                isMockServStart = mIsServiceBound;
                if (mIsServiceBound) {
                    mRouteEditor.setConnecting();
                    mButtonStart.setImageResource(R.drawable.ic_fly);
                } else {
                    mPendingRoute = null;
                    mPendingPosition = null;
                    GoUtils.DisplayToast(this, getString(R.string.route_service_ended));
                }
            }
        }

        initStoreHistory();

        initSearchView();

        initUpdateVersion();

        checkUpdateVersion(false);
        invalidateOptionsMenu();
        mNavigation.onResume();
        if (!mRealLocationSource.hasPermission()) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION }, 127);
        } else if (mActivityResumed) {
            mRealLocationSource.start();
        }
    }

    @Override
    protected void onPause() {
        XLog.i("MainActivity: onPause");
        mActivityResumed = false;
        if (mInitialized) mRealLocationSource.stop();
        if (mInitialized) { mMapView.onPause(); mSensorManager.unregisterListener(this); }
        super.onPause();
    }

    @Override
    protected void onResume() {
        XLog.i("MainActivity: onResume");
        mActivityResumed = true;
        if (mInitialized) {
            mRealLocationSource.start();
            mMapView.onResume();
            mSensorManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_UI);
            mSensorManager.registerListener(this, mSensorMagnetic, SensorManager.SENSOR_DELAY_UI);
        }
        super.onResume();
        if (mNavigation != null) mNavigation.onResume();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 127 && mInitialized && mActivityResumed) mRealLocationSource.start();
    }

    @Override
    protected void onStop() {
        XLog.i("MainActivity: onStop");
        //取消注册传感器监听
        if (mInitialized) mSensorManager.unregisterListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        XLog.i("MainActivity: onDestroy");
        if (!mInitialized) { super.onDestroy(); return; }
        mNavigation.destroy();
        mOkHttpClient.dispatcher().cancelAll();
        mGeoCoder.destroy();
        mLocationGeoCoder.destroy();

        mRouteUiHandler.removeCallbacks(mRouteStatusPoll);
        mRouteEditor.destroy();
        mSelectedMarker = null;

        if (mIsServiceBound) {
            unbindService(mConnection); // 解绑服务，服务要记得解绑，不要造成内存泄漏
            mIsServiceBound = false;
        }
        if (isMockServStart && !isChangingConfigurations()) {
            Intent serviceGoIntent = new Intent(MainActivity.this, ServiceGo.class);
            stopService(serviceGoIntent);
        }
        unregisterReceiver(mDownloadBdRcv);

        mSensorManager.unregisterListener(this);

        // 退出时销毁定位
        mRealLocationSource.stop();
        // 关闭定位图层
        mBaiduMap.setMyLocationEnabled(false);
        mMapView.onDestroy();
        mMapIndicator.recycle();
        mMapView = null;

        //poi search destroy
        mSuggestionSearch.destroy();

        //close db
        mLocationHistoryDB.close();
        mSearchHistoryDB.close();

        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle state) {
        if (!mInitialized) { super.onSaveInstanceState(state); return; }
        MapStatus mapStatus = mBaiduMap.getMapStatus();
        state.putDoubleArray("map_center", new double[] {mapStatus.target.latitude, mapStatus.target.longitude});
        state.putFloat("map_zoom", mapStatus.zoom);
        state.putBoolean("route_editor_open", mRouteEditor.isOpen());
        state.putBoolean("mock_running", isMockServStart);
        if (mMarkLatLngMap != null) {
            state.putDoubleArray("selected_position", new double[] {mMarkLatLngMap.latitude, mMarkLatLngMap.longitude});
            state.putString("selected_position_name", mMarkName);
        }
        if (mPendingPosition != null) state.putDoubleArray("pending_position", mPendingPosition);
        if (mPendingRoute != null) {
            double[] pending = new double[mPendingRoute.size() * 2];
            for (int index = 0; index < mPendingRoute.size(); index++) {
                pending[index * 2] = mPendingRoute.get(index).getLatitude();
                pending[index * 2 + 1] = mPendingRoute.get(index).getLongitude();
            }
            state.putDoubleArray("pending_route", pending);
            state.putDouble("pending_route_speed", mPendingRouteSpeed);
            state.putBoolean("pending_route_repeat", mPendingRouteRepeat);
        }
        super.onSaveInstanceState(state);
    }

    public boolean isRouteEditorOpen() { return mRouteEditor != null && mRouteEditor.isOpen(); }
    public void toggleRouteEditor() { if (mRouteEditor != null) mRouteEditor.toggle(); }
    public void setMapMenuVisible(boolean visible) { mMapMenuVisible = visible; invalidateOptionsMenu(); }
    public void selectHistoryLocation(String name, String lng, String lat) {
        mNavigation.navigate(R.id.map_destination);
        showLocation(name, lng, lat);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        if (!mInitialized || !mMapMenuVisible) return false;
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        //找到searchView
        searchItem = menu.findItem(R.id.action_search);
        searchItem.setOnActionExpandListener(new  MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                mSearchLayout.setVisibility(View.INVISIBLE);
                mHistoryLayout.setVisibility(View.INVISIBLE);
                return true;  // Return true to collapse action view
            }
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                mSearchLayout.setVisibility(View.INVISIBLE);
                //展示搜索历史
                List<Map<String, Object>> data = getSearchHistory();

                if (!data.isEmpty()) {
                    SimpleAdapter simAdapt = new SimpleAdapter(
                            MainActivity.this,
                            data,
                            R.layout.search_item,
                            new String[] {DataBaseHistorySearch.DB_COLUMN_KEY,
                                    DataBaseHistorySearch.DB_COLUMN_DESCRIPTION,
                                    DataBaseHistorySearch.DB_COLUMN_TIMESTAMP,
                                    DataBaseHistorySearch.DB_COLUMN_IS_LOCATION,
                                    DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM,
                                    DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM},
                            new int[] {R.id.search_key,
                                    R.id.search_description,
                                    R.id.search_timestamp,
                                    R.id.search_isLoc,
                                    R.id.search_longitude,
                                    R.id.search_latitude});
                    mSearchHistoryList.setAdapter(simAdapt);
                    mHistoryLayout.setVisibility(View.VISIBLE);
                }

                return true;  // Return true to expand action view
            }
        });

        searchView = (SearchView) searchItem.getActionView();
        searchView.setIconified(false);// 设置searchView处于展开状态
        searchView.onActionViewExpanded();// 当展开无输入内容的时候，没有关闭的图标
        searchView.setIconifiedByDefault(true);//默认为true在框内，设置false则在框外
        searchView.setSubmitButtonEnabled(false);//显示提交按钮
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (TextUtils.isEmpty(mCurrentCity)) {
                    int message = mCityLookupFailed ? R.string.map_city_lookup_failed : R.string.map_city_unavailable;
                    requestCurrentCity();
                    GoUtils.DisplayToast(MainActivity.this, getString(message));
                    return true;
                }
                try {
                    mSuggestionSearch.requestSuggestion((new SuggestionSearchOption())
                            .keyword(query)
                            .city(mCurrentCity)
                    );
                    //搜索历史 插表参数
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(DataBaseHistorySearch.DB_COLUMN_KEY, query);
                    contentValues.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, "搜索关键字");
                    contentValues.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_KEY);
                    contentValues.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);

                    DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, contentValues);
                    clearSelectedMarker();
                    mSearchLayout.setVisibility(View.INVISIBLE);
                } catch (Exception e) {
                    GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_error_search));
                    XLog.d(getResources().getString(R.string.app_error_search));
                }

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                //当输入框内容改变的时候回调
                //搜索历史置为不可见
                mHistoryLayout.setVisibility(View.INVISIBLE);

                if (newText != null && !newText.isEmpty() && !TextUtils.isEmpty(mCurrentCity)) {
                    try {
                        mSuggestionSearch.requestSuggestion((new SuggestionSearchOption())
                                .keyword(newText)
                                .city(mCurrentCity)
                        );
                    } catch (Exception e) {
                        GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_error_search));
                        XLog.d(getResources().getString(R.string.app_error_search));
                    }
                }

                return true;
            }
        });

        // 搜索框的清除按钮(该按钮属于安卓系统图标)
        ImageView closeButton = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
        closeButton.setOnClickListener(v -> {
            EditText et = findViewById(androidx.appcompat.R.id.search_src_text);
            et.setText("");
            searchView.setQuery("", false);
            mSearchLayout.setVisibility(View.INVISIBLE);
            mHistoryLayout.setVisibility(View.VISIBLE);
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if(sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            mAccValues = sensorEvent.values;
        }
        else if(sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD){
            mMagValues = sensorEvent.values;
        }

        SensorManager.getRotationMatrix(mR, null, mAccValues, mMagValues);
        SensorManager.getOrientation(mR, mDirectionValues);
        mCurrentDirection = (float) Math.toDegrees(mDirectionValues[0]);    // 弧度转角度
        if (mCurrentDirection < 0) {    // 由 -180 ~ + 180 转为 0 ~ 360
            mCurrentDirection += 360;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    /*============================== NavigationView 相关 ==============================*/
    private void initNavigationView() {
        /*============================== NavigationView 相关 ==============================*/
        NavigationView mNavigationView = findViewById(R.id.nav_view);
        mNavigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_history) {
                mNavigation.navigate(R.id.history_destination);
            } else if (id == R.id.nav_settings) {
                mNavigation.navigate(R.id.settings_destination);
            } else if (id == R.id.nav_ui_settings) {
                mNavigation.navigate(R.id.ui_settings_destination);
            } else if (id == R.id.nav_map) {
                mNavigation.navigate(R.id.map_destination);
            } else if (id == R.id.nav_dev) {
                com.suku616miao.meow.miaowuwu.utils.SystemSettingsNavigator.openDeveloperOptions(this);
            } else if (id == R.id.nav_update) {
                checkUpdateVersion(true);
            } else if (id == R.id.nav_feedback) {
                File file = new File(getExternalFilesDir("Logs"), GoApplication.LOG_FILE_NAME);
                ShareUtils.shareFile(this, file, item.getTitle().toString());
            } else if (id == R.id.nav_contact) {
                Uri uri = Uri.parse("https://gitee.com/itexp/gogogo/issues");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
            }

            DrawerLayout drawer = findViewById(R.id.drawer_layout);
            mNavigation.closeDrawer();

            return true;
        });

        // 直接获取第 0 个头部视图
        View headerView = mNavigationView.getHeaderView(0);
        TextView app_version = headerView.findViewById(R.id.app_version);
        app_version.setText(GoUtils.getVersionName(this));
    }

    /*============================== 主界面地图 相关 ==============================*/
    private void initMap() {
        // 地图初始化
        mMapView = findViewById(R.id.bdMapView);
        mMapView.showZoomControls(false);
        mBaiduMap = mMapView.getMap();
        mBaiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);
        mBaiduMap.setMyLocationEnabled(true);
        mBaiduMap.setMapStatus(MapStatusUpdateFactory.newMapStatus(new MapStatus.Builder()
                .target(DEFAULT_MAP_CENTER).zoom(16f).build()));
        mSelectedMarker = null;
        mBaiduMap.setOnMapStatusChangeListener(new BaiduMap.OnMapStatusChangeListener() {
            @Override public void onMapStatusChangeStart(MapStatus status) {
                if (mRouteEditor != null) mRouteEditor.onMapMoveStarted();
            }
            @Override public void onMapStatusChangeStart(MapStatus status, int reason) {
                if (mRouteEditor != null) mRouteEditor.onMapMoveStarted();
            }
            @Override public void onMapStatusChange(MapStatus status) {
                if (mRouteEditor != null) mRouteEditor.onMapMoving(status);
            }
            @Override public void onMapStatusChangeFinish(MapStatus status) {
                if (mRouteEditor != null) mRouteEditor.onMapMoveFinished(status);
            }
        });
        mBaiduMap.setOnMapTouchListener(event -> {

        });
        mBaiduMap.setOnMapClickListener(new BaiduMap.OnMapClickListener() {
            /**
             * 单击地图
             */
            @Override
            public void onMapClick(LatLng point) {
                if (mRouteEditor.isOpen()) {
                    mRouteEditor.selectCandidate(point);
                    return;
                }
                mMarkLatLngMap = point;
                markMap();
            }
            /**
             * 单击地图中的POI点
             */
            @Override
            public void onMapPoiClick(MapPoi poi) {
                if (mRouteEditor.isOpen()) {
                    mRouteEditor.selectCandidate(poi.getPosition());
                    return;
                }
                mMarkLatLngMap = poi.getPosition();
                markMap();
            }
        });
        mBaiduMap.setOnMapLongClickListener(new BaiduMap.OnMapLongClickListener() {
            /**
             * 长按地图
             */
            @Override
            public void onMapLongClick(LatLng point) {
                if (mRouteEditor.isOpen()) {
                    mRouteEditor.selectCandidate(point);
                    return;
                }
                mMarkLatLngMap = point;
                markMap();
                mGeoCoder.reverseGeoCode(new ReverseGeoCodeOption().location(point));
            }
        });
        mBaiduMap.setOnMapDoubleClickListener(new BaiduMap.OnMapDoubleClickListener() {
            /**
             * 双击地图
             */
            @Override
            public void onMapDoubleClick(LatLng point) {
                mBaiduMap.animateMapStatus(MapStatusUpdateFactory.zoomIn());
            }
        });

        View poiView = View.inflate(MainActivity.this, R.layout.location_poi_info, null);
        TextView poiAddress = poiView.findViewById(R.id.poi_address);
        TextView poiLongitude = poiView.findViewById(R.id.poi_longitude);
        TextView poiLatitude = poiView.findViewById(R.id.poi_latitude);
        ImageButton ibSave = poiView.findViewById(R.id.poi_save);
        ibSave.setOnClickListener(v -> {
            recordCurrentLocation(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
            GoUtils.DisplayToast(this, getResources().getString(R.string.app_location_save));
        });
        ImageButton ibCopy = poiView.findViewById(R.id.poi_copy);
        ibCopy.setOnClickListener(v -> {
            //获取剪贴板管理器：
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            // 创建普通字符型ClipData
            ClipData mClipData = ClipData.newPlainText("Label", mMarkLatLngMap.toString());
            // 将 ClipData内容放到系统剪贴板里。
            cm.setPrimaryClip(mClipData);

            GoUtils.DisplayToast(this,  getResources().getString(R.string.app_location_copy));
        });
        ImageButton ibShare = poiView.findViewById(R.id.poi_share);
        ibShare.setOnClickListener(v -> ShareUtils.shareText(MainActivity.this, "分享位置", poiLongitude.getText()+","+poiLatitude.getText()));
        ImageButton ibFly = poiView.findViewById(R.id.poi_fly);
        ibFly.setOnClickListener(this::doGoLocation);
        mGeoCoder = GeoCoder.newInstance();
        mGeoCoder.setOnGetGeoCodeResultListener(new OnGetGeoCoderResultListener() {
            @Override
            public void onGetGeoCodeResult(GeoCodeResult geoCodeResult) {
                XLog.i(geoCodeResult.getLocation());
            }

            @Override
            public void onGetReverseGeoCodeResult(ReverseGeoCodeResult reverseGeoCodeResult) {
                if (reverseGeoCodeResult == null || reverseGeoCodeResult.error != SearchResult.ERRORNO.NO_ERROR) {
                    XLog.i("逆地理位置失败!");
                } else {
                    mMarkName = String.valueOf(reverseGeoCodeResult.getAddress());
                    poiLatitude.setText(String.valueOf(reverseGeoCodeResult.getLocation().latitude));
                    poiLongitude.setText(String.valueOf(reverseGeoCodeResult.getLocation().longitude));
                    poiAddress.setText(reverseGeoCodeResult.getAddress());
                    final InfoWindow mInfoWindow = new InfoWindow(poiView, reverseGeoCodeResult.getLocation(), -100);
                    mBaiduMap.showInfoWindow(mInfoWindow);
                }
            }
        });

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);// 获取传感器管理服务
        if (mSensorManager != null) {
            mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (mSensorAccelerometer != null) {
                mSensorManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_UI);
            }
            mSensorMagnetic = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            if (mSensorMagnetic != null) {
                mSensorManager.registerListener(this, mSensorMagnetic, SensorManager.SENSOR_DELAY_UI);
            }
        }
    }

    private void initMapLocation() {
        mBaiduMap.setMyLocationConfiguration(new MyLocationConfiguration(
                MyLocationConfiguration.LocationMode.NORMAL, true, null));
        mRealLocationSource = new SystemLocationSource(getApplicationContext(), this::onRealLocation);
        // Separate geocoders keep city lookup from opening or overwriting the selected POI bubble.
        mLocationGeoCoder = GeoCoder.newInstance();
        mLocationGeoCoder.setOnGetGeoCodeResultListener(new OnGetGeoCoderResultListener() {
            @Override public void onGetGeoCodeResult(GeoCodeResult result) { }

            @Override public void onGetReverseGeoCodeResult(ReverseGeoCodeResult result) {
                mCityLookupPending = false;
                if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR || result.getAddressDetail() == null) {
                    mCityLookupFailed = true;
                    XLog.i("Current city lookup unavailable: " + (result == null ? "empty result" : result.error));
                    return;
                }
                mCurrentCity = result.getAddressDetail().city;
                mCityLookupFailed = false;
                XLog.i("Current city lookup completed");
                if (searchView != null && !TextUtils.isEmpty(searchView.getQuery()) && !TextUtils.isEmpty(mCurrentCity)) {
                    mSuggestionSearch.requestSuggestion(new SuggestionSearchOption()
                            .keyword(searchView.getQuery().toString()).city(mCurrentCity));
                }
            }
        });
    }

    private void onRealLocation(Location location) {
        mLastRealLocation = new Location(location);
        double[] bd09 = MapUtils.wgs2bd09(location.getLongitude(), location.getLatitude());
        mCurrentLon = bd09[0];
        mCurrentLat = bd09[1];
        mHasLocation = true;
        mBaiduMap.setMyLocationData(new MyLocationData.Builder()
                .accuracy(location.getAccuracy()).direction(mCurrentDirection)
                .latitude(mCurrentLat).longitude(mCurrentLon).build());
        XLog.i("Real position applied to map: provider=" + location.getProvider() + ", accuracy=" + location.getAccuracy());
        if (isFirstLoc) {
            isFirstLoc = false;
            mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(new MapStatus.Builder()
                    .target(new LatLng(mCurrentLat, mCurrentLon)).zoom(18f).build()));
        }
        // City names do not need a network request on every one-second position update.
        if (!mCityLookupPending && (mLastCityLookupAttempt == null
                || location.distanceTo(mLastCityLookupAttempt) >= CITY_REFRESH_DISTANCE_METERS)) {
            requestCurrentCity();
        }
    }

    private void requestCurrentCity() {
        if (!mHasLocation || mCityLookupPending) return;
        mLastCityLookupAttempt = new Location(mLastRealLocation);
        mCityLookupPending = mLocationGeoCoder.reverseGeoCode(new ReverseGeoCodeOption()
                .location(new LatLng(mCurrentLat, mCurrentLon)));
    }

    private void initMapButton() {
        RadioGroup mGroupMapType = this.findViewById(R.id.RadioGroupMapType);
        mGroupMapType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.mapNormal) {
                mBaiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);
            }

            if (checkedId == R.id.mapSatellite) {
                mBaiduMap.setMapType(BaiduMap.MAP_TYPE_SATELLITE);
            }
        });

        ImageButton curPosBtn = this.findViewById(R.id.cur_position);
        curPosBtn.setOnClickListener(v -> resetMap());

        ImageButton zoomInBtn = this.findViewById(R.id.zoom_in);
        zoomInBtn.setOnClickListener(v -> mBaiduMap.animateMapStatus(MapStatusUpdateFactory.zoomIn()));

        ImageButton zoomOutBtn = this.findViewById(R.id.zoom_out);
        zoomOutBtn.setOnClickListener(v -> mBaiduMap.animateMapStatus(MapStatusUpdateFactory.zoomOut()));

        ImageButton inputPosBtn = this.findViewById(R.id.input_pos);
        inputPosBtn.setOnClickListener(v -> {
            AlertDialog dialog;
            AlertDialog.Builder builder = new MaterialAlertDialogBuilder(MainActivity.this, R.style.ThemeOverlay_App_RoundedInputDialog);
            builder.setTitle("请输入经度和纬度");
            View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.location_input, null);
            builder.setView(view);
            dialog = builder.show();

            EditText dialog_lng = view.findViewById(R.id.joystick_longitude);
            EditText dialog_lat = view.findViewById(R.id.joystick_latitude);
            RadioButton rbBD = view.findViewById(R.id.pos_type_bd);

            Button btnGo = view.findViewById(R.id.input_position_ok);
            btnGo.setOnClickListener(v2 -> {
                String dialog_lng_str = dialog_lng.getText().toString();
                String dialog_lat_str = dialog_lat.getText().toString();

                if (TextUtils.isEmpty(dialog_lng_str) || TextUtils.isEmpty(dialog_lat_str)) {
                    GoUtils.DisplayToast(MainActivity.this,getResources().getString(R.string.app_error_input));
                } else {
                    double dialog_lng_double = Double.parseDouble(dialog_lng_str);
                    double dialog_lat_double = Double.parseDouble(dialog_lat_str);

                    if (dialog_lng_double > 180.0 || dialog_lng_double < -180.0) {
                        GoUtils.DisplayToast(MainActivity.this,  getResources().getString(R.string.app_error_longitude));
                    } else {
                        if (dialog_lat_double > 90.0 || dialog_lat_double < -90.0) {
                            GoUtils.DisplayToast(MainActivity.this,  getResources().getString(R.string.app_error_latitude));
                        } else {
                            if (rbBD.isChecked()) {
                                mMarkLatLngMap = new LatLng(dialog_lat_double, dialog_lng_double);
                            } else {
                                double[] bdLonLat = MapUtils.wgs2bd09(dialog_lng_double, dialog_lat_double);
                                mMarkLatLngMap = new LatLng(bdLonLat[1], bdLonLat[0]);
                            }
                            mMarkName = "手动输入的坐标";

                            markMap();

                            MapStatusUpdate mapstatusupdate = MapStatusUpdateFactory.newLatLng(mMarkLatLngMap);
                            mBaiduMap.setMapStatus(mapstatusupdate);

                            dialog.dismiss();
                        }
                    }
                }
            });

            Button btnCancel = view.findViewById(R.id.input_position_cancel);
            btnCancel.setOnClickListener(v1 -> dialog.dismiss());
        });
    }

    //标定选择的位置
    private void markMap() {
        if (mMarkLatLngMap == null) return;
        isFirstLoc = false;
        if (mRouteEditor.isOpen()) {
            mRouteEditor.selectCandidate(mMarkLatLngMap);
            return;
        }
        if (mMarkLatLngMap != null) {
            updateSelectedMarker();
        }
    }

    private void updateSelectedMarker() {
        if (mSelectedMarker == null) {
            mSelectedMarker = (Marker) mBaiduMap.addOverlay(
                    new MarkerOptions().position(mMarkLatLngMap).icon(mMapIndicator));
        } else {
            mSelectedMarker.setPosition(mMarkLatLngMap);
        }
    }

    private void clearSelectedMarker() {
        if (mSelectedMarker != null) {
            mSelectedMarker.remove();
            mSelectedMarker = null;
        }
        mBaiduMap.hideInfoWindow();
    }

    private void resetMap() {
        if (!mHasLocation) {
            GoUtils.DisplayToast(this, getString(R.string.map_location_unavailable));
            return;
        }
        clearSelectedMarker();
        mMarkLatLngMap = null;

        MyLocationData locData = new MyLocationData.Builder()
                .latitude(mCurrentLat)
                .longitude(mCurrentLon)
                .direction(mCurrentDirection)
                .build();
        mBaiduMap.setMyLocationData(locData);

        MapStatus.Builder builder = new MapStatus.Builder();
        builder.target(new LatLng(mCurrentLat, mCurrentLon)).zoom(18.0f);
        mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
    }

    // 在地图上显示位置
    private boolean showLocation(String name, String bd09Longitude, String bd09Latitude) {
        boolean ret = true;

        try {
            if (!bd09Longitude.isEmpty() && !bd09Latitude.isEmpty()) {
                isFirstLoc = false;
                mMarkName = name;
                mMarkLatLngMap = new LatLng(Double.parseDouble(bd09Latitude), Double.parseDouble(bd09Longitude));
                updateSelectedMarker();
                MapStatusUpdate mapstatusupdate = MapStatusUpdateFactory.newLatLng(mMarkLatLngMap);
                mBaiduMap.setMapStatus(mapstatusupdate);
            }
        } catch (Exception e) {
            ret = false;
            XLog.e("ERROR: showHistoryLocation");
        }

        return ret;
    }

    private void initGoBtn() {
        mButtonStart = findViewById(R.id.faBtnStart);
        mButtonStart.setOnClickListener(this::doGoLocation);
    }

    private void initRouteEditor() {
        mRouteEditor = new RouteEditor(this, mBaiduMap, findViewById(R.id.route_panel),
                findViewById(R.id.route_crosshair), mMapIndicator, new RouteEditor.Listener() {
            @Override public void onEditorVisibilityChanged(boolean visible) {
                if (mNavigation != null) mNavigation.animateRouteButtons(visible);
                if (visible) {
                    // Location fixes must not move the camera while the user chooses a route vertex.
                    isFirstLoc = false;
                    clearSelectedMarker();
                }
            }
            @Override public boolean onStartRoute(List<RoutePoint> points, double speed, boolean repeat) {
                if (!GoUtils.isGpsOpened(MainActivity.this)) {
                    GoUtils.showEnableGpsDialog(MainActivity.this);
                    return false;
                }
                if (!Settings.canDrawOverlays(getApplicationContext())) {
                    GoUtils.showEnableFloatWindowDialog(MainActivity.this);
                    return false;
                }
                if (!GoUtils.isAllowMockLocation(MainActivity.this)) {
                    GoUtils.showEnableMockLocationDialog(MainActivity.this);
                    return false;
                }
                // Route playback has its own position; the normal position button stops it.
                mMarkLatLngMap = null;
                if (isMockServStart) {
                    if (mServiceBinder == null) {
                        mPendingRoute = new ArrayList<>(points);
                        mPendingRouteSpeed = speed;
                        mPendingRouteRepeat = repeat;
                    } else {
                        mServiceBinder.startRoute(points, speed, getConfiguredAltitude(), repeat);
                    }
                } else {
                    mPendingRoute = new ArrayList<>(points);
                    mPendingRouteSpeed = speed;
                    mPendingRouteRepeat = repeat;
                    RoutePoint first = points.get(0);
                    if (!startMockService(first.getLongitude(), first.getLatitude(), getConfiguredAltitude())) {
                        mPendingRoute = null;
                        return false;
                    }
                }
                return true;
            }
            @Override public void onPauseRoute() { mServiceBinder.pauseRoute(); }
            @Override public void onResumeRoute() { mServiceBinder.resumeRoute(); }
            @Override public void onStopRoute() {
                mPendingRoute = null;
                stopGoLocation();
                mRouteEditor.setPlaybackState(ServiceGo.ROUTE_NONE);
            }
            @Override public void onReplaceRoute() {
                if (isMockServStart) stopGoLocation();
            }
        });
    }

    private double getConfiguredAltitude() {
        return Double.parseDouble(sharedPreferences.getString("setting_altitude", "55.0"));
    }

    private boolean startMockService(double longitude, double latitude, double altitude) {
        Intent intent = new Intent(MainActivity.this, ServiceGo.class);
        intent.putExtra(LNG_MSG_ID, longitude);
        intent.putExtra(LAT_MSG_ID, latitude);
        intent.putExtra(ALT_MSG_ID, altitude);
        startForegroundService(intent);
        mIsServiceBound = bindService(intent, mConnection, BIND_AUTO_CREATE);
        isMockServStart = mIsServiceBound;
        if (!mIsServiceBound) {
            stopService(intent);
            GoUtils.DisplayToast(this, getString(R.string.route_service_ended));
            return false;
        }
        mButtonStart.setImageResource(R.drawable.ic_fly);
        return true;
    }

    private void startGoLocation() {
        double[] latLng = MapUtils.bd2wgs(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
        startMockService(latLng[0], latLng[1], getConfiguredAltitude());
        XLog.d("startForegroundService: ServiceGo");

    }

    private void stopGoLocation() {
        mRouteUiHandler.removeCallbacks(mRouteStatusPoll);
        mPendingRoute = null;
        mPendingPosition = null;
        mRouteEditor.setPlaybackState(ServiceGo.ROUTE_NONE);
        mServiceBinder = null;
        if (mIsServiceBound) {
            unbindService(mConnection);
            mIsServiceBound = false;
        }
        Intent serviceGoIntent = new Intent(MainActivity.this, ServiceGo.class);
        stopService(serviceGoIntent);
        isMockServStart = false;
        mButtonStart.setImageResource(R.drawable.ic_position);
    }

    private void doGoLocation(View v) {
        if (mRouteEditor.isOpen()) {
            mRouteEditor.toggle();
            GoUtils.DisplayToast(this, getString(R.string.route_changes_may_not_be_saved));
        }
        // Stopping never requires network, location or overlay access to remain enabled.
        if (isMockServStart && mMarkLatLngMap == null) {
            stopGoLocation();
            Snackbar.make(v, "模拟位置已终止", Snackbar.LENGTH_LONG).show();
            return;
        }
        if (!GoUtils.isNetworkAvailable(this)) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_network));
            return;
        }

        if (!GoUtils.isGpsOpened(this)) {
            GoUtils.showEnableGpsDialog(this);
            return;
        }

        if (!Settings.canDrawOverlays(getApplicationContext())) {//悬浮窗权限判断
            GoUtils.showEnableFloatWindowDialog(this);
            XLog.e("无悬浮窗权限!");
            return;
        }

        if (isMockServStart) {
            double[] latLng = MapUtils.bd2wgs(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
            recordCurrentLocation(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
            // BindService accepts a binding before the Binder arrives; queue the requested action.
            mPendingRoute = null;
            mPendingPosition = new double[] {latLng[0], latLng[1], getConfiguredAltitude()};
            clearSelectedMarker();
            mMarkLatLngMap = null;
            if (mServiceBinder == null) {
                Snackbar.make(v, getString(R.string.position_waiting_for_service), Snackbar.LENGTH_LONG).show();
            } else {
                applyPendingPosition();
            }

            if (GoUtils.isWifiEnabled(MainActivity.this)) {
                GoUtils.showDisableWifiDialog(MainActivity.this);
            }
        } else {
            if (!GoUtils.isAllowMockLocation(this)) {
                GoUtils.showEnableMockLocationDialog(this);
                XLog.e("无模拟位置权限!");
            } else {
                if (mMarkLatLngMap == null) {
                    Snackbar.make(v, "请先点击地图位置或者搜索位置", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                } else {
                    startGoLocation();
                    mButtonStart.setImageResource(R.drawable.ic_fly);
                    Snackbar.make(v, "模拟位置已启动", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();

                    recordCurrentLocation(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
                    clearSelectedMarker();
                    mMarkLatLngMap = null;

                    if (GoUtils.isWifiEnabled(MainActivity.this)) {
                        GoUtils.showDisableWifiDialog(MainActivity.this);
                    }
                }
            }
        }
    }

    private void applyPendingPosition() {
        if (handleSimulationFailure()) return;
        double[] position = mPendingPosition;
        mPendingPosition = null;
        mServiceBinder.setPosition(position[0], position[1], position[2]);
        mRouteEditor.setPlaybackState(ServiceGo.ROUTE_NONE);
        Snackbar.make(mButtonStart, "已传送到新位置", Snackbar.LENGTH_LONG).show();
    }

    private boolean handleSimulationFailure() {
        if (mServiceBinder.getRouteState() != ServiceGo.ROUTE_FAILED) return false;
        String reason = mServiceBinder.getFailureReason();
        stopGoLocation();
        GoUtils.DisplayToast(this, reason);
        return true;
    }

    /*============================== 历史记录 相关 ==============================*/
    private void initStoreHistory() {
        try {
            // 定位历史
            DataBaseHistoryLocation dbLocation = new DataBaseHistoryLocation(getApplicationContext());
            mLocationHistoryDB = dbLocation.getWritableDatabase();
            // 搜索历史
            DataBaseHistorySearch dbHistory = new DataBaseHistorySearch(getApplicationContext());
            mSearchHistoryDB = dbHistory.getWritableDatabase();
        } catch (Exception e) {
            XLog.e("ERROR: sqlite init error");
        }
    }

    //获取查询历史
    private List<Map<String, Object>> getSearchHistory() {
        List<Map<String, Object>> data = new ArrayList<>();

        try {
            Cursor cursor = mSearchHistoryDB.query(DataBaseHistorySearch.TABLE_NAME, null,
                    DataBaseHistorySearch.DB_COLUMN_ID + " > ?", new String[] {"0"},
                    null, null, DataBaseHistorySearch.DB_COLUMN_TIMESTAMP + " DESC", null);

            while (cursor.moveToNext()) {
                Map<String, Object> searchHistoryItem = new HashMap<>();
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_KEY, cursor.getString(1));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, cursor.getString(2));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, "" + cursor.getInt(3));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, "" + cursor.getInt(4));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, cursor.getString(7));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, cursor.getString(8));
                data.add(searchHistoryItem);
            }
            cursor.close();
        } catch (Exception e) {
            XLog.e("ERROR: getSearchHistory");
        }

        return data;
    }

    // 记录请求的位置信息
    private void recordCurrentLocation(double lng, double lat) {
        //参数坐标系：bd09
        final String safeCode = BuildConfig.MAPS_SAFE_CODE;
        final String ak = sharedPreferences.getString("setting_map_key", BuildConfig.MAPS_API_KEY);
        double[] latLng = MapUtils.bd2wgs(lng, lat);
        //bd09坐标的位置信息
        String mapApiUrl = "https://api.map.baidu.com/reverse_geocoding/v3/?ak=" + ak + "&output=json&coordtype=bd09ll" + "&location=" + lat + "," + lng + "&mcode=" + safeCode;

        okhttp3.Request request = new okhttp3.Request.Builder().url(mapApiUrl).get().build();
        final Call call = mOkHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                //http 请求失败
                XLog.e("HTTP: HTTP GET FAILED");
                //插表参数
                ContentValues contentValues = new ContentValues();
                contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LOCATION, mMarkName);
                contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(latLng[0]));
                contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84, String.valueOf(latLng[1]));
                contentValues.put(DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);
                contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_CUSTOM, Double.toString(lng));
                contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_CUSTOM, Double.toString(lat));

                DataBaseHistoryLocation.saveHistoryLocation(mLocationHistoryDB, contentValues);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                ResponseBody responseBody = response.body();
                if (responseBody != null) {
                    String resp = responseBody.string();
                    try {
                        JSONObject getRetJson = new JSONObject(resp);

                        if (Integer.parseInt(getRetJson.getString("status")) == 0) { // 位置获取成功
                            JSONObject posInfoJson = getRetJson.getJSONObject("result");
                            String formatted_address = posInfoJson.getString("formatted_address");
                            ContentValues contentValues = new ContentValues();
                            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LOCATION, formatted_address);
                            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(latLng[0]));
                            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84, String.valueOf(latLng[1]));
                            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);
                            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_CUSTOM, Double.toString(lng));
                            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_CUSTOM, Double.toString(lat));
                            DataBaseHistoryLocation.saveHistoryLocation(mLocationHistoryDB, contentValues);
                        } else {
                            ContentValues contentValues = new ContentValues();
                            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LOCATION, mMarkName == null ? getRetJson.getString("message"): mMarkName);
                            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(latLng[0]));
                            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84, String.valueOf(latLng[1]));
                            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);
                            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_CUSTOM, Double.toString(lng));
                            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_CUSTOM, Double.toString(lat));
                            DataBaseHistoryLocation.saveHistoryLocation(mLocationHistoryDB, contentValues);
                        }
                    } catch (JSONException e) {
                        XLog.e("JSON: resolve json error");
                        ContentValues contentValues = new ContentValues();
                        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LOCATION, mMarkName == null ? getResources().getString(R.string.history_location_default_name) : mMarkName);
                        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LOCATION, mMarkName);
                        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(latLng[0]));
                        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84, String.valueOf(latLng[1]));
                        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);
                        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_CUSTOM, Double.toString(lng));
                        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_CUSTOM, Double.toString(lat));
                        DataBaseHistoryLocation.saveHistoryLocation(mLocationHistoryDB, contentValues);
                    }
                }
            }
        });
    }

    /*============================== SearchView 相关 ==============================*/
    private void initSearchView() {
        mSearchLayout = findViewById(R.id.search_linear);
        mHistoryLayout = findViewById(R.id.search_history_linear);

        mSearchList = findViewById(R.id.search_list_view);
        mSearchList.setOnItemClickListener((parent, view, position, id) -> {
            String lng = ((TextView) view.findViewById(R.id.poi_longitude)).getText().toString();
            String lat = ((TextView) view.findViewById(R.id.poi_latitude)).getText().toString();
            mMarkName = ((TextView) view.findViewById(R.id.poi_name)).getText().toString();
            mMarkLatLngMap = new LatLng(Double.parseDouble(lat), Double.parseDouble(lng));
            MapStatusUpdate mapstatusupdate = MapStatusUpdateFactory.newLatLng(mMarkLatLngMap);
            mBaiduMap.setMapStatus(mapstatusupdate);

            markMap();

            double[] latLng = MapUtils.bd2wgs(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);

            // mSearchList.setVisibility(View.GONE);
            //搜索历史 插表参数
            ContentValues contentValues = new ContentValues();
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_KEY, mMarkName);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, ((TextView) view.findViewById(R.id.poi_address)).getText().toString());
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_RESULT);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, lng);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, lat);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(latLng[0]));
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_WGS84, String.valueOf(latLng[1]));
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);

            DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, contentValues);
            mSearchLayout.setVisibility(View.INVISIBLE);
            searchItem.collapseActionView();
        });
        //搜索历史列表的点击监听
        mSearchHistoryList = findViewById(R.id.search_history_list_view);
        mSearchHistoryList.setOnItemClickListener((parent, view, position, id) -> {
            String searchDescription = ((TextView) view.findViewById(R.id.search_description)).getText().toString();
            String searchKey = ((TextView) view.findViewById(R.id.search_key)).getText().toString();
            String searchIsLoc = ((TextView) view.findViewById(R.id.search_isLoc)).getText().toString();

            //如果是定位搜索
            if (searchIsLoc.equals("1")) {
                String lng = ((TextView) view.findViewById(R.id.search_longitude)).getText().toString();
                String lat = ((TextView) view.findViewById(R.id.search_latitude)).getText().toString();
                // mMarkName = ((TextView) view.findViewById(R.id.poi_name)).getText().toString();
                mMarkLatLngMap = new LatLng(Double.parseDouble(lat), Double.parseDouble(lng));
                MapStatusUpdate mapstatusupdate = MapStatusUpdateFactory.newLatLng(mMarkLatLngMap);
                mBaiduMap.setMapStatus(mapstatusupdate);

                markMap();

                double[] latLng = MapUtils.bd2wgs(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);

                //设置列表不可见
                mHistoryLayout.setVisibility(View.INVISIBLE);
                searchItem.collapseActionView();
                //更新表
                ContentValues contentValues = new ContentValues();
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_KEY, searchKey);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, searchDescription);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_RESULT);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, lng);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, lat);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(latLng[0]));
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_WGS84, String.valueOf(latLng[1]));
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);

                DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, contentValues);
            } else if (searchIsLoc.equals("0")) { //如果仅仅是搜索
                try {
                    searchView.setQuery(searchKey, true);
                } catch (Exception e) {
                    GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_search));
                    XLog.e(getResources().getString(R.string.app_error_search));
                }
            } else {
                XLog.e(getResources().getString(R.string.app_error_param));
            }
        });
        mSearchHistoryList.setOnItemLongClickListener((parent, view, position, id) -> {
            new MaterialAlertDialogBuilder(MainActivity.this)
                    .setTitle("警告")//这里是表头的内容
                    .setMessage("确定要删除该项搜索记录吗?")//这里是中间显示的具体信息
                    .setPositiveButton("确定",(dialog, which) -> {
                        String searchKey = ((TextView) view.findViewById(R.id.search_key)).getText().toString();

                        try {
                            mSearchHistoryDB.delete(DataBaseHistorySearch.TABLE_NAME, DataBaseHistorySearch.DB_COLUMN_KEY + " = ?", new String[] {searchKey});
                            //删除成功
                            //展示搜索历史
                            List<Map<String, Object>> data = getSearchHistory();

                            if (!data.isEmpty()) {
                                SimpleAdapter simAdapt = new SimpleAdapter(
                                        MainActivity.this,
                                        data,
                                        R.layout.search_item,
                                        new String[] {DataBaseHistorySearch.DB_COLUMN_KEY,
                                                DataBaseHistorySearch.DB_COLUMN_DESCRIPTION,
                                                DataBaseHistorySearch.DB_COLUMN_TIMESTAMP,
                                                DataBaseHistorySearch.DB_COLUMN_IS_LOCATION,
                                                DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM,
                                                DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM}, // 与下面数组元素要一一对应
                                        new int[] {R.id.search_key, R.id.search_description, R.id.search_timestamp, R.id.search_isLoc, R.id.search_longitude, R.id.search_latitude});
                                mSearchHistoryList.setAdapter(simAdapt);
                                mHistoryLayout.setVisibility(View.VISIBLE);
                            }
                        } catch (Exception e) {
                            XLog.e("ERROR: delete database error");
                            GoUtils.DisplayToast(MainActivity.this,getResources().getString(R.string.history_delete_error));
                        }
                    })
                    .setNegativeButton("取消",
                            (dialog, which) -> {
                            })
                    .show();
            return true;
        });
        //设置搜索建议返回值监听
        mSuggestionSearch = SuggestionSearch.newInstance();
        mSuggestionSearch.setOnGetSuggestionResultListener(suggestionResult -> {
            if (suggestionResult == null || suggestionResult.getAllSuggestions() == null) {
                GoUtils.DisplayToast(this,getResources().getString(R.string.app_search_null));
            } else {
                List<Map<String, Object>> data = getMapList(suggestionResult);

                SimpleAdapter simAdapt = new SimpleAdapter(
                        MainActivity.this,
                        data,
                        R.layout.search_poi_item,
                        new String[] {POI_NAME, POI_ADDRESS, POI_LONGITUDE, POI_LATITUDE}, // 与下面数组元素要一一对应
                        new int[] {R.id.poi_name, R.id.poi_address, R.id.poi_longitude, R.id.poi_latitude});
                mSearchList.setAdapter(simAdapt);
                // mSearchList.setVisibility(View.VISIBLE);
                mSearchLayout.setVisibility(View.VISIBLE);
            }
        });
    }

    @NonNull
    private static List<Map<String, Object>> getMapList(SuggestionResult suggestionResult) {
        List<Map<String, Object>> data = new ArrayList<>();
        int retCnt = suggestionResult.getAllSuggestions().size();

        for (int i = 0; i < retCnt; i++) {
            if (suggestionResult.getAllSuggestions().get(i).pt == null) {
                continue;
            }

            Map<String, Object> poiItem = new HashMap<>();
            poiItem.put(POI_NAME, suggestionResult.getAllSuggestions().get(i).key);
            poiItem.put(POI_ADDRESS, suggestionResult.getAllSuggestions().get(i).city + " " + suggestionResult.getAllSuggestions().get(i).district);
            poiItem.put(POI_LONGITUDE, "" + suggestionResult.getAllSuggestions().get(i).pt.longitude);
            poiItem.put(POI_LATITUDE, "" + suggestionResult.getAllSuggestions().get(i).pt.latitude);
            data.add(poiItem);
        }
        return data;
    }

    /*============================== 更新 相关 ==============================*/
    private void initUpdateVersion() {
        mDownloadManager =(DownloadManager) MainActivity.this.getSystemService(DOWNLOAD_SERVICE);

        // 用于监听下载完成后，转到安装界面
        mDownloadBdRcv = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                installNewVersion();
            }
        };
        registerReceiver(mDownloadBdRcv, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private void checkUpdateVersion(boolean result) {
        String mapApiUrl = "https://api.github.com/repos/xusu616/GoGoGo/releases/latest";

        okhttp3.Request request = new okhttp3.Request.Builder().url(mapApiUrl).get().build();
        final Call call = mOkHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                XLog.i("GitHub 更新检测失败，尝试 GitLab fallback");
                checkUpdateVersionGitLab(result);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull okhttp3.Response response) throws IOException {
                try (response) {
                    if (!response.isSuccessful()) {
                        XLog.i("GitHub 更新检测响应失败(" + response.code() + ")，尝试 GitLab fallback");
                        checkUpdateVersionGitLab(result);
                        return;
                    }
                    ResponseBody responseBody = response.body();
                    if (responseBody == null) {
                        XLog.i("GitHub 返回空数据，尝试 GitLab fallback");
                        checkUpdateVersionGitLab(result);
                        return;
                    }
                    String resp = responseBody.string();
                    // 注意，该请求在子线程，不能直接操作界面
                    runOnUiThread(() -> {
                        if (isDestroyed()) return;
                        try {
                            JSONObject getRetJson = new JSONObject(resp);
                            if (ReleaseVersion.isNewer(getRetJson.getString("tag_name"), BuildConfig.VERSION_NAME)) {
                                JSONArray jsonArray = getRetJson.getJSONArray("assets");
                                JSONObject jsonObject = findApkAsset(jsonArray, "browser_download_url");
                                if (jsonObject == null) {
                                    XLog.i("GitHub 发布没有有效的 HTTPS APK 附件，尝试 GitLab fallback");
                                    checkUpdateVersionGitLab(result);
                                    return;
                                }
                                String download_url = jsonObject.getString("browser_download_url");
                                String filename = jsonObject.getString("name");

                                showUpdateDialog(
                                        getRetJson.getString("name"),
                                        getRetJson.getString("created_at"),
                                        getRetJson.getString("target_commitish"),
                                        getRetJson.getString("body"),
                                        download_url,
                                        filename
                                );
                            } else {
                                if (result) {
                                    GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.update_last));
                                }
                            }
                        } catch (JSONException e) {
                            XLog.e("ERROR: resolve json");
                            checkUpdateVersionGitLab(result);
                        } catch (IllegalArgumentException e) {
                            XLog.e("GitHub 发布标签不是有效的稳定版本，尝试 GitLab");
                            checkUpdateVersionGitLab(result);
                        }
                    });
                }
            }
        });
    }

    private void checkUpdateVersionGitLab(boolean result) {
        String gitlabApiUrl = "https://gitlab.com/api/v4/projects/xusu616%2FGoGoGo/releases?per_page=1";

        okhttp3.Request request = new okhttp3.Request.Builder().url(gitlabApiUrl).get().build();
        final Call call = mOkHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                XLog.i("GitLab 更新检测失败");
                if (result) {
                    runOnUiThread(() -> GoUtils.DisplayToast(MainActivity.this, "更新检测失败"));
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull okhttp3.Response response) throws IOException {
                try (response) {
                    if (!response.isSuccessful()) {
                        XLog.i("GitLab 更新检测响应失败: " + response.code());
                        if (result) {
                            runOnUiThread(() -> GoUtils.DisplayToast(MainActivity.this, "更新检测失败"));
                        }
                        return;
                    }
                    ResponseBody responseBody = response.body();
                    if (responseBody == null) {
                        XLog.i("GitLab 返回空数据");
                        if (result) {
                            runOnUiThread(() -> GoUtils.DisplayToast(MainActivity.this, "更新检测失败"));
                        }
                        return;
                    }
                    String resp = responseBody.string();
                    runOnUiThread(() -> {
                        if (isDestroyed()) return;
                        try {
                            JSONArray array = new JSONArray(resp);
                            if (array.length() == 0) {
                                if (result) {
                                    GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.update_last));
                                }
                                return;
                            }
                            JSONObject getRetJson = array.getJSONObject(0);
                            String releaseName = getRetJson.optString("name");
                            String tagName = getRetJson.optString("tag_name");

                            if (ReleaseVersion.isNewer(tagName, BuildConfig.VERSION_NAME)) {
                                JSONObject assets = getRetJson.optJSONObject("assets");
                                JSONArray links = assets != null ? assets.optJSONArray("links") : null;
                                JSONObject targetLink = findApkAsset(links, "url");
                                if (targetLink == null) {
                                    XLog.i("GitLab 发布没有有效的 HTTPS APK 附件");
                                    if (result) {
                                        GoUtils.DisplayToast(MainActivity.this, "更新检测失败：发布中没有有效的 HTTPS APK 附件");
                                    }
                                    return;
                                }
                                String download_url = targetLink.getString("url");
                                String filename = targetLink.getString("name");

                                JSONObject commitObj = getRetJson.optJSONObject("commit");
                                String commitStr = commitObj != null ? commitObj.optString("short_id") : "";

                                showUpdateDialog(
                                        releaseName,
                                        getRetJson.optString("created_at"),
                                        commitStr,
                                        getRetJson.optString("description"),
                                        download_url,
                                        filename
                                );
                            } else {
                                if (result) {
                                    GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.update_last));
                                }
                            }
                        } catch (JSONException e) {
                            XLog.e("ERROR: resolve gitlab json");
                            if (result) {
                                GoUtils.DisplayToast(MainActivity.this, "更新检测失败");
                            }
                        } catch (IllegalArgumentException e) {
                            XLog.e("GitLab 发布标签不是有效的稳定版本");
                            if (result) GoUtils.DisplayToast(MainActivity.this, "更新检测失败：版本标签无效");
                        }
                    });
                }
            }
        });
    }

    private JSONObject findApkAsset(JSONArray assets, String urlField) throws JSONException {
        if (assets == null) {
            return null;
        }
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String filename = asset.optString("name");
            if (!filename.endsWith(".apk") || filename.contains("/") || filename.contains("\\")) {
                continue;
            }
            try {
                URI downloadUri = new URI(asset.optString(urlField));
                if ("https".equalsIgnoreCase(downloadUri.getScheme())
                        && !TextUtils.isEmpty(downloadUri.getHost())) {
                    return asset;
                }
            } catch (URISyntaxException e) {
                XLog.i("跳过下载地址格式错误的 APK 附件");
            }
        }
        return null;
    }

    private void showUpdateDialog(String title, String createdAt, String commit, String body, String downloadUrl, String filename) {
        final android.app.AlertDialog alertDialog = new android.app.AlertDialog.Builder(MainActivity.this).create();
        alertDialog.show();
        alertDialog.setCancelable(false);
        Window window = alertDialog.getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);      // 防止出现闪屏
            window.setContentView(R.layout.update);
            window.setGravity(Gravity.CENTER);
            window.setWindowAnimations(R.style.DialogAnimFadeInFadeOut);

            TextView updateTitle = window.findViewById(R.id.update_title);
            updateTitle.setText(title);
            TextView updateTime = window.findViewById(R.id.update_time);
            updateTime.setText(createdAt);
            TextView updateCommit = window.findViewById(R.id.update_commit);
            updateCommit.setText(commit);

            TextView updateContent = window.findViewById(R.id.update_content);
            final Markwon markwon = Markwon.create(MainActivity.this);
            markwon.setMarkdown(updateContent, body);

            Button updateCancel = window.findViewById(R.id.update_ignore);
            updateCancel.setOnClickListener(v -> alertDialog.cancel());

            mUpdateFilename = filename;

            Button updateAgree = window.findViewById(R.id.update_agree);
            updateAgree.setOnClickListener(v -> {
                alertDialog.cancel();
                GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.update_downloading));
                downloadNewVersion(downloadUrl);
            });
        }
    }

    private void downloadNewVersion(String url) {
        if (mDownloadManager == null) {
            return;
        }

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setAllowedOverRoaming(false);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setTitle(GoUtils.getAppName(this));
        request.setDescription("正在下载新版本...");
        request.setMimeType("application/vnd.android.package-archive");

        // DownloadManager不会覆盖已有的同名文件，需要自己来删除已存在的文件
        File file = new File(getExternalFilesDir("Updates"), mUpdateFilename);
        if (file.exists()) {
            if(!file.delete()) {
                return;
            }
        }
        request.setDestinationUri(Uri.fromFile(file));

        mDownloadId = mDownloadManager.enqueue(request);
    }

    private void installNewVersion() {
        Intent install = new Intent(Intent.ACTION_VIEW);
        Uri downloadFileUri = mDownloadManager.getUriForDownloadedFile(mDownloadId);
        File file = new File(getExternalFilesDir("Updates"), mUpdateFilename);
        if (downloadFileUri != null) {
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            // 在Broadcast中启动活动需要添加Intent.FLAG_ACTIVITY_NEW_TASK
            install.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);    //添加这一句表示对目标应用临时授权该Uri所代表的文件
            install.addCategory("android.intent.category.DEFAULT");
            install.setDataAndType(ShareUtils.getUriFromFile(MainActivity.this, file), "application/vnd.android.package-archive");
            startActivity(install);
        } else {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
            intent.addCategory("android.intent.category.DEFAULT");
            startActivity(intent);
        }
    }
}
