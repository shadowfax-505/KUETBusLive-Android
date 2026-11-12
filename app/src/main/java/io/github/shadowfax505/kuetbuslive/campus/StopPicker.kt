package io.github.shadowfax505.kuetbuslive.campus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.shadowfax505.kuetbuslive.model.BusRoute
import io.github.shadowfax505.kuetbuslive.model.Stop
import io.github.shadowfax505.kuetbuslive.ui.components.AppTextField
import io.github.shadowfax505.kuetbuslive.ui.components.EmptyState

// Every stop on every route, searchable. The chosen stop is where ETAs are measured to.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopPickerSheet(routes: List<BusRoute>, current: String?, onPick: (Stop) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    // A stop on several routes is listed once, under its first route.
    val entries = remember(routes) {
        val seen = mutableSetOf<String>()
        routes.flatMap { route -> route.stops.drop(1).map { route to it } }
            .filter { seen.add(it.second.name) }
    }
    val filtered = entries.filter { (route, stop) ->
        query.isBlank() || stop.name.contains(query, ignoreCase = true) ||
            (stop.nameBn?.contains(query) ?: false) || route.name.contains(query, ignoreCase = true)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Where do you get on?", style = MaterialTheme.typography.headlineSmall)
            Text(
                "ETAs and arrival alerts are measured to this stop.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(6.dp))
            AppTextField(query, { query = it }, "Search stops", leadingIcon = Icons.Rounded.Search)
            Spacer(Modifier.padding(6.dp))
            if (filtered.isEmpty()) {
                EmptyState(Icons.Rounded.Place, "No stops found", "The transport office adds routes and stops. Try another name.")
            }
            LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(filtered, key = { it.second.name }) { (route, stop) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onPick(stop) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (stop.name == current) Icons.Rounded.CheckCircle else Icons.Rounded.Place,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stop.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull(stop.nameBn, route.name).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
