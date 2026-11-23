package io.github.shadowfax505.kuetbuslive.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendPolicyTest {
    private val stop = LatLng(22.9000, 89.5000)
    private val moved = LatLng(22.9003, 89.5000)   // about 33 m north
    private val nudged = LatLng(22.90005, 89.5000) // about 6 m north

    @Test
    fun firstFixIsAlwaysSent() {
        assertTrue(SendPolicy.shouldSend(10_000, 0, null, stop))
    }

    @Test
    fun neverFasterThanTheInterval() {
        assertFalse(SendPolicy.shouldSend(4_000, 0, stop, moved))
        assertTrue(SendPolicy.shouldSend(5_000, 0, stop, moved))
    }

    @Test
    fun standingStillSendsOnlyTheHeartbeat() {
        assertFalse(SendPolicy.shouldSend(10_000, 0, stop, nudged))
        assertFalse(SendPolicy.shouldSend(19_999, 0, stop, stop))
        assertTrue(SendPolicy.shouldSend(20_000, 0, stop, stop))
    }
}
