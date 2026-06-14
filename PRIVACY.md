# Privacy

RadarLite does not include ads, analytics, crash reporting, or third-party tracking SDKs.

## Data Processed On Device

RadarLite processes passive location, speed, and bearing updates on device to detect nearby speed-camera records. Recent alert history is stored locally in the app database.

## Network Requests

RadarLite uses the network only when checking for or downloading the camera database from GitHub Releases. These requests are made to GitHub-hosted release URLs.

## Data Sharing

RadarLite does not transmit your location, alert history, or device identifiers to the project maintainers.

## Permissions

RadarLite requests foreground and background location so it can receive passive location updates while running in the background. It does not request Activity Recognition and does not start its own high-accuracy GPS polling. Notification permission is used for the required foreground service notification on supported Android versions.
