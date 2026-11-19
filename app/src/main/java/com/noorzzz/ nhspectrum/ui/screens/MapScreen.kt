package com.noorzzz.nhspectrum.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.preference.PreferenceManager
import android.view.Gravity
import android.view.ViewGroup
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
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.noorzzz.nhspectrum.data.wardrive.WardriveObservation
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

@Composable
fun MapScreen(viewModel: ScannerViewModel) {
    val context = LocalContext.current
    val wardrive by viewModel.wardriveState.collectAsState()
    val scanSnapshot by viewModel.snapshot.collectAsState()
    var hasFix by remember { mutableStateOf(false) }
    var locationAccuracy by remember { mutableFloatStateOf(0f) }
    var currentLat by remember { mutableDoubleStateOf(0.0) }
    var currentLon by remember { mutableDoubleStateOf(0.0) }
    var status by remember { mutableStateOf("Waiting for location permission") }
    var mapInitError by remember { mutableStateOf<String?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var routeLine by remember { mutableStateOf<Polyline?>(null) }
    var userMarker by remember { mutableStateOf<Marker?>(null) }
    var mapHasCentered by remember { mutableStateOf(false) }
    var recenterRequested by remember { mutableIntStateOf(0) }
    var pendingMapRecenter by remember { mutableStateOf(false) }
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/geo+json")
    ) { uri ->
        if (uri != null) {
            val geoJson = viewModel.exportWardriveGeoJson()
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(geoJson) }
                        true
                    } catch (_: Exception) { false }
                }
                Toast.makeText(context, if (success) "Wardriving GeoJSON exported" else "Export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                currentLat = loc.latitude
                currentLon = loc.longitude
                locationAccuracy = loc.accuracy
                hasFix = loc.hasAccuracy() && loc.accuracy > 0f
                status = if (hasFix) "GPS FIX · ±${loc.accuracy.toInt()} m" else "Location received · accuracy unavailable"
                viewModel.updateWardriveLocation(loc.latitude, loc.longitude, loc.accuracy, loc.time)

                val point = GeoPoint(loc.latitude, loc.longitude)
                val activeMap = mapView
                if (activeMap != null && userMarker == null) {
                    val marker = Marker(activeMap).apply {
                        position = point
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Current device location · live GPS"
                    }
                    activeMap.overlays.add(marker)
                    userMarker = marker
                }
                userMarker?.position = point
                activeMap?.invalidate()
                if (activeMap == null) {
                    pendingMapRecenter = true
                } else if (!mapHasCentered || pendingMapRecenter) {
                    activeMap.controller.animateTo(point)
                    activeMap.controller.setZoom(18.0)
                    mapHasCentered = true
                    pendingMapRecenter = false
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            status = "Location permission required"
        } else {
            val priority = if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            val request = LocationRequest.Builder(priority, 1500L)
                .setMinUpdateIntervalMillis(1000L)
                .setWaitForAccurateLocation(fine)
                .build()
            try {
                fusedClient.requestLocationUpdates(request, locationCallback, context.mainLooper)
                status = "Acquiring live location…"
            } catch (_: SecurityException) {
                status = "Location permission denied"
            }
        }
        onDispose { fusedClient.removeLocationUpdates(locationCallback) }
    }

    DisposableEffect(mapView) {
        runCatching { mapView?.onResume() }
            .onFailure { status = "Map renderer could not resume: ${it.message ?: "unknown error"}" }
        onDispose {
            // Compose AndroidView manages attachment. Pause the osmdroid view
            // when this screen leaves composition; avoid manually detaching a
            // View that AndroidView may still be releasing.
            runCatching { mapView?.onPause() }
        }
    }

    LaunchedEffect(mapView, hasFix, currentLat, currentLon) {
        val activeMap = mapView ?: return@LaunchedEffect
        if (!hasFix) return@LaunchedEffect
        val point = GeoPoint(currentLat, currentLon)
        if (userMarker == null) {
            val marker = Marker(activeMap).apply {
                position = point
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "Current phone location · GPS"
                icon = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.OvalShape()).apply {
                    paint.color = android.graphics.Color.rgb(25, 118, 255)
                    setBounds(0, 0, 28, 28)
                }
            }
            userMarker = marker
            activeMap.overlays.add(marker)
        } else {
            userMarker?.position = point
        }
        if (!mapHasCentered || pendingMapRecenter) {
            activeMap.controller.animateTo(point)
            activeMap.controller.setZoom(18.0)
            mapHasCentered = true
            pendingMapRecenter = false
        }
        activeMap.invalidate()
    }

    LaunchedEffect(recenterRequested, hasFix, currentLat, currentLon, mapView) {
        if (recenterRequested == 0) return@LaunchedEffect
        val activeMap = mapView
        if (activeMap == null) {
            pendingMapRecenter = true
        } else if (hasFix) {
            activeMap.controller.animateTo(GeoPoint(currentLat, currentLon))
            activeMap.controller.setZoom(18.0)
        } else {
            pendingMapRecenter = true
            status = "Waiting for a valid GPS fix to locate this phone"
        }
    }

    LaunchedEffect(mapView, wardrive.fixes) {
        val activeMap = mapView ?: return@LaunchedEffect
        routeLine?.setPoints(wardrive.fixes.map { GeoPoint(it.latitude, it.longitude) })
        activeMap.invalidate()
    }

    LaunchedEffect(mapView, wardrive.observations) {
        val activeMap = mapView ?: return@LaunchedEffect
        val oldMarkers = activeMap.overlays.filterIsInstance<Marker>().filter { it.title?.startsWith("NHSpectrum observation:") == true }
        activeMap.overlays.removeAll(oldMarkers.toSet())
        wardrive.observations.takeLast(500).forEach { observation ->
            val marker = Marker(activeMap).apply {
                position = GeoPoint(observation.latitude, observation.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "NHSpectrum observation: ${observation.type.uppercase()} · ${observation.label}"
                snippet = "${observation.signalDbm} dBm · ±${observation.accuracyMeters.toInt()} m"
            }
            activeMap.overlays.add(marker)
        }
        activeMap.invalidate()
    }

    LaunchedEffect(mapView, scanSnapshot.drones) {
        val activeMap = mapView ?: return@LaunchedEffect
        val oldDroneMarkers = activeMap.overlays.filterIsInstance<Marker>()
            .filter { it.title?.startsWith("Remote ID aircraft position:") == true }
        activeMap.overlays.removeAll(oldDroneMarkers.toSet())
        scanSnapshot.drones.forEach { drone ->
            val lat = drone.latitude ?: return@forEach
            val lon = drone.longitude ?: return@forEach
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@forEach
            activeMap.overlays.add(Marker(activeMap).apply {
                position = GeoPoint(lat, lon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Remote ID aircraft position: ${drone.serial.ifBlank { drone.model }}"
                snippet = "Broadcast position · RSSI ${drone.rssi} dBm · not phone-based triangulation"
            })
        }
        activeMap.invalidate()
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                try {
                    // osmdroid requires its cache/preferences to be initialized before MapView.
                    val configuration = Configuration.getInstance()
                    configuration.load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
                    configuration.userAgentValue = ctx.packageName
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(15.0)
                        // Use the latest phone fix when available; otherwise show a regional view
                        // and let the explicit location control recenter when GPS becomes ready.
                        val initial = if (hasFix) GeoPoint(currentLat, currentLon) else GeoPoint(20.0, 0.0)
                        controller.setCenter(initial)
                        if (hasFix) controller.setZoom(18.0)
                        val line = Polyline().apply {
                            outlinePaint.color = android.graphics.Color.parseColor("#FF465A")
                            outlinePaint.strokeWidth = 6f
                            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                            outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                        }
                        overlays.add(line)
                        routeLine = line
                        mapView = this
                        mapInitError = null
                    }
                } catch (error: Exception) {
                    // A vendor/storage/map-library initialization failure must not take down the app.
                    mapInitError = error.message ?: error.javaClass.simpleName
                    android.util.Log.e("NHSpectrum", "Map initialization failed", error)
                    FrameLayout(ctx).apply {
                        setBackgroundColor(android.graphics.Color.rgb(5, 13, 17))
                        addView(TextView(ctx).apply {
                            text = "Map could not initialize. Other NHSpectrum features remain available.\n\n${mapInitError ?: "Map service unavailable"}"
                            setTextColor(android.graphics.Color.WHITE)
                            textSize = 14f
                            gravity = Gravity.CENTER
                            setPadding(32, 32, 32, 32)
                        }, FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        ))
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(Modifier.align(Alignment.TopStart).padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            HudBadge(status, accent = hasFix)
            if (hasFix) {
                HudBadge(String.format("%.6f, %.6f", currentLat, currentLon))
                HudBadge("Accuracy ±${locationAccuracy.toInt()} m")
            }
        }
        HudBadge(
            String.format("%.2f km · %d fixes · %d observations",
                wardrive.distanceMeters / 1000.0, wardrive.fixes.size, wardrive.observations.size),
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
        )
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 58.dp, end = 10.dp)
                .background(Panel, RoundedCornerShape(10.dp))
                .border(1.dp, Accent, RoundedCornerShape(10.dp))
                .clickable { recenterRequested++ }
                .padding(horizontal = 13.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("◎ MY LOCATION", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(if (wardrive.isRecording) Panel else Accent, RoundedCornerShape(10.dp))
                    .border(if (wardrive.isRecording) 1.5.dp else 0.dp, if (wardrive.isRecording) Accent else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable {
                        if (wardrive.isRecording) {
                            viewModel.stopWardriving()
                        } else if (hasFix) {
                            viewModel.startWardriving()
                            viewModel.updateWardriveLocation(currentLat, currentLon, locationAccuracy, System.currentTimeMillis())
                        } else {
                            status = "Waiting for a valid GPS fix before recording"
                        }
                    }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (wardrive.isRecording) "■ STOP WARDRIVING" else "▶ START WARDRIVING",
                    color = if (wardrive.isRecording) Accent else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier.weight(1f).background(Panel, RoundedCornerShape(10.dp))
                        .border(1.dp, Border, RoundedCornerShape(10.dp))
                        .clickable { exportLauncher.launch("NHSpectrum-wardrive.geojson") }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("EXPORT GEOJSON · ${wardrive.observations.size}", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier.weight(1f).background(Panel, RoundedCornerShape(10.dp))
                        .border(1.dp, Border, RoundedCornerShape(10.dp))
                        .clickable {
                            if (wardrive.isRecording) {
                                Toast.makeText(context, "Stop the current session before clearing it", Toast.LENGTH_SHORT).show()
                            } else {
                                val cleared = viewModel.clearWardriveSession()
                                Toast.makeText(context, if (cleared) "Previous route cleared · ready for a fresh session" else "Could not clear session", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("CLEAR OLD SESSION", color = ThreatColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HudBadge(text: String, accent: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = if (accent) Accent else TextPrimary,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.background(Panel, RoundedCornerShape(6.dp))
            .border(1.dp, if (accent) Accent else Border, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}
