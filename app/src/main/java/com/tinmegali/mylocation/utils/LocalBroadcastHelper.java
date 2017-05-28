package com.tinmegali.mylocation.utils;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

/**
 * Helper function for broadcasting events to others
 * Created by fnokeke on 5/28/17.
 */

class LocalBroadcastHelper {

    static void broadcast(Context context, String filter, String key, String value) {
        Intent intent = new Intent(filter);
        intent.putExtra(key, value);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}
