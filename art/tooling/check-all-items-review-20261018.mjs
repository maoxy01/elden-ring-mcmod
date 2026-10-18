import fs from 'node:fs/promises';import path from 'node:path';import {fileURLToPath,pathToFileURL} from 'node:url';import {createRequire} from 'node:module';import assert from 'node:assert/strict';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),dir=path.join(root,'art/equipment-2d/blockbench-all-items-20261018');
const d=JSON.parse(await fs.readFile(path.join(dir,'manifest.json'))),require=createRequire('C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json');
const {chromium}=require('playwright'),browser=await chromium.launch({channel:'msedge',headless:true,args:['--allow-file-access-from-files']});const errors=[],checks=[];
try{
 const page=await browser.newPage();page.on('pageerror',e=>errors.push(e.message));
 const wait=async(id,state,view)=>{await page.waitForFunction(key=>window.REVIEW_READY===key,id+':'+state+':'+view);assert.equal(await page.evaluate(()=>Boolean(window.REVIEW_ERROR)),false);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);await page.locator('#pair img').evaluateAll(imgs=>Promise.all(imgs.map(i=>i.decode())))};
 for(const width of [1440,768,390]){
  await page.setViewportSize({width,height:width===390?844:1000});await page.goto(pathToFileURL(path.join(dir,'review.html')).href);await page.waitForFunction(()=>window.REVIEW_READY);
  for(const a of d.assets){
   await page.selectOption('#item',a.id);await wait(a.id,a.variants[0].state,'native');
   await page.selectOption('#view','oblique');await wait(a.id,a.variants[0].state,'oblique');
   await page.selectOption('#view','texture');await wait(a.id,a.variants[0].state,'texture');
   await page.selectOption('#view','proportion');await wait(a.id,a.variants[0].state,'proportion');
   assert.equal(await page.locator('#pair canvas').count(),2);await page.locator('#left').check();await page.locator('#dark').check();await wait(a.id,a.variants[0].state,'proportion');
   await page.locator('#left').uncheck();await page.locator('#dark').uncheck();
   await page.selectOption('#view','native');await wait(a.id,a.variants[0].state,'native');
   for(const v of [...a.variants,...a.gui]){await page.selectOption('#state',v.state);await wait(a.id,v.state,'native');await page.selectOption('#view','texture');await wait(a.id,v.state,'texture');await page.selectOption('#view','native');await wait(a.id,v.state,'native')}
   checks.push({width,id:a.id,states:a.variants.length+a.gui.length,native:true,oblique:true,texture:true,proportion:true});
  }
  for(const [value,count] of [['1',8],['2',12],['3',8],['all',28]]){await page.selectOption('#batch',value);assert.equal(await page.locator('#gallery button').count(),count)}
  await page.selectOption('#item',width===390?'morgotts_cursed_sword':width===768?'shield':'sacred_relic_sword');await page.waitForTimeout(150);await page.locator('#pair img').evaluateAll(imgs=>Promise.all(imgs.map(i=>i.decode())));await page.screenshot({path:path.join(dir,'qa/review-'+width+'.png')});
 }
 await page.setViewportSize({width:1440,height:1000});
 for(const batch of ['1','2','3']){
  await page.selectOption('#batch',batch);await page.locator('#gallery img').evaluateAll(imgs=>Promise.all(imgs.map(i=>{i.src=i.src.replace('-front.png','-oblique.png');return i.decode()})));await page.locator('#gallery').screenshot({path:path.join(dir,'qa/side-batch'+batch+'.png')});
 }
 await page.locator('#gallery button').evaluateAll(buttons=>buttons.forEach(b=>{if(!['great_shield','shield','small_shield'].includes(b.dataset.id))b.remove()}));
 await page.locator('#gallery img').evaluateAll(imgs=>Promise.all(imgs.map(i=>{i.src=i.src.replace('-oblique.png','-back.png');return i.decode()})));await page.locator('#gallery').screenshot({path:path.join(dir,'qa/backs-sheet.png')});
 assert.deepEqual(errors,[]);
}finally{await browser.close()}
await fs.writeFile(path.join(dir,'review-validation.json'),JSON.stringify({viewports:[1440,768,390],itemsPerViewport:28,statesPerViewport:94,checks,browserErrors:errors,nativeScreenshotsOnly:true,humanAccepted:false,runtimeIntegrated:false},null,2)+'\n');console.log('PASS 28 items / 94 states at 1440, 768, 390; native/oblique/texture/proportion, left/dark and batch filters; no script errors or overflow.');
