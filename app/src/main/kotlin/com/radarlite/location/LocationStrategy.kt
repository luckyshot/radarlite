package com.radarlite.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.*

class LocationStrategy(
    context: Context,
    private val onLocation: (LocationState) -> Unit
) {
    enum class Mode { PASSIVE, ACTIVE, PRECISE }

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private var active = false

    var currentMode: Mode = Mode.PASSIVE
        private set

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                onLocation(
                    LocationState(
                        lat = loc.latitude,
                        lon = loc.longitude,
                        speedKmh = (loc.speed * 3.6f).coerceAtLeast(0f),
                        bearingDeg = loc.bearing,
                        accuracyM = loc.accuracy
                    )
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        active = true
        applyMode(Mode.PASSIVE)
    }

    @SuppressLint("MissingPermission")
    fun setMode(mode: Mode) {
        if (!active || mode == currentMode) return
        currentMode = mode
        fusedClient.removeLocationUpdates(callback)
        fusedClient.requestLocationUpdates(buildRequest(mode), callback, Looper.getMainLooper())
    }

    fun stop() {
        active = false
        fusedClient.removeLocationUpdates(callback)
    }

    @SuppressLint("MissingPermission")
    private fun applyMode(mode: Mode) {
        currentMode = mode
        fusedClient.requestLocationUpdates(buildRequest(mode), callback, Looper.getMainLooper())
    }

    private fun buildRequest(mode: Mode): LocationRequest = when (mode) {
        Mode.PASSIVE -> LocationRequest.Builder(Priority.PRIORITY_PASSIVE, Long.MAX_VALUE)
            .setMinUpdateIntervalMillis(5_000)
            .setMinUpdateDistanceMeters(30f)
            .build()
        Mode.ACTIVE -> LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000)
            .setMinUpdateIntervalMillis(5_000)
            .setMinUpdateDistanceMeters(30f)
            .build()
        Mode.PRECISE -> LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000)
            .setMinUpdateIntervalMillis(1_000)
            .setMinUpdateDistanceMeters(10f)
            .build()
    }
}
