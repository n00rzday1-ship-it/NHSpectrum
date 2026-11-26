# NHSpectrum USB Companion Protocol v1

## Supported first profile

Initial reference target: ESP32-S3 development board with native USB CDC or a supported USB-UART bridge. This firmware profile is passive and reports capabilities explicitly. It does not claim 5 GHz/6 GHz Wi-Fi, calibrated spectrum power, arbitrary BLE raw-frame capture, or packet injection.

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

Fields: wifi_channel, wifi_scan_state, ble_scan_state, dropped_events, free_heap_bytes, temperature_c (only if sensor exists).

## Trust model

- Firmware is untrusted input. Validate all lengths, enum values, MAC formatting, numeric ranges and rate limits in Android.
- No command exists for transmit, deauth, injection, jamming, handshake capture or PMKID capture.
- Supported app commands are hello, status, scan_start_passive, scan_stop and set_channel_observation_profile (bounded channel list).
- The app must show the actual chip and firmware capability profile; USB VID/PID alone is not sufficient.
- ESP32-S3 Wi-Fi is 2.4 GHz only. A different board requires a separate verified profile.
