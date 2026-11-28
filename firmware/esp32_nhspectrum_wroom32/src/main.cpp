/*
 * HUNTWARE — NHSpectrum ESP32-WROOM-32 companion
 * Target: ESP32 Dev Module / ESP32-WROOM-32, CH340 USB-UART
 * Arduino-ESP32 3.x | 115200 8-N-1 | Protocol v1
 *
 * Receive-only Wi-Fi management observation plus explicitly synthetic
 * lab events for validating the Android alert pipeline. No RF attack TX,
 * deauthentication, injection, jamming, credential capture or handshake capture.
 */
#include <Arduino.h>
#include <WiFi.h>
#include <esp_wifi.h>
#include <esp_timer.h>

static constexpr uint32_t SERIAL_BAUD = 115200;
static constexpr size_t MAX_COMMAND = 96;
static constexpr uint8_t DEFAULT_CHANNEL = 1;
static constexpr uint8_t MAX_AP_CACHE = 48;
static constexpr uint32_t AP_REPEAT_MS = 8000;

static char commandBuffer[MAX_COMMAND];
static size_t commandLength = 0;
static uint32_t sequenceNo = 0;
static uint8_t monitorChannel = DEFAULT_CHANNEL;
static bool wifiInitialized = false;
static bool monitorRunning = false;
static uint32_t apsSeen = 0, deauthSeen = 0, disassocSeen = 0, droppedEvents = 0;
static uint32_t lastHeartbeatMs = 0;

struct RadioEvent {
  uint8_t kind; // 1 AP observation, 2 management observation
  char subtype[16];
  char ssid[33];
  char transmitter[18];
  char receiver[18];
  char bssid[18];
  int8_t rssi;
  uint8_t channel;
  uint16_t reason;
  uint32_t uptimeMs;
};
struct ApCache { char bssid[18]; uint32_t lastMs; };
static QueueHandle_t eventQueue = nullptr;
static ApCache apCache[MAX_AP_CACHE] = {};

static void emit(const String &line) { Serial.println(line); }
static uint32_t uptimeMs() { return (uint32_t)(esp_timer_get_time() / 1000ULL); }

static void macString(const uint8_t *m, char *out, size_t n) {
  snprintf(out, n, "%02X:%02X:%02X:%02X:%02X:%02X", m[0],m[1],m[2],m[3],m[4],m[5]);
}
static String jsonSafe(const char *s) {
  String out;
  while (*s) {
    const uint8_t c = (uint8_t)*s++;
    if (c == '"' || c == '\\') { out += '\\'; out += (char)c; }
    else if (c >= 0x20 && c < 0x7F) out += (char)c;
  }
  return out;
}
static bool shouldReport(const char *bssid, uint32_t now) {
  int freeSlot = -1;
  for (uint8_t i=0; i<MAX_AP_CACHE; ++i) {
    if (!apCache[i].bssid[0]) { if (freeSlot < 0) freeSlot=i; continue; }
    if (!strcmp(apCache[i].bssid,bssid)) {
      if (now - apCache[i].lastMs < AP_REPEAT_MS) return false;
      apCache[i].lastMs=now; return true;
    }
  }
  int slot = freeSlot >= 0 ? freeSlot : (now / AP_REPEAT_MS) % MAX_AP_CACHE;
  strlcpy(apCache[slot].bssid,bssid,sizeof(apCache[slot].bssid));
  apCache[slot].lastMs=now;
  return true;
}

static void IRAM_ATTR onPromiscuous(void *buf, wifi_promiscuous_pkt_type_t type) {
  if (type != WIFI_PKT_MGMT || !buf || !eventQueue) return;
  const auto *pkt = (const wifi_promiscuous_pkt_t*)buf;
  const uint16_t len = pkt->rx_ctrl.sig_len;
  if (len < 26) return;
  const uint8_t *f = pkt->payload;
  const uint16_t fc = (uint16_t)f[0] | ((uint16_t)f[1] << 8);
  if (((fc >> 2) & 3) != 0) return; // management only
  const uint8_t st = (fc >> 4) & 15;
  const bool ap = st == 8 || st == 5; // beacon / probe response
  const bool mgmt = st == 10 || st == 12; // disassoc / deauth
  if (!ap && !mgmt) return;
  if (ap && len < 36) return;

  RadioEvent e{};
  e.kind = ap ? 1 : 2;
  strlcpy(e.subtype, ap ? (st == 8 ? "BEACON" : "PROBE_RESPONSE") : (st == 12 ? "DEAUTH" : "DISASSOC"), sizeof(e.subtype));
  macString(f+10,e.transmitter,sizeof(e.transmitter));
  macString(f+4,e.receiver,sizeof(e.receiver));
  macString(f+16,e.bssid,sizeof(e.bssid));
  e.rssi = pkt->rx_ctrl.rssi;
  e.channel = pkt->rx_ctrl.channel;
  e.reason = mgmt ? ((uint16_t)f[24] | ((uint16_t)f[25] << 8)) : 0;
  e.uptimeMs = uptimeMs();

  if (ap) {
    uint16_t off=36;
    while (off+2<=len) {
      uint8_t id=f[off], sz=f[off+1]; off+=2;
      if (off+sz>len) break;
      if (id==0) {
        uint8_t n=sz>32?32:sz;
        for(uint8_t i=0;i<n;i++) {
          uint8_t ch=f[off+i];
          e.ssid[i]=(ch>=0x20 && ch<0x7F)?(char)ch:'_';
        }
        e.ssid[n]='\0'; break;
      }
      off+=sz;
    }
  }
  if (xQueueSend(eventQueue,&e,0)!=pdTRUE) droppedEvents++;
}

static void sendHello() {
  emit("{\"v\":1,\"type\":\"hello_ack\",\"seq\":"+String(++sequenceNo)+
       ",\"uptime_ms\":"+String(uptimeMs())+
       ",\"firmware\":\"huntware-wroom32-1.0.0\",\"chip\":\"ESP32-WROOM-32\","
       "\"usb_bridge\":\"CH340-or-compatible-UART\","
       "\"capabilities\":[\"wifi.ap.observe\",\"wifi.management.deauth.observe\","
       "\"wifi.management.disassoc.observe\",\"wifi.channel.fixed\",\"lab.simulation\",\"self_test\"]}");
}
static void sendStatus() {
  emit("{\"v\":1,\"type\":\"status\",\"seq\":"+String(++sequenceNo)+
       ",\"uptime_ms\":"+String(uptimeMs())+
       ",\"wifi_channel\":"+String(monitorChannel)+
       ",\"wifi_scan_state\":\""+String(monitorRunning?"MONITOR":"STOPPED")+"\""+
       ",\"aps_seen\":"+String(apsSeen)+",\"deauth_events\":"+String(deauthSeen)+
       ",\"disassoc_events\":"+String(disassocSeen)+",\"dropped_events\":"+String(droppedEvents)+
       ",\"free_heap_bytes\":"+String(ESP.getFreeHeap())+"}");
}
static bool initWifi() {
  if (wifiInitialized) return true;
  WiFi.mode(WIFI_MODE_NULL);
  esp_wifi_init_config_t cfg = WIFI_INIT_CONFIG_DEFAULT();
  esp_err_t e=esp_wifi_init(&cfg);
  if (e!=ESP_OK && e!=ESP_ERR_WIFI_INIT_STATE) return false;
  e=esp_wifi_set_mode(WIFI_MODE_NULL);
  if (e!=ESP_OK) return false;
  e=esp_wifi_start();
  if (e!=ESP_OK && e!=ESP_ERR_WIFI_NOT_STOPPED) return false;
  wifiInitialized=true;
  return true;
}
static bool startMonitor() {
  if (!initWifi()) return false;
  if (esp_wifi_set_channel(monitorChannel,WIFI_SECOND_CHAN_NONE)!=ESP_OK) return false;
  wifi_promiscuous_filter_t filter{};
  filter.filter_mask=WIFI_PROMIS_FILTER_MASK_MGMT;
  if (esp_wifi_set_promiscuous_filter(&filter)!=ESP_OK) return false;
  if (esp_wifi_set_promiscuous_rx_cb(&onPromiscuous)!=ESP_OK) return false;
  if (esp_wifi_set_promiscuous(true)!=ESP_OK) return false;
  monitorRunning=true; return true;
}
static void stopMonitor() {
  if (wifiInitialized) esp_wifi_set_promiscuous(false);
  monitorRunning=false;
}
static void sendSelfTest() {
  wifi_mode_t mode=WIFI_MODE_NULL;
  bool radio=wifiInitialized && esp_wifi_get_mode(&mode)==ESP_OK;
  bool queue=eventQueue!=nullptr;
  bool heap=ESP.getFreeHeap()>12000;
  bool ok=radio&&queue&&heap;
  emit("{\"v\":1,\"type\":\"self_test\",\"seq\":"+String(++sequenceNo)+
       ",\"ok\":"+String(ok?"true":"false")+
       ",\"wifi\":\""+String(radio?"READY":"NOT_READY")+"\""+
       ",\"queue\":\""+String(queue?"READY":"NOT_READY")+"\""+
       ",\"monitor\":\""+String(monitorRunning?"RUNNING":"STOPPED")+"\""+
       ",\"free_heap_bytes\":"+String(ESP.getFreeHeap())+"}");
}
static void sendHelp() {
  emit("{\"v\":1,\"type\":\"help\",\"commands\":[\"hello\",\"status\",\"self_test\",\"scan_start_passive\",\"scan_stop\",\"set_channel 1..13\",\"lab_run_tests\",\"lab_simulate_deauth\",\"lab_simulate_evil_twin\",\"lab_simulate_ble_flood\"]}");
}
static void emitLab(const char *test,const char *detail) {
  emit("{\"v\":1,\"type\":\"lab_simulation\",\"seq\":"+String(++sequenceNo)+
       ",\"simulated\":true,\"test\":\""+String(test)+"\",\"detail\":\""+String(detail)+"\"}");
}
static void runLabTests() {
  emitLab("DEAUTH_DETECTOR","Synthetic deauthentication observation; no RF frame transmitted");
  emitLab("EVIL_TWIN_HEURISTIC","Synthetic same-SSID security mismatch; validate Android alert path");
  emitLab("BLE_ADVERTISEMENT_RATE","Synthetic BLE advertisement-rate burst; no BLE packets transmitted");
  emit("{\"v\":1,\"type\":\"lab_test_summary\",\"seq\":"+String(++sequenceNo)+",\"ok\":true,\"tests\":3}");
}
static void handleCommand(const char *cmd) {
  if (!strcmp(cmd,"hello")) sendHello();
  else if (!strcmp(cmd,"help") || !strcmp(cmd,"/help")) sendHelp();
  else if (!strcmp(cmd,"status")) sendStatus();
  else if (!strcmp(cmd,"self_test") || !strcmp(cmd,"self-test")) sendSelfTest();
  else if (!strcmp(cmd,"scan_start_passive") || !strcmp(cmd,"scanap") || !strcmp(cmd,"scan start")) {
    bool ok=startMonitor();
    emit("{\"v\":1,\"type\":\"ack\",\"command\":\"scan_start_passive\",\"ok\":"+String(ok?"true":"false")+"}");
  } else if (!strcmp(cmd,"scan_stop") || !strcmp(cmd,"stopscan") || !strcmp(cmd,"scan stop")) {
    stopMonitor();
    emit("{\"v\":1,\"type\":\"ack\",\"command\":\"scan_stop\",\"ok\":true}");
  } else if (!strncmp(cmd,"set_channel ",12)) {
    int ch=atoi(cmd+12);
    if (ch<1 || ch>13) emit("{\"v\":1,\"type\":\"error\",\"error\":\"channel_must_be_1_to_13\"}");
    else {
      monitorChannel=(uint8_t)ch;
      bool ok=!monitorRunning || (esp_wifi_set_channel(monitorChannel,WIFI_SECOND_CHAN_NONE)==ESP_OK);
      emit("{\"v\":1,\"type\":\"ack\",\"command\":\"set_channel\",\"ok\":"+String(ok?"true":"false")+"}");
    }
  } else if (!strcmp(cmd,"lab_run_tests")) runLabTests();
  else if (!strcmp(cmd,"lab_simulate_deauth")) emitLab("DEAUTH_DETECTOR","Synthetic deauth event; receive-only test");
  else if (!strcmp(cmd,"lab_simulate_evil_twin")) emitLab("EVIL_TWIN_HEURISTIC","Synthetic SSID/security mismatch");
  else if (!strcmp(cmd,"lab_simulate_ble_flood")) emitLab("BLE_ADVERTISEMENT_RATE","Synthetic BLE rate anomaly");
  else emit("{\"v\":1,\"type\":\"error\",\"error\":\"unsupported_command\",\"hint\":\"send help\"}");
}

static void emitEvent(const RadioEvent &e) {
  if (e.kind==1) {
    if (!shouldReport(e.bssid,millis())) return;
    apsSeen++;
    emit("{\"v\":1,\"type\":\"wifi_ap_observation\",\"seq\":"+String(++sequenceNo)+
      ",\"uptime_ms\":"+String(e.uptimeMs)+",\"ssid\":\""+jsonSafe(e.ssid)+"\""+
      ",\"bssid\":\""+String(e.bssid)+"\",\"rssi_dbm\":"+String(e.rssi)+
      ",\"channel\":"+String(e.channel)+"}");
  } else {
    if (!strcmp(e.subtype,"DEAUTH")) deauthSeen++; else disassocSeen++;
    emit("{\"v\":1,\"type\":\"wifi_management_event\",\"seq\":"+String(++sequenceNo)+
      ",\"uptime_ms\":"+String(e.uptimeMs)+",\"subtype\":\""+String(e.subtype)+"\""+
      ",\"transmitter\":\""+String(e.transmitter)+"\",\"receiver\":\""+String(e.receiver)+"\""+
      ",\"bssid\":\""+String(e.bssid)+"\",\"reason_code\":"+String(e.reason)+
      ",\"rssi_dbm\":"+String(e.rssi)+",\"channel\":"+String(e.channel)+"}");
  }
}

void setup() {
  Serial.begin(SERIAL_BAUD);
  delay(250);
  eventQueue=xQueueCreate(48,sizeof(RadioEvent));
  sendHello();
  if (!eventQueue) emit("{\"v\":1,\"type\":\"fatal\",\"error\":\"event_queue_allocation_failed\"}");
  else sendSelfTest();
}
void loop() {
  while (Serial.available()>0) {
    char c=(char)Serial.read();
    if (c=='\n' || c=='\r') {
      if (commandLength) { commandBuffer[commandLength]='\0'; handleCommand(commandBuffer); commandLength=0; }
    } else if ((uint8_t)c>=0x20 && (uint8_t)c<=0x7E) {
      if (commandLength<MAX_COMMAND-1) commandBuffer[commandLength++]=c;
      else { commandLength=0; emit("{\"v\":1,\"type\":\"error\",\"error\":\"command_too_long\"}"); }
    }
  }
  RadioEvent event{};
  while (eventQueue && xQueueReceive(eventQueue,&event,0)==pdTRUE) emitEvent(event);
  uint32_t now=millis();
  if (now-lastHeartbeatMs>=5000) { lastHeartbeatMs=now; sendStatus(); }
  delay(2);
}
