package io.github.shadowfax505.kuetbuslive.model

import java.time.LocalDate

// Who drives which fleet bus on a given day, the same rule as the iOS app and the
// Firestore rules:
//     cover active that day -> the cover driver
//     otherwise             -> the regular (default) driver
//     neither               -> nobody
object BusAssignment {

    fun isCoverActive(cover: BusCover?, millis: Long): Boolean {
        cover ?: return false
        val today = Timetable.dayString(millis)
        return cover.fromDate <= today && today <= cover.untilDate
    }

    fun driverFor(bus: FleetBus, millis: Long): String? =
        if (isCoverActive(bus.cover, millis)) bus.cover?.driverId else bus.defaultDriverId

    fun busFor(driverId: String, fleet: List<FleetBus>, millis: Long): FleetBus? =
        fleet.firstOrNull { driverFor(it, millis) == driverId }

    // Builds a cover from two days, with the timestamps the rules compare against.
    fun makeCover(driverId: String, fromDay: String, untilDay: String, reason: String, setBy: String): BusCover? {
        val first = runCatching { LocalDate.parse(fromDay) }.getOrNull() ?: return null
        var last = runCatching { LocalDate.parse(untilDay) }.getOrNull() ?: return null
        if (last.isBefore(first)) last = first
        val start = first.atStartOfDay(Timetable.dhaka).toInstant().toEpochMilli()
        val end = last.plusDays(1).atStartOfDay(Timetable.dhaka).toInstant().toEpochMilli()
        return BusCover(driverId, first.toString(), last.toString(), start, end, reason, setBy)
    }

    // "normal-1" for "Normal-1", "special-1-ac" for "Special-1 (AC)".
    fun fleetId(name: String): String =
        name.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
            .split("-").filter { it.isNotEmpty() }.joinToString("-")

    // Approved, active drivers with no bus that day who aren't off: they can cover.
    fun freeDrivers(drivers: List<AppUser>, fleet: List<FleetBus>, absentIds: Set<String>, millis: Long): List<AppUser> {
        val busy = fleet.mapNotNull { driverFor(it, millis) }.toSet()
        return drivers.filter { it.isApproved && it.active && it.id !in busy && it.id !in absentIds }
    }
}
