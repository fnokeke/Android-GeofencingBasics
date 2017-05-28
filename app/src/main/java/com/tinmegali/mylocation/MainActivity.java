package com.tinmegali.mylocation;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
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
import com.tinmegali.mylocation.utils.AlarmHelper;
import com.tinmegali.mylocation.utils.JsonHelper;
import com.tinmegali.mylocation.utils.Store;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Map;

import static com.tinmegali.mylocation.R.id.geofence;

public class MainActivity extends AppCompatActivity implements
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
    private static final String RECREATE_PREFIX = "recreate-";

    private static final String KEY_GEOFENCE_RADIUS = "geofenceRadius";
    private static final String KEY_ALL_GEOFENCES = "allGeofences";
    private final static Locale locale = Locale.getDefault();
    private static final String PREF_NAME = "geoPrefs";
    public static final String IS_REBOOT_SIGNAL = "isSignalFromReboot";
    public static final String REDRAW_CIRCLE_FILTER = "redrawCircleIntentFilter";
    public static final String REDRAW_CIRCLE_INTENT_KEY = "redrawCircleIntentKey";

//    SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
//    SharedPreferences.Editor editor = sharedPref.edit();

    private PendingIntent geoFencePendingIntent;
    private final int GEOFENCE_REQ_CODE = 0;
    JSONObject mGeo = new JSONObject();


    CircleOptions mCircleOptions = new CircleOptions();
    private Circle geoFenceBorder;
    private GoogleMap map;
    private GoogleApiClient googleApiClient;
    private Location lastLocation;

    private EditText etPlaceLabel;
    private EditText etPlaceRadius;
    Button saveBtn;
    Context mContext;

    MapFragment mapFragment;

    public void setContext(Context context) {
        mContext = context;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        setContentView(R.layout.activity_main);
        setResources();
        createAutoCompleteFragment();
        initGMaps();
        initBroadcastReceiver();
    }

    private void initBroadcastReceiver() {
        LocalBroadcastManager.getInstance(mContext).registerReceiver(
                uiBroadcastReceiver, new IntentFilter(REDRAW_CIRCLE_FILTER));
    }

    private BroadcastReceiver uiBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            JSONObject geo = JsonHelper.strToJsonObject(intent.getStringExtra(REDRAW_CIRCLE_INTENT_KEY));
            redrawCircle(geo.optDouble("lat"), geo.optDouble("lon"), geo.optInt("radius"));
        }
    };

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

            createGeofence(lastPlaceSaved.optString("id"), placeLabel, lastPlaceSaved.optString("address"), latLng, radius);
            String msg = String.format(Locale.getDefault(), "Saved: %s (%s meters)", placeLabel, radius);
            Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show();
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

    private GoogleApiClient.ConnectionCallbacks getGoogleCallback() {
        return new GoogleApiClient.ConnectionCallbacks() {
            @Override
            public void onConnected(@Nullable Bundle bundle) {
                AlarmHelper.sendNotification(mContext, "boss mode activated", 5454);
                recreateAllGeofences();
            }

            @Override
            public void onConnectionSuspended(int i) {
                AlarmHelper.sendNotification(mContext, "boss suspended", 5455);

            }
        };
    }

    private GoogleApiClient.OnConnectionFailedListener getOnConnectionFailedListener() {
        return new GoogleApiClient.OnConnectionFailedListener() {
            @Override
            public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                AlarmHelper.sendNotification(mContext, "boss failed", 5459);
            }
        };
    }

    public void beginGeoCreationProcess() {
        AlarmHelper.sendNotification(mContext, "2nd reGoogleApi next.", 2222);
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(mContext)
                    .addConnectionCallbacks(getGoogleCallback())
                    .addOnConnectionFailedListener(getOnConnectionFailedListener())
                    .addApi(LocationServices.API)
                    .build();
        }
        googleApiClient.connect();
    }

    public void createGoogleApi() {
        AlarmHelper.sendNotification(mContext, "CreateGoogleApi next.", 2222);
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        AlarmHelper.sendNotification(mContext, "onConnected follows.", 2222);
        Log.i(TAG, "onConnected()");
        getLastKnownLocation();
        recoverGeofenceMarker();
//        recreateAllGeofences();
        AlarmHelper.sendNotification(mContext, "googleapi onConnected", 5459);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.w(TAG, "onConnectionSuspended()");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.w(TAG, "onConnectionFailed()");
    }


    @Override
    protected void onStart() {
        super.onStart();

        // Call GoogleApiClient connection when starting the Activity
//        createGoogleApi();
//        googleApiClient.connect();
        beginGeoCreationProcess();
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Disconnect GoogleApiClient when stopping Activity
//        googleApiClient.disconnect();
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
                viewAllGeofences();
                return true;
            }
            case R.id.clear: {
                clearAllGeofences();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }


    private void viewAllGeofences() {
        JSONObject allGeofences = getSavedGeofences();
        String geofenceSummary = "";
        String key, label, address;
        int radius;
        JSONObject joValue;

        for (int i = 0; i < allGeofences.length(); i++) {
            key = allGeofences.names().optString(i);
            joValue = allGeofences.optJSONObject(key);
            label = joValue.optString("label");
            address = joValue.optString("address");
            radius = joValue.optInt("radius");
            geofenceSummary += String.format(locale, "%s: %s (%d meters)\n\n\n", label, address, radius);
        }

        if (allGeofences.length() == 0) {
            geofenceSummary = "No Geofence Added.";
        }

        displayDialog(geofenceSummary);
    }

    public void recreateAllGeofences() {
        AlarmHelper.sendNotification(mContext, "Geofence recreated after device boot.", 2221);
        String key;
        JSONObject joValue;
        JSONObject allGeofences = getSavedGeofences();
        for (int i = 0; i < allGeofences.length(); i++) {
            key = allGeofences.names().optString(i);
            joValue = allGeofences.optJSONObject(key);
            createGeofence(
                    joValue.optString("id"),
                    joValue.optString("label"),
                    joValue.optString("address"),
                    new LatLng(joValue.optDouble("lat"), joValue.optDouble("lon")),
                    joValue.optInt("radius")
            );
        }
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
        return (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
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
//        Log.d(TAG, "onMapClick(" + latLng + ")");
//        markerForGeofence(latLng);
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
            float zoom = 14f;
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

    private static final int GEO_DWELL_TIME = 0;
    private static final float GEOFENCE_RADIUS = 200.0f; // in meters

    // Create a Geofence
    private void createGeofence(String id, String label, String address, LatLng latLng, int radius) {
        Log.d(TAG, "createGeofence");
        Geofence geofence = new Geofence.Builder()
                .setRequestId(id)
                .setCircularRegion(latLng.latitude, latLng.longitude, radius)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setLoiteringDelay(GEO_DWELL_TIME)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();

        GeofencingRequest geofenceRequest = createGeofenceRequest(geofence);
        addGeofence(geofenceRequest);

        JSONObject geo = new JSONObject();
        JsonHelper.setJSONValue(geo, "id", id);
        JsonHelper.setJSONValue(geo, "label", label);
        JsonHelper.setJSONValue(geo, "address", address);
        JsonHelper.setJSONValue(geo, "lat", latLng.latitude);
        JsonHelper.setJSONValue(geo, "lon", latLng.longitude);
        JsonHelper.setJSONValue(geo, "radius", radius);
        setGeofenceToSave(geo);
    }

    private void storeGeofenceOnDevice(String id, String label, String address, LatLng latLng, int radius) {
        JSONObject newJsonGeofence = new JSONObject();
        JsonHelper.setJSONValue(newJsonGeofence, "id", id);
        JsonHelper.setJSONValue(newJsonGeofence, "label", label);
        JsonHelper.setJSONValue(newJsonGeofence, "address", address);
        JsonHelper.setJSONValue(newJsonGeofence, "lat", latLng.latitude);
        JsonHelper.setJSONValue(newJsonGeofence, "lon", latLng.longitude);
        JsonHelper.setJSONValue(newJsonGeofence, "radius", radius);
        updateSavedGeofences(id, newJsonGeofence);
    }

    private void updateSavedGeofences(String id, JSONObject newJsonGeofence) {
        JSONObject allGeofences = getSavedGeofences();
        JsonHelper.setJSONValue(allGeofences, id, newJsonGeofence);
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

    // Add the created GeofenceRequest to the device's monitoring list
    private void addGeofence(GeofencingRequest request) {
        Log.d(TAG, "addGeofence");
        if (checkPermission())
            LocationServices.GeofencingApi.addGeofences(googleApiClient, request,
                    createGeofencePendingIntent()).setResultCallback(new ResultCallback<Status>() {
                @Override
                public void onResult(@NonNull Status status) {
                    workOnCallback(status);
                }
            });
    }

    // Create a Geofence Request
    private GeofencingRequest createGeofenceRequest(Geofence geofence) {
        Log.d(TAG, "createGeofenceRequest");
        return new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build();
    }

    @Override
    public void onResult(@NonNull Status status) {
        workOnCallback(status);
    }

    private void workOnCallback(Status status) {
        Log.i(TAG, "onResult: " + status);
        if (status.isSuccess()) {
            Toast.makeText(mContext, "Geofence Successfully created.", Toast.LENGTH_LONG).show();

            JSONObject geo = getGeofenceToSave();
            String pID = geo.optString("id");
            String pLabel = geo.optString("label");
            String pAddress = geo.optString("address");
            int pRadius = geo.optInt("radius");
            LatLng pLatLng = new LatLng(geo.optDouble("lat"), geo.optDouble("lon"));

//            addToGeofencesViewList(pID, pLabel, pAddress, pRadius);
            storeGeofenceOnDevice(pID, pLabel, pAddress, pLatLng, pRadius);
            redrawCircle(pLatLng.latitude, pLatLng.longitude, pRadius);

        } else {
            // inform about fail
            Toast.makeText(MainActivity.this, "Failed to create geofence.", Toast.LENGTH_LONG).show();
        }
    }

    void addToGeofencesViewList(String id, String placeLabel, String placeAddress, float radius) {
        String name = String.format("%s%s", GEO_PREFIX, id);
        String content = String.format("%s: %s (%s meters)", placeLabel, placeAddress, radius);
        getEditor(mContext).putString(name, content).apply();

        content = String.format("%s: %s... (%s m)", placeLabel, placeAddress.substring(0, 15), radius);
        storeIdWithGeofenceDetails(id, content);
    }

    void storeIdWithGeofenceDetails(String id, String content) {
        getEditor(mContext).putString(id, content).apply();
    }

    public String getGeofenceSummary(String geoId) {
        JSONObject geo = getSavedGeofences().optJSONObject(geoId);
        String label = geo.optString("label");
        String address = geo.optString("address").substring(0, 25);
        int radius = geo.optInt("radius");
        return String.format(locale, "%s: %s ... (%d m)", label, address, radius);
    }

    private PendingIntent createGeofencePendingIntent() {
        Log.d(TAG, "createGeofencePendingIntent");
        if (geoFencePendingIntent != null)
            return geoFencePendingIntent;

        Intent intent = new Intent(mContext, GeofenceTransitionService.class);
        return PendingIntent.getService(mContext, GEOFENCE_REQ_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    // Draw Geofence circle on GoogleMap

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


    void redrawCircle(double lat, double lon, int radius) {
        if (map == null) return; // MainActivity view not active

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
    private void clearAllGeofences() {
        LocationServices.GeofencingApi.removeGeofences(
                googleApiClient,
                createGeofencePendingIntent()
        ).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(@NonNull Status status) {
                if (status.isSuccess()) {
                    clearGeofencesViewList();
                    removeGeofenceDraw();
                    Toast.makeText(mContext, "All Geofences Cleared.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void clearGeofencesViewList() {
        setSavedGeofences(new JSONObject());
    }

    private void oldclearGeofencesViewList() {
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

    public static SharedPreferences.Editor getEditor(Context context) {
        return getPrefs(context).edit();
    }

    public void setGeofenceToSave(JSONObject geo) {
        mGeo = geo;
    }

    public JSONObject getGeofenceToSave() {
        return mGeo;
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


    private void oldRecreateSingleGeofence(String geoContent) {
        JSONObject geo;
        if (geoContent.equals("")) return;
        try {
            geo = new JSONObject(geoContent);
            createGeofence(
                    geo.optString("id"),
                    geo.optString("placeLabel"),
                    geo.optString("address"),
                    new LatLng(geo.optDouble("lat"), geo.optDouble("lon")),
                    geo.optInt("radius"));
        } catch (JSONException e) {
            Log.e(TAG, "oldRecreateSingleGeofence: json-exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    //    private void oldviewGeofences() {
//        String allGeofencesMsg = "";
//
//        Map<String, ?> keys = getPrefs(mContext).getAll();
//        for (Map.Entry<String, ?> entry : keys.entrySet()) {
//            Log.d("map values", entry.getKey() + ": " + entry.getValue().toString());
//            if (entry.getKey().contains(GEO_PREFIX)) {
//                allGeofencesMsg += entry.getValue().toString() + "\n\n\n";
//            }
//        }
//
//        if (allGeofencesMsg.equals("")) {
//            allGeofencesMsg = "No Geofence Added.";
//        }
//
//        displayDialog(allGeofencesMsg);
//    }
    // Saving GeoFence marker with prefs mng
    private void saveGeofence() {
        Log.d(TAG, "saveGeofence()");
//        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
//        SharedPreferences.Editor editor = sharedPref.edit();

//        editor.putLong(OLD_KEY_GEOFENCE_LAT, Double.doubleToRawLongBits(geoFenceMarker.getPosition().latitude));
//        editor.putLong(OLD_KEY_GEOFENCE_LON, Double.doubleToRawLongBits(geoFenceMarker.getPosition().longitude));
//        editor.apply();
    }

    public static String oldgetGeofenceLabel(Context context, String id) {
        return getPrefs(context).getString(id, "");
    }

    public void oldrecreateAllGeofences() {
        clearGeofencesViewList();
        GeofenceTransitionService gt = new GeofenceTransitionService();
        AlarmHelper.sendNotification(mContext, "Geofence recreated after boot.", 2221);

        Map<String, ?> keys = getPrefs(mContext).getAll();
        String geoId;
        String geoContent;
        int c = 5;
        for (Map.Entry<String, ?> entry : keys.entrySet()) {
            Log.d("map values", entry.getKey() + ": " + entry.getValue().toString());
            geoId = RECREATE_PREFIX + entry.getKey();
            geoContent = entry.getValue().toString();
//            geoContent = getPrefs(mContext).getString(geoId, "");
            if (geoId.contains(RECREATE_PREFIX)) {
                oldRecreateSingleGeofence(geoContent);
                AlarmHelper.sendNotification(mContext, geoContent, c);
                c += 1;
            }
        }
    }


//    private static final String NOTIFICATION_MSG = "NOTIFICATION MSG";

//    // Create a Intent send by the notification
//    public static Intent makeNotificationIntent(Context context, String msg) {
//        Intent intent = new Intent(context, MainActivity.class);
//        intent.putExtra(NOTIFICATION_MSG, msg);
//        return intent;
//    }


}

// TODO: 5/27/17 avoid showing duplicate list of the same addresses / check if address has already been added before adding new one
