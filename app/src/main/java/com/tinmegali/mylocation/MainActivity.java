package com.tinmegali.mylocation;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.common.api.Status;
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
import com.tinmegali.mylocation.utils.GeofenceHelper;
import com.tinmegali.mylocation.utils.JsonHelper;

import org.json.JSONObject;

import java.util.Locale;

import static com.tinmegali.mylocation.R.id.geofence;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String LAST_PLACE_ID = "geofenceId";
    private static final String LAST_PLACE_ADDRESS = "geofenceLabel";
    private static final String LAST_PLACE_LAT = "geofenceLat";
    private static final String LAST_PLACE_LON = "geofenceLon";

    private static final String PREF_NAME = "geoPrefs";
    public static final String REDRAW_CIRCLE_FILTER = "redrawCircleIntentFilter";
    public static final String REDRAW_TYPE_KEY = "redrawType";
    public static final String REDRAW_CONTENT_KEY = "redrawContent";
    public static final String ACTION_REDRAW_CIRCLE = "redrawCircleIntentKey";
    public static final String ACTION_REMOVE_CIRCLE = "removeDrawCircleIntentKey";

    private Marker locationMarker;
    private final static Locale locale = Locale.getDefault();

    CircleOptions mCircleOptions = new CircleOptions();
    private Circle geoFenceBorder;
    private GoogleMap map;

    private EditText etPlaceLabel;
    private EditText etPlaceRadius;
    Button saveBtn;
    Context mContext;

    private GeofenceHelper geofenceHelper;

    MapFragment mapFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        setContentView(R.layout.activity_main);
        setResources();
        init();
    }

    private void init() {
        initAutoCompleteFragment();
        initGMaps();
        initGeofence();
        initBroadcastReceiver();
        if (!checkPermission()) askPermission();
    }

    private void initAutoCompleteFragment() {
        PlaceAutocompleteFragment autocompleteFragment = (PlaceAutocompleteFragment) getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);
        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
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

    private void saveAsLastPlaceSearched(float lat, float lon, String address, String id) {
        SharedPreferences.Editor editor = getEditor(mContext);
        editor.putFloat(LAST_PLACE_LAT, lat);
        editor.putFloat(LAST_PLACE_LON, lon);
        editor.putString(LAST_PLACE_ADDRESS, address);
        editor.putString(LAST_PLACE_ID, id);
        editor.apply();
    }

    private void initGMaps() {
        mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    private void initGeofence() {
        geofenceHelper = new GeofenceHelper(mContext);
        geofenceHelper.connectGoogleApiClient();
    }

    private void initBroadcastReceiver() {
        LocalBroadcastManager.getInstance(mContext).registerReceiver(uiBroadcastReceiver, new IntentFilter(REDRAW_CIRCLE_FILTER));
    }

    private BroadcastReceiver uiBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String redrawType = intent.getStringExtra(REDRAW_TYPE_KEY);
            String redrawContent = intent.getStringExtra(REDRAW_CONTENT_KEY);
            switch (redrawType) {
                case ACTION_REDRAW_CIRCLE:
                    JSONObject geo = JsonHelper.strToJsonObject(redrawContent);
                    redrawCircle(geo.optDouble("lat"), geo.optDouble("lon"), geo.optInt("radius"));
                    break;
                case ACTION_REMOVE_CIRCLE:
                    removeGeofenceDraw();
                    break;
            }
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

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static SharedPreferences.Editor getEditor(Context context) {
        return getPrefs(context).edit();
    }

    JSONObject getLastPlaceSearched() {
        JSONObject place = new JSONObject();
        SharedPreferences sharedPref = getPrefs(mContext);
        JsonHelper.setJSONValue(place, "id", sharedPref.getString(LAST_PLACE_ID, "defaultLabel"));
        JsonHelper.setJSONValue(place, "address", sharedPref.getString(LAST_PLACE_ADDRESS, "defaultLabel"));
        JsonHelper.setJSONValue(place, "lat", (double) sharedPref.getFloat(LAST_PLACE_LAT, -1));
        JsonHelper.setJSONValue(place, "lon", (double) sharedPref.getFloat(LAST_PLACE_LON, -1));
        return place;
    }

    View.OnClickListener saveBtnHandler = new View.OnClickListener() {
        public void onClick(View v) {
            JSONObject lastPlaceSaved = getLastPlaceSearched();
            double lat = lastPlaceSaved.optDouble("lat");
            double lon = lastPlaceSaved.optDouble("lon");

            String radiusStr = etPlaceRadius.getText().toString();
            Integer radius = radiusStr.equals("") ? 150 : Integer.parseInt(radiusStr);
            radius = radius < 150 ? 150 : radius;
            etPlaceRadius.setText(String.valueOf(radius));

            String label = etPlaceLabel.getText().toString();
            label = label.equals("") ? String.format("%s..", lastPlaceSaved.optString("address").substring(0,7)) : label;
            etPlaceLabel.setText(label);

            JSONObject geo = new JSONObject();
            JsonHelper.setJSONValue(geo, "id", lastPlaceSaved.optString("id"));
            JsonHelper.setJSONValue(geo, "label", label);
            JsonHelper.setJSONValue(geo, "address", lastPlaceSaved.optString("address"));
            JsonHelper.setJSONValue(geo, "lat", lat);
            JsonHelper.setJSONValue(geo, "lon", lon);
            JsonHelper.setJSONValue(geo, "radius", radius);
            geofenceHelper.createGeofence(geo);

            String msg = String.format(Locale.getDefault(), "Saved: %s (%s meters)", label, radius);
            Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show();

        }
    };


    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        geofenceHelper.disconnectGoogleApiClient();
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
                geofenceHelper.clearAllGeofences();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }


    private void viewAllGeofences() {
        JSONObject allGeofences = GeofenceHelper.getSavedGeofences(mContext);
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

    private boolean checkPermission() {
        return (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
    }

    private void askPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_PERMISSION);
    }

    // Verify user's response of the permission requested
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult()");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQ_PERMISSION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(mContext, "Add your places (Home, Work...)", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(mContext, "App will not work unless you grant location permission. Try Again.", Toast.LENGTH_LONG).show();
                    finish();
                }
                break;
            }
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.d(TAG, "onMapReady()");
        map = googleMap;
    }


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


    void drawCircle(LatLng latLng) {
        if (geoFenceBorder != null)
            geoFenceBorder.remove();

        final float GEOFENCE_RADIUS = 200.0f; // in meters
        mCircleOptions
                .center(latLng)
                .strokeColor(Color.argb(50, 70, 70, 70))
                .fillColor(Color.argb(100, 150, 150, 150))
                .radius(GEOFENCE_RADIUS);
        geoFenceBorder = map.addCircle(mCircleOptions);
    }


    void redrawCircle(double lat, double lon, int radius) {
        if (map == null) return;

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

    private void removeGeofenceDraw() {
        Log.d(TAG, "removeGeofenceDraw()");
        if (geoFenceMarker != null)
            geoFenceMarker.remove();
        if (geoFenceBorder != null)
            geoFenceBorder.remove();
    }

}

