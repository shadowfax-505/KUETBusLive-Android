package io.github.shadowfax505.kuetbuslive.model

import io.github.shadowfax505.kuetbuslive.reminders.TripReminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

// "My trips": the days a pin uses, when its bus next leaves and when its reminder
// rings, in Dhaka. Same cases as the iOS MyTripTests.
class MyTripTest {
    private val schedule = BusSchedule(
        id = "s1", tripName = "Normal-1", audience = BusAudience.STUDENT, days = TripDays.SUN_TO_THU,
        period = TripPeriod.MORNING, campusTime = "07:00", spot = "Rupsha", spotBn = null, spotTime = "07:40", remarks = "",
    )

    private fun dhaka(day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, Timetable.dhaka).toInstant().toEpochMilli()

    @Test
    fun newPinUsesEveryDayTheTripRuns() {
        assertEquals(listOf("sun", "mon", "tue", "wed", "thu"), MyTrip.new(schedule).days)
        assertEquals(listOf("sat"), MyTrip.new(schedule.copy(days = TripDays.SATURDAY)).days)
        assertEquals(10, MyTrip.new(schedule).remindMinutes)
    }

    @Test
    fun nextDepartureSkipsTodayOnceTheBusHasGone() {
        // Monday 16 November 2026, 08:00: Monday's 07:00 bus has gone, so Wednesday.
        assertEquals(dhaka(18, 7), MyTrip.nextDeparture("07:00", listOf("mon", "wed"), dhaka(16, 8)))
        assertEquals(dhaka(16, 7), MyTrip.nextDeparture("07:00", listOf("mon", "wed"), dhaka(16, 6)))
    }

    @Test
    fun reminderRingsMinutesBeforeTheChosenDeparture() {
        val trip = MyTrip.new(schedule).copy(leg = MyTrip.Leg.SPOT, days = listOf("mon"), remindMinutes = 15)
        val entry = TripReminders.entries(listOf(trip), listOf(schedule)).single()
        assertEquals(dhaka(16, 7, 25), entry.nextAt(dhaka(16, 6)))
        // Within the 15 minutes before the bus, the reminder for this week has passed.
        assertEquals(dhaka(23, 7, 25), entry.nextAt(dhaka(16, 7, 30)))
    }

    @Test
    fun noReminderWhenOffOrTheTripLeftTheTimetable() {
        val off = MyTrip.new(schedule).copy(remindMinutes = 0)
        assertTrue(TripReminders.entries(listOf(off), listOf(schedule)).isEmpty())
        assertTrue(TripReminders.entries(listOf(MyTrip.new(schedule)), emptyList()).isEmpty())
    }
}
