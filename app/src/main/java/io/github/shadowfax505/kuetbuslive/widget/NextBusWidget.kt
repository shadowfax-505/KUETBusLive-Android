package io.github.shadowfax505.kuetbuslive.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import io.github.shadowfax505.kuetbuslive.MainActivity
import io.github.shadowfax505.kuetbuslive.R
import io.github.shadowfax505.kuetbuslive.model.BusSchedule
import io.github.shadowfax505.kuetbuslive.model.Timetable
import io.github.shadowfax505.kuetbuslive.model.UserRole

// The "Next bus" home screen widget, in the same look as the iOS widget: white with
// near-black text (dark grey with white text at night), the KUET mark, a small
// uppercase label, and the next trip's time in a large serif.
class NextBusWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val now = System.currentTimeMillis()
        val next = nextTrip(context, now)
        ids.forEach { manager.updateAppWidget(it, views(context, next, now)) }
        scheduleRefresh(context, next, now)
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

    private fun nextTrip(context: Context, now: Long): BusSchedule? {
        val role = WidgetStore.role(context) ?: return null
        if (role == UserRole.DRIVER || role == UserRole.ADMIN) return null
        return Timetable.nextTrip(WidgetStore.schedules(context), role, now)
    }

    private fun views(context: Context, next: BusSchedule?, now: Long): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_next_bus)
        val signedIn = WidgetStore.role(context) != null
        if (next == null) {
            views.setTextViewText(R.id.widget_time, "--")
            views.setTextViewText(R.id.widget_trip, context.getString(
                if (signedIn) R.string.widget_no_more_buses else R.string.widget_sign_in
            ))
            views.setViewVisibility(R.id.widget_detail, View.GONE)
        } else {
            val leavesCampus = Timetable.minutesUntil(next.campusTime, now) != null
            val time = if (leavesCampus) next.campusTime else next.spotTime
            views.setTextViewText(R.id.widget_time, Timetable.display(time))
            views.setTextViewText(R.id.widget_trip, next.tripName)
            views.setTextViewText(R.id.widget_detail, context.getString(
                if (leavesCampus) R.string.widget_from_campus else R.string.widget_from_spot,
                next.spotName(bangla = context.resources.configuration.locales[0].language == "bn"),
            ))
            views.setViewVisibility(R.id.widget_detail, View.VISIBLE)
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        return views
    }

    // Shows the following trip a minute after this one leaves, and checks again at
    // least every half hour (a new day, or a timetable change). An inexact alarm, so
    // no special permission is needed and the phone may group it with others.
    private fun scheduleRefresh(context: Context, next: BusSchedule?, now: Long) {
        val leaveMinutes = next?.let {
            Timetable.minutesUntil(it.campusTime, now) ?: Timetable.minutesUntil(it.spotTime, now)
        }
        val wait = ((leaveMinutes ?: 30) + 1).coerceAtMost(30) * 60_000L
        alarmManager(context).set(AlarmManager.RTC, now + wait, tickIntent(context))
    }

    companion object {
        private const val ACTION_TICK = "io.github.shadowfax505.kuetbuslive.widget.TICK"

        private fun alarmManager(context: Context) = context.getSystemService(AlarmManager::class.java)

        private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 0, Intent(context, NextBusWidget::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
