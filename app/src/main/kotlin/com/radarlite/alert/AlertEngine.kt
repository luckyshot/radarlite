package com.radarlite.alert

import com.radarlite.db.Camera
import com.radarlite.location.LocationState
import com.radarlite.util.GeoUtils

class AlertEngine(
    private val soundManager: SoundManager,
    private val onAlert: (Camera, AlertStage) -> Unit
) {
    // camera id -> highest stage already alerted this pass
    private val alerted = mutableMapOf<Long, AlertStage>()
    // camera id -> rolling distance buffer (last 4 readings)
    private val distHistory = mutableMapOf<Long, ArrayDeque<Float>>()

    fun process(state: LocationState, cameras: List<Camera>) {
        val activeIds = cameras.map { it.id }.toSet()

        // drop cameras no longer in query radius
        val gone = alerted.keys.filter { it !in activeIds }
        gone.forEach { alerted.remove(it); distHistory.remove(it) }

        for (cam in cameras) {
            val dist = GeoUtils.haversine(state.lat, state.lon, cam.lat, cam.lon)

            val history = distHistory.getOrPut(cam.id) { ArrayDeque(4) }
            if (history.size >= 4) history.removeFirst()
            history.addLast(dist)

            if (!isApproaching(state, cam, history)) continue

            val alertDist = alertDistance(state.speedKmh, cam.speedLimit)
            val urgentDist = alertDist * 0.35f

            val target = when {
                dist <= urgentDist -> AlertStage.URGENT
                dist <= alertDist  -> AlertStage.WARNING
                else               -> continue
            }

            val last = alerted[cam.id]
            // only alert if new or escalating from WARNING -> URGENT
            if (last == null || (target == AlertStage.URGENT && last == AlertStage.WARNING)) {
                alerted[cam.id] = target
                soundManager.play(target)
                onAlert(cam, target)
            }
        }
    }

    private fun isApproaching(
        state: LocationState,
        cam: Camera,
        history: ArrayDeque<Float>
    ): Boolean {
        // need at least 2 readings to determine trend
        if (history.size < 2) return true

        val distDecreasing = history.last() < history.first()

        // below 15 km/h GPS heading is unreliable, use distance only
        if (state.speedKmh < 15f) return distDecreasing

        val bearingToCam = GeoUtils.bearingBetween(state.lat, state.lon, cam.lat, cam.lon)
        val headingDiff = GeoUtils.angularDifference(state.bearingDeg, bearingToCam)

        // if camera has an explicit direction tag, use it as hard filter
        cam.direction?.let { dir ->
            val camDiff = GeoUtils.angularDifference(state.bearingDeg, dir.toFloat())
            return camDiff < 45f && distDecreasing
        }

        return headingDiff < 70f && distDecreasing
    }

    private fun alertDistance(speedKmh: Float, limitKmh: Int?): Float {
        val ref = limitKmh?.toFloat() ?: speedKmh
        return (ref * 3.5f).coerceIn(120f, 600f)
    }

    fun reset() {
        alerted.clear()
        distHistory.clear()
    }
}
