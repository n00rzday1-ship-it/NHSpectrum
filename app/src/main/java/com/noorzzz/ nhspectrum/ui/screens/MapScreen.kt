package com.noorzzz.nhspectrum.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
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
import androidx.core.content.ContextCompat
import com.noorzzz.nhspectrum.ui.theme.*
import com.noorzzz.nhspectrum.viewmodel.ScannerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import com.noorzzz.nhspectrum.data.wardrive.WardriveFix
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private const val MAP_TAG = "NHSpectrum.Map"

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(viewModel: ScannerViewModel, visible: Boolean = true) {
    val context = LocalContext.current
    val wardrive by viewModel.wardriveState.collectAsState()
    val snapshot by viewModel.snapshot.collectAsState()
    val scope = rememberCoroutineScope()

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var tileHandler by remember { mutableStateOf<Handler?>(null) }
    var routeLine by remember { mutableStateOf<Polyline?>(null) }
    var lastRenderedRouteTimestamp by remember { mutableLongStateOf(Long.MIN_VALUE) }
    var userMarker by remember { mutableStateOf<Marker?>(null) }
    val observationMarkers = remember { mutableListOf<Marker>() }

    var hasFix by remember { mutableStateOf(false) }
    var accuracy by remember { mutableFloatStateOf(0f) }
    var speedKmh by remember { mutableFloatStateOf(0f) }
    var bearingDegrees by remember { mutableFloatStateOf(Float.NaN) }
    var fixAgeSeconds by remember { mutableIntStateOf(0) }
    var elapsedRecordingSeconds by remember { mutableLongStateOf(0L) }
    var lastFixElapsedNanos by remember { mutableLongStateOf(0L) }
    var lastFixTimestampMillis by remember { mutableLongStateOf(0L) }
    var latitude by remember { mutableDoubleStateOf(0.0) }
    var longitude by remember { mutableDoubleStateOf(0.0) }
    var cameraCentered by remember { mutableStateOf(false) }
    var followLocation by remember { mutableStateOf(true) }
    var gpsStatus by remember { mutableStateOf("WAITING FOR GPS") }
    var tileSuccess by remember { mutableIntStateOf(0) }
    var tileFailures by remember { mutableIntStateOf(0) }
    var mapStatus by remember { mutableStateOf("STARTING MAP ENGINE") }
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

    val locationManager = remember {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    // Rebuild the native route overlay when a new accepted GPS point arrives.
    // Replacing the overlay (rather than relying only on Compose invalidation) avoids
    // stale osmdroid path/bounds caches and makes the line visible immediately.
    fun syncRouteOverlay(map: MapView, fixes: List<WardriveFix>) {
        val points = fixes.takeLast(5000).mapNotNull { fix ->
            if (fix.latitude.isFinite() && fix.longitude.isFinite() &&
                fix.latitude in -90.0..90.0 && fix.longitude in -180.0..180.0
            ) GeoPoint(fix.latitude, fix.longitude) else null
        }
        val newestTimestamp = fixes.lastOrNull()?.timestamp ?: Long.MIN_VALUE
        val existing = routeLine
        if (existing == null || newestTimestamp != lastRenderedRouteTimestamp || !map.overlays.contains(existing)) {
            existing?.let { map.overlays.remove(it) }
            val replacement = Polyline().apply {
                setPoints(points)
                outlinePaint.color = AndroidColor.parseColor("#FFFF3158")
                outlinePaint.strokeWidth = 9f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                outlinePaint.isAntiAlias = true
                isEnabled = true
            }
            // Insert before the live-position marker so the marker remains visible.
            map.overlays.add(0, replacement)
            routeLine = replacement
            lastRenderedRouteTimestamp = newestTimestamp
        } else {
            existing.setPoints(points)
        }
        map.invalidate()
        map.postInvalidateOnAnimation()
    }

    val locationListener = remember {
        object : LocationListener {
            override fun onLocationChanged(fix: Location) {
                if (!fix.hasAccuracy() || !fix.accuracy.isFinite() || fix.accuracy <= 0f ||
                    !fix.latitude.isFinite() || !fix.longitude.isFinite() ||
                    fix.latitude !in -90.0..90.0 || fix.longitude !in -180.0..180.0
                ) return
                val ageNanos = SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos
                if (ageNanos < 0L || ageNanos > 15_000_000_000L) return

                latitude = fix.latitude
                longitude = fix.longitude
                accuracy = fix.accuracy
                speedKmh = if (fix.hasSpeed() && fix.speed.isFinite()) (fix.speed * 3.6f).coerceAtLeast(0f) else 0f
                bearingDegrees = if (fix.hasBearing() && fix.bearing.isFinite()) fix.bearing else Float.NaN
                lastFixElapsedNanos = fix.elapsedRealtimeNanos
                lastFixTimestampMillis = fix.time
                fixAgeSeconds = 0
                hasFix = true
                val speedMps = if (fix.hasSpeed() && fix.speed.isFinite()) fix.speed else null
                val speedAccuracyMps = if (android.os.Build.VERSION.SDK_INT >= 26 &&
                    fix.hasSpeedAccuracy() && fix.speedAccuracyMetersPerSecond.isFinite()
                ) fix.speedAccuracyMetersPerSecond else null
                // One GPS stream drives both the live marker and the wardriving recorder.
                // This avoids duplicate provider subscriptions and timestamp drift.
                viewModel.updateWardriveLocation(
                    fix.latitude, fix.longitude, fix.accuracy, fix.time, speedMps, speedAccuracyMps
                )
                // Push the just-recorded state straight into osmdroid on the GPS callback.
                // This does not wait for Compose to recompose or for a tab transition.
                if (viewModel.wardriveState.value.isRecording) {
                    mapView?.let { activeMap ->
                        activeMap.post {
                            if (mapView === activeMap) {
                                syncRouteOverlay(activeMap, viewModel.wardriveState.value.fixes)
                            }
                        }
                    }
                }
                gpsStatus = when {
                    accuracy > 100f -> "GPS VERY WEAK · ±${accuracy.toInt()} m"
                    accuracy > 50f -> "GPS WEAK · ±${accuracy.toInt()} m"
                    accuracy > 25f -> "GPS MODERATE · ±${accuracy.toInt()} m"
                    else -> "GPS FIX · ±${accuracy.toInt()} m"
                }
            }

            override fun onProviderDisabled(provider: String) {
                if (provider == LocationManager.GPS_PROVIDER) gpsStatus = "GPS DISABLED · ENABLE DEVICE LOCATION"
            }

            override fun onProviderEnabled(provider: String) {
                if (provider == LocationManager.GPS_PROVIDER) gpsStatus = "WAITING FOR FRESH GPS FIX"
            }
        }
    }

    // For wardriving, use fresh satellite GPS measurements rather than a cached
    // fused/network location which can remain nearly stationary during movement.
    DisposableEffect(context, locationManager, locationListener) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        var registered = false
        try {
            if (fine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener, context.mainLooper
                )
                registered = true
                gpsStatus = "WAITING FOR FRESH GPS FIX"
                val last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (last != null) locationListener.onLocationChanged(last)
            } else if (fine) {
                gpsStatus = "GPS DISABLED · ENABLE DEVICE LOCATION"
            } else if (coarse && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 3000L, 0f, locationListener, context.mainLooper
                )
                registered = true
                gpsStatus = "APPROXIMATE LOCATION · ENABLE PRECISE GPS"
            } else {
                gpsStatus = "PRECISE LOCATION PERMISSION REQUIRED"
            }
        } catch (error: Exception) {
            gpsStatus = "GPS UNAVAILABLE · ${error.javaClass.simpleName}"
            Log.e(MAP_TAG, "GPS provider subscription failed", error)
        }
        onDispose {
            if (registered) runCatching { locationManager.removeUpdates(locationListener) }
        }
    }

    LaunchedEffect(hasFix, lastFixElapsedNanos) {
        if (hasFix && lastFixElapsedNanos > 0L) {
            while (isActive) {
                val age = (SystemClock.elapsedRealtimeNanos() - lastFixElapsedNanos).coerceAtLeast(0L)
                fixAgeSeconds = (age / 1_000_000_000L).toInt()
                delay(1000L)
            }
        }
    }

    LaunchedEffect(wardrive.isRecording, wardrive.fixes.firstOrNull()?.timestamp) {
        if (wardrive.isRecording) {
            while (isActive) {
                val startedAt = wardrive.fixes.firstOrNull()?.timestamp
                elapsedRecordingSeconds = if (startedAt != null) {
                    (System.currentTimeMillis() - startedAt).coerceAtLeast(0L) / 1000L
                } else 0L
                delay(1000L)
            }
        } else {
            elapsedRecordingSeconds = 0L
        }
    }

    // Match the last green osmdroid lifecycle pattern (build #316):
    // resume once when this screen enters composition, pause on disposal.
    // AndroidView owns View detachment; never call MapView.onDetach() manually.
    DisposableEffect(mapView) {
        val view = mapView
        runCatching { view?.onResume() }
            .onFailure { Log.e(MAP_TAG, "MapView resume failed", it) }
        onDispose {
            tileHandler?.let { handler ->
                handler.removeCallbacksAndMessages(null)
                runCatching { view?.tileProvider?.getTileRequestCompleteHandlers()?.remove(handler) }
            }
            runCatching { view?.onPause() }
            if (mapView === view) {
                mapView = null
                tileHandler = null
                routeLine = null
                userMarker = null
                observationMarkers.clear()
            }
        }
    }

    LaunchedEffect(mapView) {
        val map = mapView ?: return@LaunchedEffect
        delay(9000L)
        if (mapView === map && tileSuccess == 0 && mapError == null) {
            mapStatus = if (hasValidatedInternet(context)) {
                "NO MAP TILES · PROVIDER/REQUEST FAILED"
            } else {
                "NO MAP TILES · CHECK INTERNET"
            }
        }
    }

    LaunchedEffect(mapView, hasFix, latitude, longitude) {
        val map = mapView ?: return@LaunchedEffect
        if (hasFix) {
            val point = GeoPoint(latitude, longitude)
            // Keep the original osmdroid pin marker used by the earlier working map.
            val marker = userMarker ?: Marker(map).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "NHSpectrum · Live GPS location"
            }.also {
                map.overlays.add(it)
                userMarker = it
            }
            marker.position = point
            marker.snippet = "Live GPS · accuracy ±${accuracy.toInt()} m"
            if (!cameraCentered) {
                map.controller.setZoom(17.0)
                map.controller.setCenter(point)
                cameraCentered = true
            } else if (followLocation) {
                // Do not restart an animation on every one-second GPS update.
                // setCenter keeps the camera locked to the accepted live fix.
                map.controller.setCenter(point)
            }
        }
        map.invalidate()
    }

    LaunchedEffect(mapView, wardrive.fixes) {
        val map = mapView ?: return@LaunchedEffect
        syncRouteOverlay(map, wardrive.fixes)
    }

    // Route updates are event-driven from the single GPS StateFlow and native AndroidView.update.
    // No polling loop or tab-switch refresh is required.

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

    // Keep the native MapView at its real measured size for its entire lifetime.
    // INVISIBLE prevents drawing/touch interception on other tabs without collapsing it.
    Box(Modifier.fillMaxSize().background(Color(0xFF061014))) {
        AndroidView<View>(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                try {
                    // Restore the known-good #316 osmdroid configuration first.
                    // Keep its default cache when writable; fall back to app-private storage
                    // only when Android/device storage makes that cache unusable.
                    val configuration = Configuration.getInstance()
                    configuration.load(ctx, androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx))
                    configuration.userAgentValue = "NHSpectrum/2.4.3 (${ctx.packageName}; Android)"
                    val defaultCache = configuration.osmdroidTileCache
                    val defaultCacheWritable = runCatching {
                        defaultCache.mkdirs()
                        val probe = java.io.File.createTempFile("nhspectrum-tile-check", ".tmp", defaultCache)
                        probe.delete()
                    }.getOrDefault(false)
                    if (!defaultCacheWritable) {
                        val fallbackRoot = java.io.File(ctx.filesDir, "NHSpectrum/osmdroid")
                        val fallbackTiles = java.io.File(fallbackRoot, "tiles")
                        fallbackRoot.mkdirs()
                        fallbackTiles.mkdirs()
                        configuration.osmdroidBasePath = fallbackRoot
                        configuration.osmdroidTileCache = fallbackTiles
                        Log.w(MAP_TAG, "Default osmdroid cache unavailable; using app-private cache: ${fallbackTiles.absolutePath}")
                    } else {
                        Log.i(MAP_TAG, "Using osmdroid tile cache: ${defaultCache.absolutePath}")
                    }

                    val map = MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        tileProvider.setUseDataConnection(true)
                        visibility = if (visible) View.VISIBLE else View.INVISIBLE
                        setMultiTouchControls(true)
                        setMinZoomLevel(3.0)
                        setMaxZoomLevel(20.0)
                        setBackgroundColor(AndroidColor.rgb(232, 235, 232))
                        // Start at a neutral India overview. Never label an old saved
                        // wardriving fix or the hard-coded fallback as the live location.
                        controller.setZoom(5.0)
                        controller.setCenter(GeoPoint(20.5937, 78.9629))
                        cameraCentered = false
                        followLocation = true
                        userMarker = null

                        // A deliberate map drag pauses follow mode. The target button
                        // below re-enables it and returns to street-level GPS zoom.
                        val touchSlop = ViewConfiguration.get(ctx).scaledTouchSlop
                        var gestureStartX = 0f
                        var gestureStartY = 0f
                        var followPausedForGesture = false
                        setOnTouchListener { _, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    gestureStartX = event.x
                                    gestureStartY = event.y
                                    followPausedForGesture = false
                                }
                                // Pinch-to-zoom must not pause GPS following.
                                MotionEvent.ACTION_POINTER_DOWN -> Unit
                                MotionEvent.ACTION_MOVE -> {
                                    val dx = event.x - gestureStartX
                                    val dy = event.y - gestureStartY
                                    if (event.pointerCount == 1 && !followPausedForGesture &&
                                        dx * dx + dy * dy > touchSlop * touchSlop
                                    ) {
                                        followLocation = false
                                        followPausedForGesture = true
                                    }
                                }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                    followPausedForGesture = false
                                }
                            }
                            false
                        }

                        val line = Polyline().apply {
                            outlinePaint.color = AndroidColor.parseColor("#FFFF3158")
                            outlinePaint.strokeWidth = 9f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND
                            outlinePaint.isAntiAlias = true
                        }
                        overlays.add(line)
                        routeLine = line

                        // Count actual tile callbacks so the HUD can distinguish
                        // an empty map from a failed network tile request.
                        val handler = Handler(Looper.getMainLooper()) { message ->
                            when (message.what) {
                                MapTileProviderBase.MAPTILE_SUCCESS_ID -> {
                                    tileSuccess += 1
                                    mapStatus = "OPENSTREETMAP · ${tileSuccess} TILES LOADED"
                                }
                                MapTileProviderBase.MAPTILE_FAIL_ID -> {
                                    tileFailures += 1
                                    if (tileSuccess == 0) mapStatus = "TILE REQUESTS FAILING · ${tileFailures}"
                                }
                            }
                            // This callback is inside MapView.apply; use its receiver directly.
                            this@apply.invalidate()
                            true
                        }
                        tileProvider.getTileRequestCompleteHandlers().add(handler)
                        tileHandler = handler
                        mapView = this
                        mapStatus = "OPENSTREETMAP · REQUESTING TILES"
                    }
                    map
                } catch (error: Exception) {
                    mapError = "${error.javaClass.simpleName}: ${error.message ?: "unknown error"}"
                    mapStatus = "MAP INITIALIZATION FAILED"
                    Log.e(MAP_TAG, "MapView initialization failed", error)
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
                    map.visibility = if (visible) View.VISIBLE else View.INVISIBLE
                    // AndroidView.update is the synchronization point between Compose GPS
                    // state and the native osmdroid View. Do not rely only on the location
                    // callback: the first fix can arrive before the MapView is created.
                    // Synchronize the route in AndroidView.update as well as the
                    // StateFlow collector. This is the native-view handoff point after
                    // every Compose update, preventing a stale Polyline after tab changes.
                    syncRouteOverlay(map, wardrive.fixes)

                    if (hasFix && latitude.isFinite() && longitude.isFinite() &&
                        latitude in -90.0..90.0 && longitude in -180.0..180.0
                    ) {
                        val point = GeoPoint(latitude, longitude)
                        val marker = userMarker ?: Marker(map).apply {
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "NHSpectrum · Live GPS location"
                        }.also { created ->
                            map.overlays.add(created)
                            userMarker = created
                        }
                        marker.position = point
                        marker.snippet = "Live GPS · accuracy ±${accuracy.toInt()} m"
                        if (!cameraCentered) {
                            map.controller.setZoom(17.0)
                            map.controller.setCenter(point)
                            cameraCentered = true
                        } else if (followLocation) {
                            map.controller.setCenter(point)
                        }
                    }
                    map.invalidate()
                    map.postInvalidateOnAnimation()
                }
            }
        )

        if (visible) {
        Column(
            Modifier.align(Alignment.TopStart).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            MapHud(gpsStatus, highlight = hasFix && accuracy <= 25f)
            if (hasFix) {
                MapHud(String.format("%.6f, %.6f", latitude, longitude))
                MapHud("GPS ±${accuracy.toInt()} m · age ${fixAgeSeconds}s")
                MapHud("SPEED ${"%.1f".format(speedKmh)} km/h · HEADING ${if (bearingDegrees.isFinite()) compassDirection(bearingDegrees) else "—"}")
            }
            MapHud(if (followLocation) "FOLLOW MODE · ON" else "FOLLOW MODE · PAUSED")
        }

        Column(
            Modifier.align(Alignment.TopEnd).padding(10.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MapHud(mapError?.let { "MAP ERROR · $it" } ?: mapStatus)
            if (tileFailures > 0) MapHud("TILE FAILURES · $tileFailures")
        }

        // Zoom is handled by osmdroid's native pinch, double-tap and pan gestures.
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 270.dp)) {
            MapControl(if (followLocation) "◎" else "⌖") {
                if (hasFix) {
                    followLocation = true
                    mapView?.let { map ->
                        map.controller.animateTo(GeoPoint(latitude, longitude))
                        cameraCentered = true
                        map.invalidate()
                    }
                } else {
                    Toast.makeText(context, "Waiting for GPS fix", Toast.LENGTH_SHORT).show()
                }
            }
        }

        Text(
            "© OpenStreetMap contributors",
            color = Color(0xFFE4E9EA), fontSize = 9.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 248.dp)
                .background(Color(0xCC101B1F), RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 4.dp)
        )

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(
                Panel.copy(alpha = 0.97f), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
            ).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "${"%.2f".format(wardrive.distanceMeters / 1000.0)} km  ·  ${wardrive.fixes.size} fixes  ·  ${wardrive.observations.size} observations  ·  ${formatElapsed(elapsedRecordingSeconds)}",
                color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MapHud("Wi-Fi ${wardrive.observations.count { it.type.equals("wifi", true) }}")
                MapHud("BLE ${wardrive.observations.count { it.type.equals("ble", true) }}")
                MapHud("Cell ${wardrive.observations.count { it.type.equals("cell", true) }}")
                MapHud("Drone ${snapshot.drones.size}")
            }
            Box(
                Modifier.fillMaxWidth().background(if (wardrive.isRecording) Panel else Accent, RoundedCornerShape(10.dp))
                    .border(1.dp, Accent, RoundedCornerShape(10.dp))
                    .clickable {
                        if (wardrive.isRecording) {
                            viewModel.stopWardriving()
                        } else if (hasFix && accuracy <= 25f) {
                            followLocation = true
                            mapView?.let { map ->
                                map.controller.animateTo(GeoPoint(latitude, longitude))
                                cameraCentered = true
                            }
                            if (!viewModel.startWardriving(
                                latitude, longitude, accuracy,
                                lastFixTimestampMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
                            )) {
                                Toast.makeText(context, "Precise location permission is required to start wardriving", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, "Need GPS accuracy of 25 m or better to start route capture", Toast.LENGTH_SHORT).show()
                        }
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
}

private fun compassDirection(degrees: Float): String {
    val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val normalized = (degrees % 360f + 360f) % 360f
    return directions[((normalized + 22.5f) / 45f).toInt() % 8]
}

private fun formatElapsed(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

private fun hasValidatedInternet(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

@Composable
private fun MapHud(text: String, highlight: Boolean = false) {
    Text(
        text, color = if (highlight) Accent else TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(Panel.copy(alpha = 0.96f), RoundedCornerShape(7.dp))
            .border(1.dp, if (highlight) Accent else Border, RoundedCornerShape(7.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp)
    )
}

@Composable
private fun MapControl(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp)
            .background(Panel.copy(alpha = 0.96f), RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
