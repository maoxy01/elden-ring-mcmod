import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {createRequire} from 'node:module';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),out=path.join(root,'art/vfx-2d');
const require=createRequire('C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json');
const {PNG}=require('pngjs'),manifest=JSON.parse(await fs.readFile(path.join(out,'manifest.json'),'utf8'));
const hash=b=>createHash('sha256').update(b).digest('hex');
for(const entry of manifest.hashes){const target=entry.scope==='workspace'?path.join(root,entry.file):path.join(out,entry.file);assert.equal(hash(await fs.readFile(target)),entry.sha256,entry.file)}
for(const asset of manifest.assets){
  assert.equal(asset.frames.length,16);const atlas=PNG.sync.read(await fs.readFile(path.join(out,asset.atlas.file)));
  for(const frame of asset.frames){
    const png=PNG.sync.read(await fs.readFile(path.join(out,frame.file))),[x,y,w,h]=frame.atlasRectPixels;
    assert.deepEqual([png.width,png.height],asset.frameSize);
    for(let row=0;row<h;row++)assert.deepEqual(png.data.subarray(row*w*4,(row+1)*w*4),atlas.data.subarray(((y+row)*atlas.width+x)*4,((y+row)*atlas.width+x+w)*4),'Atlas/frame mismatch');
  }
}
assert.equal(new Set(manifest.assets.map(a=>a.skillId)).size,8);
assert.equal(manifest.assets.length,10);
const previewData=await fs.readFile(path.join(out,'preview-data.js'),'utf8');
assert.equal(previewData,'window.VFX_ASSETS = '+JSON.stringify(manifest.assets)+';\n');
console.log('PASS manifest hashes, 8 skills / 10 clips / 160 PNGs, preview data and atlas placement.');
if(process.argv.includes('--browser')){
  const {chromium}=require('playwright'),browser=await chromium.launch({channel:'msedge',headless:true});
  const browserChecks=[];
  try{
    for(const viewport of [{width:1440,height:1000},{width:390,height:844}]){
      const page=await browser.newPage({viewport}),errors=[];page.on('pageerror',e=>errors.push(String(e)));
      await page.goto(`file:///${path.join(out,'preview.html').replaceAll('\\','/')}`);await page.waitForFunction(()=>window.VFX_QA?.ready());
      assert.equal(await page.locator('section').count(),8);assert.equal(await page.locator('.frames img').count(),160);
      assert(await page.locator('img').evaluateAll(images=>images.every(i=>i.complete&&i.naturalWidth>0)));
      assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Layout overflow');
      await page.evaluate(()=>{window.VFX_QA.setMode('clip');window.VFX_QA.setTick(2.4)});
      await page.screenshot({path:path.join(out,`eight-${viewport.width}-light.png`),fullPage:true});
      await page.locator('#dark').click();await page.screenshot({path:path.join(out,`eight-${viewport.width}-dark.png`),fullPage:true});
      const canvases=await page.locator('canvas').all(),visible=[];
      for(const canvas of canvases)visible.push(PNG.sync.read(await canvas.screenshot()));
      await page.evaluate(()=>window.VFX_QA.setTick(-1));
      const counts=[];
      for(let c=0;c<canvases.length;c++){
        const baseline=PNG.sync.read(await canvases[c].screenshot()),actual=visible[c];let changed=0;
        assert.deepEqual([actual.width,actual.height],[baseline.width,baseline.height]);
        for(let i=0;i<actual.data.length;i+=4)if(!actual.data.subarray(i,i+4).equals(baseline.data.subarray(i,i+4)))changed++;
        counts.push(changed);
      }
      assert(counts.every(n=>n>60),'Effect pixel coverage missing');
      await page.evaluate(()=>window.VFX_QA.setTick(8));const open=await page.locator('section[data-effect=wave_of_gold] canvas').screenshot();
      const openFlame=await page.locator('section[data-effect=takers_flames] canvas').screenshot();
      await page.locator('#blocked').check();const clipped=await page.locator('section[data-effect=wave_of_gold] canvas').screenshot();assert.notEqual(hash(open),hash(clipped),'Blocked lane control has no effect');
      assert.notEqual(hash(openFlame),hash(await page.locator('section[data-effect=takers_flames] canvas').screenshot()),'Flame lanes not clipped');
      await page.screenshot({path:path.join(out,`eight-${viewport.width}-blocked.png`),fullPage:true});
      const lags=await page.evaluate(()=>[window.VFX_QA.waveLags(4),window.VFX_QA.waveLags(16)]);assert.deepEqual(lags[0],lags[1]);
      await page.evaluate(()=>window.VFX_QA.setTick(2.4));const front=await page.locator('section[data-effect=waterfowl_dance] canvas').screenshot();
      await page.locator('#view').selectOption('80');const side=await page.locator('section[data-effect=waterfowl_dance] canvas').screenshot();assert.notEqual(hash(front),hash(side),'Slash plane incorrectly camera-facing');
      await page.locator('#view').selectOption('0');await page.evaluate(()=>window.VFX_QA.setMode('full'));
      const timings=[];for(const time of [11,23,35]){await page.evaluate(t=>window.VFX_QA.setTick(t),time);timings.push(await page.evaluate(()=>window.VFX_QA.snapshot().find(s=>s.skill==='waterfowl_dance')))}
      assert.deepEqual(timings.map(t=>t.phase),[0,1,2]);
      const death=[];for(const time of [9,17,25]){await page.evaluate(t=>window.VFX_QA.setTick(t),time);death.push(await page.evaluate(()=>window.VFX_QA.snapshot().find(s=>s.skill==='destined_death')))}
      assert.deepEqual(death.map(t=>t.phase),[0,1,2]);
      await page.evaluate(()=>window.VFX_QA.setTick(13));
      const cursed=await page.evaluate(()=>window.VFX_QA.snapshot().find(s=>s.skill==='cursed_blood_slice'));
      assert.deepEqual(cursed.active.map(s=>s.id),['cursed_slash','blood_flame']);
      await page.locator('section[data-effect=starcaller_cry] .clip-select').selectOption('gravity_impact');
      await page.evaluate(()=>window.VFX_QA.setTick(3));
      assert.equal(await page.evaluate(()=>window.VFX_QA.snapshot().find(s=>s.skill==='starcaller_cry').id),'gravity_impact');
      await page.locator('section[data-effect=cursed_blood_slice] .clip-select').selectOption('blood_flame');
      await page.evaluate(()=>window.VFX_QA.setTick(3));
      await page.screenshot({path:path.join(out,`eight-${viewport.width}-secondary.png`),fullPage:true});
      await page.evaluate(()=>window.VFX_QA.setTick(18));await page.locator('#play').click();const t0=await page.evaluate(()=>window.VFX_QA.snapshot()[0].t);await page.waitForTimeout(180);const t1=await page.evaluate(()=>window.VFX_QA.snapshot()[0].t);assert(t1>t0,'Playback did not advance');await page.locator('#play').click();
      assert.deepEqual(errors,[]);browserChecks.push({viewport,imagesLoaded:true,canvasPixelCounts:counts,burstTimings:true,planeForeshortening:true,blockedLanes:true,waveStableAfterFormation:true,playback:true});
      await page.close();
    }
  }finally{await browser.close()}
  const validation=JSON.parse(await fs.readFile(path.join(out,'validation.json'),'utf8'));validation.browserVerified=true;validation.browserChecks=browserChecks;
  manifest.reviewScreenshots={status:'eight-* screenshots verify this delivery; preview-1440-* belongs to the backed-up three-sample batch',currentBrowserVerification:true,files:browserChecks.flatMap(c=>['light','dark','blocked','secondary'].map(name=>`eight-${c.viewport.width}-${name}.png`))};
  await fs.writeFile(path.join(out,'manifest.json'),JSON.stringify(manifest,null,2)+'\n');
  await fs.writeFile(path.join(out,'validation.json'),JSON.stringify(validation,null,2)+'\n');console.log('PASS desktop/mobile playback, canvas coverage, burst timing, lane clipping and plane view.');
}
