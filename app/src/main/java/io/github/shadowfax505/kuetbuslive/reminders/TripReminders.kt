package io.github.shadowfax505.kuetbuslive.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.shadowfax505.kuetbuslive.KuetBusApp
import io.github.shadowfax505.kuetbuslive.MainActivity
import io.github.shadowfax505.kuetbuslive.R
import io.github.shadowfax505.kuetbuslive.model.BusSchedule
import io.github.shadowfax505.kuetbuslive.model.MyTrip
import io.github.shadowfax505.kuetbuslive.model.Timetable
import org.json.JSONArray
import org.json.JSONObject

// Weekly reminders for "My trips", kept on this phone like on iOS. The pins and their
// times are saved here, and one alarm waits for the next reminder due; when it rings,
// the reminder is shown and the alarm moves to the one after. The alarm is inexact
// (within a minute), so no "exact alarm" permission is needed.
object TripReminders {
    private const val FILE = "kuet_bus_reminders"
    private const val ENTRIES = "entries"
    private const val DUE = "due"
    private const val WINDOW_MILLIS = 60_000L

    data class Entry(val key: String, val title: String, val body: String, val time: String,
                     val days: List<String>, val minutesBefore: Int) {
        // The next reminder after `now`.
        fun nextAt(now: Long): Long? {
            val before = minutesBefore * 60_000L
            return MyTrip.nextDeparture(time, days, now + before)?.minus(before)
        }
    }

    fun entries(trips: List<MyTrip>, schedules: List<BusSchedule>): List<Entry> =
        trips.filter { it.remindMinutes > 0 }.mapNotNull { trip ->
            val schedule = schedules.firstOrNull { it.id == trip.scheduleId } ?: return@mapNotNull null
            val time = trip.time(schedule)
            val body = if (trip.leg == MyTrip.Leg.CAMPUS) {
                "Leaves KUET at ${Timetable.display(time)}, in ${trip.remindMinutes} min."
            } else {
                "Leaves ${schedule.spot} at ${Timetable.display(time)}, in ${trip.remindMinutes} min."
            }
            Entry(trip.scheduleId, schedule.title, body, time, trip.days, trip.remindMinutes)
        }

    // Pins or the timetable changed. An empty timetable means it hasn't loaded yet.
    fun update(context: Context, trips: List<MyTrip>, schedules: List<BusSchedule>) {
        if (schedules.isEmpty()) return
        save(context, entries(trips, schedules))
        arm(context, System.currentTimeMillis())
    }

    // Signing out or deleting the account: no reminders for that account.
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        alarms(context).cancel(alarmIntent(context))
    }

    fun arm(context: Context, now: Long) {
        val entries = load(context)
        val next = entries.mapNotNull { e -> e.nextAt(now)?.let { e to it } }
        val at = next.minOfOrNull { it.second }
        if (at == null) {
            alarms(context).cancel(alarmIntent(context))
            prefs(context).edit().remove(DUE).apply()
            return
        }
        val due = next.filter { it.second == at }.map { it.first.key }
        prefs(context).edit().putString(DUE, JSONObject().put("at", at).put("keys", JSONArray(due)).toString()).apply()
        alarms(context).setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MILLIS, alarmIntent(context))
    }

    internal fun ring(context: Context) {
        val due = prefs(context).getString(DUE, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        val at = due?.optLong("at") ?: 0L
        val keys = due?.optJSONArray("keys")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) {
            load(context).filter { it.key in keys }.forEach { e ->
                val open = PendingIntent.getActivity(
                    context, 4, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE,
                )
                manager.notify(
                    e.key.hashCode(),
                    NotificationCompat.Builder(context, KuetBusApp.CHANNEL_REMINDER)
                        .setSmallIcon(R.drawable.ic_stat_bus)
                        .setContentTitle(e.title)
                        .setContentText(e.body)
                        .setAutoCancel(true)
                        .setContentIntent(open)
                        .build(),
                )
            }
        }
        arm(context, maxOf(at, System.currentTimeMillis()) + 1)
    }

    private fun save(context: Context, entries: List<Entry>) {
        val list = JSONArray()
        entries.forEach { e ->
            list.put(JSONObject().put("key", e.key).put("title", e.title).put("body", e.body).put("time", e.time)
                .put("days", JSONArray(e.days)).put("minutesBefore", e.minutesBefore))
        }
        prefs(context).edit().putString(ENTRIES, list.toString()).apply()
    }

    private fun load(context: Context): List<Entry> {
        val json = prefs(context).getString(ENTRIES, null) ?: return emptyList()
        return runCatching {
            val list = JSONArray(json)
            (0 until list.length()).map { i ->
                val o = list.getJSONObject(i)
                val days = o.getJSONArray("days")
                Entry(o.getString("key"), o.getString("title"), o.getString("body"), o.getString("time"),
                    (0 until days.length()).map { days.getString(it) }, o.getInt("minutesBefore"))
            }
        }.getOrDefault(emptyList())
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun alarms(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, TripReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

// The alarm rang, or the phone restarted (alarms don't survive a restart).
class TripReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            TripReminders.arm(context, System.currentTimeMillis())
        } else {
            TripReminders.ring(context)
        }
    }
}
