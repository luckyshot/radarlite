package com.radarlite.util

import kotlin.math.*

object GeoUtils {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return (EARTH_RADIUS_M * 2 * asin(sqrt(a))).toFloat()
    }

    fun bearingBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1R = Math.toRadians(lat1)
        val lat2R = Math.toRadians(lat2)
        val y = sin(dLon) * cos(lat2R)
        val x = cos(lat1R) * sin(lat2R) - sin(lat1R) * cos(lat2R) * cos(dLon)
        return ((Math.toDegrees(atan2(y, x)).toFloat() + 360f) % 360f)
    }

    fun angularDifference(a: Float, b: Float): Float {
        val diff = abs(a - b) % 360f
        return if (diff > 180f) 360f - diff else diff
    }

    fun lateralOffset(distanceM: Float, headingDeg: Float, bearingToTargetDeg: Float): Float {
        val diff = Math.toRadians(angularDifference(headingDeg, bearingToTargetDeg).toDouble())
        return abs(distanceM * sin(diff)).toFloat()
    }

    // Returns [minLat, maxLat, minLon, maxLon]
    fun boundingBox(lat: Double, lon: Double, radiusM: Float): DoubleArray {
        val latDelta = radiusM / 111_000.0
        val lonDelta = radiusM / (111_000.0 * cos(Math.toRadians(lat)))
        return doubleArrayOf(lat - latDelta, lat + latDelta, lon - lonDelta, lon + lonDelta)
    }
}
