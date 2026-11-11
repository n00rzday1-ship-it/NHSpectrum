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
    var distanceKm by remember { mutableFloatStateOf(0f) }
    var currentLat by remember { mutableDoubleStateOf(12.9716) } // fallback
    var currentLon by remember { mutableDoubleStateOf(77.5946) }

    val points = remember { mutableStateListOf<GeoPoint>() }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var routeLine by remember { mutableStateOf<Polyline?>(null) }
    var userMarker by remember { mutableStateOf<Marker?>(null) }

    // Location client
    val fusedClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                currentLat = loc.latitude
                currentLon = loc.longitude
                val gp = GeoPoint(loc.latitude, loc.longitude)

                if (isRecording) {
                    if (points.isNotEmpty()) {
                        val last = points.last()
                        val results = FloatArray(1)
                        Location.distanceBetween(
                            last.latitude, last.longitude,
                            gp.latitude, gp.longitude, results
                        )
                        distanceKm += results[0] / 1000f
                    }
                    points.add(gp)
                    routeLine?.setPoints(points.toList())
                }

                userMarker?.position = gp
                mapView?.controller?.animateTo(gp)
                mapView?.invalidate()
            }
        }
    }

    DisposableEffect(isRecording) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                .setMinUpdateIntervalMillis(1000L)
                .build()
            fusedClient.requestLocationUpdates(request, locationCallback, context.mainLooper)
        }

        onDispose {
            fusedClient.removeLocationUpdates(locationCallback)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // OSM Map
        AndroidView(
            factory = { ctx ->
                Configuration.getInstance().userAgentValue = ctx.packageName
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(16.0)
                    controller.setCenter(GeoPoint(currentLat, currentLon))

                    val marker = Marker(this).apply {
                        position = GeoPoint(currentLat, currentLon)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "You"
                    }
                    overlays.add(marker)
                    userMarker = marker

                    val line = Polyline().apply {
                        outlinePaint.color = android.graphics.Color.parseColor("#FF2D2D")
                        outlinePaint.strokeWidth = 8f
                    }
                    overlays.add(line)
                    routeLine = line
                    mapView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                map.onResume()
            }
        )

        // HUD
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HudBadge(
                text = if (isRecording) "GPS: RECORDING" else "GPS: FIXED",
                accent = true
            )
            HudBadge(text = "%.2f KM".format(distanceKm))
        }

        // Start / Stop button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth()
                .background(
                    if (isRecording) Panel else Accent,
                    RoundedCornerShape(10.dp)
                )
                .border(
                    if (isRecording) 1.5.dp else 0.dp,
                    if (isRecording) Accent else Color.Transparent,
                    RoundedCornerShape(10.dp)
                )
                .clickable {
                    if (isRecording) {
                        isRecording = false
                    } else {
                        points.clear()
                        distanceKm = 0f
                        routeLine?.setPoints(emptyList())
                        isRecording = true
                    }
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isRecording) "■ STOP WARDRIVE" else "▶ START WARDRIVE",
                color = if (isRecording) Accent else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun HudBadge(text: String, accent: Boolean = false) {
    Text(
        text = text,
        color = if (accent) Accent else TextPrimary,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(Panel, RoundedCornerShape(6.dp))
            .border(1.dp, if (accent) Accent else Border, RoundedCornerShape(6.dp))
            .padding(horizontal = 11.dp, vertical = 6.dp)
    )
}
