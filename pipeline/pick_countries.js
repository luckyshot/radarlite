#!/usr/bin/env node
// Pick the two countries whose alert data is oldest (or never built), so the daily
// scheduled run rotates through every country roughly once a week.
import { readFileSync } from 'fs';
import { COUNTRY_CODES } from './countries.js';

const manifestFile = process.argv[2] || '/tmp/existing_versions.json';
let manifest = {};
try { manifest = JSON.parse(readFileSync(manifestFile, 'utf8')); } catch { /* first-ever run */ }

const lastBuilt = (name) => manifest[COUNTRY_CODES[name]]?.version || '0000-00-00';

const picked = Object.keys(COUNTRY_CODES)
  .sort((a, b) => lastBuilt(a).localeCompare(lastBuilt(b)))
  .slice(0, 2);

console.log(JSON.stringify(picked));
