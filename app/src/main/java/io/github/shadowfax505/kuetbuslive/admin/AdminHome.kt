package io.github.shadowfax505.kuetbuslive.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreHoriz
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
import io.github.shadowfax505.kuetbuslive.campus.SettingRow
import io.github.shadowfax505.kuetbuslive.model.AppUser
import io.github.shadowfax505.kuetbuslive.rider.LiveMapScreen
import io.github.shadowfax505.kuetbuslive.rider.RiderViewModel
import io.github.shadowfax505.kuetbuslive.schedule.ScheduleScreen
import io.github.shadowfax505.kuetbuslive.ui.components.Page

private enum class AdminTab(val label: String, val icon: ImageVector) {
    MAP("Map", Icons.Rounded.Map),
    SCHEDULE("Schedule", Icons.Rounded.CalendarMonth),
    APPROVALS("Approvals", Icons.Rounded.HowToReg),
    MORE("More", Icons.Rounded.MoreHoriz),
}

// The transport office: every bus on the map, the timetable, approvals, and the rest under More.
@Composable
fun AdminHome(session: SessionViewModel, user: AppUser) {
    val campus: CampusViewModel = viewModel()
    val rider: RiderViewModel = viewModel()
    val admin: AdminViewModel = viewModel()
    var tab by rememberSaveable { mutableStateOf(AdminTab.MAP) }
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    val pending by admin.pending.collectAsStateWithLifecycle()
    val complaints by admin.complaints.collectAsStateWithLifecycle()
    val unread = complaints.count { !it.readByAdmin }

    LaunchedEffect(user.id) { campus.setUser(user.id) }
    BackHandler(enabled = page != null || tab != AdminTab.MAP) { if (page != null) page = null else tab = AdminTab.MAP }

    when (page) {
        "fleet" -> { FleetScreen(user, admin, onBack = { page = null }); return }
        "complaints" -> { ComplaintsInboxScreen(admin, campus, onBack = { page = null }); return }
        "news" -> { NewsScreen(user, campus, onBack = { page = null }); return }
        "profile" -> {
            ProfileScreen(user, campus, onPickStop = null, onOpenComplaints = { page = "mine" }, onOpenNews = null,
                onSignOut = { session.signOut() }, onBack = { page = null })
            return
        }
        "mine" -> { MyComplaintsScreen(user, campus, onBack = { page = "profile" }); return }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                AdminTab.MAP -> LiveMapScreen(user, rider, onPickStop = { tab = AdminTab.MORE })
                AdminTab.SCHEDULE -> ScheduleScreen(user, campus)
                AdminTab.APPROVALS -> ApprovalsScreen(admin)
                AdminTab.MORE -> Page("More") {
                    item { SettingRow(Icons.Rounded.DirectionsBus, "Drivers & buses", "Regular drivers, covers and absences") { page = "fleet" } }
                    item { SettingRow(Icons.Rounded.Feedback, "Complaints", if (unread > 0) "$unread unread" else "All read") { page = "complaints" } }
                    item { SettingRow(Icons.Rounded.Campaign, "News", "Post to every rider") { page = "news" } }
                    item { SettingRow(Icons.Rounded.AccountCircle, "Profile", user.email) { page = "profile" } }
                }
            }
        }
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            AdminTab.entries.forEach { item ->
                val badge = when (item) {
                    AdminTab.APPROVALS -> pending.size
                    AdminTab.MORE -> unread
                    else -> 0
                }
                NavigationBarItem(
                    selected = tab == item,
                    onClick = { tab = item },
                    icon = {
                        if (badge > 0) BadgedBox(badge = { Badge { Text("$badge") } }) { Icon(item.icon, null) } else Icon(item.icon, null)
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
}
