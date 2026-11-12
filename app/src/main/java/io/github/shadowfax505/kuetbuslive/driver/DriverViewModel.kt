package io.github.shadowfax505.kuetbuslive.driver

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.shadowfax505.kuetbuslive.data.Osrm
import io.github.shadowfax505.kuetbuslive.data.Repository
import io.github.shadowfax505.kuetbuslive.model.AppUser
import io.github.shadowfax505.kuetbuslive.model.BusAssignment
import io.github.shadowfax505.kuetbuslive.model.BusRoute
import io.github.shadowfax505.kuetbuslive.model.BusSchedule
import io.github.shadowfax505.kuetbuslive.model.DriverAbsence
import io.github.shadowfax505.kuetbuslive.model.FleetBus
import io.github.shadowfax505.kuetbuslive.model.Geo
import io.github.shadowfax505.kuetbuslive.model.LatLng
import io.github.shadowfax505.kuetbuslive.model.Timetable
import io.github.shadowfax505.kuetbuslive.model.Wave
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WaitingRider(val wave: Wave, val metresAhead: Double)

data class DriverState(
    val fleet: List<FleetBus> = emptyList(),
    val schedules: List<BusSchedule> = emptyList(),
    val routes: List<BusRoute> = emptyList(),
    val absences: List<DriverAbsence> = emptyList(),
    val routePath: List<LatLng> = emptyList(),
    val waitingAhead: List<WaitingRider> = emptyList(),
    val message: String? = null,
)

// Driver side: which bus they drive today, the trip and route to drive, riders
// waiting ahead, and "I can't drive" requests.
@OptIn(ExperimentalCoroutinesApi::class)
class DriverViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(DriverState())
    val state: StateFlow<DriverState> = _state.asStateFlow()
    private var started = false
    private var wavesJob: Job? = null
    private var waves: List<Wave> = emptyList()
    private var pathRouteId: String? = null

    fun start(user: AppUser) {
        if (started) return
        started = true
        viewModelScope.launch { Repository.fleet().collect { f -> _state.update { it.copy(fleet = f) } } }
        viewModelScope.launch { Repository.schedules().collect { s -> _state.update { it.copy(schedules = s) } } }
        viewModelScope.launch { Repository.routes().collect { r -> _state.update { it.copy(routes = r.sortedBy { route -> route.name }) } } }
        viewModelScope.launch {
            Repository.myAbsences(user.id).collect { a -> _state.update { it.copy(absences = a.sortedByDescending { x -> x.createdAtMillis }) } }
        }
        // Riders waiting ahead are worked out again on every GPS fix.
        viewModelScope.launch {
            TripTracker.state.collect { status ->
                val trip = status.trip
                if (trip != null && wavesJob == null) listenToWaves(trip.busDocId)
                if (trip == null && wavesJob != null) {
                    wavesJob?.cancel(); wavesJob = null; waves = emptyList()
                    _state.update { it.copy(waitingAhead = emptyList()) }
                }
                updateWaitingAhead(status.position)
            }
        }
    }

    fun myBus(user: AppUser, now: Long = System.currentTimeMillis()): FleetBus? =
        BusAssignment.busFor(user.id, _state.value.fleet, now)

    // Today's trips of my bus. On a day with no buses (Friday) all its trips are listed.
    fun tripChoices(bus: FleetBus?, now: Long = System.currentTimeMillis()): List<BusSchedule> {
        bus ?: return emptyList()
        val mine = _state.value.schedules.filter { it.tripName == bus.name }.sortedBy { Timetable.minutes(it.campusTime) ?: 0 }
        val today = mine.filter { Timetable.runsOn(it, now) }
        return today.ifEmpty { mine }
    }

    fun loadPath(route: BusRoute?) {
        if (route == null || route.id == pathRouteId) return
        pathRouteId = route.id
        val stops = route.stops.map { it.position }
        _state.update { it.copy(routePath = stops) }
        viewModelScope.launch {
            val path = Osrm.path(route.id, stops)
            if (pathRouteId == route.id) _state.update { it.copy(routePath = path) }
        }
    }

    fun startTrip(user: AppUser, bus: FleetBus, schedule: BusSchedule, route: BusRoute) {
        TripLocationService.start(getApplication(), ActiveTrip(schedule, route, bus.id, user.id))
    }

    fun endTrip() = TripLocationService.end(getApplication())

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun requestAbsence(user: AppUser, from: String, until: String, reason: String, done: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                require(Timetable.startOfDayMillis(from) != null && Timetable.startOfDayMillis(until) != null) { "Dates look like 2026-11-14." }
                require(from <= until) { "The last day can't be before the first day." }
                require(reason.isNotBlank()) { "Write a short reason." }
                Repository.requestAbsence(user.id, user.name, from, until, reason.trim().take(300))
            }.onSuccess {
                _state.update { it.copy(message = "Sent. The transport office will arrange a cover driver.") }
                done()
            }.onFailure { e -> _state.update { it.copy(message = e.message ?: "Could not send.") } }
        }
    }

    fun withdrawAbsence(id: String) {
        viewModelScope.launch { runCatching { Repository.withdrawAbsence(id) } }
    }

    // ---------- Riders waiting ahead ----------

    private fun listenToWaves(busDocId: String) {
        wavesJob = viewModelScope.launch {
            Repository.waves(busDocId).collect { list ->
                waves = list
                updateWaitingAhead(TripTracker.state.value.position)
            }
        }
    }

    // Riders the bus has passed, and waves older than 15 minutes, are cleared.
    // A new rider ahead plays a short tone and a buzz, so the driver needn't look.
    private fun updateWaitingAhead(position: LatLng?) {
        val trip = TripTracker.state.value.trip
        val path = _state.value.routePath
        if (position == null || trip == null || path.size < 2) {
            if (_state.value.waitingAhead.isNotEmpty()) _state.update { it.copy(waitingAhead = emptyList()) }
            return
        }
        val now = System.currentTimeMillis()
        val ahead = mutableListOf<WaitingRider>()
        for (wave in waves) {
            val metres = Geo.metresAhead(position, wave.position, path) ?: 0.0
            if (!wave.isActive(now) || metres < -PASSED_METRES) {
                viewModelScope.launch { Repository.deleteWave(trip.busDocId, wave.uid) }
            } else {
                ahead += WaitingRider(wave, metres.coerceAtLeast(0.0))
            }
        }
        ahead.sortBy { it.metresAhead }
        if (ahead.size > _state.value.waitingAhead.size) alert()
        _state.update { it.copy(waitingAhead = ahead) }
    }

    private fun alert() {
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).startTone(ToneGenerator.TONE_PROP_ACK, 200) }
        runCatching {
            val vibrator = getApplication<Application>().getSystemService(Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    companion object {
        private const val PASSED_METRES = 40.0
    }
}
