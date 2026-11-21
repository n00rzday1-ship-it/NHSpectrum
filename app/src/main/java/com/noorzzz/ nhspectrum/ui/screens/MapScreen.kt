package com.noorzzz.nhspectrum.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.*
import com.noorzzz.nhspectrum.ui.theme.*
import com.noorzzz.nhspectrum.viewmodel.ScannerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

private const val NHSPECTRUM_MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(viewModel: ScannerViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val wardrive by viewModel.wardriveState.collectAsState()
    val scanSnapshot by viewModel.snapshot.collectAsState()
    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }
    val scope = rememberCoroutineScope()

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var nativeMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var userMarker by remember { mutableStateOf<Marker?>(null) }
    var routeLine by remember { mutableStateOf<Polyline?>(null) }
    val observationMarkers = remember { mutableStateListOf<Marker>() }

    var hasFix by remember { mutableStateOf(false) }
    var accuracy by remember { mutableFloatStateOf(0f) }
    var currentLat by remember { mutableDoubleStateOf(20.5937) }
    var currentLon by remember { mutableDoubleStateOf(78.9629) }
    var cameraCentered by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("WAITING FOR GPS") }
    var styleReady by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/geo+json")
    ) { uri ->
        if (uri != null) {
            val json = viewModel.exportWardriveGeoJson()
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                            ?: error("Unable to open export destination")
                    }.isSuccess
                }
                Toast.makeText(context, if (ok) "GeoJSON exported" else "Export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val fix: Location = result.lastLocation ?: return
                if (!fix.latitude.isFinite() || !fix.longitude.isFinite() ||
                    fix.latitude !in -90.0..90.0 || fix.longitude !in -180.0..180.0
                ) return

                currentLat = fix.latitude
                currentLon = fix.longitude
                accuracy = if (fix.hasAccuracy() && fix.accuracy.isFinite()) fix.accuracy else 0f
                hasFix = accuracy > 0f
                status = when {
                    !hasFix -> "GPS FIX · ACCURACY UNKNOWN"
                    accuracy > 25f -> "GPS WEAK · ±${accuracy.toInt()} m"
                    else -> "GPS FIX · ±${accuracy.toInt()} m"
                }
                viewModel.updateWardriveLocation(fix.latitude, fix.longitude, accuracy, fix.time)

                userMarker?.let { marker ->
                    marker.position = LatLng(fix.latitude, fix.longitude)
                    marker.snippet = "GPS accuracy ±${accuracy.toInt()} m"
                    nativeMap?.updateMarker(marker)
                }
                if (!cameraCentered && styleReady) {
                    nativeMap?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(fix.latitude, fix.longitude)))
                    cameraCentered = true
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            val priority = if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            val request = LocationRequest.Builder(priority, 2000L)
                .setMinUpdateIntervalMillis(1000L)
                .build()
            runCatching { fused.requestLocationUpdates(request, locationCallback, context.mainLooper) }
                .onFailure {
                    status = "GPS UNAVAILABLE"
                    Log.e("NHSpectrum.Map", "Location updates failed", it)
                }
        } else status = "LOCATION PERMISSION REQUIRED"
        onDispose { fused.removeLocationUpdates(locationCallback) }
    }

    // Forward every Android lifecycle event to MapLibre's native MapView.
    DisposableEffect(lifecycleOwner, mapView) {
        val view = mapView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> runCatching { view?.onStart() }
                Lifecycle.Event.ON_RESUME -> runCatching { view?.onResume() }
                Lifecycle.Event.ON_PAUSE -> runCatching { view?.onPause() }
                Lifecycle.Event.ON_STOP -> runCatching { view?.onStop() }
                Lifecycle.Event.ON_DESTROY -> runCatching { view?.onDestroy() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { view?.onPause() }
            runCatching { view?.onStop() }
            runCatching { view?.onDestroy() }
            nativeMap = null
            userMarker = null
            routeLine = null
            observationMarkers.clear()
        }
    }

    LaunchedEffect(nativeMap, styleReady, wardrive.fixes) {
        val map = nativeMap ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        val points = wardrive.fixes.mapNotNull { fix ->
            if (fix.latitude.isFinite() && fix.longitude.isFinite() &&
                fix.latitude in -90.0..90.0 && fix.longitude in -180.0..180.0
            ) LatLng(fix.latitude, fix.longitude) else null
        }
        routeLine?.let { runCatching { map.removePolyline(it) } }
        routeLine = if (points.size >= 2) {
            runCatching {
                map.addPolyline(
                    PolylineOptions().addAll(points)
                        .color(android.graphics.Color.rgb(0, 214, 178))
                        .width(4.5f)
                )
            }.getOrNull()
        } else null
    }

    LaunchedEffect(nativeMap, styleReady, wardrive.observations) {
        val map = nativeMap ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        observationMarkers.forEach { marker -> runCatching { map.removeMarker(marker) } }
        observationMarkers.clear()
        // Bound annotation count to keep the map responsive on mid-range phones.
        wardrive.observations.takeLast(100).forEach { observation ->
            if (!observation.latitude.isFinite() || !observation.longitude.isFinite() ||
                observation.latitude !in -90.0..90.0 || observation.longitude !in -180.0..180.0
            ) return@forEach
            runCatching {
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(LatLng(observation.latitude, observation.longitude))
                        .title("${observation.type.uppercase()} · ${observation.label}")
                        .snippet("RSSI ${observation.signalDbm} dBm · GPS ±${observation.accuracyMeters.toInt()} m")
                )
                observationMarkers.add(marker)
            }.onFailure { Log.w("NHSpectrum.Map", "Observation marker skipped", it) }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF061014))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                try {
                    MapLibre.getInstance(ctx.applicationContext)
                    MapView(ctx).apply {
                        onCreate(null)
                        // This Composable can be entered after the Activity has already
                        // received ON_START/ON_RESUME. Lifecycle observers do not replay
                        // those events, so activate MapLibre's FileSource explicitly now.
                        onStart()
                        onResume()
                        addOnDidFailLoadingMapListener { errorMessage ->
                            mapError = errorMessage
                            status = "MAP LOAD FAILED"
                            Log.e("NHSpectrum.Map", "MapLibre failed to load style/map: $errorMessage")
                        }
                        mapView = this
                        getMapAsync { map ->
                            nativeMap = map
                            map.uiSettings.isRotateGesturesEnabled = true
                            map.uiSettings.isTiltGesturesEnabled = true
                            map.uiSettings.isZoomGesturesEnabled = true
                            map.setPrefetchesTiles(true)
                            map.setTileCacheEnabled(true)
                            val saved = wardrive.fixes.lastOrNull()
                            val initial = if (saved != null &&
                                saved.latitude.isFinite() && saved.longitude.isFinite() &&
                                saved.latitude in -90.0..90.0 && saved.longitude in -180.0..180.0
                            ) LatLng(saved.latitude, saved.longitude) else LatLng(currentLat, currentLon)

                            map.setStyle(NHSPECTRUM_MAP_STYLE) {
                                styleReady = true
                                status = if (hasFix) "VECTOR MAP READY · GPS FIX" else "VECTOR MAP READY · WAITING FOR GPS"
                                map.cameraPosition = CameraPosition.Builder()
                                    .target(initial)
                                    .zoom(15.0)
                                    .tilt(42.0)
                                    .build()
                                userMarker = map.addMarker(
                                    MarkerOptions()
                                        .position(initial)
                                        .title("NHSpectrum · Current location")
                                        .snippet("Waiting for GPS fix")
                                )
                            }
                        }
                    }
                } catch (error: Exception) {
                    mapError = error.message ?: error.javaClass.simpleName
                    status = "MAP INITIALIZATION FAILED"
                    Log.e("NHSpectrum.Map", "MapLibre initialization failed", error)
                    android.widget.FrameLayout(ctx).apply {
                        setBackgroundColor(android.graphics.Color.rgb(6, 16, 20))
                        addView(android.widget.TextView(ctx).apply {
                            text = "MAP INITIALIZATION FAILED\n${error.javaClass.simpleName}: ${error.message ?: "No details"}"
                            setTextColor(android.graphics.Color.WHITE)
                            textSize = 14f
                            setPadding(24, 24, 24, 24)
                        })
                    }
                }
            },
            update = { view ->
                if (view is MapView && styleReady) {
                    userMarker?.let { marker ->
                        if (hasFix) {
                            marker.position = LatLng(currentLat, currentLon)
                            nativeMap?.updateMarker(marker)
                        }
                    }
                }
            }
        )

        Column(
            Modifier.align(Alignment.TopStart).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            MapHud(status, highlight = hasFix && accuracy <= 25f)
            if (hasFix) {
                MapHud(String.format("%.6f, %.6f", currentLat, currentLon))
                MapHud("GPS accuracy ±${accuracy.toInt()} m")
            }
        }

        MapHud(
            if (mapError != null) "MAP ERROR" else if (styleReady) "MAPLIBRE · OPENFREEMAP" else "LOADING VECTOR MAP",
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
        )

        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapControl("＋") { nativeMap?.animateCamera(CameraUpdateFactory.zoomIn()) }
            MapControl("－") { nativeMap?.animateCamera(CameraUpdateFactory.zoomOut()) }
            MapControl("◎") {
                if (hasFix) {
                    nativeMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(currentLat, currentLon), 16.0))
                    cameraCentered = true
                } else Toast.makeText(context, "Waiting for GPS fix", Toast.LENGTH_SHORT).show()
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(
                Panel.copy(alpha = 0.97f), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
            ).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "${"%.1f".format(wardrive.distanceMeters / 1000.0)} km  ·  ${wardrive.fixes.size} GPS fixes  ·  ${wardrive.observations.size} observations",
                color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MapHud("Wi-Fi ${wardrive.observations.count { it.type.equals("wifi", true) }}")
                MapHud("BLE ${wardrive.observations.count { it.type.equals("ble", true) }}")
                MapHud("Cell ${wardrive.observations.count { it.type.equals("cell", true) }}")
                MapHud("Drone ${scanSnapshot.drones.size}")
            }
            Box(
                Modifier.fillMaxWidth().background(if (wardrive.isRecording) Panel else Accent, RoundedCornerShape(10.dp))
                    .border(1.dp, Accent, RoundedCornerShape(10.dp))
                    .clickable {
                        if (wardrive.isRecording) viewModel.stopWardriving()
                        else if (hasFix && accuracy <= 25f) {
                            viewModel.startWardriving()
                            viewModel.updateWardriveLocation(currentLat, currentLon, accuracy, System.currentTimeMillis())
                        } else Toast.makeText(context, "Need GPS accuracy of 25 m or better to start route capture", Toast.LENGTH_SHORT).show()
                    }.padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (wardrive.isRecording) "■ STOP WARDRIVING" else "▶ START WARDRIVING",
                    color = if (wardrive.isRecording) Accent else Color(0xFF001A17),
                    fontWeight = FontWeight.Bold, fontSize = 13.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).background(Bg, RoundedCornerShape(9.dp)).clickable {
                    exportLauncher.launch("NHSpectrum-wardrive.geojson")
                }.padding(12.dp), contentAlignment = Alignment.Center) {
                    Text("EXPORT GEOJSON", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.weight(1f).background(Bg, RoundedCornerShape(9.dp)).clickable {
                    if (wardrive.isRecording) Toast.makeText(context, "Stop the session before clearing", Toast.LENGTH_SHORT).show()
                    else Toast.makeText(context, if (viewModel.clearWardriveSession()) "Session cleared" else "Could not clear session", Toast.LENGTH_SHORT).show()
                }.padding(12.dp), contentAlignment = Alignment.Center) {
                    Text("CLEAR SESSION", color = ThreatColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MapHud(text: String, highlight: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text, color = if (highlight) Accent else TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
        modifier = modifier.background(Panel.copy(alpha = 0.96f), RoundedCornerShape(7.dp))
            .border(1.dp, if (highlight) Accent else Border, RoundedCornerShape(7.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp)
    )
}

@Composable
private fun MapControl(label: String, onClick: () -> Unit) {
    Box(
        Modifier.background(Panel.copy(alpha = 0.96f), RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
