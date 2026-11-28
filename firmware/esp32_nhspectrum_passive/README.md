# HUNTWARE · NHSpectrum ESP32-S3 companion firmware

## Target and protocol
- ESP32-S3 with native USB CDC or supported USB-UART bridge.
- Arduino-ESP32 core 3.x.
- Serial: 115200 baud, 8-N-1, newline-delimited UTF-8 JSON.
- Protocol version: 1.

## Flash
1. Install Arduino IDE and Espressif ESP32 board package 3.x.
2. Select the correct ESP32-S3 board and USB CDC setting.
3. Open `nhspectrum_passive.ino`, flash, and reset the board.
4. Connect to Android with a data-capable OTG cable.
5. Select NHSpectrum in Android's USB-device chooser or open Terminal and tap CONNECT.
6. Wait for **HUNTWARE LINK READY** and the automatic self-check.

## Commands
- `hello` — protocol handshake.
- `help` — firmware command list.
- `status` — radio, counters, channel and heap.
- `self_test` — firmware health check.
- `scan_start_passive` — start passive fixed-channel Wi-Fi observation.
- `scan_stop` — stop observation.
- `scanap` and `stopscan` — compatibility aliases.

The Android terminal also supports local `/help`, `clear`, `devices`, `select <number>`, and `inspect <number>`.

## What it observes
- Beacon and probe-response metadata (SSID when broadcast, BSSID, RSSI and channel).
- Deauthentication/disassociation management-frame metadata as observed events, not transmitted attacks.
- One fixed 2.4 GHz channel at a time; default channel 1.

## Safety and limitations
This firmware is receive-only. It does not transmit crafted frames, deauthenticate clients, inject packets, jam, capture handshakes/PMKIDs, or collect credentials. Management-event counts are observations and do not prove an attack. A single-channel monitor cannot see other channels simultaneously. RSSI is not a calibrated distance estimate. ESP32-S3 Wi-Fi is 2.4 GHz only; BLE is not advertised as supported by this firmware profile.
