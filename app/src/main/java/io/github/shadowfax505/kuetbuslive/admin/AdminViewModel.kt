package io.github.shadowfax505.kuetbuslive.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.shadowfax505.kuetbuslive.data.Repository
import io.github.shadowfax505.kuetbuslive.model.AbsenceStatus
import io.github.shadowfax505.kuetbuslive.model.AppUser
import io.github.shadowfax505.kuetbuslive.model.BusAssignment
import io.github.shadowfax505.kuetbuslive.model.BusSchedule
import io.github.shadowfax505.kuetbuslive.model.Timetable
import io.github.shadowfax505.kuetbuslive.model.Complaint
import io.github.shadowfax505.kuetbuslive.model.DriverAbsence
import io.github.shadowfax505.kuetbuslive.model.FleetBus
import io.github.shadowfax505.kuetbuslive.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// One fleet bus as the office sees it: the bus document (if it exists yet) and its trips.
data class FleetRow(val id: String, val name: String, val bus: FleetBus?, val trips: List<BusSchedule> = emptyList())

// The transport office's tools: approvals, the fleet and its drivers, absences and complaints.
class AdminViewModel : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val staff: StateFlow<List<AppUser>> = Repository.usersWithRoles(listOf(UserRole.TEACHER, UserRole.DRIVER))
        .map { list -> list.sortedBy { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pending: StateFlow<List<AppUser>> = staff.map { list -> list.filter { !it.isApproved } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val drivers: StateFlow<List<AppUser>> = staff.map { list -> list.filter { it.role == UserRole.DRIVER && it.isApproved } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Every bus named in the timetable, plus any fleet document, so a new trip name
    // shows up here before a driver is assigned.
    val fleet: StateFlow<List<FleetRow>> = combine(Repository.fleet(), Repository.schedules()) { buses, schedules: List<BusSchedule> ->
        val names = (schedules.map { it.tripName } + buses.map { it.name }).distinct().sorted()
        names.map { name ->
            val id = BusAssignment.fleetId(name)
            val trips = schedules.filter { it.tripName == name }
                .sortedWith(compareBy({ it.days.ordinal }, { Timetable.minutes(it.campusTime) ?: 0 }))
            FleetRow(id, name, buses.firstOrNull { it.id == id }, trips)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val absences: StateFlow<List<DriverAbsence>> = Repository.allAbsences()
        .map { list -> list.sortedByDescending { it.createdAtMillis } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val complaints: StateFlow<List<Complaint>> = Repository.allComplaints()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearMessage() {
        _message.value = null
    }

    fun approve(user: AppUser, approved: Boolean) = run(if (approved) "${user.name} approved." else "${user.name} declined.") {
        Repository.setApproved(user.id, approved)
    }

    fun setActive(user: AppUser, active: Boolean) = run(if (active) "${user.name} is active again." else "${user.name} deactivated.") {
        Repository.setActive(user.id, active)
    }

    // A driver drives one bus: assigning them here takes them off any other bus first.
    fun setRegularDriver(row: FleetRow, driverId: String?) = run("Saved.") {
        if (driverId != null) {
            fleet.value.filter { it.id != row.id && it.bus?.defaultDriverId == driverId }.forEach {
                Repository.setDefaultDriver(it.id, it.name, null)
            }
        }
        Repository.setDefaultDriver(row.id, row.name, driverId)
    }

    fun setCover(row: FleetRow, driverId: String, from: String, until: String, reason: String, adminId: String) = run("Cover saved.") {
        val cover = BusAssignment.makeCover(driverId, from, until, reason, adminId)
            ?: throw IllegalArgumentException("Dates look like 2026-11-14.")
        if (row.bus == null) Repository.setDefaultDriver(row.id, row.name, null)
        Repository.setCover(row.id, cover)
    }

    // A different driver for one trip, or null for the bus's usual driver.
    fun setTripDriver(row: FleetRow, scheduleId: String, driverId: String?) = run("Trip driver saved.") {
        if (driverId == null && row.bus == null) return@run
        Repository.setTripDriver(row.id, row.name, scheduleId, driverId)
    }

    fun clearCover(row: FleetRow) = run("Cover removed.") { Repository.setCover(row.id, null) }

    // Marks an absence covered and puts the cover driver on the absent driver's bus.
    fun coverAbsence(absence: DriverAbsence, coverDriverId: String, adminId: String) = run("Cover arranged.") {
        val row = fleet.value.firstOrNull { it.bus?.defaultDriverId == absence.driverId }
        if (row != null) {
            val cover = BusAssignment.makeCover(coverDriverId, absence.fromDate, absence.untilDate, absence.reason, adminId)
                ?: throw IllegalArgumentException("The request has invalid dates.")
            Repository.setCover(row.id, cover)
        }
        Repository.updateAbsence(absence.id, AbsenceStatus.COVERED, coverDriverId)
    }

    fun cancelAbsence(absence: DriverAbsence) = run("Marked as cancelled.") {
        Repository.updateAbsence(absence.id, AbsenceStatus.CANCELLED, null)
    }

    private fun run(success: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { _message.value = success }
                .onFailure { _message.value = it.message ?: "Something went wrong." }
        }
    }
}
