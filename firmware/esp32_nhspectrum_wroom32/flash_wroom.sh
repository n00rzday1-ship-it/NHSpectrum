#!/usr/bin/env bash
set -euo pipefail
PORT="${1:-}"
if [[ -z "$PORT" ]]; then read -r -p "Enter ESP32 serial port (e.g. /dev/ttyUSB0): " PORT; fi
if [[ ! -e "$PORT" ]]; then echo "Serial port does not exist: $PORT" >&2; exit 1; fi
DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD="$DIR/.pio/build/esp32wroom32"
for f in bootloader.bin partitions.bin firmware.bin; do
  [[ -s "$BUILD/$f" ]] || { echo "Missing $f; build firmware first." >&2; exit 1; }
done
echo "WARNING: this erases the entire ESP32 flash, including previous firmware, NVS settings and stored data."
read -r -p "Type ERASE to continue: " CONFIRM
[[ "$CONFIRM" == "ERASE" ]] || { echo "Cancelled; no flash changes made."; exit 1; }
python -m esptool --chip esp32 --port "$PORT" --baud 115200 erase-flash
python -m esptool --chip esp32 --port "$PORT" --baud 460800 write-flash -z \
  0x1000 "$BUILD/bootloader.bin" 0x8000 "$BUILD/partitions.bin" 0x10000 "$BUILD/firmware.bin"
echo "HUNTWARE WROOM firmware flashed. Reconnect USB and open NHSpectrum Terminal."
