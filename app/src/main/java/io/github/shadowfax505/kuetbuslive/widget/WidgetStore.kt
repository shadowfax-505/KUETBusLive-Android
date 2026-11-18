package io.github.shadowfax505.kuetbuslive.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import io.github.shadowfax505.kuetbuslive.model.BusAudience
import io.github.shadowfax505.kuetbuslive.model.BusSchedule
import io.github.shadowfax505.kuetbuslive.model.TripDays
import io.github.shadowfax505.kuetbuslive.model.TripPeriod
import io.github.shadowfax505.kuetbuslive.model.UserRole
import org.json.JSONArray
import org.json.JSONObject

// What the home screen widget shows, kept on this phone: the timetable and who is
// looking at it. The app saves it whenever the timetable loads, so the widget works
// without signing in again or reaching the network.
object WidgetStore {
    private const val FILE = "kuet_bus_widget"
    private const val SCHEDULES = "schedules"
    private const val ROLE = "role"

    fun save(context: Context, schedules: List<BusSchedule>, role: UserRole) {
        if (schedules.isEmpty()) return   // Firestore hasn't answered yet; keep what the widget has.
        val list = JSONArray()
        schedules.forEach { s ->
            list.put(
                JSONObject()
                    .put("id", s.id).put("tripName", s.tripName).put("audience", s.audience.raw)
                    .put("days", s.days.raw).put("period", s.period.raw).put("campusTime", s.campusTime)
                    .put("spot", s.spot).put("spotBn", s.spotBn ?: "").put("spotTime", s.spotTime)
                    .put("remarks", s.remarks)
            )
        }
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val json = list.toString()
        if (prefs.getString(SCHEDULES, null) == json && prefs.getString(ROLE, null) == role.raw) return
        prefs.edit().putString(SCHEDULES, json).putString(ROLE, role.raw).apply()
        refresh(context)
    }

    // Signing out or deleting the account leaves nothing behind on the home screen.
    fun clear(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
        refresh(context)
    }

    fun schedules(context: Context): List<BusSchedule> {
        val json = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(SCHEDULES, null) ?: return emptyList()
        return runCatching {
            val list = JSONArray(json)
            (0 until list.length()).map { i ->
                val o = list.getJSONObject(i)
                val tripName = o.getString("tripName")
                BusSchedule(
                    id = o.getString("id"),
                    tripName = tripName,
                    audience = o.optString("audience").takeIf { it.isNotBlank() }?.let { BusAudience.from(it) }
                        ?: BusAudience.fromTripName(tripName),
                    days = TripDays.from(o.optString("days")),
                    period = TripPeriod.from(o.optString("period")),
                    campusTime = o.optString("campusTime"),
                    spot = o.optString("spot"),
                    spotBn = o.optString("spotBn").takeIf { it.isNotBlank() },
                    spotTime = o.optString("spotTime"),
                    remarks = o.optString("remarks"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun role(context: Context): UserRole? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(ROLE, null)?.let { UserRole.from(it) }

    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, NextBusWidget::class.java))
        if (ids.isEmpty()) return
        context.sendBroadcast(
            Intent(context, NextBusWidget::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        )
    }
}
