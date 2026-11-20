package com.noorzzz.nhspectrum.data.maps

import android.content.Context
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Downloads a small, user-initiated OpenFreeMap region around the current GPS fix.
 * Provider policy: <= 3,000 tiles and <= 50 MB per download; the shared HTTP client
 * identifies NHSpectrum and limits requests to four concurrent connections.
 */
class OfflineMapManager(context: Context) {
    private val manager = OfflineManager.getInstance(context.applicationContext)

    fun downloadAround(
        latitude: Double,
        longitude: Double,
        onProgress: (String) -> Unit
    ) {
        if (!latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) {
            onProgress("Offline map failed: invalid GPS coordinates")
            return
        }

        manager.setOfflineMapboxTileCountLimit(MAX_TILES)
        val deltaLat = RADIUS_KM / 111.0
        val deltaLon = RADIUS_KM / (111.0 * kotlin.math.cos(Math.toRadians(latitude)).coerceAtLeast(0.2))
        val bounds = LatLngBounds.Builder()
            .include(LatLng(latitude + deltaLat, longitude + deltaLon))
            .include(LatLng(latitude - deltaLat, longitude - deltaLon))
            .build()
        val definition = OfflineTilePyramidRegionDefinition(
            STYLE_URL, bounds, MIN_ZOOM, MAX_ZOOM, 1.0f
        )
        val metadata = JSONObject()
            .put("name", "NHSpectrum nearby area")
            .put("provider", "OpenFreeMap")
            .put("created_at", System.currentTimeMillis())
            .toString().toByteArray(Charsets.UTF_8)

        onProgress("Preparing offline map area…")
        manager.createOfflineRegion(definition, metadata, object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(region: OfflineRegion) {
                val stoppedForSize = AtomicBoolean(false)
                region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                    override fun onStatusChanged(status: org.maplibre.android.offline.OfflineRegionStatus) {
                        val size = status.completedResourceSize
                        if (size > MAX_BYTES && stoppedForSize.compareAndSet(false, true)) {
                            region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            onProgress("Paused: 50 MB offline-map limit reached. Save a smaller area.")
                            return
                        }
                        val total = status.requiredResourceCount
                        val done = status.completedResourceCount
                        val percent = if (total > 0) (done * 100 / total).coerceIn(0, 100) else 0
                        val mb = size / (1024 * 1024)
                        if (status.isComplete) {
                            region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            onProgress("Offline map ready · $done tiles · $mb MB")
                        } else {
                            onProgress("Downloading offline map · $percent% · $done tiles · $mb MB")
                        }
                    }

                    override fun onError(error: OfflineRegionError) {
                        onProgress("Offline map error: $error")
                    }

                    override fun mapboxTileCountLimitExceeded(limit: Long) {
                        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        onProgress("Paused: tile limit $limit reached. Save a smaller area.")
                    }
                })
                region.setDownloadState(OfflineRegion.STATE_ACTIVE)
            }

            override fun onError(error: String) {
                onProgress("Could not create offline map: $error")
            }
        })
    }

    companion object {
        const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
        private const val RADIUS_KM = 2.0
        private const val MIN_ZOOM = 10.0
        private const val MAX_ZOOM = 16.0
        private const val MAX_TILES = 3000L
        private const val MAX_BYTES = 50L * 1024L * 1024L
    }
}
