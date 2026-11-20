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

Offline map packs are intentionally deferred. Add them only after selecting a provider and dataset whose licence explicitly permits offline storage and redistribution. Do not bulk-download the public OpenStreetMap standard tile service.
