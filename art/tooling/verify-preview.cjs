const path = require('node:path');
const fs = require('node:fs/promises');
const assert = require('node:assert/strict');
const { createRequire } = require('node:module');
const { pathToFileURL } = require('node:url');
const dependencies = process.argv[2] ? createRequire(path.join(process.argv[2], 'package.json')) : require;
const { chromium } = dependencies('playwright');
const { PNG } = dependencies('pngjs');
const out = path.resolve(__dirname, '../margit');
const previews = path.join(out, 'previews');
const url = pathToFileURL(path.join(out, 'preview.html')).href;

function darkPixels(bytes) {
  const png = PNG.sync.read(bytes);
  let count = 0;
  for (let y = 130; y < png.height - 30; y++) for (let x = 10; x < png.width - 10; x++) {
    const i = (y * png.width + x) * 4;
    if (png.data[i] < 170 && png.data[i + 1] < 175 && png.data[i + 2] < 170) count++;
  }
  return count;
}
function differences(a, b) {
  const first = PNG.sync.read(a), second = PNG.sync.read(b);
  let count = 0;
  for (let i = 0; i < first.data.length; i += 4) {
    if (Math.abs(first.data[i] - second.data[i]) + Math.abs(first.data[i + 1] - second.data[i + 1]) + Math.abs(first.data[i + 2] - second.data[i + 2]) > 25) count++;
  }
  return count;
}
function goldenPixels(bytes) {
  const png = PNG.sync.read(bytes);
  let count = 0;
  for (let y = 80; y < png.height - 30; y++) for (let x = 10; x < png.width - 10; x++) {
    const i = (y * png.width + x) * 4;
    if (png.data[i] > 190 && png.data[i + 1] > 140 && png.data[i + 2] < 150 && png.data[i] - png.data[i + 2] > 55) count++;
  }
  return count;
}

(async () => {
  await fs.mkdir(previews, { recursive: true });
  const texture = PNG.sync.read(await fs.readFile(path.join(out, 'margit.png')));
  assert.equal(texture.width, 128); assert.equal(texture.height, 128);
  const bb = JSON.parse(await fs.readFile(path.join(out, 'margit.bbmodel')));
  assert.equal(bb.meta.format_version, '5.0'); assert.equal(bb.elements.length, 38);
  const ids = new Set(bb.elements.map(c => c.uuid));
  const groups = new Set(bb.groups.map(g => g.uuid));
  const references = new Set();
  const traverse = entries => entries.forEach(entry => {
    if (typeof entry === 'string') { assert(ids.has(entry)); assert(!references.has(entry)); references.add(entry); }
    else { assert(groups.has(entry.uuid)); traverse(entry.children); }
  });
  traverse(bb.outliner); assert.equal(references.size, 38);
  for (const animation of bb.animations) for (const id of Object.keys(animation.animators)) assert(groups.has(id));
  const hammer = JSON.parse(await fs.readFile(path.join(out, 'spectral_hammer.bbmodel')));
  const hammerHead = hammer.elements.find(cube => cube.name === 'head');
  assert.deepEqual(hammerHead.faces.north.uv, hammerHead.faces.south.uv, 'Hammer must not use the character face texture');
  const errors = [];
  const browser = await chromium.launch({ channel: 'msedge', headless: true, args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--allow-file-access-from-files'] });
  try {
    const page = await browser.newPage({ viewport: { width: 1800, height: 960 }, deviceScaleFactor: 1 });
    page.on('pageerror', error => errors.push(error.message));
    for (const [name, query] of [
      ['model-sheet', 'view=sheet&play=0'],
      ['perspective', 'view=orbit&play=0'],
      ['tail-side', 'view=side&play=0'],
      ['hammer-anticipation', 'view=orbit&animation=hammer_slam&tick=32&play=0']
    ]) {
      await page.goto(`${url}?${query}`);
      await page.waitForFunction(() => window.reviewReady && window.reviewState);
      await page.waitForTimeout(150);
      const screenshot = await page.screenshot({ path: path.join(previews, `${name}.png`) });
      assert(darkPixels(screenshot) > 8000, `${name}: blank or missing model`);
      assert.equal(await page.evaluate(() => window.reviewState.tailBones), 3);
      if (name === 'hammer-anticipation') {
        assert.deepEqual(await page.evaluate(() => window.reviewState.visibleWeapons), ['spectral_hammer']);
        assert(goldenPixels(screenshot) > 1500, 'Golden hammer is occluded or missing');
      }
    }
    await page.goto(`${url}?animation=walk`);
    await page.waitForFunction(() => window.reviewReady && window.reviewState?.animation === 'walk');
    const frameA = await page.screenshot();
    await page.waitForTimeout(520);
    const frameB = await page.screenshot();
    const changedPixels = differences(frameA, frameB);
    assert(changedPixels > 1000, 'Animation is not changing the canvas');
    await page.locator('#bones').check();
    await page.waitForTimeout(120);
    assert(await page.evaluate(() => document.getElementById('bones').checked));
    await page.locator('#play').uncheck();
    await page.waitForTimeout(80);
    const pausedTick = await page.evaluate(() => window.reviewState.tick);
    await page.waitForTimeout(120);
    assert.equal(await page.evaluate(() => window.reviewState.tick), pausedTick);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(`${url}?play=0`);
    await page.waitForFunction(() => window.reviewReady && window.reviewState);
    const mobile = await page.screenshot({ path: path.join(previews, 'mobile.png') });
    assert(darkPixels(mobile) > 1500, 'Mobile canvas is blank');
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    const fits = await page.locator('header').evaluate(header => {
      const elements = Array.from(header.children).filter(e => getComputedStyle(e).display !== 'none');
      return elements.every(e => { const r = e.getBoundingClientRect(); return r.left >= 0 && r.right <= innerWidth && r.bottom <= header.getBoundingClientRect().bottom; });
    });
    assert(fits, 'Mobile toolbar overflow');
    assert.deepEqual(errors, [], 'Browser runtime errors');
    const report = { status: 'passed', png: '128x128 decoded', blockbenchReferences: 'all resolved', tailBones: 3, desktop: '1800x960 nonblank', mobile: '390x844 nonblank, controls fit', animationChangedPixels: changedPixels, pause: 'passed', browserErrors: errors };
    await fs.writeFile(path.join(out, 'preview-validation.json'), JSON.stringify(report, null, 2) + '\n');
    console.log(JSON.stringify(report, null, 2));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
