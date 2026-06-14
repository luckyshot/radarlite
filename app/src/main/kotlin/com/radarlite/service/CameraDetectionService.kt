package com.radarlite.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.radarlite.MainActivity
import com.radarlite.R
import com.radarlite.ServiceState
import com.radarlite.alert.*
import com.radarlite.db.*
import com.radarlite.location.LocationState
import com.radarlite.location.LocationStrategy
import com.radarlite.update.DatabaseUpdater
import kotlinx.coroutines.*

class CameraDetectionService : Service() {

    companion object {
        const val ACTION_START         = "com.radarlite.START"
        const val ACTION_STOP          = "com.radarlite.STOP"
        const val ACTION_DRIVING_START = "com.radarlite.DRIVING_START"
        const val ACTION_DRIVING_STOP  = "com.radarlite.DRIVING_STOP"
        const val NOTIFICATION_ID      = 1
        const val CHANNEL_ID           = "radarlite_service"
    }

    private lateinit var locationStrategy: LocationStrategy
    private lateinit var alertEngine: AlertEngine
    private lateinit var soundManager: SoundManager
    private lateinit var cameraDb: CameraDbHelper
    private lateinit var appDb: AppDatabase
    private lateinit var activityClient: ActivityRecognitionClient

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var lastFixMs = 0L
    private var isDriving = false
    private var passiveFallbackJob: Job? = null
    private var drivingStopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        cameraDb     = CameraDbHelper(this).apply { open() }
        appDb        = AppDatabase.get(this)
        soundManager = SoundManager(this)
        alertEngine  = AlertEngine(soundManager) { cam, stage -> logAlert(cam, stage) }
        locationStrategy = LocationStrategy(this) { state -> onLocationUpdate(state) }
        activityClient   = ActivityRecognition.getClient(this)

        ServiceState.dbVersion.value     = cameraDb.getVersion() ?: "No database"
        ServiceState.dbCameraCount.value = cameraDb.getCameraCount()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, null -> startMonitoring()
            ACTION_STOP        -> { stopMonitoring(); stopSelf(); return START_NOT_STICKY }
            ACTION_DRIVING_START -> onDrivingStart()
            ACTION_DRIVING_STOP  -> onDrivingStop()
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
        startForeground(NOTIFICATION_ID, buildNotification("Monitoring"))
        locationStrategy.start()
        requestActivityTransitions()
        startPassiveFallbackMonitor()
        ServiceState.isRunning.value = true
        ServiceState.gpsMode.value   = getString(R.string.gps_passive)
    }

    private fun stopMonitoring() {
        locationStrategy.stop()
        removeActivityTransitions()
        passiveFallbackJob?.cancel()
        alertEngine.reset()
        ServiceState.isRunning.value  = false
        ServiceState.speedKmh.value   = 0f
        ServiceState.camerasNearby.value = 0
        ServiceState.gpsMode.value    = "—"
    }

    private fun onLocationUpdate(state: LocationState) {
        lastFixMs = System.currentTimeMillis()

        scope.launch {
            val cameras = cameraDb.getCamerasNear(state.lat, state.lon, 600f)

            // upgrade to precise mode when close to a camera
            locationStrategy.setMode(
                when {
                    cameras.isNotEmpty() -> LocationStrategy.Mode.PRECISE
                    isDriving            -> LocationStrategy.Mode.ACTIVE
                    else                 -> LocationStrategy.Mode.PASSIVE
                }
            )

            alertEngine.process(state, cameras)

            // update UI state
            ServiceState.speedKmh.value = state.speedKmh
            ServiceState.camerasNearby.value = cameras.size
            ServiceState.gpsMode.value = when (locationStrategy.currentMode) {
                LocationStrategy.Mode.PASSIVE -> getString(R.string.gps_passive)
                LocationStrategy.Mode.ACTIVE  -> getString(R.string.gps_active)
                LocationStrategy.Mode.PRECISE -> getString(R.string.gps_precise)
            }
        }
    }

    private fun onDrivingStart() {
        drivingStopJob?.cancel()
        isDriving = true
        // if no passive fix recently, switch to active
        if (System.currentTimeMillis() - lastFixMs > 30_000) {
            locationStrategy.setMode(LocationStrategy.Mode.ACTIVE)
        }
        updateNotification("Monitoring")
    }

    private fun onDrivingStop() {
        drivingStopJob?.cancel()
        drivingStopJob = scope.launch {
            delay(3 * 60 * 1000L) // 3 minute grace period before going passive
            isDriving = false
            locationStrategy.setMode(LocationStrategy.Mode.PASSIVE)
            alertEngine.reset()
            ServiceState.gpsMode.value = getString(R.string.gps_passive)
            updateNotification("Standby")
        }
    }

    // Monitor for passive fix drought; escalate to active if driving with no fix
    private fun startPassiveFallbackMonitor() {
        passiveFallbackJob?.cancel()
        passiveFallbackJob = scope.launch {
            while (isActive) {
                delay(30_000)
                if (isDriving && locationStrategy.currentMode == LocationStrategy.Mode.PASSIVE) {
                    val drought = System.currentTimeMillis() - lastFixMs
                    if (drought > 30_000) {
                        locationStrategy.setMode(LocationStrategy.Mode.ACTIVE)
                        ServiceState.gpsMode.value = getString(R.string.gps_active)
                    }
                }
            }
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

    // ---- Activity Transitions ----

    @SuppressLint("MissingPermission")
    private fun requestActivityTransitions() {
        if (!hasActivityRecognitionPermission()) {
            fallBackToActiveLocation()
            return
        }
        val transitions = listOf(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.ON_BICYCLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build()
        )
        activityClient.requestActivityTransitionUpdates(
            ActivityTransitionRequest(transitions),
            getActivityPendingIntent()
        ).addOnFailureListener {
            fallBackToActiveLocation()
        }
    }

    private fun removeActivityTransitions() {
        if (hasActivityRecognitionPermission()) {
            activityClient.removeActivityTransitionUpdates(getActivityPendingIntent())
        }
    }

    private fun hasActivityRecognitionPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun fallBackToActiveLocation() {
        isDriving = true
        locationStrategy.setMode(LocationStrategy.Mode.ACTIVE)
        ServiceState.gpsMode.value = getString(R.string.gps_active)
    }

    private fun getActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, com.radarlite.receiver.ActivityReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getBroadcast(this, 0, intent, flags)
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

    private fun updateNotification(status: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(status))
    }

    // Trigger DB update check (called from MainActivity on demand)
    fun checkDatabaseUpdate() {
        scope.launch {
            val result = DatabaseUpdater.checkAndUpdate(applicationContext, cameraDb)
            if (result == DatabaseUpdater.Result.UPDATED) {
                ServiceState.dbVersion.value     = cameraDb.getVersion() ?: "unknown"
                ServiceState.dbCameraCount.value = cameraDb.getCameraCount()
                ServiceState.lastDbCheckMs.value = System.currentTimeMillis()
            }
        }
    }
}
