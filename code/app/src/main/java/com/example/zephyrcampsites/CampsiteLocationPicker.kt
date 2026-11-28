package com.example.zephyrcampsites

// Displays an interactive OpenStreetMap-based picker and returns the confirmed Firestore GeoPoint.

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.tile.TileOperation

private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val MAP_VECTOR_SOURCE = "openmaptiles"
private const val MAP_LOAD_TIMEOUT_MS = 20_000L
private const val WEB_MERCATOR_LIMIT = 85.05112878

/** Stores the form's map pin using the two coordinates supported by Compose saved state. */
internal val CampsiteGeoPointSaver = Saver<GeoPoint?, DoubleArray>(
    save = { point -> point?.let { doubleArrayOf(it.latitude, it.longitude) } },
    restore = { coordinates -> GeoPoint(coordinates[0], coordinates[1]) }
)

// AI Citation [AI-44]
// OpenAI, Codex, November 27, 2026. Generated the picker and its MapLibre lifecycle handling.
// See Bhav Saluja's entry in the team's AI Documentation Google Doc.
/**
 * Lets the owner move a pin on OpenStreetMap data served by OpenFreeMap.
 *
 * @param initialLocation Saved or previously confirmed pin, or null for a new campsite.
 * @param onConfirm Receives the pin once the owner confirms it.
 * @param onDismiss Closes the picker and discards its unconfirmed position.
 * @see <a href="https://maplibre.org/maplibre-native/android/examples/getting-started/">MapLibre Android quickstart</a>
 * @see <a href="https://openfreemap.org/quick_start/">OpenFreeMap quickstart</a>
 */
@Composable
internal fun CampsiteLocationPicker(
    initialLocation: GeoPoint?,
    onConfirm: (GeoPoint) -> Unit,
    onDismiss: () -> Unit
) {
    var attempt by remember { mutableIntStateOf(0) }
    var ready by remember(attempt) { mutableStateOf(false) }
    var failed by remember(attempt) { mutableStateOf(false) }
    var map by remember(attempt) { mutableStateOf<MapLibreMap?>(null) }
    var pin by remember(attempt) { mutableStateOf<GeoPoint?>(null) }
    val uriHandler = LocalUriHandler.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val title = stringResource(R.string.campsite_picker_title)
    val center = if (initialLocation == null) {
        LatLng(53.55, -116.0)
    } else {
        LatLng(
            initialLocation.latitude.coerceIn(-WEB_MERCATOR_LIMIT, WEB_MERCATOR_LIMIT),
            initialLocation.longitude
        )
    }

    LaunchedEffect(attempt, ready, failed) {
        if (!ready && !failed) {
            delay(MAP_LOAD_TIMEOUT_MS)
            failed = true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.campsite_picker_instruction))
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (failed) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.campsite_map_error))
                            TextButton(onClick = { attempt++ }) {
                                Text(stringResource(R.string.campsite_picker_retry))
                            }
                        }
                    } else {
                        key(attempt) {
                            var observer by remember { mutableStateOf<LifecycleEventObserver?>(null) }
                            AndroidView(
                                modifier = Modifier.fillMaxSize().testTag("CampsiteLocationPickerMap"),
                                factory = { context ->
                                    MapLibre.getInstance(context)
                                    val options = MapLibreMapOptions.createFromAttributes(context)
                                        .textureMode(true)
                                    MapView(context, options).apply {
                                        contentDescription = title
                                        onCreate(null)
                                        val mapView = this
                                        observer = LifecycleEventObserver { _, event ->
                                            when (event) {
                                                Lifecycle.Event.ON_START -> mapView.onStart()
                                                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                                                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                                                Lifecycle.Event.ON_STOP -> mapView.onStop()
                                                else -> Unit
                                            }
                                        }.also(lifecycle::addObserver)
                                        addOnDidFailLoadingMapListener { failed = true }
                                        var hasMapTiles = false
                                        addOnTileActionListener { operation, _, _, _, _, _, source ->
                                            if (operation == TileOperation.EndParse && source == MAP_VECTOR_SOURCE) {
                                                hasMapTiles = true
                                            }
                                        }
                                        addOnDidFinishRenderingMapListener { fully ->
                                            if (fully && hasMapTiles) {
                                                ready = true
                                            }
                                        }
                                        getMapAsync { loadedMap ->
                                            map = loadedMap
                                            loadedMap.uiSettings.isRotateGesturesEnabled = false
                                            loadedMap.uiSettings.isTiltGesturesEnabled = false
                                            loadedMap.uiSettings.isLogoEnabled = false
                                            loadedMap.cameraPosition = CameraPosition.Builder()
                                                .target(center)
                                                .zoom(if (initialLocation == null) 6.0 else 14.0)
                                                .build()
                                            pin = center.toCampsiteGeoPoint()
                                            loadedMap.addOnCameraIdleListener {
                                                pin = loadedMap.cameraPosition.target?.toCampsiteGeoPoint()
                                            }
                                            loadedMap.addOnMapClickListener { point ->
                                                loadedMap.moveCamera(CameraUpdateFactory.newLatLng(point))
                                                true
                                            }
                                            loadedMap.setStyle(MAP_STYLE_URL)
                                        }
                                    }
                                },
                                onRelease = { mapView ->
                                    observer?.let(lifecycle::removeObserver)
                                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                        mapView.onPause()
                                    }
                                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                        mapView.onStop()
                                    }
                                    mapView.onDestroy()
                                }
                            )
                        }
                        if (ready) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp).padding(3.dp)
                                )
                            }
                            Column(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                                FilledTonalIconButton(onClick = {
                                    map?.animateCamera(CameraUpdateFactory.zoomIn())
                                }) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = stringResource(R.string.campsite_picker_zoom_in)
                                    )
                                }
                                FilledTonalIconButton(onClick = {
                                    map?.animateCamera(CameraUpdateFactory.zoomOut())
                                }) {
                                    Icon(
                                        Icons.Default.Remove,
                                        contentDescription = stringResource(R.string.campsite_picker_zoom_out)
                                    )
                                }
                            }
                        } else {
                            CircularProgressIndicator()
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.campsite_picker_attribution),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://www.openstreetmap.org/copyright")
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                        Text(stringResource(R.string.campsite_picker_cancel))
                    }
                    Button(
                        enabled = ready && !failed && pin != null,
                        onClick = {
                            map?.cameraPosition?.target?.toCampsiteGeoPoint()?.let(onConfirm)
                        }
                    ) {
                        Text(stringResource(R.string.campsite_picker_confirm))
                    }
                }
            }
        }
    }
}

/** Converts the selected point and keeps the legacy missing-location value reserved. */
private fun LatLng.toCampsiteGeoPoint(): GeoPoint? {
    val point = wrap()
    return GeoPoint(point.latitude, point.longitude).takeUnless { it == GeoPoint(0.0, 0.0) }
}
