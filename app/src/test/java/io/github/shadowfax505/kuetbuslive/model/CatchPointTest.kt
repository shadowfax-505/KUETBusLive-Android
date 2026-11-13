package io.github.shadowfax505.kuetbuslive.model

import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// The same cases as the iOS CatchPointTests, so both apps answer alike.
class CatchPointTest {
    private val base = LatLng(22.90, 89.50)

    private fun north(metres: Double, east: Double = 0.0) =
        LatLng(base.lat + metres / 111_320, base.lng + east / (111_320 * cos(Math.toRadians(base.lat))))

    private val stops get() = listOf(north(0.0), north(1000.0), north(2000.0), north(3000.0))
    private val names = listOf("A", "B", "C", "D")
    private val line get() = RouteLine("r1", stops, stops)
    private val bucket = TimeBucket(DayType.SUN_TO_THU, 16)

    private fun timing(stats: Map<String, RunningStat> = emptyMap(), driver: String? = null) =
        RouteTiming(line, SegmentProfile(stats), bucket, driver)

    private fun stat(values: List<Double>) = values.fold(RunningStat()) { s, v -> s.add(v) }

    @Test
    fun runningStatMatchesPlainAverageForFewSamples() {
        val s = stat(listOf(100.0, 200.0, 300.0))
        assertEquals(3, s.n)
        assertEquals(200.0, s.mean, 1e-9)
        assertEquals(20_000.0 / 3, s.variance, 1e-6)
    }

    @Test
    fun runningStatFollowsChangeAfterTenTrips() {
        var s = stat(List(30) { 100.0 })
        repeat(10) { s = s.add(200.0) }
        assertTrue(s.mean > 160 && s.mean < 200)
    }

    @Test
    fun timeBucketUsesHalfHoursInDhaka() {
        val millis = ZonedDateTime.of(2026, 11, 15, 8, 20, 0, 0, Timetable.dhaka).toInstant().toEpochMilli()
        assertEquals("sunToThu-16", TimeBucket.of(millis).key)
        assertEquals("exam-16", TimeBucket.of(millis, "exam").key)
        val friday = ZonedDateTime.of(2026, 11, 13, 8, 0, 0, 0, Timetable.dhaka).toInstant().toEpochMilli()
        assertEquals("friday-16", TimeBucket.of(friday).key)
    }

    @Test
    fun documentIdsMatchTheIosApp() {
        assertEquals("r1_t0_sunToThu-16_d1", StatKey.travel("r1", 0, bucket, "d1"))
        assertEquals("r1_u2_sunToThu-16", StatKey.travel("r1", 2, bucket, reversed = true))
        assertEquals("r1_t0_all", StatKey.travel("r1", 0))
        assertEquals("r1_d1_all", StatKey.dwell("r1", 1))
        assertEquals("r1_late_ts9", StatKey.late("r1", StatKey.letter(false) + "s9"))
        assertEquals("r1_calib_all", StatKey.calibration("r1"))
    }

    @Test
    fun fallbackPrefersDriverThenSlotThenRouteThenRouter() {
        val driverKey = StatKey.travel("r1", 0, bucket, "d1")
        val slotKey = StatKey.travel("r1", 0, bucket)
        val routeKey = StatKey.travel("r1", 0)
        var stats = mapOf(driverKey to stat(listOf(60.0, 60.0, 60.0)), slotKey to stat(listOf(90.0, 90.0, 90.0)),
            routeKey to stat(listOf(120.0, 120.0, 120.0)))
        fun estimate(driver: String?) = SegmentProfile(stats).travel("r1", 0, bucket, driver, 1000.0)
        assertEquals(EstimateSource.DRIVER_SLOT, estimate("d1").source)
        assertEquals(60.0, estimate("d1").seconds, 1e-9)
        assertEquals(EstimateSource.SLOT, estimate("d2").source)
        stats = stats + (slotKey to stat(listOf(90.0, 90.0)))
        assertEquals(EstimateSource.ROUTE, estimate("d2").source)
        assertEquals(120.0, estimate("d2").seconds, 1e-9)
        stats = emptyMap()
        val router = estimate(null)
        assertEquals(EstimateSource.ROUTER, router.source)
        assertEquals(1000 / SegmentProfile.DEFAULT_SPEED, router.seconds, 1e-6)
        assertEquals(0, router.samples)
    }

    @Test
    fun specialDayFallsBackToTheOrdinaryDay() {
        val exam = TimeBucket(DayType.SUN_TO_THU, 16, "exam")
        val stats = mapOf(StatKey.travel("r1", 0, bucket) to stat(listOf(90.0, 90.0, 90.0)))
        assertEquals(90.0, SegmentProfile(stats).travel("r1", 0, exam, null, 1000.0).seconds, 1e-9)
    }

    @Test
    fun routerSpeedIsUsedWhenNoTripsYet() {
        assertEquals(100.0, SegmentProfile.EMPTY.travel("r1", 0, bucket, null, 1000.0, 10.0).seconds, 1e-6)
    }

    @Test
    fun timingAddsStretchesAndStopTimes() {
        val stats = (0 until 3).associate { StatKey.travel("r1", it) to stat(listOf(100.0, 100.0, 100.0)) } +
            (StatKey.dwell("r1", 1) to stat(listOf(30.0, 30.0, 30.0)))
        val estimate = timing(stats).estimate(500.0, 2000.0)
        assertEquals(180.0, estimate.seconds, 1.0)
        assertEquals(EstimateSource.ROUTE, estimate.source)
    }

    @Test
    fun marginShrinksWithMoreTrips() {
        val few = Estimate(300.0, 3600.0, 3, EstimateSource.SLOT)
        val many = Estimate(300.0, 3600.0, 50, EstimateSource.SLOT)
        assertTrue(Margin.seconds(few) > Margin.seconds(many))
        assertEquals(1.2816 * sqrt(3600.0 + 72), Margin.seconds(many), 0.01)
        assertEquals(30.0, Margin.seconds(Estimate(60.0, 0.0, 9, EstimateSource.SLOT)), 0.0)
    }

    @Test
    fun ninetyPercentMarginGivesNinetyPercentChance() {
        assertEquals(0.9, Margin.probabilityPositive(1.2816 * 60, 60.0), 0.001)
        assertEquals(0.5, Margin.probabilityPositive(0.0, 60.0), 1e-7)
    }

    @Test
    fun catchPrefersTheNearStopTheRiderCanReach() {
        val rider = north(2000.0, east = 150.0)
        val points = CatchPlanner.candidates(line, names, emptyList(), 0.0, rider)
        val plan = CatchPlanner.bestCatch(rider, 0.0, points, timing(), WalkingSpeed(), 0.5)!!
        assertEquals("C", plan.point.name)
        assertEquals(RoutePoint.Kind.Stop(2), plan.point.kind)
        assertTrue(plan.catchChance > 0.9)
        assertTrue(plan.leaveInSeconds > 0)
    }

    @Test
    fun catchSkipsAPointTheBusReachesFirst() {
        val stats = (0 until 3).associate { StatKey.travel("r1", it) to stat(listOf(600.0, 600.0, 600.0)) }
        val rider = north(1300.0, east = 100.0)
        val points = CatchPlanner.candidates(line, names, emptyList(), 900.0, rider)
        val plan = CatchPlanner.bestCatch(rider, 900.0, points, timing(stats), WalkingSpeed(), 0.5)!!
        assertEquals(RoutePoint.Kind.Nearest, plan.point.kind)
        assertEquals(1300.0, plan.point.along, 5.0)
        assertEquals("B", plan.point.name)
        assertTrue(plan.catchChance >= CatchPlanner.MINIMUM_CHANCE)
    }

    @Test
    fun noPlanWhenTheBusIsTooCloseEverywhere() {
        val rider = north(1000.0, east = 600.0)
        val points = CatchPlanner.candidates(line, names, emptyList(), 900.0, rider)
        assertNull(CatchPlanner.bestCatch(rider, 900.0, points, timing(), WalkingSpeed(), 0.5))
    }

    @Test
    fun learnedSpotWhereDriversStopBeatsAFartherStop() {
        val spot = RoutePoint("Gate 2", north(1700.0), 1700.0, RoutePoint.Kind.Spot(9, 10))
        val rider = north(1700.0, east = 40.0)
        val points = CatchPlanner.candidates(line, names, listOf(spot), 0.0, rider).filter { it.kind != RoutePoint.Kind.Nearest }
        assertEquals("Gate 2", CatchPlanner.bestCatch(rider, 0.0, points, timing(), WalkingSpeed(), 0.5)?.point?.name)
    }

    @Test
    fun stopChanceFromPastOutcomes() {
        assertEquals(0.5, RoutePoint("x", base, 0.0, RoutePoint.Kind.Spot(0, 0)).stopChance(0.9), 0.0)
        assertEquals(19.0 / 22.0, RoutePoint("x", base, 0.0, RoutePoint.Kind.Spot(18, 20)).stopChance(0.0), 1e-9)
    }

    @Test
    fun walkingSpeedBlendsTowardsTheRidersOwn() {
        var walking = WalkingSpeed()
        assertEquals(WalkingSpeed.DEFAULT_SPEED, walking.metresPerSecond, 0.0)
        walking = walking.add(9.0).add(0.1)
        assertEquals(0, walking.stat.n)
        repeat(40) { walking = walking.add(1.0) }
        assertEquals(1.0, walking.metresPerSecond, 1e-6)
    }

    @Test
    fun dropPicksTheStopClosestToTheDestination() {
        val destination = north(2950.0, east = 200.0)
        val points = CatchPlanner.candidates(line, names, emptyList(), 0.0, destination).filter { it.kind != RoutePoint.Kind.Nearest }
        val plan = CatchPlanner.bestDrop(destination, 0.0, 0.0, points, timing(), WalkingSpeed(), 0.5)!!
        assertEquals("D", plan.point.name)
        assertEquals(4, plan.walkMinutes)
    }

    @Test
    fun dropUsesANearerRoutePointWhenItSavesWalking() {
        val destination = north(1500.0, east = 50.0)
        val points = CatchPlanner.candidates(line, names, emptyList(), 0.0, destination)
        val plan = CatchPlanner.bestDrop(destination, 0.0, 0.0, points, timing(), WalkingSpeed(), 1.0)!!
        assertEquals(RoutePoint.Kind.Nearest, plan.point.kind)
        assertEquals(1500.0, plan.point.along, 5.0)
    }

    @Test
    fun dropNeverBeforeBoarding() {
        val destination = north(500.0, east = 30.0)
        val points = CatchPlanner.candidates(line, names, emptyList(), 0.0, destination)
        val plan = CatchPlanner.bestDrop(destination, 0.0, 1000.0, points, timing(), WalkingSpeed(), 1.0)
        assertTrue((plan?.point?.along ?: 0.0) > 1000)
    }

    @Test
    fun tripOptionsRankByArrival() {
        val point = RoutePoint("D", north(3000.0), 3000.0, RoutePoint.Kind.Stop(3))
        val drop = DropPlan(point, 600.0, 120.0, 720.0, 5)
        val running = TripOption("Normal-1", 0.0, null, drop)
        val later = TripOption("Normal-2", 900.0, null, drop)
        assertEquals(listOf("Normal-1", "Normal-2"), TripOption.ranked(listOf(later, running)).map { it.title })
        assertEquals(1620.0, later.arrivalSeconds, 0.0)
    }

    @Test
    fun calibratedMarginStaysInRange() {
        assertEquals(Margin.K, Margin.k(stat(listOf(0.0, 1.0))), 0.0)
        val wide = stat(List(20) { if (it % 2 == 0) -3.0 else 3.0 })
        assertEquals(3.0, Margin.k(wide), 0.0)
        assertNotEquals(Margin.K, Margin.k(wide))
    }
}
