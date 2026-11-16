package io.github.shadowfax505.kuetbuslive.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.shadowfax505.kuetbuslive.KuetBusApp
import io.github.shadowfax505.kuetbuslive.MainActivity
import io.github.shadowfax505.kuetbuslive.R

// With the app closed, Android shows the push by itself. With the app open, it
// arrives here and is shown the same way.
class DeparturePushService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val note = message.notification ?: return
        val allowed = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, KuetBusApp.CHANNEL_DEPARTURE)
            .setSmallIcon(R.drawable.ic_stat_bus)
            .setContentTitle(note.title)
            .setContentText(note.body)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(this).notify(message.messageId?.hashCode() ?: 3, notification)
    }

    // Topics follow the device, not the token, so a new token needs nothing saved.
    override fun onNewToken(token: String) = Unit
}
