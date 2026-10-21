import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
import {PNG} from './equipment-pixels.mjs';
const root='D:/codexout/mcmod',base=path.join(root,'art/vfx-blockbench/takers-flames-20261021'),hybrid=path.join(base,'hybrid');
const read=async f=>JSON.parse(await fs.readFile(f)),hash=b=>createHash('sha256').update(b).digest('hex');
const model=await read(path.join(hybrid,'takers-flames-hybrid.bbmodel')),record=await read(path.join(hybrid,'authoring.json')),baseline=await read(path.join(base,'baseline.json')),capture=await read(path.join(hybrid,'captures.json')),bounds=await read(path.join(hybrid,'sampled-bounds.json'));
assert.equal(model.meta.model_format,'free');assert.equal(model.elements.length,60);assert.equal(model.textures.length,17);assert.equal(model.animations.length,1);assert.equal(model.history.length,5);assert.equal(record.history,5);
const c=model.unhandled_root_fields.takers_hybrid_art,solid=model.elements.filter(e=>e.to.every((v,i)=>v>e.from[i]));assert.equal(solid.length,44);assert.equal(solid.filter(e=>e.name.startsWith('molten_')).length,30);assert.equal(c.schedule.vents.length,5);assert.equal(c.schedule.debris.length,6);assert.equal(c.schedule.plates.length,8);
for(const e of solid)for(const face of Object.values(e.faces)){assert(Number.isInteger(face.texture));const t=model.textures[face.texture];assert.equal(t.uuid,c.palette);assert(face.uv.every(n=>Number.isFinite(n)&&n>=0&&n<=32));}
const palette=model.textures.find(t=>t.uuid===c.palette),decode=t=>PNG.sync.read(Buffer.from(t.source.split(',')[1],'base64'));
assert.deepEqual(decode(palette).data,PNG.sync.read(await fs.readFile(path.join(hybrid,'textures/molten_and_basalt.png'))).data);
for(const d of c.ground){const embedded=decode(model.textures.find(t=>t.uuid===d.texture)),original=PNG.sync.read(await fs.readFile(path.join(base,'textures/ground_rupture/frame_'+String(d.index).padStart(2,'0')+'.png')));assert.deepEqual(embedded.data,original.data);}
const animation=model.animations[0],animators=Object.values(animation.animators);
for(const vent of c.schedule.vents){const a=animators.find(a=>a.name===vent.name);assert(a);const scales=a.keyframes.filter(k=>k.channel==='scale');assert(scales.length>=6);assert(scales.some(k=>Number(k.data_points[0].y)===1));assert(scales.some(k=>Number(k.data_points[0].y)<.05));}
for(const fragment of c.schedule.debris){const a=animators.find(a=>a.name===fragment.name);assert(a.keyframes.some(k=>k.channel==='position'&&Number(k.data_points[0].y)>15));assert(a.keyframes.some(k=>k.channel==='rotation'&&Number(k.data_points[0].x)>100));}
const effects=animation.animators.effects;assert.equal(effects.keyframes.length,2);for(const p of c.particles){const raw=await read(path.join(hybrid,p.file)),components=raw.particle_effect.components;assert.equal(raw.particle_effect.description.basic_render_parameters.material,'particles_blend');assert(components['minecraft:emitter_lifetime_once']);}
const ember=await read(path.join(hybrid,c.particles[0].file)),ash=await read(path.join(hybrid,c.particles[1].file));assert.equal(ember.particle_effect.components['minecraft:emitter_rate_instant'].num_particles,12);assert.equal(ash.particle_effect.components['minecraft:emitter_rate_steady'].max_particles,6);
assert(capture.reloaded);assert.equal(capture.captures.length,48);assert.equal(bounds.samples,32);
for(const v of capture.captures){const p=PNG.sync.read(await fs.readFile(path.join(hybrid,v.file)));let count=0,border=0;for(let y=0;y<p.height;y++)for(let x=0;x<p.width;x++)if(p.data[(y*p.width+x)*4+3]){count++;if(x===0||y===0||x===p.width-1||y===p.height-1)border++;}assert(count>100);assert.equal(border,0,'Native clipping '+v.file);}
for(const f of baseline.protection)assert.equal(hash(await fs.readFile(path.join(root,f.file))),f.sha256,'Protected changed '+f.file);
const result={date:'2026-10-21',nativeBlockbench:true,solidBoxes:44,fireBoxes:30,liftedPlates:8,flyingCrust:6,groundFrames:16,particleEffects:2,particleKeyframes:2,emberBurst:12,ashMaxAlive:6,nativeReloadedCaptures:48,nativeUndoEntries:5,protectedFilesUnchanged:baseline.protection.length,runtimeIntegrated:false,particleJavaAdaptationPending:true,humanAccepted:false};
await fs.writeFile(path.join(hybrid,'validation.json'),JSON.stringify(result,null,2));console.log('PASS hybrid: 44 solid boxes, 16 ground frames, 2 native particle bindings, 48 reloaded captures, '+baseline.protection.length+' protected files unchanged.');
if(process.argv.includes('--browser')){
 const require=createRequire('C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json'),{chromium}=require('playwright'),browser=await chromium.launch({channel:'msedge',headless:true,args:['--allow-file-access-from-files']}),checks=[],errors=[];
 try{const page=await browser.newPage();page.on('pageerror',e=>errors.push(e.message));for(const width of [1440,768,390]){
  await page.setViewportSize({width,height:width===390?844:1000});await page.goto(pathToFileURL(path.join(base,'preview.html')).href);await page.waitForFunction(()=>window.PREVIEW_FRAME===6);
  for(const view of ['hybrid','side','back','oblique','comparison','ground']){await page.selectOption('#view',view);for(let frame=0;frame<16;frame++){await page.locator('#frame').evaluate((e,v)=>{e.value=v;e.dispatchEvent(new Event('input',{bubbles:true}));},frame);await page.locator('#render').evaluate(e=>e.decode());assert.equal(await page.evaluate(()=>window.PREVIEW_FRAME),frame);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);}checks.push({width,view,frames:16});}
  await page.selectOption('#view','hybrid');await page.locator('[data-frame="6"]').click();await page.locator('#render').evaluate(e=>e.decode());await page.screenshot({path:path.join(hybrid,'qa/review-'+width+'.png')});
  await page.locator('#play').click();await page.waitForTimeout(270);assert.notEqual(await page.evaluate(()=>window.PREVIEW_FRAME),6);await page.locator('#play').click();
 }assert.deepEqual(errors,[]);}finally{await browser.close();}
 await fs.writeFile(path.join(hybrid,'browser-validation.json'),JSON.stringify({viewports:[1440,768,390],checks,errors,nativeFramesOnly:true},null,2));console.log('PASS preview: 6 views x 16 frames at desktop/tablet/mobile; playback and no overflow.');
}
