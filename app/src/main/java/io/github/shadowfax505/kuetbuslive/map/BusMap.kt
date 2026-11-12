package io.github.shadowfax505.kuetbuslive.map

import android.os.Bundle
import android.view.Gravity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.shadowfax505.kuetbuslive.model.Bus
import io.github.shadowfax505.kuetbuslive.model.LatLng
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.maps.MapView
import org.maplibre.android.geometry.LatLng as MapLatLng

// Asks the map to show these points. A new key moves the camera again, even to the same points.
data class FocusRequest(val points: List<LatLng>, val key: Long = System.nanoTime())

@Composable
fun BusMap(
    modifier: Modifier = Modifier,
    buses: List<Bus> = emptyList(),
    selectedBusId: String? = null,
    followBusId: String? = null,
    routePath: List<LatLng> = emptyList(),
    stops: List<LatLng> = emptyList(),
    etaPath: List<LatLng> = emptyList(),
    boardingPoint: LatLng? = null,
    waves: List<LatLng> = emptyList(),
    showUserLocation: Boolean = false,
    focus: FocusRequest? = null,
    bottomPadding: Dp = 320.dp,
    topPadding: Dp = 96.dp,
    onBusClick: (String) -> Unit = {},
    onUserMovedMap: () -> Unit = {},
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val bottomPx = with(density) { bottomPadding.roundToPx() }
    val topPx = with(density) { topPadding.roundToPx() }
    var controller by remember { mutableStateOf<BusMapController?>(null) }
    val clickHandler by rememberUpdatedState(onBusClick)
    val movedHandler by rememberUpdatedState(onUserMovedMap)

    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
            getMapAsync { map ->
                map.cameraPosition = CameraPosition.Builder()
                    .target(MapLatLng(BusMapController.KUET.lat, BusMapController.KUET.lng))
                    .zoom(12.5)
                    .build()
                map.uiSettings.apply {
                    isLogoEnabled = false
                    isTiltGesturesEnabled = false
                    compassGravity = Gravity.TOP or Gravity.END
                    setCompassMargins(0, topPx, (16 * resources.displayMetrics.density).toInt(), 0)
                    attributionGravity = Gravity.TOP or Gravity.START
                    setAttributionMargins((8 * resources.displayMetrics.density).toInt(), topPx, 0, 0)
                }
                map.setStyle(if (dark) BusMapController.STYLE_DARK else BusMapController.STYLE_LIGHT) { style ->
                    controller = BusMapController(context, map).also {
                        it.onBusClick = { id -> clickHandler(id) }
                        it.onUserMovedMap = { movedHandler() }
                        it.onStyleLoaded(style)
                    }
                }
            }
        }
    }

    // The map view follows the screen's lifecycle, and is freed when it leaves the screen.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller?.stop()
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)

    val c = controller
    LaunchedEffect(c, buses) { c?.setBuses(buses) }
    LaunchedEffect(c, selectedBusId) { c?.setSelected(selectedBusId) }
    LaunchedEffect(c, followBusId) { c?.follow(followBusId) }
    LaunchedEffect(c, routePath) { c?.setRoute(routePath) }
    LaunchedEffect(c, stops) { c?.setStops(stops) }
    LaunchedEffect(c, etaPath) { c?.setEtaPath(etaPath) }
    LaunchedEffect(c, boardingPoint) { c?.setBoardingPoint(boardingPoint) }
    LaunchedEffect(c, waves) { c?.setWaves(waves) }
    LaunchedEffect(c, showUserLocation) { if (showUserLocation) c?.showUserLocation() }
    LaunchedEffect(c, focus?.key) { focus?.let { c?.focus(it.points, bottomPx) } }
}
