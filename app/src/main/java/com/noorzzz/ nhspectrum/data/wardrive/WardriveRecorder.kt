package com.noorzzz.nhspectrum.data.wardrive

import android.content.Context
import com.noorzzz.nhspectrum.data.models.ScanSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

data class WardriveFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestamp: Long
)

data class WardriveObservation(
    val type: String,
    val id: String,
    val label: String,
    val signalDbm: Int,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestamp: Long,
    val metadata: Map<String, String>
)

data class WardriveSessionState(
    val isRecording: Boolean = false,
    val fixes: List<WardriveFix> = emptyList(),
    val observations: List<WardriveObservation> = emptyList(),
    val distanceMeters: Double = 0.0
)

class WardriveRecorder(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nhspectrum_wardrive", Context.MODE_PRIVATE)
    private val lock = Any()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<WardriveSessionState> = _state.asStateFlow()
    private var currentFix: WardriveFix? = null
    private data class RecordGate(val recordedAt: Long, val signal: Int, val latitude: Double, val longitude: Double)
    private val lastRecordedByDevice = HashMap<String, RecordGate>()

    fun start() = synchronized(lock) {
        lastRecordedByDevice.clear()
        currentFix = null
        publish(WardriveSessionState(isRecording = true))
    }

    fun stop() = synchronized(lock) {
        publish(_state.value.copy(isRecording = false))
    }

    /** Clears the saved session only while recording is stopped. */
    fun clearSession(): Boolean = synchronized(lock) {
        if (_state.value.isRecording) return@synchronized false
        currentFix = null
        lastRecordedByDevice.clear()
        publish(WardriveSessionState())
        true
    }

    fun updateFix(latitude: Double, longitude: Double, accuracy: Float, timestamp: Long = System.currentTimeMillis()) = synchronized(lock) {
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return
        // Keep raw UI GPS separate from route quality: never attach new observations
        // to a stale, previously accepted fix when the current GPS sample is poor.
        currentFix = null
        if (!accuracy.isFinite() || accuracy <= 0f || accuracy > MAX_ROUTE_ACCURACY_METERS) return
        val fix = WardriveFix(latitude, longitude, accuracy, timestamp)
        currentFix = fix
        if (!_state.value.isRecording) return

        val previous = _state.value.fixes.lastOrNull()
        if (previous == null) {
            publish(_state.value.copy(fixes = listOf(fix)))
            return
        }

        val elapsedMs = timestamp - previous.timestamp
        if (elapsedMs <= 0L) return
        val rawDistance = haversineMeters(previous.latitude, previous.longitude, latitude, longitude)
        val elapsedSeconds = elapsedMs / 1000.0
        // Reject physically implausible jumps while allowing normal road travel.
        val allowedJump = MAX_ROUTE_SPEED_MPS * elapsedSeconds +
            previous.accuracyMeters.toDouble() + accuracy.toDouble()
        if (rawDistance > allowedJump) return

        // Require movement beyond the GPS uncertainty floor to suppress stationary jitter.
        val movementThreshold = maxOf(
            MIN_ROUTE_STEP_METERS,
            maxOf(previous.accuracyMeters, accuracy).toDouble() * ROUTE_ACCURACY_FACTOR
        )
        val timeThresholdReached = elapsedMs >= MAX_ROUTE_SAMPLE_GAP_MS
        if (rawDistance >= movementThreshold || timeThresholdReached) {
            val distanceContribution = if (rawDistance >= movementThreshold) rawDistance else 0.0
            publish(_state.value.copy(
                fixes = (_state.value.fixes + fix).takeLast(MAX_FIXES),
                distanceMeters = _state.value.distanceMeters + distanceContribution
            ))
        }
    }

    fun recordSnapshot(snapshot: ScanSnapshot) = synchronized(lock) {
        val state = _state.value
        val fix = currentFix ?: return
        if (!state.isRecording) return
        val accuracy = fix.accuracyMeters
        val additions = mutableListOf<WardriveObservation>()
        val now = System.currentTimeMillis()
        snapshot.wifi.forEach { s ->
            val key = "WIFI:" + s.id
            if (shouldRecord(key, s.rssi, now, fix)) additions += WardriveObservation(
                "wifi", s.id, s.ssid.ifBlank { "Hidden network" }, s.rssi,
                fix.latitude, fix.longitude, accuracy, s.lastSeen,
                mapOf("bssid" to s.bssid, "frequency_mhz" to s.frequency.toString(),
                    "channel" to s.channel.toString(), "band" to s.band, "security" to s.security,
                    "channel_width_mhz" to (s.channelWidthMhz?.toString() ?: ""))
            )
        }
        snapshot.ble.forEach { s ->
            val key = "BLE:" + s.id
            if (shouldRecord(key, s.rssi, now, fix)) additions += WardriveObservation(
                "ble", s.id, s.name.ifBlank { "Unnamed BLE device" }, s.rssi,
                fix.latitude, fix.longitude, accuracy, s.lastSeen,
                mapOf("address" to s.address, "tx_power" to (s.txPower?.toString() ?: ""),
                    "services" to s.serviceUuids.joinToString(","))
            )
        }
        snapshot.cell.forEach { s ->
            val key = "CELL:" + s.id + ":" + s.pci + ":" + s.channelNumber
            val cellSignal = s.rsrp.takeIf { it != Int.MIN_VALUE } ?: s.rsrq
            if (shouldRecord(key, cellSignal, now, fix)) additions += WardriveObservation(
                "cell", s.id, "${s.type} ${s.operator}".trim(), s.rsrp,
                fix.latitude, fix.longitude, accuracy, s.lastSeen,
                mapOf("cid" to s.cid.toString(), "tac" to s.tac.toString(), "pci" to s.pci.toString(),
                    "band" to s.band, "channel" to s.channelNumber.toString(), "rsrq_db" to s.rsrq.toString(),
                    "registered" to s.registered.toString())
            )
        }
        snapshot.drones.forEach { s ->
            val key = "DRONE:" + s.id
            if (shouldRecord(key, s.rssi, now, fix)) additions += WardriveObservation(
                "drone", s.id, s.model, s.rssi,
                fix.latitude, fix.longitude, accuracy, s.lastSeen,
                mapOf("serial" to s.serial, "altitude_m" to (s.altitude?.toString() ?: ""),
                    "aircraft_latitude" to (s.latitude?.toString() ?: ""), "aircraft_longitude" to (s.longitude?.toString() ?: ""),
                    "operator_latitude" to (s.operatorLatitude?.toString() ?: ""), "operator_longitude" to (s.operatorLongitude?.toString() ?: ""))
            )
        }
        if (additions.isNotEmpty()) {
            publish(state.copy(observations = (state.observations + additions).takeLast(MAX_OBSERVATIONS)))
        }
    }

    fun exportGeoJson(): String = synchronized(lock) {
        val current = _state.value
        val features = JSONArray()
        if (current.fixes.isNotEmpty()) {
            val coordinates = JSONArray()
            current.fixes.forEach { coordinates.put(JSONArray().put(it.longitude).put(it.latitude)) }
            val route = JSONObject()
                .put("type", "Feature")
                .put("geometry", JSONObject().put("type", "LineString").put("coordinates", coordinates))
                .put("properties", JSONObject().put("kind", "route").put("distance_m", current.distanceMeters))
            features.put(route)
        }
        current.observations.forEach { observation ->
            val properties = JSONObject()
                .put("kind", observation.type)
                .put("id", observation.id)
                .put("label", observation.label)
                .put("signal_dbm", observation.signalDbm)
                .put("accuracy_m", observation.accuracyMeters)
                .put("timestamp_ms", observation.timestamp)
            observation.metadata.forEach { (key, value) -> properties.put(key, value) }
            features.put(JSONObject()
                .put("type", "Feature")
                .put("geometry", JSONObject().put("type", "Point")
                    .put("coordinates", JSONArray().put(observation.longitude).put(observation.latitude)))
                .put("properties", properties))
        }
        JSONObject().put("type", "FeatureCollection").put("features", features).toString(2)
    }

    private fun shouldRecord(key: String, signal: Int, now: Long, fix: WardriveFix): Boolean {
        val previous = lastRecordedByDevice[key]
        val shouldRecord = previous == null ||
            now - previous.recordedAt >= MIN_OBSERVATION_INTERVAL_MS ||
            abs(signal.toLong() - previous.signal.toLong()) >= MIN_SIGNAL_CHANGE_DB ||
            haversineMeters(previous.latitude, previous.longitude, fix.latitude, fix.longitude) >= MIN_LOCATION_CHANGE_METERS
        if (shouldRecord) lastRecordedByDevice[key] = RecordGate(now, signal, fix.latitude, fix.longitude)
        return shouldRecord
    }
    private fun publish(value: WardriveSessionState) {
        _state.value = value
        persist(value)
    }

    private fun persist(value: WardriveSessionState) {
        val root = JSONObject().put("recording", value.isRecording).put("distance_m", value.distanceMeters)
        val fixes = JSONArray()
        value.fixes.forEach { fixes.put(JSONObject().put("lat", it.latitude).put("lon", it.longitude)
            .put("accuracy", it.accuracyMeters).put("time", it.timestamp)) }
        val observations = JSONArray()
        value.observations.forEach { o ->
            val metadata = JSONObject()
            o.metadata.forEach { (k, v) -> metadata.put(k, v) }
            observations.put(JSONObject().put("type", o.type).put("id", o.id).put("label", o.label)
                .put("signal", o.signalDbm).put("lat", o.latitude).put("lon", o.longitude)
                .put("accuracy", o.accuracyMeters).put("time", o.timestamp).put("metadata", metadata))
        }
        root.put("fixes", fixes).put("observations", observations)
        prefs.edit().putString(KEY_SESSION, root.toString()).apply()
    }

    private fun load(): WardriveSessionState {
        return try {
            val root = JSONObject(prefs.getString(KEY_SESSION, null) ?: return WardriveSessionState())
            val fixesJson = root.optJSONArray("fixes") ?: JSONArray()
            val observationsJson = root.optJSONArray("observations") ?: JSONArray()
            // Discard corrupt or out-of-range persisted coordinates before they reach MapView.
            val fixes = (0 until fixesJson.length()).mapNotNull { i ->
                val x = fixesJson.optJSONObject(i) ?: return@mapNotNull null
                val lat = x.optDouble("lat", Double.NaN)
                val lon = x.optDouble("lon", Double.NaN)
                val accuracy = x.optDouble("accuracy", Double.NaN).toFloat()
                if (!lat.isFinite() || !lon.isFinite() ||
                    lat !in -90.0..90.0 || lon !in -180.0..180.0 ||
                    !accuracy.isFinite() || accuracy <= 0f
                ) return@mapNotNull null
                WardriveFix(lat, lon, accuracy, x.optLong("time"))
            }.takeLast(MAX_FIXES)
            val observations = (0 until observationsJson.length()).mapNotNull { i ->
                val x = observationsJson.optJSONObject(i) ?: return@mapNotNull null
                val lat = x.optDouble("lat", Double.NaN)
                val lon = x.optDouble("lon", Double.NaN)
                val accuracy = x.optDouble("accuracy", Double.NaN).toFloat()
                if (!lat.isFinite() || !lon.isFinite() ||
                    lat !in -90.0..90.0 || lon !in -180.0..180.0 ||
                    !accuracy.isFinite() || accuracy <= 0f
                ) return@mapNotNull null
                val meta = x.optJSONObject("metadata") ?: JSONObject()
                val map = buildMap { meta.keys().forEach { key -> put(key, meta.optString(key)) } }
                WardriveObservation(x.optString("type"), x.optString("id"), x.optString("label"),
                    x.optInt("signal"), lat, lon, accuracy, x.optLong("time"), map)
            }.takeLast(MAX_OBSERVATIONS)
            val distance = root.optDouble("distance_m", 0.0).takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
            WardriveSessionState(false, fixes, observations, distance)
        } catch (_: Exception) { WardriveSessionState() }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earth = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        return earth * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    companion object {
        private const val KEY_SESSION = "last_session_geojson_v1"
        private const val MAX_FIXES = 20000
        private const val MAX_OBSERVATIONS = 10000
        private const val MAX_ROUTE_ACCURACY_METERS = 25f
        private const val MIN_ROUTE_STEP_METERS = 2.5
        private const val ROUTE_ACCURACY_FACTOR = 0.35
        private const val MAX_ROUTE_SPEED_MPS = 55.0
        private const val MAX_ROUTE_SAMPLE_GAP_MS = 5_000L
        private const val MIN_OBSERVATION_INTERVAL_MS = 10_000L
        private const val MIN_SIGNAL_CHANGE_DB = 6L
        private const val MIN_LOCATION_CHANGE_METERS = 10.0
    }
}
