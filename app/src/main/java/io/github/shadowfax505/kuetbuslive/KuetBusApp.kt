package io.github.shadowfax505.kuetbuslive

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.google.firebase.FirebaseApp
import org.maplibre.android.MapLibre

class KuetBusApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // initializeApp returns null when google-services.json was not added to the build.
        firebaseReady = FirebaseApp.getApps(this).isNotEmpty() || FirebaseApp.initializeApp(this) != null
        MapLibre.getInstance(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TRIP, getString(R.string.trip_channel_name), NotificationManager.IMPORTANCE_LOW)
                .apply { description = getString(R.string.trip_channel_description) }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ARRIVAL, getString(R.string.arrival_channel_name), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = getString(R.string.arrival_channel_description) }
        )
    }

    companion object {
        const val CHANNEL_TRIP = "live_trip"
        const val CHANNEL_ARRIVAL = "bus_arriving"

        var firebaseReady = false
            private set
    }
}
