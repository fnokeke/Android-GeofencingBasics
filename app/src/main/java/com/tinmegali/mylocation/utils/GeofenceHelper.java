package com.tinmegali.mylocation.utils;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.tinmegali.mylocation.GeofenceTransitionService;
import com.tinmegali.mylocation.MainActivity;

import org.json.JSONObject;

import java.util.Stack;

import static android.content.ContentValues.TAG;

/**
 * Helper class for creating geofences
 * Created by fnokeke on 5/28/17.
 */

public class GeofenceHelper {
    private Context mContext;
    private GoogleApiClient googleApiClient;
    private Stack<JSONObject> stack;
    private static final int GEO_DWELL_TIME = 0;
    private PendingIntent geoFencePendingIntent;

    public GeofenceHelper(Context context) {
        mContext = context;
        stack = new Stack<>();
    }

    private void enqueueGeofenceToSave(JSONObject geo) {
        stack.push(geo);
    }

    private JSONObject dequeueGeofenceToSave() {
        return stack.pop();
    }

    public void beginGeoEntryPoint() {
        AlarmHelper.sendNotification(mContext, "GeoEntryPoint Started.", 2222);
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(mContext)
                    .addConnectionCallbacks(createStoredGeofencesCallback())
                    .addOnConnectionFailedListener(getOnConnectionFailedListener())
                    .addApi(LocationServices.API)
                    .build();
        }
        googleApiClient.connect();
    }

    private GoogleApiClient.ConnectionCallbacks createStoredGeofencesCallback() {
        return new GoogleApiClient.ConnectionCallbacks() {
            @Override
            public void onConnected(@Nullable Bundle bundle) {
                createAllGeofencesStoreOnDevice();
            }

            @Override
            public void onConnectionSuspended(int i) {
                Log.d(TAG, "onConnectionSuspended()");
            }
        };
    }

    private GoogleApiClient.OnConnectionFailedListener getOnConnectionFailedListener() {
        return new GoogleApiClient.OnConnectionFailedListener() {
            @Override
            public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                Log.d(TAG, "onConnectionFailed()");
                Toast.makeText(mContext, "Error: onConnectionFailed.", Toast.LENGTH_SHORT).show();
            }
        };
    }

    private void createAllGeofencesStoreOnDevice() {
        String key;
        JSONObject geoJsonObject;
        JSONObject allGeofences = getSavedGeofences();
        for (int i = 0; i < allGeofences.length(); i++) {
            key = allGeofences.names().optString(i);
            geoJsonObject = allGeofences.optJSONObject(key);
            createGeofence(geoJsonObject);
        }
    }

    private GeofencingRequest createGeofenceRequest(Geofence geofence) {
        Log.d(TAG, "createGeofenceRequest");
        return new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build();
    }

    private void createGeofence(JSONObject geo) {
        Geofence geofence = new Geofence.Builder()
                .setRequestId(geo.optString("id"))
                .setCircularRegion(geo.optDouble("lat"), geo.optDouble("lon"), geo.optInt("radius"))
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setLoiteringDelay(GEO_DWELL_TIME)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();

        GeofencingRequest geofenceRequest = createGeofenceRequest(geofence);
        addGeofence(geofenceRequest, geo);
    }

    private void addGeofence(GeofencingRequest request, JSONObject geo) {
        if (checkPermission()) {
            LocationServices.GeofencingApi.addGeofences(googleApiClient, request,
                    createGeofencePendingIntent()).setResultCallback(handleAddGeoCallback());
            enqueueGeofenceToSave(geo);
        }
    }

    private ResultCallback<Status> handleAddGeoCallback() {
        return new ResultCallback<Status>() {
            @Override
            public void onResult(@NonNull Status status) {
                if (status.isSuccess()) {
                    Toast.makeText(mContext, "Geofence Successfully created.", Toast.LENGTH_LONG).show();
                    JSONObject geo = dequeueGeofenceToSave();
                    storeGeofenceOnDevice(geo);
                    LocalBroadcastHelper.broadcast(mContext,
                            MainActivity.REDRAW_CIRCLE_FILTER, MainActivity.REDRAW_CIRCLE_INTENT_KEY, geo.toString());
                } else {
                    Toast.makeText(mContext, "Failed to create geofence.", Toast.LENGTH_LONG).show();
                }
            }
        };
    }

    private void storeGeofenceOnDevice(JSONObject geo) {
        JSONObject allGeofences = getSavedGeofences();
        JsonHelper.setJSONValue(allGeofences, geo.optString("id"), geo);
        setSavedGeofences(allGeofences);
    }

    private JSONObject getSavedGeofences() {
        JSONObject allGeofences = JsonHelper.strToJsonObject(Store.getString(mContext, Store.SAVED_GEOFENCES));
        return valuesStrToJsonObject(allGeofences);
    }

    private void setSavedGeofences(JSONObject allGeofences) {
        allGeofences = valuesJsonObjectToStr(allGeofences);
        Store.setString(mContext, Store.SAVED_GEOFENCES, allGeofences.toString());
    }

    private PendingIntent createGeofencePendingIntent() {
        if (geoFencePendingIntent == null) {
            int req_code = 0;
            Intent intent = new Intent(mContext, GeofenceTransitionService.class);
            geoFencePendingIntent = PendingIntent.getService(mContext, req_code, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        return geoFencePendingIntent;
    }

    // Check for permission to access Location
    private boolean checkPermission() {
        Log.d(TAG, "checkPermission()");
        // Ask for permission if it wasn't granted yet
        return (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED);
    }

    private JSONObject valuesStrToJsonObject(JSONObject allGeofences) {
        String key, value;
        JSONObject jo;
        JSONObject results = new JSONObject();

        for (int i = 0; i < allGeofences.length(); i++) {
            key = allGeofences.names().optString(i);
            value = allGeofences.optString(key);
            jo = JsonHelper.strToJsonObject(value);
            JsonHelper.setJSONValue(results, key, jo);
        }
        return results;
    }

    private JSONObject valuesJsonObjectToStr(JSONObject allGeofences) {
        String key;
        JSONObject joValue;
        JSONObject results = new JSONObject();

        for (int i = 0; i < allGeofences.length(); i++) {
            key = allGeofences.names().optString(i);
            joValue = allGeofences.optJSONObject(key);
            JsonHelper.setJSONValue(results, key, joValue.toString());
        }
        return results;
    }
}
