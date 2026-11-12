package io.github.shadowfax505.kuetbuslive.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KuetVerifierTest {
    private val release = KuetVerifier(allowDemoAccounts = false)
    private val debug = KuetVerifier(allowDemoAccounts = true)

    @Test
    fun studentNeedsMatchingRollAndEmail() {
        assertNull(release.validate(UserRole.STUDENT, "name2207001@stud.kuet.ac.bd", "2207001", 2026))
        assertNotNull(release.validate(UserRole.STUDENT, "name2207002@stud.kuet.ac.bd", "2207001", 2026))
        assertNotNull(release.validate(UserRole.STUDENT, "someone@gmail.com", "2207001", 2026))
    }

    @Test
    fun rollMustBeSevenDigitsFromAPastBatch() {
        assertTrue(release.isValidRoll("2207001", 2026))
        assertFalse(release.isValidRoll("220700", 2026))
        assertFalse(release.isValidRoll("22070a1", 2026))
        assertFalse(release.isValidRoll("3007001", 2026))
    }

    @Test
    fun teacherNeedsStaffEmail() {
        assertNull(release.validate(UserRole.TEACHER, "rahim@cse.kuet.ac.bd", "E-101", 2026))
        assertNull(release.validate(UserRole.TEACHER, "rahim@kuet.ac.bd", "E-101", 2026))
        assertNotNull(release.validate(UserRole.TEACHER, "rahim@stud.kuet.ac.bd", "E-101", 2026))
        assertNotNull(release.validate(UserRole.TEACHER, "rahim@cse.kuet.ac.bd", "", 2026))
    }

    @Test
    fun demoAccountsOnlyInDebugBuilds() {
        assertNull(debug.validate(UserRole.STUDENT, "student1@test.com", "2207001", 2026))
        assertNotNull(release.validate(UserRole.STUDENT, "student1@test.com", "2207001", 2026))
        assertFalse(debug.needsEmailVerification(UserRole.STUDENT, "student1@test.com"))
        assertTrue(release.needsEmailVerification(UserRole.STUDENT, "student1@test.com"))
    }

    @Test
    fun adminsCannotSignUp() {
        assertNotNull(debug.validate(UserRole.ADMIN, "admin@test.com", "1", 2026))
        assertEquals(listOf(UserRole.STUDENT, UserRole.TEACHER, UserRole.DRIVER), UserRole.signUpRoles)
    }
}
