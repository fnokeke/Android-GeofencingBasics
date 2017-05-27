package com.tinmegali.mylocation;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.GeofencingEvent;
import com.tinmegali.mylocation.utils.AlarmHelper;

import java.util.ArrayList;
import java.util.List;


public class GeofenceTransitionService extends IntentService {

    private static final String TAG = GeofenceTransitionService.class.getSimpleName();

    public static final int GEOFENCE_NOTIFICATION_ID = 3330;

    public GeofenceTransitionService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        // Handling errors
        if (geofencingEvent.hasError()) {
            String errorMsg = getErrorString(geofencingEvent.getErrorCode());
            Log.e(TAG, "onHandleIntent: error: " + errorMsg);
            AlarmHelper.sendNotification(this, "Error onHandleIntent: " + errorMsg, 4444);
            AlarmHelper.sendNotification(this, "Error onHandleIntent: " + errorMsg, 4444);
            return;
        }

        int geoFenceTransition = geofencingEvent.getGeofenceTransition();
        if (geoFenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
                geoFenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT ||
                geoFenceTransition == Geofence.GEOFENCE_TRANSITION_DWELL) {

            List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();
            String geofenceTransitionDetails = getGeoTransDetails(geoFenceTransition, triggeringGeofences);
            AlarmHelper.sendNotification(this, geofenceTransitionDetails, GEOFENCE_NOTIFICATION_ID);
        } else {
            AlarmHelper.sendNotification(this, "Transitions not for existing geofences", 3333);
        }
    }

    private String getGeoTransDetails(int geoFenceTransition, List<Geofence> triggeringGeofences) {
        // get the ID of each geofence triggered
        ArrayList<String> triggeringGeofencesList = new ArrayList<>();
        String geofenceLabel;
        for (Geofence geofence : triggeringGeofences) {
            MainActivity mainActivity = new MainActivity();
            mainActivity.setContext(this);
            geofenceLabel = mainActivity.getGeofenceSummary(geofence.getRequestId());
            triggeringGeofencesList.add(geofenceLabel);
        }

        String status = null;
        if (geoFenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER)
            status = "Entered ";
        else if (geoFenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT)
            status = "Left ";
        else if (geoFenceTransition == Geofence.GEOFENCE_TRANSITION_DWELL)
            status = "Dwell ";
        return status + TextUtils.join(", ", triggeringGeofencesList);
    }

    private static String getErrorString(int errorCode) {
        switch (errorCode) {
            case GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE:
                return "GeoFence not available";
            case GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES:
                return "Too many GeoFences";
            case GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS:
                return "Too many pending intents";
            default:
                return "Unknown error.";
        }
    }


//    public void sendNotification(String msg, int notifId) {
//        Log.i(TAG, "sendNotification: " + msg);
//
//        // Intent to start the main Activity
//        Intent notificationIntent = MainActivity.makeNotificationIntent(mContext, msg);
//
//        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
//        stackBuilder.addParentStack(MainActivity.class);
//        stackBuilder.addNextIntent(notificationIntent);
//        PendingIntent notificationPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
//
//        // Creating and sending Notification
//        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
//        notificationManager.notify(notifId, createNotification(mContext, msg, notificationPendingIntent));
//    }

    // Create notification
//    private Notification createNotification(Context context, String msg, PendingIntent notificationPendingIntent) {
//        Intent launchAppIntent = getLaunchIntent(context, "io.smalldatalab.android.pam");
//        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, launchAppIntent, PendingIntent.FLAG_CANCEL_CURRENT);
//
//        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this);
//        notificationBuilder
//                .setSmallIcon(R.drawable.ic_action_location)
//                .setColor(Color.RED)
//                .setContentTitle(msg)
//                .setContentText("So how are you feeling?")
//                .setContentIntent(contentIntent)
//                .setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE | Notification.DEFAULT_SOUND)
////                .addAction(android.R.drawable.ic_input_add, "Fantastic", contentIntent) // #0
////                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Meh", contentIntent) // #2
////                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Ughh!", contentIntent) // #2
//                .setAutoCancel(true);
//        return notificationBuilder.build();
//    }

//    public static Intent getLaunchIntent(Context context, String appPackageName) {
//        PackageManager pm = context.getPackageManager();
//        Intent appStartIntent = pm.getLaunchIntentForPackage(appPackageName);
//        if (appStartIntent == null) {
//            appStartIntent = pm.getLaunchIntentForPackage(context.getPackageName());
//        }
//        return appStartIntent;
//    }

}
