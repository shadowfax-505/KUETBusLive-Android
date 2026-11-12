package io.github.shadowfax505.kuetbuslive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.shadowfax505.kuetbuslive.auth.SessionViewModel
import io.github.shadowfax505.kuetbuslive.model.AppUser
import io.github.shadowfax505.kuetbuslive.model.UserRole
import io.github.shadowfax505.kuetbuslive.rider.RiderHome

// Each role gets its own home: riders the live map, drivers the trip screen,
// the transport admin the office tools.
@Composable
fun HomeRouter(session: SessionViewModel, user: AppUser) {
    when (user.role) {
        UserRole.STUDENT, UserRole.TEACHER -> RiderHome(session, user)
        else -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Signed in as ${user.name} (${user.role.title})", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { session.signOut() }) { Text("Sign out") }
        }
    }
}
