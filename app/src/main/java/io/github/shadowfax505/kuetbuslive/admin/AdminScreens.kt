package io.github.shadowfax505.kuetbuslive.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.shadowfax505.kuetbuslive.campus.CampusViewModel
import io.github.shadowfax505.kuetbuslive.campus.ComplaintCard
import io.github.shadowfax505.kuetbuslive.model.AbsenceStatus
import io.github.shadowfax505.kuetbuslive.model.AppUser
import io.github.shadowfax505.kuetbuslive.model.BusAssignment
import io.github.shadowfax505.kuetbuslive.model.Complaint
import io.github.shadowfax505.kuetbuslive.model.ComplaintStatus
import io.github.shadowfax505.kuetbuslive.model.Timetable
import io.github.shadowfax505.kuetbuslive.ui.components.AppTextField
import io.github.shadowfax505.kuetbuslive.ui.components.EmptyState
import io.github.shadowfax505.kuetbuslive.ui.components.IconBubble
import io.github.shadowfax505.kuetbuslive.ui.components.Page
import io.github.shadowfax505.kuetbuslive.ui.components.Pill
import io.github.shadowfax505.kuetbuslive.ui.components.SectionTitle
import io.github.shadowfax505.kuetbuslive.ui.theme.Brand

// Teachers and drivers waiting for the office, and the drivers who can be turned off.
@Composable
fun ApprovalsScreen(vm: AdminViewModel) {
    val pending by vm.pending.collectAsStateWithLifecycle()
    val staff by vm.staff.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    Page("Approvals", "Check every teacher and driver", message = message, onMessageShown = vm::clearMessage) {
        item { SectionTitle("Waiting (${pending.size})") }
        if (pending.isEmpty()) {
            item { EmptyState(Icons.Rounded.HowToReg, "All caught up", "New teacher and driver sign-ups appear here.") }
        }
        items(pending, key = { "p-" + it.id }) { user ->
            PersonCard(user) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { vm.approve(user, true) }) { Text("Approve") }
                    OutlinedButton(onClick = { vm.setActive(user, false) }) { Text("Decline") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        val approved = staff.filter { it.isApproved }
        if (approved.isNotEmpty()) item { SectionTitle("Approved (${approved.size})", Modifier.padding(top = 12.dp)) }
        items(approved, key = { "a-" + it.id }) { user ->
            PersonCard(user) {
                if (user.active) {
                    TextButton(onClick = { vm.setActive(user, false) }) { Text("Deactivate", color = Brand.Coral) }
                } else {
                    TextButton(onClick = { vm.setActive(user, true) }) { Text("Reactivate") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PersonCard(user: AppUser, actions: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.Person, if (user.role.raw == "driver") Brand.Amber else Brand.TeacherBus, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(user.name, style = MaterialTheme.typography.titleSmall)
                Text("${user.role.title} · ${user.role.idLabel} ${user.kuetId.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!user.active) Pill("Inactive", Brand.Unknown)
        }
        Spacer(Modifier.height(8.dp))
        actions()
    }
}

// Drivers & buses: the regular driver of each bus, temporary covers, and absence requests.
@Composable
fun FleetScreen(admin: AppUser, vm: AdminViewModel, onBack: () -> Unit) {
    val fleet by vm.fleet.collectAsStateWithLifecycle()
    val drivers by vm.drivers.collectAsStateWithLifecycle()
    val absences by vm.absences.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<FleetRow?>(null) }
    var covering by remember { mutableStateOf<io.github.shadowfax505.kuetbuslive.model.DriverAbsence?>(null) }
    val now = System.currentTimeMillis()
    val nameOf = { id: String? -> drivers.firstOrNull { it.id == id }?.name ?: if (id == null) "Nobody" else "Unknown driver" }

    Page("Drivers & buses", "Who drives each bus today", onBack = onBack, message = message, onMessageShown = vm::clearMessage) {
        val open = absences.filter { it.status == AbsenceStatus.OPEN }
        if (open.isNotEmpty()) {
            item { SectionTitle("Can't drive (${open.size})") }
            items(open, key = { "abs-" + it.id }) { absence ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brand.Amber.copy(alpha = 0.15f)).padding(16.dp)) {
                    Text(absence.driverName, style = MaterialTheme.typography.titleSmall)
                    Text("${absence.fromDate} – ${absence.untilDate} · ${absence.reason}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { covering = absence }) { Text("Arrange cover") }
                        TextButton(onClick = { vm.cancelAbsence(absence) }) { Text("Cancel") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        item { SectionTitle("Buses", Modifier.padding(top = 8.dp)) }
        if (fleet.isEmpty()) {
            item { EmptyState(Icons.Rounded.DirectionsBus, "No buses yet", "Buses appear here from the trip names in the timetable.") }
        }
        items(fleet, key = { it.id }) { row ->
            val today = row.bus?.let { BusAssignment.driverFor(it, now) }
            val coverActive = BusAssignment.isCoverActive(row.bus?.cover, now)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)
                    .clickable { editing = row }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBubble(Icons.Rounded.DirectionsBus, if (today == null) Brand.Unknown else MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.name, style = MaterialTheme.typography.titleMedium)
                    Text("Regular: ${nameOf(row.bus?.defaultDriverId)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    row.bus?.cover?.let { c ->
                        Text("Cover: ${nameOf(c.driverId)}, ${c.fromDate} – ${c.untilDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Pill(if (today == null) "No driver today" else if (coverActive) "Cover today" else "Ready", if (today == null) Brand.Coral else Brand.Seats)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    editing?.let { row ->
        var regular by remember(row.id) { mutableStateOf(row.bus?.defaultDriverId) }
        var coverDriver by remember(row.id) { mutableStateOf<String?>(null) }
        val today = Timetable.dayString(now)
        var from by remember(row.id) { mutableStateOf(today) }
        var until by remember(row.id) { mutableStateOf(today) }
        var reason by remember(row.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(row.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Regular driver", style = MaterialTheme.typography.titleSmall)
                    DriverChips(drivers, regular, allowNobody = true) { regular = it }
                    TextButton(onClick = { vm.setRegularDriver(row, regular); editing = null }) { Text("Save regular driver") }
                    Text("Temporary cover", style = MaterialTheme.typography.titleSmall)
                    row.bus?.cover?.let { c ->
                        Text("Now: ${nameOf(c.driverId)}, ${c.fromDate} – ${c.untilDate}", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { vm.clearCover(row); editing = null }) { Text("Remove cover", color = Brand.Coral) }
                    }
                    DriverChips(drivers, coverDriver, allowNobody = false) { coverDriver = it }
                    AppTextField(from, { from = it }, "First day (yyyy-MM-dd)")
                    AppTextField(until, { until = it }, "Last day (yyyy-MM-dd)")
                    AppTextField(reason, { reason = it }, "Reason")
                }
            },
            confirmButton = {
                TextButton(enabled = coverDriver != null, onClick = {
                    coverDriver?.let { vm.setCover(row, it, from.trim(), until.trim(), reason, admin.id) }
                    editing = null
                }) { Text("Save cover") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Close") } },
        )
    }

    covering?.let { absence ->
        var pick by remember(absence.id) { mutableStateOf<String?>(null) }
        val free = BusAssignment.freeDrivers(
            drivers, fleet.mapNotNull { it.bus }, setOf(absence.driverId),
            Timetable.startOfDayMillis(absence.fromDate) ?: now,
        )
        AlertDialog(
            onDismissRequest = { covering = null },
            title = { Text("Cover for ${absence.driverName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${absence.fromDate} – ${absence.untilDate}", style = MaterialTheme.typography.bodyMedium)
                    if (free.isEmpty()) Text("Every approved driver already drives a bus that day.", style = MaterialTheme.typography.bodySmall)
                    DriverChips(free, pick, allowNobody = false) { pick = it }
                }
            },
            confirmButton = {
                TextButton(enabled = pick != null, onClick = { pick?.let { vm.coverAbsence(absence, it, admin.id) }; covering = null }) { Text("Arrange") }
            },
            dismissButton = { TextButton(onClick = { covering = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun DriverChips(drivers: List<AppUser>, selected: String?, allowNobody: Boolean, onSelect: (String?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (allowNobody) FilterChip(selected == null, { onSelect(null) }, { Text("Nobody") })
        drivers.forEach { d -> FilterChip(selected == d.id, { onSelect(d.id) }, { Text(d.name) }) }
    }
}

// Every complaint, newest first. Opening one marks it read; the admin sets the status and replies.
@Composable
fun ComplaintsInboxScreen(vm: AdminViewModel, campus: CampusViewModel, onBack: () -> Unit) {
    val complaints by vm.complaints.collectAsStateWithLifecycle()
    val message by campus.message.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<Complaint?>(null) }
    var filter by remember { mutableStateOf<ComplaintStatus?>(ComplaintStatus.OPEN) }
    val shown = complaints.filter { filter == null || it.status == filter }
    Page("Complaints", "${complaints.count { !it.readByAdmin }} unread", onBack = onBack, message = message, onMessageShown = campus::clearMessage) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(filter == null, { filter = null }, { Text("All") })
                ComplaintStatus.entries.forEach { s -> FilterChip(filter == s, { filter = s }, { Text(s.title) }) }
            }
        }
        if (shown.isEmpty()) item { EmptyState(Icons.Rounded.Feedback, "Nothing here", "Complaints from riders and drivers appear here.") }
        items(shown, key = { it.id }) { complaint ->
            ComplaintCard(complaint, showSender = true) {
                if (!complaint.readByAdmin) campus.markComplaintRead(complaint.id)
                open = complaint
            }
            Spacer(Modifier.height(10.dp))
        }
    }
    open?.let { complaint ->
        var status by remember(complaint.id) { mutableStateOf(if (complaint.status == ComplaintStatus.OPEN) ComplaintStatus.IN_REVIEW else complaint.status) }
        var reply by remember(complaint.id) { mutableStateOf(complaint.adminReply.orEmpty()) }
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(complaint.category.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${complaint.userName} (${complaint.userRole})", style = MaterialTheme.typography.labelLarge)
                    Text(complaint.text, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ComplaintStatus.entries.forEach { s -> FilterChip(status == s, { status = s }, { Text(s.title) }) }
                    }
                    AppTextField(reply, { reply = it }, "Reply (the sender sees it)", singleLine = false, minLines = 3)
                }
            },
            confirmButton = { TextButton(onClick = { campus.replyToComplaint(complaint.id, status, reply) { open = null } }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { open = null }) { Text("Close") } },
        )
    }
}
