package com.mcmasterbaja.maceng.bajalocationloggerv5;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.os.Environment;
import android.os.Looper;
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
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

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

    // Date Object for filename
    public Date currentDate;

    // UI Objects
    public TextView fileName;
    public TextView logValueText;
    public Button mainButton;

    // Power Manager
    public PowerManager.WakeLock wl;

    // Booleans to Indicate when to log data
    public boolean mRequestingLocationUpdates = false;

    // Labels for Data
    private static final String folder = "LocationLogs";
    private static final String timeL = "Time";
    private static final String latL = "Lat";
    private static final String lonL = "Long";
    private static final String altL = "Alt";
    private static final String radialAccL = "Radial Accuracy";


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
    private String timeStamp;
    private String mLatitudeText;
    private String mLongitudeText;
    private String mAltitudeText;

    private String mRadialAccText;

    private List<String> dataLog = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        currentDate = new Date();
        makeNewFile(null);
        logFolder = makeStorageDir(folder);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mSettingsClient = LocationServices.getSettingsClient(this);

        // Kick off the process of building the LocationCallback, LocationRequest, and
        // LocationSettingsRequest objects.
        createLocationCallback();
        createLocationRequest();
        buildLocationSettingsRequest();

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
        mRequestingLocationUpdates = !mRequestingLocationUpdates;

        if(mRequestingLocationUpdates) {
            mainButton.setText("Stop");
            startLocationUpdates();
        }
        else{
            mainButton.setText("Start");
            //timer.cancel();
            //logLocation.cancel();
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
        long initialT = 0;
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

            mRadialAccText = String.format(Locale.ENGLISH, "%s: %f",radialAccL,
                    mCurrentLocation.getAccuracy());

            timeStamp = timeL+": "+logTime()+" ";

            dataLog.add(Integer.toString(logCount) + " | " + timeStamp + mLatitudeText + mLongitudeText + mAltitudeText + mRadialAccText);
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

            } catch (java.io.IOException e) {
                Log.e("LOG_TAG", "DID NOT WRITE DATA");
            }
        }
    }

    public void setWakeLock(){
        PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakeLock");
        wl.acquire();
    }

    public void makeNewFile(View view){
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

        currentFileName = "Log For: " + DateFormat.getDateInstance().format(currentDate) + ": " + Integer.toString(fileCount);
        currentLog = new File(logFolder, currentFileName);
        while(currentLog.exists()){
            fileCount ++;
            currentFileName = "Log For: " + DateFormat.getDateInstance().format(currentDate) + ": " + Integer.toString(fileCount);
            currentLog = new File(logFolder, currentFileName);
            logCount = 0;
            updateUI();
        }

    }

}