package com.example.testmouseapp.activities;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.testmouseapp.R;

import com.example.testmouseapp.dataOperations.MovingAverage;
import com.example.testmouseapp.dataOperations.Filter;
import com.example.testmouseapp.threads.CommunicationThread;
import com.example.testmouseapp.threads.ConnectThread;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "MainActivity";

    private SensorManager sensorManager;
    Sensor accelerometer;

    //maximum and minimum acceleration values measured
    float xmax = 0;
    float xmin = 0;
    float ymax = 0;
    float ymin = 0;

    //accelerometer bounds
    float x_pos_bound;
    float x_neg_bound;
    float y_pos_bound;
    float y_neg_bound;

    //printed accelerometer values
    float val_x;
    float val_y;

    //calibration vars
    boolean calibrating = false;
    int num_readings = 0;
    int readings_max = 10000;  //change this to determine how many readings the accelerometer calibrates on
    float x_total;
    float y_total;
    float x_pad = 0;
    float y_pad = 0;

    //Used to interpret Bluetooth messages
    public interface MessageConstants {
        int CONNECTION_OBJ = 0;
        int MESSAGE_READ = 1;
        int MESSAGE_WRITE = 2;
        int MESSAGE_TOAST = 3;
    }

    //bluetooth vars
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private CommunicationThread mm_coms = null;
    private ConnectThread mm_connection = null;
    final int REQUEST_ENABLE_BT = 3;
    final int OPEN_BT_SETTINGS = 6;
    final int SHOW_DEVICES = 9;
    final int REQUEST_COARSE_LOCATION = 12;
    @SuppressLint("HandlerLeak")
    public Handler mm_handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MessageConstants.CONNECTION_OBJ) {
                mm_connection = (ConnectThread) msg.obj;
                execute();
            } else if (msg.what == MessageConstants.MESSAGE_READ) {
                if (!msg.obj.toString().contains("CON: ")) {
                    Toast.makeText(getApplicationContext(), "Received message: " + msg.obj.toString(), Toast.LENGTH_LONG).show();
                }
            } else if (msg.what == MessageConstants.MESSAGE_WRITE) {
                Toast.makeText(getApplicationContext(), "Message \"" + msg.obj.toString() + "\" sent", Toast.LENGTH_SHORT).show();
            } else if (msg.what == MessageConstants.MESSAGE_TOAST) {
                Toast.makeText(getApplicationContext(), msg.obj.toString(), Toast.LENGTH_SHORT).show();
            } else {
                Log.e(TAG, "Received bad message code from handler: " + msg.what);
            }
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate: Initializing accelerometer");

        //get sensor manager services
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        //get sensor (accelerometer in this case)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        //setup listener
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);  //can be changed to different delays

        Log.d(TAG, "onCreate: Registered accelerometer listener");

        Button calibrate = findViewById(R.id.calibrate);
        calibrate.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                activateCalibrate(v);
            }
        });

        //Set up Action Bar
        Toolbar toolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.app_name);
    }

    //on sensor value change, display X and Z values
    @Override
    public void onSensorChanged(SensorEvent event) {

        TextView live_acceleration;
        TextView max_acceleration;

        live_acceleration = findViewById(R.id.acceleration);
        max_acceleration = findViewById(R.id.maximums);

        if (calibrating) {
            live_acceleration.setText("Calibrating");
            calibrateAccelerometer(event);
        }

        else {

            //if (event.values[0] > 0.0254 || event.values[0] < -0.0254) {  //attempt to ignore small values
            if (true) {

                if (event.values[0] > xmax) {xmax = event.values[0];}
                if (event.values[0] < xmin) {xmin = event.values[0];}

                if (event.values[1] > ymax) { ymax = event.values[1];}
                if (event.values[1] < ymin) { ymin = event.values[1];}

                val_x = event.values[0] + x_pad;
                val_y = event.values[1] + y_pad;

                //Log.d(TAG, "onSensorChanged: X: " + event.values[0] + " Y: " + event.values[1] + " Z: " + event.values[2]);
                String data_live = "X: " + val_x + "\nY: " + val_y;
                String data_max = "X Maximum: " + xmax + "\nX Minimum: " + xmin + "\n\nY Maximum: " + ymax + "\nY Minimum: " + ymin;

                live_acceleration.setText(data_live);
                max_acceleration.setText(data_max);
            }

            else {

                String data_live = "X: " + 0 + "\nY: " + 0;
                String data_max = "X Maximum: " + xmax + "\nX Minimum: " + xmin + "\n\nY Maximum: " + ymax + "\nY Minimum: " + ymin;

                live_acceleration.setText(data_live);
                max_acceleration.setText(data_max);
            }
        }

    }

    public void calibrateAccelerometer(SensorEvent event) {
        num_readings += 1;
        xmax = 0;
        ymax = 0;
        xmin = 0;
        ymin = 0;

        if (num_readings > readings_max) {
            x_total += event.values[0];
            y_total += event.values[1];
        }

        else {
            x_pad = x_total / readings_max;
            y_pad = y_total / readings_max;

            calibrating = false;
            num_readings = 0;
            Log.d(TAG, "accelerometer calibrated");
        }
    }

    public void activateCalibrate(View view) {
        calibrating = true;
        x_total = 0;
        y_total = 0;
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void connectDevice(View view) {
        if (bluetoothAdapter == null) {
            String noBtMsg = "Your device does not support Bluetooth. Please connect using a USB cable.";

            Toast noBtToast = Toast.makeText(getApplicationContext(), noBtMsg, Toast.LENGTH_LONG);
            noBtToast.show();
        }
        else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_COARSE_LOCATION);
            }
            else {
                enableBluetooth();
            }
        }
    }

    public void enableBluetooth() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        else {
            Intent showDevices = new Intent(this, DevicesActivity.class);
            Message m = mm_handler.obtainMessage();
            Toast.makeText(this, m.getTarget().toString(), Toast.LENGTH_SHORT).show();
            showDevices.putExtra("android.os.Message", m);
            startActivityForResult(showDevices, SHOW_DEVICES);
        }
    }

    public void execute() {
        //Send messages to server here
        String test1 = "Test message 1 from client";
        mm_coms.write(test1.getBytes());
        String test2 = "Test message 2 from client";
        mm_coms.write(test2.getBytes());

        //
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                //String btEnabledMsg = "Thank you for activating Bluetooth.";
                //Toast noBtToast = Toast.makeText(getApplicationContext(), btEnabledMsg, Toast.LENGTH_LONG);
                //noBtToast.show();
                Intent showDevices = new Intent(this, DevicesActivity.class);
                Message m = mm_handler.obtainMessage();
                showDevices.putExtra("handlerMessage", m);
                startActivityForResult(showDevices, SHOW_DEVICES);
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(getApplicationContext(), "You must enable Bluetooth for wireless connection.", Toast.LENGTH_LONG).show();
            }
        }
        if (requestCode == REQUEST_COARSE_LOCATION) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                enableBluetooth();
            }
            else Toast.makeText(this, "You must enable location permissions to discover devices", Toast.LENGTH_LONG).show();

        }
        if (requestCode == OPEN_BT_SETTINGS) {
            if (!bluetoothAdapter.isEnabled()) {
                String btDisabledMsg = "You must enable Bluetooth for wireless connection.";

                Toast noBtToast = Toast.makeText(getApplicationContext(), btDisabledMsg, Toast.LENGTH_LONG);
                noBtToast.show();
            }
        }
    }

    public void onDestroy() {
        Toast.makeText(this, "Shutting down", Toast.LENGTH_SHORT).show();
        //Shut down communicationsThread and connectThread
        mm_coms = null;
        if (mm_connection != null)
            mm_connection.cancel();
        super.onDestroy();
    }

}

