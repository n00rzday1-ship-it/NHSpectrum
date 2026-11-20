# NHSpectrum map configuration

## Active map engine

NHSpectrum currently restores the earlier, simple OpenStreetMap map implementation:

- Android map widget: osmdroid 6.1.20
- Street tiles: `TileSourceFactory.MAPNIK` (OpenStreetMap standard raster tiles)
- API key: not required
- Attribution: `© OpenStreetMap contributors`
- HTTP identification: stable NHSpectrum User-Agent
- Cache: app-scoped `NHSpectrum/osmdroid/tiles` directory
- Wardriving route: red Polyline overlay
- Current GPS: map Marker overlay
- Recorded observations: last 250 local observation markers
- Controls: zoom in, zoom out, recenter
- Removed: MapLibre style toggles, 2D/3D control and SAVE/offline-download control

The old MapScreen in the `main` branch used osmdroid with `TileSourceFactory.MAPNIK`; this branch restores that source while preserving the newer recorder, GeoJSON export, GPS quality gate and session controls.

## Offline/cache behaviour

The app keeps tiles already viewed in its app-scoped local cache. This can make previously viewed parts of the map available when revisited without connectivity, subject to cache contents and provider cache headers.

This is **not** a downloadable city/region offline-map pack. The public `tile.openstreetmap.org` service prohibits bulk prefetch and offline-region downloads. Do not add a prefetch/download-area feature using that service. See https://operations.osmfoundation.org/policies/tiles/.

For genuine user-controlled offline packs, use a provider that explicitly permits downloads or self-host OSM-derived vector tiles. OpenFreeMap permits up to 3,000 tiles or 50 MB per download, four concurrent requests and a unique User-Agent. See https://openfreemap.org/tos/.

## Maintenance note

The upstream osmdroid project was archived on 20 November 2024 and its latest published release is 6.1.20. This restoration prioritizes the previous NHSpectrum map behaviour and is a compatibility choice, not a recommendation to build new long-term map features on an unmaintained SDK. WiGLE's FOSS client has moved to MapLibre for its vector-map path; a later NHSpectrum migration should use MapLibre after the current rendering issue is separately diagnosed and device-tested.

## GPS route quality

- Recording only accepts GPS fixes with accuracy at or below 25 m.
- Small movements within the GPS uncertainty threshold are suppressed.
- Implausible location jumps are rejected.
- Route line uses red (#FF3B5C).
- Poor GPS fixes are not used to place new observations at an old location.
- GPS accuracy is an estimate, not a guarantee of exact position.
