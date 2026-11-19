# NHSpectrum map configuration

NHSpectrum uses MapLibre Native (OpenGL ES renderer) for the Android map. This replaces the previous osmdroid renderer and keeps map rendering independent from the rest of the Compose UI.

## Default build

Without a provider key, the app uses the public MapLibre demo style as a development fallback. It is intended for development and smoke testing, not as a guaranteed production tile service. The Satellite control explains when a provider key is missing rather than showing a blank map.

## Enable production street and satellite styles

1. Create a MapTiler Cloud account and create an Android client key.
2. Restrict the key to the NHSpectrum Android application ID (`com.noorzzz.nhspectrum`) and the relevant package/signing certificate restrictions supported by the provider.
3. For a local build, add this to the user's Gradle properties file (do not commit the key):

   ```properties
   MAPTILER_KEY=your_restricted_client_key
   ```

4. For GitHub Actions, add a repository Actions secret named `MAPTILER_KEY` and expose it to the Gradle build step as a Gradle project property/environment variable.
5. Rebuild and test Dark, Street and Satellite styles on a real Android device.

The key is a client-side map access key, not a private server credential. Restrict it at the provider. Never commit it to source control.

## Map behaviour

- GPS updates are requested only while the map screen is visible.
- The first valid fix centres the map. Manual pan/zoom disables follow mode.
- The location control re-enables follow mode and recentres to a fresh fix.
- GPS accuracy and coordinates are shown in the HUD.
- Wardriving route and observation features are GeoJSON sources; observation markers are separate from the phone location.
- Satellite imagery requires a configured provider key. The app must not mislabel a street style as satellite imagery.

## Data and licensing

Map styles and tiles are supplied by the configured provider and remain subject to that provider's terms, attribution and usage limits. Do not bulk-download public OpenStreetMap standard tiles. Offline map packs should be added only with a provider and dataset whose license explicitly permits offline use.