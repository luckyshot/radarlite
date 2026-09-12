#!/usr/bin/env node
// Fetch explicit, lightweight road-alert points from OpenStreetMap via Overpass API.
import { writeFileSync } from 'fs';
import { COUNTRY_CODES } from './countries.js';

// Public mirrors of the Overpass API. overpass-api.de intermittently returns 504s
// under load, so failed attempts rotate to another mirror instead of hammering the same one.
const OVERPASS_URLS = [
  'https://overpass-api.de/api/interpreter',
  'https://overpass.kumi.systems/api/interpreter',
  'https://maps.mail.ru/osm/tools/overpass/api/interpreter'
];
const OUTPUT = '/tmp/osm_cameras.json';

const COUNTRY = process.env.COUNTRY || 'Spain';
const COUNTRY_CODE = COUNTRY_CODES[COUNTRY];
if (!COUNTRY_CODE) throw new Error(`Unknown country: ${COUNTRY}`);

function buildQuery(countryCode) {
  return `
[out:json][timeout:300];
// Use the country's administrative area so the shared Overpass server does not have
// to assemble every matching alert point in the world.
area["ISO3166-1"="${countryCode}"]["boundary"="administrative"]->.country;
(
  relation(area.country)["type"="enforcement"]["enforcement"~"maxspeed|average_speed|traffic_signals"];
  node(area.country)["highway"="speed_camera"];
  node(area.country)["hazard"="curve"];
  node(area.country)["hazard"="dangerous_junction"];
  node(area.country)["railway"="level_crossing"];
  node(area.country)["traffic_calming"];
);
out center;
`;
}

async function fetchOverpass(query) {
  console.log(`Fetching OSM data for ${COUNTRY} (this takes a few minutes)...`);
  const attempts = OVERPASS_URLS.length * 2;
  for (let attempt = 0; attempt < attempts; attempt++) {
    const url = OVERPASS_URLS[attempt % OVERPASS_URLS.length];
    try {
      const res = await fetch(url, {
        method:  'POST',
        body:    `data=${encodeURIComponent(query)}`,
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'User-Agent':   'RadarLite/1.0 (open source speed camera warning app)'
        },
        signal: AbortSignal.timeout(360_000)
      });
      if (!res.ok) throw new Error(`HTTP ${res.status} from ${url}`);
      const data = await res.json();
      return data.elements || [];
    } catch (e) {
      console.error(`  Attempt ${attempt + 1} (${url}) failed: ${e.message}`);
      if (attempt < attempts - 1) await sleep(30_000 * (attempt % OVERPASS_URLS.length + 1));
    }
  }
  return [];
}

function parseSpeed(raw) {
  if (!raw) return null;
  const match = raw.trim().match(/^(\d+)\s*(km\/h|kph|mph)?$/i);
  if (!match) return null; // Ignore conditional, variable, and multi-value limits.
  const speed = Number(match[1]);
  return match[2]?.toLowerCase() === 'mph' ? Math.round(speed * 1.60934) : speed;
}

function classifyType(tags) {
  if (tags.hazard === 'curve') return 'sharp_curve';
  if (tags.hazard === 'dangerous_junction') return 'dangerous_junction';
  if (tags.railway === 'level_crossing') return 'level_crossing';
  if (tags.traffic_calming) return 'traffic_calming';
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

const elements = await fetchOverpass(buildQuery(COUNTRY_CODE));

const seen = new Set();
const cameras = [];
for (const el of elements) {
  const lat = el.lat ?? el.center?.lat;
  const lon = el.lon ?? el.center?.lon;
  if (lat == null || lon == null) continue;
  const tags = el.tags || {};
  const type = classifyType(tags);
  // Different alert types may legitimately share a mapped position.
  const key = `${type}:${Math.round(lat * 1e5)},${Math.round(lon * 1e5)}`;
  if (seen.has(key)) continue;
  seen.add(key);
  cameras.push({
    lat,
    lon,
    speed_limit: parseSpeed(tags.maxspeed),
    type,
    direction:   parseDirection(tags.direction),
    sources:     'osm'
  });
}

console.log(`OSM: ${cameras.length} unique cameras in ${COUNTRY}`);
writeFileSync(OUTPUT, JSON.stringify(cameras));
