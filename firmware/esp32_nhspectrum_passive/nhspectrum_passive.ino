/*
 * HUNTWARE / NHSpectrum ESP32-S3 companion firmware
 * Protocol v1 · 115200 baud · receive-only Wi-Fi observation
 *
 * Observes 2.4 GHz beacon/probe-response metadata and deauth/disassociation
 * management-event metadata on one fixed channel. It does not transmit,
 * inject, deauthenticate, jam, capture credentials or perform attacks.
 */
#include <Arduino.h>
#include <WiFi.h>
#include <esp_wifi.h>
#include <esp_timer.h>

static constexpr uint32_t BAUD = 115200;
static constexpr size_t MAX_LINE = 128;
static constexpr uint8_t MONITOR_CHANNEL = 1;
static constexpr uint8_t MAX_AP_CACHE = 64;
static constexpr uint32_t AP_REPORT_INTERVAL_MS = 10000;

static char inputLine[MAX_LINE];
static size_t inputLength = 0;
static uint32_t sequenceNumber = 0;
static bool monitorEnabled = false;
static uint32_t observedDeauth = 0;
static uint32_t observedDisassoc = 0;
static uint32_t observedAps = 0;
static uint32_t droppedEvents = 0;
static uint32_t lastHeartbeat = 0;

struct FrameEvent {
  char subtype[12];
  char transmitter[18];
  char receiver[18];
  char bssid[18];
  char ssid[33];
  uint16_t reason;
  int8_t rssi;
  uint8_t channel;
  uint64_t uptimeMs;
  bool isAccessPoint;
};

struct ApCacheEntry {
  char bssid[18];
  uint32_t lastReportedMs;
};

static QueueHandle_t frameQueue = nullptr;
static ApCacheEntry apCache[MAX_AP_CACHE] = {};

static void macToString(const uint8_t *mac, char *out, size_t size) {
  snprintf(out, size, "%02X:%02X:%02X:%02X:%02X:%02X",
           mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
}

static String jsonSafe(const char *input) {
  String out;
  while (*input) {
    const uint8_t c = (uint8_t)*input++;
    if (c == '"' || c == '\\') { out += '\\'; out += (char)c; }
    else if (c >= 0x20 && c < 0x7F) out += (char)c;
  }
  return out;
}

static void emitLine(const String &json) { Serial.println(json); }

static bool shouldReportAp(const char *bssid, uint32_t now) {
  int freeSlot = -1;
  for (uint8_t i = 0; i < MAX_AP_CACHE; i++) {
    if (apCache[i].bssid[0] == '\0') { if (freeSlot < 0) freeSlot = i; continue; }
    if (strcmp(apCache[i].bssid, bssid) == 0) {
      if (now - apCache[i].lastReportedMs < AP_REPORT_INTERVAL_MS) return false;
      apCache[i].lastReportedMs = now;
      return true;
    }
  }
  const int slot = freeSlot >= 0 ? freeSlot : (now / AP_REPORT_INTERVAL_MS) % MAX_AP_CACHE;
  strlcpy(apCache[slot].bssid, bssid, sizeof(apCache[slot].bssid));
  apCache[slot].lastReportedMs = now;
  return true;
}

static void emitAccessPoint(const FrameEvent &e) {
  String line = "{\"v\":1,\"type\":\"wifi_ap_observation\",\"seq\":";
  line += String(++sequenceNumber);
  line += ",\"uptime_ms\":" + String((uint32_t)e.uptimeMs);
  line += ",\"ssid\":\"" + jsonSafe(e.ssid) + "\"";
  line += ",\"bssid\":\"" + String(e.bssid) + "\"";
  line += ",\"rssi_dbm\":" + String(e.rssi);
  line += ",\"channel\":" + String(e.channel) + "}";
  emitLine(line);
}

static void emitManagementEvent(const FrameEvent &e) {
  String line = "{\"v\":1,\"type\":\"wifi_management_event\",\"seq\":";
  line += String(++sequenceNumber);
  line += ",\"uptime_ms\":" + String((uint32_t)e.uptimeMs);
  line += ",\"subtype\":\"" + String(e.subtype) + "\"";
  line += ",\"transmitter\":\"" + String(e.transmitter) + "\"";
  line += ",\"receiver\":\"" + String(e.receiver) + "\"";
  line += ",\"bssid\":\"" + String(e.bssid) + "\"";
  line += ",\"reason_code\":" + String(e.reason);
  line += ",\"rssi_dbm\":" + String(e.rssi);
  line += ",\"channel\":" + String(e.channel) + "}";
  emitLine(line);
}

static void IRAM_ATTR promiscuousCallback(void *buffer, wifi_promiscuous_pkt_type_t type) {
  if (type != WIFI_PKT_MGMT || frameQueue == nullptr || buffer == nullptr) return;
  const auto *packet = static_cast<const wifi_promiscuous_pkt_t *>(buffer);
  const uint16_t length = packet->rx_ctrl.sig_len;
  if (length < 26) return;

  const uint8_t *frame = packet->payload;
  const uint16_t fc = (uint16_t)frame[0] | ((uint16_t)frame[1] << 8);
  if (((fc >> 2) & 0x03) != 0) return;
  const uint8_t subtype = (fc >> 4) & 0x0F;
  const bool isApFrame = subtype == 8 || subtype == 5; // beacon / probe response
  const bool isManagementEvent = subtype == 10 || subtype == 12; // disassoc / deauth
  if (!isApFrame && !isManagementEvent) return;
  if (isApFrame && length < 36) return;
  if (isManagementEvent && length < 26) return;

  FrameEvent event{};
  event.isAccessPoint = isApFrame;
  if (isApFrame) {
    strlcpy(event.subtype, subtype == 8 ? "BEACON" : "PROBE_RESPONSE", sizeof(event.subtype));
  } else {
    strlcpy(event.subtype, subtype == 12 ? "DEAUTH" : "DISASSOC", sizeof(event.subtype));
  }
  macToString(frame + 10, event.transmitter, sizeof(event.transmitter));
  macToString(frame + 4, event.receiver, sizeof(event.receiver));
  macToString(frame + 16, event.bssid, sizeof(event.bssid));
  event.reason = isManagementEvent ? ((uint16_t)frame[24] | ((uint16_t)frame[25] << 8)) : 0;
  event.rssi = packet->rx_ctrl.rssi;
  event.channel = packet->rx_ctrl.channel;
  event.uptimeMs = (uint64_t)(esp_timer_get_time() / 1000ULL);

  if (isApFrame) {
    // Beacon/probe response fixed fields occupy 12 bytes after the 24-byte header.
    uint16_t offset = 36;
    while (offset + 2 <= length) {
      const uint8_t id = frame[offset];
      const uint8_t size = frame[offset + 1];
      offset += 2;
      if (offset + size > length) break;
      if (id == 0) {
        const uint8_t copyLength = size > 32 ? 32 : size;
        for (uint8_t i = 0; i < copyLength; i++) {
          const uint8_t c = frame[offset + i];
          event.ssid[i] = (c >= 0x20 && c < 0x7F) ? (char)c : '_';
        }
        event.ssid[copyLength] = '\0';
        break;
      }
      offset += size;
    }
  }

  // ESP-IDF promiscuous callbacks run in the Wi-Fi task, not an ISR.
  if (xQueueSend(frameQueue, &event, 0) != pdTRUE) droppedEvents++;
}

static void sendHello() {
  emitLine("{\"v\":1,\"type\":\"hello_ack\",\"seq\":" + String(++sequenceNumber) +
    ",\"uptime_ms\":" + String((uint32_t)(esp_timer_get_time() / 1000ULL)) +
    ",\"firmware\":\"huntware-esp32-passive-1.1.0\",\"chip\":\"ESP32-S3\","
    "\"capabilities\":[\"wifi.ap.observe\",\"wifi.management.deauth.observe\","
    "\"wifi.management.disassoc.observe\",\"wifi.channel.fixed\",\"self_test\"]}");
}

static void sendStatus() {
  emitLine("{\"v\":1,\"type\":\"status\",\"seq\":" + String(++sequenceNumber) +
    ",\"uptime_ms\":" + String((uint32_t)(esp_timer_get_time() / 1000ULL)) +
    ",\"wifi_channel\":" + String(MONITOR_CHANNEL) +
    ",\"wifi_scan_state\":\"" + String(monitorEnabled ? "MONITOR" : "STOPPED") + "\""
    ",\"aps_seen\":" + String(observedAps) +
    ",\"deauth_events\":" + String(observedDeauth) +
    ",\"disassoc_events\":" + String(observedDisassoc) +
    ",\"dropped_events\":" + String(droppedEvents) +
    ",\"free_heap_bytes\":" + String(ESP.getFreeHeap()) + "}");
}

static bool startPassiveMonitor() {
  if (monitorEnabled) return true;
  WiFi.mode(WIFI_MODE_NULL);
  esp_wifi_init_config_t config = WIFI_INIT_CONFIG_DEFAULT();
  esp_err_t err = esp_wifi_init(&config);
  if (err != ESP_OK && err != ESP_ERR_WIFI_INIT_STATE) return false;
  err = esp_wifi_set_mode(WIFI_MODE_NULL);
  if (err != ESP_OK) return false;
  err = esp_wifi_start();
  if (err != ESP_OK && err != ESP_ERR_WIFI_NOT_STOPPED) return false;
  if (esp_wifi_set_channel(MONITOR_CHANNEL, WIFI_SECOND_CHAN_NONE) != ESP_OK) return false;

  wifi_promiscuous_filter_t filter{};
  filter.filter_mask = WIFI_PROMIS_FILTER_MASK_MGMT;
  if (esp_wifi_set_promiscuous_filter(&filter) != ESP_OK) return false;
  if (esp_wifi_set_promiscuous_rx_cb(&promiscuousCallback) != ESP_OK) return false;
  if (esp_wifi_set_promiscuous(true) != ESP_OK) return false;
  monitorEnabled = true;
  return true;
}

static void stopPassiveMonitor() {
  esp_wifi_set_promiscuous(false);
  monitorEnabled = false;
}

static void sendSelfTest() {
  wifi_mode_t mode = WIFI_MODE_NULL;
  const bool radioReady = esp_wifi_get_mode(&mode) == ESP_OK;
  const bool queueReady = frameQueue != nullptr;
  const bool ok = radioReady && queueReady && ESP.getFreeHeap() > 12000;
  emitLine("{\"v\":1,\"type\":\"self_test\",\"seq\":" + String(++sequenceNumber) +
    ",\"ok\":" + String(ok ? "true" : "false") +
    ",\"wifi\":\"" + String(radioReady ? "READY" : "NOT_READY") + "\""
    ",\"queue\":\"" + String(queueReady ? "READY" : "NOT_READY") + "\""
    ",\"monitor\":\"" + String(monitorEnabled ? "RUNNING" : "STOPPED") + "\""
    ",\"free_heap_bytes\":" + String(ESP.getFreeHeap()) + "}");
}

static void sendHelp() {
  emitLine("{\"v\":1,\"type\":\"help\",\"commands\":[\"hello\",\"status\",\"self_test\",\"scan_start_passive\",\"scan_stop\"]}");
}

static void handleCommand(const char *line) {
  if (strcmp(line, "hello") == 0) sendHello();
  else if (strcmp(line, "help") == 0) sendHelp();
  else if (strcmp(line, "status") == 0) sendStatus();
  else if (strcmp(line, "self_test") == 0) sendSelfTest();
  else if (strcmp(line, "scan_start_passive") == 0 || strcmp(line, "scanap") == 0) {
    const bool ok = startPassiveMonitor();
    emitLine("{\"v\":1,\"type\":\"ack\",\"command\":\"scan_start_passive\",\"ok\":" + String(ok ? "true" : "false") + "}");
  } else if (strcmp(line, "scan_stop") == 0 || strcmp(line, "stopscan") == 0) {
    stopPassiveMonitor();
    emitLine("{\"v\":1,\"type\":\"ack\",\"command\":\"scan_stop\",\"ok\":true}");
  } else {
    emitLine("{\"v\":1,\"type\":\"error\",\"error\":\"unsupported_command\",\"hint\":\"send help\"}");
  }
}

void setup() {
  Serial.begin(BAUD);
  delay(300);
  frameQueue = xQueueCreate(48, sizeof(FrameEvent));
  if (frameQueue == nullptr) {
    emitLine("{\"v\":1,\"type\":\"fatal\",\"error\":\"frame_queue_allocation_failed\"}");
    return;
  }
  const bool started = startPassiveMonitor();
  sendHello();
  if (!started) emitLine("{\"v\":1,\"type\":\"warning\",\"message\":\"passive_monitor_start_failed\"}");
  sendSelfTest();
}

void loop() {
  while (Serial.available() > 0) {
    const char c = (char)Serial.read();
    if (c == '\n' || c == '\r') {
      if (inputLength > 0) {
        inputLine[inputLength] = '\0';
        handleCommand(inputLine);
        inputLength = 0;
      }
    } else if (inputLength < MAX_LINE - 1) {
      inputLine[inputLength++] = c;
    } else {
      inputLength = 0;
      emitLine("{\"v\":1,\"type\":\"error\",\"error\":\"command_line_too_long\"}");
    }
  }

  FrameEvent event{};
  while (frameQueue != nullptr && xQueueReceive(frameQueue, &event, 0) == pdTRUE) {
    if (event.isAccessPoint) {
      if (shouldReportAp(event.bssid, millis())) {
        observedAps++;
        emitAccessPoint(event);
      }
    } else {
      if (strcmp(event.subtype, "DEAUTH") == 0) observedDeauth++;
      else observedDisassoc++;
      emitManagementEvent(event);
    }
  }

  const uint32_t now = millis();
  if (now - lastHeartbeat >= 5000) {
    lastHeartbeat = now;
    sendStatus();
  }
  delay(2);
}
