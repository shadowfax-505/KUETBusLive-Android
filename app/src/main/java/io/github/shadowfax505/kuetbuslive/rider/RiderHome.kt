package io.github.shadowfax505.kuetbuslive.rider

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.shadowfax505.kuetbuslive.auth.SessionViewModel
import io.github.shadowfax505.kuetbuslive.campus.CampusViewModel
import io.github.shadowfax505.kuetbuslive.campus.MyComplaintsScreen
import io.github.shadowfax505.kuetbuslive.campus.NewsScreen
import io.github.shadowfax505.kuetbuslive.campus.ProfileScreen
import io.github.shadowfax505.kuetbuslive.campus.RoutesScreen
import io.github.shadowfax505.kuetbuslive.campus.StopPickerSheet
import io.github.shadowfax505.kuetbuslive.model.AppUser
import io.github.shadowfax505.kuetbuslive.schedule.ScheduleScreen

private enum class RiderTab(val label: String, val icon: ImageVector) {
    MAP("Map", Icons.Rounded.Map),
    SCHEDULE("Schedule", Icons.Rounded.CalendarMonth),
    ROUTES("Routes", Icons.Rounded.Route),
    NEWS("News", Icons.Rounded.Campaign),
    PROFILE("Profile", Icons.Rounded.AccountCircle),
}

// Students and teachers: the live map first, like opening a ride app.
@Composable
fun RiderHome(session: SessionViewModel, user: AppUser) {
    val campus: CampusViewModel = viewModel()
    val rider: RiderViewModel = viewModel()
    var tab by rememberSaveable { mutableStateOf(RiderTab.MAP) }
    var showComplaints by rememberSaveable { mutableStateOf(false) }
    var pickingStop by rememberSaveable { mutableStateOf(false) }
    val routes by campus.routes.collectAsStateWithLifecycle()
    val news by campus.announcements.collectAsStateWithLifecycle()
    val read by campus.readNews.collectAsStateWithLifecycle()
    val unread = news.count { it.id !in read }

    LaunchedEffect(user.id) { campus.setUser(user.id) }
    BackHandler(enabled = showComplaints || tab != RiderTab.MAP) {
        if (showComplaints) showComplaints = false else tab = RiderTab.MAP
    }

    if (showComplaints) {
        MyComplaintsScreen(user, campus, onBack = { showComplaints = false })
        return
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                RiderTab.MAP -> LiveMapScreen(user, rider, onPickStop = { pickingStop = true })
                RiderTab.SCHEDULE -> ScheduleScreen(user, campus)
                RiderTab.ROUTES -> RoutesScreen(user, campus)
                RiderTab.NEWS -> NewsScreen(user, campus)
                RiderTab.PROFILE -> ProfileScreen(
                    user, campus,
                    onPickStop = { pickingStop = true },
                    onOpenComplaints = { showComplaints = true },
                    onOpenNews = null,
                    onSignOut = { session.signOut() },
                )
            }
        }
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            RiderTab.entries.forEach { item ->
                NavigationBarItem(
                    selected = tab == item,
                    onClick = { tab = item },
                    icon = {
                        if (item == RiderTab.NEWS && unread > 0) {
                            BadgedBox(badge = { Badge { Text("$unread") } }) { Icon(item.icon, null) }
                        } else {
                            Icon(item.icon, null)
                        }
                    },
                    label = { Text(item.label) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
    }

    if (pickingStop) {
        StopPickerSheet(
            routes = routes,
            current = user.favoriteStopName,
            onPick = { stop -> campus.saveFavoriteStop(user, stop); pickingStop = false },
            onDismiss = { pickingStop = false },
        )
    }
}
