package io.github.shadowfax505.kuetbuslive.campus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import io.github.shadowfax505.kuetbuslive.auth.AccountDeletion
import io.github.shadowfax505.kuetbuslive.auth.SessionViewModel
import io.github.shadowfax505.kuetbuslive.driver.TripLocationService
import io.github.shadowfax505.kuetbuslive.driver.TripTracker
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.shadowfax505.kuetbuslive.data.AppIconChoice
import io.github.shadowfax505.kuetbuslive.data.AppIcons
import io.github.shadowfax505.kuetbuslive.data.BusStyle
import io.github.shadowfax505.kuetbuslive.data.BusStyles
import io.github.shadowfax505.kuetbuslive.data.Prefs
import io.github.shadowfax505.kuetbuslive.rider.BusPreview
import io.github.shadowfax505.kuetbuslive.model.AppUser
import io.github.shadowfax505.kuetbuslive.ui.components.ActionRow
import io.github.shadowfax505.kuetbuslive.ui.components.AppIcon
import io.github.shadowfax505.kuetbuslive.ui.components.AppSwitch
import io.github.shadowfax505.kuetbuslive.ui.components.AppTextField
import io.github.shadowfax505.kuetbuslive.ui.components.Avatar
import io.github.shadowfax505.kuetbuslive.ui.components.Page
import io.github.shadowfax505.kuetbuslive.ui.components.Section
import io.github.shadowfax505.kuetbuslive.ui.components.SettingRow
import io.github.shadowfax505.kuetbuslive.ui.theme.Tile

@Composable
fun ProfileScreen(
    user: AppUser,
    campus: CampusViewModel,
    onPickStop: (() -> Unit)?,
    onOpenComplaints: () -> Unit,
    onOpenNews: (() -> Unit)?,
    onSignOut: () -> Unit,
    session: SessionViewModel,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val message by campus.message.collectAsStateWithLifecycle()
    var alerts by remember { mutableStateOf(prefs.arrivalAlerts) }
    var appIcon by remember { mutableStateOf(AppIcons.current(context)) }
    val busStyle = BusStyles.current(context)
    var editingName by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val sessionState by session.state.collectAsStateWithLifecycle()

    Page("Profile", onBack = onBack, message = message, onMessageShown = campus::clearMessage) {
        item {
            Section {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(user.name, size = 60.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(user.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${user.role.title} · ${user.role.idLabel} ${user.kuetId.orEmpty()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Section(header = "Account", separatorInset = 60.dp) {
                SettingRow(Icons.Rounded.Edit, "Name", null, Tile.Blue, value = user.name) { editingName = true }
                if (onPickStop != null) {
                    SettingRow(Icons.Rounded.Place, "My stop", null, Tile.Green, value = user.favoriteStopName ?: "Choose", onClick = onPickStop)
                }
                SettingRow(
                    Icons.Rounded.Badge, "Account check",
                    if (user.isApproved) "Verified KUET ${user.role.title.lowercase()}" else "Waiting for approval",
                    Tile.Indigo,
                )
            }
        }
        if (onPickStop != null) {
            item {
                Section(header = "Notifications", footer = "We tell you when your bus is about 5 minutes from your stop.", separatorInset = 60.dp) {
                    SettingRow(Icons.Rounded.NotificationsActive, "Arrival alerts", null, Tile.Red, trailing = {
                        AppSwitch(alerts) { alerts = it; prefs.arrivalAlerts = it }
                    })
                }
            }
        }
        item {
            Section(header = "App icon", footer = "Your home screen can take a few seconds to show the new icon.") {
                AppIconPicker(appIcon) { appIcon = it; AppIcons.set(context, it) }
            }
        }
        item {
            Section(header = "Bus on the trip bar", footer = "How the bus looks as it drives towards your stop.") {
                BusStylePicker(busStyle) { BusStyles.set(context, it) }
            }
        }
        item {
            Section(header = "Help", separatorInset = 60.dp) {
                if (onOpenNews != null) SettingRow(Icons.Rounded.Campaign, "News", "From the transport office", Tile.Pink, onClick = onOpenNews)
                SettingRow(Icons.Rounded.Feedback, "Complaints", "Send one and read replies", Tile.Orange, onClick = onOpenComplaints)
            }
        }
        item {
            Section(Modifier.padding(top = 20.dp)) {
                ActionRow("Sign Out", destructive = true) { confirmSignOut = true }
            }
        }
        item {
            Section(footer = "Removes your account and everything it saved, for good.") {
                ActionRow("Delete Account", destructive = true) { deleting = true }
            }
        }
    }

    if (deleting) {
        var password by remember { mutableStateOf("") }
        var problem by remember { mutableStateOf<String?>(null) }
        val onTrip = TripTracker.state.value.trip != null
        AlertDialog(
            onDismissRequest = { if (!sessionState.busy) deleting = false },
            title = { Text("Delete your account?") },
            text = {
                Column {
                    Text(AccountDeletion.explanation(user.role, onTrip), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    AppTextField(password, { password = it }, "Password", isPassword = true)
                    problem?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = password.isNotEmpty() && !sessionState.busy,
                    onClick = {
                        problem = null
                        session.deleteAccount(
                            password,
                            wavingAtBusId = prefs.wavingAtBusId,
                            beforeDeleting = {
                                // Stop sharing GPS first, so the bus isn't written again after it's removed.
                                if (TripTracker.state.value.trip != null) TripLocationService.end(context)
                            },
                            clearPhone = { prefs.clear() },
                        ) { result ->
                            if (result == null) deleting = false else problem = result
                        }
                    },
                ) { Text(if (sessionState.busy) "Deleting…" else "Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(enabled = !sessionState.busy, onClick = { deleting = false }) { Text("Cancel") } },
        )
    }

    if (editingName) {
        var name by remember { mutableStateOf(user.name) }
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text("Your name") },
            text = { AppTextField(name, { name = it.take(100) }, "Full name") },
            confirmButton = { TextButton(onClick = { campus.saveName(user, name); editingName = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text("Cancel") } },
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text("Sign Out", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BusStylePicker(selected: BusStyle, onPick: (BusStyle) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        BusStyle.entries.forEach { style ->
            val isSelected = style == selected
            Column(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClickLabel = "Show the bus ${style.title.lowercase()}") { onPick(style) }
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .border(
                            2.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            RoundedCornerShape(14.dp),
                        )
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    BusPreview(style)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    style.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AppIconPicker(selected: AppIconChoice, onPick: (AppIconChoice) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        AppIconChoice.entries.forEach { choice ->
            val isSelected = choice == selected
            Column(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClickLabel = "Use the ${choice.title} icon") { onPick(choice) }
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .border(
                            2.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            RoundedCornerShape(17.dp),
                        )
                        .padding(4.dp),
                ) {
                    AppIcon(size = 56.dp, background = choice.background, foreground = choice.foreground)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    choice.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
