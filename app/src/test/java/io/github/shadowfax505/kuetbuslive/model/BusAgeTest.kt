package io.github.shadowfax505.kuetbuslive.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// How long ago a bus last sent its position, and when it counts as left behind.
// Same wording and limits as the iOS app.
class BusAgeTest {

    @Test
    fun ageReadsInSecondsMinutesHoursAndDays() {
        assertEquals("0 s ago", Bus.ageText(-5))
        assertEquals("59 s ago", Bus.ageText(59))
        assertEquals("1 min ago", Bus.ageText(60))
        assertEquals("2 h ago", Bus.ageText(157 * 60L))
        assertEquals("1 d ago", Bus.ageText(86_400))
    }

    @Test
    fun busSilentForHalfAnHourIsAbandoned() {
        val now = 10_000_000_000L
        fun bus(ageMillis: Long) = Bus(
            id = "bus-normal-1", number = "Normal-1", routeId = "r1", routeName = "Rupsha", driverId = "d",
            lat = 22.9, lng = 89.5, updatedAtMillis = now - ageMillis, crowd = CrowdLevel.UNKNOWN,
            audience = BusAudience.STUDENT, scheduleId = null, fleetBusId = null, heading = null, speed = null,
        )
        assertFalse(bus(29 * 60_000L).isAbandoned(now))
        assertTrue(bus(31 * 60_000L).isAbandoned(now))
    }
}
