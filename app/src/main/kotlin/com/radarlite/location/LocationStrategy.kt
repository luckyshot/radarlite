package com.radarlite.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.*

class LocationStrategy(
    context: Context,
    private val onLocation: (LocationState) -> Unit
) {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

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
        fusedClient.requestLocationUpdates(buildRequest(), callback, Looper.getMainLooper())
    }

    fun stop() {
        fusedClient.removeLocationUpdates(callback)
    }

    private fun buildRequest(): LocationRequest =
        LocationRequest.Builder(Priority.PRIORITY_PASSIVE, Long.MAX_VALUE)
            // Passive keeps GPS owned by other apps; these gates only accept fresh navigation fixes sooner.
            .setMinUpdateIntervalMillis(1_000)
            .setMinUpdateDistanceMeters(10f)
            .build()
}
