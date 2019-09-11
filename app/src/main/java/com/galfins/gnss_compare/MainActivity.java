/*
 * Copyright 2018 TFI Systems

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.galfins.gnss_compare;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GnssMeasurementsEvent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.androidplot.util.PixelUtils;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.rd.PageIndicatorView;

import java.util.Observable;
import java.util.Observer;

import com.galfins.gnss_compare.DataViewers.DataViewer;
import com.galfins.gnss_compare.DataViewers.DataViewerAdapter;
import com.galfins.gnss_compare.FileLoggers.RawMeasurementsFileLogger;


public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static int dismissableNotificationTextColor;
    /**
     * Tag used for logging to logcat
     */
    @SuppressWarnings("unused")
    private final String TAG = "MainActivity";

    /**
     * Tag used to mark module names for savedInstanceStates of the onCreate method.
     */
    private final String MODULE_NAMES_BUNDLE_TAG = "__module_names";

    /**
     * Permission needed for accessing the measurements from the GNSS chip
     */
    private static final String GNSS_REQUIRED_PERMISSIONS = Manifest.permission.ACCESS_FINE_LOCATION;

    /**
     * Permission needed for accessing the measurements from the GNSS chip
     */
    private static final String LOG_REQUIRED_PERMISSIONS = Manifest.permission.WRITE_EXTERNAL_STORAGE;

    /**
     * Request code used for permissions
     */
    private static final int PERMISSION_REQUEST_CODE = 1;

    /**
     * Client for receiving the location from Google Services
     */
    private FusedLocationProviderClient mFusedLocationClient;

    /**
     * LocationManager object to receive GNSS measurements
     */
    private LocationManager mLocationManager;

    /**
     * ViewPager object, which allows for scrolling over Fragments
     */
    private ViewPager mPager;

    /**
     * Adapter for the ViewPager
     */
    private DataViewerAdapter mPagerAdapter;

    /**
     * Raw measurements logger
     */
    public static RawMeasurementsFileLogger rawMeasurementsLogger = new RawMeasurementsFileLogger("rawMeasurements");

    private static Snackbar rnpFailedSnackbar = null;

    private Menu menu;

    private Observer calculationModuleObserver;

    private GnssCoreService.GnssCoreBinder gnssCoreBinder;

    private boolean mGnssCoreBound = false;

    CalculationModule newModule = null;

    int i = 0;

    // 定义Sensor管理器
    private SensorManager mSensorManager;
    //位置管理对象
    LocationManager locManager;

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        for(DataViewer viewer : mPagerAdapter.getViewers()){
            viewer.updateSensor(sensorEvent,mSensorManager);
        }


    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private class GnssCoreServiceConnector implements ServiceConnection{

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

            if(!mGnssCoreBound) {
                gnssCoreBinder = (GnssCoreService.GnssCoreBinder) service;
                mGnssCoreBound = true;

                gnssCoreBinder.addObserver(calculationModuleObserver);
                gnssCoreBinder.assignUserNotifier(userNotifierHandler);
            }
        }

        public void resetConnection(){
            if(gnssCoreBinder != null && mGnssCoreBound) {
                gnssCoreBinder.removeObserver(calculationModuleObserver);
                mGnssCoreBound = false;

                gnssCoreBinder = null;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            resetConnection();
        }

    }

    private UserNotifier userNotifierHandler = new UserNotifier() {

        Snackbar snackbar = null;
        String snackbarId = "";
        String snackbarText = "";
        int snackbarDuration = 0;
        boolean snackbarAlive = false;

        Runnable snackbarDismisser = new Runnable() {
            @Override
            public void run() {
                while (snackbarAlive) {
                    synchronized (this) {
                        if (snackbarDuration > 100) {
                            snackbarDuration -= 100;
                        } else if (snackbar.isShown()) {
                            snackbar.dismiss();
                            snackbarAlive = false;
                        } else {
                            snackbarAlive = false;
                        }
                    }

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        @Override
        public void displayMessage(String text, int duration, String id) {
            if(mainView == null)
                return;
            if (id == null) {
                snackbar = Snackbar.make(
                        mainView,
                        text,
                        duration
                );
                snackbar.show();
            } else {
                synchronized (this) {
                    boolean snackbarExtended = false;
                    if (id.equals(snackbarId) && text.equals(snackbarText) && snackbar!=null){
                        if(snackbar.isShown()){
                            snackbarDuration = duration;
                            snackbarExtended = true;
                        }
                    }
                    if (!snackbarExtended) {
                        snackbar = Snackbar.make(
                                mainView,
                                text,
                                Snackbar.LENGTH_INDEFINITE);
                        snackbarText = text;
                        snackbarId = id;
                        snackbarDuration = duration;
                        snackbar.show();
                        snackbarAlive = true;
                        new Thread(snackbarDismisser).start();
                    }
                }
            }
        }
    };

    private ServiceConnection mConnection = new GnssCoreServiceConnector() ;

    /**
     * Callback used for receiving phone's location
     */
    LocationCallback locationCallback;
    private static final Object metaDataMutex = new Object();

    public static Location getLocationFromGoogleServices() {
        synchronized (locationFromGoogleServicesMutex) {
            return locationFromGoogleServices;
        }
    }

    /**
     * Callback object assigned to the GNSS measurement callback
     */
    GnssMeasurementsEvent.Callback gnssCallback;

    static View mainView;

    private static Location locationFromGoogleServices = null;

    public static boolean isLocationFromGoogleServicesInitialized(){
        synchronized (locationFromGoogleServicesMutex) {
            return locationFromGoogleServices != null;
        }
    }

    private static final Object locationFromGoogleServicesMutex = new Object();

    /**
     * Bundle storing manifest's meta data, so that it can be used outside of MainActivity
     */
    private static Bundle metaData;

    /**
     * Registers GNSS measurement event manager callback.
     */
    private void registerLocationManager() {

    }

    /**
     * Initializes ViewPager, it's adapter and page indicator view
     */
    private void initializePager(){
        mPager = findViewById(R.id.pager);
        mPagerAdapter = new DataViewerAdapter(getSupportFragmentManager());
        //所有数据视图的初始化
        mPagerAdapter.initialize();
        mPager.setAdapter(mPagerAdapter);
        PageIndicatorView pageIndicatorView = findViewById(R.id.pageIndicatorView);
        pageIndicatorView.setViewPager(mPager);
    }

    /**
     * Initializes the toolbar
     */
    public void initializeToolbar(){
        Toolbar myToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initializeGnssCompareMainActivity();

        if (hasGnssAndLogPermissions()) {
            registerLocationManager();
        } else {
            requestGnssAndLogPermissions();
        }

        mainView = findViewById(R.id.main_view);

        showInitializationDisclamer();

        startService(new Intent(this, GnssCoreService.class));



    }


    private void initializeGnssCompareMainActivity() {

        setContentView(R.layout.activity_main);

        try {
            TextView versionTextView = findViewById(R.id.versionCode);
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;

            versionTextView.setText(getResources().getString(R.string.version_text_view,  version));
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        initializeMetaDataHandler();

        calculationModuleObserver = new Observer() {

            class UiThreadRunnable implements Runnable {

                CalculationModulesArrayList calculationModules;

                public void setCalculationModules(CalculationModulesArrayList newCalculationModules){
                    synchronized (this) {
                        calculationModules = newCalculationModules;
                    }
                }

                @Override
                public void run() {
                    synchronized (this) {
                        for (DataViewer viewer : mPagerAdapter.getViewers())
                            viewer.updateOnUiThread(calculationModules);
                    }
                }
            }

            UiThreadRunnable uiThreadRunnable = new UiThreadRunnable();

            @Override
            public void update(Observable o, Object calculationModules) {

                for(DataViewer viewer : mPagerAdapter.getViewers()){
                    viewer.update((CalculationModulesArrayList) calculationModules);
                }

                uiThreadRunnable.setCalculationModules((CalculationModulesArrayList) calculationModules);

                runOnUiThread(uiThreadRunnable);

            }
        };

        initializeMetaDataHandler();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        PixelUtils.init(this);

        initializePager();
        initializeToolbar();

        dismissableNotificationTextColor = ContextCompat.getColor(this, R.color.colorPrimaryBright2);



    }

    private void showInitializationDisclamer() {

        makeDismissableNotification(
                "All calculations are initialized with phone's FINE location",
                Snackbar.LENGTH_LONG
        );
    }

    private void initializeMetaDataHandler() {
        ApplicationInfo ai = null;
        try {
            ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        if(ai!=null) {
            synchronized (metaDataMutex) {
                metaData = ai.metaData;
            }
        }
    }

    public static String getMetaDataString(String key){
        return metaData.getString(key);
    }

    public static int getMetaDataInt(String key){
        return metaData.getInt(key);
    }

    public static boolean getMetaDataBoolean(String key){
        return metaData.getBoolean(key);
    }

    public static float getMetaDataFloat(String key){
        return metaData.getFloat(key);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 程序退出时取消注册传感器监听器
        mSensorManager.unregisterListener(this);

        Log.d(TAG, "onStop: invoked");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        this.menu = menu;
        return true;
    }

    //下拉item事件
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.action_create_module:
                // User chose the "Settings" item, show the app settings UI...
                Intent preferenceIntent = new Intent(this, CreateModulePreference.class);
                startActivity(preferenceIntent);
                return true;

            case R.id.action_modify_module:
                Intent preferencesIntent = new Intent(this, ModifyModulePreference.class);
                startActivity(preferencesIntent);
                return true;

            case R.id.action_start_stop_log:
                MenuItem logButton = menu.findItem(R.id.action_start_stop_log);
                if (!rawMeasurementsLogger.isStarted()) {
                    rawMeasurementsLogger.startNewLog();
                    makeNotification("Starting raw GNSS measurements log...");
                    logButton.setTitle(R.string.stop_log_button_description);
                } else {
                    rawMeasurementsLogger.closeLog();
                    makeNotification("Stopping raw GNSS measurements log...");
                    logButton.setTitle(R.string.start_log_button_description);
                }
                return true;
             //新增加的传感器开关
            case R.id.action_open_sensors:
                MenuItem sensorsButton = menu.findItem(R.id.action_open_sensors);
                //默认关闭
                if (i==0) {
                    // 获取传感器管理服务
                    mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
                    // 为方向传感器注册监听器
                    mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION), SensorManager.SENSOR_DELAY_UI);
                    // 为磁场传感器注册监听器
                    mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_UI);
                    // 为线性加速度传感器注册监听器
                    mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION), SensorManager.SENSOR_DELAY_UI);
                    // 为压力传感器注册监听器
                    mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE), SensorManager.SENSOR_DELAY_UI);
                    // 为陀螺仪传感器注册监听器
                    mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_UI);
                    makeNotification("Open Sensors Available...");
                    sensorsButton.setTitle(R.string.close_sensors);
                    i=1;
                } else {
                    mSensorManager.unregisterListener(this);
                    //数据全部设置为unavairable
                    for(DataViewer viewer : mPagerAdapter.getViewers()){
                        viewer.setUnavairable();
                    }
                    makeNotification("Close sensors Unavailable...");
                    sensorsButton.setTitle(R.string.open_sensors);
                    i=0;
                }
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }

    /**
     * Called when activity is resumed
     * restarts the data generating threads
     */
    @Override
    public void onResume() {
        super.onResume();

        new Thread(new Runnable() {
            @Override
            public void run() {
                startAndBindGnssCoreService();
            }
        }).start();

        Log.d(TAG, "onResume: invoked");



    }

    public void startAndBindGnssCoreService(){
        if(!GnssCoreService.isServiceStarted()) {
            //todo: encapsulate this in GnssCoreService
            startService(new Intent(MainActivity.this, GnssCoreService.class));

            if(!GnssCoreService.waitForServiceStarted()){
                makeDismissableNotification(
                        "Issue starting GNSS Core service...",
                        Snackbar.LENGTH_INDEFINITE );

                //todo: consider a return here?
            }

        }

        //todo: encapsulate this in GnssCoreService
        bindService(
                new Intent(MainActivity.this, GnssCoreService.class),
                mConnection,
                Context.BIND_AUTO_CREATE);
    }

    /**
     * Called when activity is paused
     * stops the data generating threads
     */
    @Override
    public void onPause() {
        super.onPause();

        if(mGnssCoreBound) {
            unbindService(mConnection);
            ((GnssCoreServiceConnector) mConnection).resetConnection();
        }
        // 程序暂停时取消注册传感器监听器
        mSensorManager.unregisterListener(this);

        Log.d(TAG, "onPause: invoked");
    }

    @Override
    protected void onRestart() {
        super.onRestart();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mLocationManager.unregisterGnssMeasurementsCallback(gnssCallback);
        mFusedLocationClient.removeLocationUpdates(locationCallback);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                registerLocationManager();
            }
        }
    }

    /**
     * Checks if the permission has been granted
     * @return True of false depending on if permission has been granted
     */
    @SuppressLint("ObsoleteSdkInt")
    private boolean hasGnssAndLogPermissions() {
        // Permissions granted at install time.
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                (ContextCompat.checkSelfPermission(this, GNSS_REQUIRED_PERMISSIONS)
                            == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(this, LOG_REQUIRED_PERMISSIONS)
                                == PackageManager.PERMISSION_GRANTED);
    }

    /**
     * Requests permission to access GNSS measurements
     */
    private void requestGnssAndLogPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{GNSS_REQUIRED_PERMISSIONS, LOG_REQUIRED_PERMISSIONS}, PERMISSION_REQUEST_CODE);
    }

    public static void makeNotification(final String note){
        Snackbar snackbar = Snackbar
                .make(mainView, note, Snackbar.LENGTH_LONG);

        snackbar.show();
    }

    public static void makeDismissableNotification(String note, int length){

        final Snackbar snackbar = Snackbar
                .make(mainView, note, length);

        snackbar.setAction("Dismiss", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                snackbar.dismiss();
            }
        });

        snackbar.setActionTextColor(dismissableNotificationTextColor);

        snackbar.show();
    }

    public static void makeRnpFailedNotification(){

        if(rnpFailedSnackbar==null) {
            rnpFailedSnackbar = Snackbar.make(
                    mainView,
                    "Failed to get ephemeris data. Retrying...",
                    Snackbar.LENGTH_LONG
            );
            rnpFailedSnackbar.show();
        } else if (!rnpFailedSnackbar.isShown())
            rnpFailedSnackbar.show();

    }

}

