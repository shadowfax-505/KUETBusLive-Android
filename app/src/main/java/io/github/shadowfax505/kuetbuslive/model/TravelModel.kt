package io.github.shadowfax505.kuetbuslive.model

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// How long the bus takes between stops, learned from real trips. The same model and
// the same Firestore document IDs as the iOS app (TravelModel.swift there), so both
// apps learn from and read the same numbers. docs/catch-point.md in the iOS
// repository explains the formulas.

// An exponentially weighted average and variance. The first ten trips count equally;
// after that each new trip keeps a weight of one tenth.
data class RunningStat(val n: Int = 0, val mean: Double = 0.0, val variance: Double = 0.0) {
    fun add(x: Double): RunningStat {
        val count = n + 1
        val weight = max(1.0 / count, MIN_WEIGHT)
        val difference = x - mean
        return RunningStat(
            n = count,
            mean = mean + weight * difference,
            variance = (1 - weight) * (variance + weight * difference * difference),
        )
    }

    val standardDeviation: Double get() = sqrt(max(variance, 0.0))

    companion object {
        const val MIN_WEIGHT = 0.1
    }
}

// The kind of day used for learning: Sunday to Thursday, Saturday or Friday.
enum class DayType(val raw: String) {
    SUN_TO_THU("sunToThu"), SATURDAY("saturday"), FRIDAY("friday");

    companion object {
        fun of(millis: Long, zone: ZoneId = Timetable.dhaka): DayType =
            when (Instant.ofEpochMilli(millis).atZone(zone).dayOfWeek.value) {
                6 -> SATURDAY
                5 -> FRIDAY
                else -> SUN_TO_THU
            }
    }
}

// Kind of day plus the half-hour slot: "sunToThu-16" is Sunday to Thursday, 8:00 to 8:30.
// A day the admin marked special ("exam", "holiday", "event") is learned on its own.
data class TimeBucket(val dayType: DayType, val slot: Int, val special: String? = null) {
    val key: String get() = "${special ?: dayType.raw}-$slot"

    // The same half-hour on an ordinary day of this kind.
    val ordinary: TimeBucket get() = TimeBucket(dayType, slot)

    companion object {
        const val SLOT_MINUTES = 30

        fun of(millis: Long, special: String? = null, zone: ZoneId = Timetable.dhaka): TimeBucket {
            val time = Instant.ofEpochMilli(millis).atZone(zone)
            return TimeBucket(DayType.of(millis, zone), (time.hour * 60 + time.minute) / SLOT_MINUTES, special)
        }
    }
}

// Where an estimate came from, from most to least specific.
enum class EstimateSource { DRIVER_SLOT, SLOT, ROUTE, ROUTER }

data class Estimate(val seconds: Double, val variance: Double, val samples: Int, val source: EstimateSource) {
    // The spread, plus how unsure the average itself is with few trips.
    val totalVariance: Double get() = if (samples > 0) variance + variance / samples else variance
}

// The segmentStats document IDs, character for character the same as on iOS.
// "t" is a stretch in the route's own stop order, "u" the same road driven back.
object StatKey {
    fun letter(reversed: Boolean): String = if (reversed) "u" else "t"

    fun travel(route: String, segment: Int, bucket: TimeBucket, driver: String, reversed: Boolean = false): String =
        "${route}_${letter(reversed)}${segment}_${bucket.key}_$driver"

    fun travel(route: String, segment: Int, bucket: TimeBucket, reversed: Boolean = false): String =
        "${route}_${letter(reversed)}${segment}_${bucket.key}"

    fun travel(route: String, segment: Int, reversed: Boolean = false): String =
        "${route}_${letter(reversed)}${segment}_all"

    fun dwell(route: String, stop: Int): String = "${route}_d${stop}_all"

    // How late a timetable trip usually leaves its first stop. `schedule` is "t" or "u" + the schedule ID.
    fun late(route: String, schedule: String): String = "${route}_late_$schedule"

    fun calibration(route: String): String = "${route}_calib_all"
}

data class SegmentProfile(val stats: Map<String, RunningStat>) {

    fun travel(
        route: String, segment: Int, bucket: TimeBucket, driver: String?, metres: Double,
        routerSpeed: Double? = null, reversed: Boolean = false,
    ): Estimate {
        val keys = mutableListOf<Pair<String, EstimateSource>>()
        // On a special day, that day's own numbers first, then an ordinary day's.
        val buckets = if (bucket.special == null) listOf(bucket) else listOf(bucket, bucket.ordinary)
        for (b in buckets) {
            if (driver != null) keys += StatKey.travel(route, segment, b, driver, reversed) to EstimateSource.DRIVER_SLOT
            keys += StatKey.travel(route, segment, b, reversed) to EstimateSource.SLOT
        }
        keys += StatKey.travel(route, segment, reversed) to EstimateSource.ROUTE
        for ((key, source) in keys) {
            val stat = stats[key]
            if (stat != null && stat.n >= MIN_SAMPLES) return Estimate(stat.mean, stat.variance, stat.n, source)
        }
        val speed = if ((routerSpeed ?: 0.0) > 1) routerSpeed!! else DEFAULT_SPEED
        val seconds = metres / speed
        val spread = seconds * ROUTER_SPREAD
        return Estimate(seconds, spread * spread, 0, EstimateSource.ROUTER)
    }

    // Seconds a timetable trip usually leaves late (0 until 3 trips are known).
    fun lateness(route: String, schedule: String): Double {
        val stat = stats[StatKey.late(route, schedule)] ?: return 0.0
        return if (stat.n >= MIN_SAMPLES) stat.mean else 0.0
    }

    fun calibration(route: String): RunningStat? = stats[StatKey.calibration(route)]

    fun dwell(route: String, stop: Int): Estimate {
        val stat = stats[StatKey.dwell(route, stop)]
        if (stat != null && stat.n >= MIN_SAMPLES) return Estimate(stat.mean, stat.variance, stat.n, EstimateSource.ROUTE)
        return DEFAULT_DWELL
    }

    companion object {
        const val MIN_SAMPLES = 3
        const val DEFAULT_SPEED = 5.5
        const val ROUTER_SPREAD = 0.33
        val DEFAULT_DWELL = Estimate(20.0, 15.0 * 15.0, 0, EstimateSource.ROUTER)
        val EMPTY = SegmentProfile(emptyMap())
    }
}

// A route laid out along its road path: where each stop sits, in metres from the start.
// `reversed` lays it out the other way round, for a bus driving back to KUET.
class RouteLine(val routeId: String, path: List<LatLng>, stops: List<LatLng>, val reversed: Boolean = false) {
    val path: List<LatLng>
    val stops: List<LatLng>
    val stopAlong: List<Double>

    init {
        val forward = if (path.size >= 2) path else stops
        this.path = if (reversed) forward.reversed() else forward
        this.stops = if (reversed) stops.reversed() else stops
        val along = mutableListOf<Double>()
        for (stop in this.stops) {
            // Stops come in order, so never let one sit before the previous one.
            val value = Geo.snap(stop, this.path)?.distanceAlong ?: 0.0
            along += max(value, along.lastOrNull() ?: 0.0)
        }
        stopAlong = along
    }

    val length: Double get() = stopAlong.lastOrNull() ?: 0.0

    // The stop's number in the route's own order, whichever way the line is laid out.
    fun routeStopIndex(index: Int): Int = if (reversed) stopAlong.size - 1 - index else index

    fun flipped(): RouteLine {
        val forwardPath = if (reversed) path.reversed() else path
        val forwardStops = if (reversed) stops.reversed() else stops
        return RouteLine(routeId, forwardPath, forwardStops, !reversed)
    }

    // Compass bearing of the path where it passes closest to `point`.
    fun bearing(point: LatLng): Double? {
        if (path.size < 2) return null
        val snap = Geo.snap(point, path) ?: return null
        val a = pointAt(max(snap.distanceAlong - 15, 0.0))
        val b = pointAt(snap.distanceAlong + 15)
        val dLng = (b.lng - a.lng) * cos(Math.toRadians(a.lat))
        val dLat = b.lat - a.lat
        if (dLng == 0.0 && dLat == 0.0) return null
        val degrees = Math.toDegrees(atan2(dLng, dLat))
        return if (degrees < 0) degrees + 360 else degrees
    }

    // A bus heading more than 90 degrees off the road's direction drives the other way.
    fun isAgainst(heading: Double, point: LatLng): Boolean {
        val road = bearing(point) ?: return false
        val difference = abs(((heading - road + 540) % 360) - 180)
        return difference > 90
    }

    fun along(point: LatLng): Double = Geo.snap(point, path)?.distanceAlong ?: 0.0

    // The coordinate at a distance along the path.
    fun pointAt(distance: Double): LatLng {
        if (path.size < 2) return path.firstOrNull() ?: LatLng(0.0, 0.0)
        var travelled = 0.0
        for (i in 0 until path.size - 1) {
            val a = path[i]
            val b = path[i + 1]
            val piece = Geo.distanceMetres(a, b)
            if (travelled + piece >= distance && piece > 0) {
                return Geo.lerp(a, b, (distance - travelled) / piece)
            }
            travelled += piece
        }
        return path.last()
    }
}

// "When does the bus reach this point, and how sure are we?"
data class RouteTiming(
    val line: RouteLine,
    val profile: SegmentProfile,
    val bucket: TimeBucket,
    val driver: String?,
    val routerSpeed: Double? = null,
    // Today's bus compared with the usual: 1.2 means 20% slower.
    val liveFactor: Double = 1.0,
) {
    // Travel on each stretch it covers (in proportion), plus the usual stop time at every stop it passes.
    fun estimate(start: Double, end: Double): Estimate {
        if (end <= start || line.stopAlong.size < 2) return Estimate(0.0, 0.0, 0, EstimateSource.ROUTER)
        var seconds = 0.0
        var variance = 0.0
        var fewest = Int.MAX_VALUE
        var weakest = EstimateSource.DRIVER_SLOT
        for (segment in 0 until line.stopAlong.size - 1) {
            val a = line.stopAlong[segment]
            val b = line.stopAlong[segment + 1]
            val overlap = min(end, b) - max(start, a)
            if (overlap <= 0 || b <= a) continue
            val fraction = overlap / (b - a)
            val part = profile.travel(line.routeId, segment, bucket, driver, b - a, routerSpeed, line.reversed)
            seconds += fraction * part.seconds * liveFactor
            variance += fraction * part.totalVariance * liveFactor * liveFactor
            fewest = min(fewest, part.samples)
            if (part.source > weakest) weakest = part.source
        }
        // Stops strictly between the two points: the bus usually pauses there.
        line.stopAlong.forEachIndexed { index, along ->
            if (along > start + 1 && along < end - 1) {
                val dwell = profile.dwell(line.routeId, line.routeStopIndex(index))
                seconds += dwell.seconds
                variance += dwell.totalVariance
            }
        }
        return Estimate(seconds, variance, if (fewest == Int.MAX_VALUE) 0 else fewest, weakest)
    }
}

// The safety margin: how much earlier than the average to be there.
object Margin {
    const val K = 1.2816
    const val MINIMUM_SECONDS = 30.0
    const val MIN_CALIBRATION_TRIPS = 10

    // k checked against real trips once there are enough: 1.28 x spread of z - average of z.
    fun k(calibration: RunningStat?): Double {
        if (calibration == null || calibration.n < MIN_CALIBRATION_TRIPS) return K
        return (K * calibration.standardDeviation - calibration.mean).coerceIn(0.8, 3.0)
    }

    fun seconds(estimate: Estimate, k: Double = K): Double = max(k * sqrt(estimate.totalVariance), MINIMUM_SECONDS)

    // Chance a normal variable with this mean and spread is above zero.
    fun probabilityPositive(mean: Double, sd: Double): Double {
        if (sd <= 0) return if (mean >= 0) 1.0 else 0.0
        return 0.5 * (1 + erf(mean / (sd * sqrt(2.0))))
    }

    // Error function (Abramowitz and Stegun 7.1.26, error below 1.5e-7).
    fun erf(x: Double): Double {
        val sign = if (x < 0) -1.0 else 1.0
        val ax = abs(x)
        val t = 1.0 / (1.0 + 0.3275911 * ax)
        val y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-ax * ax)
        return sign * y
    }
}

// Today's bus against the usual, from the positions seen while the map is open:
// factor = real seconds / expected seconds over the last 10 minutes, pulled towards 1
// until the bus has covered 2 km, and kept within 0.6 ... 1.8.
class LiveCorrection {
    data class Point(val millis: Long, val along: Double)

    private val points = mutableListOf<Point>()

    fun add(millis: Long, along: Double) {
        val last = points.lastOrNull()
        if (last != null && (along <= last.along + 5 || millis <= last.millis)) return
        points += Point(millis, along)
        points.removeAll { millis - it.millis > WINDOW_MILLIS }
    }

    fun factor(timing: RouteTiming): Double {
        val first = points.firstOrNull() ?: return 1.0
        val last = points.last()
        if (last.along - first.along <= 100) return 1.0
        val expected = timing.copy(liveFactor = 1.0).estimate(first.along, last.along).seconds
        if (expected <= 0) return 1.0
        val real = (last.millis - first.millis) / 1000.0
        val raw = (real / expected).coerceIn(0.6, 1.8)
        val trust = min((last.along - first.along) / TRUSTED_METRES, 1.0)
        return 1 + trust * (raw - 1)
    }

    companion object {
        const val WINDOW_MILLIS = 10 * 60 * 1000L
        const val TRUSTED_METRES = 2000.0
    }
}
