package io.github.shadowfax505.kuetbuslive.rider

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.EventSeat
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GroupOff
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.SignalCellularConnectedNoInternet0Bar
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.shadowfax505.kuetbuslive.map.BusMap
import io.github.shadowfax505.kuetbuslive.map.BusMapController
import io.github.shadowfax505.kuetbuslive.map.FocusRequest
import io.github.shadowfax505.kuetbuslive.model.AppUser
import io.github.shadowfax505.kuetbuslive.model.Bus
import io.github.shadowfax505.kuetbuslive.model.CrowdLevel
import io.github.shadowfax505.kuetbuslive.model.Geo
import io.github.shadowfax505.kuetbuslive.model.Timetable
import io.github.shadowfax505.kuetbuslive.ui.components.AudienceBadge
import io.github.shadowfax505.kuetbuslive.ui.components.Avatar
import io.github.shadowfax505.kuetbuslive.ui.components.CrowdPill
import io.github.shadowfax505.kuetbuslive.ui.components.Pill
import io.github.shadowfax505.kuetbuslive.ui.components.PrimaryButton
import io.github.shadowfax505.kuetbuslive.ui.components.color
import io.github.shadowfax505.kuetbuslive.ui.theme.Brand
import io.github.shadowfax505.kuetbuslive.ui.theme.EtaNumberStyle
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
fun LiveMapScreen(user: AppUser, vm: RiderViewModel, onPickStop: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var focus by remember { mutableStateOf<FocusRequest?>(null) }
    var didInitialFocus by rememberSaveable { mutableStateOf(false) }
    var hasLocation by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasLocation = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(user.id) { vm.start(user) }
    // The rider's position is used only while the map is on screen.
    LifecycleStartEffect(hasLocation) {
        vm.setMapVisible(true)
        onStopOrDispose { vm.setMapVisible(false) }
    }
    var pickingDestination by remember { mutableStateOf(false) }
    LaunchedEffect(user) { vm.updateUser(user) }
    LaunchedEffect(Unit) {
        // Location for the blue dot, notifications for "your bus is close". Both optional.
        val wanted = buildList {
            if (!hasLocation) { add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION) }
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (wanted.isNotEmpty()) permissionLauncher.launch(wanted.toTypedArray())
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }
    // First time buses appear, frame them all.
    LaunchedEffect(state.buses.isNotEmpty()) {
        if (state.buses.isNotEmpty() && !didInitialFocus) {
            didInitialFocus = true
            focus = FocusRequest(state.buses.map { it.position } + listOfNotNull(user.favoriteStop?.position))
        }
    }

    val selected = state.selectedBus
    val selectedPath = selected?.let { state.routePaths[it.routeId] } ?: emptyList()
    val selectedRoute = selected?.let { vm.routeFor(it) }
    val etaPath = selected?.let { bus -> state.eta[bus.id]?.path?.let { Geo.remainingPath(bus.position, it) } } ?: emptyList()

    Box(Modifier.fillMaxSize()) {
        BusMap(
            modifier = Modifier.fillMaxSize(),
            buses = state.buses,
            selectedBusId = state.selectedBusId,
            followBusId = state.followBusId,
            routePath = selectedPath,
            stops = selectedRoute?.stops?.map { it.position } ?: emptyList(),
            etaPath = etaPath,
            // Where the plan says to wait, once there is one; otherwise the rider's stop.
            boardingPoint = state.catchPlan?.takeIf { state.onBoardBusId != selected?.id }?.point?.position ?: user.favoriteStop?.position,
            etaLabel = selected?.let { state.eta[it.id] }?.let { formatMinutes(it.minutes) },
            showUserLocation = hasLocation,
            focus = focus,
            onBusClick = { id ->
                vm.select(id)
                state.buses.firstOrNull { it.id == id }?.let { bus ->
                    focus = FocusRequest(listOfNotNull(bus.position, user.favoriteStop?.position))
                }
            },
            onUserMovedMap = { vm.stopFollowing() },
        )

        TopBar(user, onPickStop, Modifier.align(Alignment.TopCenter))

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.End) {
                MapButton(Icons.Rounded.ZoomOutMap, "Show all buses") {
                    vm.stopFollowing()
                    val points = state.buses.map { it.position } + listOfNotNull(user.favoriteStop?.position)
                    focus = FocusRequest(points.ifEmpty { listOf(BusMapController.KUET) })
                }
            }
            BottomPanel(user, state, vm, onPickStop, onWhereTo = { pickingDestination = true }, onFocusBus = { bus ->
                focus = FocusRequest(listOfNotNull(bus.position, user.favoriteStop?.position))
            })
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 80.dp))
    }

    if (pickingDestination) {
        DestinationSheet(
            stops = remember(state.routes, state.buses.size) { vm.destinations() },
            onPick = { vm.setDestination(it); pickingDestination = false },
            onDismiss = { pickingDestination = false },
        )
    }
    val destination = state.destination
    val options = state.options
    if (destination != null && options != null) {
        CompareSheet(destination, options, onDismiss = { vm.closeOptions() })
    }
}

// A round avatar on the left and the boarding stop in a centred pill, floating over the map.
@Composable
private fun TopBar(user: AppUser, onPickStop: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth(),
    ) {
        Surface(
            modifier = Modifier.align(Alignment.CenterStart).size(48.dp).shadow(10.dp, CircleShape),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(contentAlignment = Alignment.Center) { Avatar(user.name, size = 40.dp) }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 60.dp)
                .shadow(10.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onPickStop),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(Modifier.padding(start = 14.dp, end = 10.dp, top = 11.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(10.dp))
                Text(
                    user.favoriteStopName?.let { "Boarding at $it" } ?: "${greeting()}, ${user.firstName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.KeyboardArrowDown, "Change stop", Modifier.size(20.dp))
            }
        }
    }
}

private fun greeting(): String {
    val hour = Timetable.dhakaTime(System.currentTimeMillis()).hour
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
private fun MapButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = CircleShape,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(icon, contentDescription = description)
    }
}

@Composable
private fun BottomPanel(
    user: AppUser,
    state: RiderState,
    vm: RiderViewModel,
    onPickStop: () -> Unit,
    onWhereTo: () -> Unit,
    onFocusBus: (Bus) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().shadow(24.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(top = 10.dp)) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outlineVariant)
            )
            AnimatedContent(
                targetState = state.selectedBus,
                contentKey = { it?.id },
                transitionSpec = {
                    (slideInVertically { it / 6 } + fadeIn()) togetherWith (slideOutVertically { it / 6 } + fadeOut())
                },
                label = "panel",
            ) { bus ->
                if (bus == null) {
                    BusList(user, state, vm, onPickStop, onFocusBus)
                } else {
                    TripCard(user, bus, state, vm, onPickStop, onWhereTo)
                }
            }
        }
    }
}

@Composable
private fun BusList(
    user: AppUser,
    state: RiderState,
    vm: RiderViewModel,
    onPickStop: () -> Unit,
    onFocusBus: (Bus) -> Unit,
) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Buses on the road", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (state.buses.isNotEmpty()) Pill("${state.buses.size} live", MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(12.dp))
        if (state.buses.isEmpty()) {
            NoBusesCard(user, state)
        } else {
            LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.buses, key = { it.id }) { bus ->
                    BusRow(bus, state, onClick = { vm.select(bus.id); onFocusBus(bus) })
                }
            }
        }
        if (user.favoriteStop == null) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onPickStop, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Rounded.Place, null)
                Spacer(Modifier.width(8.dp))
                Text("Choose your stop to see ETAs")
            }
        }
    }
}

@Composable
private fun NoBusesCard(user: AppUser, state: RiderState) {
    val next = Timetable.nextTrip(state.schedules, user.role, state.nowMillis)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.DirectionsBus, null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("No bus is running right now", style = MaterialTheme.typography.titleSmall)
            Text(
                if (next != null) {
                    val minutes = Timetable.minutesUntil(next.campusTime, state.nowMillis)
                    "Next: ${next.tripName} to ${next.spot} at ${Timetable.display(next.campusTime)}" +
                        (minutes?.let { " · in ${formatMinutes(it)}" } ?: "")
                } else "No more trips today. Check the Schedule tab for tomorrow.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun formatMinutes(minutes: Int): String = if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"

@Composable
private fun BusRow(bus: Bus, state: RiderState, onClick: () -> Unit) {
    val eta = state.eta[bus.id]
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.DirectionsBus, null, tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(bus.tripName, style = MaterialTheme.typography.titleMedium)
            Text(
                listOf(bus.spot, bus.routeName).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            if (bus.isStale(state.nowMillis)) {
                Pill("Signal lost · ${bus.lastUpdateText(state.nowMillis)}", Brand.Danger, icon = Icons.Rounded.SignalCellularConnectedNoInternet0Bar)
            } else if (bus.isCrowded(state.nowMillis)) {
                CrowdPill(CrowdLevel.FULL)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (eta != null) {
                Text("${eta.minutes}", style = MaterialTheme.typography.headlineMedium)
                Text("min", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Icon(Icons.Rounded.NearMe, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TripCard(user: AppUser, bus: Bus, state: RiderState, vm: RiderViewModel, onPickStop: () -> Unit, onWhereTo: () -> Unit) {
    val eta = state.eta[bus.id]
    val stop = user.favoriteStop
    val progress = vm.progressFor(bus)
    val animatedProgress by animateFloatAsState((progress ?: 0.0).toFloat(), label = "progress")
    val nearest = vm.nearestStop(bus)
    val following = state.followBusId == bus.id
    val waving = state.wavingAtBusId == bus.id

    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(bus.tripName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    AudienceBadge(bus.audience)
                }
                Text(
                    listOf(bus.spot, bus.routeName).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { vm.select(null) }) { Icon(Icons.Rounded.Close, "Close") }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(18.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                when {
                    stop == null -> {
                        Text("Where do you get on?", style = MaterialTheme.typography.titleMedium)
                        Text("Choose your stop to see when this bus reaches you.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    eta != null -> {
                        Text("Reaches ${stop.name} in", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${eta.minutes}", style = EtaNumberStyle)
                            Spacer(Modifier.width(6.dp))
                            Text("min", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        Text(
                            "%.1f km · around %s".format(eta.kilometres, clock(state.nowMillis + eta.durationSeconds.toLong() * 1000)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    bus.id in state.etaFailed -> Text("ETA unavailable right now", style = MaterialTheme.typography.titleMedium)
                    else -> Text("Working out the ETA…", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (stop == null) {
                FilledTonalButton(onClick = onPickStop, shape = RoundedCornerShape(14.dp)) { Text("Choose") }
            } else {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(bus.speedKmh?.let { "$it km/h" } ?: "--", MaterialTheme.colorScheme.secondary, icon = Icons.Rounded.Speed)
                    Text(bus.lastUpdateText(state.nowMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (progress != null && stop != null) {
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.onSurface,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("KUET", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                nearest?.let { Text("Near ${it.name}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.weight(1f))
                Text(stop.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Today's pace against the usual, once it clearly differs.
        if (!bus.isStale(state.nowMillis) && kotlin.math.abs(state.liveFactor - 1) >= 0.15) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (state.liveFactor > 1) Icons.Rounded.TrendingDown else Icons.Rounded.TrendingUp, null,
                    Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (state.liveFactor > 1) "Slower than usual today" else "Faster than usual today",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Where to wait and where to get off, from the learned travel times.
        if (user.role.isRider) {
            Spacer(Modifier.height(14.dp))
            PlanCard(
                catchPlan = state.catchPlan,
                dropPlan = state.dropPlan,
                destination = state.destination,
                canBoard = bus.audience.canBoard(user.role),
                waving = state.wavingAtBusId == bus.id,
                stopRequested = state.requestedBusId == bus.id,
                onWave = { vm.wave(bus, it) },
                onWhereTo = onWhereTo,
                onRequestStop = { vm.requestStop(bus) },
                onCancelStop = { vm.cancelStop() },
                onCompare = { vm.compare() },
                onClearDestination = { vm.setDestination(null) },
            )
        }

        // Crowding is shown softly, for 10 minutes after a rider on board reported it.
        val crowded = bus.isCrowded(state.nowMillis)
        val onBoard = state.onBoardBusId == bus.id
        if (crowded || onBoard) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (crowded) {
                    val ago = bus.crowdedMinutesAgo(state.nowMillis)
                    Text(
                        if (ago == 0) "Riders say it's crowded · just now" else "Riders say it's crowded · $ago min ago",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text("You're on this bus", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                }
                if (onBoard) {
                    OutlinedButton(onClick = { vm.reportCrowded(bus) }, shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Rounded.GroupOff, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("It's overcrowded")
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { vm.toggleFollow() },
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Rounded.GpsFixed, null, tint = if (following) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(if (following) "Following" else "Follow")
            }
            if (user.role.isRider) {
                PrimaryButton(
                    text = if (waving) "Cancel wave" else "I'm waiting here",
                    onClick = { if (waving) vm.cancelWave() else vm.wave(bus) },
                    icon = Icons.Rounded.PanTool,
                    modifier = Modifier.weight(1f),
                    containerColor = if (waving) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primary,
                    contentColor = if (waving) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

private val clockFormat = DateTimeFormatter.ofPattern("h:mm a")

fun clock(millis: Long): String = Instant.ofEpochMilli(millis).atZone(Timetable.dhaka).format(clockFormat)
