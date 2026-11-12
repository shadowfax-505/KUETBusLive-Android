package io.github.shadowfax505.kuetbuslive.campus

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.Feedback
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.shadowfax505.kuetbuslive.model.AppUser
import io.github.shadowfax505.kuetbuslive.model.Complaint
import io.github.shadowfax505.kuetbuslive.model.ComplaintCategory
import io.github.shadowfax505.kuetbuslive.model.ComplaintStatus
import io.github.shadowfax505.kuetbuslive.ui.components.AppTextField
import io.github.shadowfax505.kuetbuslive.ui.components.EmptyState
import io.github.shadowfax505.kuetbuslive.ui.components.Page
import io.github.shadowfax505.kuetbuslive.ui.components.Pill
import io.github.shadowfax505.kuetbuslive.ui.theme.Brand

fun ComplaintStatus.color() = when (this) {
    ComplaintStatus.OPEN -> Brand.Amber
    ComplaintStatus.IN_REVIEW -> Brand.StudentBus
    ComplaintStatus.RESOLVED -> Brand.Seats
}

// A rider's or driver's own complaints, with the office's replies.
@Composable
fun MyComplaintsScreen(user: AppUser, campus: CampusViewModel, onBack: () -> Unit) {
    val complaints by campus.myComplaints.collectAsStateWithLifecycle()
    val message by campus.message.collectAsStateWithLifecycle()
    var composing by remember { mutableStateOf(false) }
    Page(
        "Complaints", "Tell the transport office what went wrong", onBack = onBack,
        message = message, onMessageShown = campus::clearMessage,
        floating = {
            ExtendedFloatingActionButton(onClick = { composing = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("New complaint") })
        },
    ) {
        if (complaints.isEmpty()) {
            item { EmptyState(Icons.Rounded.Feedback, "Nothing sent yet", "Late buses, driver behaviour, overcrowding or an app problem: the office reads every one.") }
        }
        items(complaints, key = { it.id }) { complaint ->
            ComplaintCard(complaint, showSender = false)
            Spacer(Modifier.height(10.dp))
        }
    }
    if (composing) {
        NewComplaintDialog(onDismiss = { composing = false }) { category, bus, route, text ->
            campus.sendComplaint(user, category, bus, route, text) { composing = false }
        }
    }
}

@Composable
fun ComplaintCard(complaint: Complaint, showSender: Boolean, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(complaint.category.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (showSender && !complaint.readByAdmin) {
                Icon(Icons.Rounded.MarkEmailUnread, "Unread", tint = Brand.Coral)
                Spacer(Modifier.width(8.dp))
            }
            Pill(complaint.status.title, complaint.status.color())
        }
        Text(
            listOfNotNull(
                if (showSender) "${complaint.userName} (${complaint.userRole})" else null,
                complaint.busAndRoute,
                relativeTime(complaint.createdAtMillis),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(complaint.text, style = MaterialTheme.typography.bodyMedium)
        complaint.adminReply?.let { reply ->
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)).padding(12.dp),
            ) {
                Text("Transport office", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(reply, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun NewComplaintDialog(onDismiss: () -> Unit, onSend: (ComplaintCategory, String?, String?, String) -> Unit) {
    var category by remember { mutableStateOf(ComplaintCategory.LATE) }
    var bus by remember { mutableStateOf("") }
    var route by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New complaint") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ComplaintCategory.entries.forEach { FilterChip(category == it, { category = it }, { Text(it.title) }) }
                }
                AppTextField(bus, { bus = it }, "Bus (optional), e.g. Normal-2")
                AppTextField(route, { route = it }, "Route (optional)")
                AppTextField(
                    text, { text = it.take(Complaint.MAX_TEXT_LENGTH) }, "What happened?",
                    singleLine = false, minLines = 4,
                    supportingText = "${text.length} / ${Complaint.MAX_TEXT_LENGTH}",
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSend(category, bus, route, text) }, enabled = text.isNotBlank()) { Text("Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
