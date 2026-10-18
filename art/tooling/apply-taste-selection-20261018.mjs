import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {PNG,stats,opaque} from './equipment-pixels.mjs';
import {selectionFile,assertSelectedArt} from './selected-equipment.mjs';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const full='art/equipment-2d/full/',batch='art/equipment-2d/taste-weapons-20261018/';
const thickness='art/item-thickness/manifest.json',receiptFile=batch+'selection.json';
const ids=['curved_sword','curved_greatsword','twinblade','marikas_hammer','mohgwyns_sacred_spear'];
const bytes=f=>fs.readFile(path.join(root,f)),read=async f=>JSON.parse(await bytes(f));
const hash=b=>createHash('sha256').update(b).digest('hex');
const write=(f,d)=>fs.writeFile(path.join(root,f),JSON.stringify(d,null,2)+'\n');
const selection=await assertSelectedArt();assert(selection);
assert(!selection.assets.some(a=>ids.includes(a.id)),'Already adopted; verify selection instead of rerunning.');
const d=await read(batch+'manifest.json'),data=await read(full+'equipment-42.json');
const pub=await read(full+'publishable-assets.json'),t=await read(thickness);
const display=await read(full+'display-candidates.json'),validation=await read(full+'validation.json');
const before=new Map(),changes=[],scaleChanges=[];
for(const f of d.protectedFiles){assert.equal(hash(await bytes(f.file)),f.sha256,'Protected baseline: '+f.file);before.set(f.file,f.sha256)}
assert.deepEqual(d.candidates.map(a=>a.id),ids);
for(const a of d.candidates){
 const target=data.assets.find(v=>v.id===a.id);assert(target);
 assert.equal(a.worldLengthBlocks,target.worldLengthBlocks);assert.deepEqual(a.anchorPixels,target.anchorPixels);
 assert.equal(a.beforeSourceSpanPixels,target.sourceSpanPixels);assert(a.sourceSpanPixels>0);
 for(const v of a.variants){
  const original=[...target.variants,...target.gui].find(vv=>vv.state===v.state);assert(original);
  assert.deepEqual(v.size,original.size);assert.deepEqual(v.anchorPixels,original.anchorPixels);
  const b=await bytes(batch+v.file),old=await bytes(full+original.file),p=PNG.sync.read(b),s=stats(p);
  assert.equal(hash(b),v.sha256);assert.equal(hash(old),original.sha256);assert.equal(hash(old),v.beforeHash);
  assert.equal(hash(await bytes(batch+v.before)),v.beforeHash);assert.deepEqual([p.width,p.height],v.size);
  assert.equal(s.components8.length,1);assert(s.transparentMargin);assert(opaque(p,...v.anchorPixels));
  const m=t.models.find(m=>m.id==='eldencombat:'+a.id&&m.state===v.state);assert(m);
  assert.equal(m.thicknessUnits,1);assert.equal(m.textures.length,1);assert.equal(m.textures[0].source,full+original.file);
  assert.equal(hash(await bytes('art/item-thickness/'+m.file)),m.sha256);
  assert(pub.include.some(x=>x.file===original.file&&x.sha256===original.sha256));
  changes.push({id:a.id,state:v.state,file:full+original.file,candidate:batch+v.file,sha256:v.sha256,previousSha256:hash(old)});
 }
}
assert.equal(changes.length,15);
const authorization={date:'2026-10-18',threadId:'01a12894-a556-7953-be25-8d8c1f761036',quote:'都采用新版',scope:'The five weapons in taste-weapons-20261018 only; art selection, not runtime admission or in-game acceptance'};
const backup=batch+'selection-backup-'+Date.now();
const metadata=[full+'equipment-42.json',full+'publishable-assets.json',full+'preview-data.js',full+'validation.json',full+'display-candidates.json',selectionFile,thickness,batch+'manifest.json',batch+'preview-data.js'];
const laterEdits=[batch+'preview.html','docs/ART-WEAPON-TASTE-REVISION-20261018.md','art/tooling/build-taste-weapons-20261018.mjs','art/tooling/verify-taste-weapons-20261018.mjs'];
const expected=new Set([...metadata,...laterEdits,...changes.map(f=>f.file)]);
// Back up every pre-existing output, including the review text edited after promotion.
for(const f of expected){const b=await bytes(f);before.set(f,hash(b));const dest=path.join(root,backup,f);await fs.mkdir(path.dirname(dest),{recursive:true});await fs.writeFile(dest,b,{flag:'wx'})}
for(const a of d.candidates){
 const target=data.assets.find(v=>v.id===a.id);
 for(const v of a.variants){
  const original=[...target.variants,...target.gui].find(vv=>vv.state===v.state),c=changes.find(c=>c.id===a.id&&c.state===v.state);
  await fs.copyFile(path.join(root,c.candidate),path.join(root,c.file));
  for(const k of ['sha256','boundsInclusive','opaquePixels','colors','components8','transparentMargin'])original[k]=v[k];
  pub.include.find(x=>x.file===original.file).sha256=v.sha256;
  t.models.find(m=>m.id==='eldencombat:'+a.id&&m.state===v.state).textures[0].sha256=v.sha256;
 }
 const oldSpan=target.sourceSpanPixels,sourceUnits=oldSpan*target.projection.modelUnitsPerPixel;
 target.sourceSpanPixels=a.sourceSpanPixels;
 target.projection.modelUnitsPerPixel=sourceUnits/a.sourceSpanPixels;
 target.suggestedDisplay.uniformXYScale=target.worldLengthBlocks*target.variants[0].size[0]/a.sourceSpanPixels;
 a.projection=structuredClone(target.projection);a.suggestedDisplay=structuredClone(target.suggestedDisplay);
 scaleChanges.push({id:a.id,beforeSourceSpanPixels:oldSpan,sourceSpanPixels:a.sourceSpanPixels,worldLengthBlocks:a.worldLengthBlocks,uniformXYScale:target.suggestedDisplay.uniformXYScale});
 target.artRevision={date:'2026-10-18',status:'user_selected_art_current',source:batch+'manifest.json',authorization,features:a.features,runtimeIntegrated:false,inGameAccepted:false};
 a.status='user_selected_art_current';a.selectionRegistry=selectionFile;
 selection.assets.push({id:a.id,authorization,worldLengthBlocks:a.worldLengthBlocks,anchorPixels:a.anchorPixels,files:changes.filter(c=>c.id===a.id)});
 // Historical pose proposals must not masquerade as recalibrated matrices for a new silhouette.
 const stale=display.items[a.id];assert(stale);
 stale.status='superseded_by_selected_art_requires_recalibration';stale.supersededBy=batch+'manifest.json';stale.verificationHistorical=true;
}
assert(ids.every(id=>display.items[id].status==='superseded_by_selected_art_requires_recalibration'));
(selection.revisions??=[]).push({authorization,backup,receipt:receiptFile});
d.humanAccepted=true;d.humanAcceptanceScope='Five art selections only; no in-game acceptance';d.selectionReceipt=receiptFile;
d.validation.protectedUnchanged=false;d.validation.protectionBaselineRevision={reason:'Explicit user selection',receipt:receiptFile};
validation.browserVerified=false;validation.visualReview=false;validation.previousBrowserChecksAreHistorical=true;
validation.lastArtRevision='2026-10-18';validation.revisionPreview=batch+'manifest.json';
await write(full+'equipment-42.json',data);await write(full+'publishable-assets.json',pub);await write(full+'display-candidates.json',display);
await write(full+'validation.json',validation);await write(thickness,t);await write(selectionFile,selection);
await fs.writeFile(path.join(root,full+'preview-data.js'),'window.EQUIPMENT = '+JSON.stringify(data.assets)+';\n');
const transitions=[];
for(const f of d.protectedFiles){const actual=hash(await bytes(f.file));if(actual!==f.sha256){assert(expected.has(f.file),'Unexpected change: '+f.file);transitions.push({file:f.file,before:f.sha256,after:actual});f.sha256=actual}}
d.validation.protectionBaselineRevision.changedFiles=transitions;
await write(batch+'manifest.json',d);await fs.writeFile(path.join(root,batch+'preview-data.js'),'window.IDENTITY='+JSON.stringify(d)+';\n');
for(const [f,h] of before)if(!expected.has(f))assert.equal(hash(await bytes(f)),h,'Unrelated file: '+f);
await assertSelectedArt();
await write(receiptFile,{date:'2026-10-18',authorization,backup,selectedArtFiles:changes,scaleChanges,protectedTransitions:transitions,beforeSnapshot:[...before].map(([file,sha256])=>({file,sha256})),expectedChangedFiles:[...expected],runtimeIntegrated:false,inGameAccepted:false});
console.log('PASS 5 adopted weapons, 15 PNGs; synchronized art and hashes; backup: '+backup);
