package io.github.shadowfax505.kuetbuslive.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import io.github.shadowfax505.kuetbuslive.model.BusAudience
import io.github.shadowfax505.kuetbuslive.model.BusRoute
import io.github.shadowfax505.kuetbuslive.model.BusSchedule
import io.github.shadowfax505.kuetbuslive.model.Geo
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
    private const val STOP = "stop"
    private const val LIVE = "live"
    private const val LIVE_SAVED_AT = "liveSavedAt"

    // The live bus the app saw last, for the widget's ETA and progress track.
    data class LiveTrip(
        val busTitle: String,       // "Normal-1 · Sonadanga"
        val stopName: String,       // the rider's stop
        val etaMinutes: Int,
        val distanceKm: Double,
        val crowd: String,          // "seats", "full" or "unknown"
        val progress: Double,       // 0..1 from KUET to the rider's stop
        val stopFractions: List<Double>,
        val teacher: Boolean,
        val updatedAt: Long,
    ) {
        // Older than 10 minutes, the app has probably stopped hearing from the bus.
        fun isFresh(now: Long) = now - updatedAt < 10 * 60_000L
    }

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

    // The rider's stop and its route, for the end of the track and the stop dots on it.
    data class RiderRoute(val stopName: String, val routeName: String?, val stopFractions: List<Double>)

    fun saveRoute(context: Context, route: RiderRoute?) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val json = route?.let {
            JSONObject().put("stop", it.stopName).put("route", it.routeName ?: "")
                .put("stops", JSONArray(it.stopFractions)).toString()
        }
        if (prefs.getString(STOP, null) == json) return
        prefs.edit().putString(STOP, json).apply()
        refresh(context)
    }

    fun route(context: Context): RiderRoute? {
        val json = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(STOP, null) ?: return null
        return runCatching {
            val o = JSONObject(json)
            RiderRoute(o.getString("stop"), o.optString("route").takeIf { it.isNotBlank() }, doubles(o.optJSONArray("stops")))
        }.getOrNull()
    }

    // Where the stops between KUET and the rider's stop sit on the track, 0..1, by
    // straight-line distance between stops as on iOS.
    fun stopFractions(route: BusRoute, stopName: String): List<Double> {
        val end = route.stops.indexOfFirst { it.name == stopName }
        if (end <= 0) return emptyList()
        val legs = (1..end).map { Geo.distanceMetres(route.stops[it - 1].position, route.stops[it].position) }
        val total = legs.sum().takeIf { it > 0 } ?: return emptyList()
        return legs.runningReduce { a, b -> a + b }.dropLast(1).map { it / total }
    }

    private fun doubles(a: JSONArray?): List<Double> = a?.let { (0 until it.length()).map { i -> it.getDouble(i) } } ?: emptyList()

    // A new ETA for a running bus. Keeps the bus that reaches the rider first, and
    // redraws the widget at most once a minute to spare the battery.
    fun saveLive(context: Context, trip: LiveTrip) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val current = live(context)
        if (current != null && current.isFresh(trip.updatedAt) && current.busTitle != trip.busTitle &&
            current.etaMinutes < trip.etaMinutes
        ) return
        val json = JSONObject()
            .put("busTitle", trip.busTitle).put("stopName", trip.stopName).put("eta", trip.etaMinutes)
            .put("km", trip.distanceKm).put("crowd", trip.crowd).put("progress", trip.progress)
            .put("stops", JSONArray(trip.stopFractions)).put("teacher", trip.teacher).put("updatedAt", trip.updatedAt)
        val lastDraw = prefs.getLong(LIVE_SAVED_AT, 0)
        val redraw = trip.updatedAt - lastDraw >= 60_000L || current?.busTitle != trip.busTitle
        prefs.edit().putString(LIVE, json.toString()).apply {
            if (redraw) putLong(LIVE_SAVED_AT, trip.updatedAt)
        }.apply()
        if (redraw) refresh(context)
    }

    fun live(context: Context): LiveTrip? {
        val json = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(LIVE, null) ?: return null
        return runCatching {
            val o = JSONObject(json)
            LiveTrip(
                busTitle = o.getString("busTitle"),
                stopName = o.optString("stopName"),
                etaMinutes = o.getInt("eta"),
                distanceKm = o.optDouble("km", 0.0),
                crowd = o.optString("crowd", "unknown"),
                progress = o.optDouble("progress", 0.0),
                stopFractions = doubles(o.optJSONArray("stops")),
                teacher = o.optBoolean("teacher"),
                updatedAt = o.getLong("updatedAt"),
            )
        }.getOrNull()
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
