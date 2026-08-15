package com.radarlite

import android.content.Context

// One home for settings shared by the activity and the monitoring service.
object SpeedAnnouncements {
    const val MIN_SPEED_KMH = 20f
    private const val PREFS = "radarlite_prefs"
    private const val KEY_INTERVAL = "speed_announcement_interval"

    fun interval(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getInt(KEY_INTERVAL, 0)

    fun setInterval(context: Context, interval: Int) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putInt(KEY_INTERVAL, interval).apply()
}
