package io.github.shadowfax505.kuetbuslive.driver

import io.github.shadowfax505.kuetbuslive.model.BusRoute
import io.github.shadowfax505.kuetbuslive.model.BusSchedule
import io.github.shadowfax505.kuetbuslive.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// The trip being driven, shared by the driver screen and the location service,
// which keeps running when the screen is off or another app is open.
data class ActiveTrip(
    val schedule: BusSchedule,
    val route: BusRoute,
    val fleetBusId: String,
    val driverId: String,
) {
    // One live document per fleet bus, the same ID as the iOS app and the rules expect.
    val busDocId: String get() = "bus-$fleetBusId"
}

data class TripStatus(
    val trip: ActiveTrip? = null,
    val position: LatLng? = null,
    val heading: Double? = null,
    val speed: Double? = null,
    val updatesSent: Int = 0,
    val status: String = "Not on a trip",
    val error: String? = null,
)

object TripTracker {
    private val _state = MutableStateFlow(TripStatus())
    val state: StateFlow<TripStatus> = _state.asStateFlow()

    fun begin(trip: ActiveTrip) = _state.update { TripStatus(trip = trip, status = "Waiting for GPS…") }

    fun moved(position: LatLng, heading: Double?, speed: Double?) =
        _state.update { it.copy(position = position, heading = heading ?: it.heading, speed = speed) }

    fun sent() = _state.update { it.copy(updatesSent = it.updatesSent + 1, status = "Sharing live location", error = null) }

    fun status(text: String) = _state.update { it.copy(status = text) }

    fun failed(message: String) = _state.update { it.copy(trip = null, status = "Trip stopped", error = message) }

    fun ended() = _state.update { TripStatus(position = it.position, status = "Trip ended") }

    fun clearError() = _state.update { it.copy(error = null) }
}
