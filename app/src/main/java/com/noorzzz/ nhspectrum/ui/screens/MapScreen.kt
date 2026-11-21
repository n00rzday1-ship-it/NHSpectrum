package com.noorzzz.nhspectrum.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.location.Location
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.noorzzz.nhspectrum.ui.theme.*
import com.noorzzz.nhspectrum.viewmodel.ScannerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

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
    var routeLine by remember { mutableStateOf<Polyline?>(null) }
    var userMarker by remember { mutableStateOf<Marker?>(null) }
    val observationMarkers = remember { mutableListOf<Marker>() }

    var hasFix by remember { mutableStateOf(false) }
    var accuracy by remember { mutableFloatStateOf(0f) }
    var currentLat by remember { mutableDoubleStateOf(20.5937) }
    var currentLon by remember { mutableDoubleStateOf(78.9629) }
    var cameraCentered by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("WAITING FOR GPS") }
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
                    marker.position = GeoPoint(fix.latitude, fix.longitude)
                    marker.snippet = "GPS accuracy ±${accuracy.toInt()} m"
                    if (!cameraCentered) {
                        mapView?.controller?.animateTo(marker.position)
                        cameraCentered = true
                    }
                    mapView?.invalidate()
                }
            }
        }
    }

    // Keep the location callback active only while this screen is composed.
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
        } else {
            status = "LOCATION PERMISSION REQUIRED"
        }
        onDispose { fused.removeLocationUpdates(locationCallback) }
    }

    // Drive osmdroid from the owning Android lifecycle. Do not call onResume()
    // from AndroidView.update: Compose may invoke update repeatedly.
    DisposableEffect(lifecycleOwner, mapView) {
        val view = mapView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> runCatching { view?.onResume() }
                Lifecycle.Event.ON_PAUSE -> runCatching { view?.onPause() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            runCatching { view?.onResume() }
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { view?.onPause() }
            runCatching { view?.onDetach() }
        }
    }

    LaunchedEffect(mapView, wardrive.fixes) {
        val map = mapView ?: return@LaunchedEffect
        val points = wardrive.fixes.mapNotNull { fix ->
            if (fix.latitude.isFinite() && fix.longitude.isFinite() &&
                fix.latitude in -90.0..90.0 && fix.longitude in -180.0..180.0
            ) GeoPoint(fix.latitude, fix.longitude) else null
        }
        routeLine?.setPoints(points)
        map.invalidate()
    }

    LaunchedEffect(mapView, wardrive.observations) {
        val map = mapView ?: return@LaunchedEffect
        observationMarkers.forEach { map.overlays.remove(it) }
        observationMarkers.clear()
        wardrive.observations.takeLast(250).forEach { observation ->
            if (!observation.latitude.isFinite() || !observation.longitude.isFinite() ||
                observation.latitude !in -90.0..90.0 || observation.longitude !in -180.0..180.0
            ) return@forEach
            val marker = Marker(map).apply {
                position = GeoPoint(observation.latitude, observation.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "${observation.type.uppercase()} · ${observation.label}"
                snippet = "RSSI ${observation.signalDbm} dBm · GPS ±${observation.accuracyMeters.toInt()} m"
            }
            observationMarkers += marker
            map.overlays.add(marker)
        }
        map.invalidate()
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF061014))) {
        AndroidView<View>(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                try {
                    // Same osmdroid MAPNIK setup as the original NHSpectrum screen.
                    Configuration.getInstance().userAgentValue = "NHSpectrum/2.4.3 (Android)"
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(15.0)
                        val saved = wardrive.fixes.lastOrNull()
                        val initial = if (saved != null &&
                            saved.latitude.isFinite() && saved.longitude.isFinite() &&
                            saved.latitude in -90.0..90.0 && saved.longitude in -180.0..180.0
                        ) GeoPoint(saved.latitude, saved.longitude)
                        else GeoPoint(currentLat, currentLon)
                        controller.setCenter(initial)

                        val marker = Marker(this).apply {
                            position = initial
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "NHSpectrum · Current location"
                        }
                        overlays.add(marker)
                        userMarker = marker

                        val line = Polyline().apply {
                            outlinePaint.color = AndroidColor.parseColor("#FF3B5C")
                            outlinePaint.strokeWidth = 8f
                        }
                        overlays.add(line)
                        routeLine = line
                        mapView = this
                    }
                } catch (error: Exception) {
                    mapError = error.message ?: error.javaClass.simpleName
                    status = "MAP INITIALIZATION FAILED"
                    Log.e("NHSpectrum.Map", "MapView initialization failed", error)
                    FrameLayout(ctx).apply {
                        setBackgroundColor(AndroidColor.rgb(6, 16, 20))
                        addView(TextView(ctx).apply {
                            text = "MAP INITIALIZATION FAILED\n${error.javaClass.simpleName}: ${error.message ?: "No details"}"
                            setTextColor(AndroidColor.WHITE)
                            textSize = 14f
                            setPadding(24, 24, 24, 24)
                        })
                    }
                }
            },
            update = { view ->
                val map = view as? MapView
                if (map != null) {
                    userMarker?.let { marker ->
                        if (hasFix) marker.position = GeoPoint(currentLat, currentLon)
                    }
                    routeLine?.setPoints(wardrive.fixes.mapNotNull { fix ->
                        if (fix.latitude.isFinite() && fix.longitude.isFinite() &&
                            fix.latitude in -90.0..90.0 && fix.longitude in -180.0..180.0
                        ) GeoPoint(fix.latitude, fix.longitude) else null
                    })
                    map.invalidate()
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
            if (mapError == null) "OPENSTREETMAP · MAPNIK" else "MAP ERROR",
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
        )

        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapControl("＋") { mapView?.controller?.zoomIn() }
            MapControl("－") { mapView?.controller?.zoomOut() }
            MapControl("◎") {
                if (hasFix) {
                    mapView?.controller?.animateTo(GeoPoint(currentLat, currentLon))
                    cameraCentered = true
                } else Toast.makeText(context, "Waiting for GPS fix", Toast.LENGTH_SHORT).show()
            }
        }

        Text(
            "© OpenStreetMap contributors",
            color = Color(0xFFE4E9EA), fontSize = 9.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 248.dp)
                .background(Color(0xCC101B1F), RoundedCornerShape(5.dp))
                .padding(horizontal = 7.dp, vertical = 4.dp)
        )

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
