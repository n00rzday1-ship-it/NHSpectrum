package com.noorzzz.nhspectrum.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import com.noorzzz.nhspectrum.BuildConfig
import com.noorzzz.nhspectrum.data.wardrive.WardriveSessionState
import com.noorzzz.nhspectrum.ui.theme.*
import com.noorzzz.nhspectrum.viewmodel.ScannerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private enum class BaseMap(val label: String) {
    DARK("DARK"), STREET("STREET"), SATELLITE("SAT")
}

@Composable
fun MapScreen(viewModel: ScannerViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val wardrive by viewModel.wardriveState.collectAsState()
    val scanSnapshot by viewModel.snapshot.collectAsState()
    val mapKey = BuildConfig.MAPTILER_KEY
    var baseMap by remember { mutableStateOf(BaseMap.DARK) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var mapLibre by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapReady by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var hasFix by remember { mutableStateOf(false) }
    var accuracy by remember { mutableFloatStateOf(0f) }
    var latitude by remember { mutableDoubleStateOf(0.0) }
    var longitude by remember { mutableDoubleStateOf(0.0) }
    var followLocation by remember { mutableStateOf(true) }
    var recenterNonce by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Waiting for GPS permission") }
    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }
    val scope = rememberCoroutineScope()

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
                Toast.makeText(context, if (ok) "Wardriving GeoJSON exported" else "Export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun styleUrl(style: BaseMap): String {
        if (mapKey.isNotBlank()) {
            val mapName = when (style) {
                BaseMap.DARK -> "streets-v2-dark"
                BaseMap.STREET -> "streets-v2"
                BaseMap.SATELLITE -> "satellite"
            }
            return "https://api.maptiler.com/maps/$mapName/style.json?key=$mapKey"
        }
        return "https://demotiles.maplibre.org/style.json"
    }

    fun refreshOverlays(map: MapLibreMap, session: WardriveSessionState) {
        val style = map.style ?: return
        val currentFeature = if (hasFix) {
            Feature.fromGeometry(Point.fromLngLat(longitude, latitude)).apply {
                addStringProperty("kind", "phone")
            }
        } else null
        val observationFeatures = session.observations.takeLast(1500).map { observation ->
            Feature.fromGeometry(Point.fromLngLat(observation.longitude, observation.latitude)).apply {
                addStringProperty("kind", observation.type.lowercase())
                addStringProperty("label", observation.label)
                addNumberProperty("rssi", observation.signalDbm)
                addNumberProperty("accuracy", observation.accuracyMeters)
            }
        }
        val droneFeatures = scanSnapshot.drones.mapNotNull { drone ->
            val lat = drone.latitude ?: return@mapNotNull null
            val lon = drone.longitude ?: return@mapNotNull null
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
            Feature.fromGeometry(Point.fromLngLat(lon, lat)).apply {
                addStringProperty("kind", "drone")
                addStringProperty("label", drone.serial.ifBlank { drone.model })
            }
        }
        style.getSourceAs<GeoJsonSource>("nhspectrum-phone")?.setGeoJson(
            FeatureCollection.fromFeatures(if (currentFeature == null) emptyArray() else arrayOf(currentFeature))
        )
        style.getSourceAs<GeoJsonSource>("nhspectrum-observations")?.setGeoJson(
            FeatureCollection.fromFeatures(observationFeatures + droneFeatures)
        )
        val routePoints = session.fixes.map { Point.fromLngLat(it.longitude, it.latitude) }
        val routeFeature = if (routePoints.size >= 2) {
            Feature.fromGeometry(LineString.fromLngLats(routePoints))
        } else null
        style.getSourceAs<GeoJsonSource>("nhspectrum-route")?.setGeoJson(
            FeatureCollection.fromFeatures(if (routeFeature == null) emptyArray() else arrayOf(routeFeature))
        )
    }

    fun loadMapStyle(map: MapLibreMap, selected: BaseMap) {
        map.setStyle(styleUrl(selected)) { style ->
            try {
                style.addSource(GeoJsonSource("nhspectrum-phone", FeatureCollection.fromFeatures(emptyArray())))
                style.addSource(GeoJsonSource("nhspectrum-observations", FeatureCollection.fromFeatures(emptyArray())))
                style.addSource(GeoJsonSource("nhspectrum-route", FeatureCollection.fromFeatures(emptyArray())))
                style.addLayer(CircleLayer("nhspectrum-route-line", "nhspectrum-route").withProperties(
                    lineColor("#00D6B5"), lineWidth(4f)
                ))
                style.addLayer(CircleLayer("nhspectrum-observation-points", "nhspectrum-observations").withProperties(
                    circleColor("#20D8B8"), circleRadius(6f), circleStrokeColor("#06201E"), circleStrokeWidth(1.5f)
                ))
                style.addLayer(CircleLayer("nhspectrum-current-location", "nhspectrum-phone").withProperties(
                    circleColor("#1677FF"), circleRadius(9f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(2.5f)
                ))
                map.uiSettings.isRotateGesturesEnabled = true
                map.uiSettings.isTiltGesturesEnabled = true
                map.uiSettings.isCompassEnabled = true
                map.uiSettings.isAttributionEnabled = true
                mapReady = true
                mapError = null
                if (hasFix) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), 17.0), 650)
                }
                refreshOverlays(map, wardrive)
            } catch (error: Exception) {
                mapReady = false
                mapError = "Map style setup failed: ${error.message ?: error.javaClass.simpleName}"
            }
        }
    }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val fix = result.lastLocation ?: return
                if (!fix.latitude.isFinite() || !fix.longitude.isFinite()) return
                latitude = fix.latitude
                longitude = fix.longitude
                accuracy = fix.accuracy
                hasFix = fix.hasAccuracy() && fix.accuracy > 0f
                status = if (hasFix) "GPS FIX · ±${fix.accuracy.toInt()} m" else "GPS fix received · accuracy unavailable"
                viewModel.updateWardriveLocation(fix.latitude, fix.longitude, fix.accuracy, fix.time)
                mapLibre?.let { map ->
                    refreshOverlays(map, wardrive)
                    if (followLocation && hasFix) {
                        map.animateCamera(CameraUpdateFactory.newLatLng(LatLng(latitude, longitude)), 350)
                    }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val view = mapView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view?.onStart()
                Lifecycle.Event.ON_RESUME -> view?.onResume()
                Lifecycle.Event.ON_PAUSE -> view?.onPause()
                Lifecycle.Event.ON_STOP -> view?.onStop()
                Lifecycle.Event.ON_DESTROY -> view?.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { view?.onPause() }
            runCatching { view?.onStop() }
            runCatching { view?.onDestroy() }
        }
    }

    DisposableEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            status = "Location permission required · grant it in Android Settings"
        } else {
            val priority = if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            val request = LocationRequest.Builder(priority, 2000L)
                .setMinUpdateIntervalMillis(1200L)
                .setWaitForAccurateLocation(fine)
                .build()
            runCatching { fused.requestLocationUpdates(request, locationCallback, context.mainLooper) }
                .onFailure { status = "Location unavailable: ${it.message ?: "permission or provider error"}" }
        }
        onDispose { fused.removeLocationUpdates(locationCallback) }
    }

    LaunchedEffect(baseMap, mapLibre) {
        val map = mapLibre ?: return@LaunchedEffect
        mapReady = false
        if (baseMap == BaseMap.SATELLITE && mapKey.isBlank()) {
            mapError = "Satellite imagery needs a MapTiler key. Street map remains available."
            loadMapStyle(map, BaseMap.DARK)
        } else {
            loadMapStyle(map, baseMap)
        }
    }

    LaunchedEffect(mapLibre, wardrive, hasFix, latitude, longitude, scanSnapshot.drones) {
        mapLibre?.let { refreshOverlays(it, wardrive) }
    }

    LaunchedEffect(recenterNonce, hasFix, latitude, longitude) {
        if (recenterNonce > 0) {
            followLocation = true
            if (hasFix) {
                mapLibre?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), 17.0), 700)
                status = "Following current location · ±${accuracy.toInt()} m"
            } else status = "Waiting for a fresh GPS fix…"
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF061014))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapLibre.getInstance(ctx)
                MapView(ctx).also { view ->
                    mapView = view
                    view.onCreate(Bundle())
                    view.getMapAsync { map ->
                        mapLibre = map
                        map.uiSettings.isZoomGesturesEnabled = true
                        map.uiSettings.isScrollGesturesEnabled = true
                        map.uiSettings.isRotateGesturesEnabled = true
                        map.uiSettings.isTiltGesturesEnabled = true
                        map.addOnCameraMoveStartedListener { reason ->
                            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) followLocation = false
                        }
                        map.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                            .target(if (hasFix) LatLng(latitude, longitude) else LatLng(10.5601, 72.6409))
                            .zoom(if (hasFix) 17.0 else 12.0)
                            .build()
                        loadMapStyle(map, baseMap)
                    }
                }
            },
            update = { }
        )

        Column(Modifier.align(Alignment.TopStart).padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            MapHud(status, highlight = hasFix)
            if (hasFix) {
                MapHud(String.format("%.6f, %.6f", latitude, longitude))
                MapHud("GPS accuracy ±${accuracy.toInt()} m")
            }
        }
        Row(
            Modifier.align(Alignment.TopEnd).padding(10.dp)
                .background(Panel.copy(alpha = 0.96f), RoundedCornerShape(12.dp))
                .border(1.dp, Border, RoundedCornerShape(12.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            BaseMap.entries.forEach { item ->
                Box(
                    Modifier.background(if (baseMap == item) Accent.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable {
                            baseMap = item
                            if (item == BaseMap.SATELLITE && mapKey.isBlank()) {
                                Toast.makeText(context, "Add MAPTILER_KEY to Gradle properties to enable satellite imagery", Toast.LENGTH_LONG).show()
                            }
                        }.padding(horizontal = 10.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) { Text(item.label, color = if (baseMap == item) Accent else TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            }
        }

        Column(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MapControl("＋") { mapLibre?.animateCamera(CameraUpdateFactory.zoomIn()) }
            MapControl("－") { mapLibre?.animateCamera(CameraUpdateFactory.zoomOut()) }
            MapControl("◎") {
                followLocation = true
                recenterNonce++
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(
                Panel.copy(alpha = 0.97f), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
            ).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "${wardrive.distanceMeters / 1000.0} km  ·  ${wardrive.fixes.size} GPS fixes  ·  ${wardrive.observations.size} observations",
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
                        else if (hasFix) {
                            viewModel.startWardriving()
                            viewModel.updateWardriveLocation(latitude, longitude, accuracy, System.currentTimeMillis())
                        } else Toast.makeText(context, "Waiting for an accurate GPS fix", Toast.LENGTH_SHORT).show()
                    }.padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (wardrive.isRecording) "■ STOP WARDRIVING" else "▶ START WARDRIVING",
                    color = if (wardrive.isRecording) Accent else Color(0xFF001A17), fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
            if (!mapError.isNullOrBlank()) Text(mapError!!, color = ThreatColor, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MapHud(text: String, highlight: Boolean = false) {
    Text(text, color = if (highlight) Accent else TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(Panel.copy(alpha = 0.96f), RoundedCornerShape(7.dp))
            .border(1.dp, if (highlight) Accent else Border, RoundedCornerShape(7.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp))
}

@Composable
private fun MapControl(label: String, onClick: () -> Unit) {
    Box(Modifier.background(Panel.copy(alpha = 0.96f), RoundedCornerShape(10.dp))
        .border(1.dp, Border, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(12.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
