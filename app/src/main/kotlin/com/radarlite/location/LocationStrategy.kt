package com.radarlite.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*

class LocationStrategy(
    context: Context,
    private val onLocation: (LocationState) -> Unit
) {
    private companion object {
        const val TAG = "LocationStrategy"
        // Self-powered GPS interval: frequent enough to catch a 600 m-range camera alert with
        // margin at motorway speed (~55 m/s * 3s ~= 165 m per fix), infrequent enough to keep
        // battery drain well below a full-time navigation app.
        const val ACTIVE_INTERVAL_MS = 3_000L
        const val ACTIVE_MIN_INTERVAL_MS = 2_000L
    }

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private var active = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::emitLocation)
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            // Fused can report GPS availability before sending a passive result when another app
            // starts navigation in the background. Reading the latest cache does not start GPS.
            if (availability.isLocationAvailable) emitCachedLastLocation()
        }
    }

    fun start() {
        active = false
        requestUpdates()
    }

    /** Self-powered mode: RadarLite drives GPS itself instead of waiting on another app. */
    fun startActive() {
        active = true
        requestUpdates()
    }

    fun stop() {
        fusedClient.removeLocationUpdates(callback)
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates() {
        fusedClient.removeLocationUpdates(callback)
        try {
            fusedClient.requestLocationUpdates(buildRequest(), callback, Looper.getMainLooper())
                // If navigation was already active, use the externally produced fix immediately.
                .addOnSuccessListener { emitCachedLastLocation() }
                .addOnFailureListener { Log.w(TAG, "Location request failed", it) }
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission missing", e)
        }
    }

    private fun buildRequest(): LocationRequest =
        if (active) {
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, ACTIVE_INTERVAL_MS)
                .setMinUpdateIntervalMillis(ACTIVE_MIN_INTERVAL_MS)
                .build()
        } else {
            LocationRequest.Builder(Priority.PRIORITY_PASSIVE, Long.MAX_VALUE)
                // Passive keeps GPS owned by other apps. Avoid a distance gate: the first
                // background fix can reuse the same coordinates and still proves another app is
                // driving GPS.
                .setMinUpdateIntervalMillis(1_000)
                .setMaxUpdateDelayMillis(0)
                .build()
        }

    @SuppressLint("MissingPermission")
    private fun emitCachedLastLocation() {
        try {
            fusedClient.lastLocation
                .addOnSuccessListener { it?.let(::emitLocation) }
                .addOnFailureListener { Log.w(TAG, "Cached passive location read failed", it) }
        } catch (e: SecurityException) {
            Log.w(TAG, "Cached passive location permission missing", e)
        }
    }

    private fun emitLocation(loc: Location) {
        onLocation(LocationState(
            lat = loc.latitude,
            lon = loc.longitude,
            speedKmh = (loc.speed * 3.6f).coerceAtLeast(0f),
            bearingDeg = loc.takeIf { it.hasBearing() }?.bearing,
            accuracyM = loc.accuracy,
            timeMs = loc.time
        ))
    }
}
