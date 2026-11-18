package io.github.shadowfax505.kuetbuslive.model

import io.github.shadowfax505.kuetbuslive.data.RouteInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// The ETA estimate used when the routing server can't be reached (same as iOS).
class EtaFallbackTest {
    @Test
    fun usesTheDistanceAlongTheRouteAt18KmPerHour() {
        val info = RouteInfo.estimate(alongRoute = 3_000.0, straightLine = 2_000.0)!!
        assertEquals(10, info.minutes)
        assertEquals(3.0, info.kilometres, 0.001)
    }

    @Test
    fun withoutARouteTheStraightLineIsMadeLongerForTheRoads() {
        assertEquals(2_700.0, RouteInfo.estimate(null, 2_000.0)!!.distanceMetres, 0.001)
    }

    @Test
    fun aBusThatPassedTheStopHasNoEstimate() {
        assertNull(RouteInfo.estimate(-50.0, 100.0))
    }
}
