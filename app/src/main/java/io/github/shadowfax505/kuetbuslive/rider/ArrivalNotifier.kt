package io.github.shadowfax505.kuetbuslive.rider

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.shadowfax505.kuetbuslive.KuetBusApp
import io.github.shadowfax505.kuetbuslive.MainActivity
import io.github.shadowfax505.kuetbuslive.R

// "Normal-2 is 4 min from Fulbarigate": a local notification, no server needed.
class ArrivalNotifier(private val context: Context) {

    fun notifyArriving(tripName: String, stopName: String, minutes: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (minutes <= 1) "$tripName is about to reach $stopName." else "$tripName is $minutes min from $stopName."
        val notification = NotificationCompat.Builder(context, KuetBusApp.CHANNEL_ARRIVAL)
            .setSmallIcon(R.drawable.ic_stat_bus)
            .setContentTitle("Your bus is close")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(tripName.hashCode(), notification) }
    }

    companion object {
        const val ALERT_MINUTES = 5
    }
}
