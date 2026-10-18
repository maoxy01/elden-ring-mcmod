import fs from 'node:fs/promises';import path from 'node:path';import {fileURLToPath,pathToFileURL} from 'node:url';import {createRequire} from 'node:module';import {createHash} from 'node:crypto';import assert from 'node:assert/strict';import {PNG,stats,opaque} from './equipment-pixels.mjs';
import {assertSelectedArt} from './selected-equipment.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),dir=path.join(root,'art/equipment-2d/taste-weapons-20261018');const read=f=>fs.readFile(path.join(dir,f)),hash=b=>createHash('sha256').update(b).digest('hex');const d=JSON.parse(await read('manifest.json'));
let selectionChecks=null;
if(d.humanAccepted){
 const selection=await assertSelectedArt(),receipt=JSON.parse(await read('selection.json'));
 const full=JSON.parse(await fs.readFile(path.join(root,'art/equipment-2d/full/equipment-42.json')));
 const pub=JSON.parse(await fs.readFile(path.join(root,'art/equipment-2d/full/publishable-assets.json')));
 const t=JSON.parse(await fs.readFile(path.join(root,'art/item-thickness/manifest.json')));
 const display=JSON.parse(await fs.readFile(path.join(root,'art/equipment-2d/full/display-candidates.json')));
 const changed=new Set(receipt.expectedChangedFiles);
 assert.equal(receipt.selectedArtFiles.length,15);assert.equal(full.assets.length,42);assert.equal(pub.include.length,94);
 for(const f of receipt.beforeSnapshot){
  if(changed.has(f.file))assert.equal(hash(await fs.readFile(path.join(root,receipt.backup,f.file))),f.sha256,'Backup: '+f.file);
  else assert.equal(hash(await fs.readFile(path.join(root,f.file))),f.sha256,'Unrelated: '+f.file);
 }
 for(const a of full.assets){
  assert.equal(hash(await fs.readFile(path.join(root,a.source))),a.sourceHash);
  for(const v of [...a.variants,...a.gui]){
   const f=a.delivery==='provider_reference_only'?v.file:'art/equipment-2d/full/'+v.file;
   const b=await fs.readFile(path.join(root,f));assert.equal(hash(b),v.sha256);const p=PNG.sync.read(b);
   assert(opaque(p,...v.anchorPixels));
   if(a.delivery==='own_sprite'){
    assert(pub.include.some(x=>x.file===v.file&&x.sha256===v.sha256));
    if(d.candidates.some(c=>c.id===a.id)){assert.equal(stats(p).components8.length,1);assert(stats(p).transparentMargin)}
   }
  }
 }
 for(const a of d.candidates){
  const target=full.assets.find(x=>x.id===a.id);assert(selection.assets.some(x=>x.id===a.id));
  assert.equal(target.sourceSpanPixels,a.sourceSpanPixels);assert.equal(target.worldLengthBlocks,a.worldLengthBlocks);assert.deepEqual(target.anchorPixels,a.anchorPixels);
  assert.equal(target.suggestedDisplay.uniformXYScale,target.worldLengthBlocks*target.variants[0].size[0]/target.sourceSpanPixels);
  assert.equal(display.items[a.id].status,'superseded_by_selected_art_requires_recalibration');
  for(const v of a.variants){
   assert.equal(hash(await fs.readFile(path.join(root,'art/equipment-2d/full',v.file))),v.sha256);
   const m=t.models.find(x=>x.id==='eldencombat:'+a.id&&x.state===v.state);assert.equal(m.textures[0].sha256,v.sha256);
   assert.equal(hash(await fs.readFile(path.join(root,'art/item-thickness',m.file))),m.sha256);
  }
 }
 selectionChecks={weapons:5,promotedPngs:15,totalProtectedSelections:selection.assets.length,fullEntries:42,whitelistedPngs:94,backupHashes:true,unrelatedSnapshotsUnchanged:true,geometryScope:'Five selected weapons only; all 42 entries checked for hashes and grips, not global binary alpha acceptance',existingUnchangedObservation:'morgotts_cursed_sword front contains one semi-transparent pixel; user-edited source preserved'};
}
for(const f of d.protectedFiles)assert.equal(hash(await fs.readFile(path.join(root,f.file))),f.sha256);
for(const a of d.candidates){for(const v of a.variants){const b=await read(v.file),p=PNG.sync.read(b),s=stats(p);assert.equal(hash(b),v.sha256);assert.equal(s.components8.length,1);assert(s.transparentMargin);assert(opaque(p,...v.anchorPixels));assert.equal(hash(await read(v.before)),v.beforeHash)}const m=JSON.parse(await read(a.model));assert(m.elements.length);assert(m.textures[0].source.startsWith('data:image/png;base64,'));for(const e of m.elements){assert.equal(e.to[2]-e.from[2],1);for(const f of Object.values(e.faces))assert.equal(f.texture,0)}assert.equal(hash(Buffer.from(m.textures[0].source.split(',')[1],'base64')),a.variants[0].sha256)}
const require=createRequire('C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json');const {chromium}=require('playwright');const b=await chromium.launch({channel:'msedge',headless:true,args:['--allow-file-access-from-files']});const errors=[],shots=[];await fs.mkdir(path.join(dir,'qa'),{recursive:true});
try{const p=await b.newPage();p.on('pageerror',e=>errors.push(e.message));for(const width of [1440,768,390]){await p.setViewportSize({width,height:width===390?844:1000});await p.goto(pathToFileURL(path.join(dir,'preview.html')).href);await p.waitForFunction(()=>window.WEAPON_READY);for(const a of d.candidates){await p.selectOption('#item',a.id);await p.waitForFunction(name=>document.querySelector('#name').textContent===name,a.name);await p.waitForTimeout(80);assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);assert(await p.locator('.pair img').evaluateAll(imgs=>imgs.every(i=>i.complete&&i.naturalWidth)));const shot=`qa/${a.id}-${width}.png`;await p.screenshot({path:path.join(dir,shot)});shots.push(shot)}await p.locator('#dark').check();await p.locator('#left').check();await p.waitForTimeout(100);await p.screenshot({path:path.join(dir,`qa/left-dark-${width}.png`)});assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false)}assert.deepEqual(errors,[])}finally{await b.close()}
await fs.writeFile(path.join(dir,d.humanAccepted?'selection-validation.json':'validation.json'),JSON.stringify({date:'2026-10-18',candidates:5,pngs:15,bbmodels:5,baseThicknessUnits:1,protectionBaselineFilesVerified:d.protectedFiles.length,protectionTransitions:d.validation.protectionBaselineRevision?.changedFiles||[],selectionChecks,staticPass:true,browserPass:true,viewports:[1440,768,390],screenshots:shots,browserErrors:errors,authorVisualReview:false,runtimeIntegrated:false,humanAccepted:d.humanAccepted,humanAcceptanceScope:d.humanAcceptanceScope,inGameAccepted:false},null,2)+'\n');console.log('PASS 15 PNGs / 5 bbmodels / '+d.protectedFiles.length+' protected baseline files / five weapons at three widths; art selection: '+Boolean(d.humanAccepted));
