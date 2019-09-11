package com.galfins.gnss_compare.DataViewers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.galfins.gnss_compare.CalculationModulesArrayList;
import com.galfins.gnss_compare.R;

import java.text.DecimalFormat;

import static android.content.Context.SENSOR_SERVICE;

//传感器
public class SensorFragment extends Fragment  implements DataViewer{


    private TextView mSensorLogView;
    private TextView mSensorRawAccView;
    private TextView mSensorRawPressView;
    private TextView mSensorRawMagView;
    private TextView mSensorRawGyroView;
    DecimalFormat fnum = new DecimalFormat("##0.000");
    DecimalFormat fnumOH = new DecimalFormat("##0.0");

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(
                R.layout.fragment_log3, container, false);
        //日志，先不管
        mSensorLogView = (TextView) rootView.findViewById(R.id.sensorview);
        //加速度
        mSensorRawAccView = (TextView) rootView.findViewById(R.id.sensorAccView);
        //压力
        mSensorRawPressView = (TextView) rootView.findViewById(R.id.sensorPressView);
        //磁场
        mSensorRawMagView = (TextView) rootView.findViewById(R.id.sensorMagView);
        //角速度
        mSensorRawGyroView = (TextView) rootView.findViewById(R.id.sensorGyroView);

        return rootView;
    }

    @Override
    public void onLocationFromGoogleServicesResult(Location location)
    {

    }

    @Override
    public void update(CalculationModulesArrayList calculationModules)
    {

    }

    @Override
    public void updateOnUiThread(CalculationModulesArrayList calculationModules)
    {

    }
    public   void setUnavairable(){
        if (mSensorLogView == null)
            return;
        if (mSensorRawAccView == null)
            return;
        if (mSensorRawPressView == null)
            return;
        if (mSensorRawMagView == null)
            return;
        if (mSensorRawGyroView == null)
            return;

        mSensorLogView.setText("Unavairable");
        mSensorRawAccView.setText("Unavairable");
        mSensorRawPressView.setText("Unavairable");
        mSensorRawMagView.setText("Unavairable");
        mSensorRawGyroView.setText("Unavairable");
    }


    float[] mAccelerometerValues;// 用于保存加速度值
    float[] mMagneticValues;// 用于保存地磁值
    float[] mPressureValues;//存储气压值以获取海拔
    @Override
    public void updateSensor(SensorEvent event,SensorManager mSensorManager)
    {
        if (mSensorLogView == null)
            return;
        if (mSensorRawAccView == null)
            return;
        if (mSensorRawPressView == null)
            return;
        if (mSensorRawMagView == null)
            return;
        if (mSensorRawGyroView == null)
            return;

        float[] values = event.values;
        for(int i=0;i<values.length;i++){
            //values[i] = (float)(Math.round(values[i]*1000))/1000;
            values[i] = Float.valueOf(fnum.format(values[i]));
        }
        // 获取传感器类型
        int type = event.sensor.getType();
        StringBuilder sb;
        switch (type) {
            //线性加速度
            case Sensor.TYPE_LINEAR_ACCELERATION:
                sb = new StringBuilder();
                sb.append(" X= ");
                sb.append(values[0]);
                sb.append(", Y= ");
                sb.append(values[1]);
                sb.append(", Z= ");
                sb.append(values[2]);
                mSensorRawAccView.setText(sb.toString());
                mAccelerometerValues = values;
                break;
            //陀螺仪 角速度
            case Sensor.TYPE_GYROSCOPE:
                sb = new StringBuilder();
                sb.append(" X= ");
                sb.append(values[0]);
                sb.append(", Y= ");
                sb.append(values[1]);
                sb.append(", Z= ");
                sb.append(values[2]);
                mSensorRawGyroView.setText(sb.toString());
                break;

                //磁场
            case Sensor.TYPE_MAGNETIC_FIELD:
                sb = new StringBuilder();
                sb.append(" X= ");
                sb.append(values[0]);
                sb.append(", Y= ");
                sb.append(values[1]);
                sb.append(", Z= ");
                sb.append(values[2]);
                mSensorRawMagView.setText(sb.toString());
                mMagneticValues = values;
                break;
                //压力
            case Sensor.TYPE_PRESSURE:
                sb = new StringBuilder();
                sb.append("Ambient Pressure= ");
                sb.append(values[0]);
                mSensorRawPressView.setText(sb.toString());
                mPressureValues = values;
                break;

        }
        //计算方向
        if(mAccelerometerValues!=null && mMagneticValues!=null ){
            sb = new StringBuilder();
            float[] valueso = new float[3];// 最终结果
            float[] R = new float[9];// 旋转矩阵
            SensorManager.getRotationMatrix(R, null, mAccelerometerValues, mMagneticValues);// 得到旋转矩阵
            SensorManager.getOrientation(R, valueso);// 得到最终结果
            float azimuth = (float) Math.toDegrees(valueso[0]);// 航向角
            if (azimuth < 0) {
                azimuth += 360;
            }
            azimuth = azimuth / 5 * 5;// 做了一个处理，表示以5°的为幅度
            float pitch = (float) Math.toDegrees(valueso[1]);// 俯仰角
            float roll = (float) Math.toDegrees(valueso[2]);// 翻滚角
            sb.append("Pitch = ");
            sb.append(fnumOH.format(pitch));
            sb.append(",Roll = ");
            sb.append(fnumOH.format(roll));
            sb.append(",Azimuth = ");
            sb.append(fnumOH.format(azimuth));
            sb.append("\n");
            if(mPressureValues!=null){
                //海拔
                float Altitude =(float)(((Math.pow((1013.25/mPressureValues[0]),(1/5.257)) - 1)*(18.0 + 273.15)) / 0.0065);
                mSensorLogView.setText(sb.toString()+"Altitude = "+fnumOH.format(Altitude));
            }else{
                mSensorLogView.setText(sb.toString()+"Altitude = 0.0");
            }

        }

    }




}
