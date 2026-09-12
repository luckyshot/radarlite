#!/usr/bin/env node
// Merge this run's per-country version-<CODE>.json files into the shared versions.json
// manifest, preserving entries for countries not rebuilt this run.
import { readFileSync, writeFileSync, readdirSync, mkdirSync } from 'fs';

const RELEASE_DIR = '/tmp/release';
const EXISTING_MANIFEST = process.argv[2] || '/tmp/existing_versions.json';

let manifest = {};
try { manifest = JSON.parse(readFileSync(EXISTING_MANIFEST, 'utf8')); } catch { /* first-ever run */ }

// build_db.js is the only thing that normally creates this directory; if every
// country failed at the fetch/merge stage this run, it never ran, so re-create it
// here rather than crash — the merge below then just republishes the old manifest.
mkdirSync(RELEASE_DIR, { recursive: true });

const versionFiles = readdirSync(RELEASE_DIR).filter(f => f.startsWith('version-') && f.endsWith('.json'));
for (const file of versionFiles) {
  const code = file.slice('version-'.length, -'.json'.length);
  manifest[code] = JSON.parse(readFileSync(`${RELEASE_DIR}/${file}`, 'utf8'));
}

writeFileSync(`${RELEASE_DIR}/versions.json`, JSON.stringify(manifest, null, 2));
console.log(`versions.json: ${Object.keys(manifest).join(', ')}`);
