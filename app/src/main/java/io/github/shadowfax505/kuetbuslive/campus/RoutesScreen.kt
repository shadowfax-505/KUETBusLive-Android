package io.github.shadowfax505.kuetbuslive.campus

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.shadowfax505.kuetbuslive.model.AppUser
import io.github.shadowfax505.kuetbuslive.model.BusRoute
import io.github.shadowfax505.kuetbuslive.ui.components.EmptyState
import io.github.shadowfax505.kuetbuslive.ui.components.IconBubble
import io.github.shadowfax505.kuetbuslive.ui.components.Page
import io.github.shadowfax505.kuetbuslive.ui.components.Pill

// Every route with its stops in driving order. Riders can make any stop their own.
@Composable
fun RoutesScreen(user: AppUser, campus: CampusViewModel) {
    val routes by campus.routes.collectAsStateWithLifecycle()
    val message by campus.message.collectAsStateWithLifecycle()
    Page("Routes", "Stops in driving order, from KUET", message = message, onMessageShown = campus::clearMessage) {
        if (routes.isEmpty()) {
            item { EmptyState(Icons.Rounded.Route, "No routes yet", "The transport office adds routes and their stops.") }
        }
        items(routes, key = { it.id }) { route ->
            RouteCard(route, user, onPickStop = if (user.role.isRider) ({ stop -> campus.saveFavoriteStop(user, stop) }) else null)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun RouteCard(route: BusRoute, user: AppUser, onPickStop: ((io.github.shadowfax505.kuetbuslive.model.Stop) -> Unit)?) {
    var expanded by rememberSaveable(route.id) { mutableStateOf(route.stops.any { it.name == user.favoriteStopName }) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .animateContentSize(),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBubble(Icons.Rounded.Route, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(route.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(route.nameBn, "${route.stops.size} stops").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
        }
        if (expanded) {
            Column(Modifier.padding(start = 20.dp, end = 12.dp, bottom = 12.dp)) {
                route.stops.forEachIndexed { index, stop ->
                    val mine = stop.name == user.favoriteStopName
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // A timeline: dots joined by a line.
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(20.dp)) {
                            Box(Modifier.width(2.dp).height(10.dp).background(if (index == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
                            Box(
                                Modifier.size(if (mine) 14.dp else 10.dp).clip(CircleShape)
                                    .background(if (mine || index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                            )
                            Box(Modifier.width(2.dp).height(10.dp).background(if (index == route.stops.lastIndex) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stop.name, style = MaterialTheme.typography.bodyLarge)
                            stop.nameBn?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        when {
                            mine -> Pill("My stop", MaterialTheme.colorScheme.primary)
                            onPickStop != null && index > 0 -> TextButton(onClick = { onPickStop(stop) }) { Text("Board here") }
                        }
                    }
                }
            }
        }
    }
}
