package com.radarlite.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.ActivityTransition
import com.radarlite.service.CameraDetectionService

class ActivityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return

        for (event in result.transitionEvents) {
            val action = when {
                event.activityType == DetectedActivity.IN_VEHICLE &&
                event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER -> CameraDetectionService.ACTION_DRIVING_START
                event.activityType == DetectedActivity.ON_BICYCLE &&
                event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER -> CameraDetectionService.ACTION_DRIVING_START
                event.activityType == DetectedActivity.IN_VEHICLE &&
                event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT  -> CameraDetectionService.ACTION_DRIVING_STOP
                event.activityType == DetectedActivity.STILL &&
                event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER -> CameraDetectionService.ACTION_DRIVING_STOP
                else -> continue
            }
            val si = Intent(context, CameraDetectionService::class.java).apply { this.action = action }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(si)
            } else {
                context.startService(si)
            }
        }
    }
}
