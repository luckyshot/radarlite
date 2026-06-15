package com.radarlite

import kotlinx.coroutines.flow.MutableStateFlow

// Shared state between CameraDetectionService and MainActivity.
// All updates happen on the service side; MainActivity observes.
object ServiceState {
    val isRunning              = MutableStateFlow(false)
    val speedKmh               = MutableStateFlow(0f)
    val camerasNearby          = MutableStateFlow(0)
    val closestCameraDistanceM = MutableStateFlow<Float?>(null)
    val gpsMode                = MutableStateFlow("—")
    val dbVersion              = MutableStateFlow("—")
    val dbCameraCount          = MutableStateFlow(0)
    val lastDbCheckMs          = MutableStateFlow(0L)
}
