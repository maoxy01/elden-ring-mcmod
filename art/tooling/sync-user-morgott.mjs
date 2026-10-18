import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {PNG,project,stats,opaque,nearestGrip} from './equipment-pixels.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),full=path.join(root,'art/equipment-2d/full'),review=path.join(root,'art/equipment-2d/shape-review'),user=path.join(root,'art/equipment-2d/user-edits');
const hash=b=>createHash('sha256').update(b).digest('hex'),read=async f=>JSON.parse(await fs.readFile(f,'utf8')),write=async(f,d)=>fs.writeFile(f,JSON.stringify(d,null,2)+'\n');
const data=await read(path.join(full,'equipment-42.json')),pub=await read(path.join(full,'publishable-assets.json')),d=await read(path.join(review,'manifest.json')),selection=await read(path.join(review,'selection.json'));
const a=data.assets.find(a=>a.id==='morgotts_cursed_sword'),front=a.variants.find(v=>v.state==='front'),file=path.join(full,front.file),bytes=await fs.readFile(file),masterHash=hash(bytes),p=PNG.sync.read(bytes);
assert.equal(p.width,64);assert.equal(p.height,64);assert(opaque(p,...front.anchorPixels),'User grip moved: calibrate before synchronization');
const backup=path.join(user,'morgotts_cursed_sword',masterHash);await fs.mkdir(backup,{recursive:true});
for(const [dir,files] of [[full,['equipment-42.json','publishable-assets.json','preview-data.js',...a.variants.map(v=>v.file),...a.gui.map(v=>v.file)]],[review,['manifest.json','selection.json','preview-data.js']]])for(const f of files){const target=path.join(backup,dir===full?'full':'review',f);await fs.mkdir(path.dirname(target),{recursive:true});try{await fs.copyFile(path.join(dir,f),target,fs.constants.COPYFILE_EXCL)}catch(e){if(e.code!=='EEXIST')throw e}}
const polygons=[],partial=[];let alphaMask=PNG.sync.read(bytes);
for(let y=0;y<64;y++)for(let x=0;x<64;x++){const i=(y*64+x)*4,alpha=p.data[i+3];if(alpha&&alpha!==255)partial.push({x,y,alpha});alphaMask.data[i+3]=alpha>=128?255:0;if(alpha<128)continue;polygons.push({color:[...p.data.subarray(i,i+3)],points:[[x,-y],[x+1,-y],[x+1,-y-1],[x,-y-1]]})}
const frontStats=stats(alphaMask),coordinates=polygons.flatMap(q=>q.points.map(([x,y])=>(x+y)/Math.sqrt(2))),newSpan=Math.max(...coordinates)-Math.min(...coordinates);
assert.equal(frontStats.components8.length,1,'Disconnected visible source; review manually');
const pending=[];
for(const v of a.gui){const n=v.size[0],q=project(polygons,n,0),expected=[q.origin[0]+front.anchorPixels[0]*q.scale,q.origin[1]+front.anchorPixels[1]*q.scale],anchor=nearestGrip(q.png,expected,2),s=stats(q.png);assert.equal(s.components8.length,1);assert(s.transparentMargin);pending.push({v,png:q.png,stats:s,anchor,bytes:PNG.sync.write(q.png)})}
assert.equal(hash(await fs.readFile(file)),masterHash,'Master changed during synchronization; rerun against latest save');
Object.assign(front,frontStats,{sha256:masterHash,partialAlphaPixels:partial,statsAlphaThreshold:128});
for(const q of pending){await fs.writeFile(path.join(full,q.v.file),q.bytes);Object.assign(q.v,q.stats,{sha256:hash(q.bytes),anchorPixels:q.anchor,derivedFrom:{file:front.file,sha256:masterHash,method:'palette coverage, solid pixels, transparent margin; master unchanged'}})}
a.previousSourceSpanPixels=a.sourceSpanPixels;a.sourceSpanPixels=newSpan;a.suggestedDisplay.uniformXYScale=a.worldLengthBlocks*64/newSpan;
a.artRevision={date:'2026-10-16',author:'user',masterFile:front.file,masterHash,source:'Latest user save, preserved byte-for-byte',runtimeIntegrated:false};
for(const v of [...a.variants,...a.gui])pub.include.find(f=>f.file===v.file).sha256=v.sha256;
const r=d.candidates.find(c=>c.id===a.id);r.variants=[...a.variants,...a.gui].map(v=>{const old=r.variants.find(w=>w.state===v.state);return {...old,...v,file:'sprites/'+path.basename(v.file),url:'sprites/'+path.basename(v.file),before:old.before,beforeHash:old.beforeHash}});r.sourceSpanPixels=newSpan;r.suggestedDisplay=a.suggestedDisplay;r.artRevision=a.artRevision;r.features=['用户亲自修订手持图','GUI从最新图派生','保留原始PNG'];
for(const v of r.variants){const source=[...a.variants,...a.gui].find(w=>w.state===v.state);await fs.copyFile(path.join(full,source.file),path.join(review,v.file));const s=selection.selectedArtFiles.find(s=>s.id===a.id&&s.state===v.state);s.sha256=v.sha256}
await write(path.join(full,'equipment-42.json'),data);await write(path.join(full,'publishable-assets.json'),pub);await fs.writeFile(path.join(full,'preview-data.js'),'window.EQUIPMENT = '+JSON.stringify(data.assets)+';\n');
d.validation.browser=false;await write(path.join(review,'manifest.json'),d);await write(path.join(review,'selection.json'),selection);await fs.writeFile(path.join(review,'preview-data.js'),'window.IDENTITY='+JSON.stringify({candidates:d.candidates,audit:d.audit})+';\n');
const validation=await read(path.join(full,'validation.json'));validation.browserVerified=false;validation.staticPass=false;validation.userRevision=a.artRevision;await write(path.join(full,'validation.json'),validation);
let registry={assets:[]};try{registry=await read(path.join(user,'selected.json'))}catch(e){if(e.code!=='ENOENT')throw e}
registry.assets=registry.assets.filter(e=>e.id!==a.id);registry.assets.push({id:a.id,master:front.file,sha256:masterHash,files:[...a.variants,...a.gui].map(v=>({file:v.file,sha256:v.sha256})),snapshot:path.relative(root,backup).replaceAll('\\','/')});await write(path.join(user,'selected.json'),registry);
assert.equal(hash(await fs.readFile(file)),masterHash);for(const v of [...a.variants,...a.gui])assert.equal(hash(await fs.readFile(path.join(full,v.file))),v.sha256);
await write(path.join(backup,'sync-receipt.json'),{masterHash,masterUnchanged:true,partialAlphaPreserved:partial,worldLengthBlocks:a.worldLengthBlocks,heldAnchor:front.anchorPixels,oldSpan:a.previousSourceSpanPixels,newSpan,gui:pending.map(q=>({file:q.v.file,...q.stats,anchor:q.anchor})),runtimeIntegrated:false});
console.log('PASS latest user PNG unchanged; GUI16/32, selected preview and hashes synchronized; backup '+path.relative(root,backup));
