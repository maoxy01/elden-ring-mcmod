import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),review=path.join(root,'art/equipment-2d/shape-review'),full=path.join(root,'art/equipment-2d/full'),backup=path.join(review,'backup-before-selection');
const hash=b=>createHash('sha256').update(b).digest('hex'),read=async p=>JSON.parse(await fs.readFile(p,'utf8'));
const d=await read(path.join(review,'manifest.json')),data=await read(path.join(full,'equipment-42.json')),pub=await read(path.join(full,'publishable-assets.json'));
let manual={assets:[]};try{manual=await read(path.join(root,'art/equipment-2d/user-edits/selected.json'))}catch(e){if(e.code!=='ENOENT')throw e}
for(const a of d.candidates)for(const v of a.variants){const protectedAsset=manual.assets.find(p=>p.id===a.id),expected=protectedAsset?.files.find(p=>p.file==='sprites/'+path.basename(v.file));if(expected)assert.equal(v.sha256,expected.sha256,'Stale generated candidate cannot overwrite user-edited art');}
await fs.mkdir(backup,{recursive:true});
async function preserve(file){const target=path.join(backup,file);await fs.mkdir(path.dirname(target),{recursive:true});try{await fs.copyFile(path.join(full,file),target,fs.constants.COPYFILE_EXCL)}catch(e){if(e.code!=='EEXIST')throw e}}
for(const file of ['equipment-42.json','publishable-assets.json','preview-data.js','validation.json'])await preserve(file);
const selections=[];
for(const a of d.candidates){
 const target=data.assets.find(v=>v.id===a.id);assert.equal(target.worldLengthBlocks,a.worldLengthBlocks);assert.deepEqual(target.anchorPixels,a.anchorPixels);
 for(const v of a.variants){
   const original=[...target.variants,...target.gui].find(s=>s.state===v.state),bytes=await fs.readFile(path.join(review,v.file)),current=await fs.readFile(path.join(full,original.file));
   assert.equal(hash(bytes),v.sha256);assert([v.beforeHash,v.sha256].includes(hash(current)),'Selected source changed; review before applying');await preserve(original.file);await fs.writeFile(path.join(full,original.file),bytes);
   for(const key of ['sha256','size','boundsInclusive','opaquePixels','colors','components8','transparentMargin'])original[key]=v[key];
   const p=pub.include.find(p=>p.file===original.file);assert(p);p.sha256=v.sha256;
   selections.push({id:a.id,state:v.state,file:original.file,sha256:v.sha256});
 }
 target.artRevision={date:'2026-10-16',request:a.id==='blasphemous_blade'?'User selected identity-review new version':'User requested old-base continuous curve revision',source:'art/equipment-2d/shape-review/manifest.json',runtimeIntegrated:false};
}
await fs.writeFile(path.join(full,'equipment-42.json'),JSON.stringify(data,null,2)+'\n');await fs.writeFile(path.join(full,'publishable-assets.json'),JSON.stringify(pub,null,2)+'\n');await fs.writeFile(path.join(full,'preview-data.js'),'window.EQUIPMENT = '+JSON.stringify(data.assets)+';\n');
const validation=await read(path.join(full,'validation.json'));validation.browserVerified=false;validation.visualReview=false;validation.previousBrowserChecksAreHistorical=true;validation.lastArtRevision='2026-10-16';validation.revisionPreview='art/equipment-2d/shape-review/manifest.json';await fs.writeFile(path.join(full,'validation.json'),JSON.stringify(validation,null,2)+'\n');
for(const s of selections)assert.equal(hash(await fs.readFile(path.join(full,s.file))),s.sha256);
for(const f of d.protectedFiles)assert.equal(hash(await fs.readFile(path.join(root,f.file))),f.sha256);
await fs.writeFile(path.join(review,'selection.json'),JSON.stringify({date:'2026-10-16',selectedArtFiles:selections,backups:'art/equipment-2d/shape-review/backup-before-selection',protectedUnchanged:true,runtimeIntegrated:false,inGameAccepted:false},null,2)+'\n');
console.log(`PASS ${selections.length} selected art files, manifest and whitelist synchronized; lengths/grips/protected files unchanged.`);
