# NHSpectrum Capability Matrix

| Feature | Stock Android | ESP32-S3 companion | USB Wi-Fi adapter |
|---|---|---|---|
| Nearby AP list | Yes, throttled/cached | Passive 2.4 GHz scan | Depends on chipset + kernel driver |
| 5 GHz AP list | If phone supports | No on S3 | Depends on driver |
| 6 GHz AP list | If phone supports | No on S3 | Depends on driver |
| Raw Wi-Fi management frames | No general public API | Passive 2.4 GHz monitor callback | Only if driver exposes monitor mode |
| Deauth/disassoc detection | Connection-drop heuristic only | Passive frame observation | Passive frame observation if monitor mode works |
| Packet injection | Not supported | Not implemented | Not implemented |
| BLE advertisements | Android BLE scanner | ESP32 BLE scan | Not applicable |
| Full BLE promiscuous sniffer | No | Not guaranteed by standard Arduino BLE scan | Not applicable |
| Calibrated wideband RF spectrum | No | No; channel observation only | Only with an actual SDR/spectrum receiver |
| Cellular cell observations | TelephonyManager, partial | No | No |
| GNSS satellite status | GnssStatus | No | Optional dedicated GNSS receiver only |
| Open Drone ID | BLE if broadcast/API exposes it | Passive BLE support varies by stack | Wi-Fi Remote ID only if raw frame path exists |

## Never claim without verification

- USB Host does not provide a kernel driver for RTL8812AU, MT7612U or TL-WN722N.
- ESP32-S3 does not provide 5 GHz or 6 GHz Wi-Fi.
- Android WifiManager scan results are not raw monitor-mode packets.
- BLE scanner callbacks are not a complete promiscuous BLE capture.
- Channel density is not a calibrated spectrum analyzer.
- Cell IDs do not reveal a physical tower's coordinates.
- RSSI does not provide reliable metre-level ranging.
