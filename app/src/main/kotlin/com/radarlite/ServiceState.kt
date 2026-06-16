package com.radarlite

import kotlinx.coroutines.flow.MutableStateFlow

// Shared state between CameraDetectionService and MainActivity.
// All updates happen on the service side; MainActivity observes.
object ServiceState {
    val isRunning              = MutableStateFlow(false)
    val isReceivingLocation    = MutableStateFlow(false)
    val lastLat                = MutableStateFlow<Double?>(null)
    val lastLon                = MutableStateFlow<Double?>(null)
    val bearingDeg             = MutableStateFlow<Float?>(null)
    val accuracyM              = MutableStateFlow<Float?>(null)
    val lastFixMs              = MutableStateFlow<Long?>(null)
    val speedKmh               = MutableStateFlow(0f)
    val camerasNearby          = MutableStateFlow(0)
    val closestCameraDistanceM = MutableStateFlow<Float?>(null)
    val gpsMode                = MutableStateFlow("—")
    val dbVersion              = MutableStateFlow("—")
    val dbCameraCount          = MutableStateFlow(0)
    val lastDbCheckMs          = MutableStateFlow(0L)
}
