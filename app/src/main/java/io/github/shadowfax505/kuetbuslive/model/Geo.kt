package io.github.shadowfax505.kuetbuslive.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

// Small map maths, free of Android and Firebase so it can be unit tested.
object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val METRES_PER_DEGREE_LAT = 111_320.0

    fun distanceMetres(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    // Compass bearing from a to b, 0..360 degrees, 0 = north.
    fun bearing(a: LatLng, b: LatLng): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    // Shortest turn from one heading to another, in -180..180 degrees,
    // so a bus turning from 350° to 10° turns 20° right instead of 340° left.
    fun angleDelta(from: Double, to: Double): Double {
        var d = (to - from) % 360
        if (d > 180) d -= 360
        if (d < -180) d += 360
        return d
    }

    fun lerp(a: LatLng, b: LatLng, t: Double): LatLng =
        LatLng(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)

    fun lerpAngle(from: Double, to: Double, t: Double): Double =
        (from + angleDelta(from, to) * t + 360) % 360

    data class Snap(val point: LatLng, val distanceFromRoute: Double, val distanceAlong: Double)

    // Closest spot on a polyline to a place: where the bus really passes.
    fun snap(place: LatLng, path: List<LatLng>): Snap? {
        if (path.size < 2) return null
        var best: Snap? = null
        var travelled = 0.0
        for (i in 0 until path.size - 1) {
            val a = path[i]
            val b = path[i + 1]
            val mLng = METRES_PER_DEGREE_LAT * cos(Math.toRadians(a.lat))
            val bx = (b.lng - a.lng) * mLng
            val by = (b.lat - a.lat) * METRES_PER_DEGREE_LAT
            val px = (place.lng - a.lng) * mLng
            val py = (place.lat - a.lat) * METRES_PER_DEGREE_LAT
            val lengthSq = bx * bx + by * by
            val length = sqrt(lengthSq)
            val t = if (lengthSq > 0) ((px * bx + py * by) / lengthSq).coerceIn(0.0, 1.0) else 0.0
            val gap = hypot(px - t * bx, py - t * by)
            if (best == null || gap < best.distanceFromRoute) {
                best = Snap(lerp(a, b, t), gap, travelled + t * length)
            }
            travelled += length
        }
        return best
    }

    // How far ahead of the bus a rider waits, along the route. Negative once passed.
    fun metresAhead(bus: LatLng, rider: LatLng, path: List<LatLng>): Double? {
        val busAlong = snap(bus, path)?.distanceAlong ?: return null
        val riderAlong = snap(rider, path)?.distanceAlong ?: return null
        return riderAlong - busAlong
    }

    // Share of the trip done from the first stop to a target, 0..1, measured along the path.
    fun progress(bus: LatLng, target: LatLng, path: List<LatLng>): Double? {
        val busAlong = snap(bus, path)?.distanceAlong ?: return null
        val targetAlong = snap(target, path)?.distanceAlong ?: return null
        if (targetAlong <= 0) return null
        return (busAlong / targetAlong).coerceIn(0.0, 1.0)
    }

    // The road from the bus onwards: drops the part of the path the bus already passed.
    fun remainingPath(bus: LatLng, path: List<LatLng>): List<LatLng> {
        if (path.isEmpty()) return emptyList()
        val closest = path.indices.minBy { distanceMetres(bus, path[it]) }
        return listOf(bus) + path.drop(closest + 1)
    }
}
