package com.radarlite

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import com.radarlite.alert.AlertStage
import com.radarlite.alert.SoundManager
import com.radarlite.db.AlertLogEntry
import com.radarlite.db.AppDatabase
import com.radarlite.db.CameraDbHelper
import com.radarlite.service.CameraDetectionService
import com.radarlite.ui.ALERT_TYPES
import com.radarlite.ui.BackgroundLocationDialog
import com.radarlite.ui.DatabaseMissingDialog
import com.radarlite.ui.DatabaseStaleDialog
import com.radarlite.ui.DisclaimerDialog
import com.radarlite.ui.MainScreen
import com.radarlite.ui.MainScreenActions
import com.radarlite.ui.MainUiState
import com.radarlite.ui.theme.RadarLiteTheme
import com.radarlite.update.DatabaseUpdater
import kotlinx.coroutines.launch

private enum class PendingDialog { NONE, DISCLAIMER, BG_LOCATION, DB_MISSING, DB_STALE }

class MainActivity : ComponentActivity() {

    private lateinit var soundManager: SoundManager
    private val prefs by lazy { getSharedPreferences("radarlite_prefs", MODE_PRIVATE) }

    private var serviceEnabled by mutableStateOf(false)
    private var country1Code by mutableStateOf(Countries.DEFAULT_CODE)
    private var country2Code by mutableStateOf(Countries.NONE_CODE)
    private var alertToggles by mutableStateOf<Map<String, Boolean>>(emptyMap())
    private var overspeedEnabled by mutableStateOf(true)
    private var selectedSpeeds by mutableStateOf<Set<Int>>(emptySet())
    private var updatingDb by mutableStateOf(false)
    private var pendingDialog by mutableStateOf(PendingDialog.NONE)
    private var pendingGrantedAction: () -> Unit = ::startService

    // Permission launchers (must be declared before onCreate)
    private val requestFineLocation = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            requestBackgroundLocation()
        } else {
            showToast("Location permission required")
            serviceEnabled = false
        }
    }

    private val requestBgLocation = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) requestPostNotifications()
        else {
            showToast(getString(R.string.perm_bg_required))
            serviceEnabled = false
        }
    }

    private val openAppSettings = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (!serviceEnabled) return@registerForActivityResult
        if (hasBgLocation()) requestPostNotifications()
        else {
            showToast(getString(R.string.perm_bg_required))
            serviceEnabled = false
        }
    }

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        pendingGrantedAction()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        soundManager = SoundManager(this)

        loadSettingsFromPrefs()

        setContent {
            RadarLiteTheme {
                val recentAlerts by AppDatabase.get(this).alertLogDao().getRecent()
                    .observeAsState(emptyList())

                val isRunning by ServiceState.isRunning.collectAsState()
                val isReceiving by ServiceState.isReceivingLocation.collectAsState()
                val speedKmh by ServiceState.speedKmh.collectAsState()
                val bearing by ServiceState.bearingDeg.collectAsState()
                val accuracy by ServiceState.accuracyM.collectAsState()
                val lastLat by ServiceState.lastLat.collectAsState()
                val lastLon by ServiceState.lastLon.collectAsState()
                val lastFixMs by ServiceState.lastFixMs.collectAsState()
                val camerasNearby by ServiceState.camerasNearby.collectAsState()
                val closestDistance by ServiceState.closestCameraDistanceM.collectAsState()
                val gpsMode by ServiceState.gpsMode.collectAsState()
                val dbVersion by ServiceState.dbVersion.collectAsState()
                val dbCameraCount by ServiceState.dbCameraCount.collectAsState()
                val lastDbCheckMs by ServiceState.lastDbCheckMs.collectAsState()
                val activeGpsDeadlineMs by ServiceState.activeGpsDeadlineMs.collectAsState()

                var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
                LaunchedEffect(activeGpsDeadlineMs) {
                    while (activeGpsDeadlineMs != null) {
                        nowMs = System.currentTimeMillis()
                        delay(1_000)
                    }
                }
                val activeGpsRemainingMs = activeGpsDeadlineMs?.let { (it - nowMs).coerceAtLeast(0) }

                val state = MainUiState(
                    serviceEnabled = serviceEnabled,
                    isRunning = isRunning,
                    isReceivingLocation = isReceiving,
                    speedKmh = speedKmh,
                    closestCameraDistanceM = closestDistance,
                    camerasNearby = camerasNearby,
                    heading = formatHeading(bearing),
                    gpsMode = gpsMode,
                    accuracyM = accuracy,
                    lastLat = lastLat,
                    lastLon = lastLon,
                    lastFixMs = lastFixMs,
                    country1Code = country1Code,
                    country2Code = country2Code,
                    dbVersion = dbVersion,
                    dbCameraCount = dbCameraCount,
                    lastDbCheckMs = lastDbCheckMs,
                    updatingDb = updatingDb,
                    alertToggles = alertToggles,
                    overspeedEnabled = overspeedEnabled,
                    selectedSpeeds = selectedSpeeds,
                    recentAlerts = recentAlerts,
                    activeGpsRemainingMs = activeGpsRemainingMs,
                )

                MainScreen(
                    state = state,
                    actions = MainScreenActions(
                        onToggleService = ::onToggleService,
                        onCountry1Selected = ::onCountry1Selected,
                        onCountry2Selected = ::onCountry2Selected,
                        onSpeedToggle = ::onSpeedToggle,
                        onAlertToggle = ::onAlertToggle,
                        onOverspeedToggle = ::onOverspeedToggle,
                        onCheckUpdate = { lifecycleScope.launch { runDatabaseUpdate() } },
                        onTestSound = { type, limit, overspeed ->
                            soundManager.play(AlertStage.WARNING, limit, type, overspeed)
                        },
                        onTestUrgent = { soundManager.play(AlertStage.URGENT) },
                        onLastFixClick = ::openLastFixInMaps,
                        onAlertEntryClick = ::openAlertInMaps,
                        onPrivacyClick = { openExternal(getString(R.string.url_privacy), R.string.no_browser) },
                        onSourceClick = { openExternal(getString(R.string.url_source), R.string.no_browser) },
                        onOsmClick = { openExternal(getString(R.string.url_osm), R.string.no_browser) },
                        onActivateGps = ::onActivateGps,
                        onDeactivateGps = ::onDeactivateGps,
                    ),
                )

                when (pendingDialog) {
                    PendingDialog.DISCLAIMER -> DisclaimerDialog(onAccept = ::onDisclaimerAccepted)
                    PendingDialog.BG_LOCATION -> BackgroundLocationDialog(
                        onOpenSettings = ::onBgLocationSettingsConfirmed,
                        onCancel = { pendingDialog = PendingDialog.NONE; serviceEnabled = false },
                    )
                    PendingDialog.DB_MISSING -> DatabaseMissingDialog(
                        onDownload = {
                            pendingDialog = PendingDialog.NONE
                            lifecycleScope.launch { runDatabaseUpdate() }
                        },
                        onLater = {
                            pendingDialog = PendingDialog.NONE
                            promptForStaleDatabaseIfNeeded()
                        },
                    )
                    PendingDialog.DB_STALE -> DatabaseStaleDialog(
                        onUpdate = {
                            pendingDialog = PendingDialog.NONE
                            lifecycleScope.launch { runDatabaseUpdate() }
                        },
                        onSkip = {
                            pendingDialog = PendingDialog.NONE
                            DatabaseUpdater.markStalePromptShown(this)
                        },
                    )
                    PendingDialog.NONE -> Unit
                }
            }
        }

        refreshDatabaseState()
        if (serviceEnabled) checkPermissionsAndStart()
        showDisclaimerIfFirst { promptForInitialDatabaseIfMissing() }
    }

    override fun onDestroy() {
        soundManager.release()
        super.onDestroy()
    }

    // ---- Settings load/save ----

    private fun loadSettingsFromPrefs() {
        serviceEnabled = prefs.getBoolean("service_enabled", false)
        country1Code = CountrySettings.country1(this)
        country2Code = CountrySettings.country2(this)
        alertToggles = ALERT_TYPES.associate { it.key to AlertSettings.enabled(this, it.key) }
        overspeedEnabled = AlertSettings.overspeedEnabled(this)
        selectedSpeeds = SpeedAnnouncements.selected(this)
    }

    private fun onToggleService(checked: Boolean) {
        serviceEnabled = checked
        prefs.edit().putBoolean("service_enabled", checked).apply()
        if (checked) checkPermissionsAndStart() else stopService()
    }

    private fun onCountry1Selected(code: String) {
        if (code == country1Code) return
        country1Code = code
        CountrySettings.setCountry1(this, code)
        onActiveCountriesChanged()
    }

    private fun onCountry2Selected(code: String) {
        if (code == country2Code) return
        country2Code = code
        CountrySettings.setCountry2(this, code)
        onActiveCountriesChanged()
    }

    private fun onActiveCountriesChanged() {
        refreshDatabaseState()
        if (ServiceState.isRunning.value) {
            CameraDetectionService.start(applicationContext, CameraDetectionService.ACTION_RELOAD_DB)
        }
    }

    private fun onSpeedToggle(speed: Int, enabled: Boolean) {
        SpeedAnnouncements.setSelected(this, speed, enabled)
        selectedSpeeds = SpeedAnnouncements.selected(this)
    }

    private fun onAlertToggle(type: String, enabled: Boolean) {
        AlertSettings.setEnabled(this, type, enabled)
        alertToggles = alertToggles + (type to enabled)
    }

    private fun onOverspeedToggle(enabled: Boolean) {
        AlertSettings.setOverspeedEnabled(this, enabled)
        overspeedEnabled = enabled
    }

    // ---- Database update ----

    private suspend fun runDatabaseUpdate(): DatabaseUpdater.Result {
        updatingDb = true
        val result = DatabaseUpdater.checkAndUpdate(applicationContext)
        val msg = when (result) {
            DatabaseUpdater.Result.UPDATED -> getString(R.string.db_updated)
            DatabaseUpdater.Result.UP_TO_DATE -> getString(R.string.db_up_to_date)
            DatabaseUpdater.Result.FAILED -> getString(R.string.db_update_failed)
        }
        showToast(msg)
        updatingDb = false
        if (result == DatabaseUpdater.Result.UPDATED || result == DatabaseUpdater.Result.UP_TO_DATE) {
            ServiceState.lastDbCheckMs.value = System.currentTimeMillis()
        }
        if (result == DatabaseUpdater.Result.UPDATED && ServiceState.isRunning.value) {
            CameraDetectionService.start(applicationContext, CameraDetectionService.ACTION_RELOAD_DB)
        }
        refreshDatabaseState()
        return result
    }

    private fun promptForInitialDatabaseIfMissing() {
        val codes = CountrySettings.selectedCodes(this)
        val missingDatabase = CameraDbHelper(applicationContext).also { it.open(codes) }.let { dbHelper ->
            try {
                dbHelper.getCameraCount() == 0
            } finally {
                dbHelper.close()
            }
        }
        if (!missingDatabase) {
            refreshDatabaseState()
            promptForStaleDatabaseIfNeeded()
            return
        }
        pendingDialog = PendingDialog.DB_MISSING
    }

    private fun promptForStaleDatabaseIfNeeded() {
        if (!DatabaseUpdater.shouldPromptForStale(this)) return
        pendingDialog = PendingDialog.DB_STALE
    }

    private fun refreshDatabaseState() {
        val codes = CountrySettings.selectedCodes(this)
        val dbHelper = CameraDbHelper(applicationContext).also { it.open(codes) }
        try {
            ServiceState.dbVersion.value = dbHelper.versionSummary(codes)
            ServiceState.dbCameraCount.value = dbHelper.getCameraCount()
            ServiceState.lastDbCheckMs.value = DatabaseUpdater.lastCheckMs(this)
        } finally {
            dbHelper.close()
        }
    }

    // ---- Service control ----

    private fun checkPermissionsAndStart(onGranted: () -> Unit = ::startService) {
        pendingGrantedAction = onGranted
        when {
            !hasFineLocation() -> requestFineLocation.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
            !hasBgLocation() -> requestBackgroundLocation()
            !hasPostNotifications() -> requestPostNotifications()
            else -> onGranted()
        }
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { requestPostNotifications(); return }
        if (hasBgLocation()) { requestPostNotifications(); return }
        pendingDialog = PendingDialog.BG_LOCATION
    }

    private fun onBgLocationSettingsConfirmed() {
        pendingDialog = PendingDialog.NONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            openAppSettings.launch(appLocationSettingsIntent())
        } else {
            requestBgLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private fun appLocationSettingsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            .putExtra(":settings:fragment_args_key", "permissions_location")

    private fun requestPostNotifications() {
        if (hasPostNotifications()) { pendingGrantedAction(); return }
        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun startService() {
        CameraDetectionService.start(this)
    }

    private fun stopService() {
        CameraDetectionService.start(this, CameraDetectionService.ACTION_STOP)
    }

    private fun onActivateGps(minutes: Int) {
        checkPermissionsAndStart {
            serviceEnabled = true
            prefs.edit().putBoolean("service_enabled", true).apply()
            CameraDetectionService.activateGps(this, minutes * 60_000L)
        }
    }

    private fun onDeactivateGps() {
        CameraDetectionService.deactivateGps(this)
    }

    // ---- Permission checks ----

    private fun hasFineLocation() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasBgLocation(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    // ---- One-time disclaimer ----

    private fun showDisclaimerIfFirst(onDone: () -> Unit) {
        if (prefs.getBoolean("disclaimer_shown", false)) {
            onDone()
            return
        }
        pendingDialogOnDone = onDone
        pendingDialog = PendingDialog.DISCLAIMER
    }

    private var pendingDialogOnDone: (() -> Unit)? = null

    private fun onDisclaimerAccepted() {
        prefs.edit().putBoolean("disclaimer_shown", true).apply()
        pendingDialog = PendingDialog.NONE
        pendingDialogOnDone?.invoke()
        pendingDialogOnDone = null
    }

    private fun formatHeading(bearing: Float?): String {
        if (bearing == null) return "—"
        val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val index = (((bearing + 22.5f) / 45f).toInt() % names.size)
        return "${names[index]} ${bearing.toInt()}°"
    }

    private fun openLastFixInMaps() {
        val lat = ServiceState.lastLat.value ?: return
        val lon = ServiceState.lastLon.value ?: return
        // geo with q drops a pin in the user's default map/navigation app.
        val uri = Uri.parse("geo:0,0?q=$lat,$lon(${Uri.encode("RadarLite last fix")})")
        openExternal(uri, R.string.no_map_app)
    }

    private fun openAlertInMaps(entry: AlertLogEntry) {
        val lat = entry.cameraLat ?: return
        val lon = entry.cameraLon ?: return
        val uri = Uri.parse("geo:0,0?q=$lat,$lon(${Uri.encode(alertMapLabel(entry))})")
        openExternal(uri, R.string.no_map_app)
    }

    private fun alertMapLabel(entry: AlertLogEntry): String {
        val type = when (entry.cameraType) {
            "red_light" -> "Red light"
            "average_speed" -> "Average speed zone"
            "sharp_curve" -> "Sharp curve"
            "dangerous_junction" -> "Dangerous junction"
            "level_crossing" -> "Level crossing"
            "traffic_calming" -> "Traffic calming"
            else -> "Speed limit"
        }
        return entry.speedLimit?.let { "$type $it" } ?: type
    }

    private fun openExternal(url: String, fallbackRes: Int) = openExternal(Uri.parse(url), fallbackRes)

    private fun openExternal(uri: Uri, fallbackRes: Int) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            showToast(getString(fallbackRes))
        }
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
