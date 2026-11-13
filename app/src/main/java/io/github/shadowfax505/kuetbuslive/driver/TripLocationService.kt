package io.github.shadowfax505.kuetbuslive.driver

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.github.shadowfax505.kuetbuslive.KuetBusApp
import io.github.shadowfax505.kuetbuslive.MainActivity
import io.github.shadowfax505.kuetbuslive.R
import io.github.shadowfax505.kuetbuslive.data.Osrm
import io.github.shadowfax505.kuetbuslive.data.Repository
import io.github.shadowfax505.kuetbuslive.data.SpecialDays
import io.github.shadowfax505.kuetbuslive.data.StatsStore
import io.github.shadowfax505.kuetbuslive.model.GpsSample
import io.github.shadowfax505.kuetbuslive.model.LatLng
import io.github.shadowfax505.kuetbuslive.model.RouteLine
import io.github.shadowfax505.kuetbuslive.model.RouteTiming
import io.github.shadowfax505.kuetbuslive.model.SeenWave
import io.github.shadowfax505.kuetbuslive.model.SegmentProfile
import io.github.shadowfax505.kuetbuslive.model.StatKey
import io.github.shadowfax505.kuetbuslive.model.TimeBucket
import io.github.shadowfax505.kuetbuslive.model.TripLearner
import io.github.shadowfax505.kuetbuslive.model.Wave
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

// Shares the bus position while a trip runs, also with the screen off. Android shows
// an ongoing notification for it, with a button to end the trip.
//
// It also keeps this trip's own GPS track (a point every 2 s) and the waves the driver
// saw. When the trip ends, the track becomes a few numbers for the shared travel model
// (TripLearner, StatsStore), saved before the bus is removed because the rules accept
// them only while the bus is running. The track itself never leaves the phone.
class TripLocationService : LifecycleService() {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var lastSentMillis = 0L
    private var created = false
    private var tripRecordId: String? = null
    private var sending = false
    private val samples = mutableListOf<GpsSample>()
    private val seenWaves = mutableMapOf<String, SeenWave>()
    private var wavesJob: Job? = null
    private var waves: List<Wave> = emptyList()
    private var dropoffs: List<Wave> = emptyList()
    // The model's numbers from before this trip, to check this trip's predictions.
    private var profileBefore: SegmentProfile = SegmentProfile.EMPTY

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val heading = if (location.hasBearing() && location.speed > 0.5f) location.bearing.toDouble() else null
            val speed = if (location.hasSpeed()) location.speed.toDouble() else null
            val position = LatLng(location.latitude, location.longitude)
            val accurate = !location.hasAccuracy() || location.accuracy < 50f
            if (accurate && (samples.isEmpty() || location.time - samples.last().millis >= SAMPLE_MILLIS)) {
                samples += GpsSample(location.time, position, if (location.hasSpeed()) location.speed.toDouble() else null)
            }
            TripTracker.moved(position, heading, speed)
            send(position, heading, speed)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_END) {
            endTrip()
            return START_NOT_STICKY
        }
        val trip = TripTracker.state.value.trip
        if (trip == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(trip),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        startUpdates()
        samples.clear()
        seenWaves.clear()
        SpecialDays.start()
        lifecycleScope.launch {
            tripRecordId = runCatching {
                Repository.recordTripStart(trip.driverId, trip.fleetBusId, trip.schedule.id, trip.route.id)
            }.getOrNull()
        }
        lifecycleScope.launch {
            StatsStore.load(trip.route.id, trip.driverId, force = true)
            profileBefore = StatsStore.profile(trip.route.id)
        }
        // Every wave seen on this trip, for "does this driver stop for waves".
        wavesJob = lifecycleScope.launch {
            launch {
                Repository.waves(trip.busDocId).collect { list ->
                    waves = list
                    list.forEach { w -> seenWaves.getOrPut(w.uid) { SeenWave(w.position, w.createdAtMillis) } }
                }
            }
            launch { Repository.dropoffs(trip.busDocId).collect { dropoffs = it } }
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .build()
        runCatching { client.requestLocationUpdates(request, callback, Looper.getMainLooper()) }
            .onFailure { TripTracker.failed("Location permission is needed to share the bus position.") ; stopSelf() }
    }

    // At most every 5 seconds, like the iOS app.
    private fun send(position: LatLng, heading: Double?, speed: Double?) {
        val trip = TripTracker.state.value.trip ?: return
        val now = System.currentTimeMillis()
        if (sending || now - lastSentMillis < SEND_INTERVAL_MILLIS) return
        lastSentMillis = now
        sending = true
        lifecycleScope.launch {
            try {
                if (!created) {
                    // First fix: the whole document. The rules check the bus is this driver's today.
                    Repository.createLiveBus(trip.busDocId, trip.schedule, trip.route, trip.fleetBusId, trip.driverId,
                        position.lat, position.lng, heading, speed)
                    created = true
                } else {
                    // Later fixes: only the moving fields, so crowd reports are kept.
                    Repository.moveLiveBus(trip.busDocId, position.lat, position.lng, heading, speed)
                }
                TripTracker.sent()
            } catch (e: Exception) {
                if (!created) {
                    // The server refused, for example because this bus isn't assigned to this driver today.
                    TripTracker.failed("Could not start the trip: ${e.localizedMessage ?: "refused by the server"}")
                    stopUpdates()
                    stopSelf()
                } else {
                    TripTracker.status("Weak signal, retrying…")
                }
            } finally {
                sending = false
            }
        }
    }

    private fun endTrip() {
        val trip = TripTracker.state.value.trip
        stopUpdates()
        TripTracker.ended()
        wavesJob?.cancel()
        if (trip != null) {
            lifecycleScope.launch {
                withContext(NonCancellable) {
                    tripRecordId?.let { id -> runCatching { Repository.recordTripEnd(id) } }
                    // Leave no waves or stop requests behind.
                    waves.forEach { Repository.deleteWave(trip.busDocId, it.uid) }
                    dropoffs.forEach { Repository.cancelDropoff(trip.busDocId, it.uid) }
                    // The numbers first: the rules accept them only while the bus is running.
                    if (created) withTimeoutOrNull(UPLOAD_TIMEOUT_MILLIS) { learnFromTrip(trip) }
                    runCatching { Repository.endLiveBus(trip.busDocId) }
                }
                stopSelf()
            }
        } else {
            stopSelf()
        }
    }

    // Turns this trip's track into stretch times, stop times and wave outcomes, and saves them.
    private suspend fun learnFromTrip(trip: ActiveTrip) {
        val track = samples.toList()
        val seen = seenWaves.values.toList()
        samples.clear()
        seenWaves.clear()
        val stops = trip.route.stops.map { it.position }
        if (track.size < 2 || stops.size < 2) return
        val path = runCatching { Osrm.path(trip.route.id, stops) }.getOrDefault(stops)
        val line = RouteLine(trip.route.id, path, stops)
        val observations = TripLearner.learn(track, line, seen)
        // What the model would have said when the bus left, against what really happened.
        val started = observations.departedAtMillis ?: System.currentTimeMillis()
        val timing = RouteTiming(
            if (observations.reversed) line.flipped() else line,
            profileBefore,
            TimeBucket.of(started, SpecialDays.kindOn(started)),
            trip.driverId,
        )
        val errors = TripLearner.predictionErrors(observations, timing)
        // Trips from KUET follow the campus time; trips back follow the city spot's time.
        val scheduled = if (observations.reversed) trip.schedule.spotTime else trip.schedule.campusTime
        val late = TripLearner.lateness(observations.departedAtMillis, scheduled)?.let {
            (StatKey.letter(observations.reversed) + trip.schedule.id) to it
        }
        StatsStore.upload(
            observations, trip.route.id, trip.busDocId, trip.driverId,
            specialKindAt = { SpecialDays.kindOn(it) }, late = late, errors = errors,
        )
    }

    private fun stopUpdates() {
        runCatching { client.removeLocationUpdates(callback) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        runCatching { client.removeLocationUpdates(callback) }
        super.onDestroy()
    }

    private fun notification(trip: ActiveTrip): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val end = PendingIntent.getService(
            this, 1, Intent(this, TripLocationService::class.java).setAction(ACTION_END), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, KuetBusApp.CHANNEL_TRIP)
            .setSmallIcon(R.drawable.ic_stat_bus)
            .setContentTitle("${trip.schedule.title} is live")
            .setContentText("Riders can see your bus on the map.")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "End trip", end)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 42
        private const val SEND_INTERVAL_MILLIS = 5_000L
        private const val SAMPLE_MILLIS = 2_000L
        // Ending a trip waits at most this long for the numbers to be saved.
        private const val UPLOAD_TIMEOUT_MILLIS = 8_000L
        private const val ACTION_END = "end_trip"

        fun start(context: Context, trip: ActiveTrip) {
            TripTracker.begin(trip)
            ContextCompat.startForegroundService(context, Intent(context, TripLocationService::class.java))
        }

        fun end(context: Context) {
            context.startService(Intent(context, TripLocationService::class.java).setAction(ACTION_END))
        }
    }
}
