# HUNTWARE — NHSpectrum ESP32-WROOM-32 Companion

Target: ESP32-WROOM-32 DevKit, 30-pin, CH340 USB-UART bridge.
PlatformIO environment: `esp32wroom32` / `esp32dev`.
Arduino-ESP32 framework; 115200 baud, 8-N-1; newline-delimited UTF-8 JSON protocol v1.

## Supported in this profile
- USB serial handshake and health self-test.
- Passive 2.4 GHz Wi-Fi beacon / probe-response observation.
- Passive 802.11 deauthentication and disassociation management-event observation.
- Fixed-channel selection, channels 1–13 (regional/legal settings apply).
- Status counters, queue-drop counter and free-heap telemetry.
- Synthetic lab events for validating Android detections: deauth detector, Evil Twin heuristic, BLE advertisement-rate alert.
- Synthetic events are explicitly marked as simulations and transmit no radio packets.

## Not implemented / not claimed
- No active deauthentication, disassociation, packet injection, jamming, credential or handshake capture.
- BLE scanning is not enabled in this first WROOM firmware profile. The chip supports Bluetooth 4.2 BR/EDR/BLE, but BLE scan integration is a separate tested capability; this build does not advertise it.
- No 5 GHz / 6 GHz Wi-Fi, calibrated RF spectrum amplitude, CSI, or precise device ranging.
- Passive monitor mode observes one Wi-Fi channel at a time; it is not a simultaneous full-band scan.

## Build with PlatformIO
Install PlatformIO Core, then:
```sh
pio run -e esp32wroom32
```
Outputs:
- `.pio/build/esp32wroom32/firmware.bin`
- `.pio/build/esp32wroom32/bootloader.bin`
- `.pio/build/esp32wroom32/partitions.bin`

## Full erase and flash (destructive)
**This erases the entire ESP32 flash, including any existing firmware, Wi-Fi settings, NVS and stored data. Back up anything needed first. It does not erase the Android app or the ESP32 eFuses.**

Install esptool 5.x and connect the board using a known-good USB-C data cable. Identify the correct COM/tty port. Hold BOOT while starting the erase/flash only if auto-reset fails; release BOOT after the ROM bootloader connects.

```sh
python -m esptool --chip esp32 --port COM5 --baud 115200 erase-flash
python -m esptool --chip esp32 --port COM5 --baud 460800 write-flash -z 0x1000 .pio/build/esp32wroom32/bootloader.bin 0x8000 .pio/build/esp32wroom32/partitions.bin 0x10000 .pio/build/esp32wroom32/firmware.bin
```
Replace COM5 with the actual port. On Linux use `/dev/ttyUSB0`; on macOS use the matching `/dev/cu.*` device. Never guess the port.

## NHSpectrum commands
- `hello`, `help`, `status`, `self_test`
- `scan_start_passive`, `scan_stop`
- `set_channel 1` through `set_channel 13`
- `lab_run_tests`
- `lab_simulate_deauth`
- `lab_simulate_evil_twin`
- `lab_simulate_ble_flood`

The Android app must display synthetic lab events as SIMULATED, never as live radio detections.
