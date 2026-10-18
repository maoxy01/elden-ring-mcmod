import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {PNG,stats,opaque} from './equipment-pixels.mjs';
import {selectionFile,assertSelectedArt} from './selected-equipment.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const full='art/equipment-2d/full/',batch='art/equipment-2d/identity-batch-two/',thickness='art/item-thickness/manifest.json';
const read=async f=>JSON.parse(await fs.readFile(path.join(root,f),'utf8')),hash=b=>createHash('sha256').update(b).digest('hex');
const write=async(f,d)=>fs.writeFile(path.join(root,f),JSON.stringify(d,null,2)+'\n');
const existing=await assertSelectedArt(),malenia=process.argv.includes('--malenia');
if(malenia){assert(existing,'Promote the first three selections before Malenia');assert(!existing.assets.some(a=>a.id==='hand_of_malenia'),'Malenia already selected')}else if(existing)throw new Error('Selection already registered; verify it instead of applying again.');
const ids=malenia?['hand_of_malenia']:['flail','heavy_thrusting_sword','malikeths_black_blade'],receiptFile=batch+(malenia?'malenia-selection.json':'selection.json');
const d=await read(batch+'manifest.json'),data=await read(full+'equipment-42.json'),pub=await read(full+'publishable-assets.json'),t=await read(thickness),validation=await read(full+'validation.json');
const backup=batch+'selection-backup-'+Date.now(),before=new Map(),changes=[];
for(const f of d.protectedFiles){const bytes=await fs.readFile(path.join(root,f.file));assert.equal(hash(bytes),f.sha256,'Protected baseline changed: '+f.file);before.set(f.file,f.sha256)}
for(const f of ['equipment-42.json','publishable-assets.json','preview-data.js','validation.json','preview.html','preview.js','display-candidates.json'])before.set(full+f,hash(await fs.readFile(path.join(root,full+f))));
for(const f of [thickness,batch+'manifest.json',batch+'preview-data.js',...d.models.map(m=>batch+m.file)])before.set(f,hash(await fs.readFile(path.join(root,f))));
for(const m of t.models)before.set('art/item-thickness/'+m.file,hash(await fs.readFile(path.join(root,'art/item-thickness/'+m.file))));
for(const a of d.candidates)for(const v of a.variants)before.set(batch+v.file,hash(await fs.readFile(path.join(root,batch+v.file))));
for(const id of ids){
 const a=d.candidates.find(a=>a.id===id),target=data.assets.find(a=>a.id===id);assert.equal(a.worldLengthBlocks,target.worldLengthBlocks);assert.deepEqual(a.anchorPixels,target.anchorPixels);
 assert.equal(a.sourceSpanPixels,target.sourceSpanPixels);assert.deepEqual(a.suggestedDisplay,target.suggestedDisplay);
 for(const v of a.variants){
  const original=[...target.variants,...target.gui].find(x=>x.state===v.state);assert(original);assert.deepEqual(v.anchorPixels,original.anchorPixels);assert.deepEqual(v.size,original.size);
  const bytes=await fs.readFile(path.join(root,batch+v.file)),previous=await fs.readFile(path.join(root,full+original.file));assert.equal(hash(bytes),v.sha256);assert.equal(hash(previous),original.sha256);
  const p=PNG.sync.read(bytes);assert.deepEqual([p.width,p.height],v.size);assert(opaque(p,...v.anchorPixels));assert.equal(stats(p).components8.length,1);
  const model=t.models.find(m=>m.id==='eldencombat:'+id&&m.state===v.state);assert(model);assert.equal(model.textures.length,1);assert.equal(model.textures[0].source,full+original.file);assert.equal(model.thicknessUnits,1);
  assert.equal(hash(await fs.readFile(path.join(root,'art/item-thickness/'+model.file))),model.sha256);
  changes.push({id,state:v.state,file:full+original.file,candidate:batch+v.file,sha256:v.sha256,previousSha256:hash(previous)});
 }
}
assert.equal(changes.length,malenia?3:9);
// Preserve every pre-existing file this operation will write, before the first replacement.
for(const f of [full+'equipment-42.json',full+'publishable-assets.json',full+'preview-data.js',full+'validation.json',full+'preview.html',full+'preview.js',thickness,batch+'manifest.json',batch+'preview-data.js',...(existing?[selectionFile]:[]),...changes.map(f=>f.file)]){
 const dest=path.join(root,backup,f);await fs.mkdir(path.dirname(dest),{recursive:true});await fs.copyFile(path.join(root,f),dest,fs.constants.COPYFILE_EXCL);
}
const authorization=malenia?{date:'2026-10-16',threadId:'01a12894-a556-7953-be25-8d8c1f761036',quote:'采用修改后的义手刀',scope:'Latest revised Malenia art only; not runtime integration or game acceptance'}:{date:'2026-10-16',threadId:'01a12894-a556-7953-be25-8d8c1f761036',messageId:'01a14361-3bca-7d03-8912-0b45168025c1',quote:'连枷，重刺剑，黑剑采用候选，不需要改了',scope:'Art selection only; not runtime integration or game acceptance'};
const selection=existing||{date:'2026-10-16',authorization,backup,assets:[],runtimeIntegrated:false,inGameAccepted:false};
if(existing)(selection.revisions??=[]).push({authorization,backup,receipt:receiptFile});
for(const id of ids){
 const a=d.candidates.find(a=>a.id===id),target=data.assets.find(a=>a.id===id);
 for(const v of a.variants){
  const original=[...target.variants,...target.gui].find(x=>x.state===v.state),change=changes.find(f=>f.id===id&&f.state===v.state);
  await fs.copyFile(path.join(root,change.candidate),path.join(root,change.file));
  for(const k of ['sha256','boundsInclusive','opaquePixels','colors','components8','transparentMargin'])original[k]=v[k];
  const p=pub.include.find(p=>p.file===original.file);assert(p);p.sha256=v.sha256;
  t.models.find(m=>m.id==='eldencombat:'+id&&m.state===v.state).textures[0].sha256=v.sha256;
 }
 target.artRevision={date:'2026-10-16',status:'user_selected_art_current',source:batch+'manifest.json',authorization,features:a.features,runtimeIntegrated:false,inGameAccepted:false};
 a.status='user_selected_art_current';a.selectionRegistry=selectionFile;
 selection.assets.push({id,authorization,worldLengthBlocks:target.worldLengthBlocks,anchorPixels:target.anchorPixels,files:changes.filter(f=>f.id===id)});
}
d.publication.selection=malenia?'All four user-selected candidates synchronized byte-for-byte to full art. No runtime admission.':'Three user-selected candidates synchronized byte-for-byte to full art; Hand of Malenia remains pending review. No runtime admission.';
validation.browserVerified=false;validation.visualReview=false;validation.previousBrowserChecksAreHistorical=true;validation.lastArtRevision='2026-10-16';validation.revisionPreview=batch+'manifest.json';
await write(full+'equipment-42.json',data);await write(full+'publishable-assets.json',pub);await write(full+'validation.json',validation);await write(thickness,t);await write(selectionFile,selection);
await fs.writeFile(path.join(root,full+'preview-data.js'),'window.EQUIPMENT = '+JSON.stringify(data.assets)+';\n');
const transitions=[];
for(const f of d.protectedFiles){const actual=hash(await fs.readFile(path.join(root,f.file)));if(actual!==f.sha256){transitions.push({file:f.file,before:f.sha256,after:actual});f.sha256=actual}}
const allowed=new Set([full+'equipment-42.json',full+'publishable-assets.json',...(existing?[selectionFile]:[]),...changes.map(f=>f.file)]);assert(transitions.every(f=>allowed.has(f.file)));
if(!existing)d.protectedFiles.push({file:selectionFile,sha256:hash(await fs.readFile(path.join(root,selectionFile)))});
if(d.validation.protectionBaselineRevision)(d.validation.protectionBaselineHistory??=[]).push(d.validation.protectionBaselineRevision);
d.validation.protectedUnchanged=false;d.validation.protectionBaselineRevision={reason:'Explicit user selection promoted to current art',receipt:receiptFile,changedFiles:transitions};
d.validation.sourceHashScope='candidates_4_and_protected_snapshots_not_42_asset_audit';d.validation.sourceAuditEntriesChecked=0;
await write(batch+'manifest.json',d);await fs.writeFile(path.join(root,batch+'preview-data.js'),'window.IDENTITY='+JSON.stringify(d)+';\n');
const expectedChanged=new Set([...allowed,full+'validation.json',full+'preview-data.js',thickness,batch+'manifest.json',batch+'preview-data.js']);
for(const [f,sha] of before)if(!expectedChanged.has(f))assert.equal(hash(await fs.readFile(path.join(root,f))),sha,'Unrelated file changed: '+f);
await assertSelectedArt();
await write(receiptFile,{date:'2026-10-16',authorization,backup,selectedArtFiles:changes,protectedTransitions:transitions,beforeSnapshot:[...before].map(([file,sha256])=>({file,sha256})),expectedChangedFiles:[...expectedChanged],runtimeIntegrated:false,inGameAccepted:false});
console.log('PASS '+changes.length+' selected PNGs promoted byte-for-byte; full/whitelist/thickness texture hashes synchronized; unrelated snapshots unchanged. Backup: '+backup);
