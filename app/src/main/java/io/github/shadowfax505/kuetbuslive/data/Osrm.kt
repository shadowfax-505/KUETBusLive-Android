package io.github.shadowfax505.kuetbuslive.data

import io.github.shadowfax505.kuetbuslive.model.LatLng
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

// Travel time, distance and road path from the public OSRM server, the same API
// the iOS app uses. The server has fair-use limits, so callers ask at most every
// 30 seconds per bus.
data class RouteInfo(
    val durationSeconds: Double,
    val distanceMetres: Double,
    val path: List<LatLng>,
) {
    val minutes: Int get() = ceil(durationSeconds / 60).toInt()
    val kilometres: Double get() = distanceMetres / 1000
    val summary: String get() = "$minutes min · ${"%.1f".format(kilometres)} km"

    companion object {
        // When the routing server can't be reached: the distance along the route (or the
        // straight line, a third longer for the roads) at a city bus's 18 km/h. Same
        // numbers on iOS. null once the bus has passed the stop.
        const val FALLBACK_SPEED = 5.0      // metres per second
        const val ROAD_FACTOR = 1.35

        fun estimate(alongRoute: Double?, straightLine: Double): RouteInfo? {
            val distance = alongRoute ?: (straightLine * ROAD_FACTOR)
            if (distance < 0) return null
            return RouteInfo(distance / FALLBACK_SPEED, distance, emptyList())
        }
    }
}

object Osrm {
    // Set in app/build.gradle.kts (kuetbus.osrmBaseUrl); the public demo server by default.
    private val BASE_URL = io.github.shadowfax505.kuetbuslive.BuildConfig.OSRM_BASE_URL
    const val REFRESH_MILLIS = 30_000L
    private const val TAG = "Osrm"

    private val pathCache = mutableMapOf<String, List<LatLng>>()

    suspend fun route(points: List<LatLng>): RouteInfo? = withContext(Dispatchers.IO) {
        if (points.size < 2) return@withContext null
        // OSRM wants longitude first, points separated by ";".
        val coordinates = points.joinToString(";") { "${it.lng},${it.lat}" }
        val url = URL("$BASE_URL$coordinates?overview=full&geometries=geojson")
        runCatching {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "KUETBusLive-Android")
            }
            try {
                if (connection.responseCode != 200) {
                    Log.w(TAG, "Routing server answered ${connection.responseCode}")
                    return@runCatching null
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parse(body)
            } finally {
                connection.disconnect()
            }
        }.onFailure { Log.w(TAG, "Routing failed: ${it.message}") }.getOrNull()
    }

    // The road path through a route's stops, fetched once per route.
    suspend fun path(routeId: String, stops: List<LatLng>): List<LatLng> {
        pathCache[routeId]?.let { return it }
        val path = route(stops)?.path?.takeIf { it.size >= 2 } ?: return stops
        pathCache[routeId] = path
        return path
    }

    fun parse(json: String): RouteInfo? {
        val root = JSONObject(json)
        if (root.optString("code") != "Ok") return null
        val first = root.optJSONArray("routes")?.optJSONObject(0) ?: return null
        val coordinates = first.optJSONObject("geometry")?.optJSONArray("coordinates")
        val path = buildList {
            if (coordinates != null) {
                for (i in 0 until coordinates.length()) {
                    val pair = coordinates.optJSONArray(i) ?: continue
                    if (pair.length() == 2) add(LatLng(pair.getDouble(1), pair.getDouble(0)))
                }
            }
        }
        return RouteInfo(first.optDouble("duration", 0.0), first.optDouble("distance", 0.0), path)
    }
}
