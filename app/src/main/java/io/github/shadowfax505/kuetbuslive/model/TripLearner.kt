package io.github.shadowfax505.kuetbuslive.model

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sqrt

// At the end of a trip the driver's phone turns its own GPS track into a few numbers
// for the shared model. No raw track leaves the phone. Same rules as the iOS app:
//  - each stretch between two stops, from 20 m past one to 20 m before the next
//  - how long the bus stood at each middle stop
//  - for every wave the driver saw: did the bus stop there or drive past

data class GpsSample(val millis: Long, val position: LatLng, val speed: Double?)

data class SeenWave(val position: LatLng, val createdAtMillis: Long)

data class TripObservations(
    val reversed: Boolean = false,
    val departedAtMillis: Long? = null,
    val arrivals: Map<Int, Long> = emptyMap(),     // reached stop i, in the trip's own order
    val stretches: List<Stretch> = emptyList(),
    val stopTimes: List<StopTime> = emptyList(),
    val waves: List<WaveOutcome> = emptyList(),
) {
    data class Stretch(val segment: Int, val seconds: Double, val startedAtMillis: Long)
    data class StopTime(val stop: Int, val seconds: Double)
    data class WaveOutcome(val position: LatLng, val stopped: Boolean, val seconds: Double)
}

object TripLearner {
    const val STOP_ZONE = 20.0
    const val STOPPED_SPEED = 1.5
    const val WAVE_RADIUS = 20.0
    private val stretchSeconds = 5.0..3600.0
    private val stopSeconds = 0.0..900.0

    // `forward` is the route in its own order. A trip that ends nearer the start than it
    // began was driven back, and is learned on the flipped line.
    fun learn(samples: List<GpsSample>, forward: RouteLine, waves: List<SeenWave>): TripObservations {
        if (samples.size < 2 || forward.stopAlong.size < 2) return TripObservations()
        val backwards = forward.along(samples.last().position) < forward.along(samples.first().position)
        val line = if (backwards) forward.flipped() else forward

        // GPS wobbles, so the distance along the route never goes backwards.
        val along = mutableListOf<Double>()
        for (sample in samples) {
            val value = line.along(sample.position)
            along += max(value, along.lastOrNull() ?: value)
        }

        fun timeAt(target: Double): Long? {
            for (k in 1 until samples.size) {
                if (along[k - 1] < target && along[k] >= target) {
                    val fraction = (target - along[k - 1]) / (along[k] - along[k - 1])
                    val gap = samples[k].millis - samples[k - 1].millis
                    return samples[k - 1].millis + (fraction * gap).roundToLong()
                }
            }
            return null
        }

        val arrive = line.stopAlong.map { timeAt(it - STOP_ZONE) }
        val leave = line.stopAlong.map { timeAt(it + STOP_ZONE) }
        val arrivals = buildMap { arrive.forEachIndexed { i, t -> if (i > 0 && t != null) put(i, t) } }

        val stretches = mutableListOf<TripObservations.Stretch>()
        for (segment in 0 until line.stopAlong.size - 1) {
            val from = leave[segment] ?: continue
            val to = arrive[segment + 1] ?: continue
            val seconds = (to - from) / 1000.0
            if (seconds in stretchSeconds) stretches += TripObservations.Stretch(segment, seconds, from)
        }
        // Middle stops only: the first and last include waiting to start and parking.
        val stopTimes = mutableListOf<TripObservations.StopTime>()
        for (stop in 1 until max(line.stopAlong.size - 1, 1)) {
            val from = arrive[stop] ?: continue
            val to = leave[stop] ?: continue
            val seconds = (to - from) / 1000.0
            if (seconds in stopSeconds) stopTimes += TripObservations.StopTime(line.routeStopIndex(stop), seconds)
        }

        val outcomes = mutableListOf<TripObservations.WaveOutcome>()
        for (wave in waves) {
            val waveAlong = line.along(wave.position)
            // Only waves the bus actually went past.
            if (along.last() <= waveAlong + STOP_ZONE) continue
            var stoodFor = 0.0
            for (k in 1 until samples.size) {
                val sample = samples[k]
                if (sample.millis < wave.createdAtMillis) continue
                if (Geo.distanceMetres(sample.position, wave.position) > WAVE_RADIUS) continue
                if ((sample.speed ?: Double.POSITIVE_INFINITY) >= STOPPED_SPEED) continue
                stoodFor += (sample.millis - samples[k - 1].millis) / 1000.0
            }
            outcomes += TripObservations.WaveOutcome(wave.position, stoodFor > 0, stoodFor)
        }
        return TripObservations(backwards, leave[0], arrivals, stretches, stopTimes, outcomes)
    }

    // How far this trip's real arrivals fell from the prediction made when it left the
    // first stop, in spreads. `timing` must use the numbers from before this trip.
    fun predictionErrors(observations: TripObservations, timing: RouteTiming, limit: Int = 4): List<Double> {
        val departed = observations.departedAtMillis ?: return emptyList()
        val line = timing.line
        val stops = observations.arrivals.keys.sorted()
        if (stops.isEmpty()) return emptyList()
        val step = max(stops.size / limit, 1)
        val errors = mutableListOf<Double>()
        val chosen = (step - 1 until stops.size step step).map { stops[it] }.take(limit)
        for (stop in chosen) {
            val arrived = observations.arrivals[stop] ?: continue
            if (stop >= line.stopAlong.size) continue
            val predicted = timing.estimate(line.stopAlong[0] + STOP_ZONE, line.stopAlong[stop] - STOP_ZONE)
            val spread = sqrt(predicted.totalVariance)
            if (spread <= 0) continue
            val real = (arrived - departed) / 1000.0
            errors += ((real - predicted.seconds) / spread).coerceIn(-20.0, 20.0)
        }
        return errors
    }

    // Seconds late against the timetable time ("HH:mm" in Dhaka), never below 0; null if it can't be told.
    fun lateness(departedAtMillis: Long?, scheduled: String, zone: ZoneId = Timetable.dhaka): Double? {
        departedAtMillis ?: return null
        val minutes = Timetable.minutes(scheduled) ?: return null
        val day = Instant.ofEpochMilli(departedAtMillis).atZone(zone).toLocalDate()
        val scheduledMillis = day.atStartOfDay(zone).toInstant().toEpochMilli() + minutes * 60_000L
        val late = (departedAtMillis - scheduledMillis) / 1000.0
        // More than two hours off is a different trip, not a late one.
        if (abs(late) >= 2 * 3600) return null
        return max(late, 0.0)
    }
}

// Spots riders use, in ~30 m squares, so nearby waves and drop-offs add up.
object SpotKey {
    const val GRID_DEGREES = 0.0003

    fun snapped(p: LatLng): LatLng =
        LatLng(Math.round(p.lat / GRID_DEGREES) * GRID_DEGREES, Math.round(p.lng / GRID_DEGREES) * GRID_DEGREES)

    // "r1_22.8891_89.5095", the same string as on iOS.
    fun id(route: String, position: LatLng): String {
        val cell = snapped(position)
        return route + "_" + String.format(Locale.US, "%.4f_%.4f", cell.lat, cell.lng)
    }
}

