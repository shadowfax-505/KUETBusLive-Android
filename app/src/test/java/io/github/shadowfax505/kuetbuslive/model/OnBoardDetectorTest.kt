package io.github.shadowfax505.kuetbuslive.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnBoardDetectorTest {
    private fun bus(lat: Double, speed: Double = 8.0) = Bus(
        id = "bus-normal-1", number = "Normal-1 · Rupsha", routeId = "r", routeName = "R", driverId = "d",
        lat = lat, lng = 89.5, updatedAtMillis = 0, crowd = CrowdLevel.UNKNOWN, audience = BusAudience.STUDENT,
        scheduleId = "s1", fleetBusId = "normal-1", heading = 0.0, speed = speed,
    )

    @Test
    fun onBoardAfterAMinuteNextToAMovingBus() {
        val d = OnBoardDetector()
        val rider = LatLng(22.9, 89.5)
        assertFalse(d.update(rider, bus(22.9001), 0))
        assertFalse(d.update(rider, bus(22.9001), 30_000))
        assertTrue(d.update(rider, bus(22.9001), 60_000))
    }

    @Test
    fun walkingAwayOrAParkedBusDoesNotCount() {
        val d = OnBoardDetector()
        val rider = LatLng(22.9, 89.5)
        assertFalse(d.update(rider, bus(22.9001, speed = 0.0), 0))
        assertFalse(d.update(rider, bus(22.9001, speed = 0.0), 120_000))
        d.update(rider, bus(22.9001), 130_000)
        assertFalse(d.update(rider, bus(22.902), 170_000))   // about 220 m apart
        assertFalse(d.update(rider, bus(22.9001), 200_000))
    }

    @Test
    fun crowdedShowsForTenMinutes() {
        val b = bus(22.9).copy(crowd = CrowdLevel.FULL, crowdAtMillis = 1_000_000)
        assertTrue(b.isCrowded(1_000_000 + 9 * 60_000))
        assertFalse(b.isCrowded(1_000_000 + 11 * 60_000))
        assertFalse(bus(22.9).copy(crowd = CrowdLevel.FULL).isCrowded(1_000_000))
    }
}
