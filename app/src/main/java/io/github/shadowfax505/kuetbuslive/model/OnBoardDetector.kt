package io.github.shadowfax505.kuetbuslive.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Is the rider on this bus? The same rule as the iOS app: for at least a minute the
// phone stayed within 30 m of the bus while both moved at about the same speed. The
// bus's position is a few seconds old, so it is first moved ahead along its heading.
// Short stops (signals, stops) don't reset the minute; moving away from the bus does.
class OnBoardDetector {
    var sinceMillis: Long? = null
        private set
    var isOnBoard = false
        private set

    fun update(rider: LatLng, riderSpeed: Double?, bus: Bus, nowMillis: Long): Boolean {
        val busNow = projected(bus.position, bus.heading, bus.speed, bus.updatedAtMillis, nowMillis)
        if (Geo.distanceMetres(rider, busNow) > RADIUS) {
            sinceMillis = null
            isOnBoard = false
            return false
        }
        val busSpeed = bus.speed ?: 0.0
        val mySpeed = riderSpeed ?: 0.0
        if (busSpeed >= MOVING_SPEED && mySpeed >= MOVING_SPEED && abs(busSpeed - mySpeed) <= SPEED_TOLERANCE) {
            if (sinceMillis == null) sinceMillis = nowMillis
        }
        val since = sinceMillis
        if (since != null && nowMillis - since >= DURATION_MILLIS) isOnBoard = true
        return isOnBoard
    }

    companion object {
        const val RADIUS = 30.0
        const val DURATION_MILLIS = 60_000L
        const val MOVING_SPEED = 2.0
        const val SPEED_TOLERANCE = 2.5
        const val MAX_PROJECTION_MILLIS = 10_000L

        // Where the bus probably is now: its last position moved ahead by speed x age.
        fun projected(bus: LatLng, heading: Double?, speed: Double?, updatedAtMillis: Long, nowMillis: Long): LatLng {
            if (heading == null || speed == null || speed <= 0) return bus
            val age = (nowMillis - updatedAtMillis).coerceIn(0, MAX_PROJECTION_MILLIS) / 1000.0
            val metres = speed * age
            val radians = Math.toRadians(heading)
            val north = metres * cos(radians)
            val east = metres * sin(radians)
            return LatLng(bus.lat + north / 111_320, bus.lng + east / (111_320 * cos(Math.toRadians(bus.lat))))
        }
    }
}
