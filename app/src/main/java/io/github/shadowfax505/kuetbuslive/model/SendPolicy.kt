package io.github.shadowfax505.kuetbuslive.model

// When a driver's phone sends the bus position. Every write is read once by every
// rider watching the map, so a write goes out only when it tells them something:
// the bus moved at least MIN_METRES (and at most every INTERVAL_MILLIS), or
// HEARTBEAT_MILLIS passed, so riders know a bus standing at a stop is still there.
// The iOS app uses the same numbers (DriverViewModel).
object SendPolicy {
    const val INTERVAL_MILLIS = 5_000L
    const val MIN_METRES = 15.0
    const val HEARTBEAT_MILLIS = 20_000L

    fun shouldSend(nowMillis: Long, lastSentMillis: Long, lastSentAt: LatLng?, position: LatLng): Boolean {
        val since = nowMillis - lastSentMillis
        if (since < INTERVAL_MILLIS) return false
        if (lastSentAt == null) return true
        return since >= HEARTBEAT_MILLIS || Geo.distanceMetres(lastSentAt, position) >= MIN_METRES
    }
}
