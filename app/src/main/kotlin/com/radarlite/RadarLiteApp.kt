package com.radarlite

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.radarlite.service.CameraDetectionService

class RadarLiteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CameraDetectionService.CHANNEL_ID,
            "RadarLite",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Road alert monitoring service"
            setSound(null, null)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
