# Privacy

RadarLite does not include ads, analytics, crash reporting, or third-party tracking SDKs.

## Data Processed On Device

RadarLite processes location, speed, bearing, and activity-recognition signals on device to detect nearby speed-camera records. Recent alert history is stored locally in the app database.

## Network Requests

RadarLite uses the network only when checking for or downloading the camera database from GitHub Releases. These requests are made to GitHub-hosted release URLs.

## Data Sharing

RadarLite does not transmit your location, activity state, alert history, or device identifiers to the project maintainers.

## Permissions

RadarLite requests foreground and background location so monitoring can continue while driving with the screen off. Activity recognition is used to reduce battery use by adjusting GPS mode when motion changes. Notification permission is used for the required foreground service notification on supported Android versions.
