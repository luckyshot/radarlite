package com.radarlite.alert

import com.radarlite.db.Camera
import com.radarlite.location.LocationState
import com.radarlite.util.GeoUtils

class AlertEngine(
    private val soundManager: SoundManager,
    private val isTypeEnabled: (String) -> Boolean,
    private val isOverspeedEnabled: () -> Boolean,
    private val onAlert: (Camera, Float) -> Unit
) {
    companion object {
        private const val MIN_ALERT_SPEED_KMH = 15f
        private const val OVERSPEED_TOLERANCE_KMH = 3f
        private val CAMERA_TYPES = setOf("speed", "red_light", "average_speed")
    }

    // Alert id -> highest stage already alerted this pass.
    private val alerted = mutableMapOf<Long, AlertStage>()
    // Alert id -> rolling distance buffer (last 4 readings).
    private val distHistory = mutableMapOf<Long, ArrayDeque<Float>>()
    private var lastBearingDeg: Float? = null
    private var turningFixes = 0

    fun process(state: LocationState, cameras: List<Camera>) {
        updateTurnState(state)
        // Ignore walking and other very slow movement; GPS heading and distance trends are too noisy here.
        val heading = state.bearingDeg ?: return
        if (state.speedKmh < MIN_ALERT_SPEED_KMH) return

        val enabledAlerts = cameras.filter { isTypeEnabled(it.type) }
        val activeIds = enabledAlerts.mapTo(mutableSetOf()) { it.id }
        alerted.keys.retainAll(activeIds)
        distHistory.keys.retainAll(activeIds)

        for (cam in enabledAlerts) {
            val dist = GeoUtils.haversine(state.lat, state.lon, cam.lat, cam.lon)

            val history = distHistory.getOrPut(cam.id) { ArrayDeque(4) }
            if (history.size >= 4) history.removeFirst()
            history.addLast(dist)

            if (!isApproaching(state, cam, history, heading)) continue

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
                    cameraType = if (target == AlertStage.WARNING) cam.type else null,
                    overspeed = target == AlertStage.WARNING && cam.type in CAMERA_TYPES &&
                        isOverspeedEnabled() &&
                        cam.speedLimit?.let { state.speedKmh > it + OVERSPEED_TOLERANCE_KMH } == true
                )
                // A closer urgent tone is the same encounter, not a second log entry.
                if (last == null) onAlert(cam, state.speedKmh)
            }
        }
    }

    private fun isApproaching(
        state: LocationState,
        cam: Camera,
        history: ArrayDeque<Float>,
        heading: Float
    ): Boolean {
        // need at least 2 readings to determine trend
        if (history.size < 2) return true

        val distDecreasing = history.last() < history.first()

        val bearingToCam = GeoUtils.bearingBetween(state.lat, state.lon, cam.lat, cam.lon)
        val headingDiff = GeoUtils.angularDifference(heading, bearingToCam)
        val onLikelyPath = isOnLikelyPath(
            distanceM = history.last(),
            headingDiff = headingDiff,
            speedKmh = state.speedKmh,
            heading = heading,
            camBearing = bearingToCam
        )

        // if camera has an explicit direction tag, use it as hard filter
        cam.direction?.let { dir ->
            val camDiff = GeoUtils.angularDifference(heading, dir.toFloat())
            return camDiff < 45f && distDecreasing && onLikelyPath
        }

        return headingDiff < 70f && distDecreasing && onLikelyPath
    }

    private fun updateTurnState(state: LocationState) {
        val heading = state.bearingDeg
        if (state.speedKmh < MIN_ALERT_SPEED_KMH || heading == null) {
            lastBearingDeg = null
            turningFixes = 0
            return
        }

        val last = lastBearingDeg
        if (last != null && GeoUtils.angularDifference(last, heading) > 25f) {
            turningFixes = 2
        } else if (turningFixes > 0) {
            turningFixes--
        }
        lastBearingDeg = heading
    }

    private fun isOnLikelyPath(
        distanceM: Float,
        headingDiff: Float,
        speedKmh: Float,
        heading: Float,
        camBearing: Float
    ): Boolean {
        if (headingDiff >= 90f) return false

        // Use a conservative side-offset check to reject obvious parallel/side-street cameras.
        // While turning, keep this loose so cameras after a bend are not discarded too early.
        val baseLimit = if (distanceM < 200f) 45f else 70f
        val lateralLimit = if (speedKmh >= 80f) 85f else baseLimit
        val limit = if (turningFixes > 0) lateralLimit + 40f else lateralLimit
        return GeoUtils.lateralOffset(distanceM, heading, camBearing) <= limit
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
