package io.github.shadowfax505.kuetbuslive.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Choreographer
import io.github.shadowfax505.kuetbuslive.model.Bus
import io.github.shadowfax505.kuetbuslive.model.BusAudience
import io.github.shadowfax505.kuetbuslive.model.Geo
import io.github.shadowfax505.kuetbuslive.model.LatLng
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.min
import org.maplibre.android.geometry.LatLng as MapLatLng

// Drives one MapLibre map: draws the route, stops, boarding point and waves, and
// moves every bus smoothly between GPS fixes.
//
// Drivers send a position about every 5 seconds. Instead of jumping, each bus glides
// from where it is drawn now to the new fix over roughly the time between fixes, and
// turns to its new heading over a shorter time, the way a car moves in a ride app.
class BusMapController(private val context: Context, private val map: MapLibreMap) {

    var onBusClick: ((String) -> Unit)? = null
    var onUserMovedMap: (() -> Unit)? = null

    private var style: Style? = null
    private val animated = mutableMapOf<String, AnimatedBus>()
    private var selectedId: String? = null
    private var followId: String? = null
    private var frameScheduled = false

    private class AnimatedBus(
        var from: LatLng,
        var to: LatLng,
        var fromBearing: Double,
        var toBearing: Double,
        var startNanos: Long,
        var durationNanos: Long,
        var audience: BusAudience,
        var lastFixMillis: Long,
    ) {
        fun position(now: Long): LatLng {
            val t = progress(now, durationNanos)
            return Geo.lerp(from, to, t)
        }

        fun bearing(now: Long): Double {
            // Turning finishes faster than moving, so the bus faces the road before it glides.
            val t = easeOut(progress(now, min(durationNanos, TURN_NANOS)))
            return Geo.lerpAngle(fromBearing, toBearing, t)
        }

        fun isMoving(now: Long): Boolean = now - startNanos < durationNanos

        private fun progress(now: Long, duration: Long): Double =
            if (duration <= 0) 1.0 else ((now - startNanos).toDouble() / duration).coerceIn(0.0, 1.0)

        private fun easeOut(t: Double): Double = 1 - (1 - t) * (1 - t)
    }

    fun onStyleLoaded(loaded: Style) {
        style = loaded
        loaded.addImage(ICON_STUDENT, MapIcons.bus(context, STUDENT_COLOR, selected = false))
        loaded.addImage(ICON_STUDENT_SELECTED, MapIcons.bus(context, STUDENT_COLOR, selected = true))
        loaded.addImage(ICON_TEACHER, MapIcons.bus(context, TEACHER_COLOR, selected = false))
        loaded.addImage(ICON_TEACHER_SELECTED, MapIcons.bus(context, TEACHER_COLOR, selected = true))
        loaded.addImage(ICON_BOARDING, MapIcons.boardingPoint(context, BOARDING_COLOR))
        loaded.addImage(ICON_WAVE, MapIcons.wave(context))

        listOf(SRC_ROUTE, SRC_ETA, SRC_STOPS, SRC_BOARDING, SRC_WAVES, SRC_BUSES).forEach {
            loaded.addSource(GeoJsonSource(it, FeatureCollection.fromFeatures(emptyList())))
        }

        loaded.addLayer(
            LineLayer(LAYER_ROUTE_CASING, SRC_ROUTE).withProperties(
                PropertyFactory.lineColor(Color.WHITE),
                PropertyFactory.lineWidth(9f),
                PropertyFactory.lineOpacity(0.9f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
        loaded.addLayer(
            LineLayer(LAYER_ROUTE, SRC_ROUTE).withProperties(
                PropertyFactory.lineColor(ROUTE_COLOR),
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
        loaded.addLayer(
            LineLayer(LAYER_ETA, SRC_ETA).withProperties(
                PropertyFactory.lineColor(ETA_COLOR),
                PropertyFactory.lineWidth(4.5f),
                PropertyFactory.lineDasharray(arrayOf(0.1f, 1.8f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            )
        )
        loaded.addLayer(
            CircleLayer(LAYER_STOPS, SRC_STOPS).withProperties(
                PropertyFactory.circleRadius(5f),
                PropertyFactory.circleColor(Color.WHITE),
                PropertyFactory.circleStrokeColor(ROUTE_COLOR),
                PropertyFactory.circleStrokeWidth(2.5f),
            )
        )
        loaded.addLayer(
            SymbolLayer(LAYER_WAVES, SRC_WAVES).withProperties(
                PropertyFactory.iconImage(ICON_WAVE),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            )
        )
        loaded.addLayer(
            SymbolLayer(LAYER_BOARDING, SRC_BOARDING).withProperties(
                PropertyFactory.iconImage(ICON_BOARDING),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            )
        )
        // A soft halo under the selected bus.
        loaded.addLayer(
            CircleLayer(LAYER_HALO, SRC_BUSES).withProperties(
                PropertyFactory.circleRadius(30f),
                PropertyFactory.circleColor(Expression.get(PROP_COLOR)),
                PropertyFactory.circleOpacity(0.18f),
                PropertyFactory.circleBlur(0.4f),
                PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP),
            ).withFilter(Expression.eq(Expression.get(PROP_SELECTED), Expression.literal(true)))
        )
        loaded.addLayer(
            SymbolLayer(LAYER_BUSES, SRC_BUSES).withProperties(
                PropertyFactory.iconImage(Expression.get(PROP_ICON)),
                PropertyFactory.iconRotate(Expression.get(PROP_BEARING)),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(
                    Expression.interpolate(
                        Expression.linear(), Expression.zoom(),
                        Expression.stop(11, 0.55f),
                        Expression.stop(14, 0.85f),
                        Expression.stop(17, 1.15f),
                    )
                ),
            )
        )

        map.addOnMapClickListener { point ->
            val screen = map.projection.toScreenLocation(point)
            val hit = map.queryRenderedFeatures(screen, LAYER_BUSES).firstOrNull()
            val id = hit?.getStringProperty(PROP_ID)
            if (id != null) onBusClick?.invoke(id)
            id != null
        }
        map.addOnCameraMoveStartedListener { reason ->
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                followId = null
                onUserMovedMap?.invoke()
            }
        }
        drawBuses(System.nanoTime())
    }

    // ---------- Buses ----------

    fun setBuses(buses: List<Bus>) {
        val now = System.nanoTime()
        val ids = buses.map { it.id }.toSet()
        animated.keys.retainAll(ids)
        for (bus in buses) {
            val target = bus.position
            val current = animated[bus.id]
            if (current == null) {
                val bearing = bus.heading ?: 0.0
                animated[bus.id] = AnimatedBus(target, target, bearing, bearing, now, 0, bus.audience, bus.updatedAtMillis)
                continue
            }
            current.audience = bus.audience
            if (current.to == target) continue

            val drawnAt = current.position(now)
            val drawnBearing = current.bearing(now)
            val moved = Geo.distanceMetres(drawnAt, target)
            // Use the GPS heading while the bus is moving; otherwise face the direction of travel.
            val gpsHeading = bus.heading?.takeIf { (bus.speed ?: 0.0) > 1.0 }
            val newBearing = gpsHeading ?: if (moved > 4) Geo.bearing(drawnAt, target) else drawnBearing
            // Glide over about the time between fixes, so the bus keeps moving until the next one.
            val gap = (bus.updatedAtMillis - current.lastFixMillis).takeIf { it in 1_000..8_000 } ?: DEFAULT_GLIDE_MILLIS
            val duration = if (moved > TELEPORT_METRES) 0L else gap * 1_000_000L

            current.from = drawnAt
            current.to = target
            current.fromBearing = drawnBearing
            current.toBearing = newBearing
            current.startNanos = now
            current.durationNanos = duration
            current.lastFixMillis = bus.updatedAtMillis
        }
        scheduleFrame()
    }

    fun setSelected(id: String?) {
        selectedId = id
        scheduleFrame()
    }

    // Keeps the camera on this bus as it moves, until the user drags the map.
    fun follow(id: String?) {
        followId = id
        scheduleFrame()
    }

    fun positionOf(id: String): LatLng? = animated[id]?.position(System.nanoTime())

    private fun scheduleFrame() {
        if (frameScheduled) return
        frameScheduled = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private val frameCallback = Choreographer.FrameCallback {
        frameScheduled = false
        val now = System.nanoTime()
        drawBuses(now)
        followId?.let { id ->
            animated[id]?.let { bus ->
                val p = bus.position(now)
                map.moveCamera(CameraUpdateFactory.newLatLng(MapLatLng(p.lat, p.lng)))
            }
        }
        if (animated.values.any { it.isMoving(now) || now - it.startNanos < TURN_NANOS }) scheduleFrame()
    }

    private fun drawBuses(now: Long) {
        val source = style?.getSourceAs<GeoJsonSource>(SRC_BUSES) ?: return
        val features = animated.map { (id, bus) ->
            val p = bus.position(now)
            val selected = id == selectedId
            Feature.fromGeometry(Point.fromLngLat(p.lng, p.lat)).apply {
                addStringProperty(PROP_ID, id)
                addNumberProperty(PROP_BEARING, bus.bearing(now))
                addBooleanProperty(PROP_SELECTED, selected)
                addStringProperty(PROP_COLOR, if (bus.audience == BusAudience.TEACHER) "#8B5CF6" else "#2F6BFF")
                addStringProperty(
                    PROP_ICON,
                    when {
                        bus.audience == BusAudience.TEACHER && selected -> ICON_TEACHER_SELECTED
                        bus.audience == BusAudience.TEACHER -> ICON_TEACHER
                        selected -> ICON_STUDENT_SELECTED
                        else -> ICON_STUDENT
                    },
                )
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    // ---------- Route, stops, boarding point and waves ----------

    fun setRoute(path: List<LatLng>) = setLine(SRC_ROUTE, path)

    fun setEtaPath(path: List<LatLng>) = setLine(SRC_ETA, path)

    fun setStops(stops: List<LatLng>) = setPoints(SRC_STOPS, stops)

    fun setBoardingPoint(point: LatLng?) = setPoints(SRC_BOARDING, listOfNotNull(point))

    fun setWaves(points: List<LatLng>) = setPoints(SRC_WAVES, points)

    private fun setLine(sourceId: String, path: List<LatLng>) {
        val source = style?.getSourceAs<GeoJsonSource>(sourceId) ?: return
        val features = if (path.size >= 2) {
            listOf(Feature.fromGeometry(LineString.fromLngLats(path.map { Point.fromLngLat(it.lng, it.lat) })))
        } else emptyList()
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun setPoints(sourceId: String, points: List<LatLng>) {
        val source = style?.getSourceAs<GeoJsonSource>(sourceId) ?: return
        source.setGeoJson(FeatureCollection.fromFeatures(points.map { Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat)) }))
    }

    // ---------- Camera ----------

    // Fits the camera around these points, leaving room for the sheet at the bottom.
    fun focus(points: List<LatLng>, bottomPaddingPx: Int, animate: Boolean = true) {
        if (points.isEmpty()) return
        val d = context.resources.displayMetrics.density
        val update = if (points.size == 1) {
            CameraUpdateFactory.newLatLngZoom(MapLatLng(points[0].lat, points[0].lng), 15.5)
        } else {
            val bounds = LatLngBounds.Builder().apply { points.forEach { include(MapLatLng(it.lat, it.lng)) } }.build()
            CameraUpdateFactory.newLatLngBounds(bounds, (48 * d).toInt(), (120 * d).toInt(), (48 * d).toInt(), bottomPaddingPx)
        }
        if (animate) map.animateCamera(update, 900) else map.moveCamera(update)
    }

    // The rider's own blue dot. Only call once location permission is granted.
    @SuppressLint("MissingPermission")
    fun showUserLocation() {
        val loaded = style ?: return
        runCatching {
            val component = map.locationComponent
            if (!component.isLocationComponentActivated) {
                component.activateLocationComponent(LocationComponentActivationOptions.builder(context, loaded).build())
            }
            component.isLocationComponentEnabled = true
            component.renderMode = RenderMode.COMPASS
        }
    }

    fun stop() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        frameScheduled = false
    }

    companion object {
        // OpenFreeMap: free vector tiles from OpenStreetMap data, no API key needed.
        const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/positron"
        const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"

        val KUET = LatLng(22.8999, 89.5024)

        private const val DEFAULT_GLIDE_MILLIS = 4_800L
        private const val TURN_NANOS = 900_000_000L
        // A jump this big is a new trip or lost signal: place the bus without gliding.
        private const val TELEPORT_METRES = 2_000.0

        private val STUDENT_COLOR = Color.rgb(47, 107, 255)
        private val TEACHER_COLOR = Color.rgb(139, 92, 246)
        private val ROUTE_COLOR = Color.rgb(15, 138, 114)
        private val ETA_COLOR = Color.rgb(11, 27, 58)
        private val BOARDING_COLOR = Color.rgb(15, 138, 114)

        private const val SRC_ROUTE = "route"
        private const val SRC_ETA = "eta"
        private const val SRC_STOPS = "stops"
        private const val SRC_BOARDING = "boarding"
        private const val SRC_WAVES = "waves"
        private const val SRC_BUSES = "buses"

        private const val LAYER_ROUTE_CASING = "route-casing"
        private const val LAYER_ROUTE = "route-line"
        private const val LAYER_ETA = "eta-line"
        private const val LAYER_STOPS = "stops"
        private const val LAYER_WAVES = "waves"
        private const val LAYER_BOARDING = "boarding"
        private const val LAYER_HALO = "bus-halo"
        private const val LAYER_BUSES = "buses"

        private const val ICON_STUDENT = "bus-student"
        private const val ICON_STUDENT_SELECTED = "bus-student-selected"
        private const val ICON_TEACHER = "bus-teacher"
        private const val ICON_TEACHER_SELECTED = "bus-teacher-selected"
        private const val ICON_BOARDING = "boarding"
        private const val ICON_WAVE = "wave"

        private const val PROP_ID = "id"
        private const val PROP_ICON = "icon"
        private const val PROP_BEARING = "bearing"
        private const val PROP_SELECTED = "selected"
        private const val PROP_COLOR = "color"
    }
}
