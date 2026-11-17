package com.noorzzz.nhspectrum.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
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
import com.noorzzz.nhspectrum.ui.theme.*
import com.noorzzz.nhspectrum.viewmodel.ScannerViewModel
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
    var isRecording by remember { mutableStateOf(false) }
    var hasFix by remember { mutableStateOf(false) }
    var locationAccuracy by remember { mutableStateOf<Float?>(null) }
    var currentLat by remember { mutableDoubleStateOf(0.0) }
    var currentLon by remember { mutableDoubleStateOf(0.0) }
    var status by remember { mutableStateOf("Waiting for location permission") }
    var distanceKm by remember { mutableFloatStateOf(0f) }
    val points = remember { mutableStateListOf<GeoPoint>() }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var routeLine by remember { mutableStateOf<Polyline?>(null) }
    var userMarker by remember { mutableStateOf<Marker?>(null) }
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                currentLat = loc.latitude
                currentLon = loc.longitude
                locationAccuracy = loc.accuracy
                hasFix = loc.hasAccuracy()
                status = if (hasFix) "GPS FIX · ±${loc.accuracy.toInt()} m" else "Location received · accuracy unavailable"
                val point = GeoPoint(loc.latitude, loc.longitude)
                if (isRecording && (points.isEmpty() || points.last().distanceToAsDouble(point) >= 3.0)) {
                    if (points.isNotEmpty()) {
                        val results = FloatArray(1)
                        Location.distanceBetween(points.last().latitude, points.last().longitude, point.latitude, point.longitude, results)
                        distanceKm += results[0] / 1000f
                    }
                    points.add(point)
                    routeLine?.setPoints(points.toList())
                }
                userMarker?.position = point
                mapView?.invalidate()
                if (points.size <= 1) mapView?.controller?.animateTo(point)
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
            val request = LocationRequest.Builder(priority, 1500L).setMinUpdateIntervalMillis(1000L).setWaitForAccurateLocation(fine).build()
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
        mapView?.onResume()
        onDispose { mapView?.onPause() }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                Configuration.getInstance().userAgentValue = ctx.packageName
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(15.0)
                    controller.setCenter(GeoPoint(0.0, 0.0))
                    val marker = Marker(this).apply {
                        position = GeoPoint(0.0, 0.0)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Current device location"
                    }
                    overlays.add(marker)
                    userMarker = marker
                    val line = Polyline().apply {
                        outlinePaint.color = android.graphics.Color.parseColor("#20D6B2")
                        outlinePaint.strokeWidth = 7f
                    }
                    overlays.add(line)
                    routeLine = line
                    mapView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(Modifier.align(Alignment.TopStart).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HudBadge(status, accent = hasFix)
            if (hasFix) {
                HudBadge(String.format("%.6f, %.6f", currentLat, currentLon))
                locationAccuracy?.let { HudBadge("Accuracy ±${it.toInt()} m") }
            }
        }
        HudBadge(String.format("%.2f km · %d points", distanceKm, points.size), modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))

        Box(
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth()
                .background(if (isRecording) Panel else Accent, RoundedCornerShape(10.dp))
                .border(if (isRecording) 1.5.dp else 0.dp, if (isRecording) Accent else Color.Transparent, RoundedCornerShape(10.dp))
                .clickable {
                    if (isRecording) {
                        isRecording = false
                    } else if (hasFix) {
                        points.clear()
                        distanceKm = 0f
                        routeLine?.setPoints(emptyList())
                        isRecording = true
                    } else {
                        status = "Waiting for a valid location fix before recording"
                    }
                }.padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (isRecording) "■ STOP ROUTE RECORDING" else "▶ START WARDRIVING",
                color = if (isRecording) Accent else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
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
            .padding(horizontal = 11.dp, vertical = 6.dp)
    )
}
