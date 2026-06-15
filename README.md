# RadarLite

Lightweight Android speed camera warning app. No ads. No analytics SDK. No navigation. RadarLite passively listens for location updates produced by other apps and alerts when an OpenStreetMap speed-camera record appears ahead.

## Status

RadarLite is early-stage software. Validate the generated camera database and local legal requirements before using it on public roads.

## Setup

### Android App

1. Install Android Studio with Android SDK 34
2. Sync Gradle
3. Build and install on device
4. On first launch, grant the requested permissions. Background location is required for monitoring while the screen is off.
5. Toggle the switch ON

The app downloads camera database metadata from GitHub Releases:

```
https://github.com/luckyshot/radarlite/releases/latest/download/version.json
```

For a fork, override this without editing Kotlin:

```
./gradlew :app:assembleDebug -Pradarlite.dbVersionUrl=https://github.com/OWNER/REPO/releases/latest/download/version.json
```

**Note on sounds:** Audio alerts are generated programmatically via `SoundManager.kt` using `AudioTrack`. No audio files are bundled. The app generates a 880 Hz double-beep for warnings and a 660 Hz single tone for urgent alerts.

**Note on database:** The app gracefully handles a missing bundled database by creating an empty schema. Tap "Check for update" on first run to download the full camera database (requires Wi-Fi).

To bundle an initial database, run the pipeline locally once and copy the resulting `cameras.db` (not the .gz) into `app/src/main/assets/cameras.db`.

### Pipeline (GitHub Actions + GitHub Releases)

1. Fork this repo
2. Make sure GitHub Actions has write permission for releases: Settings > Actions > General > Workflow permissions > Read and write permissions
3. The workflow runs every Sunday at 03:00 UTC. Trigger manually via Actions > Update Camera Database > Run workflow
4. The workflow publishes `cameras.db.gz` and `version.json` to a GitHub Release. The Android app uses GitHub's stable `releases/latest/download` URLs.

Run the pipeline locally with:

```
cd pipeline
npm ci
npm run all
```

## Data sources

- **OpenStreetMap** via Overpass API: `highway=speed_camera` nodes and enforcement records with center coordinates

Data is merged with a 25m spatial deduplication radius.

## Legal

Speed camera warning apps are illegal or restricted in some countries and regions. You are responsible for checking local laws before use. The app shows a disclaimer on first launch.

## License

RadarLite is licensed under the GNU Affero General Public License v3.0 or later. See `LICENSE`.

## Privacy

RadarLite does not include ads, analytics, crash reporting, or a tracking SDK. Location data is processed on device for camera detection. The app makes network requests only when checking/downloading the camera database from GitHub Releases.

## Architecture

```
CameraDetectionService (ForegroundService)
├── LocationStrategy        — passive-only location listener
├── AlertEngine             — proximity + direction check + staged alerts
├── SoundManager            — programmatic tone generation via AudioTrack
└── CameraDbHelper          — raw SQLite reads from cameras.db

AppDatabase (Room)          — alert_log only

DatabaseUpdater             — OkHttp download of cameras.db.gz from GitHub Releases

ServiceState (StateFlow)    — shared state observable from MainActivity
```

## Battery impact

RadarLite uses `PRIORITY_PASSIVE` location only. It does not start its own GPS polling, including when driving or near a camera. In practice, it works when another app or the system is already producing location fixes, such as a navigation app running in the foreground. If no external location fixes are produced, RadarLite stays idle and will not alert.

## Changelog

### 1.0.0

- Initial version
- Shows the distance to the closest camera found during passive monitoring.
