import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {PNG,stats,opaque} from './equipment-pixels.mjs';
import {assertSelectedArt} from './selected-equipment.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),dir='art/equipment-2d/blockbench-polish-20261018/';
const read=async f=>JSON.parse(await fs.readFile(path.join(root,f))),hash=b=>createHash('sha256').update(b).digest('hex');
const source=await read(dir+'source-manifest.json'),native=await read(dir+'native-edit-report.json');
const model=await read(dir+'items-optimized.bbmodel'),before=await read(dir+'before-current.bbmodel');
const selected=await assertSelectedArt(),checks=[],captures=[];
assert.equal(source.items.length,28);assert.equal(native.report.report.length,45);assert.equal(native.report.undoEntries,45);
assert.equal(model.elements.length,1871);assert.deepEqual(model.elements,before.elements,'Native paint pass must not change geometry');
for(const e of model.elements)assert.equal(e.to[2]-e.from[2],1,'Vanilla slab thickness');
for(const f of source.protection)assert.equal(hash(await fs.readFile(path.join(root,f.file))),f.sha256,'Protected: '+f.file);
const decode=s=>PNG.sync.read(Buffer.from(s.split(',')[1],'base64'));
function difference(a,b){assert.equal(a.width,b.width);assert.equal(a.height,b.height);let diff=0,alphaDiff=0;for(let i=0;i<a.data.length;i+=4){if(a.data[i+3]!==b.data[i+3])alphaDiff++;if(!a.data.subarray(i,i+4).equals(b.data.subarray(i,i+4)))diff++}return {diff,alphaDiff}}
for(const a of source.items){
 for(const s of a.states){
  const b=PNG.sync.read(await fs.readFile(path.join(root,s.before)));
  assert.equal(hash(await fs.readFile(path.join(root,s.source))),s.sha256);
  const texture=model.textures.find(t=>t.uuid===s.texture);assert(texture);
  const embedded=decode(texture.source);assert.equal(embedded.width,s.size[0]);assert.equal(embedded.height,s.size[1]);
  let diff=difference(b,embedded);assert.equal(diff.alphaDiff,0,a.id+' silhouette/alpha');assert(opaque(embedded,...s.anchorPixels));
  if(a.editable){
   const bytes=await fs.readFile(path.join(root,s.output)),p=PNG.sync.read(bytes),st=stats(p);
   assert.deepEqual(p.data,embedded.data,'Native embedded and exported texture');assert(diff.diff>0);assert.equal(st.components8.length,1);assert(st.transparentMargin);
   checks.push({id:a.id,state:s.state,file:s.output,sha256:hash(bytes),...diff,colors:st.colors});
  }else assert.equal(diff.diff,0,'Selected material changed: '+a.id);
 }
 const b=PNG.sync.read(await fs.readFile(path.join(root,dir+'qa/'+a.id+'-before.png')));
 const p=PNG.sync.read(await fs.readFile(path.join(root,dir+'qa/'+a.id+'-front.png'))),d=difference(b,p);
 let pixels=0;for(let i=3;i<p.data.length;i+=4)if(p.data[i]>0)pixels++;
 assert(pixels>150);if(a.editable)assert(d.diff>0,a.id+' native render unchanged');else assert.equal(d.diff,0,a.id+' preserved native render');
 captures.push({id:a.id,...d,visiblePixels:pixels,comparison:'Same recorded native camera, projection and canvas'});
}
const result={date:'2026-10-18',tool:'Native Blockbench 5.2.2',source:'Current selected full art; scripted candidates shelved',nativeEdited:true,editedItems:12,editedTextures:45,reviewedGeometryItems:28,preservedItems:16,slabs:1871,thicknessUnits:1,allAlphaAndSilhouettesUnchanged:true,protectedFiles:source.protection.length,protectedUnchanged:true,selectedArtProtections: selected.assets.length,nativeRenderComparisons:captures,textureChecks:checks,runtimeIntegrated:false,humanAccepted:false,inGameAccepted:false,limitations:['This pass changes texture materials and interior clusters, not item silhouettes','Native render evidence is not Minecraft acceptance','Provider routes and runtime display matrices were not edited']};
await fs.writeFile(path.join(root,dir+'validation.json'),JSON.stringify(result,null,2)+'\n');
await fs.writeFile(path.join(root,dir+'review-data.js'),'window.BB_REVIEW='+JSON.stringify({items:source.items,checks})+';\n');
console.log('PASS native exported/embedded 45 textures; 28 matched-camera native captures (12 changed / 16 unchanged); '+source.protection.length+' protected files unchanged; all slabs 1 unit.');
