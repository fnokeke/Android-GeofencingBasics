package com.tinmegali.mylocation.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * Created by fnokeke on 2/14/17.
 *
 */

class IntentLauncher {

    static Intent getLaunchIntent(Context context, String appPackageName) {
        PackageManager pm = context.getPackageManager();
        Intent appStartIntent = pm.getLaunchIntentForPackage(appPackageName);
        if (appStartIntent == null) {
            appStartIntent = pm.getLaunchIntentForPackage(context.getPackageName());
        }
        return appStartIntent;
    }

    public static void launchApp(Context context, String appPackageName) {
        context.startActivity(getLaunchIntent(context, appPackageName));
    }
}
