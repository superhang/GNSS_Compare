package com.galfins.gnss_compare.DataViewers;

import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.galfins.gnss_compare.CalculationModulesArrayList;
import com.galfins.gnss_compare.R;

public class DataListFragment extends Fragment implements DataViewer{
    private ListView list;
    private TextView title;

    @Override
    public void onLocationFromGoogleServicesResult(Location location) {

    }
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(
                R.layout.fragment_log, container, false);

        list = (ListView) rootView.findViewById(R.id.list);
        title = (TextView) rootView.findViewById(R.id.textView);
        String[] arr = new String[20];
        for(int i=1;i<=20;i++){
            arr[i-1] = "数据"+i;
        }
        ArrayAdapter<String> adapter= new ArrayAdapter<String>(getActivity(),android.R.layout.simple_list_item_1, arr);

        list.setAdapter(adapter);
        return rootView;
    }

    @Override
    public void update(CalculationModulesArrayList calculationModules) {

    }

    @Override
    public void updateOnUiThread(CalculationModulesArrayList calculationModules) {

    }

    @Override
    public void updateSensor(SensorEvent event, SensorManager mSensorManager) {

    }

    @Override
    public void setUnavairable() {

    }
}
