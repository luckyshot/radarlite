package com.radarlite

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.radarlite.alert.AlertStage
import com.radarlite.alert.SoundManager
import com.radarlite.databinding.ActivityMainBinding
import com.radarlite.db.AppDatabase
import com.radarlite.db.CameraDbHelper
import com.radarlite.service.CameraDetectionService
import com.radarlite.ui.AlertLogAdapter
import com.radarlite.update.DatabaseUpdater
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AlertLogAdapter
    private lateinit var soundManager: SoundManager
    private val prefs by lazy { getSharedPreferences("radarlite_prefs", MODE_PRIVATE) }

    // Permission launchers (must be declared before onCreate)
    private val requestFineLocation = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            requestBackgroundLocation()
        } else {
            showToast("Location permission required")
            binding.switchService.isChecked = false
        }
    }

    private val requestBgLocation = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) requestPostNotifications()
        else {
            showToast("Background location required — select \"Allow all the time\" in Settings")
            binding.switchService.isChecked = false
        }
    }

    private val openAppSettings = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (!binding.switchService.isChecked) return@registerForActivityResult
        if (hasBgLocation()) requestPostNotifications()
        else {
            showToast("Background location required")
            binding.switchService.isChecked = false
        }
    }

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        startService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        soundManager = SoundManager(this)

        setupRecyclerView()
        setupSwitch()
        setupUpdateButton()
        setupSoundButtons()
        observeServiceState()
        observeAlertLog()
        showDisclaimerIfFirst { promptForInitialDatabaseIfMissing() }
    }

    override fun onDestroy() {
        soundManager.release()
        super.onDestroy()
    }

    // ---- Setup ----

    private fun setupRecyclerView() {
        adapter = AlertLogAdapter()
        binding.rvAlerts.layoutManager = LinearLayoutManager(this)
        binding.rvAlerts.adapter = adapter
    }

    private fun setupSwitch() {
        binding.switchService.isChecked = prefs.getBoolean("service_enabled", false)
        binding.switchService.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("service_enabled", checked).apply()
            if (checked) checkPermissionsAndStart() else stopService()
        }
    }

    private fun setupUpdateButton() {
        binding.btnCheckUpdate.setOnClickListener {
            lifecycleScope.launch {
                runDatabaseUpdate(requireWifi = true)
            }
        }
    }

    private fun setupSoundButtons() {
        binding.btnTestWarning.setOnClickListener { soundManager.play(AlertStage.WARNING, speedLimit = 50) }
        binding.btnTestUrgent.setOnClickListener { soundManager.play(AlertStage.URGENT) }
    }

    private suspend fun runDatabaseUpdate(requireWifi: Boolean): DatabaseUpdater.Result {
        binding.btnCheckUpdate.isEnabled = false
        binding.btnCheckUpdate.text = getString(R.string.updating_db)

        val dbHelper = CameraDbHelper(applicationContext).also { it.open() }
        val result = try {
            DatabaseUpdater.checkAndUpdate(applicationContext, dbHelper, requireWifi)
        } finally {
            dbHelper.close()
        }
        val msg = when (result) {
            DatabaseUpdater.Result.UPDATED      -> getString(R.string.db_updated)
            DatabaseUpdater.Result.UP_TO_DATE   -> getString(R.string.db_up_to_date)
            DatabaseUpdater.Result.NOT_ON_WIFI  -> getString(R.string.not_on_wifi)
            DatabaseUpdater.Result.FAILED       -> getString(R.string.db_update_failed)
        }
        showToast(msg)
        binding.btnCheckUpdate.text = getString(R.string.btn_check_update)
        binding.btnCheckUpdate.isEnabled = true
        if (result == DatabaseUpdater.Result.UPDATED ||
            result == DatabaseUpdater.Result.UP_TO_DATE) {
            ServiceState.lastDbCheckMs.value = System.currentTimeMillis()
        }
        refreshDatabaseState()
        return result
    }

    private fun promptForInitialDatabaseIfMissing() {
        val missingDatabase = CameraDbHelper(applicationContext).also { it.open() }.let { dbHelper ->
            try {
                dbHelper.getCameraCount() == 0
            } finally {
                dbHelper.close()
            }
        }
        if (!missingDatabase) {
            refreshDatabaseState()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.db_missing_title))
            .setMessage(getString(R.string.db_missing_message))
            .setPositiveButton(getString(R.string.db_missing_download)) { _, _ ->
                lifecycleScope.launch { runDatabaseUpdate(requireWifi = false) }
            }
            .setNegativeButton(getString(R.string.db_missing_later), null)
            .show()
    }

    private fun refreshDatabaseState() {
        val dbHelper = CameraDbHelper(applicationContext).also { it.open() }
        try {
            ServiceState.dbVersion.value = dbHelper.getVersion() ?: getString(R.string.no_database)
            ServiceState.dbCameraCount.value = dbHelper.getCameraCount()
        } finally {
            dbHelper.close()
        }
    }

    // ---- Observe state ----

    private fun observeServiceState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    ServiceState.isRunning.collect { running ->
                        binding.tvServiceStatus.text = if (running)
                            getString(R.string.status_monitoring)
                        else getString(R.string.status_stopped)
                        binding.tvServiceStatus.setTextColor(
                            ContextCompat.getColor(this@MainActivity,
                                if (running) R.color.status_active else R.color.text_primary)
                        )
                    }
                }
                launch {
                    ServiceState.speedKmh.collect { speed ->
                        binding.tvSpeed.text = if (speed > 0) "${speed.toInt()} km/h" else "—"
                    }
                }
                launch {
                    ServiceState.camerasNearby.collect { count ->
                        binding.tvCamerasNear.text = count.toString()
                    }
                }
                launch {
                    ServiceState.closestCameraDistanceM.collect { distance ->
                        binding.tvClosestCamera.text = formatDistance(distance)
                    }
                }
                launch {
                    ServiceState.gpsMode.collect { mode ->
                        binding.tvGpsMode.text = mode
                    }
                }
                launch {
                    ServiceState.dbVersion.collect { ver ->
                        binding.tvDbVersion.text = ver
                    }
                }
                launch {
                    ServiceState.dbCameraCount.collect { count ->
                        binding.tvDbCount.text = if (count > 0)
                            "%,d".format(count)
                        else "No database"
                    }
                }
                launch {
                    ServiceState.lastDbCheckMs.collect { ms ->
                        binding.tvLastCheck.text = if (ms > 0)
                            SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(ms))
                        else "Never"
                    }
                }
            }
        }
    }

    private fun observeAlertLog() {
        AppDatabase.get(this).alertLogDao().getRecent().observe(this) { entries ->
            adapter.submit(entries)
            binding.tvNoAlerts.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            binding.rvAlerts.visibility   = if (entries.isEmpty()) View.GONE   else View.VISIBLE
        }
    }

    // ---- Service control ----

    private fun checkPermissionsAndStart() {
        when {
            !hasFineLocation() -> requestFineLocation.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
            !hasBgLocation()   -> requestBackgroundLocation()
            !hasPostNotifications() -> requestPostNotifications()
            else               -> startService()
        }
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { requestPostNotifications(); return }
        if (hasBgLocation()) { requestPostNotifications(); return }
        AlertDialog.Builder(this)
            .setTitle("Background location needed")
            .setMessage(getString(R.string.perm_rationale_background))
            .setPositiveButton("Open Settings") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    openAppSettings.launch(appLocationSettingsIntent())
                } else {
                    requestBgLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
            .setNegativeButton("Cancel") { _, _ -> binding.switchService.isChecked = false }
            .show()
    }

    private fun appLocationSettingsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            .putExtra(":settings:fragment_args_key", "permissions_location")

    private fun requestPostNotifications() {
        if (hasPostNotifications()) { startService(); return }
        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun startService() {
        val si = Intent(this, CameraDetectionService::class.java).apply {
            action = CameraDetectionService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(si)
        else startService(si)
    }

    private fun stopService() {
        startService(Intent(this, CameraDetectionService::class.java).apply {
            action = CameraDetectionService.ACTION_STOP
        })
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
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.disclaimer_title))
            .setMessage(getString(R.string.disclaimer_message))
            .setPositiveButton(getString(R.string.disclaimer_ok)) { _, _ ->
                prefs.edit().putBoolean("disclaimer_shown", true).apply()
                onDone()
            }
            .setCancelable(false)
            .show()
    }

    private fun formatDistance(distanceM: Float?): String {
        if (distanceM == null) return "—"
        return if (distanceM < 1000f) "${distanceM.toInt()} m" else "%.1f km".format(distanceM / 1000f)
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
