package io.github.shadowfax505.kuetbuslive.campus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.shadowfax505.kuetbuslive.data.Prefs
import io.github.shadowfax505.kuetbuslive.model.AppUser
import io.github.shadowfax505.kuetbuslive.ui.components.AppTextField
import io.github.shadowfax505.kuetbuslive.ui.components.Avatar
import io.github.shadowfax505.kuetbuslive.ui.components.BrandGradient
import io.github.shadowfax505.kuetbuslive.ui.components.Page
import io.github.shadowfax505.kuetbuslive.ui.components.SectionTitle
import io.github.shadowfax505.kuetbuslive.ui.theme.Brand

@Composable
fun ProfileScreen(
    user: AppUser,
    campus: CampusViewModel,
    onPickStop: (() -> Unit)?,
    onOpenComplaints: () -> Unit,
    onOpenNews: (() -> Unit)?,
    onSignOut: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val message by campus.message.collectAsStateWithLifecycle()
    var alerts by remember { mutableStateOf(prefs.arrivalAlerts) }
    var editingName by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    Page("Profile", onBack = onBack, message = message, onMessageShown = campus::clearMessage) {
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(BrandGradient).padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(user.name, size = 60.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(user.name, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(user.email, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                    Text(
                        "${user.role.title} · ${user.role.idLabel} ${user.kuetId.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
        item { SectionTitle("Account", Modifier.padding(top = 12.dp)) }
        item { SettingRow(Icons.Rounded.Edit, "Change name", user.name) { editingName = true } }
        if (onPickStop != null) {
            item { SettingRow(Icons.Rounded.Place, "My stop", user.favoriteStopName ?: "Not chosen yet", onClick = onPickStop) }
            item {
                SettingRow(Icons.Rounded.NotificationsActive, "Arrival alerts", "Tell me when my bus is 5 min away", trailing = {
                    Switch(checked = alerts, onCheckedChange = { alerts = it; prefs.arrivalAlerts = it })
                })
            }
        }
        item { SectionTitle("Help", Modifier.padding(top = 12.dp)) }
        if (onOpenNews != null) item { SettingRow(Icons.Rounded.Campaign, "News", "From the transport office", onClick = onOpenNews) }
        item { SettingRow(Icons.Rounded.Feedback, "Complaints", "Send one and read replies", onClick = onOpenComplaints) }
        item { SettingRow(Icons.Rounded.Badge, "Account check", if (user.isApproved) "Verified KUET ${user.role.title.lowercase()}" else "Waiting for approval") }
        item { Spacer(Modifier.height(12.dp)) }
        item { SettingRow(Icons.AutoMirrored.Rounded.Logout, "Sign out", null, tint = Brand.Danger) { confirmSignOut = true } }
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
            confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    tint: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (tint == Brand.Danger) tint else MaterialTheme.colorScheme.onSurface)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
