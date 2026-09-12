#!/usr/bin/env node
// Build one country's cameras.db SQLite file, gzip it, and write its version-<CODE>.json.
// Runs once per country; the workflow loops it over whichever countries need a rebuild.
import Database from 'better-sqlite3';
import { readFileSync, writeFileSync, mkdirSync, statSync, createReadStream, createWriteStream, existsSync, unlinkSync } from 'fs';
import { createGzip } from 'zlib';
import { pipeline } from 'stream/promises';
import { COUNTRY_CODES } from './countries.js';

const RELEASE_DIR = '/tmp/release';
mkdirSync(RELEASE_DIR, { recursive: true });

const country = process.env.COUNTRY || 'Spain';
const code = COUNTRY_CODES[country];
if (!code) throw new Error(`Unknown country: ${country}`);

const DB_FILE      = `/tmp/cameras.db`;
const DB_GZ_FILE   = `${RELEASE_DIR}/cameras-${code}.db.gz`;
const VERSION_FILE = `${RELEASE_DIR}/version-${code}.json`;

const cameras = JSON.parse(readFileSync('/tmp/merged_cameras.json', 'utf8'));
// Never replace this country's public release with a valid-looking, empty database when
// an upstream source is unavailable. This also protects future source changes.
if (cameras.length === 0)
  throw new Error(`No alert points for ${country}; preserving its last known-good release`);
const today = new Date().toISOString().slice(0, 10);

// GitHub Releases gives us a stable "latest" download URL.
// DOWNLOAD_URL can override it for tests or alternate hosting.
const downloadUrl = process.env.DOWNLOAD_URL
  ?? `https://github.com/${process.env.GITHUB_REPOSITORY}/releases/latest/download/cameras-${code}.db.gz`;

// --- Build SQLite ---
if (existsSync(DB_FILE)) unlinkSync(DB_FILE);

const db = new Database(DB_FILE);
db.exec(`
  CREATE TABLE cameras (
    id          INTEGER PRIMARY KEY,
    lat         REAL    NOT NULL,
    lon         REAL    NOT NULL,
    speed_limit INTEGER,
    type        TEXT    DEFAULT 'speed',
    direction   INTEGER,
    sources     TEXT
  );
  CREATE INDEX idx_lat ON cameras(lat);
  CREATE INDEX idx_lon ON cameras(lon);
  CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT);
`);

const insert = db.prepare(
  'INSERT INTO cameras (id,lat,lon,speed_limit,type,direction,sources) VALUES (?,?,?,?,?,?,?)'
);
const insertAll = db.transaction(cams => {
  for (const c of cams)
    insert.run(c.id, c.lat, c.lon, c.speed_limit ?? null, c.type ?? 'speed', c.direction ?? null, c.sources ?? null);
});
insertAll(cameras);

db.prepare("INSERT INTO meta VALUES ('version', ?)").run(today);
db.prepare("INSERT INTO meta VALUES ('camera_count', ?)").run(String(cameras.length));
db.close();

const dbSize = statSync(DB_FILE).size;
console.log(`${country}: ${cameras.length} rows, ${(dbSize / 1024 / 1024).toFixed(1)} MB`);

// --- Gzip ---
await pipeline(
  createReadStream(DB_FILE),
  createGzip({ level: 9 }),
  createWriteStream(DB_GZ_FILE)
);
const gzSize = statSync(DB_GZ_FILE).size;
console.log(`Gzipped: ${(gzSize / 1024 / 1024).toFixed(1)} MB`);

// --- version-<CODE>.json ---
const version = { version: today, country, camera_count: cameras.length, url: downloadUrl, size_bytes: gzSize };
writeFileSync(VERSION_FILE, JSON.stringify(version, null, 2));
console.log(`${VERSION_FILE}: ${JSON.stringify(version)}`);
