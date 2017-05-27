package com.tinmegali.mylocation.utils;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;

import com.tinmegali.mylocation.R;

/**
 * Helper.java
 * Created: 1/24/17
 * author: Fabian Okeke
 */

public class AlarmHelper {

    public static void sendNotification(Context context, String title, int notifId) {
        showInstantNotif(context, title, "So how are you feeling?", "io.smalldatalab.android.pam", notifId);
    }

    private static void showInstantNotif(Context context, String title, String message, String appIdToLaunch, Integer NOTIF_ID) {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context);
        mBuilder.setSmallIcon(R.drawable.ic_action_location)
                .setColor(Color.RED)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setSound(getDefaultSound())
                .setContentTitle(title)
                .setContentText(message);

        if (!appIdToLaunch.equals("")) {
            Intent launchAppIntent = IntentLauncher.getLaunchIntent(context, appIdToLaunch);
            PendingIntent contentIntent = PendingIntent.getActivity(context, 0, launchAppIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            mBuilder.setContentIntent(contentIntent);
        }

        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(NOTIF_ID, mBuilder.build());
    }

    private static Uri getDefaultSound() {
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    }

}


