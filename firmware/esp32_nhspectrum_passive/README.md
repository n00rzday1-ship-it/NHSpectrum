# ESP32-S3 passive companion reference firmware

## Build target
- ESP32-S3 board with USB CDC or USB-UART serial bridge.
- Arduino-ESP32 core 3.x.
- Serial: 115200 baud, 8-N-1.
- Initial profile: fixed-channel 2.4 GHz passive 802.11 management-frame observer.

## Current capability declaration
- Passive 802.11 management-frame observation.
- Deauthentication and disassociation event metadata.
- Status/heartbeat and USB serial protocol v1.

BLE scanning is intentionally not declared by this firmware profile yet. It must not be shown as available until a separately tested BLE implementation is added and the hello capability list is updated. This avoids claiming simultaneous Wi-Fi/BLE capability without validating ESP32 radio coexistence.

## Flash
1. Install Arduino IDE and Espressif ESP32 board package (3.x).
2. Select an ESP32-S3 board and USB CDC setting appropriate to the board.
3. Open `nhspectrum_passive.ino`.
4. Flash and open Serial Monitor at 115200.
5. Send `hello` or `status` as a newline-terminated command.

## Safety
This firmware is receive-only. It does not transmit deauth frames, inject packets, jam, capture handshakes/PMKIDs or collect credentials. It observes management frames on the configured channel only. To observe other channels, change the compile-time channel and reflash; channel hopping is not enabled in this baseline.

## Known limitations
- ESP32-S3 Wi-Fi is 2.4 GHz only.
- One fixed channel at a time.
- Event queue overflow is counted in `dropped_events`.
- This is not a calibrated RF spectrum analyzer.
- BLE capability is not enabled in this profile.
- The Android app must validate the serial handshake and every field before consuming telemetry.
