package io.github.shadowfax505.kuetbuslive.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    @Test
    fun bearingPointsTheRightWay() {
        val a = LatLng(22.90, 89.50)
        assertEquals(0.0, Geo.bearing(a, LatLng(22.91, 89.50)), 0.5)
        assertEquals(90.0, Geo.bearing(a, LatLng(22.90, 89.51)), 0.5)
        assertEquals(180.0, Geo.bearing(a, LatLng(22.89, 89.50)), 0.5)
    }

    @Test
    fun turnsTakeTheShortWayRound() {
        assertEquals(20.0, Geo.angleDelta(350.0, 10.0), 1e-9)
        assertEquals(-20.0, Geo.angleDelta(10.0, 350.0), 1e-9)
        assertEquals(0.0, Geo.lerpAngle(350.0, 10.0, 0.5), 1e-9)
    }

    @Test
    fun snapsOntoTheRoute() {
        val path = listOf(LatLng(22.90, 89.50), LatLng(22.90, 89.52))
        val snap = Geo.snap(LatLng(22.901, 89.51), path)!!
        assertEquals(22.90, snap.point.lat, 1e-9)
        assertEquals(89.51, snap.point.lng, 1e-9)
        assertTrue(snap.distanceFromRoute in 100.0..120.0)
    }

    @Test
    fun riderAheadIsPositiveAndPassedIsNegative() {
        val path = listOf(LatLng(22.90, 89.50), LatLng(22.90, 89.52))
        val bus = LatLng(22.90, 89.51)
        assertTrue(Geo.metresAhead(bus, LatLng(22.90, 89.515), path)!! > 0)
        assertTrue(Geo.metresAhead(bus, LatLng(22.90, 89.505), path)!! < 0)
    }
}
