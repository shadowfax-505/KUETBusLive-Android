package io.github.shadowfax505.kuetbuslive.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnBoardDetectorTest {
    private fun bus(lat: Double, speed: Double = 8.0, updated: Long = 0) = Bus(
        id = "bus-normal-1", number = "Normal-1 · Rupsha", routeId = "r", routeName = "R", driverId = "d",
        lat = lat, lng = 89.5, updatedAtMillis = updated, crowd = CrowdLevel.UNKNOWN, audience = BusAudience.STUDENT,
        scheduleId = "s1", fleetBusId = "normal-1", heading = 0.0, speed = speed,
    )

    @Test
    fun onBoardAfterAMinuteMovingWithTheBus() {
        val d = OnBoardDetector()
        val rider = LatLng(22.9, 89.5)
        assertFalse(d.update(rider, 8.0, bus(22.9001, updated = 0), 0))
        assertFalse(d.update(rider, 8.0, bus(22.9001, updated = 30_000), 30_000))
        assertTrue(d.update(rider, 8.0, bus(22.9001, updated = 60_000), 60_000))
        // A short stop doesn't reset it.
        assertTrue(d.update(rider, 0.0, bus(22.9001, speed = 0.0, updated = 70_000), 70_000))
    }

    @Test
    fun walkingBesideAParkedBusOrLeavingDoesNotCount() {
        val d = OnBoardDetector()
        val rider = LatLng(22.9, 89.5)
        assertFalse(d.update(rider, 1.0, bus(22.9001, speed = 0.0), 0))
        assertFalse(d.update(rider, 1.0, bus(22.9001, speed = 0.0, updated = 120_000), 120_000))
        assertFalse(d.update(rider, 8.0, bus(22.902, updated = 130_000), 130_000))   // about 220 m apart
    }

    @Test
    fun theBusIsMovedAheadByItsAge() {
        val p = OnBoardDetector.projected(LatLng(22.9, 89.5), 0.0, 10.0, 0, 5_000)
        assertEquals(50.0, Geo.distanceMetres(LatLng(22.9, 89.5), p), 0.5)
        // Never more than 10 seconds ahead.
        val far = OnBoardDetector.projected(LatLng(22.9, 89.5), 0.0, 10.0, 0, 60_000)
        assertEquals(100.0, Geo.distanceMetres(LatLng(22.9, 89.5), far), 0.5)
    }

    @Test
    fun crowdedShowsForTenMinutes() {
        val b = bus(22.9).copy(crowd = CrowdLevel.FULL, crowdAtMillis = 1_000_000)
        assertTrue(b.isCrowded(1_000_000 + 9 * 60_000))
        assertFalse(b.isCrowded(1_000_000 + 11 * 60_000))
        assertFalse(bus(22.9).copy(crowd = CrowdLevel.FULL).isCrowded(1_000_000))
    }
}
