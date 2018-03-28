package com.mcmasterbaja.maceng.bajalocationloggerv5;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    // Relevant Object declarations

    // File objects
    File currentLog;
    File logFolder;

    // Writer Objects
    BufferedWriter writer;

    //Location API + Settings objects
    /**
     * Provides access to the Fused Location Provider API.
     */
    private FusedLocationProviderClient mFusedLocationClient;

    /**
     * Provides access to the Location Settings API.
     */
    private SettingsClient mSettingsClient;

    /**
     * Stores parameters for requests to the FusedLocationProviderApi.
     */
    private LocationRequest mLocationRequest;

    /**
     * Stores the types of location services the client is interested in using. Used for checking
     * settings to determine if the device has optimal location settings.
     */
    private LocationSettingsRequest mLocationSettingsRequest;

    /**
     * Callback for Location events.
     */
    private LocationCallback mLocationCallback;

    /**
     * Represents a geographical location.
     */
    private Location mCurrentLocation;

    /**
     * Bluetooth Stuff
     */
    BluetoothAdapter mBluetoothAdapter;
    BluetoothDevice mDevice;
    Thread mConnectThread;
    Thread mConnectedThread;


    // Date Object for filename
    public Date currentDate;

    // UI Objects
    public TextView fileName;
    public TextView logValueText;
    public Button mainButton;

    // Power Manager
    public PowerManager.WakeLock wl;
    // Boolean to manage when to override bluetooth syncing
    public boolean overrideBT = false;

    // Boolean to Indicate when to log data
    public boolean mRequestingLocationUpdates = false;

    // String to represent device name
    public String DEVICE_NAME = "Adafruit Bluefruit LE";

    // Labels for Data
    private static final String folder = "LocationLogs";
    private static final String timeL = "Time";
    private static final String latL = "Lat";
    private static final String lonL = "Long";
    private static final String altL = "Alt";
    private static final String radialAccL = "Radial Accuracy";
    private static final String speedL = "Speed: ";


    private static final String TAG = MainActivity.class.getSimpleName();
    /**
     * Constant used in the location settings dialog.
     */
    private static final int REQUEST_CHECK_SETTINGS = 0x1;

    // Variables to Store Relevant Data
    public String currentFileName;
    public int fileCount = 0;

    private static long delay = 1000L;

    private int logCount = 0;
    long initialT = 0;
    private String timeStamp;
    private String mLatitudeText;
    private String mLongitudeText;
    private String mAltitudeText;
    private String mSpeedText;
    private String mRadialAccText;
    private String BTcode;



    private List<String> dataLog = new ArrayList<>();

    /**
     * Private classes for threads made to handle the bluetooth services
     */
    //Connect Thread
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;
            mmDevice = device;
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) { }
            mmSocket = tmp;
        }
        public void run() {
            mBluetoothAdapter.cancelDiscovery();
            try {
                mmSocket.connect();
            } catch (IOException connectException) {
                try {
                    mmSocket.close();
                } catch (IOException closeException) { }
                return;
            }
            mConnectedThread = new ConnectedThread(mmSocket);
            mConnectedThread.start();

        }
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }
    //Connected Thread deals with communications once already connected in the connect thread
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }
        public void run() {
            byte[] buffer = new byte[1024];
            int begin = 0;
            int bytes = 0;
            while (true) {
                try {
                    bytes += mmInStream.read(buffer, bytes, buffer.length - bytes);
                    for(int i = begin; i < bytes; i++) {
                        if(buffer[i] == "#".getBytes()[0]) {
                            mHandler.obtainMessage(1, begin, i, buffer).sendToTarget();
                            begin = i + 1;
                            if(i == bytes - 1) {
                                bytes = 0;
                                begin = 0;
                            }
                        }
                    }
                } catch (IOException e) {
                    break;
                }
            }
        }
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }
    //Handler meant to send data from connected thread and be used in the main UI thread
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            byte[] writeBuf = (byte[]) msg.obj;
            int begin = (int)msg.arg1;
            int end = (int)msg.arg2;

            switch(msg.what) {
                case 1:
                    String writeMessage = new String(writeBuf);
                    writeMessage = writeMessage.substring(begin, end);
                    Log.e(TAG, "Recieved data:"+writeMessage);
                    BTcode = writeMessage;
                    break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        currentDate = new Date();
        logFolder = makeStorageDir(folder);
        makeNewFile(null);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            Log.e(TAG, "device does not support Bluetooth");
        }
        else {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBTIntent, 1);
            }
            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    if (device.getName().equals(DEVICE_NAME)) {
                        mDevice = device;
                        Log.e(TAG, "Device Name:"+mDevice.getName());
                        break;
                    }

                }
            }
            else{
                Toast toast = Toast.makeText(this, "No Paired Devices", Toast.LENGTH_LONG);
                toast.show();
            }
        }



        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mSettingsClient = LocationServices.getSettingsClient(this);

        // Kick off the process of building the LocationCallback, LocationRequest, and
        // LocationSettingsRequest objects.
        createLocationCallback();
        createLocationRequest();
        buildLocationSettingsRequest();

        Log.e(TAG,"Running BT Connect Thread");
        mConnectThread = new ConnectThread(mDevice);
        mConnectThread.start();


    }

    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();

        // Sets the desired interval for active location updates. This interval is
        // inexact. You may not receive updates at all if no location sources are available, or
        // you may receive them slower than requested. You may also receive updates faster than
        // requested if other applications are requesting location at a faster interval.
        mLocationRequest.setInterval(delay);

        // Sets the fastest rate for active location updates. This interval is exact, and your
        // application will never receive updates faster than this value.
        mLocationRequest.setFastestInterval(delay/2);

        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void createLocationCallback() {
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                mCurrentLocation = locationResult.getLastLocation();
                logLocationData();
            }
        };
    }

    private void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        mLocationSettingsRequest = builder.build();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            // Check for the integer request code originally supplied to startResolutionForResult().
            case REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        Log.i(TAG, "User agreed to make required location settings changes.");
                        // Nothing to do. startLocationupdates() gets called in onResume again.
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.i(TAG, "User chose not to make required location settings changes.");
                        mRequestingLocationUpdates = false;
                        break;

                }
                break;
        }
    }

    protected void onStart(){
        super.onStart();
        if (wl != null) {
            wl.release();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

    }

    public void logSwitch (View view){
        Log.e(TAG, "Button Hit");
        mRequestingLocationUpdates = !mRequestingLocationUpdates;

        if(mRequestingLocationUpdates) {
            if (mBluetoothAdapter != null) {
                //Toast toast = Toast.makeText(this, "Awating bluetooth sync...", Toast.LENGTH_SHORT);
                //toast.show();
            }
            else{
                overrideBT = true;
            }
            Toast toast = Toast.makeText(this,"Logging...", Toast.LENGTH_SHORT);
            toast.show();
            mainButton.setText("Stop");
            startLocationUpdates();
        }
        else{
            mainButton.setText("Start");
            stopLocationUpdates();
            storeData();
        }
    }
    private void startLocationUpdates() {
        // Begin by checking if the device has the necessary location settings.
        mSettingsClient.checkLocationSettings(mLocationSettingsRequest)
                .addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
                    @Override
                    public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                        Log.i(TAG, "All location settings are satisfied.");

                        //noinspection MissingPermission
                        mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                                mLocationCallback, Looper.myLooper());

                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        int statusCode = ((ApiException) e).getStatusCode();
                        switch (statusCode) {
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                Log.i(TAG, "Location settings are not satisfied. Attempting to upgrade " +
                                        "location settings ");
                                try {
                                    // Show the dialog by calling startResolutionForResult(), and check the
                                    // result in onActivityResult().
                                    ResolvableApiException rae = (ResolvableApiException) e;
                                    rae.startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS);
                                } catch (IntentSender.SendIntentException sie) {
                                    Log.i(TAG, "PendingIntent unable to execute request.");
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                String errorMessage = "Location settings are inadequate, and cannot be " +
                                        "fixed here. Fix in Settings.";
                                Log.e(TAG, errorMessage);
                                Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                                mRequestingLocationUpdates = false;
                        }

                    }
                });
    }
    /**
     * Removes location updates from the FusedLocationApi.
     */
    private void stopLocationUpdates() {
        /*if (!mRequestingLocationUpdates) {
            Log.d(TAG, "stopLocationUpdates: updates never requested, no-op.");
            return;
        }
        */

        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.
        mFusedLocationClient.removeLocationUpdates(mLocationCallback)
                .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        mRequestingLocationUpdates = false;
                    }
                });
    }

    public void onResume(){
        super.onResume();
        setContentView(R.layout.activity_main);
        logValueText = findViewById(R.id.logText);
        mainButton = findViewById(R.id.Button);
        logValueText.setText(Integer.toString(logCount));
        if(mRequestingLocationUpdates) {
            mainButton.setText("Stop");
        }
        else{
            mainButton.setText("Start");
        }
        updateUI();
    }

    public void onPause() {
        super.onPause();
        storeData();
        setWakeLock();
    }

    public File makeStorageDir(String albumName) {
        // Get the directory for the user's public pictures directory.
        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), albumName);
        if (!file.mkdirs()) {
            Log.e("LOG_TAG", "Directory not created");
        }
        return file;
    }

    public String logTime(){
        if(logCount == 1){
            initialT = System.currentTimeMillis();
        }

        return Long.toString(System.currentTimeMillis()-initialT);
    }
    
    public void updateUI(){
        fileName = findViewById(R.id.fileName);
        fileName.setText(currentFileName);
        logValueText = findViewById(R.id.logText);
        logValueText.setText(Integer.toString(logCount));
    }

    public void logLocationData(){
        if (mCurrentLocation != null){
            logCount++;
            mLatitudeText = String.format(Locale.ENGLISH, "%s: %f ", latL,
                    mCurrentLocation.getLatitude());
            mLongitudeText = String.format(Locale.ENGLISH, "%s: %f ",lonL,
                    mCurrentLocation.getLongitude());
            mAltitudeText = String.format(Locale.ENGLISH, "%s: %f ",altL,
                    mCurrentLocation.getAltitude());
            mSpeedText = String.format(Locale.ENGLISH, "%s: %f",speedL,
                    mCurrentLocation.getSpeed()); //In m/s

            mRadialAccText = String.format(Locale.ENGLISH, "%s: %f",radialAccL,
                    mCurrentLocation.getAccuracy());

            timeStamp = timeL+": "+logTime()+" ";

            dataLog.add(Integer.toString(logCount) + " | " + timeStamp + mLatitudeText + mLongitudeText + mAltitudeText + mRadialAccText +mSpeedText);
            Log.d("LoggingCheck", "LogCount: " + Integer.toString(logCount));
        }
        updateUI();

    }

    public void storeData(){
        if (logCount > 0 ) {
            try {
                writer = new BufferedWriter(new FileWriter(currentLog));
                for (int i = 0; i < dataLog.size(); i++) {
                    writer.write(dataLog.get(i));
                    writer.newLine();
                }
                writer.flush();
                writer.close();
                Log.d("Writing Check", "Writing to: " + currentFileName);

            } catch (java.io.IOException e) {
                Log.e("Data Write Error", "DID NOT WRITE DATA" + e.getMessage());
            }
        }
    }

    public void setWakeLock(){
        PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakeLock");
        wl.acquire();
    }

    public void makeNewFile(View view) {
        /* Recursive implementation, Potential risk for a large count of files
        currentFileName =  "Log For: " + DateFormat.getDateInstance().format(currentDate) + ": " + Integer.toString(fileCount);
        currentLog = new File(logFolder, currentFileName);
        if(currentLog.exists()) {
            fileCount ++;
            makeNewFile(null);
            logCount = 0;
            updateUI();
        }
        */
        if (!mRequestingLocationUpdates) {
            currentFileName = "Log For: " + DateFormat.getDateInstance().format(currentDate) + ": " + Integer.toString(fileCount);
            currentLog = new File(logFolder, currentFileName);
            while (currentLog.exists()) {
                fileCount++;
                currentFileName = "Log For: " + DateFormat.getDateInstance().format(currentDate) + ": " + Integer.toString(fileCount);
                currentLog = new File(logFolder, currentFileName);
                logCount = 0;
                updateUI();
                dataLog.clear();
            }

        }
    }

    public void startBTConnection(BluetoothDevice device, UUID uuid){
        Log.d(TAG, "startBTConnection: Initializing RFCOM Bluetooth Connection.");

    }

    public void BToverride(View view){
        overrideBT = true;
        Toast toast = Toast.makeText(this, "Overriding BT...",Toast.LENGTH_SHORT);
        toast.show();
    }

}