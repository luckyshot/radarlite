# RadarLite

Lightweight Android road-alert app. All features are free, with no ads, subscriptions, analytics SDK, user tracking, maps, or navigation. RadarLite passively listens for location updates produced by other apps and alerts when an OpenStreetMap camera or explicitly mapped hazard point appears ahead. Once the alert database is installed, monitoring works offline and uses no mobile data while you drive.

RadarLite is free and open source. Store listing assets and Play review notes live in `store-assets/`. The public GitHub Pages site lives in `docs/` and uses plain-language copy for non-technical users.

## Status

RadarLite is early-stage software. Validate the generated alert database and local legal requirements before using it on public roads.

The lightweight motorist-alert scope is documented in [PROJECT_MOTORIST.md](PROJECT_MOTORIST.md).

## Setup

### Android App

1. Install Android Studio with Android SDK 36. RadarLite targets Android 15 / API 35 for Play submission and compiles with the locally installed API 36 SDK.
2. Sync Gradle
3. Build and install on device
4. On first launch, grant the requested permissions. Background location is required for monitoring while the screen is off.
5. Toggle the switch ON

The app downloads alert database metadata from GitHub Releases:

```
https://github.com/luckyshot/radarlite/releases/latest/download/version.json
```

For a fork, override this without editing Kotlin:

```
./gradlew :app:assembleDebug -Pradarlite.dbVersionUrl=https://github.com/OWNER/REPO/releases/latest/download/version.json
```

**Note on sounds:** Audio alerts are generated programmatically via `SoundManager.kt` using `AudioTrack`. No audio files are bundled. Warning alerts play one short 880 Hz tone and one short phrase; urgent alerts play one 500 ms, 1200 Hz tone. Each camera and hazard type can be switched off independently. When enabled, the app says `Over speed limit 50` if it approaches a camera with a known numeric 50 km/h limit while travelling more than 3 km/h over it. Conditional, variable, and multi-value map limits are ignored.

**Note on walking and heading:** Alerts are suppressed below 15 km/h, and wait for a passive fix with a valid travel heading rather than guessing a direction. The app can still show location status and nearby alerts while moving slowly.

**Speed announcements:** The Interval dropdown can speak the current speed as only a number when it enters a new 5, 10, or 20 km/h band; it is disabled by default and never announces below 20 km/h. It uses the same fresh passive location fixes as road alerts, so it does not add GPS or network use.

**Note on database:** The app gracefully handles a missing bundled database by creating an empty schema. Tap "Check for update" on first run to download the full alert database. Manual checks contact the release metadata each time, then download the database only when a newer version exists. If monitoring is running, it reloads the database after a successful update. On launch, RadarLite prompts for an update when the database has not been checked for 7 days or more; choosing Skip suppresses the prompt for 24 hours.

To bundle an initial database, run the pipeline locally once and copy the resulting `cameras.db` (not the .gz) into `app/src/main/assets/cameras.db`.

### Pipeline (GitHub Actions + GitHub Releases)

1. Fork this repo
2. Make sure GitHub Actions has write permission for releases: Settings > Actions > General > Workflow permissions > Read and write permissions
3. The workflow runs every Sunday at 03:00 UTC. Trigger manually via Actions > Update Alert Database > Run workflow
4. The workflow publishes `cameras.db.gz` and `version.json` to a GitHub Release. The Android app uses GitHub's stable `releases/latest/download` URLs.

Run the pipeline locally with:

```
cd pipeline
npm ci
npm run all
```

The pipeline runs on Node 24 in GitHub Actions. Keep native pipeline dependencies, especially `better-sqlite3`, on versions that support Node 24 so `npm ci` can use compatible prebuilt binaries.

## Data sources

- **OpenStreetMap** via Overpass API: speed-camera/enforcement records and explicit hazard points for curves, dangerous junctions, level crossings, and traffic calming

Data is merged with a 25m spatial deduplication radius.

Alert data is derived from OpenStreetMap and must credit OpenStreetMap under the Open Data Commons Open Database License. See `https://www.openstreetmap.org/copyright`.

## Legal

Road-alert apps are illegal or restricted in some countries and regions. You are responsible for checking local laws before use. The app shows a disclaimer on first launch.

## License

RadarLite is licensed under the GNU Affero General Public License v3.0 or later. See `LICENSE`.

## Privacy

RadarLite does not include ads, subscriptions, analytics, crash reporting, user tracking, or a tracking SDK. Location data is processed on device for alert detection. The app makes network requests only when checking/downloading the alert database from GitHub Releases; normal monitoring works offline and does not use mobile data.

The public privacy policy is hosted through GitHub Pages at `https://luckyshot.github.io/radarlite/privacy.html`.

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

RadarLite uses `PRIORITY_PASSIVE` location only. It never starts GPS polling; it alerts only when another app or the system is already producing fixes. Without external fixes, it stays idle. While monitoring is on, the foreground service accepts every external fix, may read the latest cached Fused fix, and periodically re-registers the passive listener to recover stale callbacks. Speech starts only when an alert needs it.
