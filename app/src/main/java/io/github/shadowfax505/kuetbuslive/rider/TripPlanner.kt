package io.github.shadowfax505.kuetbuslive.rider

import io.github.shadowfax505.kuetbuslive.model.Bus
import io.github.shadowfax505.kuetbuslive.model.BusRoute
import io.github.shadowfax505.kuetbuslive.model.BusSchedule
import io.github.shadowfax505.kuetbuslive.model.CatchPlan
import io.github.shadowfax505.kuetbuslive.model.CatchPlanner
import io.github.shadowfax505.kuetbuslive.model.DropPlan
import io.github.shadowfax505.kuetbuslive.model.Geo
import io.github.shadowfax505.kuetbuslive.model.LatLng
import io.github.shadowfax505.kuetbuslive.model.LiveCorrection
import io.github.shadowfax505.kuetbuslive.model.OnBoardDetector
import io.github.shadowfax505.kuetbuslive.model.RouteLine
import io.github.shadowfax505.kuetbuslive.model.RoutePoint
import io.github.shadowfax505.kuetbuslive.model.RouteTiming
import io.github.shadowfax505.kuetbuslive.model.SegmentProfile
import io.github.shadowfax505.kuetbuslive.model.StatKey
import io.github.shadowfax505.kuetbuslive.model.Stop
import io.github.shadowfax505.kuetbuslive.model.TimeBucket
import io.github.shadowfax505.kuetbuslive.model.Timetable
import io.github.shadowfax505.kuetbuslive.model.TripOption
import io.github.shadowfax505.kuetbuslive.model.WalkingSpeed
import kotlin.math.abs

// A place the rider wants to go.
data class Destination(val name: String, val position: LatLng)

// What the planners need from the learned numbers for one route.
data class RouteData(
    val profile: SegmentProfile,
    val spots: List<io.github.shadowfax505.kuetbuslive.data.StatsStore.SpotStat>,
    val waveRate: Double,
)

// Rider side of "best place to wait" and "best place to get off", the same as the
// iOS TripPlanner: it feeds the planners with the bus, the route, the learned numbers
// and the rider's position and walking speed. It also notices when the rider rides a
// bus and gets off, so one anonymous drop-off can be added to the shared numbers.
class TripPlanner {
    data class Plans(val catchPlan: CatchPlan?, val dropPlan: DropPlan?, val liveFactor: Double)

    private val lines = mutableMapOf<String, RouteLine>()
    private val lastAlong = mutableMapOf<String, Double>()
    private val backwards = mutableMapOf<String, Boolean>()
    private val corrections = mutableMapOf<String, Pair<Boolean, LiveCorrection>>()
    private val factors = mutableMapOf<String, Double>()

    fun line(route: BusRoute, path: List<LatLng>?): RouteLine? {
        if (route.stops.size < 2) return null
        val stops = route.stops.map { it.position }
        val cached = lines[route.id]
        // Rebuilt once the road path arrives (until then the stops are joined by straight lines).
        val pointCount = if ((path?.size ?: 0) >= 2) path!!.size else stops.size
        if (cached != null && cached.path.size == pointCount) return cached
        return RouteLine(route.id, path ?: emptyList(), stops).also { lines[route.id] = it }
    }

    // The route laid out the way this bus is driving: from its heading, or from whether it moved forwards.
    fun directedLine(bus: Bus, route: BusRoute, path: List<LatLng>?): RouteLine? {
        val forward = line(route, path) ?: return null
        val heading = bus.heading
        if (heading != null && (bus.speed ?: 0.0) > 1) {
            backwards[bus.id] = forward.isAgainst(heading, bus.position)
        } else {
            val along = forward.along(bus.position)
            val last = lastAlong[bus.id]
            if (last != null && abs(along - last) > 20) backwards[bus.id] = along < last
            lastAlong[bus.id] = along
        }
        return if (backwards[bus.id] == true) forward.flipped() else forward
    }

    private fun ordered(stops: List<Stop>, line: RouteLine): List<Stop> = if (line.reversed) stops.reversed() else stops

    private fun stopNameAt(stops: List<Stop>, line: RouteLine): (Double) -> String = { along ->
        val index = line.stopAlong.withIndex().minByOrNull { abs(it.value - along) }?.index ?: 0
        stops.getOrNull(index)?.name ?: ""
    }

    // Learned spots as route points: where drivers stopped for waves, or riders got off.
    private fun spotPoints(data: RouteData, line: RouteLine, nameAt: (Double) -> String, forDropOff: Boolean): List<RoutePoint> =
        data.spots.mapNotNull { spot ->
            if (if (forDropOff) spot.dropoffs < 2 else spot.asks < 1) return@mapNotNull null
            val snap = Geo.snap(spot.position, line.path) ?: return@mapNotNull null
            if (snap.distanceFromRoute >= 40) return@mapNotNull null
            val extra = if (forDropOff) spot.dropoffs else 0
            RoutePoint(nameAt(snap.distanceAlong), snap.point, snap.distanceAlong, RoutePoint.Kind.Spot(spot.stops + extra, spot.asks + extra))
        }

    private fun timing(bus: Bus, line: RouteLine, data: RouteData, routerSpeed: Double?, special: String?, now: Long): RouteTiming {
        var timing = RouteTiming(line, data.profile, TimeBucket.of(now, special), bus.driverId, routerSpeed)
        val entry = corrections[bus.id]?.takeIf { it.first == line.reversed } ?: (line.reversed to LiveCorrection())
        entry.second.add(bus.updatedAtMillis, line.along(bus.position))
        corrections[bus.id] = entry
        timing = timing.copy(liveFactor = entry.second.factor(timing))
        factors[bus.id] = timing.liveFactor
        return timing
    }

    // Called when the bus or the rider moves.
    fun update(
        bus: Bus, route: BusRoute, path: List<LatLng>?, data: RouteData, rider: LatLng?, destination: Destination?,
        onBoard: Boolean, boardingStop: LatLng?, walking: WalkingSpeed, routerSpeed: Double?, special: String?,
        now: Long = System.currentTimeMillis(),
    ): Plans {
        val line = directedLine(bus, route, path) ?: return Plans(null, null, 1.0)
        val stops = ordered(route.stops, line)
        val names = stops.map { it.name }
        val nameAt = stopNameAt(stops, line)
        val busAlong = line.along(bus.position)
        val timing = timing(bus, line, data, routerSpeed, special, now)

        val catchPlan = if (!onBoard && rider != null) {
            val candidates = CatchPlanner.candidates(line, names, spotPoints(data, line, nameAt, false), busAlong, rider)
            CatchPlanner.bestCatch(rider, busAlong, candidates, timing, walking, data.waveRate)
        } else null

        val dropPlan = destination?.let { d ->
            val boardAlong = when {
                onBoard -> busAlong
                catchPlan != null -> catchPlan.point.along
                boardingStop != null -> line.along(boardingStop)
                else -> busAlong
            }
            val candidates = CatchPlanner.candidates(line, names, spotPoints(data, line, nameAt, true), boardAlong, d.position)
            CatchPlanner.bestDrop(d.position, busAlong, boardAlong, candidates, timing, walking, data.waveRate)
        }
        return Plans(catchPlan, dropPlan, timing.liveFactor)
    }

    // Every running bus the rider may take, plus today's trips that haven't started,
    // ranked by when they would get the rider to the destination.
    fun compare(
        destination: Destination, buses: List<Bus>, routes: List<BusRoute>, paths: Map<String, List<LatLng>>,
        schedules: List<BusSchedule>, rider: LatLng?, walking: WalkingSpeed, dataFor: (String, String?) -> RouteData,
        routerSpeedFor: (String) -> Double?, specialAt: (Long) -> String?, now: Long = System.currentTimeMillis(),
    ): List<TripOption> {
        val found = mutableListOf<TripOption>()
        for (bus in buses) {
            val route = routes.firstOrNull { it.id == bus.routeId } ?: continue
            val line = directedLine(bus, route, paths[route.id]) ?: continue
            option(bus.number, route, line, ordered(route.stops, line), bus.driverId, line.along(bus.position), 0.0,
                destination, rider, walking, dataFor(route.id, bus.driverId), routerSpeedFor(bus.id), factors[bus.id] ?: 1.0,
                specialAt, now)?.let { found += it }
        }
        // Trips later today, on a route whose name has the trip's city spot: leaving KUET at
        // the campus time, or leaving the spot at the spot time (driven back to KUET).
        val nowMinutes = Timetable.minuteOfDay(now)
        for (schedule in schedules) {
            if (!Timetable.runsOn(schedule, now)) continue
            val route = routes.firstOrNull { it.name.contains(schedule.spot, ignoreCase = true) } ?: continue
            if (route.stops.size < 2) continue
            val forward = line(route, paths[route.id]) ?: continue
            val data = dataFor(route.id, null)
            for ((time, line) in listOf(schedule.campusTime to forward, schedule.spotTime to forward.flipped())) {
                val departure = Timetable.minutes(time) ?: continue
                if (departure <= nowMinutes) continue
                // Timetable trips often leave late: add the usual delay learned from real trips.
                val late = data.profile.lateness(route.id, StatKey.letter(line.reversed) + schedule.id)
                val startsIn = (departure - nowMinutes) * 60.0 + late
                option("${schedule.tripName} · ${Timetable.display(time)}", route, line, ordered(route.stops, line), null, 0.0,
                    startsIn, destination, rider, walking, data, null, 1.0, specialAt, now)?.let { found += it }
            }
        }
        return TripOption.ranked(found)
    }

    private fun option(
        title: String, route: BusRoute, line: RouteLine, stops: List<Stop>, driver: String?, busAlong: Double,
        startsIn: Double, destination: Destination, rider: LatLng?, walking: WalkingSpeed, data: RouteData,
        routerSpeed: Double?, liveFactor: Double, specialAt: (Long) -> String?, now: Long,
    ): TripOption? {
        val leaves = now + (startsIn * 1000).toLong()
        val timing = RouteTiming(line, data.profile, TimeBucket.of(leaves, specialAt(leaves)), driver, routerSpeed, liveFactor)
        val names = stops.map { it.name }
        val rate = if (driver != null) data.waveRate else 0.5
        var catchPlan: CatchPlan? = null
        var boardAlong = busAlong
        if (rider != null) {
            val candidates = CatchPlanner.candidates(line, names, emptyList(), busAlong, rider)
            // A bus the rider can't reach in time is no option.
            catchPlan = CatchPlanner.bestCatch(rider, busAlong, candidates, timing, walking, rate, startsIn) ?: return null
            boardAlong = catchPlan.point.along
        }
        val dropCandidates = CatchPlanner.candidates(line, names, emptyList(), boardAlong, destination.position)
        val drop = CatchPlanner.bestDrop(destination.position, busAlong, boardAlong, dropCandidates, timing, walking, rate)
            ?: return null
        return TripOption(title, startsIn, catchPlan, drop)
    }

    // ---------- Riding, and learning where riders get off ----------

    private val detectors = mutableMapOf<String, OnBoardDetector>()
    private var lastOnBoard: LatLng? = null
    private var ridingRouteId: String? = null
    var ridingBusId: String? = null
        private set

    // Called on every rider position, for every running bus. Leaving the bus again
    // returns one drop-off (route, point on the route) to record, with no name saved.
    fun trackRides(
        buses: List<Bus>, rider: LatLng, riderSpeed: Double?, paths: Map<String, List<LatLng>>, now: Long,
    ): Pair<String, LatLng>? {
        var dropoff: Pair<String, LatLng>? = null
        for (bus in buses) {
            if (bus.isStale(now)) continue
            val detector = detectors.getOrPut(bus.id) { OnBoardDetector() }
            val onBoard = detector.update(rider, riderSpeed, bus, now)
            if (onBoard) {
                ridingBusId = bus.id
                ridingRouteId = bus.routeId
                lastOnBoard = rider
            } else if (ridingBusId == bus.id) {
                val point = lastOnBoard
                val routeId = ridingRouteId
                if (point != null && routeId != null) {
                    val onRoute = paths[routeId]?.let { Geo.snap(point, it)?.point } ?: point
                    dropoff = routeId to onRoute
                }
                ridingBusId = null
                ridingRouteId = null
                lastOnBoard = null
            }
        }
        // The bus I was on finished its trip.
        if (ridingBusId != null && buses.none { it.id == ridingBusId }) ridingBusId = null
        detectors.keys.retainAll(buses.map { it.id }.toSet())
        return dropoff
    }
}
