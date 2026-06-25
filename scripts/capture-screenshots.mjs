// Captures full-page screenshots of every Spring Batch Admin GUI screen with Chromium.
//
// Usage: node capture-screenshots.mjs
// Env:
//   BATCH_ADMIN_URL  base URL of the GUI (default http://localhost:8080/batch-admin)
//   SCREENSHOT_DIR   output directory (default ../docs/screenshots relative to this file)

import { chromium } from 'playwright';
import { mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const base = (process.env.BATCH_ADMIN_URL || 'http://localhost:8080/batch-admin').replace(/\/$/, '');
const outDir = process.env.SCREENSHOT_DIR || resolve(here, '..', 'docs', 'screenshots');

const screens = [
  { name: 'dashboard', path: '' },
  { name: 'jobs', path: '/jobs' },
  { name: 'create-job', path: '/jobs/new' },
  { name: 'executions', path: '/executions' },
  { name: 'execution-logs', path: '/executions?selected=1&logLevel=INFO' },
  { name: 'schedules', path: '/schedules' },
];

await mkdir(outDir, { recursive: true });

// --no-sandbox is required when Chromium runs as root (common in CI/container environments).
const browser = await chromium.launch({ args: ['--no-sandbox'] });
const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
const page = await context.newPage();

let failures = 0;
for (const screen of screens) {
  const url = base + screen.path;
  try {
    const response = await page.goto(url, { waitUntil: 'networkidle', timeout: 30000 });
    if (response && !response.ok()) {
      throw new Error(`HTTP ${response.status()}`);
    }
    const file = resolve(outDir, `${screen.name}.png`);
    await page.screenshot({ path: file, fullPage: true });
    console.log(`captured ${screen.name.padEnd(12)} <- ${url}`);
  } catch (err) {
    failures += 1;
    console.error(`FAILED   ${screen.name.padEnd(12)} <- ${url}: ${err.message}`);
  }
}

await browser.close();
console.log(`\nScreenshots written to ${outDir}`);
process.exit(failures === 0 ? 0 : 1);
