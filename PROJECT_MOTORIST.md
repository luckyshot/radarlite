# Motorist alerts project

RadarLite stays a lightweight, offline, passive-location warning app. It does not download maps, calculate routes, poll GPS, or use elevation data.

## Included alerts

The existing downloaded SQLite database contains speed cameras and explicit OpenStreetMap point hazards:

- speed cameras, red lights, and average-speed zones;
- sharp curves tagged as `hazard=curve`;
- dangerous junctions tagged as `hazard=dangerous_junction`;
- railway level crossings;
- traffic-calming features.

The pipeline stores only those points, not road geometry or map tiles. This keeps database size, download size, lookup work, and battery use low. OpenStreetMap coverage varies, so a missing alert never means a road is safe.

## Overspeed indication

The app already measures current speed from the same fresh passive location fixes used for alerts. When the motorist approaches an enabled camera with a numeric tagged limit and is more than 3 km/h over it, the normal early warning says `Over speed limit <limit>`.

This is not a general road-speed-limit feature. It makes no claim about the current road away from a camera, because that would require downloading road maps and matching each location to a road.

## Controls and sounds

Every alert type, including the camera-limit overspeed indication, has its own switch. A disabled type is ignored before distance and direction calculations, and it produces no warning or alert-log entry. The global monitoring switch remains the master control.

Warnings use one brief tone and one concise spoken phrase. Urgent alerts use one brief higher-pitched tone. Current-speed announcements remain independently configurable and use no tones.

## Battery and privacy

Monitoring remains passive-only: it processes a location fix only when Android or another app supplies one. It performs a small indexed lookup around that fix and makes no network requests while driving. Location stays on the device.

## Out of scope

- general speed-limit alerts;
- map downloads, map rendering, routes, or road matching;
- calculated curves, hills, gradients, or elevation data;
- crowd reports, tracking, analytics, advertising, or cloud location storage.

## Data and legal note

Alert data is derived from OpenStreetMap and must retain OpenStreetMap attribution and comply with the Open Database License. Motorists must obey road signs, local laws, and current conditions.
