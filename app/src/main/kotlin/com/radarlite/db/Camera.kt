package com.radarlite.db

data class Camera(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val speedLimit: Int?,    // km/h, nullable
    val type: String,        // speed | red_light | average_speed
    val direction: Int?,     // degrees 0-359, nullable
    val sources: String?
)
