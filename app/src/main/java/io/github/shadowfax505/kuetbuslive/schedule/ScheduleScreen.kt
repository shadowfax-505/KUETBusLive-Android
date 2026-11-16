package io.github.shadowfax505.kuetbuslive.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.shadowfax505.kuetbuslive.campus.CampusViewModel
import io.github.shadowfax505.kuetbuslive.model.AppUser
import io.github.shadowfax505.kuetbuslive.model.BusAudience
import io.github.shadowfax505.kuetbuslive.model.BusSchedule
import io.github.shadowfax505.kuetbuslive.model.Timetable
import io.github.shadowfax505.kuetbuslive.model.TripDays
import io.github.shadowfax505.kuetbuslive.model.TripPeriod
import io.github.shadowfax505.kuetbuslive.model.UserRole
import io.github.shadowfax505.kuetbuslive.ui.components.AppChip
import io.github.shadowfax505.kuetbuslive.ui.components.Chevron
import io.github.shadowfax505.kuetbuslive.ui.components.IconTile
import io.github.shadowfax505.kuetbuslive.ui.components.Section
import io.github.shadowfax505.kuetbuslive.ui.components.SegmentedControl
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material3.IconButton
import io.github.shadowfax505.kuetbuslive.ui.components.AppTextField
import io.github.shadowfax505.kuetbuslive.ui.components.AudienceBadge
import io.github.shadowfax505.kuetbuslive.ui.components.EmptyState
import io.github.shadowfax505.kuetbuslive.ui.components.Page
import io.github.shadowfax505.kuetbuslive.ui.components.Pill
import io.github.shadowfax505.kuetbuslive.ui.components.SectionTitle
import io.github.shadowfax505.kuetbuslive.ui.components.color
import io.github.shadowfax505.kuetbuslive.ui.theme.Brand

// The KUET timetable. Riders see the trips they may ride; the admin edits every trip.
@Composable
fun ScheduleScreen(user: AppUser, campus: CampusViewModel) {
    val schedules by campus.schedules.collectAsStateWithLifecycle()
    val message by campus.message.collectAsStateWithLifecycle()
    val isAdmin = user.role == UserRole.ADMIN
    val now = System.currentTimeMillis()
    val todayIsSaturday = Timetable.dhakaTime(now).dayOfWeek.value == 6
    var days by rememberSaveable { mutableStateOf(if (todayIsSaturday) TripDays.SATURDAY else TripDays.SUN_TO_THU) }
    var editing by remember { mutableStateOf<BusSchedule?>(null) }
    // Riders pin the trips they usually take ("My trips").
    val canPin = user.role == UserRole.STUDENT || user.role == UserRole.TEACHER
    val myTrips by campus.myTrips.collectAsStateWithLifecycle()
    var pinning by remember { mutableStateOf<BusSchedule?>(null) }
    val pinned = myTrips.mapNotNull { t -> schedules.firstOrNull { it.id == t.scheduleId }?.let { t to it } }
        .sortedBy { (t, s) -> Timetable.minutes(t.time(s)) ?: 0 }

    val visible = schedules
        .filter { it.days == days && (isAdmin || it.audience.canBoard(user.role)) }
        .sortedBy { Timetable.sortKey(it) }
    val next = Timetable.nextTrip(schedules, user.role, now)

    Page(
        title = "Schedule",
        subtitle = if (Timetable.dhakaTime(now).dayOfWeek.value == 5) "No buses on Friday" else "Official KUET timetable",
        message = message,
        onMessageShown = campus::clearMessage,
        actions = {
            if (isAdmin) {
                IconButton(onClick = { editing = blankSchedule(days) }) {
                    Icon(Icons.Rounded.Add, "Add trip", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                }
            }
        },
    ) {
        item {
            val options = TripDays.entries
            SegmentedControl(options.map { it.title }, options.indexOf(days), { days = options[it] }, Modifier.padding(bottom = 4.dp))
        }
        if (canPin && pinned.isNotEmpty()) {
            item(key = "my-trips") {
                Section(Modifier.padding(top = 12.dp), header = "My trips", separatorInset = 52.dp) {
                    pinned.forEach { (trip, schedule) -> MyTripRow(trip, schedule) { pinning = schedule } }
                }
            }
        }
        if (next != null && !isAdmin) {
            item { NextTripCard(next, now) }
        }
        if (visible.isEmpty()) {
            item { EmptyState(Icons.Rounded.CalendarMonth, "No trips yet", "The transport office hasn't added trips for these days.") }
        }
        TripPeriod.entries.forEach { period ->
            val trips = visible.filter { it.period == period }
            if (trips.isNotEmpty()) {
                item(key = "section-${period.raw}") {
                    Section(header = period.title, separatorInset = 104.dp) {
                        trips.forEach { trip ->
                            val onClick: (() -> Unit)? = when {
                                isAdmin -> ({ editing = trip })
                                canPin -> ({ pinning = trip })
                                else -> null
                            }
                            TripRow(trip, now, pinned = myTrips.any { it.scheduleId == trip.id }, onClick = onClick)
                        }
                    }
                }
            }
        }
    }

    pinning?.let { schedule ->
        MyTripDialog(
            schedule = schedule,
            existing = myTrips.firstOrNull { it.scheduleId == schedule.id },
            onDismiss = { pinning = null },
            onSave = { campus.saveMyTrip(it) { pinning = null } },
            onRemove = { campus.removeMyTrip(it) { pinning = null } },
        )
    }

    editing?.let { trip ->
        ScheduleEditDialog(
            initial = trip,
            onDismiss = { editing = null },
            onSave = { campus.saveSchedule(it) { editing = null } },
            onDelete = if (trip.id.isNotBlank()) ({ campus.deleteSchedule(trip.id); editing = null }) else null,
        )
    }
}

private fun blankSchedule(days: TripDays) = BusSchedule(
    id = "", tripName = "Normal-1", audience = BusAudience.STUDENT, days = days, period = TripPeriod.MORNING,
    campusTime = "", spot = "", spotBn = null, spotTime = "", remarks = "",
)

@Composable
private fun NextTripCard(trip: BusSchedule, now: Long) {
    val minutes = Timetable.minutesUntil(trip.campusTime, now)
    Section(Modifier.padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.DirectionsBus, Brand.Seats, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("YOUR NEXT BUS", style = MaterialTheme.typography.labelMedium, color = Brand.Seats, fontWeight = FontWeight.SemiBold)
                Text("${trip.tripName} · ${trip.spot}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Leaves KUET ${Timetable.display(trip.campusTime)} · ${trip.spot} ${Timetable.display(trip.spotTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            minutes?.let {
                Text(
                    io.github.shadowfax505.kuetbuslive.rider.formatMinutes(it),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun TripRow(trip: BusSchedule, now: Long, pinned: Boolean = false, onClick: (() -> Unit)?) {
    val upcoming = Timetable.isUpcoming(trip, now)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(76.dp)) {
            Text(Timetable.display(trip.campusTime), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("from KUET", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.padding(end = 12.dp).size(width = 3.dp, height = 40.dp).clip(RoundedCornerShape(2.dp)).background(trip.audience.color()))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(trip.tripName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                if (pinned) {
                    Icon(Icons.Rounded.PushPin, "In My trips", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp).size(14.dp))
                }
                Spacer(Modifier.width(8.dp))
                AudienceBadge(trip.audience)
            }
            Text(
                "${trip.spot} · leaves ${Timetable.display(trip.spotTime)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (trip.remarks.isNotBlank()) {
                Text(trip.remarks, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (upcoming) Pill("Today", Brand.Seats)
        if (onClick != null) Chevron()
    }
}

@Composable
private fun ScheduleEditDialog(
    initial: BusSchedule,
    onDismiss: () -> Unit,
    onSave: (BusSchedule) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var tripName by remember { mutableStateOf(initial.tripName) }
    var spot by remember { mutableStateOf(initial.spot) }
    var spotBn by remember { mutableStateOf(initial.spotBn.orEmpty()) }
    var campusTime by remember { mutableStateOf(initial.campusTime) }
    var spotTime by remember { mutableStateOf(initial.spotTime) }
    var remarks by remember { mutableStateOf(initial.remarks) }
    var days by remember { mutableStateOf(initial.days) }
    var period by remember { mutableStateOf(initial.period) }
    val timesValid = Timetable.isValidTime(campusTime) && Timetable.isValidTime(spotTime)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "New trip" else "Edit trip") },
        text = {
            Column(Modifier.verticalScrollSafe(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(tripName, { tripName = it }, "Trip name (Normal-1, Special-1 (AC))")
                Text(
                    "Allocated to: " + BusAudience.fromTripName(tripName).title.lowercase() + " (from the name)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TripDays.entries.forEach { AppChip(days == it, { days = it }, { Text(it.title) }) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TripPeriod.entries.forEach { AppChip(period == it, { period = it }, { Text(it.title) }) }
                }
                AppTextField(campusTime, { campusTime = it }, "Leaves KUET (HH:mm)")
                AppTextField(spot, { spot = it }, "Starting spot in the city")
                AppTextField(spotBn, { spotBn = it }, "Spot in Bangla (optional)")
                AppTextField(spotTime, { spotTime = it }, "Leaves the spot (HH:mm)")
                AppTextField(remarks, { remarks = it }, "Remarks", singleLine = false)
                if (!timesValid) {
                    Text("Times use 24-hour HH:mm, like 06:30 or 13:15.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                        Text("Delete this trip", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = timesValid,
                onClick = {
                    onSave(
                        initial.copy(
                            tripName = tripName.trim(),
                            audience = BusAudience.fromTripName(tripName),
                            days = days,
                            period = period,
                            campusTime = campusTime.trim(),
                            spot = spot.trim(),
                            spotBn = spotBn.trim().ifBlank { null },
                            spotTime = spotTime.trim(),
                            remarks = remarks.trim(),
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Modifier.verticalScrollSafe(): Modifier = this.then(Modifier.verticalScroll(rememberScrollState()))
