/*
 * NHSpectrum passive ESP32-S3 companion — protocol v1
 *
 * Arduino-ESP32 core 3.x
 * Passive 2.4 GHz 802.11 management-frame observation.
 * BLE advertisement capture is intentionally reported as unsupported in this
 * minimal reference build until a pinned BLE stack profile is selected.
 *
 * This firmware never transmits crafted 802.11 frames, deauth frames, probes,
 * credentials or jamming signals. It does not implement injection.
 */
#include <Arduino.h>
#include <WiFi.h>
#include <esp_wifi.h>
#include <esp_timer.h>

static constexpr uint32_t BAUD = 115200;
static constexpr size_t MAX_LINE = 256;
static constexpr uint8_t MONITOR_CHANNEL = 1;

static char inputLine[MAX_LINE];
static size_t inputLength = 0;
static uint32_t sequenceNumber = 0;
static bool monitorEnabled = true;
static uint32_t observedDeauth = 0;
static uint32_t observedDisassoc = 0;
static uint32_t droppedEvents = 0;
static uint32_t lastHeartbeat = 0;

struct FrameEvent {
  char subtype[12];
  char transmitter[18];
  char receiver[18];
  char bssid[18];
  uint16_t reason;
  int8_t rssi;
  uint8_t channel;
  uint64_t uptimeMs;
};

static QueueHandle_t frameQueue;

static void macToString(const uint8_t *mac, char *out, size_t size) {
  snprintf(out, size, "%02X:%02X:%02X:%02X:%02X:%02X",
           mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
}

static void emitLine(const String &json) {
  Serial.println(json);
}

static void emitFrame(const FrameEvent &e) {
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
  if (type != WIFI_PKT_MGMT || frameQueue == nullptr) return;
  const auto *packet = static_cast<const wifi_promiscuous_pkt_t *>(buffer);
  if (packet == nullptr || packet->rx_ctrl.sig_len < 26) return;

  const uint8_t *frame = packet->payload;
  const uint16_t frameControl = (uint16_t)frame[0] | ((uint16_t)frame[1] << 8);
  const uint8_t frameType = (frameControl >> 2) & 0x03;
  const uint8_t frameSubtype = (frameControl >> 4) & 0x0F;
  if (frameType != 0 || (frameSubtype != 10 && frameSubtype != 12)) return;

  FrameEvent event{};
  strlcpy(event.subtype, frameSubtype == 12 ? "DEAUTH" : "DISASSOC", sizeof(event.subtype));
  macToString(frame + 10, event.transmitter, sizeof(event.transmitter));
  macToString(frame + 4, event.receiver, sizeof(event.receiver));
  macToString(frame + 16, event.bssid, sizeof(event.bssid));
  event.reason = (uint16_t)frame[24] | ((uint16_t)frame[25] << 8);
  event.rssi = packet->rx_ctrl.rssi;
  event.channel = packet->rx_ctrl.channel;
  event.uptimeMs = (uint64_t)(esp_timer_get_time() / 1000ULL);

  BaseType_t higherPriorityTaskWoken = pdFALSE;
  if (xQueueSendFromISR(frameQueue, &event, &higherPriorityTaskWoken) != pdTRUE) {
    droppedEvents++;
  }
  if (higherPriorityTaskWoken == pdTRUE) portYIELD_FROM_ISR();
}

static void sendHello() {
  emitLine("{\"v\":1,\"type\":\"hello_ack\",\"seq\":" + String(++sequenceNumber) +
           ",\"uptime_ms\":" + String((uint32_t)(esp_timer_get_time() / 1000ULL)) +
           ",\"firmware\":\"nhspectrum-passive-1.0.0\",\"chip\":\"ESP32-S3\","
           "\"capabilities\":[\"wifi.management.deauth.observe\","
           "\"wifi.management.disassoc.observe\",\"wifi.channel.fixed\"]}");
}

static void sendStatus() {
  emitLine("{\"v\":1,\"type\":\"status\",\"seq\":" + String(++sequenceNumber) +
           ",\"uptime_ms\":" + String((uint32_t)(esp_timer_get_time() / 1000ULL)) +
           ",\"wifi_channel\":" + String(MONITOR_CHANNEL) +
           ",\"wifi_scan_state\":\"" + String(monitorEnabled ? "MONITOR" : "STOPPED") + "\""
           ",\"ble_scan_state\":\"UNSUPPORTED_IN_THIS_PROFILE\""
           ",\"deauth_events\":" + String(observedDeauth) +
           ",\"disassoc_events\":" + String(observedDisassoc) +
           ",\"dropped_events\":" + String(droppedEvents) +
           ",\"free_heap_bytes\":" + String(ESP.getFreeHeap()) + "}");
}

static void startPassiveMonitor() {
  WiFi.mode(WIFI_MODE_NULL);
  esp_wifi_init_config_t config = WIFI_INIT_CONFIG_DEFAULT();
  esp_err_t err = esp_wifi_init(&config);
  if (err != ESP_OK && err != ESP_ERR_WIFI_INIT_STATE) return;
  esp_wifi_set_mode(WIFI_MODE_NULL);
  esp_wifi_start();
  esp_wifi_set_channel(MONITOR_CHANNEL, WIFI_SECOND_CHAN_NONE);

  wifi_promiscuous_filter_t filter{};
  filter.filter_mask = WIFI_PROMIS_FILTER_MASK_MGMT;
  esp_wifi_set_promiscuous_filter(&filter);
  esp_wifi_set_promiscuous_rx_cb(&promiscuousCallback);
  esp_wifi_set_promiscuous(true);
  monitorEnabled = true;
}

static void stopPassiveMonitor() {
  esp_wifi_set_promiscuous(false);
  monitorEnabled = false;
}

static void handleCommand(const char *line) {
  if (strcmp(line, "hello") == 0) sendHello();
  else if (strcmp(line, "status") == 0) sendStatus();
  else if (strcmp(line, "scan_start_passive") == 0) {
    startPassiveMonitor();
    emitLine("{\"v\":1,\"type\":\"ack\",\"command\":\"scan_start_passive\",\"ok\":true}");
  } else if (strcmp(line, "scan_stop") == 0) {
    stopPassiveMonitor();
    emitLine("{\"v\":1,\"type\":\"ack\",\"command\":\"scan_stop\",\"ok\":true}");
  } else {
    emitLine("{\"v\":1,\"type\":\"error\",\"error\":\"unsupported_command\"}");
  }
}

void setup() {
  Serial.begin(BAUD);
  delay(250);
  frameQueue = xQueueCreate(32, sizeof(FrameEvent));
  if (frameQueue == nullptr) {
    emitLine("{\"v\":1,\"type\":\"fatal\",\"error\":\"frame_queue_allocation_failed\"}");
    return;
  }
  startPassiveMonitor();
  sendHello();
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
    if (strcmp(event.subtype, "DEAUTH") == 0) observedDeauth++;
    else observedDisassoc++;
    emitFrame(event);
  }

  const uint32_t now = millis();
  if (now - lastHeartbeat >= 5000) {
    lastHeartbeat = now;
    sendStatus();
  }
  delay(2);
}
