// Behavioural check of the Create-job drag-and-drop step reorder.
// Loads the page, sets three steps, simulates an HTML5 drag of the first step onto
// the bottom half of the third, and asserts the textarea is reordered to b, c, a.
import { chromium } from 'playwright';

const base = (process.env.BATCH_ADMIN_URL || 'http://localhost:8080/batch-admin').replace(/\/$/, '');
const browser = await chromium.launch({ args: ['--no-sandbox'] });
const page = await browser.newContext().then((c) => c.newPage());
await page.goto(`${base}/jobs/new`, { waitUntil: 'networkidle' });

// Seed three steps and let the widget render its rows.
await page.evaluate(() => {
  const ta = document.getElementById('steps-input');
  ta.value = 'a = log\nb = log\nc = log\n';
  ta.dispatchEvent(new Event('input', { bubbles: true }));
});
await page.waitForFunction(() => document.querySelectorAll('#step-order > div').length === 3);

// Simulate HTML5 DnD: drag row 0 onto the lower half of row 2 (=> drop after it).
const result = await page.evaluate(() => {
  const rows = document.querySelectorAll('#step-order > div');
  const from = rows[0];
  const onto = rows[2];
  const dt = new DataTransfer();
  const rect = onto.getBoundingClientRect();
  const lowerY = rect.top + rect.height * 0.75; // lower half => insert after
  const fire = (el, type, clientY) => el.dispatchEvent(
    new DragEvent(type, { bubbles: true, cancelable: true, dataTransfer: dt, clientY }));
  fire(from, 'dragstart', rect.top);
  fire(onto, 'dragover', lowerY);
  fire(onto, 'drop', lowerY);
  fire(from, 'dragend', rect.top);
  return document.getElementById('steps-input').value.trim();
});

await browser.close();

const expected = 'b = log\nc = log\na = log';
if (result === expected) {
  console.log('OK: drag-and-drop reordered steps to ->\n' + result);
  process.exit(0);
} else {
  console.error('FAIL: expected\n' + expected + '\n---got---\n' + result);
  process.exit(1);
}
