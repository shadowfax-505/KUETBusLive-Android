package io.github.shadowfax505.kuetbuslive.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import io.github.shadowfax505.kuetbuslive.MainActivity
import io.github.shadowfax505.kuetbuslive.R
import io.github.shadowfax505.kuetbuslive.data.WidgetFonts
import io.github.shadowfax505.kuetbuslive.model.BusSchedule
import io.github.shadowfax505.kuetbuslive.model.CrowdLevel
import io.github.shadowfax505.kuetbuslive.model.Timetable
import io.github.shadowfax505.kuetbuslive.model.UserRole

// The "Next bus" home screen widget, in the same look as the iOS widget.
// - small: a ring showing how far along the trip is, with the minutes left inside
// - medium: the next bus plus the trip track from KUET to my stop, with the bus on it
// - large: a big ring, the next bus, the track, and the two buses after it
// A live bus the app saw in the last 10 minutes comes first (ETA, distance, crowd);
// otherwise the timetable the app saved.
class NextBusWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val now = System.currentTimeMillis()
        val data = load(context, now)
        ids.forEach { id ->
            val options = manager.getAppWidgetOptions(id)
            manager.updateAppWidget(id, views(context, data, Size.of(options), Size.widthDp(options, DEFAULT_WIDTH_DP)))
        }
        scheduleRefresh(context, data, now)
    }

    // Resizing can move the widget to another size, so draw it again.
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        val data = load(context, System.currentTimeMillis())
        manager.updateAppWidget(id, views(context, data, Size.of(options), Size.widthDp(options, DEFAULT_WIDTH_DP)))
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TICK) {
            val manager = AppWidgetManager.getInstance(context)
            onUpdate(context, manager, manager.getAppWidgetIds(ComponentName(context, NextBusWidget::class.java)))
            return
        }
        super.onReceive(context, intent)
    }

    override fun onDisabled(context: Context) {
        alarmManager(context).cancel(tickIntent(context))
    }

    // ---------- What to show ----------

    // One departure today: from campus, or from the city towards KUET.
    private data class Departure(val schedule: BusSchedule, val time: String, val fromCampus: Boolean, val minutesAway: Int)

    private class Data(
        val signedIn: Boolean,
        val live: WidgetStore.LiveTrip?,
        val departures: List<Departure>,   // soonest first
        val running: Double?,               // how far along a timetable trip that already left is, 0..1
        val route: WidgetStore.RiderRoute?,
    ) {
        val progress: Double get() = live?.progress ?: running ?: 0.0
        val stopFractions: List<Double> get() = live?.stopFractions?.takeIf { it.isNotEmpty() } ?: route?.stopFractions.orEmpty()
    }

    private fun load(context: Context, now: Long): Data {
        val role = WidgetStore.role(context)
        val canRide = role != null && role != UserRole.DRIVER && role != UserRole.ADMIN
        val schedules = if (canRide) WidgetStore.schedules(context).filter { it.audience.canBoard(role!!) && Timetable.runsOn(it, now) } else emptyList()
        val departures = schedules.mapNotNull { s ->
            val campus = Timetable.minutesUntil(s.campusTime, now)
            val spot = Timetable.minutesUntil(s.spotTime, now)
            when {
                campus != null && (spot == null || campus <= spot) -> Departure(s, s.campusTime, true, campus)
                spot != null -> Departure(s, s.spotTime, false, spot)
                else -> null
            }
        }.sortedBy { it.minutesAway }
        // A trip that left campus in the last 40 minutes is probably still on the road.
        val nowMinute = Timetable.minuteOfDay(now)
        val running = schedules.mapNotNull { Timetable.minutes(it.campusTime) }
            .map { nowMinute - it }
            .filter { it in 0 until TRIP_MINUTES }
            .minOrNull()
            ?.let { it.toDouble() / TRIP_MINUTES }
        val live = if (canRide) WidgetStore.live(context)?.takeIf { it.isFresh(now) } else null
        return Data(role != null, live, departures, running, WidgetStore.route(context))
    }

    // ---------- Drawing ----------

    private enum class Size {
        SMALL, MEDIUM, LARGE;

        companion object {
            // The launcher gives the size in dp: the width in portrait, the height in portrait.
            fun of(options: Bundle?): Size {
                val width = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
                val height = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
                return when {
                    width in 1 until 220 -> SMALL
                    height >= 300 -> LARGE
                    width == 0 -> SMALL
                    else -> MEDIUM
                }
            }

            fun widthDp(options: Bundle?, fallback: Float): Float =
                options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)?.takeIf { it > 0 }?.toFloat() ?: fallback
        }
    }

    private fun views(context: Context, data: Data, size: Size, widthDp: Float = DEFAULT_WIDTH_DP): RemoteViews {
        val font = WidgetFonts.current(context)
        val layout = when (size) {
            Size.SMALL -> font.small
            Size.MEDIUM -> font.medium
            Size.LARGE -> font.large
        }
        val v = RemoteViews(context.packageName, layout)
        v.setTextViewText(R.id.widget_headline, context.getString(if (data.live != null) R.string.widget_arriving else R.string.widget_label))
        when (size) {
            Size.SMALL -> small(context, v, data)
            Size.MEDIUM -> {
                summary(context, v, data)
                track(context, v, data, widthDp - 32f)
            }
            Size.LARGE -> {
                v.setTextViewText(R.id.widget_route, data.route?.routeName.orEmpty())
                ring(context, v, data, 112f)
                summary(context, v, data)
                track(context, v, data, widthDp - 32f)
                later(v, data)
            }
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        v.setOnClickPendingIntent(R.id.widget_root, open)
        return v
    }

    private fun small(context: Context, v: RemoteViews, data: Data) {
        ring(context, v, data, 66f)
        val live = data.live
        val next = data.departures.firstOrNull()
        when {
            live != null -> {
                v.setTextViewText(R.id.widget_title, live.busTitle.substringBefore(" · "))
                v.setTextViewText(R.id.widget_subtitle, live.stopName)
            }
            next != null -> {
                v.setTextViewText(R.id.widget_title, next.schedule.tripName)
                v.setTextViewText(R.id.widget_subtitle, Timetable.display(next.time))
            }
            else -> {
                v.setTextViewText(R.id.widget_title, context.getString(if (data.signedIn) R.string.widget_no_more_buses else R.string.widget_sign_in))
                v.setTextViewText(R.id.widget_subtitle, "")
            }
        }
    }

    // The ring with minutes left inside: the live ETA, or until the next bus leaves.
    // Past 100 minutes it counts hours instead.
    private fun ring(context: Context, v: RemoteViews, data: Data, sizeDp: Float) {
        v.setImageViewBitmap(R.id.widget_ring, WidgetArt.ring(context, sizeDp, data.progress, if (sizeDp > 90) 7f else 6f))
        val minutes = data.live?.etaMinutes ?: data.departures.firstOrNull()?.minutesAway
        val (number, unit) = when {
            minutes == null -> "--" to R.string.widget_min
            minutes >= 100 -> "${minutes / 60}" to R.string.widget_hours
            else -> "$minutes" to R.string.widget_min
        }
        v.setTextViewText(R.id.widget_ring_number, number)
        v.setTextViewText(R.id.widget_ring_unit, context.getString(unit))
    }

    // The main line: the live bus and its ETA, or the next bus from the timetable and its time.
    private fun summary(context: Context, v: RemoteViews, data: Data) {
        val live = data.live
        val next = data.departures.firstOrNull()
        val bangla = context.resources.configuration.locales[0].language == "bn"
        val numberSp = WidgetFonts.current(context).numberSp
        when {
            live != null -> {
                v.setTextViewText(R.id.widget_title, live.busTitle)
                v.setTextViewText(R.id.widget_subtitle,
                    if (live.crowd == CrowdLevel.FULL.raw) context.getString(R.string.widget_crowded)
                    else context.getString(R.string.widget_on_the_way, live.stopName))
                v.setTextViewText(R.id.widget_number, "${live.etaMinutes}")
                v.setViewVisibility(R.id.widget_unit, View.VISIBLE)
                v.setTextViewText(R.id.widget_distance, context.getString(R.string.widget_km, "%.1f".format(live.distanceKm)))
                v.setViewVisibility(R.id.widget_distance, View.VISIBLE)
                v.setTextViewTextSize(R.id.widget_number, TypedValue.COMPLEX_UNIT_SP, numberSp)
            }
            next != null -> {
                v.setTextViewText(R.id.widget_title, next.schedule.title)
                val where = next.schedule.spotName(bangla)
                v.setTextViewText(R.id.widget_subtitle,
                    if (next.minutesAway < 60) context.getString(R.string.widget_in_minutes, next.minutesAway)
                    else context.getString(if (next.fromCampus) R.string.widget_from_campus else R.string.widget_from_spot, where))
                v.setTextViewText(R.id.widget_number, Timetable.display(next.time))
                v.setViewVisibility(R.id.widget_unit, View.GONE)
                v.setViewVisibility(R.id.widget_distance, View.GONE)
                // A clock time is wider than a number of minutes.
                v.setTextViewTextSize(R.id.widget_number, TypedValue.COMPLEX_UNIT_SP, numberSp * 0.72f)
            }
            else -> {
                v.setTextViewText(R.id.widget_title, context.getString(if (data.signedIn) R.string.widget_no_more_buses else R.string.widget_sign_in))
                v.setTextViewText(R.id.widget_subtitle, "")
                v.setTextViewText(R.id.widget_number, "")
                v.setViewVisibility(R.id.widget_unit, View.GONE)
                v.setViewVisibility(R.id.widget_distance, View.GONE)
            }
        }
    }

    private fun track(context: Context, v: RemoteViews, data: Data, widthDp: Float) {
        v.setImageViewBitmap(R.id.widget_track, WidgetArt.track(context, widthDp, data.progress, data.stopFractions))
        v.setImageViewBitmap(R.id.widget_bus, WidgetArt.bus(context, widthDp, data.progress, data.live?.teacher ?: false))
        v.setTextViewText(R.id.widget_stop, data.live?.stopName ?: data.route?.stopName ?: context.getString(R.string.widget_my_stop))
    }

    // The two buses after the next one, only while no bus is on its way.
    private fun later(v: RemoteViews, data: Data) {
        val rows = if (data.live == null) data.departures.drop(1).take(2) else emptyList()
        v.setViewVisibility(R.id.widget_later, if (rows.isEmpty()) View.GONE else View.VISIBLE)
        listOf(
            Triple(R.id.widget_later1, R.id.widget_later1_name, R.id.widget_later1_time),
            Triple(R.id.widget_later2, R.id.widget_later2_name, R.id.widget_later2_time),
        ).forEachIndexed { i, (row, name, time) ->
            val d = rows.getOrNull(i)
            v.setViewVisibility(row, if (d == null) View.GONE else View.VISIBLE)
            if (d != null) {
                v.setTextViewText(name, d.schedule.title)
                v.setTextViewText(time, Timetable.display(d.time))
            }
        }
    }

    // ---------- Refreshing ----------

    // Every 5 minutes while a bus is near or on its way, so the ring and minutes keep
    // moving; otherwise just after the next bus leaves, and at least every half hour.
    // An inexact alarm, so no special permission is needed and the phone may group it.
    private fun scheduleRefresh(context: Context, data: Data, now: Long) {
        val next = data.departures.firstOrNull()?.minutesAway
        val busy = data.live != null || data.running != null || (next != null && next < 100)
        val minutes = if (busy) 5 else ((next ?: 30) + 1).coerceIn(1, 30)
        alarmManager(context).set(AlarmManager.RTC, now + minutes * 60_000L, tickIntent(context))
    }

    companion object {
        // The timetable has no arrival times, so assume a typical trip length, as on iOS.
        private const val TRIP_MINUTES = 40

        // A 4 x 2 widget on most phones, used until the launcher says otherwise.
        private const val DEFAULT_WIDTH_DP = 320f

        // The widget as it looks now at one size, for the README screenshots.
        fun preview(context: Context, size: String, widthDp: Float): RemoteViews {
            val widget = NextBusWidget()
            val data = widget.load(context, System.currentTimeMillis())
            return widget.views(context, data, Size.valueOf(size.uppercase()), widthDp)
        }

        private const val ACTION_TICK = "io.github.shadowfax505.kuetbuslive.widget.TICK"

        private fun alarmManager(context: Context) = context.getSystemService(AlarmManager::class.java)

        private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 0, Intent(context, NextBusWidget::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
