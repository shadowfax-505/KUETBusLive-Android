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
}

object Osrm {
    private const val BASE_URL = "https://router.project-osrm.org/route/v1/driving/"
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
