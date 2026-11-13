package io.github.shadowfax505.kuetbuslive.driver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.shadowfax505.kuetbuslive.auth.SessionViewModel
import io.github.shadowfax505.kuetbuslive.campus.CampusViewModel
import io.github.shadowfax505.kuetbuslive.campus.MyComplaintsScreen
import io.github.shadowfax505.kuetbuslive.campus.NewsScreen
import io.github.shadowfax505.kuetbuslive.campus.ProfileScreen
import io.github.shadowfax505.kuetbuslive.map.BusMap
import io.github.shadowfax505.kuetbuslive.map.FocusRequest
import io.github.shadowfax505.kuetbuslive.model.AppUser
import io.github.shadowfax505.kuetbuslive.model.Bus
import io.github.shadowfax505.kuetbuslive.model.BusAssignment
import io.github.shadowfax505.kuetbuslive.model.CrowdLevel
import io.github.shadowfax505.kuetbuslive.model.Timetable
import io.github.shadowfax505.kuetbuslive.ui.components.AppTextField
import io.github.shadowfax505.kuetbuslive.ui.components.AudienceBadge
import io.github.shadowfax505.kuetbuslive.ui.components.Pill
import io.github.shadowfax505.kuetbuslive.ui.components.PrimaryButton
import io.github.shadowfax505.kuetbuslive.ui.components.SectionTitle
import io.github.shadowfax505.kuetbuslive.ui.theme.Brand

@Composable
fun DriverHome(session: SessionViewModel, user: AppUser) {
    val campus: CampusViewModel = viewModel()
    var page by rememberSaveable { mutableStateOf("home") }
    LaunchedEffect(user.id) { campus.setUser(user.id) }
    androidx.activity.compose.BackHandler(enabled = page != "home") { page = if (page == "complaints" || page == "news") "profile" else "home" }
    when (page) {
        "profile" -> ProfileScreen(
            user, campus, onPickStop = null,
            onOpenComplaints = { page = "complaints" },
            onOpenNews = { page = "news" },
            onSignOut = { session.signOut() },
            onBack = { page = "home" },
        )
        "complaints" -> MyComplaintsScreen(user, campus, onBack = { page = "profile" })
        "news" -> NewsScreen(user, campus, onBack = { page = "profile" })
        else -> DriverTripScreen(user, onProfile = { page = "profile" })
    }
}

@Composable
private fun DriverTripScreen(user: AppUser, onProfile: () -> Unit) {
    val vm: DriverViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val trip by TripTracker.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var selectedScheduleId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRouteId by rememberSaveable { mutableStateOf<String?>(null) }
    var askingAbsence by remember { mutableStateOf(false) }
    var focus by remember { mutableStateOf<FocusRequest?>(null) }

    LaunchedEffect(user.id) { vm.start(user) }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
    LaunchedEffect(trip.error) { trip.error?.let { snackbar.showSnackbar(it); TripTracker.clearError() } }

    val now = System.currentTimeMillis()
    val dayBus = vm.myBus(user, now)
    val tripChoices = vm.tripChoices(user, now)
    val choices = tripChoices.map { it.second }
    val active = trip.trip
    val schedule = active?.schedule ?: choices.firstOrNull { it.id == selectedScheduleId } ?: choices.firstOrNull()
    // The bus of the chosen trip: usually my own bus, or another bus for a single trip.
    val myBus = tripChoices.firstOrNull { it.second.id == schedule?.id }?.first ?: dayBus
    // Routes whose name mentions the trip's spot come first; the driver can pick any.
    val routes = state.routes.sortedByDescending { schedule != null && it.name.contains(schedule.spot, ignoreCase = true) }
    val route = active?.route ?: routes.firstOrNull { it.id == selectedRouteId } ?: routes.firstOrNull()
    LaunchedEffect(route?.id) { vm.loadPath(route) }
    LaunchedEffect(route?.id, state.routePath.size) {
        if (state.routePath.size >= 2 && active == null) focus = FocusRequest(state.routePath)
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted && myBus != null && schedule != null && route != null) vm.startTrip(user, myBus, schedule, route)
    }
    fun start() {
        if (myBus == null || schedule == null || route == null) return
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine) {
            vm.startTrip(user, myBus, schedule, route)
        } else {
            permissions.launch(
                buildList {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                }.toTypedArray()
            )
        }
    }

    // The driver's own bus on the map, gliding like the riders see it.
    val me = trip.position?.let { p ->
        Bus(
            id = "me", number = schedule?.title ?: "", routeId = route?.id ?: "", routeName = route?.name ?: "",
            driverId = user.id, lat = p.lat, lng = p.lng, updatedAtMillis = System.currentTimeMillis(),
            crowd = CrowdLevel.UNKNOWN, audience = schedule?.audience ?: io.github.shadowfax505.kuetbuslive.model.BusAudience.STUDENT,
            scheduleId = schedule?.id, fleetBusId = myBus?.id, heading = trip.heading, speed = trip.speed,
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelMaxHeight = maxHeight * 0.6f
        BusMap(
            modifier = Modifier.fillMaxSize(),
            buses = listOfNotNull(me),
            followBusId = if (active != null && me != null) "me" else null,
            routePath = state.routePath,
            stops = route?.stops?.map { it.position } ?: emptyList(),
            waves = state.waitingAhead.map { it.wave.position },
            showUserLocation = active == null &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
            focus = focus,
            bottomPadding = 380.dp,
        )

        // Header: who and which bus.
        Surface(
            Modifier.statusBarsPadding().padding(16.dp).fillMaxWidth().shadow(14.dp, RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (myBus == null) Brand.Danger else MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(if (myBus == null) Icons.Rounded.Warning else Icons.Rounded.DirectionsBus, null, tint = if (myBus == null) Color.White else MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(myBusText(user, dayBus ?: myBus, now), style = MaterialTheme.typography.titleMedium)
                    Text(trip.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onProfile) { Icon(Icons.Rounded.AccountCircle, "Profile") }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            AnimatedVisibility(active != null && state.waitingAhead.isNotEmpty()) {
                WaitingBanner(state.waitingAhead)
            }
            Surface(
                Modifier.fillMaxWidth().heightIn(max = panelMaxHeight).shadow(24.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding()) {
                    if (active != null) {
                        Text(active.schedule.title, style = MaterialTheme.typography.headlineSmall)
                        Text(active.route.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("${trip.updatesSent} updates sent", Brand.Seats)
                            trip.speed?.let { Pill("${(it * 3.6).toInt()} km/h", MaterialTheme.colorScheme.secondary) }
                            AudienceBadge(active.schedule.audience)
                        }
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton("End trip", onClick = { vm.endTrip() }, icon = Icons.Rounded.Stop, containerColor = Brand.Danger, contentColor = Color.White)
                    } else {
                        SectionTitle("Trip")
                        if (choices.isEmpty()) {
                            Text(
                                if (myBus == null) "Ask the transport office to assign you a bus or a trip." else "No trips in the timetable for ${myBus.name}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            choices.forEach { s ->
                                FilterChip(
                                    selected = s.id == schedule?.id,
                                    onClick = { selectedScheduleId = s.id },
                                    label = {
                                        val onOtherBus = dayBus != null && s.tripName != dayBus.name
                                        Text("${Timetable.display(s.campusTime)} · ${s.spot}" + if (onOtherBus || dayBus == null) " · ${s.tripName}" else "")
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                )
                            }
                        }
                        schedule?.let { s ->
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Leaves KUET ${Timetable.display(s.campusTime)} · ${s.spot} ${Timetable.display(s.spotTime)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                AudienceBadge(s.audience)
                            }
                        }
                        SectionTitle("Route", Modifier.padding(top = 10.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            routes.forEach { r ->
                                FilterChip(selected = r.id == route?.id, onClick = { selectedRouteId = r.id }, label = { Text(r.name) }, shape = RoundedCornerShape(14.dp))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(
                            "Start trip",
                            onClick = { start() },
                            enabled = myBus != null && schedule != null && route != null,
                            icon = Icons.Rounded.PlayArrow,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { askingAbsence = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Bedtime, null)
                            Spacer(Modifier.width(8.dp))
                            Text("I can't drive")
                        }
                        state.absences.take(3).forEach { absence ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${absence.fromDate} – ${absence.untilDate}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text(absence.status.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                if (absence.status == io.github.shadowfax505.kuetbuslive.model.AbsenceStatus.OPEN) {
                                    TextButton(onClick = { vm.withdrawAbsence(absence.id) }) { Text("Withdraw") }
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.Center))
    }

    if (askingAbsence) {
        val today = Timetable.dayString(System.currentTimeMillis())
        var from by remember { mutableStateOf(today) }
        var until by remember { mutableStateOf(today) }
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askingAbsence = false },
            title = { Text("I can't drive") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("The transport office will arrange a cover driver.", style = MaterialTheme.typography.bodyMedium)
                    AppTextField(from, { from = it }, "First day (yyyy-MM-dd)")
                    AppTextField(until, { until = it }, "Last day (yyyy-MM-dd)")
                    AppTextField(reason, { reason = it.take(300) }, "Reason", singleLine = false)
                }
            },
            confirmButton = { TextButton(onClick = { vm.requestAbsence(user, from.trim(), until.trim(), reason) { askingAbsence = false } }) { Text("Send") } },
            dismissButton = { TextButton(onClick = { askingAbsence = false }) { Text("Cancel") } },
        )
    }
}

private fun myBusText(user: AppUser, bus: io.github.shadowfax505.kuetbuslive.model.FleetBus?, now: Long): String {
    if (bus == null) return "No bus assigned today"
    val cover = bus.cover
    if (BusAssignment.isCoverActive(cover, now) && cover?.driverId == user.id) return "Covering ${bus.name} until ${cover.untilDate}"
    return "Your bus today: ${bus.name}"
}

// Big and simple, so it can be read at a glance while driving.
@Composable
private fun WaitingBanner(waiting: List<WaitingRider>) {
    val nearest = waiting.first()
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable { }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PanTool, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                if (waiting.size == 1) "1 rider waiting ahead" else "${waiting.size} riders waiting ahead",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                "Next: ${nearest.wave.label} · ${formatDistance(nearest.metresAhead)}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

private fun formatDistance(metres: Double): String =
    if (metres < 1000) "${(metres / 10).toInt() * 10} m" else "%.1f km".format(metres / 1000)
