const path = require('node:path');
const fs = require('node:fs/promises');
const assert = require('node:assert/strict');
const { createRequire } = require('node:module');
const { pathToFileURL } = require('node:url');
const dependencies = createRequire(path.join(process.argv[2], 'package.json'));
const { chromium } = dependencies('playwright');
const { PNG } = dependencies('pngjs');
const out = path.resolve(__dirname, '../player'), previews = path.join(out, 'previews');
const url = pathToFileURL(path.join(out, 'preview.html')).href;
function pixels(bytes, predicate) {
  const png = PNG.sync.read(bytes); let count = 0;
  for (let y = 160; y < png.height - 50; y++) for (let x = 8; x < png.width - 8; x++) {
    const i = (y * png.width + x) * 4; if (predicate(png.data[i], png.data[i + 1], png.data[i + 2])) count++;
  }
  return count;
}
function difference(a, b) {
  const first = PNG.sync.read(a), second = PNG.sync.read(b); let count = 0;
  for (let i = 0; i < first.data.length; i += 4) if (Math.abs(first.data[i] - second.data[i]) + Math.abs(first.data[i + 1] - second.data[i + 1]) + Math.abs(first.data[i + 2] - second.data[i + 2]) > 25) count++;
  return count;
}
(async () => {
  await fs.mkdir(previews, { recursive: true });
  await fs.writeFile(path.join(out, 'preview-validation.json'), JSON.stringify({ status: 'running' }) + '\n');
  for (const name of ['player_classic', 'player_slim', 'straight_sword', 'great_sword', 'shield', 'healing_flask']) {
    const bb = JSON.parse(await fs.readFile(path.join(out, `${name}.bbmodel`)));
    const ids = new Set(bb.elements.map(c => c.uuid)), groups = new Set(bb.groups.map(g => g.uuid)), seen = new Set();
    const traverse = entries => entries.forEach(entry => {
      if (typeof entry === 'string') { assert(ids.has(entry)); assert(!seen.has(entry)); seen.add(entry); }
      else { assert(groups.has(entry.uuid)); traverse(entry.children); }
    });
    traverse(bb.outliner); assert.equal(seen.size, ids.size);
    for (const c of bb.animations) for (const id of Object.keys(c.animators)) assert(groups.has(id));
    assert.equal(PNG.sync.read(Buffer.from(bb.textures[0].source.split(',')[1], 'base64')).width, 64);
  }
  const errors = [], browser = await chromium.launch({ channel: 'msedge', headless: true,
    args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--allow-file-access-from-files'] });
  try {
    const page = await browser.newPage({ viewport: { width: 1600, height: 960 }, deviceScaleFactor: 1 });
    page.on('pageerror', e => errors.push(e.message));
    const goto = async query => { await page.goto(`${url}?play=0&${query}`); await page.waitForFunction(() => window.reviewReady && window.reviewState); await page.waitForTimeout(120); };
    for (const [name, query] of [
      ['player-sheet', 'view=sheet'], ['sword-shield', ''], ['equipment', 'view=equipment'],
      ['great-sword', 'profile=great_sword&animation=heavy_windup&tick=20'],
      ['roll', 'animation=roll&tick=10'], ['drink', 'animation=drink&tick=14'], ['death', 'animation=death&tick=32'],
      ['slim', 'variant=slim']
    ]) {
      await goto(query);
      const bytes = await page.screenshot({ path: path.join(previews, `${name}.png`) });
      assert(pixels(bytes, (r, g, b) => r < 170 && g < 175 && b < 175) > 1500, `${name}: blank canvas`);
      assert.equal(await page.evaluate(() => window.reviewState.boneCount), 17);
      if (name !== 'player-sheet') {
        const f = await page.evaluate(() => window.reviewState.framing);
        assert(f.minX > -1 && f.maxX < 1 && f.minY > -1 && f.maxY < 1, `${name}: cropped asset`);
      }
      if (['roll', 'death'].includes(name)) {
        const min = await page.evaluate(() => window.reviewState.bodyMinY); assert(min >= -0.0001 && min < 0.125);
      }
      if (name === 'great-sword') assert((await page.evaluate(() => window.reviewState.gripError)) < 0.05, 'great-sword grip misses');
    }
    let maxGripError = 0;
    for (const animation of ['idle', 'light_1', 'light_2', 'heavy_windup', 'heavy_release', 'charged_release']) {
      await goto(`profile=great_sword&animation=${animation}`);
      for (const tick of [0, 4, 8, 12, 16, 20]) {
        await page.locator('#timeline').evaluate((slider, tick) => { slider.value = tick; slider.dispatchEvent(new Event('input', { bubbles: true })); }, tick);
        await page.waitForTimeout(35);
        maxGripError = Math.max(maxGripError, await page.evaluate(() => window.reviewState.gripError));
      }
    }
    assert(maxGripError < 0.05, `two-hand reach ${maxGripError}`);
    await goto('animation=walk'); await page.locator('#play').check();
    const a = await page.screenshot(); await page.waitForTimeout(500); const b = await page.screenshot();
    const changed = difference(a, b); assert(changed > 500, 'animation frozen');
    await page.locator('#play').uncheck(); await page.waitForTimeout(70);
    const tick = await page.evaluate(() => window.reviewState.tick); await page.waitForTimeout(120);
    assert.equal(await page.evaluate(() => window.reviewState.tick), tick);
    await page.locator('#bones').check(); await page.waitForTimeout(50);
    await page.locator('#variant').selectOption('slim'); await page.waitForTimeout(50);
    assert.equal(await page.evaluate(() => window.reviewState.variant), 'slim');
    await page.locator('#skin').setInputFiles(path.join(out, 'player-preview.png'));
    await page.waitForFunction(() => window.customSkinLoaded);
    // Check a distinct skin so the viewer cannot pass by ignoring the selected file.
    const red = new PNG({ width: 64, height: 64 });
    for (let i = 0; i < red.data.length; i += 4) { red.data[i] = 200; red.data[i + 1] = 24; red.data[i + 2] = 24; red.data[i + 3] = 255; }
    await page.locator('#skin').setInputFiles({ name: 'red-test.png', mimeType: 'image/png', buffer: PNG.sync.write(red) });
    await page.waitForTimeout(200);
    assert(pixels(await page.screenshot(), (r, g, b) => r > 150 && r > g * 1.8 && r > b * 1.8) > 1000, 'custom skin ignored');
    await page.setViewportSize({ width: 390, height: 844 }); await goto('');
    const mobile = await page.screenshot({ path: path.join(previews, 'mobile.png') });
    assert(pixels(mobile, (r, g, b) => r < 170 && g < 175 && b < 175) > 1500);
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    assert(await page.locator('header').evaluate(header => Array.from(header.children).every(e => {
      const r = e.getBoundingClientRect(); return r.left >= 0 && r.right <= innerWidth && r.bottom <= header.getBoundingClientRect().bottom;
    })), 'toolbar overflow');
    for (const query of ['profile=great_sword&animation=heavy_windup&tick=20', 'view=equipment', 'animation=drink&tick=18', 'animation=roll&tick=10']) {
      await goto(query);
      const f = await page.evaluate(() => window.reviewState.framing);
      assert(f.minX > -1 && f.maxX < 1 && f.minY > -1 && f.maxY < 1, `mobile ${query}: cropped asset`);
    }
    assert.deepEqual(errors, []);
    const report = { status: 'passed', desktop: '1600x960', mobile: '390x844', framing: 'body and equipment fully framed on desktop/mobile', blockbenchReferences: 'resolved',
      floorClearance: 'roll/death passed', maxGreatSwordGripErrorBlocks: maxGripError, animatedPixels: changed, customSkin: 'passed',
      pauseScrubVariantControls: 'passed', browserErrors: errors };
    await fs.writeFile(path.join(out, 'preview-validation.json'), JSON.stringify(report, null, 2) + '\n');
    console.log(JSON.stringify(report, null, 2));
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
