package com.tinmegali.mylocation;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Map;

import static android.R.attr.id;
import static android.R.attr.radius;
import static com.tinmegali.mylocation.R.id.geofence;

public class MainActivity extends AppCompatActivity
        implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        OnMapReadyCallback,
        GoogleMap.OnMapClickListener,
        GoogleMap.OnMarkerClickListener,
        ResultCallback<Status> {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String LAST_PLACE_ID = "geofenceId";
    private static final String LAST_PLACE_ADDRESS = "geofenceLabel";
    private static final String LAST_PLACE_LAT = "geofenceLat";
    private static final String LAST_PLACE_LON = "geofenceLon";
    private static final String GEO_PREFIX = "geo-";

    private static final String KEY_GEOFENCE_RADIUS = "geofenceRadius";
    private static final String KEY_ALL_GEOFENCES = "allGeofences";
    private final static Locale locale = Locale.getDefault();
    private static final String PREF_NAME = "geoPrefs";

//    SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
//    SharedPreferences.Editor editor = sharedPref.edit();

    private PendingIntent geoFencePendingIntent;
    private final int GEOFENCE_REQ_CODE = 0;


    CircleOptions mCircleOptions = new CircleOptions();
    private GoogleMap map;
    private GoogleApiClient googleApiClient;
    private Location lastLocation;

    private EditText etPlaceLabel;
    private EditText etPlaceRadius;
    Button saveBtn;
    Context mContext;

    MapFragment mapFragment;

    private static final String NOTIFICATION_MSG = "NOTIFICATION MSG";

    // Create a Intent send by the notification
    public static Intent makeNotificationIntent(Context context, String msg) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(NOTIFICATION_MSG, msg);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;

        setContentView(R.layout.activity_main);

        setResources();

        createAutoCompleteFragment();

        initGMaps();

        createGoogleApi();
    }

    void setResources() {
        etPlaceLabel = (EditText) findViewById(R.id.et_place_label);
        etPlaceRadius = (EditText) findViewById(R.id.et_place_radius);

        saveBtn = (Button) findViewById(R.id.btn_save);
        if (saveBtn != null) {
            saveBtn.setOnClickListener(saveBtnHandler);
        }
    }


    JSONObject getLastPlaceSearched() {
        JSONObject place = new JSONObject();
        SharedPreferences sharedPref = getPrefs(mContext);
        try {
            place.put("id", sharedPref.getString(LAST_PLACE_ID, "defaultLabel"));
            place.put("address", sharedPref.getString(LAST_PLACE_ADDRESS, "defaultLabel"));
            place.put("lat", (double) sharedPref.getFloat(LAST_PLACE_LAT, -1));
            place.put("lon", (double) sharedPref.getFloat(LAST_PLACE_LON, -1));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return place;
    }

    View.OnClickListener saveBtnHandler = new View.OnClickListener() {
        public void onClick(View v) {
            JSONObject lastPlaceSaved = getLastPlaceSearched();
            double lat = lastPlaceSaved.optDouble("lat");
            double lon = lastPlaceSaved.optDouble("lon");
            LatLng latLng = new LatLng(lat, lon);

            String radiusStr = etPlaceRadius.getText().toString();
            Integer radius = radiusStr.equals("") ? 150 : Integer.parseInt(radiusStr);
            radius = radius < 100 ? 150 : radius;

            String placeLabel = etPlaceLabel.getText().toString();
            placeLabel = placeLabel.equals("") ? String.format("%s, %s", lat, lon) : placeLabel;

            createGeofence(placeLabel, lastPlaceSaved.optString("address"), lastPlaceSaved.optString("id"), latLng, radius);
            redrawCircle(lat, lon, radius);

            String msg = String.format(Locale.getDefault(), "Saved: %s (%s meters)", placeLabel, radius);
            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
        }
    };

    void createAutoCompleteFragment() {
        PlaceAutocompleteFragment autocompleteFragment = (PlaceAutocompleteFragment)
                getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);

        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                // TODO: Get info about the selected place.
                Log.i(TAG, "Place: " + place.getName());
                Log.i(TAG, "Place: " + place.getLatLng());

                LatLng latlng = place.getLatLng();
                float lat = (float) latlng.latitude;
                float lon = (float) latlng.longitude;
                String address = place.getAddress().toString();
                String id = place.getId();
                saveAsLastPlaceSearched(lat, lon, address, id);

                LatLng latLng = new LatLng(place.getLatLng().latitude, place.getLatLng().longitude);
                Log.d(TAG, "onAddressEntered(" + latLng + ")");
                markerForGeofence(latLng);
                drawCircle(latLng);
                markerLocation(place.getAddress().toString(), latLng);
            }

            @Override
            public void onError(Status status) {
                // TODO: Handle the error.
                Log.i(TAG, "An error occurred: " + status);
            }
        });

    }

    void storeRadius(Context context, int radius) {
        getEditor(context).putInt(KEY_GEOFENCE_RADIUS, radius).apply();
    }


//    View.OnClickListener increaseBtnHandler = new View.OnClickListener() {
//        public void onClick(View v) {
//            if (mCircleOptions.getRadius() <= 100)  {
//                mCircleOptions.radius(100);
//            }
//            int radius = (int) mCircleOptions.getRadius() + 20;
//            tvRadius.setText(radius);
//
//            storeRadius(radius);
//            redrawCircle();
//        }
//    };
//
//    View.OnClickListener decreaseBtnHandler = new View.OnClickListener() {
//        public void onClick(View v) {
//            if (mCircleOptions.getRadius() <= 100) return;
//
//            int radius = (int) mCircleOptions.getRadius() - 20;
//            tvRadius.setText(radius);
//
//            storeRadius(radius);
//            redrawCircle();
//        }
//    };


    // Create GoogleApiClient instance
    private void createGoogleApi() {
        Log.d(TAG, "createGoogleApi()");
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Call GoogleApiClient connection when starting the Activity
        googleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Disconnect GoogleApiClient when stopping Activity
        googleApiClient.disconnect();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case geofence: {
                viewGeofences();
                return true;
            }
            case R.id.clear: {
                clearGeofence();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void viewGeofences() {
        String allGeofencesMsg = "";

        Map<String, ?> keys = getPrefs(mContext).getAll();
        for (Map.Entry<String, ?> entry : keys.entrySet()) {
            Log.d("map values", entry.getKey() + ": " + entry.getValue().toString());
            if (entry.getKey().contains(GEO_PREFIX)) {
                allGeofencesMsg += entry.getValue().toString() + "\n\n\n";
            }
        }

        if (allGeofencesMsg.equals("")) {
            allGeofencesMsg = "No Geofence Added.";
        }

        displayDialog(allGeofencesMsg);
    }

    private void displayDialog(String msg) {
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle("My Geofences");
        alertDialog.setMessage(msg);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Close",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
    }

    private final int REQ_PERMISSION = 999;

    // Check for permission to access Location
    private boolean checkPermission() {
        Log.d(TAG, "checkPermission()");
        // Ask for permission if it wasn't granted yet
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED);
    }

    // Asks for permission
    private void askPermission() {
        Log.d(TAG, "askPermission()");
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                REQ_PERMISSION
        );
    }

    // Verify user's response of the permission requested
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult()");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQ_PERMISSION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted
                    getLastKnownLocation();

                } else {
                    // Permission denied
                    permissionsDenied();
                }
                break;
            }
        }
    }

    // App cannot work without the permissions
    private void permissionsDenied() {
        Log.w(TAG, "permissionsDenied()");
        // TODO close app and warn user
    }

    // Initialize GoogleMaps
    private void initGMaps() {
        mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    // Callback called when Map is ready
    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.d(TAG, "onMapReady()");
        map = googleMap;
        map.setOnMapClickListener(this);
        map.setOnMarkerClickListener(this);
    }

    @Override
    public void onMapClick(LatLng latLng) {
        Log.d(TAG, "onMapClick(" + latLng + ")");
        markerForGeofence(latLng);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        Log.d(TAG, "onMarkerClickListener: " + marker.getPosition());
        return false;
    }

    private LocationRequest locationRequest;
    // Defined in mili seconds.
    // This number in extremely low, and should be used only for debug
    private final int UPDATE_INTERVAL = 15 * 60 * 1000;
    private final int FASTEST_INTERVAL = 15 * 60 * 900;

    // Start location Updates
    private void startLocationUpdates() {
        Log.i(TAG, "startLocationUpdates()");
        locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(UPDATE_INTERVAL)
                .setFastestInterval(FASTEST_INTERVAL);

        if (checkPermission())
            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "onLocationChanged [" + location + "]");
        lastLocation = location;
//        writeActualLocation(location);
    }

    // GoogleApiClient.ConnectionCallbacks connected
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.i(TAG, "onConnected()");
        getLastKnownLocation();
        recoverGeofenceMarker();
    }

    // GoogleApiClient.ConnectionCallbacks suspended
    @Override
    public void onConnectionSuspended(int i) {
        Log.w(TAG, "onConnectionSuspended()");
    }

    // GoogleApiClient.OnConnectionFailedListener fail
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.w(TAG, "onConnectionFailed()");
    }

    // Get last known location
    private void getLastKnownLocation() {
        Log.d(TAG, "getLastKnownLocation()");
        if (checkPermission()) {
            lastLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
            if (lastLocation != null) {
                Log.i(TAG, "LasKnown location. " +
                        "Long: " + lastLocation.getLongitude() +
                        " | Lat: " + lastLocation.getLatitude());
                writeLastLocation();
                startLocationUpdates();
            } else {
                Log.w(TAG, "No location retrieved yet");
                startLocationUpdates();
            }
        } else askPermission();
    }

    private void writeActualLocation(Location location) {
//        textLat.setText("Lat: " + location.getLatitude());
//        textLong.setText("Long: " + location.getLongitude());

//        markerLocation(new LatLng(location.getLatitude(), location.getLongitude()));
    }

    private void writeLastLocation() {
        writeActualLocation(lastLocation);
    }

    private Marker locationMarker;

    private void markerLocation(String title, LatLng latLng) {
        Log.i(TAG, "markerLocation(" + latLng + ")");
        MarkerOptions markerOptions = new MarkerOptions()
                .position(latLng)
                .title(title);
        if (map != null) {
            if (locationMarker != null)
                locationMarker.remove();
            locationMarker = map.addMarker(markerOptions);
            float zoom = 16f;
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, zoom);
            map.animateCamera(cameraUpdate);
        }
    }


    private Marker geoFenceMarker;

    private void markerForGeofence(LatLng latLng) {
        Log.i(TAG, "markerForGeofence(" + latLng + ")");
        String title = latLng.latitude + ", " + latLng.longitude;
        // Define marker options
        MarkerOptions markerOptions = new MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                .title(title);
        if (map != null) {
            // Remove last geoFenceMarker
            if (geoFenceMarker != null)
                geoFenceMarker.remove();

            geoFenceMarker = map.addMarker(markerOptions);
        }
    }

    // Start Geofence creation process
//    private void startGeofence() {
//        Log.i(TAG, "startGeofence()");
//        if (geoFenceMarker != null) {
//            Geofence geofence = createGeofence(geoFenceMarker.getPosition().toString().substring(0, 15), geoFenceMarker.getPosition(), GEOFENCE_RADIUS);
//            GeofencingRequest geofenceRequest = createGeofenceRequest(geofence);
//            addGeofence(geofenceRequest);
//        } else {
//            Log.e(TAG, "Geofence marker is null");
//        }
//    }

    private static final long GEO_DURATION = 7 * 24 * 60 * 60 * 1000;
    private static final int GEO_DWELL_TIME = 0;
    private static final float GEOFENCE_RADIUS = 200.0f; // in meters

    // Create a Geofence
    private void createGeofence(String placeLabel, String placeAddress, String id, LatLng latLng, float radius) {
        Log.d(TAG, "createGeofence");
        Geofence geofence = new Geofence.Builder()
                .setRequestId(id)
                .setCircularRegion(latLng.latitude, latLng.longitude, radius)
                .setExpirationDuration(GEO_DURATION)
                .setLoiteringDelay(GEO_DWELL_TIME)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER
                        | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();

        GeofencingRequest geofenceRequest = createGeofenceRequest(geofence);
        addGeofence(geofenceRequest);
        addToGeofencesViewList(id, placeLabel, placeAddress, radius);
    }

    // Add the created GeofenceRequest to the device's monitoring list
    private void addGeofence(GeofencingRequest request) {
        Log.d(TAG, "addGeofence");

        if (checkPermission())
            LocationServices.GeofencingApi.addGeofences(
                    googleApiClient,
                    request,
                    createGeofencePendingIntent()
            ).setResultCallback(this);
    }

    // Create a Geofence Request
    private GeofencingRequest createGeofenceRequest(Geofence geofence) {
        Log.d(TAG, "createGeofenceRequest");
        return new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build();
    }

    void addToGeofencesViewList(String id, String placeLabel, String placeAddress, float radius) {
        String name = String.format("%s%s", GEO_PREFIX, id);
        String content = String.format("%s: %s (%s meters)", placeLabel, placeAddress, radius);
        getEditor(mContext).putString(name, content).apply();

        content = String.format("%s: %s... (%s m)", placeLabel, placeAddress.substring(0,15), radius);
        storeIdWithGeofenceDetails(id, content);
    }

    void storeIdWithGeofenceDetails(String id, String content) {
        getEditor(mContext).putString(id, content).apply();
    }

    public static String getGeofenceLabel(Context context, String id) {
        return getPrefs(context).getString(id, "");
    }

    private PendingIntent createGeofencePendingIntent() {
        Log.d(TAG, "createGeofencePendingIntent");
        if (geoFencePendingIntent != null)
            return geoFencePendingIntent;

        Intent intent = new Intent(this, GeofenceTrasitionService.class);
        return PendingIntent.getService(
                this, GEOFENCE_REQ_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onResult(@NonNull Status status) {
        Log.i(TAG, "onResult: " + status);
        if (status.isSuccess()) {
//            saveGeofence();
//            listGeofences();
//            drawGeofence();
            Toast.makeText(MainActivity.this, "OnResult Success", Toast.LENGTH_LONG).show();
        } else {
            // inform about fail
            Toast.makeText(MainActivity.this, "OnResult Failure", Toast.LENGTH_LONG).show();
        }
    }

    // Draw Geofence circle on GoogleMap
    private Circle geoFenceBorder;

    void drawCircle(LatLng latLng) {
        if (geoFenceBorder != null)
            geoFenceBorder.remove();

        mCircleOptions
                .center(latLng)
                .strokeColor(Color.argb(50, 70, 70, 70))
                .fillColor(Color.argb(100, 150, 150, 150))
                .radius(GEOFENCE_RADIUS);
        geoFenceBorder = map.addCircle(mCircleOptions);
    }

    void redrawCircle() {
        if (geoFenceBorder != null)
            geoFenceBorder.remove();

        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        int radius = sharedPref.getInt(KEY_GEOFENCE_RADIUS, 100);
        float lat = sharedPref.getFloat(LAST_PLACE_LAT, -1);
        float lon = sharedPref.getFloat(LAST_PLACE_LON, -1);
        LatLng latLng = new LatLng(lat, lon);

        mCircleOptions
                .center(latLng)
                .strokeColor(Color.argb(50, 70, 70, 70))
                .fillColor(Color.argb(100, 150, 150, 150))
                .radius(radius);
        geoFenceBorder = map.addCircle(mCircleOptions);
    }

    void redrawCircle(double lat, double lon, int radius) {
        if (geoFenceBorder != null)
            geoFenceBorder.remove();

        LatLng latLng = new LatLng(lat, lon);
        mCircleOptions
                .center(latLng)
                .strokeColor(Color.argb(50, 70, 70, 70))
                .fillColor(Color.argb(100, 150, 150, 150))
                .radius(radius);
        geoFenceBorder = map.addCircle(mCircleOptions);
    }


    // Saving GeoFence marker with prefs mng
    private void saveGeofence() {
        Log.d(TAG, "saveGeofence()");
//        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
//        SharedPreferences.Editor editor = sharedPref.edit();

//        editor.putLong(OLD_KEY_GEOFENCE_LAT, Double.doubleToRawLongBits(geoFenceMarker.getPosition().latitude));
//        editor.putLong(OLD_KEY_GEOFENCE_LON, Double.doubleToRawLongBits(geoFenceMarker.getPosition().longitude));
//        editor.apply();
    }

    private void saveAsLastPlaceSearched(float lat, float lon, String address, String id) {
        Log.d(TAG, "saveAsLastPlaceSearched()");
//        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
//        SharedPreferences.Editor editor = sharedPref.edit();
        SharedPreferences.Editor editor = getEditor(mContext);
        editor.putFloat(LAST_PLACE_LAT, lat);
        editor.putFloat(LAST_PLACE_LON, lon);
        editor.putString(LAST_PLACE_ADDRESS, address);
        editor.putString(LAST_PLACE_ID, id);
        editor.apply();
    }

    // Recovering last Geofence marker
    private void recoverGeofenceMarker() {
        Log.d(TAG, "recoverGeofenceMarker");
//        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
//        if (sharedPref.contains(OLD_KEY_GEOFENCE_LAT) && sharedPref.contains(OLD_KEY_GEOFENCE_LON)) {
//            double lat = Double.longBitsToDouble(sharedPref.getLong(OLD_KEY_GEOFENCE_LAT, -1));
//            double lon = Double.longBitsToDouble(sharedPref.getLong(OLD_KEY_GEOFENCE_LON, -1));
//            LatLng latLng = new LatLng(lat, lon);
//            markerForGeofence(latLng);
//            drawGeofence();
//        }
    }

    // Clear Geofence
    private void clearGeofence() {
        Log.d(TAG, "clearGeofence()");
        LocationServices.GeofencingApi.removeGeofences(
                googleApiClient,
                createGeofencePendingIntent()
        ).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(@NonNull Status status) {
                if (status.isSuccess()) {
                    // remove drawing
                    clearGeofencesViewList();
                    removeGeofenceDraw();
                    Toast.makeText(MainActivity.this, "All Geofences Cleared.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void clearGeofencesViewList() {
        Map<String, ?> keys = getPrefs(mContext).getAll();
        for (Map.Entry<String, ?> entry : keys.entrySet()) {
            Log.d("map values", entry.getKey() + ": " + entry.getValue().toString());
            if (entry.getKey().contains(GEO_PREFIX)) {
                getEditor(mContext).remove(entry.getKey()).apply();
            }
        }
    }

    private void removeGeofenceDraw() {
        Log.d(TAG, "removeGeofenceDraw()");
        if (geoFenceMarker != null)
            geoFenceMarker.remove();
        if (geoFenceBorder != null)
            geoFenceBorder.remove();
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void setString(Context context, String key, String input) {
        getPrefs(context).edit().putString(key, input).apply();
    }

    public static String getString(Context context, String key) {
        return getPrefs(context).getString(key, "");
    }

    public static void setInt(Context context, String key, Integer input) {
        getPrefs(context).edit().putInt(key, input).apply();
    }

    public static SharedPreferences.Editor getEditor(Context context) {
        return getPrefs(context).edit();
    }

}
