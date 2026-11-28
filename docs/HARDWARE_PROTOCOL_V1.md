# NHSpectrum USB Companion Protocol v1

## Supported first profile

Supported hardware profiles are separate: (1) ESP32-S3 passive companion using native USB CDC or supported USB-UART; (2) ESP32-WROOM-32 DevKit with CH340 USB-UART using the dedicated `firmware/esp32_nhspectrum_wroom32` PlatformIO target. Both use 115200 8-N-1 and protocol v1. The WROOM profile reports only the capabilities it implements. It does not claim 5/6 GHz Wi-Fi, calibrated spectrum power, BLE scanning, or packet injection.

## Serial transport

- 115200 baud, 8 data bits, no parity, 1 stop bit.
- UTF-8 newline-delimited JSON (one complete envelope per line).
- Maximum accepted line: 4096 bytes.
- App sends: `{"v":1,"type":"hello","app":"NHSpectrum"}`
- Firmware replies with hello_ack, firmware version, chip, supported capabilities and a unique boot ID.
- Heartbeat: every 5 seconds.
- Telemetry envelopes include monotonically increasing seq and device uptime_ms.
- Unknown versions, malformed JSON, oversized lines, stale sequence numbers and unknown event types are rejected and logged.

## Envelope

```json
{
  "v": 1,
  "type": "hello_ack",
  "seq": 1,
  "uptime_ms": 1200,
  "firmware": "nhspectrum-passive-1.0.0",
  "chip": "ESP32-S3",
  "capabilities": [
    "wifi.scan.passive",
    "wifi.management.deauth.observe",
    "ble.scan.advertisements"
  ]
}
```

## Event types

### wifi_ap

Fields: ssid (may be empty), bssid, channel, frequency_mhz, rssi_dbm, security_hint, observed_at_ms.

### wifi_management_event

Fields: subtype (DEAUTH or DISASSOC only), transmitter, receiver, bssid, reason_code, channel, rssi_dbm, observed_at_ms. This is a passive observation. Firmware must not transmit frames.

### ble_advertisement

Fields: address (may be randomized), address_type, rssi_dbm, manufacturer_id (optional), manufacturer_data_hex (bounded), service_uuids, connectable, observed_at_ms. Do not claim a permanent device identity from a rotating address.

### channel_observation

Fields: channel, frequency_mhz, ap_count, strongest_rssi_dbm, sample_window_ms. This is channel observation density, not calibrated spectrum amplitude/noise floor.

### status

Fields: wifi_channel, wifi_scan_state, dropped_events, free_heap_bytes, temperature_c (only if sensor exists). BLE status is included only by firmware profiles that actually implement BLE scanning.

### lab_simulation

Fields: simulated=true, test, detail. This envelope is generated locally by the firmware for exercising the Android alert/display pipeline. It is never an RF observation and must always be labelled SIMULATED in the app.

## Trust model

- Firmware is untrusted input. Validate all lengths, enum values, MAC formatting, numeric ranges and rate limits in Android.
- No command exists for transmit, deauth, injection, jamming, handshake capture or PMKID capture.
- Supported app commands depend on the advertised profile. WROOM v1: `hello`, `help`, `status`, `self_test`, `scan_start_passive`, `scan_stop`, `set_channel 1..13`, and `lab_run_tests` / `lab_simulate_*` synthetic tests. Synthetic lab commands do not transmit radio packets.
- The app must show the actual chip and firmware capability profile; USB VID/PID alone is not sufficient.
- ESP32-S3 and ESP32-WROOM-32 Wi-Fi are 2.4 GHz only. WROOM BLE scanning is not advertised in this first profile. A different board requires a separate verified profile.
