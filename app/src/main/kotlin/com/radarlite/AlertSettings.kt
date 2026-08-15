package com.radarlite

import android.content.Context

// One small preference store keeps every alert category consistent in the UI and service.
object AlertSettings {
    private const val PREFS = "radarlite_prefs"
    const val SPEED = "speed"
    const val RED_LIGHT = "red_light"
    const val AVERAGE_SPEED = "average_speed"
    const val SHARP_CURVE = "sharp_curve"
    const val DANGEROUS_JUNCTION = "dangerous_junction"
    const val LEVEL_CROSSING = "level_crossing"
    const val TRAFFIC_CALMING = "traffic_calming"
    private const val OVERSPEED = "overspeed"

    fun enabled(context: Context, type: String) = context.prefs().getBoolean(type, true)
    fun setEnabled(context: Context, type: String, enabled: Boolean) =
        context.prefs().edit().putBoolean(type, enabled).apply()

    fun overspeedEnabled(context: Context) = context.prefs().getBoolean(OVERSPEED, true)
    fun setOverspeedEnabled(context: Context, enabled: Boolean) =
        context.prefs().edit().putBoolean(OVERSPEED, enabled).apply()

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
