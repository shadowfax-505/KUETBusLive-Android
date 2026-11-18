package io.github.shadowfax505.kuetbuslive.auth

import io.github.shadowfax505.kuetbuslive.model.UserRole

// When an account may not be deleted, with the same rule and wording as iOS.
object AccountDeletion {
    // null when deleting is allowed. adminCount is only needed for an admin.
    fun blockReason(role: UserRole, adminCount: Int?): String? =
        if (role == UserRole.ADMIN && (adminCount ?: 0) <= 1) {
            "You are the only transport admin. Make another account an admin in the Firebase console before deleting this one."
        } else {
            null
        }

    // What the confirmation tells the person before they type their password.
    fun explanation(role: UserRole, isOnTrip: Boolean): String {
        var text = "This permanently deletes your profile (name, email, KUET ID and favourite stop), " +
            "your places, your complaints and your login, and clears the app's settings on this phone. " +
            "It cannot be undone."
        if (role == UserRole.DRIVER) {
            text += if (isOnTrip) " Your running trip will be ended first." else " Any bus you left on the map is removed too."
        }
        return text
    }
}
