# NHSpectrum map configuration

## Map architecture

NHSpectrum uses MapLibre Native (OpenGL ES) with a hybrid basemap strategy.

- **Street (default):** OpenFreeMap Liberty vector style.
- **Dark:** OpenFreeMap Dark vector style.
- **Satellite:** Esri World Imagery raster tiles with a geographic reference/labels overlay.
- **Recovery fallback:** Esri World Street Map raster tiles for Street and Dark if the vector style fails; satellite falls back to Street if imagery cannot load.

The map engine remains independent from the rest of the Compose UI. The map screen keeps its GPS HUD, route GeoJSON, observation GeoJSON, current-location marker, zoom controls and follow-location behaviour.

## Reliability behaviour

- The default map is Street so a first launch opens a readable geographic map.
- MapLibre style failures trigger one raster fallback attempt.
- Satellite mode uses a purpose-built imagery + reference style. It does not place imagery above opaque vector land layers.
- A visible loading state is shown while the basemap style initializes.
- A fallback warning is shown if Dark or Satellite has to fall back to Street.
- The app must never report a fallback as the originally requested imagery/style.
- GPS updates are requested only while the map screen is visible.
- The first valid fix centres the map. Manual pan/zoom disables follow mode.
- The location control re-enables follow mode and recentres to a fresh fix.
- Wardriving route and observation features remain separate GeoJSON sources.

## Provider notes and attribution

OpenFreeMap supplies the Liberty and Dark vector styles. Esri supplies World Imagery and the raster recovery basemap. Keep each provider's required attribution visible in the map UI and verify current provider terms, service limits and coverage before a production release.

Esri imagery attribution: Esri, Maxar, Earthstar Geographics, and the GIS User Community.

MapLibre's attribution control must remain enabled.

## Offline maps

- The Map screen can download a user-initiated 4 km × 4 km neighbourhood area around a valid GPS fix using MapLibre OfflineManager.
- Offline packs use the exact OpenFreeMap Liberty style URL used by the Street map so MapLibre can reuse the stored style resources.
- Each request is limited to 3,000 tiles and is paused if stored tile data reaches 50 MB.
- Requests identify the app with an NHSpectrum User-Agent and use no more than four concurrent requests.
- The user can use the downloaded Street area without an internet connection; refresh/update and delete controls should be added before release.
- OpenFreeMap's published offline allowance is up to 3,000 tiles or 50 MB per download, with at most four parallel requests and a unique app User-Agent. See https://openfreemap.org/tos/.
- The online raster recovery source is OpenStreetMap standard tiles with visible attribution. It is not used for offline downloads; do not bulk-download that service.

## Wardriving route quality

- Route capture accepts only GPS fixes with accuracy at or below 25 m.
- Route points require at least 5 m movement or the configured accuracy-relative movement threshold.
- Implausible jumps above the configured speed ceiling plus reported GPS uncertainty are rejected.
- Raw accepted fixes are retained; the recorder does not fabricate a smoothed route.
- A weak fix cannot be used to attach new observations to an older route location.
- The map draws the recorded route in red. GPS accuracy remains an estimate, not a guarantee of positional precision.
