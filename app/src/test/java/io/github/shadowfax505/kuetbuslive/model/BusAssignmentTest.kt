package io.github.shadowfax505.kuetbuslive.model

import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BusAssignmentTest {
    private fun dhakaNoon(day: Int): Long =
        ZonedDateTime.of(2026, 11, day, 12, 0, 0, 0, Timetable.dhaka).toInstant().toEpochMilli()

    @Test
    fun fleetIdsMatchTheIosApp() {
        assertEquals("normal-1", BusAssignment.fleetId("Normal-1"))
        assertEquals("special-1-ac", BusAssignment.fleetId("Special-1 (AC)"))
    }

    @Test
    fun coverDriverDrivesOnlyDuringTheCover() {
        val cover = BusAssignment.makeCover("cover", "2026-11-10", "2026-11-12", "Sick", "admin")!!
        val bus = FleetBus("normal-1", "Normal-1", "regular", cover)
        assertEquals("regular", BusAssignment.driverFor(bus, dhakaNoon(9)))
        assertEquals("cover", BusAssignment.driverFor(bus, dhakaNoon(10)))
        assertEquals("cover", BusAssignment.driverFor(bus, dhakaNoon(12)))
        assertEquals("regular", BusAssignment.driverFor(bus, dhakaNoon(13)))
    }

    @Test
    fun coverTimestampsSpanWholeDhakaDays() {
        val cover = BusAssignment.makeCover("cover", "2026-11-10", "2026-11-10", "", "admin")!!
        assertEquals(24 * 60 * 60 * 1000L, cover.endMillis - cover.startMillis)
        assertEquals(Timetable.startOfDayMillis("2026-11-10"), cover.startMillis)
    }

    @Test
    fun noDriverWhenNothingIsAssigned() {
        val bus = FleetBus("special-1", "Special-1", null, null)
        assertNull(BusAssignment.driverFor(bus, dhakaNoon(10)))
        assertNull(BusAssignment.busFor("anyone", listOf(bus), dhakaNoon(10)))
    }
}
