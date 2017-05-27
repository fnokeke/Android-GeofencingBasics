package com.tinmegali.mylocation.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Map;
import java.util.Set;

/**
 * Created by fnokeke on 1/23/17.
 * Store shared preferences
 */

public class Store {

    private static final String PREF_NAME = "BeehiveGeofencePrefs";
    private static final String TAG = "Store";
    public static String SAVED_GEOFENCES = "savedGeofences";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void setString(Context context, String key, String input) {
        getPrefs(context).edit().putString(key, input).apply();
    }

    public static String getString(Context context, String key) {
        return getPrefs(context).getString(key, "");
    }

    public static void setLong(Context context, String key, Long input) {
        getPrefs(context).edit().putLong(key, input).apply();
    }

    public static Long getLong(Context context, String key) {
        return getPrefs(context).getLong(key, 0);
    }
    public static void setInt(Context context, String key, Integer input) {
        getPrefs(context).edit().putInt(key, input).apply();
    }

    public static Integer getInt(Context context, String key) {
        return getPrefs(context).getInt(key, 0);
    }

    public static void setFloat(Context context, String key, Float input) {
        getPrefs(context).edit().putFloat(key, input).apply();
    }

    public static Float getFloat(Context context, String key) {
        return getPrefs(context).getFloat(key, 0);
    }

    public static void setBoolean(Context context, String key, Boolean input) {
        getPrefs(context).edit().putBoolean(key, input).apply();
    }

    public static Boolean getBoolean(Context context, String key) {
        return getPrefs(context).getBoolean(key, false); // use false as default value
    }

    public static void wipeAll(Context context) {
        getPrefs(context).edit().clear().apply();
    }

    @SuppressWarnings("unchecked")
    public static void printAll(Context context) {
        Map<String, ?> prefs = getPrefs(context).getAll();
        for (String key : prefs.keySet()) {
            Object pref = prefs.get(key);
            String printVal = "";
            if (pref instanceof Boolean) {
                printVal = key + " : " + pref;
            }
            if (pref instanceof Float) {
                printVal = key + " : " + pref;
            }
            if (pref instanceof Integer) {
                printVal = key + " : " + pref;
            }
            if (pref instanceof Long) {
                printVal = key + " : " + pref;
            }
            if (pref instanceof String) {
                printVal = key + " : " + pref;
            }
            if (pref instanceof Set<?>) {
                printVal = key + " : " + pref;
            }

            Log.d(TAG, "All Prefs:" + printVal);
        }
    }
}
