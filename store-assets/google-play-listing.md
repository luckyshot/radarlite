# RadarLite Google Play Listing

## Basic Details

- App name: RadarLite
- Default language: English
- App or game: App
- Category: Maps & Navigation
- Tags: Navigation, Auto & Vehicles, Travel & Local
- Price: Free
- Ads: No ads
- In-app purchases: None
- Subscriptions: None
- Source code: https://github.com/luckyshot/radarlite
- Privacy policy: https://luckyshot.github.io/radarlite/privacy.html

## Short Description

Free offline speed camera alerts. No ads, subscriptions, tracking

## Full Description

RadarLite is a lightweight, free and open source speed camera warning app for Android.

All features are free. There are no ads, no subscriptions, no in-app purchases, and no user tracking.

It passively listens for location updates already produced by other apps, such as navigation apps, and warns when an OpenStreetMap speed-camera record appears ahead.

Once the camera database is installed, RadarLite works offline while you drive. No internet connection or mobile data is needed for normal monitoring.

RadarLite does not include analytics, crash reporting, or tracking SDKs. Location processing happens on device. Network access is used only to check for and download the camera database from GitHub Releases.

RadarLite is free and open source under the AGPL. Source code is available at:
https://github.com/luckyshot/radarlite

Camera data is derived from OpenStreetMap and is available under the Open Data Commons Open Database License. See:
https://www.openstreetmap.org/copyright

Important: Speed camera warning apps are illegal or restricted in some countries and regions. Check local laws before using RadarLite on public roads.

## Background Location Declaration

RadarLite uses background location to provide speed camera alerts while the app is not visible. This is the app's core feature: warning the driver while another navigation app is open, the screen is off, or RadarLite is running as a foreground service.

Location is processed on device and is not sent to the developer.

## Prominent In-App Disclosure

RadarLite accesses location in the background to detect nearby speed cameras and show alerts even when the app is closed or not in use. Your location stays on your device and is not shared. Select Location and Allow all the time.

## Data Safety Draft

- Data collection: No user data collected
- Data sharing: No user data shared
- Data processed locally: location, speed, bearing, and alert history
- Network use: GitHub Releases database update checks and downloads only; normal monitoring works offline
- Encryption in transit: Yes, GitHub release URLs use HTTPS
- Account creation: Not supported
- Data deletion request: Not applicable because RadarLite does not maintain a server-side account or user dataset

## Content Rating Notes

- No user-generated content
- No social features
- No purchases
- No gambling
- No ads
- Not directed at children
- Contains legal/safety notice because speed camera warning apps are restricted in some regions

## Required Images

- App icon: `images/app_icon_512.png`
- Feature graphic: `images/feature_graphic_1024x500.png`
- Phone screenshots: capture at least 2 before submission; 4 portrait screenshots at 1080x1920 are recommended.

Suggested screenshots:

- Main status screen with service stopped
- Permission/disclosure flow for background location
- Active monitoring screen while receiving passive GPS updates
- Recent alerts/database card after data is loaded

## Review Video Script

Keep the video under 30 seconds.

1. Open RadarLite.
2. Show the legal notice and background-location disclosure.
3. Toggle monitoring on.
4. Show Android location permission flow.
5. Show the foreground service notification and monitoring screen.
6. Explain that location is processed on device and not shared.
