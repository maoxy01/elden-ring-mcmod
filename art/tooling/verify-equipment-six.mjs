import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createRequire} from 'node:module';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),out=path.join(root,'art/equipment-2d');
const require=createRequire('C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json');
const {PNG}=require('pngjs'),hash=b=>createHash('sha256').update(b).digest('hex');
const data=JSON.parse(await fs.readFile(path.join(out,'six-representatives.json'),'utf8'));
const mapping=JSON.parse(await fs.readFile(path.join(root,'art/equipment-reuse/equipment-mapping-42.json'),'utf8'));
assert.equal(mapping.mappings.length,42);assert.equal(new Set(mapping.mappings.map(m=>m.id)).size,42);
const inspect=process.argv.includes('--inspect');
for(const a of data.representatives)for(const v of a.variants){
  const bytes=await fs.readFile(a.delivery==='provider_reference_only'?path.join(root,v.file):path.join(out,v.file));assert.equal(hash(bytes),v.sha256);
  if(!inspect){const anchor=v.anchorPixels||a.anchorPixels;assert(anchor,`${a.id}: grip missing`);const png=PNG.sync.read(bytes);assert(png.data[(Math.floor(anchor[1])*png.width+Math.floor(anchor[0]))*4+3]>0,`${a.id} ${v.state}: grip in transparent pixel`)}
}
const {chromium}=require('playwright'),browser=await chromium.launch({channel:'msedge',headless:true});
const checks=[];
try{
  for(const viewport of inspect?[{width:1440,height:1080}]:[{width:1440,height:1080},{width:390,height:844}]){
    const page=await browser.newPage({viewport}),errors=[];page.on('pageerror',e=>errors.push(String(e)));
    await page.goto(`file:///${out.replaceAll('\\','/')}/preview.html`);await page.waitForFunction(()=>window.EQUIPMENT_QA?.ready());
    assert.equal(await page.locator('section').count(),6);assert(await page.locator('.native').evaluateAll(list=>list.every(i=>i.complete&&i.naturalWidth>0)));
    assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Horizontal overflow');
    await page.screenshot({path:path.join(out,`six-${viewport.width}-light.png`),fullPage:true});
    await page.locator('#dark').click();await page.screenshot({path:path.join(out,`six-${viewport.width}-dark.png`),fullPage:true});
    const right=await page.locator('section[data-id=straight_sword] .stage').screenshot();
    await page.locator('#left').click();const left=await page.locator('section[data-id=straight_sword] .stage').screenshot();assert.notEqual(hash(right),hash(left));
    await page.locator('#slim').check();await page.screenshot({path:path.join(out,`six-${viewport.width}-left-slim.png`),fullPage:true});
    const stateHashes=[];
    for(const state of ['standby','pulling_0','pulling_1','pulling_2']){await page.locator('section[data-id=long_bow] select').selectOption(state);stateHashes.push(hash(await page.locator('section[data-id=long_bow] .stage').screenshot()))}
    assert.equal(new Set(stateHashes).size,4,'Bow states repeated');
    const shieldHashes=[];
    for(const state of ['front','back','blocking']){await page.locator('section[data-id=shield] select').selectOption(state);shieldHashes.push(hash(await page.locator('section[data-id=shield] .stage').screenshot()))}
    assert.equal(new Set(shieldHashes).size,3,'Shield states repeated');
    await page.screenshot({path:path.join(out,`six-${viewport.width}-states.png`),fullPage:true});
    assert.deepEqual(errors,[]);checks.push({viewport,imagesLoaded:true,noOverflow:true,bowStates:4,shieldStates:3,bothHands:true});await page.close();
  }
}finally{await browser.close()}
if(!inspect){const file=path.join(out,'validation.json'),validation=JSON.parse(await fs.readFile(file,'utf8'));validation.browserVerified=true;validation.browserChecks=checks;await fs.writeFile(file,JSON.stringify(validation,null,2)+'\n')}
console.log(inspect?'Inspection captures saved; grip calibration not accepted.':'PASS hashes, opaque grip anchors, desktop/mobile, bow/shield states and left/right hands.');
