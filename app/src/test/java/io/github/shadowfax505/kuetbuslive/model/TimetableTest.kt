package io.github.shadowfax505.kuetbuslive.model

import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableTest {
    private fun dhaka(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, Timetable.dhaka).toInstant().toEpochMilli()

    private fun trip(name: String, days: TripDays, campus: String, spotTime: String) = BusSchedule(
        id = name + campus, tripName = name, audience = BusAudience.fromTripName(name), days = days,
        period = TripPeriod.MORNING, campusTime = campus, spot = "Rupsha", spotBn = null,
        spotTime = spotTime, remarks = "",
    )

    @Test
    fun parsesAndFormatsTimes() {
        assertEquals(810, Timetable.minutes("13:30"))
        assertNull(Timetable.minutes("25:00"))
        assertNull(Timetable.minutes("7.30"))
        assertEquals("1:30 PM", Timetable.display("13:30"))
        assertEquals("12:05 AM", Timetable.display("00:05"))
    }

    @Test
    fun noBusesOnFriday() {
        // 13 Nov 2026 is a Friday, 14 Nov a Saturday, 15 Nov a Sunday.
        val weekday = trip("Normal-1", TripDays.SUN_TO_THU, "06:30", "07:10")
        val saturday = trip("Normal-1", TripDays.SATURDAY, "06:30", "07:10")
        assertFalse(Timetable.runsOn(weekday, dhaka(2026, 11, 13, 5, 0)))
        assertFalse(Timetable.runsOn(saturday, dhaka(2026, 11, 13, 5, 0)))
        assertTrue(Timetable.runsOn(saturday, dhaka(2026, 11, 14, 5, 0)))
        assertTrue(Timetable.runsOn(weekday, dhaka(2026, 11, 15, 5, 0)))
    }

    @Test
    fun nextTripRespectsAudience() {
        val sunday = dhaka(2026, 11, 15, 6, 0)
        val special = trip("Special-1", TripDays.SUN_TO_THU, "06:15", "07:00")
        val normal = trip("Normal-1", TripDays.SUN_TO_THU, "06:30", "07:10")
        val all = listOf(normal, special)
        assertEquals(normal, Timetable.nextTrip(all, UserRole.STUDENT, sunday))
        assertEquals(special, Timetable.nextTrip(all, UserRole.TEACHER, sunday))
        assertNull(Timetable.nextTrip(all, UserRole.STUDENT, dhaka(2026, 11, 15, 8, 0)))
    }

    @Test
    fun dayStringsUseDhakaTime() {
        // 23:30 UTC on 9 Nov is already 10 Nov in Dhaka.
        val millis = ZonedDateTime.parse("2026-11-09T23:30:00Z").toInstant().toEpochMilli()
        assertEquals("2026-11-10", Timetable.dayString(millis))
    }
}
