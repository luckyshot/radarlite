package com.radarlite.location

data class LocationState(
    val lat: Double,
    val lon: Double,
    val speedKmh: Float,
    val bearingDeg: Float,
    val accuracyM: Float
)
