package io.github.shadowfax505.kuetbuslive.ui

import androidx.compose.runtime.Composable
import io.github.shadowfax505.kuetbuslive.admin.AdminHome
import io.github.shadowfax505.kuetbuslive.auth.SessionViewModel
import io.github.shadowfax505.kuetbuslive.driver.DriverHome
import io.github.shadowfax505.kuetbuslive.model.AppUser
import io.github.shadowfax505.kuetbuslive.model.UserRole
import io.github.shadowfax505.kuetbuslive.rider.RiderHome

// Each role gets its own home: riders the live map, drivers the trip screen,
// the transport admin the office tools.
@Composable
fun HomeRouter(session: SessionViewModel, user: AppUser) {
    when (user.role) {
        UserRole.STUDENT, UserRole.TEACHER -> RiderHome(session, user)
        UserRole.DRIVER -> DriverHome(session, user)
        UserRole.ADMIN -> AdminHome(session, user)
    }
}
