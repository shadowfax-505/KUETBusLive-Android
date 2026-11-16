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
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreHoriz
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
import io.github.shadowfax505.kuetbuslive.model.AppUser
import io.github.shadowfax505.kuetbuslive.rider.LiveMapScreen
import io.github.shadowfax505.kuetbuslive.rider.RiderViewModel
import io.github.shadowfax505.kuetbuslive.schedule.ScheduleScreen
import androidx.compose.ui.unit.dp
import io.github.shadowfax505.kuetbuslive.ui.components.Page
import io.github.shadowfax505.kuetbuslive.ui.components.Section
import io.github.shadowfax505.kuetbuslive.ui.components.SettingRow
import io.github.shadowfax505.kuetbuslive.ui.components.TabBar
import io.github.shadowfax505.kuetbuslive.ui.components.TabItem
import io.github.shadowfax505.kuetbuslive.ui.theme.Tile

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
        "special" -> { SpecialDaysScreen(onBack = { page = null }); return }
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
                    item {
                        Section(header = "Transport office", separatorInset = 60.dp) {
                            SettingRow(Icons.Rounded.DirectionsBus, "Drivers & buses", "Regular drivers, covers and absences", Tile.Blue) { page = "fleet" }
                            SettingRow(Icons.Rounded.EventNote, "Special days", "Exams, holidays and events", Tile.Orange) { page = "special" }
                            SettingRow(Icons.Rounded.Feedback, "Complaints", null, Tile.Red, value = if (unread > 0) "$unread unread" else "All read") { page = "complaints" }
                            SettingRow(Icons.Rounded.Campaign, "News", "Post to every rider", Tile.Pink) { page = "news" }
                        }
                    }
                    item {
                        Section(header = "Account", separatorInset = 60.dp) {
                            SettingRow(Icons.Rounded.AccountCircle, "Profile", user.email, Tile.Grey) { page = "profile" }
                        }
                    }
                }
            }
        }
        TabBar(
            items = AdminTab.entries.map {
                val badge = when (it) {
                    AdminTab.APPROVALS -> pending.size
                    AdminTab.MORE -> unread
                    else -> 0
                }
                TabItem(it.label, it.icon, badge)
            },
            selected = tab.ordinal,
            onSelect = { tab = AdminTab.entries[it] },
        )
    }
}
