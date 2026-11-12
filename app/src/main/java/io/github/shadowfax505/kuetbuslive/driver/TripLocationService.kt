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
import io.github.shadowfax505.kuetbuslive.data.Repository
import io.github.shadowfax505.kuetbuslive.model.LatLng
import kotlinx.coroutines.launch

// Shares the bus position while a trip runs, also with the screen off. Android shows
// an ongoing notification for it, with a button to end the trip.
class TripLocationService : LifecycleService() {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var lastSentMillis = 0L
    private var created = false
    private var tripRecordId: String? = null
    private var sending = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val heading = if (location.hasBearing() && location.speed > 0.5f) location.bearing.toDouble() else null
            val speed = if (location.hasSpeed()) location.speed.toDouble() else null
            val position = LatLng(location.latitude, location.longitude)
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
        lifecycleScope.launch {
            tripRecordId = runCatching {
                Repository.recordTripStart(trip.driverId, trip.fleetBusId, trip.schedule.id, trip.route.id)
            }.getOrNull()
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
        if (trip != null) {
            lifecycleScope.launch {
                runCatching { Repository.endLiveBus(trip.busDocId) }
                tripRecordId?.let { id -> runCatching { Repository.recordTripEnd(id) } }
                stopSelf()
            }
        } else {
            stopSelf()
        }
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
