package com.tinmegali.mylocation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.tinmegali.mylocation.utils.GeofenceHelper;

public class StartMyServiceAtBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            GeofenceHelper geofenceHelper = new GeofenceHelper(context);
            geofenceHelper.beginGeoEntryPoint();
        }

    }

}