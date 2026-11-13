package io.github.shadowfax505.kuetbuslive.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.shadowfax505.kuetbuslive.data.Repository
import io.github.shadowfax505.kuetbuslive.data.SpecialDays
import io.github.shadowfax505.kuetbuslive.model.SpecialDay
import io.github.shadowfax505.kuetbuslive.model.Timetable
import io.github.shadowfax505.kuetbuslive.ui.components.AppTextField
import io.github.shadowfax505.kuetbuslive.ui.components.EmptyState
import io.github.shadowfax505.kuetbuslive.ui.components.PrimaryButton
import io.github.shadowfax505.kuetbuslive.ui.components.Page
import io.github.shadowfax505.kuetbuslive.ui.theme.Brand
import kotlinx.coroutines.launch

// Exam days, holidays and events (specialDays/{yyyy-MM-dd}). Bus times on these days
// are learned apart, the same as in the iOS app. Only the admin can change them.
@Composable
fun SpecialDaysScreen(onBack: () -> Unit) {
    SpecialDays.start()
    val days by SpecialDays.days.collectAsStateWithLifecycle()
    val today = Timetable.dayString(System.currentTimeMillis())
    val upcoming = days.filter { it.id >= today }
    var adding by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Page("Special days", "Exams, holidays and events", onBack = onBack, message = message, onMessageShown = { message = null }) {
        item {
            Text(
                "Traffic and riders differ on these days, so bus times are learned separately.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Add a day", onClick = { adding = true }, icon = Icons.Rounded.Add)
            Spacer(Modifier.height(12.dp))
        }
        if (upcoming.isEmpty()) {
            item { EmptyState(Icons.Rounded.EventBusy, "No special days ahead", "Add exam weeks, holidays or events.") }
        }
        items(upcoming, key = { it.id }) { day ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${day.id} · ${SpecialDay.title(day.kind)}", style = MaterialTheme.typography.titleSmall)
                    if (day.note.isNotBlank()) {
                        Text(day.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = {
                    scope.launch {
                        message = runCatching { Repository.deleteSpecialDay(day.id) }.fold({ "Removed." }, { it.message ?: "Could not remove." })
                    }
                }) { Text("Remove", color = Brand.Danger) }
            }
        }
    }

    if (adding) {
        var date by remember { mutableStateOf(today) }
        var kind by remember { mutableStateOf(SpecialDay.KINDS.first()) }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Special day") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(date, { date = it.trim() }, "Date (yyyy-MM-dd)")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpecialDay.KINDS.forEach { k -> FilterChip(kind == k, { kind = k }, { Text(SpecialDay.title(k)) }) }
                    }
                    AppTextField(note, { note = it.take(100) }, "Note (optional)")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (Timetable.startOfDayMillis(date) == null || !Regex("""\d{4}-\d{2}-\d{2}""").matches(date)) {
                        message = "Dates look like 2026-12-01."
                    } else {
                        scope.launch {
                            message = runCatching { Repository.saveSpecialDay(date, kind, note.trim()) }
                                .fold({ "Saved." }, { it.message ?: "Could not save." })
                        }
                    }
                    adding = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
}
