package com.radarlite.ui

import com.radarlite.db.AlertLogEntry

data class MainUiState(
    val serviceEnabled: Boolean = false,
    val isRunning: Boolean = false,
    val isReceivingLocation: Boolean = false,
    val speedKmh: Float = 0f,
    val closestCameraDistanceM: Float? = null,
    val camerasNearby: Int = 0,
    val heading: String = "—",
    val gpsMode: String = "—",
    val accuracyM: Float? = null,
    val lastLat: Double? = null,
    val lastLon: Double? = null,
    val lastFixMs: Long? = null,
    val country1Code: String = "",
    val country2Code: String = "",
    val dbVersion: String = "—",
    val dbCameraCount: Int = 0,
    val lastDbCheckMs: Long = 0L,
    val updatingDb: Boolean = false,
    val alertToggles: Map<String, Boolean> = emptyMap(),
    val overspeedEnabled: Boolean = true,
    val selectedSpeeds: Set<Int> = emptySet(),
    val recentAlerts: List<AlertLogEntry> = emptyList(),
)

data class AlertTypeInfo(
    val key: String,
    val label: String,
    val emoji: String,
)

val ALERT_TYPES = listOf(
    AlertTypeInfo("speed", "Speed cameras", "📷"),
    AlertTypeInfo("red_light", "Red lights", "🚦"),
    AlertTypeInfo("average_speed", "Average speed zones", "⏱️"),
    AlertTypeInfo("sharp_curve", "Sharp curves", "➡️"),
    AlertTypeInfo("dangerous_junction", "Dangerous junctions", "✖️"),
    AlertTypeInfo("level_crossing", "Level crossings", "🚂"),
    AlertTypeInfo("traffic_calming", "Traffic calming", "🚧"),
)
