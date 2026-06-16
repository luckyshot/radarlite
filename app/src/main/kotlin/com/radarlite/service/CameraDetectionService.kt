package com.radarlite.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.radarlite.MainActivity
import com.radarlite.R
import com.radarlite.ServiceState
import com.radarlite.alert.*
import com.radarlite.db.*
import com.radarlite.location.LocationState
import com.radarlite.location.LocationStrategy
import com.radarlite.util.GeoUtils
import kotlinx.coroutines.*

class CameraDetectionService : Service() {

    companion object {
        const val ACTION_START         = "com.radarlite.START"
        const val ACTION_STOP          = "com.radarlite.STOP"
        const val NOTIFICATION_ID      = 1
        const val CHANNEL_ID           = "radarlite_service"

        fun start(context: Context, action: String = ACTION_START) {
            val intent = Intent(context, CameraDetectionService::class.java).apply {
                this.action = action
            }

            // Starting monitoring must use startForegroundService on Android O+.
            // Stopping is a normal command and can use startService from the foreground UI.
            if (action == ACTION_START && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private lateinit var locationStrategy: LocationStrategy
    private lateinit var alertEngine: AlertEngine
    private lateinit var soundManager: SoundManager
    private lateinit var cameraDb: CameraDbHelper
    private lateinit var appDb: AppDatabase

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var locationIdleJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        cameraDb     = CameraDbHelper(this).apply { open() }
        appDb        = AppDatabase.get(this)
        soundManager = SoundManager(this)
        alertEngine  = AlertEngine(soundManager) { cam, stage -> logAlert(cam, stage) }
        locationStrategy = LocationStrategy(this) { state -> onLocationUpdate(state) }

        ServiceState.dbVersion.value     = cameraDb.getVersion() ?: "No database"
        ServiceState.dbCameraCount.value = cameraDb.getCameraCount()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, null -> startMonitoring()
            ACTION_STOP        -> { stopMonitoring(); stopSelf(); return START_NOT_STICKY }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopMonitoring()
        soundManager.release()
        cameraDb.close()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoring() {
        startForeground(NOTIFICATION_ID, buildNotification("Passive standby"))
        locationStrategy.start()
        ServiceState.isRunning.value = true
        ServiceState.isReceivingLocation.value = false
        ServiceState.gpsMode.value   = getString(R.string.gps_passive)
    }

    private fun stopMonitoring() {
        locationStrategy.stop()
        locationIdleJob?.cancel()
        alertEngine.reset()
        ServiceState.isRunning.value  = false
        ServiceState.isReceivingLocation.value = false
        ServiceState.lastLat.value = null
        ServiceState.lastLon.value = null
        ServiceState.bearingDeg.value = null
        ServiceState.accuracyM.value = null
        ServiceState.lastFixMs.value = null
        ServiceState.speedKmh.value   = 0f
        ServiceState.camerasNearby.value = 0
        ServiceState.closestCameraDistanceM.value = null
        ServiceState.gpsMode.value    = "—"
    }

    private fun onLocationUpdate(state: LocationState) {
        markLocationActive()
        scope.launch {
            val cameras = cameraDb.getCamerasNear(state.lat, state.lon, 600f)

            alertEngine.process(state, cameras)

            // Keep the activity's status card in sync with each passive location fix.
            ServiceState.lastLat.value = state.lat
            ServiceState.lastLon.value = state.lon
            ServiceState.bearingDeg.value = state.bearingDeg
            ServiceState.accuracyM.value = state.accuracyM
            ServiceState.lastFixMs.value = state.timeMs
            ServiceState.speedKmh.value = state.speedKmh
            ServiceState.camerasNearby.value = cameras.size
            ServiceState.closestCameraDistanceM.value = cameras.minOfOrNull {
                GeoUtils.haversine(state.lat, state.lon, it.lat, it.lon)
            }
            ServiceState.gpsMode.value = getString(R.string.gps_passive)
        }
    }

    private fun markLocationActive() {
        ServiceState.isReceivingLocation.value = true
        locationIdleJob?.cancel()
        // No polling: this one-shot timeout marks the service idle after passive fixes stop.
        locationIdleJob = scope.launch {
            delay(15_000)
            ServiceState.isReceivingLocation.value = false
        }
    }

    private fun logAlert(cam: Camera, @Suppress("UNUSED_PARAMETER") stage: AlertStage) {
        scope.launch(Dispatchers.IO) {
            appDb.alertLogDao().insert(AlertLogEntry(
                cameraId    = cam.id,
                speedKmh    = ServiceState.speedKmh.value,
                speedLimit  = cam.speedLimit,
                cameraType  = cam.type
            ))
            val cutoff = System.currentTimeMillis() - 7 * 24 * 3600 * 1000L
            appDb.alertLogDao().deleteOlderThan(cutoff)
        }
    }

    // ---- Notification ----

    private fun buildNotification(status: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_IMMUTABLE else 0
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
