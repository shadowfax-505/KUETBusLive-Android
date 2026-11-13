package io.github.shadowfax505.kuetbuslive.model

import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// The same cases as the iOS TripLearnerTests.
class TripLearnerTest {
    private val base = LatLng(22.90, 89.50)
    private val start = 1_800_000_000_000L

    private fun north(metres: Double) = LatLng(base.lat + metres / 111_320, base.lng)

    private val line get() = listOf(north(0.0), north(1000.0), north(2000.0), north(3000.0)).let { RouteLine("r1", it, it) }

    // A bus at 10 m/s that stands for 30 s at B (1000 m) and 20 s at 1500 m.
    private fun track(): List<GpsSample> {
        val samples = mutableListOf<GpsSample>()
        var t = 0.0
        var position = 0.0
        val paused = ArrayDeque(listOf(1000.0 to 30.0, 1500.0 to 20.0))
        while (position <= 3100) {
            samples += GpsSample(start + (t * 1000).toLong(), north(position), 10.0)
            val pause = paused.firstOrNull()
            if (pause != null && position >= pause.first) {
                var step = 2.0
                while (step <= pause.second) {
                    samples += GpsSample(start + ((t + step) * 1000).toLong(), north(position), 0.0)
                    step += 2
                }
                t += pause.second
                paused.removeFirst()
            }
            t += 2
            position += 20
        }
        return samples
    }

    @Test
    fun stretchTimesLeaveOutStopTime() {
        val result = TripLearner.learn(track(), line, emptyList())
        assertEquals(listOf(0, 1, 2), result.stretches.map { it.segment })
        assertEquals(96.0, result.stretches[0].seconds, 1.0)
        assertEquals(116.0, result.stretches[1].seconds, 1.0)
        assertEquals(96.0, result.stretches[2].seconds, 1.0)
    }

    @Test
    fun stopTimeAtTheMiddleStops() {
        val result = TripLearner.learn(track(), line, emptyList())
        assertEquals(34.0, result.stopTimes.first { it.stop == 1 }.seconds, 1.0)
        assertFalse(result.stopTimes.any { it.stop == 0 || it.stop == 3 })
    }

    @Test
    fun waveOutcomes() {
        val waves = listOf(SeenWave(north(1505.0), start), SeenWave(north(2500.0), start), SeenWave(north(5000.0), start))
        val result = TripLearner.learn(track(), line, waves)
        assertEquals(2, result.waves.size)
        assertTrue(result.waves[0].stopped)
        assertEquals(20.0, result.waves[0].seconds, 0.1)
        assertFalse(result.waves[1].stopped)
    }

    @Test
    fun backwardGpsWobbleIsIgnored() {
        val samples = track().toMutableList()
        samples[10] = samples[10].copy(position = north(0.0))
        assertEquals(3, TripLearner.learn(samples, line, emptyList()).stretches.size)
    }

    @Test
    fun aTripDrivenBackIsLearnedOnTheFlippedLine() {
        val back = track().map { it.copy(position = north(3000 - (it.position.lat - base.lat) * 111_320)) }
        val result = TripLearner.learn(back, line, emptyList())
        assertTrue(result.reversed)
        assertEquals(listOf(0, 1, 2), result.stretches.map { it.segment })
        assertEquals(2, result.stopTimes.first().stop)
        assertNotEquals(StatKey.travel("r1", 0, reversed = true), StatKey.travel("r1", 0))
    }

    @Test
    fun directionFromHeading() {
        assertFalse(line.isAgainst(5.0, north(1500.0)))
        assertTrue(line.isAgainst(185.0, north(1500.0)))
    }

    @Test
    fun tooFewSamplesLearnNothing() {
        assertEquals(TripObservations(), TripLearner.learn(emptyList(), line, emptyList()))
    }

    @Test
    fun spotKeysGroupNearbyPointsLikeIos() {
        val a = SpotKey.id("r1", LatLng(22.88901, 89.50951))
        val b = SpotKey.id("r1", LatLng(22.88905, 89.50949))
        assertEquals(a, b)
        assertEquals("r1_22.8891_89.5095", a)
    }

    @Test
    fun latenessAgainstTheTimetableInDhaka() {
        val left = ZonedDateTime.of(2026, 11, 15, 7, 4, 30, 0, Timetable.dhaka).toInstant().toEpochMilli()
        assertEquals(270.0, TripLearner.lateness(left, "07:00")!!, 0.001)
        assertEquals(0.0, TripLearner.lateness(left, "07:10")!!, 0.0)
        assertNull(TripLearner.lateness(left, "13:00"))
    }

    @Test
    fun predictionErrorsAreInSpreads() {
        val result = TripLearner.learn(track(), line, emptyList())
        val errors = TripLearner.predictionErrors(result, RouteTiming(line, SegmentProfile.EMPTY, TimeBucket(DayType.SUN_TO_THU, 16), null))
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.all { it in -20.0..20.0 })
    }
}
