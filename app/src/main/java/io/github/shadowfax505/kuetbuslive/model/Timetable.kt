package io.github.shadowfax505.kuetbuslive.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

// Timetable maths. Times are 24-hour "HH:mm" strings, as stored in Firestore.
// KUET is in Khulna, so "now" and "today" are always taken in Asia/Dhaka.
object Timetable {
    val dhaka: ZoneId = ZoneId.of("Asia/Dhaka")

    // "13:30" -> 810. null for anything that isn't a valid time.
    fun minutes(time: String): Int? {
        val parts = time.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    fun isValidTime(time: String): Boolean = minutes(time) != null

    // "13:30" -> "1:30 PM"
    fun display(time: String): String {
        val total = minutes(time) ?: return time
        val hour = total / 60
        val minute = total % 60
        val suffix = if (hour < 12) "AM" else "PM"
        val hour12 = if (hour % 12 == 0) 12 else hour % 12
        return "%d:%02d %s".format(hour12, minute, suffix)
    }

    fun dhakaTime(millis: Long): ZonedDateTime = Instant.ofEpochMilli(millis).atZone(dhaka)

    fun minuteOfDay(millis: Long): Int = dhakaTime(millis).let { it.hour * 60 + it.minute }

    fun runsOn(schedule: BusSchedule, millis: Long): Boolean =
        schedule.days.includes(dhakaTime(millis).dayOfWeek.value)

    // The trip still has a departure later that day (from campus or from the city).
    fun isUpcoming(schedule: BusSchedule, millis: Long): Boolean {
        if (!runsOn(schedule, millis)) return false
        val now = minuteOfDay(millis)
        return listOfNotNull(minutes(schedule.campusTime), minutes(schedule.spotTime)).any { it >= now }
    }

    // The first trip (by campus time) this person may ride that still leaves later that day.
    fun nextTrip(schedules: List<BusSchedule>, role: UserRole, millis: Long): BusSchedule? =
        schedules.sortedBy { minutes(it.campusTime) ?: 0 }
            .firstOrNull { it.audience.canBoard(role) && isUpcoming(it, millis) }

    // Minutes from now until the trip leaves campus, or null if it already left today.
    fun minutesUntil(time: String, millis: Long): Int? {
        val target = minutes(time) ?: return null
        val diff = target - minuteOfDay(millis)
        return diff.takeIf { it >= 0 }
    }

    // Sort order used on the schedule screen.
    fun sortKey(schedule: BusSchedule): Int =
        schedule.period.ordinal * 10_000 + (minutes(schedule.campusTime) ?: 0)

    // "2026-11-10" for any moment on that day in Dhaka.
    fun dayString(millis: Long): String = dhakaTime(millis).toLocalDate().toString()

    // "2026-11-10" -> 00:00 that day in Dhaka.
    fun startOfDayMillis(day: String): Long? = runCatching {
        LocalDate.parse(day).atStartOfDay(dhaka).toInstant().toEpochMilli()
    }.getOrNull()
}
