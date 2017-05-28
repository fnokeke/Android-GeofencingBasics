package com.tinmegali.mylocation.utils;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import org.json.JSONObject;

/**
 * Helper function for broadcasting events to others
 * Created by fnokeke on 5/28/17.
 */

class LocalBroadcastHelper {

    static void broadcast(Context context, JSONObject jsonInput) {
        Intent intent = new Intent(jsonInput.optString("filterKey"));
        intent.putExtra(jsonInput.optString("typeKey"), jsonInput.optString("typeValue"));
        intent.putExtra(jsonInput.optString("contentKey"), jsonInput.optString("contentValue"));
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}
