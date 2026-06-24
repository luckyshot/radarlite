package com.radarlite.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.radarlite.service.CameraDetectionService

class BootReceiver : BroadcastReceiver() {
    private companion object {
        const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = context.getSharedPreferences("radarlite_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("service_enabled", false)) return

        try {
            CameraDetectionService.start(context)
        } catch (e: RuntimeException) {
            // Permission or OEM background-start rules can reject this; the UI will retry on launch.
            Log.w(TAG, "Could not restart monitoring from ${intent.action}", e)
        }
    }
}
