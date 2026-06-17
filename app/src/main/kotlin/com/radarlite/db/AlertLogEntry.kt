package com.radarlite.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alert_log")
data class AlertLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val cameraId: Long?,
    val speedKmh: Float?,
    val speedLimit: Int?,
    val cameraType: String = "speed",
    val cameraLat: Double? = null,
    val cameraLon: Double? = null
)
