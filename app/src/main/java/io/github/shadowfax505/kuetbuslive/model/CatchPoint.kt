package io.github.shadowfax505.kuetbuslive.model

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

// "Best place to wait" and "Best place to get off", the same planners as the iOS app
// (CatchPoint.swift there), so both apps give riders the same answers.

data class RoutePoint(val name: String, val position: LatLng, val along: Double, val kind: Kind) {
    sealed interface Kind {
        data class Stop(val index: Int) : Kind                    // an official stop
        data class Spot(val stops: Int, val asks: Int) : Kind     // riders waved or got off here before
        data object Nearest : Kind                                // the route's nearest point
    }

    // How likely the bus stops here when asked, 0..1.
    fun stopChance(driverWaveRate: Double): Double = when (kind) {
        is Kind.Stop -> 1.0
        is Kind.Spot -> (kind.stops + 1).toDouble() / (kind.asks + 2)
        Kind.Nearest -> driverWaveRate * 0.8
    }
}

// How fast this rider walks, learned on the phone from their own steps (never uploaded).
data class WalkingSpeed(val stat: RunningStat = RunningStat()) {
    val metresPerSecond: Double
        get() {
            val weight = minOf(stat.n / 20.0, 1.0)
            return weight * stat.mean + (1 - weight) * DEFAULT_SPEED
        }

    // Only speeds that look like walking.
    fun add(speed: Double): WalkingSpeed = if (speed < 0.5 || speed > 2.5) this else WalkingSpeed(stat.add(speed))

    fun seconds(straightLineMetres: Double): Double = straightLineMetres * DETOUR / metresPerSecond

    companion object {
        const val DEFAULT_SPEED = 1.3
        const val DETOUR = 1.3
    }
}

data class CatchPlan(
    val point: RoutePoint,
    val busSeconds: Double,
    val walkSeconds: Double,
    val marginSeconds: Double,
    val catchChance: Double,
    val samples: Int,
) {
    val leaveInSeconds: Double get() = max(busSeconds - walkSeconds - marginSeconds, 0.0)
    val leaveInMinutes: Int get() = (leaveInSeconds / 60).toInt()
    val walkMinutes: Int get() = max(ceil(walkSeconds / 60).toInt(), 1)
}

data class DropPlan(
    val point: RoutePoint,
    val busSeconds: Double,
    val walkSeconds: Double,
    val arrivalSeconds: Double,
    val samples: Int,
) {
    val walkMinutes: Int get() = max(ceil(walkSeconds / 60).toInt(), 1)
}

object CatchPlanner {
    const val WALK_COST_PER_MINUTE = 0.04
    const val MINIMUM_CHANCE = 0.5
    const val MISSED_STOP_PENALTY = 180.0

    // Stops and learned spots ahead of `start`, plus the route's nearest point to `place`.
    fun candidates(line: RouteLine, stopNames: List<String>, spots: List<RoutePoint>, start: Double, place: LatLng): List<RoutePoint> {
        val points = mutableListOf<RoutePoint>()
        line.stopAlong.forEachIndexed { index, along ->
            if (along > start && index < stopNames.size) {
                points += RoutePoint(stopNames[index], line.pointAt(along), along, RoutePoint.Kind.Stop(index))
            }
        }
        points += spots.filter { it.along > start }
        val snap = Geo.snap(place, line.path)
        if (snap != null && snap.distanceAlong > start) {
            val near = stopNames.zip(line.stopAlong).minByOrNull { abs(it.second - snap.distanceAlong) }
            points += RoutePoint(near?.first ?: "", snap.point, snap.distanceAlong, RoutePoint.Kind.Nearest)
        }
        return points
    }

    fun bestCatch(
        rider: LatLng, busAlong: Double, candidates: List<RoutePoint>, timing: RouteTiming,
        walking: WalkingSpeed, driverWaveRate: Double, startsIn: Double = 0.0,
    ): CatchPlan? {
        var best: Pair<CatchPlan, Double>? = null
        val marginK = Margin.k(timing.profile.calibration(timing.line.routeId))
        for (point in candidates) {
            if (point.along <= busAlong) continue
            val bus = timing.estimate(busAlong, point.along)
            val busSeconds = startsIn + bus.seconds
            val walk = walking.seconds(Geo.distanceMetres(rider, point.position))
            val chance = Margin.probabilityPositive(busSeconds - walk, sqrt(bus.totalVariance))
            if (chance < MINIMUM_CHANCE) continue
            val score = chance * point.stopChance(driverWaveRate) - WALK_COST_PER_MINUTE * walk / 60
            val plan = CatchPlan(point, busSeconds, walk, Margin.seconds(bus, marginK), chance, bus.samples)
            if (best == null || score > best.second) best = plan to score
        }
        return best?.first
    }

    fun bestDrop(
        destination: LatLng, busAlong: Double, boardAlong: Double, candidates: List<RoutePoint>,
        timing: RouteTiming, walking: WalkingSpeed, driverWaveRate: Double,
    ): DropPlan? {
        var best: Pair<DropPlan, Double>? = null
        for (point in candidates) {
            if (point.along <= max(busAlong, boardAlong)) continue
            val bus = timing.estimate(busAlong, point.along)
            val kind = point.kind
            val stopping = if (kind is RoutePoint.Kind.Stop) {
                timing.profile.dwell(timing.line.routeId, timing.line.routeStopIndex(kind.index)).seconds / 2
            } else SegmentProfile.DEFAULT_DWELL.seconds
            val walk = walking.seconds(Geo.distanceMetres(point.position, destination))
            val arrival = bus.seconds + stopping + walk
            val cost = arrival + (1 - point.stopChance(driverWaveRate)) * MISSED_STOP_PENALTY
            val plan = DropPlan(point, bus.seconds, walk, arrival, bus.samples)
            if (best == null || cost < best.second) best = plan to cost
        }
        return best?.first
    }
}

// One way to get somewhere today: a running bus, or a trip that hasn't started.
data class TripOption(val title: String, val startsIn: Double, val catchPlan: CatchPlan?, val dropPlan: DropPlan) {
    val arrivalSeconds: Double get() = startsIn + dropPlan.arrivalSeconds

    companion object {
        fun ranked(options: List<TripOption>): List<TripOption> = options.sortedBy { it.arrivalSeconds }
    }
}
