param(
  [string]$Port = ""
)
$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($Port)) { $Port = Read-Host "Enter ESP32 COM port (example COM5)" }
if ($Port -notmatch '^COM[0-9]+$') { throw "Invalid COM port. Use Device Manager to identify the CH340 port." }
$build = Join-Path $PSScriptRoot ".pio/build/esp32wroom32"
foreach ($name in @("bootloader.bin","partitions.bin","firmware.bin")) {
  if (!(Test-Path (Join-Path $build $name))) { throw "Missing $name. Run pio run in firmware/esp32_nhspectrum_wroom32." }
}
Write-Host "WARNING: This erases the ENTIRE ESP32 flash, including existing firmware, NVS settings and stored data." -ForegroundColor Yellow
$confirm = Read-Host "Type ERASE to continue"
if ($confirm -cne "ERASE") { Write-Host "Cancelled; no flash changes made."; exit 1 }
python -m esptool --chip esp32 --port $Port --baud 115200 erase-flash
if ($LASTEXITCODE -ne 0) { throw "Flash erase failed; firmware was not written." }
python -m esptool --chip esp32 --port $Port --baud 460800 write-flash -z 0x1000 (Join-Path $build "bootloader.bin") 0x8000 (Join-Path $build "partitions.bin") 0x10000 (Join-Path $build "firmware.bin")
if ($LASTEXITCODE -ne 0) { throw "Firmware write failed." }
Write-Host "HUNTWARE WROOM firmware flashed. Disconnect/reconnect USB and open NHSpectrum Terminal." -ForegroundColor Green
