import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
import {PNG} from './equipment-pixels.mjs';
const root='D:/codexout/mcmod',folder=path.join(root,'art/ui-pixel/hud-v02-20261023');
const read=f=>JSON.parse(fs.readFileSync(path.join(folder,f))),hash=b=>createHash('sha256').update(b).digest('hex');
const manifest=read('manifest.json');
for(const a of manifest.assets){const bytes=fs.readFileSync(path.join(folder,a.file)),p=PNG.sync.read(bytes);assert.equal(hash(bytes),a.sha256);assert.equal(p.width,a.width);assert.equal(p.height,a.height);assert(p.data.some((v,i)=>i%4===3&&v===255));assert(p.data.every((v,i)=>i%4!==3||v===0||v===255));}
for(const p of read('protection.json'))assert.equal(hash(fs.readFileSync(path.join(root,p.file))),p.sha256,'Protected changed '+p.file);
const require=createRequire('C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json'),{chromium}=require('playwright');
const browser=await chromium.launch({channel:'msedge',headless:true,args:['--allow-file-access-from-files']}),checks=[],errors=[];
try{
 const page=await browser.newPage();page.on('pageerror',e=>errors.push(e.message));
 for(const width of [1440,768,390]){
  await page.setViewportSize({width,height:width===390?844:1000});await page.goto(pathToFileURL(path.join(folder,'preview.html')).href);await page.waitForFunction(()=>window.HUD_READY||window.HUD_ERROR);assert.equal(await page.evaluate(()=>window.HUD_ERROR),undefined);
  for(const preset of ['reference','low','explore','underwater','multi']){
   await page.selectOption('#preset',preset);const state=await page.evaluate(()=>({state:HUD.state,layout:HUD_LAYOUT}));
   const b=state.layout.boxes;for(const key of ['fpLabel','staminaLabel','fpValue','staminaValue'])assert.equal(b[key],undefined);assert(b.item.y+b.item.height<=b.fp.y-4,'Item name overlaps resource bar');const fpGap=state.layout.armorY-(b.fp.y+b.fp.height),healthGap=state.layout.hpY-(state.layout.rows-1)*10-(state.layout.armorY+9);assert.equal(fpGap,healthGap,'FP/armor gap differs from armor/health gap');assert.equal(fpGap,2);assert.equal(b.sharedRight.y,state.layout.hpY-10,'Stamina must use original oxygen row');assert.equal(b.sharedRight.y,state.layout.airY);
   if(state.state.underwater){assert.equal(b.stamina,undefined);assert.deepEqual(b.oxygen,b.sharedRight);}else{assert.equal(b.oxygen,undefined);assert.equal(b.stamina.x,b.sharedRight.x);assert.equal(b.stamina.y,b.sharedRight.y);}assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
   if(preset==='low'){assert.equal(state.state.fp,8);assert.equal(state.state.stamina,0);}if(preset==='multi')assert.equal(state.layout.rows,3);checks.push({width,preset,overflow:false});
  }
  await page.selectOption('#preset','reference');await page.screenshot({path:path.join(folder,'qa/preview-'+width+'.png')});
  await page.locator('[data-view="hud"]').click();await page.screenshot({path:path.join(folder,'qa/hud-detail-'+width+'.png')});await page.locator('[data-view="full"]').click();
  for(const viewport of ['640,270','360,270','320,180','270,480']){
   await page.selectOption('#viewport',viewport);const L=await page.evaluate(()=>HUD_LAYOUT),b=L.boxes;assert(b.hud.x>=0&&b.hud.x+b.hud.width<=L.W);if(b.map&&b.boss){const overlap=b.map.x<b.boss.x+b.boss.width&&b.boss.x<b.map.x+b.map.width&&b.map.y<b.boss.y+b.boss.height&&b.boss.y<b.map.y+b.map.height;assert(!overlap,'Map/boss overlap');}checks.push({width,viewport,mapBossOverlap:false});
  }
 }
 await page.setViewportSize({width:1440,height:1000});await page.selectOption('#viewport','480,270');await page.selectOption('#preset','reference');
 await page.locator('#fp').fill('23');await page.locator('#fp').dispatchEvent('input');assert.equal(await page.evaluate(()=>HUD.state.fp),23);await page.selectOption('#equipment','5');assert.equal(await page.evaluate(()=>HUD.state.selected),5);
 await page.locator('#showMap').uncheck();assert.equal(await page.evaluate(()=>HUD_LAYOUT.boxes.map),undefined);await page.locator('#showMap').check();
 for(const view of ['map','boss','hud','full']){await page.locator('[data-view="'+view+'"]').click();assert.equal(await page.evaluate(()=>HUD.state.view),view);}
 await page.evaluate(()=>HUD.update({nameOverride:'超长武器名称测试玛莉卡的铁锤与被诅咒的长剑'}));assert((await page.evaluate(()=>HUD_LAYOUT.boxes.item.width))<=182);
 await page.selectOption('#preset','reference');await page.evaluate(()=>HUD.update({nameOverride:'',selected:0}));
 const hit=await page.locator('#preview').evaluate(c=>{const r=c.getBoundingClientRect(),L=HUD_LAYOUT;return {x:r.left+(L.left+3*20+10-L.crop.x)*r.width/L.crop.width,y:r.top+(L.hotbarY+10-L.crop.y)*r.height/L.crop.height};});await page.mouse.click(hit.x,hit.y);assert.equal(await page.evaluate(()=>HUD.state.selected),3);await page.evaluate(()=>HUD.update({selected:0}));
 const transparent=await page.evaluate(()=>HUD.exportDataURL());fs.writeFileSync(path.join(folder,'qa/hud-overlay-480.png'),Buffer.from(transparent.split(',')[1],'base64'));const overlay=PNG.sync.read(Buffer.from(transparent.split(',')[1],'base64'));assert.equal(overlay.data[3],0);assert(overlay.data.some((v,i)=>i%4===3&&v>0));
 const resourceBoxes=await page.evaluate(()=>[HUD_LAYOUT.boxes.fp,HUD_LAYOUT.boxes.stamina]);for(const box of resourceBoxes)for(let y=(box.y-12)*2;y<box.y*2;y++)for(let x=box.x*2;x<(box.x+box.width)*2;x++)assert.equal(overlay.data[(y*overlay.width+x)*4+3],0,'Resource label/value pixels remain');
 const game=await page.locator('#preview').evaluate(c=>c.toDataURL());fs.writeFileSync(path.join(folder,'qa/composite-480.png'),Buffer.from(game.split(',')[1],'base64'));
 const downloadPromise=page.waitForEvent('download');await page.locator('#export').click();const download=await downloadPromise;await download.saveAs(path.join(folder,'qa/eldencombat-hud-v02.png'));const exported=PNG.sync.read(fs.readFileSync(path.join(folder,'qa/eldencombat-hud-v02.png')));assert.equal(exported.width,1920);assert.equal(exported.height,1080);assert.equal(exported.data[3],0);
 await page.locator('[data-view="hud"]').click();await page.locator('#preview').screenshot({path:path.join(folder,'qa/hud-closeup.png')});
 const landLayout=await page.evaluate(()=>HUD_LAYOUT);await page.locator('#underwater').check();const waterLayout=await page.evaluate(()=>HUD_LAYOUT);
 for(const key of ['resourceY','staminaY','armorY','itemY','airY'])assert.equal(waterLayout[key],landLayout[key],'Underwater layout jumped: '+key);assert.deepEqual(waterLayout.boxes.oxygen,landLayout.boxes.sharedRight);assert.equal(waterLayout.boxes.stamina,undefined);assert.deepEqual(waterLayout.boxes.fp,landLayout.boxes.fp);
 await page.locator('#preview').screenshot({path:path.join(folder,'qa/hud-underwater-closeup.png')});
 const waterData=await page.evaluate(()=>HUD.exportDataURL()),water=PNG.sync.read(Buffer.from(waterData.split(',')[1],'base64')),slot=waterLayout.boxes.oxygen;let changedPixels=0;
 for(let y=0;y<water.height;y++)for(let x=0;x<water.width;x++){const i=(y*water.width+x)*4;if(water.data.subarray(i,i+4).equals(overlay.data.subarray(i,i+4)))continue;changedPixels++;assert(x>=slot.x*2&&x<(slot.x+slot.width)*2&&y>=slot.y*2&&y<(slot.y+slot.height)*2,'Underwater changed pixels outside shared slot');}assert(changedPixels>0,'Oxygen did not replace stamina');
 await page.evaluate(()=>HUD.update({stamina:0}));assert.equal(await page.evaluate(()=>HUD.exportDataURL()),waterData,'Empty stamina warning remains underwater');await page.evaluate(()=>HUD.update({stamina:120}));assert.equal(await page.evaluate(()=>HUD.exportDataURL()),waterData,'Stamina remains visible underwater');await page.locator('#underwater').uncheck();assert(await page.evaluate(()=>!!HUD_LAYOUT.boxes.stamina&&!HUD_LAYOUT.boxes.oxygen));await page.evaluate(()=>HUD.update({stamina:92,view:'full'}));
 assert.deepEqual(errors,[]);
}finally{await browser.close();}
for(const p of read('protection.json'))assert.equal(hash(fs.readFileSync(path.join(root,p.file))),p.sha256,'Protected changed '+p.file);
const result={date:'2026-10-23',originalSprites:manifest.assets.length,protectedFiles:read('protection.json').length,protectionPass:true,previewChecks:checks,errors,controlsPass:true,transparentExportPass:true,resourceTextHidden:true,staminaOxygenSharedSlot:true,underwaterStaminaHidden:true,underwaterLayoutStable:true,runtimeIntegrated:false,inGameAccepted:false};fs.writeFileSync(path.join(folder,'validation.json'),JSON.stringify(result,null,2));console.log(JSON.stringify({sprites:manifest.assets.length,checks:checks.length,protectedFiles:result.protectedFiles,controls:true,transparentExport:true,resourceTextHidden:true,staminaOxygenSharedSlot:true,underwaterStaminaHidden:true,errors}));
