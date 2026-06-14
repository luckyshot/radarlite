package com.radarlite.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.radarlite.service.CameraDetectionService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("radarlite_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("service_enabled", false)) return

        val si = Intent(context, CameraDetectionService::class.java).apply {
            action = CameraDetectionService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(si)
        } else {
            context.startService(si)
        }
    }
}
