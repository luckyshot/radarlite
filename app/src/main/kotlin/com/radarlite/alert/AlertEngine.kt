package com.radarlite.alert

import com.radarlite.db.Camera
import com.radarlite.location.LocationState
import com.radarlite.util.GeoUtils

class AlertEngine(
    private val soundManager: SoundManager,
    private val onAlert: (Camera, AlertStage) -> Unit
) {
    companion object {
        private const val MIN_ALERT_SPEED_KMH = 15f
    }

    // camera id -> highest stage already alerted this pass
    private val alerted = mutableMapOf<Long, AlertStage>()
    // camera id -> rolling distance buffer (last 4 readings)
    private val distHistory = mutableMapOf<Long, ArrayDeque<Float>>()
    private var lastBearingDeg: Float? = null
    private var turningFixes = 0

    fun process(state: LocationState, cameras: List<Camera>) {
        updateTurnState(state)
        // Ignore walking and other very slow movement; GPS heading and distance trends are too noisy here.
        if (state.speedKmh < MIN_ALERT_SPEED_KMH) return

        val activeIds = cameras.mapTo(mutableSetOf()) { it.id }
        alerted.keys.retainAll(activeIds)
        distHistory.keys.retainAll(activeIds)

        for (cam in cameras) {
            val dist = GeoUtils.haversine(state.lat, state.lon, cam.lat, cam.lon)

            val history = distHistory.getOrPut(cam.id) { ArrayDeque(4) }
            if (history.size >= 4) history.removeFirst()
            history.addLast(dist)

            if (!isApproaching(state, cam, history)) continue

            val alertDist = alertDistance(state.speedKmh, cam.speedLimit)
            val urgentDist = urgentDistance(alertDist, state.speedKmh, cam.speedLimit)

            val target = when {
                dist <= urgentDist -> AlertStage.URGENT
                dist <= alertDist  -> AlertStage.WARNING
                else               -> continue
            }

            val last = alerted[cam.id]
            // only alert if new or escalating from WARNING -> URGENT
            if (last == null || (target == AlertStage.URGENT && last == AlertStage.WARNING)) {
                alerted[cam.id] = target
                soundManager.play(
                    target,
                    speedLimit = if (target == AlertStage.WARNING) cam.speedLimit else null,
                    cameraType = if (target == AlertStage.WARNING) cam.type else null
                )
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

        val bearingToCam = GeoUtils.bearingBetween(state.lat, state.lon, cam.lat, cam.lon)
        val headingDiff = GeoUtils.angularDifference(state.bearingDeg, bearingToCam)
        val onLikelyPath = isOnLikelyPath(
            distanceM = history.last(),
            headingDiff = headingDiff,
            state = state,
            camBearing = bearingToCam
        )

        // if camera has an explicit direction tag, use it as hard filter
        cam.direction?.let { dir ->
            val camDiff = GeoUtils.angularDifference(state.bearingDeg, dir.toFloat())
            return camDiff < 45f && distDecreasing && onLikelyPath
        }

        return headingDiff < 70f && distDecreasing && onLikelyPath
    }

    private fun updateTurnState(state: LocationState) {
        if (state.speedKmh < MIN_ALERT_SPEED_KMH) {
            lastBearingDeg = null
            turningFixes = 0
            return
        }

        val last = lastBearingDeg
        if (last != null && GeoUtils.angularDifference(last, state.bearingDeg) > 25f) {
            turningFixes = 2
        } else if (turningFixes > 0) {
            turningFixes--
        }
        lastBearingDeg = state.bearingDeg
    }

    private fun isOnLikelyPath(
        distanceM: Float,
        headingDiff: Float,
        state: LocationState,
        camBearing: Float
    ): Boolean {
        if (headingDiff >= 90f) return false

        // Use a conservative side-offset check to reject obvious parallel/side-street cameras.
        // While turning, keep this loose so cameras after a bend are not discarded too early.
        val baseLimit = if (distanceM < 200f) 45f else 70f
        val lateralLimit = if (state.speedKmh >= 80f) 85f else baseLimit
        val limit = if (turningFixes > 0) lateralLimit + 40f else lateralLimit
        return GeoUtils.lateralOffset(distanceM, state.bearingDeg, camBearing) <= limit
    }

    private fun alertDistance(speedKmh: Float, limitKmh: Int?): Float {
        val ref = limitKmh?.toFloat() ?: speedKmh
        return (ref * 3.5f).coerceIn(120f, 600f)
    }

    private fun urgentDistance(alertDistanceM: Float, speedKmh: Float, limitKmh: Int?): Float {
        val urban = (limitKmh ?: speedKmh.toInt()) <= 50
        return (alertDistanceM * 0.35f).coerceAtLeast(if (urban) 65f else 45f)
    }

    fun reset() {
        alerted.clear()
        distHistory.clear()
        lastBearingDeg = null
        turningFixes = 0
    }
}
