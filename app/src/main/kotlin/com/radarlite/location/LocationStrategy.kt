package com.radarlite.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*

class LocationStrategy(
    context: Context,
    private val onLocation: (LocationState) -> Unit
) {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

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

    @SuppressLint("MissingPermission")
    fun start() {
        fusedClient.requestLocationUpdates(buildRequest(), callback, Looper.getMainLooper())
            // If navigation was already active, use the externally produced fix immediately.
            .addOnSuccessListener { emitCachedLastLocation() }
    }

    fun stop() {
        fusedClient.removeLocationUpdates(callback)
    }

    private fun buildRequest(): LocationRequest =
        LocationRequest.Builder(Priority.PRIORITY_PASSIVE, Long.MAX_VALUE)
            // Passive keeps GPS owned by other apps. Avoid a distance gate: the first background
            // fix can reuse the same coordinates and still proves another app is driving GPS.
            .setMinUpdateIntervalMillis(1_000)
            .setMaxUpdateDelayMillis(0)
            .build()

    @SuppressLint("MissingPermission")
    private fun emitCachedLastLocation() {
        fusedClient.lastLocation.addOnSuccessListener { it?.let(::emitLocation) }
    }

    private fun emitLocation(loc: Location) {
        onLocation(LocationState(
            lat = loc.latitude,
            lon = loc.longitude,
            speedKmh = (loc.speed * 3.6f).coerceAtLeast(0f),
            bearingDeg = loc.bearing,
            accuracyM = loc.accuracy,
            timeMs = loc.time
        ))
    }
}
