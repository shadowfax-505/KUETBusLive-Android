package io.github.shadowfax505.kuetbuslive.model

import java.time.DayOfWeek
import java.time.ZonedDateTime

// "My trips": a timetable trip a rider usually takes, on the days they take it, with
// an optional reminder before it leaves. Saved in users/{uid}/myTrips/{scheduleId}
// with the same fields as the iOS app.
data class MyTrip(
    val scheduleId: String,
    val leg: Leg,
    val days: List<String>,       // "sun" ... "sat"
    val remindMinutes: Int,       // 0 means no reminder
    val createdAtMillis: Long,
) {
    enum class Leg(val raw: String) {
        CAMPUS("campus"),   // leaves KUET for the city
        SPOT("spot");       // leaves the city spot for KUET

        companion object {
            fun from(raw: String?): Leg = if (raw == SPOT.raw) SPOT else CAMPUS
        }
    }

    fun time(schedule: BusSchedule): String = if (leg == Leg.CAMPUS) schedule.campusTime else schedule.spotTime

    // "Sun to Thu", "Sat", or "Sun, Tue, Thu".
    val daysText: String
        get() {
            val ordered = DAY_CODES.filter { it in days }
            if (ordered == dayCodes(TripDays.SUN_TO_THU)) return "Sun to Thu"
            return ordered.joinToString(", ") { it.replaceFirstChar(Char::uppercase) }
        }

    companion object {
        val DAY_CODES = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")
        val REMINDER_CHOICES = listOf(0, 5, 10, 15, 20, 30, 45, 60)

        fun dayOfWeek(code: String): DayOfWeek? = when (code) {
            "sun" -> DayOfWeek.SUNDAY
            "mon" -> DayOfWeek.MONDAY
            "tue" -> DayOfWeek.TUESDAY
            "wed" -> DayOfWeek.WEDNESDAY
            "thu" -> DayOfWeek.THURSDAY
            "fri" -> DayOfWeek.FRIDAY
            "sat" -> DayOfWeek.SATURDAY
            else -> null
        }

        fun dayCodes(days: TripDays): List<String> = when (days) {
            TripDays.SUN_TO_THU -> listOf("sun", "mon", "tue", "wed", "thu")
            TripDays.SATURDAY -> listOf("sat")
        }

        // A new pin: every day the trip runs, from campus, reminded ten minutes before.
        fun new(schedule: BusSchedule, now: Long = System.currentTimeMillis()) =
            MyTrip(schedule.id, Leg.CAMPUS, dayCodes(schedule.days), 10, now)

        // The next moment after `now`, in Dhaka, when this pin's bus leaves on a picked day.
        fun nextDeparture(time: String, days: List<String>, now: Long): Long? {
            val minutes = Timetable.minutes(time) ?: return null
            val picked = days.mapNotNull { dayOfWeek(it) }.toSet()
            val start = Timetable.dhakaTime(now).toLocalDate()
            for (offset in 0..7) {
                val day = start.plusDays(offset.toLong())
                if (day.dayOfWeek !in picked) continue
                val at: ZonedDateTime = day.atStartOfDay(Timetable.dhaka).plusMinutes(minutes.toLong())
                val millis = at.toInstant().toEpochMilli()
                if (millis > now) return millis
            }
            return null
        }
    }
}
