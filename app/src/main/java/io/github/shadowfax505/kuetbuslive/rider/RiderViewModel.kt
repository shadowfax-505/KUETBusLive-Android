package io.github.shadowfax505.kuetbuslive.rider

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.shadowfax505.kuetbuslive.data.Osrm
import io.github.shadowfax505.kuetbuslive.data.Prefs
import io.github.shadowfax505.kuetbuslive.data.Repository
import io.github.shadowfax505.kuetbuslive.data.RouteInfo
import io.github.shadowfax505.kuetbuslive.model.AppUser
import io.github.shadowfax505.kuetbuslive.model.Bus
import io.github.shadowfax505.kuetbuslive.model.BusRoute
import io.github.shadowfax505.kuetbuslive.model.BusSchedule
import io.github.shadowfax505.kuetbuslive.model.CrowdLevel
import io.github.shadowfax505.kuetbuslive.model.Geo
import io.github.shadowfax505.kuetbuslive.model.LatLng
import io.github.shadowfax505.kuetbuslive.model.Stop
import io.github.shadowfax505.kuetbuslive.model.UserRole
import io.github.shadowfax505.kuetbuslive.model.Wave
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RiderState(
    val buses: List<Bus> = emptyList(),
    val routes: List<BusRoute> = emptyList(),
    val schedules: List<BusSchedule> = emptyList(),
    val routePaths: Map<String, List<LatLng>> = emptyMap(),
    val eta: Map<String, RouteInfo> = emptyMap(),
    val etaFailed: Set<String> = emptySet(),
    val selectedBusId: String? = null,
    val followBusId: String? = null,
    val wavingAtBusId: String? = null,
    val message: String? = null,
    val nowMillis: Long = System.currentTimeMillis(),
) {
    val selectedBus: Bus? get() = buses.firstOrNull { it.id == selectedBusId }
}

// Rider side of the live map: running buses this rider may board, ETAs to their
// stop, crowd reports and "I'm waiting here". Students only see student buses;
// teachers see every bus. The same behaviour as the iOS BusViewModel.
class RiderViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = Prefs(application)
    private val notifier = ArrivalNotifier(application)

    private val _state = MutableStateFlow(RiderState(wavingAtBusId = prefs.wavingAtBusId))
    val state: StateFlow<RiderState> = _state.asStateFlow()

    var user: AppUser? = null
        private set
    private var started = false
    private val lastEtaFetch = mutableMapOf<String, Long>()
    private val alerted = mutableSetOf<String>()

    fun start(user: AppUser) {
        this.user = user
        if (started) {
            refreshAllEtas(force = true)
            return
        }
        started = true
        viewModelScope.launch {
            Repository.buses().collect { all ->
                val role = this@RiderViewModel.user?.role ?: UserRole.STUDENT
                val visible = all.filter { it.audience.canBoard(role) }
                _state.update { s ->
                    s.copy(
                        buses = visible,
                        selectedBusId = s.selectedBusId?.takeIf { id -> visible.any { it.id == id } },
                        followBusId = s.followBusId?.takeIf { id -> visible.any { it.id == id } },
                        // The bus I waved at finished its trip, so the wave is gone too.
                        wavingAtBusId = s.wavingAtBusId?.takeIf { id -> all.any { it.id == id } },
                    )
                }
                visible.forEach { loadRoutePath(it.routeId) }
                refreshAllEtas(force = false)
            }
        }
        viewModelScope.launch {
            Repository.routes().collect { routes ->
                _state.update { it.copy(routes = routes) }
                _state.value.buses.forEach { loadRoutePath(it.routeId) }
            }
        }
        viewModelScope.launch {
            Repository.schedules().collect { schedules -> _state.update { it.copy(schedules = schedules) } }
        }
        // A clock for "12 s ago" and the 30-second ETA refresh.
        viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                _state.update { it.copy(nowMillis = System.currentTimeMillis()) }
                refreshAllEtas(force = false)
            }
        }
    }

    fun updateUser(user: AppUser) {
        val stopChanged = user.favoriteStopName != this.user?.favoriteStopName
        this.user = user
        if (stopChanged) {
            _state.update { it.copy(eta = emptyMap(), etaFailed = emptySet()) }
            refreshAllEtas(force = true)
        }
    }

    // ---------- Selection ----------

    fun select(busId: String?) {
        _state.update { it.copy(selectedBusId = busId, followBusId = if (busId == null) null else it.followBusId) }
        busId?.let { id -> _state.value.buses.firstOrNull { it.id == id }?.let { refreshEta(it, force = true) } }
    }

    fun toggleFollow() {
        _state.update { it.copy(followBusId = if (it.followBusId == null) it.selectedBusId else null) }
    }

    fun stopFollowing() {
        if (_state.value.followBusId != null) _state.update { it.copy(followBusId = null) }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    // ---------- Routes and ETAs ----------

    fun routeFor(bus: Bus): BusRoute? = _state.value.routes.firstOrNull { it.id == bus.routeId }

    // The rider's stop. Its exact coordinates live on the profile.
    val boardingStop: Stop? get() = user?.favoriteStop

    private fun loadRoutePath(routeId: String) {
        if (_state.value.routePaths.containsKey(routeId)) return
        val route = _state.value.routes.firstOrNull { it.id == routeId } ?: return
        val stops = route.stops.map { it.position }
        // Straight lines between stops until the road path arrives.
        _state.update { it.copy(routePaths = it.routePaths + (routeId to stops)) }
        viewModelScope.launch {
            val path = Osrm.path(routeId, stops)
            _state.update { it.copy(routePaths = it.routePaths + (routeId to path)) }
        }
    }

    private fun refreshAllEtas(force: Boolean) {
        _state.value.buses.forEach { refreshEta(it, force) }
    }

    // The public routing server has fair-use limits, so ask at most every 30 seconds per bus.
    private fun refreshEta(bus: Bus, force: Boolean) {
        val stop = boardingStop ?: return
        val now = System.currentTimeMillis()
        val last = lastEtaFetch[bus.id]
        if (!force && last != null && now - last < Osrm.REFRESH_MILLIS) return
        lastEtaFetch[bus.id] = now
        viewModelScope.launch {
            val info = Osrm.route(listOf(bus.position, stop.position))
            _state.update {
                if (info == null) it.copy(etaFailed = it.etaFailed + bus.id)
                else it.copy(eta = it.eta + (bus.id to info), etaFailed = it.etaFailed - bus.id)
            }
            if (info != null) checkArrival(bus, info, stop)
        }
    }

    private fun checkArrival(bus: Bus, info: RouteInfo, stop: Stop) {
        if (!prefs.arrivalAlerts || bus.id in alerted) return
        // Only when the bus is still coming towards the stop, not after it passed.
        if (info.minutes > ArrivalNotifier.ALERT_MINUTES) return
        val path = _state.value.routePaths[bus.routeId]
        if (path != null && (Geo.metresAhead(bus.position, stop.position, path) ?: 0.0) < -50) return
        alerted += bus.id
        notifier.notifyArriving(bus.tripName, stop.name, info.minutes)
    }

    // Progress from the first stop to the rider's stop, 0..1.
    fun progressFor(bus: Bus): Double? {
        val stop = boardingStop ?: return null
        val path = _state.value.routePaths[bus.routeId] ?: return null
        return Geo.progress(bus.position, stop.position, path)
    }

    // The stop the bus is closest to right now.
    fun nearestStop(bus: Bus): Stop? =
        routeFor(bus)?.stops?.minByOrNull { Geo.distanceMetres(bus.position, it.position) }

    // ---------- Crowd and waves ----------

    fun reportCrowd(bus: Bus, crowd: CrowdLevel) {
        viewModelScope.launch {
            runCatching { Repository.reportCrowd(bus.id, crowd) }
                .onSuccess { _state.update { it.copy(message = "Thanks! Crowd report sent.") } }
                .onFailure { _state.update { it.copy(message = "Could not send the report. Check your connection.") } }
        }
    }

    // Tells the driver where this rider waits, for 15 minutes. One wave at a time.
    fun wave(bus: Bus) {
        val me = user ?: return
        val stop = boardingStop ?: run {
            _state.update { it.copy(message = "Choose your stop first, so the driver knows where you are.") }
            return
        }
        val path = _state.value.routePaths[bus.routeId] ?: emptyList()
        val point = Geo.snap(stop.position, path)?.point ?: stop.position
        viewModelScope.launch {
            runCatching {
                _state.value.wavingAtBusId?.takeIf { it != bus.id }?.let { Repository.cancelWave(it, me.id) }
                Repository.sendWave(bus.id, me.id, stop.name, point.lat, point.lng)
            }.onSuccess {
                prefs.wavingAtBusId = bus.id
                prefs.wavingUntil = System.currentTimeMillis() + Wave.LIFETIME_MILLIS
                _state.update { it.copy(wavingAtBusId = bus.id, message = "The driver can see you're waiting at ${stop.name}.") }
            }.onFailure {
                _state.update { it.copy(message = "Could not send. Check your connection.") }
            }
        }
    }

    fun cancelWave() {
        val me = user ?: return
        val busId = _state.value.wavingAtBusId ?: return
        viewModelScope.launch {
            runCatching { Repository.cancelWave(busId, me.id) }
            prefs.wavingAtBusId = null
            _state.update { it.copy(wavingAtBusId = null) }
        }
    }
}
