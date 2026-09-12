#!/usr/bin/env node
// Merge and deduplicate cameras from all sources using a spatial grid index.
import { readFileSync, writeFileSync } from 'fs';

const DEDUP_RADIUS_M = 25;    // cameras within 25m = same physical camera
const GRID_SIZE_DEG  = 0.001; // ~111m per cell; gives enough overlap for 25m check

function haversine(lat1, lon1, lat2, lon2) {
  const R = 6_371_000;
  const dlat = (lat2 - lat1) * Math.PI / 180;
  const dlon = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dlat/2)**2
    + Math.cos(lat1 * Math.PI/180) * Math.cos(lat2 * Math.PI/180) * Math.sin(dlon/2)**2;
  return R * 2 * Math.asin(Math.sqrt(a));
}

function gridKey(lat, lon) {
  return `${Math.round(lat / GRID_SIZE_DEG)},${Math.round(lon / GRID_SIZE_DEG)}`;
}

function neighborKeys(lat, lon) {
  const [glat, glon] = gridKey(lat, lon).split(',').map(Number);
  const keys = [];
  for (let dl = -1; dl <= 1; dl++)
    for (let dc = -1; dc <= 1; dc++)
      keys.push(`${glat + dl},${glon + dc}`);
  return keys;
}

// Same physical camera mapped twice (e.g. two nearby OSM points): keep the first
// position, fill in whichever fields the duplicate has that the first is missing.
function mergeTwo(a, b) {
  return {
    lat:         a.lat,
    lon:         a.lon,
    speed_limit: a.speed_limit ?? b.speed_limit ?? null,
    type:        a.type !== 'speed' ? a.type : b.type,
    direction:   a.direction ?? b.direction ?? null,
    sources:     a.sources
  };
}

const osm = JSON.parse(readFileSync('/tmp/osm_cameras.json', 'utf8'));

console.log(`Input: ${osm.length} OSM cameras`);

const grid   = new Map(); // gridKey -> array of merged[] indices
const merged = [];

for (const cam of osm) {
  let dupeIdx = null;
  for (const key of neighborKeys(cam.lat, cam.lon)) {
    for (const idx of (grid.get(key) || [])) {
      if (cam.type === merged[idx].type &&
          haversine(cam.lat, cam.lon, merged[idx].lat, merged[idx].lon) <= DEDUP_RADIUS_M) {
        dupeIdx = idx;
        break;
      }
    }
    if (dupeIdx !== null) break;
  }

  if (dupeIdx === null) {
    const idx = merged.length;
    merged.push({ ...cam });
    const key = gridKey(cam.lat, cam.lon);
    if (!grid.has(key)) grid.set(key, []);
    grid.get(key).push(idx);
  } else {
    merged[dupeIdx] = mergeTwo(merged[dupeIdx], cam);
  }
}

merged.forEach((cam, i) => { cam.id = i + 1; });

console.log(`Output: ${merged.length} cameras after deduplication`);
const withSpeed = merged.filter(c => c.speed_limit != null).length;
const withDir   = merged.filter(c => c.direction   != null).length;
console.log(`  With speed limit: ${withSpeed} (${Math.round(100 * withSpeed / merged.length)}%)`);
console.log(`  With direction  : ${withDir}`);

writeFileSync('/tmp/merged_cameras.json', JSON.stringify(merged));
console.log('Saved to /tmp/merged_cameras.json');
