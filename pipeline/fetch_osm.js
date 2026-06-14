#!/usr/bin/env node
// Fetch speed camera data from OpenStreetMap via Overpass API.
import { writeFileSync } from 'fs';

const OVERPASS_URL = 'https://overpass-api.de/api/interpreter';
const OUTPUT      = '/tmp/osm_cameras.json';

const QUERY = `
[out:json][timeout:300];
(
  relation["type"="enforcement"]["enforcement"~"maxspeed|average_speed"];
  node["highway"="speed_camera"];
);
out center;
`;

async function fetchOverpass(query) {
  console.log('Fetching OSM data (this takes a few minutes)...');
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const res = await fetch(OVERPASS_URL, {
        method:  'POST',
        body:    `data=${encodeURIComponent(query)}`,
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'User-Agent':   'RadarLite/1.0 (open source speed camera warning app)'
        },
        signal: AbortSignal.timeout(360_000)
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      return data.elements || [];
    } catch (e) {
      console.error(`  Attempt ${attempt + 1} failed: ${e.message}`);
      if (attempt < 2) await sleep(30_000 * (attempt + 1));
    }
  }
  return [];
}

function parseSpeed(raw) {
  if (!raw) return null;
  raw = raw.trim();
  if (raw.endsWith(' mph')) return Math.round(parseInt(raw) * 1.60934);
  const n = parseInt(raw);
  return isNaN(n) ? null : n;
}

function classifyType(tags) {
  const e = tags.enforcement || '';
  if (e.includes('traffic_signals') || tags['camera:type'] === 'red_light') return 'red_light';
  if (e.includes('average_speed')) return 'average_speed';
  return 'speed';
}

function parseDirection(raw) {
  if (!raw) return null;
  const compass = { N: 0, NE: 45, E: 90, SE: 135, S: 180, SW: 225, W: 270, NW: 315 };
  if (compass[raw.toUpperCase()] !== undefined) return compass[raw.toUpperCase()];
  const n = parseInt(raw);
  return isNaN(n) ? null : n;
}

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

const elements = await fetchOverpass(QUERY);

const seen = new Set();
const cameras = [];
for (const el of elements) {
  const lat = el.lat ?? el.center?.lat;
  const lon = el.lon ?? el.center?.lon;
  if (lat == null || lon == null) continue;
  const key = `${Math.round(lat * 1e5)},${Math.round(lon * 1e5)}`;
  if (seen.has(key)) continue;
  seen.add(key);
  const tags = el.tags || {};
  cameras.push({
    lat,
    lon,
    speed_limit: parseSpeed(tags.maxspeed),
    type:        classifyType(tags),
    direction:   parseDirection(tags.direction),
    sources:     'osm'
  });
}

console.log(`OSM: ${cameras.length} unique cameras`);
writeFileSync(OUTPUT, JSON.stringify(cameras));
