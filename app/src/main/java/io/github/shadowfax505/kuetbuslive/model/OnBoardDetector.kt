package io.github.shadowfax505.kuetbuslive.model

// Works out whether the rider is on a bus, the same way as the iOS app: the rider
// has stayed within 30 m of the moving bus for a minute. Only then is "it's
// overcrowded" offered, and the rules check the rider's position again.
class OnBoardDetector(
    private val nearMetres: Double = NEAR_METRES,
    private val holdMillis: Long = HOLD_MILLIS,
) {
    private var busId: String? = null
    private var nearSince: Long? = null

    // Feed every new rider fix. Returns true once the rider counts as on board.
    fun update(rider: LatLng?, bus: Bus?, nowMillis: Long): Boolean {
        if (rider == null || bus == null) {
            reset()
            return false
        }
        if (bus.id != busId) {
            busId = bus.id
            nearSince = null
        }
        val near = Geo.distanceMetres(rider, bus.position) <= nearMetres
        if (!near) {
            nearSince = null
            return false
        }
        // Standing next to a parked bus doesn't count: the bus must be moving at some point.
        val moving = (bus.speed ?: 0.0) > MOVING_SPEED
        val since = nearSince
        if (since == null) {
            if (moving) nearSince = nowMillis
            return false
        }
        return nowMillis - since >= holdMillis
    }

    fun reset() {
        busId = null
        nearSince = null
    }

    companion object {
        const val NEAR_METRES = 30.0
        const val HOLD_MILLIS = 60_000L
        const val MOVING_SPEED = 1.5   // metres per second
    }
}
