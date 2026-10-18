import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createRequire} from 'node:module';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {PNG,stats,opaque} from './equipment-pixels.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),out=path.join(root,'art/equipment-2d/full');
const require=createRequire('C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json');
const hash=b=>createHash('sha256').update(b).digest('hex'),read=async f=>JSON.parse(await fs.readFile(path.join(out,f),'utf8'));
const {assets}=await read('equipment-42.json'),pub=await read('publishable-assets.json'),validation=await read('validation.json');
assert.equal(assets.length,42);assert.equal(new Set(assets.map(a=>a.id)).size,42);assert.equal(pub.include.length,94);
for(const a of assets){
  assert.equal(hash(await fs.readFile(path.join(root,a.source))),a.sourceHash);
  for(const v of [...a.variants,...a.gui]){
    const bytes=await fs.readFile(a.delivery==='provider_reference_only'?path.join(root,v.file):path.join(out,v.file));assert.equal(hash(bytes),v.sha256);
    const p=PNG.sync.read(bytes);assert(opaque(p,...v.anchorPixels),`${a.id} ${v.state} grip`);
    if(a.delivery==='own_sprite'){assert.equal(stats(p).components8.length,1,`${a.id} ${v.state} disconnected`);assert(stats(p).transparentMargin,`${a.id} ${v.state} margin`);assert(pub.include.some(i=>i.file===v.file&&i.sha256===v.sha256))}
  }
}
for(const s of JSON.parse(await fs.readFile(path.join(root,'art/weapon-proportions/large-sword-version-selection.json'),'utf8')).results)assert.equal(hash(await fs.readFile(path.join(root,s.file))),s.sha256);
validation.staticPass=true;
if(process.argv.includes('--static')){await fs.writeFile(path.join(out,'validation.json'),JSON.stringify(validation,null,2)+'\n');console.log('PASS 42 entries, 94 own PNGs, connected silhouettes, opaque grips, hashes and legacy models');process.exit(0)}
const {chromium}=require('playwright'),browser=await chromium.launch({channel:'msedge',headless:true}),checks=[];
try{
  for(const viewport of [{width:1440,height:1080},{width:390,height:844}]){
    const page=await browser.newPage({viewport}),errors=[];page.on('pageerror',e=>errors.push(String(e)));
    await page.goto(`file:///${out.replaceAll('\\','/')}/preview.html`);await page.waitForFunction(()=>window.EQUIPMENT_QA?.ready());
    assert.equal(await page.locator('.item').count(),42);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
    assert(await page.locator('.s16').evaluateAll(xs=>xs.every(x=>x.getBoundingClientRect().width===16)));assert(await page.locator('.s32').evaluateAll(xs=>xs.every(x=>x.getBoundingClientRect().width===32)));
    await page.screenshot({path:path.join(out,`full-${viewport.width}-light.png`),fullPage:true});
    if(viewport.width===1440){
      // A review-only sheet uses the already-loaded assets, not a second renderer or runtime route.
      await page.evaluate(()=>{
        const sheet=document.createElement('div');sheet.id='review-sheet';sheet.style.cssText='display:grid;grid-template-columns:repeat(5,1fr);gap:16px;padding:20px;background:#edf0ee;color:#222b29';
        for(const a of window.EQUIPMENT.filter(a=>['great_bow','ballista','test_flask','sacred_relic_sword','katana','greataxe','great_hammer','crossbow'].includes(a.id)))for(const v of a.variants.filter(v=>!v.state.endsWith('blocking')&&!v.state.endsWith('throwing'))){
          const cell=document.createElement('div'),label=document.createElement('div'),canvas=document.createElement('canvas');canvas.width=128;canvas.height=128;canvas.style.imageRendering='pixelated';const ctx=canvas.getContext('2d');ctx.imageSmoothingEnabled=false;ctx.drawImage(v.image,0,0,128,128);ctx.strokeStyle='#e84270';const x=v.anchorPixels[0]*128/v.size[0],y=v.anchorPixels[1]*128/v.size[1];ctx.beginPath();ctx.moveTo(x-5,y);ctx.lineTo(x+5,y);ctx.moveTo(x,y-5);ctx.lineTo(x,y+5);ctx.stroke();label.textContent=a.name+' · '+v.state;cell.append(canvas,label);sheet.append(cell);
        }document.body.prepend(sheet);
      });
      await page.locator('#review-sheet').screenshot({path:path.join(out,'states-and-grips.png')});await page.locator('#review-sheet').evaluate(e=>e.remove());
    }
    await page.locator('[data-theme=dark]').click();await page.screenshot({path:path.join(out,`full-${viewport.width}-dark.png`),fullPage:true});
    await page.locator('#scale').check();await page.screenshot({path:path.join(out,`full-${viewport.width}-scale.png`),fullPage:true});
    const right=hash(await page.locator('#detail .stage').screenshot());await page.locator('[value=left]').check();await page.locator('#slim').check();assert.notEqual(hash(await page.locator('#detail .stage').screenshot()),right);
    const stateChecks=[];
    for(const id of ['short_bow','long_bow','crossbow','great_bow','ballista','shield','small_shield','great_shield']){
      await page.evaluate(id=>window.EQUIPMENT_QA.select(id),id);const states=await page.locator('#state option').evaluateAll(xs=>xs.map(x=>x.value)),captures=[];
      for(const state of states){await page.locator('#state').selectOption(state);captures.push(hash(await page.locator('#detail .stage').screenshot()))}
      assert.equal(new Set(captures).size,states.length,`${id} duplicate rendered states`);stateChecks.push({id,states:states.length});
    }
    await page.evaluate(()=>window.EQUIPMENT_QA.select('sacred_relic_sword'));await page.screenshot({path:path.join(out,`full-${viewport.width}-left-slim.png`),fullPage:false});
    // file:// provider images taint browser canvases; inspect captured pixels instead.
    for(const canvas of await page.locator('.item .stage').all()){
      const p=PNG.sync.read(await canvas.screenshot()),colors=new Set();for(let i=0;i<p.data.length;i+=4)colors.add(p.data.subarray(i,i+3).join(','));assert(colors.size>7,'Blank canvas');
    }
    assert.deepEqual(errors,[]);checks.push({viewport,noOverflow:true,native16And32:true,states:stateChecks,bothHands:true,nonblankCanvas:true});await page.close();
  }
}finally{await browser.close()}
validation.browserVerified=true;validation.browserChecks=checks;await fs.writeFile(path.join(out,'validation.json'),JSON.stringify(validation,null,2)+'\n');
console.log('PASS static assets, desktop/mobile, 16/32 native slots, all ranged/shield states, both hands, nonblank canvas. Human/game acceptance remains pending.');
