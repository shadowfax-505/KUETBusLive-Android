package io.github.shadowfax505.kuetbuslive.data

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import io.github.shadowfax505.kuetbuslive.model.LatLng
import io.github.shadowfax505.kuetbuslive.model.RunningStat
import io.github.shadowfax505.kuetbuslive.model.SegmentProfile
import io.github.shadowfax505.kuetbuslive.model.SpotKey
import io.github.shadowfax505.kuetbuslive.model.StatKey
import io.github.shadowfax505.kuetbuslive.model.TimeBucket
import io.github.shadowfax505.kuetbuslive.model.TripObservations
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await

// Reads and writes the learned travel model (segmentStats, spotStats, driverStats),
// exactly as the iOS StatsStore does. Every write is a transaction that reads the
// current numbers, adds one trip and writes them back, so two buses finishing at the
// same moment can't overwrite each other. The shared rules check each write.
object StatsStore {
    private const val TAG = "StatsStore"
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    data class SpotStat(val position: LatLng, val asks: Int, val stops: Int, val dropoffs: Int)

    data class RouteStats(
        val profile: SegmentProfile = SegmentProfile.EMPTY,
        val spots: List<SpotStat> = emptyList(),
        val loadedAtMillis: Long = 0,
    )

    private val _routes = MutableStateFlow<Map<String, RouteStats>>(emptyMap())
    val routes: StateFlow<Map<String, RouteStats>> = _routes.asStateFlow()

    // Driver uid -> chance they stop for a wave, (stops + 1) / (asks + 2).
    private val _waveRates = MutableStateFlow<Map<String, Double>>(emptyMap())
    val waveRates: StateFlow<Map<String, Double>> = _waveRates.asStateFlow()

    // Numbers change slowly, so each route is read at most every ten minutes.
    private const val REFRESH_MILLIS = 10 * 60 * 1000L

    fun profile(routeId: String): SegmentProfile = _routes.value[routeId]?.profile ?: SegmentProfile.EMPTY

    fun spots(routeId: String): List<SpotStat> = _routes.value[routeId]?.spots ?: emptyList()

    fun waveRate(driver: String?): Double = driver?.let { _waveRates.value[it] } ?: 0.5

    private fun Any?.asInt(): Int = (this as? Number)?.toInt() ?: 0
    private fun Any?.asDouble(): Double = (this as? Number)?.toDouble() ?: 0.0

    // ---------- Reading ----------

    suspend fun load(routeId: String, driver: String?, force: Boolean = false) {
        if (driver != null) loadWaveRate(driver)
        val known = _routes.value[routeId]
        val now = System.currentTimeMillis()
        if (!force && known != null && now - known.loadedAtMillis < REFRESH_MILLIS) return
        _routes.update { it + (routeId to (known ?: RouteStats()).copy(loadedAtMillis = now)) }
        runCatching {
            val docs = db.collection("segmentStats").whereEqualTo("routeId", routeId).get().await().documents
            val stats = docs.associate { d ->
                d.id to RunningStat(d.get("n").asInt(), d.get("mean").asDouble(), d.get("variance").asDouble())
            }
            _routes.update { it + (routeId to (it[routeId] ?: RouteStats()).copy(profile = SegmentProfile(stats))) }
        }.onFailure { Log.w(TAG, "Could not load travel times: ${it.message}") }
        runCatching {
            val docs = db.collection("spotStats").whereEqualTo("routeId", routeId).get().await().documents
            val spots = docs.mapNotNull { d ->
                val lat = (d.get("lat") as? Number)?.toDouble() ?: return@mapNotNull null
                val lng = (d.get("lng") as? Number)?.toDouble() ?: return@mapNotNull null
                SpotStat(LatLng(lat, lng), d.get("asks").asInt(), d.get("stops").asInt(), d.get("dropoffs").asInt())
            }
            _routes.update { it + (routeId to (it[routeId] ?: RouteStats()).copy(spots = spots)) }
        }
    }

    // How often this driver stops for a wave, with one stop and one miss assumed to start with.
    suspend fun loadWaveRate(driver: String) {
        if (_waveRates.value[driver] != null) return
        runCatching {
            val doc = db.collection("driverStats").document(driver).get().await()
            val rate = (doc.get("stops").asInt() + 1).toDouble() / (doc.get("asks").asInt() + 2)
            _waveRates.update { it + (driver to rate) }
        }
    }

    // ---------- Writing (driver, at the end of a trip, while the bus is still running) ----------

    // `late`: "t"/"u" + schedule ID and the seconds this trip left late.
    // `errors`: how far the real arrivals fell from the prediction, in spreads.
    suspend fun upload(
        observations: TripObservations,
        routeId: String,
        busId: String,
        driverId: String,
        specialKindAt: (Long) -> String?,
        late: Pair<String, Double>? = null,
        errors: List<Double> = emptyList(),
    ) = coroutineScope {
        val jobs = mutableListOf<kotlinx.coroutines.Deferred<Unit>>()
        val back = observations.reversed
        for (stretch in observations.stretches) {
            val bucket = TimeBucket.of(stretch.startedAtMillis, specialKindAt(stretch.startedAtMillis))
            val base = mapOf("routeId" to routeId, "kind" to if (back) "travelBack" else "travel", "index" to stretch.segment)
            val slotted = base + mapOf("dayType" to (bucket.special ?: bucket.dayType.raw), "slot" to bucket.slot)
            val mine = slotted + ("driverId" to driverId)
            jobs += async { add(stretch.seconds, StatKey.travel(routeId, stretch.segment, bucket, driverId, back), mine, busId) }
            jobs += async { add(stretch.seconds, StatKey.travel(routeId, stretch.segment, bucket, back), slotted, busId) }
            jobs += async { add(stretch.seconds, StatKey.travel(routeId, stretch.segment, back), base, busId) }
        }
        for (stop in observations.stopTimes) {
            jobs += async {
                add(stop.seconds, StatKey.dwell(routeId, stop.stop), mapOf("routeId" to routeId, "kind" to "dwell", "index" to stop.stop), busId)
            }
        }
        if (late != null) {
            jobs += async {
                add(late.second, StatKey.late(routeId, late.first), mapOf("routeId" to routeId, "kind" to "late", "index" to 0), busId)
            }
        }
        // Errors one after another: each is +1 on the same document.
        jobs += async {
            for (z in errors) add(z, StatKey.calibration(routeId), mapOf("routeId" to routeId, "kind" to "calib", "index" to 0), busId)
        }
        // Waves one after another: each is +1 on the same driver record.
        jobs += async { for (wave in observations.waves) recordWave(wave, routeId, busId, driverId) }
        jobs.awaitAll()
    }

    private suspend fun add(value: Double, id: String, fields: Map<String, Any>, busId: String) {
        val ref = db.collection("segmentStats").document(id)
        runCatching {
            db.runTransaction { tx ->
                val current = tx.get(ref)
                val stat = RunningStat(current.get("n").asInt(), current.get("mean").asDouble(), current.get("variance").asDouble())
                    .add(value)
                tx.set(
                    ref,
                    fields + mapOf(
                        "n" to stat.n,
                        "mean" to stat.mean,
                        "variance" to stat.variance,
                        "lastBusId" to busId,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
                null
            }.await()
        }.onFailure { Log.w(TAG, "Could not save travel time $id: ${it.message}") }
    }

    private suspend fun recordWave(wave: TripObservations.WaveOutcome, routeId: String, busId: String, driverId: String) {
        val spotRef = db.collection("spotStats").document(SpotKey.id(routeId, wave.position))
        val driverRef = db.collection("driverStats").document(driverId)
        val cell = SpotKey.snapped(wave.position)
        val stopped = if (wave.stopped) 1 else 0
        runCatching {
            db.runTransaction { tx ->
                val spot = tx.get(spotRef)
                val driver = tx.get(driverRef)
                tx.set(
                    spotRef,
                    mapOf(
                        "routeId" to routeId,
                        "lat" to ((spot.get("lat") as? Number)?.toDouble() ?: cell.lat),
                        "lng" to ((spot.get("lng") as? Number)?.toDouble() ?: cell.lng),
                        "asks" to spot.get("asks").asInt() + 1,
                        "stops" to spot.get("stops").asInt() + stopped,
                        "dropoffs" to spot.get("dropoffs").asInt(),
                        "lastBusId" to busId,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
                tx.set(
                    driverRef,
                    mapOf(
                        "asks" to driver.get("asks").asInt() + 1,
                        "stops" to driver.get("stops").asInt() + stopped,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
                null
            }.await()
        }.onFailure { Log.w(TAG, "Could not save wave outcome: ${it.message}") }
    }

    // ---------- Writing (rider, after getting off) ----------

    // One more rider got off here. No uid is saved.
    suspend fun addDropoff(routeId: String, position: LatLng) {
        val ref = db.collection("spotStats").document(SpotKey.id(routeId, position))
        val cell = SpotKey.snapped(position)
        runCatching {
            db.runTransaction { tx ->
                val spot = tx.get(ref)
                if (spot.exists()) {
                    tx.update(ref, mapOf("dropoffs" to spot.get("dropoffs").asInt() + 1, "updatedAt" to FieldValue.serverTimestamp()))
                } else {
                    tx.set(
                        ref,
                        mapOf(
                            "routeId" to routeId, "lat" to cell.lat, "lng" to cell.lng,
                            "asks" to 0, "stops" to 0, "dropoffs" to 1,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                    )
                }
                null
            }.await()
        }.onFailure { Log.w(TAG, "Could not save drop-off: ${it.message}") }
    }
}
