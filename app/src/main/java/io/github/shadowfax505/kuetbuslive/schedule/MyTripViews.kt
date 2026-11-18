package io.github.shadowfax505.kuetbuslive.schedule

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.shadowfax505.kuetbuslive.model.BusSchedule
import io.github.shadowfax505.kuetbuslive.model.MyTrip
import io.github.shadowfax505.kuetbuslive.model.Timetable
import io.github.shadowfax505.kuetbuslive.ui.components.AppChip

// One pinned trip in the "My trips" section of the Schedule screen.
@Composable
fun MyTripRow(trip: MyTrip, schedule: BusSchedule, onClick: () -> Unit) {
    val time = trip.time(schedule)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PushPin, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(schedule.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                if (trip.leg == MyTrip.Leg.CAMPUS) "Leaves KUET ${Timetable.display(time)}"
                else "Leaves ${schedule.spot} ${Timetable.display(time)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                trip.daysText + " · " + (if (trip.remindMinutes > 0) "reminder ${trip.remindMinutes} min before" else "no reminder"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Pin a trip, or change or remove a pin: which departure, which days, and the reminder.
@Composable
fun MyTripDialog(
    schedule: BusSchedule,
    existing: MyTrip?,
    onDismiss: () -> Unit,
    onSave: (MyTrip) -> Unit,
    onRemove: (MyTrip) -> Unit,
) {
    val context = LocalContext.current
    var trip by remember { mutableStateOf(existing ?: MyTrip.new(schedule)) }
    val running = MyTrip.dayCodes(schedule.days)
    // Android 13 and later ask before showing notifications.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onSave(trip) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add to My trips" else "My trip") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(schedule.title, style = MaterialTheme.typography.titleMedium)
                Label("I take it")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    AppChip(trip.leg == MyTrip.Leg.CAMPUS, { trip = trip.copy(leg = MyTrip.Leg.CAMPUS) },
                        { Text("From KUET, ${Timetable.display(schedule.campusTime)}") })
                    AppChip(trip.leg == MyTrip.Leg.SPOT, { trip = trip.copy(leg = MyTrip.Leg.SPOT) },
                        { Text("From ${schedule.spot}, ${Timetable.display(schedule.spotTime)}") })
                }
                if (running.size > 1) {
                    Label("On these days")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        running.forEach { code ->
                            val on = code in trip.days
                            AppChip(on, {
                                trip = trip.copy(days = MyTrip.DAY_CODES.filter { d -> if (d == code) !on else d in trip.days })
                            }, { Text(code.replaceFirstChar(Char::uppercase)) })
                        }
                    }
                }
                Label("Remind me")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    MyTrip.REMINDER_CHOICES.forEach { minutes ->
                        AppChip(trip.remindMinutes == minutes, { trip = trip.copy(remindMinutes = minutes) },
                            { Text(if (minutes == 0) "Off" else "$minutes min") })
                    }
                }
                Text(
                    "Reminders come from this phone, every week on the days you picked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { addToCalendar(context, schedule, trip) }) {
                    Icon(Icons.Rounded.EventAvailable, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add to Calendar")
                }
                if (existing != null) {
                    TextButton(onClick = { onRemove(existing) }) {
                        Text("Remove from My trips", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = trip.days.isNotEmpty(), onClick = {
                val needsAsk = trip.remindMinutes > 0 && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                if (needsAsk) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) else onSave(trip)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// Opens the phone's calendar app with a weekly event already filled in, so the app
// never needs access to the calendar.
fun addToCalendar(context: Context, schedule: BusSchedule, trip: MyTrip, now: Long = System.currentTimeMillis()) {
    val time = trip.time(schedule)
    val start = MyTrip.nextDeparture(time, trip.days, now) ?: now
    val byDay = MyTrip.DAY_CODES.filter { it in trip.days }.joinToString(",") { it.take(2).uppercase() }
    val fromCampus = trip.leg == MyTrip.Leg.CAMPUS
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, if (fromCampus) "${schedule.tripName} from KUET" else "${schedule.tripName} from ${schedule.spot}")
        .putExtra(CalendarContract.Events.EVENT_LOCATION, if (fromCampus) "KUET" else schedule.spot)
        .putExtra(CalendarContract.Events.DESCRIPTION, "KUET Bus Live: ${schedule.title}")
        .putExtra(CalendarContract.Events.EVENT_TIMEZONE, Timetable.dhaka.id)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 30 * 60_000L)
        .apply { if (byDay.isNotEmpty()) putExtra(CalendarContract.Events.RRULE, "FREQ=WEEKLY;BYDAY=$byDay") }
    runCatching { context.startActivity(intent) }
}
