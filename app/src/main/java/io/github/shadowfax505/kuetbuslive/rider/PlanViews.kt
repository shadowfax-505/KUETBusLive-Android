package io.github.shadowfax505.kuetbuslive.rider

import android.location.Geocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.shadowfax505.kuetbuslive.model.CatchPlan
import io.github.shadowfax505.kuetbuslive.model.DropPlan
import io.github.shadowfax505.kuetbuslive.model.LatLng
import io.github.shadowfax505.kuetbuslive.model.RoutePoint
import io.github.shadowfax505.kuetbuslive.model.TripOption
import io.github.shadowfax505.kuetbuslive.ui.components.AppTextField
import io.github.shadowfax505.kuetbuslive.ui.components.EmptyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Text for the "best place to wait" and "best place to get off" suggestions, the same as on iOS.
object PlanText {
    fun catchTitle(plan: CatchPlan): String = when (plan.point.kind) {
        is RoutePoint.Kind.Stop -> "Wait at ${plan.point.name}"
        is RoutePoint.Kind.Spot -> "Wait at the usual spot near ${plan.point.name}"
        else -> "Wait by the road near ${plan.point.name}"
    }

    fun catchDetail(plan: CatchPlan): String =
        if (plan.leaveInMinutes == 0) "${plan.walkMinutes} min walk · Leave now"
        else "${plan.walkMinutes} min walk · Leave in ${plan.leaveInMinutes} min"

    fun dropTitle(plan: DropPlan): String =
        if (plan.point.kind is RoutePoint.Kind.Stop) "Get off at ${plan.point.name}" else "Get off near ${plan.point.name}"

    fun arrival(seconds: Double, now: Long = System.currentTimeMillis()): String = clock(now + (seconds * 1000).toLong())

    fun dropDetail(plan: DropPlan, extra: Double = 0.0): String =
        "Arrive ~${arrival(plan.arrivalSeconds + extra)} · ${plan.walkMinutes} min walk"

    // How sure: from past trips, or only from the road distance.
    fun confidence(samples: Int): String = if (samples > 0) "From $samples past trips" else "Road-distance estimate"
}

// In the trip card: where to wait (with "Wave here"), and where to get off for the destination.
@Composable
fun PlanCard(
    catchPlan: CatchPlan?,
    dropPlan: DropPlan?,
    destination: Destination?,
    canBoard: Boolean,
    waving: Boolean,
    stopRequested: Boolean,
    onWave: (CatchPlan) -> Unit,
    onWhereTo: () -> Unit,
    onRequestStop: () -> Unit,
    onCancelStop: () -> Unit,
    onCompare: () -> Unit,
    onClearDestination: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        if (canBoard && catchPlan != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlanRow(
                    Icons.Rounded.DirectionsWalk, PlanText.catchTitle(catchPlan), PlanText.catchDetail(catchPlan),
                    PlanText.confidence(catchPlan.samples), Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { onWave(catchPlan) },
                    enabled = !waving,
                    shape = CircleShape,
                    modifier = Modifier.padding(end = 12.dp),
                ) { Text(if (waving) "Waving" else "Wave here") }
            }
            HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (dropPlan != null && destination != null) {
            PlanRow(
                Icons.Rounded.Flag, PlanText.dropTitle(dropPlan), PlanText.dropDetail(dropPlan),
                "To ${destination.name} · ${PlanText.confidence(dropPlan.samples)}",
            )
            Row(
                Modifier.fillMaxWidth().padding(start = 56.dp, end = 8.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canBoard) {
                    OutlinedButton(onClick = if (stopRequested) onCancelStop else onRequestStop, shape = CircleShape) {
                        Text(if (stopRequested) "Cancel stop" else "Ask to stop here")
                    }
                }
                OutlinedButton(onClick = onCompare, shape = CircleShape) { Text("Compare") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClearDestination) { Icon(Icons.Rounded.Close, "Clear destination") }
            }
        } else {
            PlanRow(
                Icons.Rounded.Search, "Where to?", "Find the best place to get off",
                modifier = Modifier.clickable(onClick = onWhereTo),
            )
        }
    }
}

// Icon in a circle, a title, a detail line and an optional grey note.
@Composable
private fun PlanRow(icon: ImageVector, title: String, detail: String, note: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(7.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall)
            note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

// "Where to?": the stops on the routes, plus a search of the map around Khulna.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationSheet(stops: List<Destination>, onPick: (Destination) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<Destination>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val filtered = stops.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }

    fun searchMap() {
        val text = query.trim()
        if (text.isEmpty() || !Geocoder.isPresent()) return
        searching = true
        scope.launch {
            found = withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    Geocoder(context).getFromLocationName(text, 6, 22.70, 89.40, 23.00, 89.70).orEmpty().map { a ->
                        val name = a.featureName?.takeIf { it.isNotBlank() && it != a.subThoroughfare } ?: a.getAddressLine(0) ?: text
                        Destination(name, LatLng(a.latitude, a.longitude))
                    }
                }.getOrDefault(emptyList())
            }
            searching = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Where to?", style = MaterialTheme.typography.headlineSmall)
            Text(
                "We'll find the best place to get off, and compare today's buses.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(6.dp))
            AppTextField(query, { query = it; found = emptyList() }, "Search a stop or place", leadingIcon = Icons.Rounded.Search)
            Spacer(Modifier.padding(6.dp))
            LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (query.isNotBlank() && Geocoder.isPresent()) {
                    item(key = "search") {
                        DestinationRow(Icons.Rounded.TravelExplore, "Search the map for \"${query.trim()}\"", "Places around Khulna") { searchMap() }
                    }
                }
                if (searching) {
                    item(key = "searching") {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                items(found, key = { "map-${it.name}-${it.position.lat}-${it.position.lng}" }) { d ->
                    DestinationRow(Icons.Rounded.Place, d.name, "Place") { onPick(d) }
                }
                items(filtered, key = { "stop-${it.name}" }) { d ->
                    DestinationRow(Icons.Rounded.Place, d.name, "Bus stop") { onPick(d) }
                }
            }
            if (filtered.isEmpty() && found.isEmpty() && !searching && query.isNotBlank() && !Geocoder.isPresent()) {
                EmptyState(Icons.Rounded.Place, "No stops found", "Try another name.")
            }
        }
    }
}

@Composable
private fun DestinationRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Today's buses for one destination, the one that gets the rider there first on top.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareSheet(destination: Destination, options: List<TripOption>, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("To ${destination.name}", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Running buses and today's later trips, fastest first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(6.dp))
            if (options.isEmpty()) {
                EmptyState(Icons.Rounded.Flag, "No bus gets you there today", "No running bus or later trip passes close enough.")
            }
            LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(options.withIndex().toList(), key = { it.index }) { (index, option) ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(
                                if (index == 0) MaterialTheme.colorScheme.surfaceContainerHighest
                                else MaterialTheme.colorScheme.surfaceContainerLow
                            )
                            .padding(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(option.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("~${PlanText.arrival(option.arrivalSeconds)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        option.catchPlan?.let {
                            Text("${PlanText.catchTitle(it)} · ${PlanText.catchDetail(it)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "${PlanText.dropTitle(option.dropPlan)} · ${option.dropPlan.walkMinutes} min walk",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (option.startsIn > 0) {
                            Text(
                                "Leaves in about ${formatMinutes((option.startsIn / 60).toInt())}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
