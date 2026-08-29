package com.radarlite

import android.content.Context

// One home for settings shared by the activity and the monitoring service.
object SpeedAnnouncements {
    // These are the only speeds the UI and service can select or announce.
    val speeds = intArrayOf(30, 50, 60, 80, 90, 100, 110, 120, 130)
    // Wait beyond a speed so small GPS fluctuations do not trigger its announcement.
    const val CONFIRMATION_BUFFER_KMH = 5
    private const val PREFS = "radarlite_prefs"
    private const val KEY_SPEEDS = "speed_announcement_speeds"

    fun selected(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getStringSet(KEY_SPEEDS, emptySet()).orEmpty().mapNotNull(String::toIntOrNull)
        .filter { it in speeds }.toSet()

    fun setSelected(context: Context, speed: Int, enabled: Boolean) {
        val selectedSpeeds = selected(context).toMutableSet()
        if (enabled) selectedSpeeds += speed else selectedSpeeds -= speed
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_SPEEDS, selectedSpeeds.map(Int::toString).toSet()).apply()
    }
}

// Keeps the speed-crossing rules independent from Android so they stay easy to test.
class SpeedAnnouncementTracker {
    private var previousSpeed: Int? = null
    private var selectedSpeeds = emptySet<Int>()
    private val announcedSpeeds = mutableSetOf<Int>()

    fun next(speedKmh: Float, selected: Set<Int>): Int? {
        val speed = speedKmh.toInt()
        if (selected != selectedSpeeds) {
            // A changed selection starts from the current speed; it never speaks retroactively.
            selectedSpeeds = selected
            announcedSpeeds.clear()
            announcedSpeeds += selected.filter { it <= speed }
            previousSpeed = speed
            return null
        }

        val previous = previousSpeed
        previousSpeed = speed
        if (previous == null) {
            announcedSpeeds += selected.filter { it <= speed }
            return null
        }
        if (speed <= previous) {
            if (speed < previous) announcedSpeeds.removeAll { it > speed }
            return null
        }

        val crossed = selected.filter {
            speed >= it + SpeedAnnouncements.CONFIRMATION_BUFFER_KMH && it !in announcedSpeeds
        }
        announcedSpeeds += crossed
        // Passive updates can skip several selections. One useful current-speed cue beats a list.
        return crossed.maxOrNull()
    }

    fun reset() {
        previousSpeed = null
        selectedSpeeds = emptySet()
        announcedSpeeds.clear()
    }
}
