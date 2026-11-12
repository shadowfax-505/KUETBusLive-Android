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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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

    val visible = schedules
        .filter { it.days == days && (isAdmin || it.audience.canBoard(user.role)) }
        .sortedBy { Timetable.sortKey(it) }
    val next = Timetable.nextTrip(schedules, user.role, now)

    Page(
        title = "Schedule",
        subtitle = if (Timetable.dhakaTime(now).dayOfWeek.value == 5) "No buses on Friday" else "Official KUET timetable",
        message = message,
        onMessageShown = campus::clearMessage,
        floating = {
            if (isAdmin) {
                ExtendedFloatingActionButton(
                    onClick = { editing = blankSchedule(days) },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("Add trip") },
                )
            }
        },
    ) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TripDays.entries.forEach { option ->
                    FilterChip(selected = days == option, onClick = { days = option }, label = { Text(option.title) }, shape = RoundedCornerShape(14.dp))
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
                item(key = "header-${period.raw}") { SectionTitle(period.title) }
                items(trips, key = { it.id }) { trip ->
                    TripRow(trip, now, onClick = if (isAdmin) ({ editing = trip }) else null)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
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
    Column(
        Modifier
            .padding(top = 12.dp, bottom = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(io.github.shadowfax505.kuetbuslive.ui.components.BrandGradient)
            .padding(20.dp),
    ) {
        Text("YOUR NEXT BUS", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
        Spacer(Modifier.height(6.dp))
        Text("${trip.tripName} · ${trip.spot}", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(
            "Leaves KUET at ${Timetable.display(trip.campusTime)}" + (minutes?.let { " · in ${io.github.shadowfax505.kuetbuslive.rider.formatMinutes(it)}" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f),
        )
        Text(
            "Leaves ${trip.spot} at ${Timetable.display(trip.spotTime)}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f),
        )
    }
}

@Composable
private fun TripRow(trip: BusSchedule, now: Long, onClick: (() -> Unit)?) {
    val upcoming = Timetable.isUpcoming(trip, now)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
            Text(Timetable.display(trip.campusTime), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("from KUET", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.padding(horizontal = 12.dp).size(width = 3.dp, height = 44.dp).clip(RoundedCornerShape(2.dp)).background(trip.audience.color()))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(trip.tripName, style = MaterialTheme.typography.titleSmall)
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
                    TripDays.entries.forEach { FilterChip(days == it, { days = it }, { Text(it.title) }) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TripPeriod.entries.forEach { FilterChip(period == it, { period = it }, { Text(it.title) }) }
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
