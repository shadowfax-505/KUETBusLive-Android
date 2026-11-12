package io.github.shadowfax505.kuetbuslive.campus

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.shadowfax505.kuetbuslive.model.Announcement
import io.github.shadowfax505.kuetbuslive.model.AppUser
import io.github.shadowfax505.kuetbuslive.model.UserRole
import io.github.shadowfax505.kuetbuslive.ui.components.AppTextField
import io.github.shadowfax505.kuetbuslive.ui.components.EmptyState
import io.github.shadowfax505.kuetbuslive.ui.components.IconBubble
import io.github.shadowfax505.kuetbuslive.ui.components.Page
import io.github.shadowfax505.kuetbuslive.ui.components.Pill

// News from the transport office. Everyone reads; the admin posts and deletes.
@Composable
fun NewsScreen(user: AppUser, campus: CampusViewModel, onBack: (() -> Unit)? = null) {
    val news by campus.announcements.collectAsStateWithLifecycle()
    val message by campus.message.collectAsStateWithLifecycle()
    val isAdmin = user.role == UserRole.ADMIN
    var composing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Announcement?>(null) }

    // Opening the tab marks everything shown as read (kept on this phone only).
    LaunchedEffect(news) { campus.markNewsRead(news.map { it.id }) }

    Page(
        "News", "From the KUET transport office", onBack = onBack,
        message = message, onMessageShown = campus::clearMessage,
        floating = {
            if (isAdmin) {
                ExtendedFloatingActionButton(onClick = { composing = true }, icon = { Icon(Icons.Rounded.Edit, null) }, text = { Text("New post") })
            }
        },
    ) {
        if (news.isEmpty()) {
            item { EmptyState(Icons.Rounded.Campaign, "No news yet", "Route changes, holidays and delays show up here.") }
        }
        items(news, key = { it.id }) { post ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.Campaign, MaterialTheme.colorScheme.tertiary, size = 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(post.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${post.postedBy} · ${relativeTime(post.createdAtMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isAdmin) {
                        IconButton(onClick = { deleting = post }) { Icon(Icons.Rounded.Delete, "Delete") }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(post.body, style = MaterialTheme.typography.bodyMedium)
                post.routeName?.let {
                    Spacer(Modifier.height(10.dp))
                    Pill(it, MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    if (composing) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var route by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { composing = false },
            title = { Text("New post") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(title, { title = it.take(200) }, "Title")
                    AppTextField(body, { body = it.take(5000) }, "Message", singleLine = false, minLines = 4)
                    AppTextField(route, { route = it }, "Route (optional)")
                }
            },
            confirmButton = {
                TextButton(onClick = { campus.postAnnouncement(user, title, body, route) { composing = false } }) { Text("Post") }
            },
            dismissButton = { TextButton(onClick = { composing = false }) { Text("Cancel") } },
        )
    }

    deleting?.let { post ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this post?") },
            text = { Text("\"${post.title}\" disappears for every rider.") },
            confirmButton = { TextButton(onClick = { campus.deleteAnnouncement(post.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

fun relativeTime(millis: Long): String {
    val now = System.currentTimeMillis()
    if (now - millis < 60_000) return "Just now"
    return DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS).toString()
}
