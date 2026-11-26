# NHSpectrum — Radio Subsystems Architecture (v1)

## Status and engineering contract

This branch starts a controlled architecture expansion from the known-good map branch. It is **not** a claim that every module in the requested specification is already implemented or hardware-validated. Existing map, wardriving, Wi-Fi, BLE, cellular, GNSS, drone and ESP32 code must be regression-tested before integration.

### Non-negotiable rules

1. Preserve the working osmdroid map, green hand marker, live route, session persistence and GeoJSON export.
2. Keep each radio protocol isolated: Wi-Fi, BLE, cellular, GNSS, Remote ID and USB transport have distinct models, parsers, repositories and UI layers.
3. Every observation carries source, timestamp, freshness and capability metadata. Unknown data stays unknown; never synthesize a tower, drone, signal, GPS fix or spectrum sample.
4. Android API limitations are displayed explicitly. External hardware capability is unlocked only after its protocol handshake and feature report succeeds.
5. Default operation is passive. No deauthentication transmission, packet injection, handshake/PMKID capture, credential collection, jamming or disruptive testing is implemented.
6. A map point is an observation made at a GNSS fix, not the physical location of an AP/cell/drone unless that radio protocol actually supplied its own coordinates.

## Target package architecture

```text
com.noorzzz.nhspectrum/
  app/
    NHSpectrumApplication.kt
    AppContainer.kt
    CapabilityRegistry.kt
  core/
    model/ Observation.kt, RadioSource.kt, Freshness.kt
    permissions/ PermissionState.kt, PermissionCoordinator.kt
    time/ Clock.kt
    export/ ExportCoordinator.kt
  data/
    wifi/
      WifiAndroidSource.kt
      WifiBandClassifier.kt
      WifiSecurityClassifier.kt
      HiddenSsidCorrelator.kt
      EvilTwinHeuristic.kt
      DeauthEventParser.kt             # passive external frame events only
      WifiRepository.kt
    ble/
      BleAndroidSource.kt
      ManufacturerIdRegistry.kt
      BleAdvertisementParser.kt
      BleFloodHeuristic.kt
      BleRepository.kt
    cellular/
      CellAndroidSource.kt
      CellIdentityValidator.kt
      MccMncResolver.kt
      CellRepository.kt
    gnss/
      GnssAndroidSource.kt
      GnssSatelliteMapper.kt
      GnssRepository.kt
      DopEstimator.kt                  # only when raw measurement solution is valid
    drone/
      OpenDroneIdParser.kt
      RemoteIdAssembler.kt
      DroneWifiHeuristic.kt
      DroneRepository.kt
    wardrive/
      WardriveRecorder.kt
      RouteFilter.kt
      SessionStore.kt
  hardware/
    usb/
      UsbDeviceMonitor.kt
      UsbSerialTransport.kt
      HardwareHandshake.kt
      CompanionEnvelope.kt
      Esp32TelemetryRepository.kt
      AdapterCapability.kt
  audio/
    proximity/
      RssiSmoother.kt
      PathLossEstimator.kt
      ProximityTrend.kt
      ProximityAudioEngine.kt
  domain/
    assessment/ ScopePolicy.kt, Finding.kt, AssessmentSession.kt
  ui/
    radar/
    scanner/
    map/
    spectrum/
    satellites/
    drone/
    hardware/
    assessment/
    settings/
  firmware/
    esp32_nhspectrum_passive/
```

The current repository is smaller and several responsibilities still live in ScannerRepository. Refactor incrementally: first extract pure parsers and validators with tests, then move platform collectors behind interfaces, then connect each screen through StateFlow. Do not perform a wholesale rewrite of MapScreen or WardriveRecorder.

## Radio subsystem boundaries

| Subsystem | Android source | External source | Independent data model |
|---|---|---|---|
| Wi-Fi | WifiManager ScanResult | ESP32 passive 802.11 frame events; supported USB driver only if OS driver exists | WifiObservation |
| BLE | BluetoothLeScanner | ESP32 BLE advertisement events | BleObservation |
| Cellular | TelephonyManager CellInfo | Not supplied by the ESP32 Wi-Fi/BLE bridge | CellObservation |
| GNSS | LocationManager + GnssStatus | Optional external GNSS receiver with documented protocol | GnssFix + SatelliteObservation |
| Drone Remote ID | BLE advertisements; Wi-Fi only if exposed by platform/hardware | ESP32 decoded passive Remote ID frames | RemoteIdObservation |
| USB | UsbManager + USB serial | USB CDC/UART companion | HardwareTelemetryEnvelope |

Never place a cell tower or GNSS satellite in the Wi-Fi AP list. Never infer a drone from a generic BLE device alone.

## Capability levels

- **A0 — Android native:** supported Android scan APIs only; no raw 802.11 monitor mode, arbitrary channel hopping, raw BLE promiscuous capture or calibrated wideband spectrum.
- **A1 — ESP32 companion:** only capabilities advertised by the connected firmware profile. ESP32-S3 Wi-Fi is 2.4 GHz; do not claim 5/6 GHz scanning. Wi-Fi promiscuous callbacks are frame observation, not a calibrated spectrum analyzer. BLE scan and Wi-Fi monitor concurrency is chipset/firmware dependent.
- **A2 — USB Wi-Fi adapter:** available only when the exact USB VID/PID, chipset, kernel driver, firmware and permission path are verified on that Android build. USB Host and libusb do not create a missing kernel driver. RTL8812AU/MT7612U monitor mode is not assumed on stock Android.
- **A3 — rooted/custom-kernel integration:** optional separate capability profile; never required for the rootless core.

Capability checks are runtime facts, not device-name guesses.

## Required observation metadata

Each observation should include:
- stable subsystem-specific ID (or explicit anonymous ID),
- source: ANDROID_API / ESP32 / USB_ADAPTER / IMPORTED,
- observedAt wall-clock time and elapsed-realtime timestamp where available,
- lastSeen, staleAfter and validity flags,
- RSSI in dBm only when actually provided,
- optional location fix and its horizontal accuracy,
- raw fields retained only when lawful and useful for debugging,
- parser/schema version for imported or external data.

## Important Android constraints

- WifiManager scan results are cached and scan requests are throttled by Android. Use SCAN_RESULTS_AVAILABLE_ACTION and EXTRA_RESULTS_UPDATED; mark cached data as cached and show its age. Do not promise a 1 Hz Wi-Fi scan.
- BLE scan callbacks expose OS-filtered advertisements, not every over-the-air frame. Random/private addresses rotate and must not be treated as stable identities.
- Telephony CellInfo may be partial, stale or rate-limited. Validate identity fields, record CellInfo timestamp and registered status, and never invent physical tower coordinates. requestCellInfoUpdate is best-effort.
- GnssStatus provides satellite constellation, SVID, C/N0, elevation, azimuth and used-in-fix. DOP, RTK state and MSL/ellipsoid conversions are not guaranteed by the basic API; only display values when derived from a documented, validated measurement solution.
- Android location accuracy is an estimate, not a guarantee. Never label RSSI-derived distance as precise metres.
- A Remote ID message only contains the fields the aircraft broadcasts. Pilot/home location, serial, altitude reference, speed, heading and emergency state may be absent or distributed across multiple messages.

## Safety boundary

NHSpectrum is a passive field-intelligence and authorized assessment tool. Implement:
- passive deauthentication/disassociation event detection from external frame telemetry,
- Android connection-drop heuristics clearly labelled heuristic,
- AP security posture and Evil Twin indicators,
- BLE advertisement-rate anomaly detection,
- read-only beacon/probe metadata diagnostics,
- evidence capture and remediation reporting.

Do not implement deauthentication transmission, packet injection, jamming, handshake/PMKID capture, credential extraction or disruptive target testing. The companion firmware must never transmit crafted management frames.

## Data truth and UI language

Use explicit states:
- AVAILABLE / LIMITED / UNSUPPORTED / PERMISSION_REQUIRED / DISCONNECTED / STALE / ERROR.
- Security findings are heuristics with evidence and confidence, not definitive attacker attribution.
- RSSI-to-distance is a relative proximity estimate; show raw RSSI, filtered RSSI, assumed path-loss exponent and uncertainty.
- Cell timing advance, where exposed, is a coarse range constraint and not a tower location.
- Spectrum view from Android scan results is channel-density/observation history, not a calibrated RF power spectral density.

## Phased implementation and release gates

### Phase 0 — Preserve baseline
- Freeze the known-good map branch as the integration base.
- Add regression tests for map route persistence, original hand marker, session lifecycle and GeoJSON.
- Capture a baseline APK and SHA-256 in CI.

### Phase 1 — Separate models and capability reporting
- Add subsystem-specific models, source/freshness metadata and CapabilityRegistry.
- Extract pure Wi-Fi, BLE, cellular and Remote ID parsers.
- Unit tests for unavailable values, malformed packets, stale timestamps and unknown capabilities.

### Phase 2 — GNSS and cellular correctness
- GNSS skyplot from GnssStatus; no fabricated DOP/RTK.
- CellInfo request/update, timestamp and strict invalid-field filtering.
- Carrier mapping from an offline, versioned MCC/MNC dataset; preserve raw operator alpha tags.

### Phase 3 — Wi-Fi/BLE intelligence
- Security/band classifier, hidden-SSID correlation, OUI and SIG manufacturer registry.
- Evil Twin and BLE flood heuristics with explainable evidence.
- Passive deauth event parser for companion frames.

### Phase 4 — USB companion
- USB attach/permission lifecycle, serial framing, versioned handshake and heartbeat.
- ESP32-S3 passive 2.4 GHz Wi-Fi + BLE capability profile.
- Fail closed on unknown firmware or malformed telemetry.
- USB adapter profiles remain experimental until exact Android kernel support is proven.

### Phase 5 — Drone Remote ID and proximity
- Standards-based message parser and multi-message assembler with field provenance.
- Proximity estimator with per-band calibration and uncertainty; AudioAttributes-based beep engine with user-controlled volume.

### Phase 6 — Reporting and production hardening
- Offline storage, exports, privacy controls, battery and thermal testing.
- Device matrix tests, permission-denial tests, USB disconnect tests, background restrictions and long-session soak tests.
- No release claim until APK CI and physical-device acceptance both pass.

## Acceptance criteria

A feature is production-ready only when:
1. No mock data is used in production code.
2. Parser and filter unit tests pass.
3. Missing permissions and unsupported hardware produce honest states.
4. Stale observations are visually distinguishable.
5. Each subsystem can be enabled/disabled independently.
6. External firmware schema and app parser versions match.
7. No disruptive RF transmission exists in the passive companion build.
8. Existing map, route, scan, GNSS, cell and export regressions pass.
9. APK assembly, lint and test tasks pass in CI.
10. Physical-device tests confirm behaviour on supported hardware.
