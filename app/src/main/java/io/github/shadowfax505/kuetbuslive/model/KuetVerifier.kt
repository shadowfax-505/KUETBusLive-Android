package io.github.shadowfax505.kuetbuslive.model

import java.time.Year

// Checks that a new account belongs to a KUET student, teacher or driver, with the
// same rules as the iOS app:
//  - Students: 7-digit roll, @stud.kuet.ac.bd email that contains the roll.
//  - Teachers: KUET staff email and an employee ID; then the admin approves them.
//  - Drivers: a driver ID; then the admin approves them.
// Debug builds also accept @test.com demo accounts.
class KuetVerifier(private val allowDemoAccounts: Boolean) {

    fun domainOf(email: String): String {
        val parts = email.trim().lowercase().split("@")
        return if (parts.size == 2) parts[1] else ""
    }

    fun isDemoEmail(email: String): Boolean = allowDemoAccounts && domainOf(email) == DEMO_DOMAIN

    fun isAppReviewEmail(email: String): Boolean = email.trim().lowercase() in APP_REVIEW_EMAILS

    fun isStudentEmail(email: String): Boolean = domainOf(email) == STUDENT_DOMAIN

    // kuet.ac.bd or a department subdomain such as cse.kuet.ac.bd, but not the student domain.
    fun isStaffEmail(email: String): Boolean {
        val domain = domainOf(email)
        if (domain == STUDENT_DOMAIN) return false
        return domain == STAFF_DOMAIN || domain.endsWith(".$STAFF_DOMAIN")
    }

    // 7 digits, starting with a batch year that isn't in the future, e.g. 2207001.
    fun isValidRoll(roll: String, currentYear: Int = Year.now().value): Boolean {
        if (roll.length != 7 || !roll.all { it.isDigit() }) return false
        val batch = roll.take(2).toInt()
        return batch <= currentYear % 100
    }

    // null when everything is fine, otherwise a message for the user.
    fun validate(role: UserRole, email: String, kuetId: String, currentYear: Int = Year.now().value): String? {
        val mail = email.trim().lowercase()
        val id = kuetId.trim()
        val demo = isDemoEmail(mail) || isAppReviewEmail(mail)
        return when (role) {
            UserRole.STUDENT -> when {
                !isValidRoll(id, currentYear) -> "Roll number must be 7 digits, like 2207001."
                demo -> null
                !isStudentEmail(mail) -> "Use your KUET student email (ending in @$STUDENT_DOMAIN)."
                !mail.contains(id) -> "The roll number doesn't match the one in your KUET email."
                else -> null
            }
            UserRole.TEACHER -> when {
                id.isEmpty() -> "Enter your KUET employee ID."
                demo -> null
                !isStaffEmail(mail) -> "Use your KUET staff email (ending in $STAFF_DOMAIN)."
                else -> null
            }
            UserRole.DRIVER -> if (id.isEmpty()) "Enter your driver ID." else null
            UserRole.ADMIN -> "Transport admin accounts are set up by the transport office."
        }
    }

    fun needsEmailVerification(role: UserRole, email: String): Boolean =
        (role == UserRole.STUDENT || role == UserRole.TEACHER) && !isDemoEmail(email) && !isAppReviewEmail(email)

    companion object {
        const val STUDENT_DOMAIN = "stud.kuet.ac.bd"
        const val STAFF_DOMAIN = "kuet.ac.bd"
        const val DEMO_DOMAIN = "test.com"
        val APP_REVIEW_EMAILS = setOf("appreview.student@kuetbuslive.app", "appreview.driver@kuetbuslive.app")
    }
}
