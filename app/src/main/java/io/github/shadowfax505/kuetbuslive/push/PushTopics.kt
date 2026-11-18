package io.github.shadowfax505.kuetbuslive.push

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import io.github.shadowfax505.kuetbuslive.model.UserRole

// Which route's "bus left campus" pushes this phone gets. The topic names are the
// same as on iOS (PushManager) and in functions/index.js: "route-<routeId>" for
// student buses, and "route-<routeId>-teachers" for teachers' buses, which only
// teachers join because students may not ride them.
object PushTopics {
    private const val FILE = "kuet_bus_push"
    private const val KEY = "topics"

    fun topics(routeId: String, role: UserRole): List<String> {
        val topic = "route-$routeId"
        return if (role == UserRole.TEACHER) listOf(topic, "$topic-teachers") else listOf(topic)
    }

    // Moves the phone to the route that has the rider's stop, leaving the old one.
    fun follow(context: Context, routeId: String?, role: UserRole) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val old = prefs.getStringSet(KEY, emptySet()) ?: emptySet()
        val new = routeId?.let { topics(it, role).toSet() } ?: emptySet()
        if (old == new) return
        val messaging = FirebaseMessaging.getInstance()
        (old - new).forEach { messaging.unsubscribeFromTopic(it) }
        (new - old).forEach { messaging.subscribeToTopic(it) }
        prefs.edit().putStringSet(KEY, new).apply()
    }

    // Signing out or deleting the account: no more pushes for that account's route.
    fun leave(context: Context) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val old = prefs.getStringSet(KEY, emptySet()) ?: emptySet()
        if (old.isEmpty()) return
        val messaging = FirebaseMessaging.getInstance()
        old.forEach { messaging.unsubscribeFromTopic(it) }
        prefs.edit().remove(KEY).apply()
    }
}
