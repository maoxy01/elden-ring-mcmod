import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {PNG} from './equipment-pixels.mjs';
const root='D:/codexout/mcmod',dir='art/vfx-blockbench/takers-flames-20261021',base=path.join(root,dir),hash=b=>createHash('sha256').update(b).digest('hex');
const read=async f=>JSON.parse(await fs.readFile(path.join(base,f))),model=await read('takers-flames-eruption.bbmodel'),record=await read('native-authoring.json'),baseline=await read('baseline.json'),capture=await read('native-captures.json');
const contract=model.unhandled_root_fields.takers_flames_art;
assert.equal(model.meta.model_format,'free');assert.equal(model.textures.length,64);assert.equal(model.elements.length,64);assert.equal(model.animations.length,1);assert(model.history.length>=82);assert.equal(record.history,model.history.length);
for(const f of baseline.protection)assert.equal(hash(await fs.readFile(path.join(root,f.file))),f.sha256,'Protected changed: '+f.file);
const rows=[],decode=t=>PNG.sync.read(Buffer.from(t.source.split(',')[1],'base64'));
for(const d of contract.defs){
 const before=d.layer==='before',file=before?'before/flame_front/frame_'+String(d.index).padStart(2,'0')+'.png':'textures/'+d.layer+'/frame_'+String(d.index).padStart(2,'0')+'.png';
 const b=await fs.readFile(path.join(base,file)),png=PNG.sync.read(b),embedded=decode(model.textures.find(t=>t.uuid===d.texture));assert.deepEqual(png.data,embedded.data,d.name+' embedded mismatch');assert.equal(png.width,64);assert.equal(png.height,64);
 if(before){const source=await fs.readFile(path.join(root,'art/vfx-2d',baseline.original.frames[d.index].file));assert.equal(hash(b),hash(source));continue;}
 let visible=0,top=64,bottom=-1;const colors=new Set();
 for(let y=0;y<64;y++)for(let x=0;x<64;x++){const i=(y*64+x)*4;assert([0,255].includes(png.data[i+3]));if(png.data[i+3]){visible++;top=Math.min(top,y);bottom=Math.max(bottom,y);colors.add(png.data.subarray(i,i+3).join(','));assert(x>0&&y>0&&x<63&&y<63,'Clipped sprite');}const bx=x-x%2,by=y-y%2;assert.deepEqual(png.data.subarray(i,i+4),png.data.subarray((by*64+bx)*4,(by*64+bx)*4+4),'Not hard 2px clusters');}
 if(d.layer!=='eruption_debris')assert(visible>0);else if(d.index===0||d.index===15)assert.equal(visible,0,'Debris endpoints should be hidden');
 const element=model.elements.find(e=>e.name===d.name);assert(element);const face=d.layer==='ground_rupture'?element.faces.up:element.faces.south;assert.equal(model.textures[face.texture].uuid,d.texture);assert.deepEqual(face.uv,[0,0,64,64]);
 rows.push({layer:d.layer,frame:d.index,file,sha256:hash(b),opaquePixels:visible,paletteSize:colors.size,boundsY:visible?[top,bottom]:null,embeddedMatches:true});
}
assert.equal(rows.length,48);
const animation=model.animations[0],animators=Object.values(animation.animators).filter(a=>a.keyframes?.some(k=>k.channel==='scale'));assert.equal(animators.length,32);
for(let i=0;i<16;i++){
 const time=i*.1+.001,enabled=[];
 for(const a of animators){const keys=a.keyframes.filter(k=>k.channel==='scale').sort((a,b)=>a.time-b.time),key=keys.filter(k=>k.time<=time).at(-1);assert(key);if(Number(key.data_points[0].x)===1)enabled.push(a.name);}
 assert.deepEqual(enabled.sort(),['before_frame_'+String(i).padStart(2,'0'),'candidate_frame_'+String(i).padStart(2,'0')].sort());
}
for(const c of capture.captures){const png=PNG.sync.read(await fs.readFile(path.join(base,c.file)));let visible=0,border=0;for(let y=0;y<png.height;y++)for(let x=0;x<png.width;x++){if(png.data[(y*png.width+x)*4+3]){visible++;if(x===0||y===0||x===png.width-1||y===png.height-1)border++;}}assert(visible>100,'Blank native capture '+c.file);assert.equal(border,0,'Clipped capture '+c.file);}
assert.equal(capture.captures.length,48);assert(capture.reloaded);assert.equal(capture.history,model.history.length);
const result={date:'2026-10-21',nativeBlockbench:true,nativeUndoEntries:model.history.length,frames:48,layers:3,frameSize:[64,64],logicalPixels:[32,32],protectedFilesUnchanged:baseline.protection.length,originalFramesBackedUpAndUnchanged:16,embeddedTexturesMatch:true,nativeAnimationStepChecks:16,nativeReloadedCaptures:48,transparentMargins:true,binaryAlpha:true,runtimeIntegrated:false,inGameAccepted:false,rows};
await fs.writeFile(path.join(base,'validation.json'),JSON.stringify(result,null,2));
await fs.writeFile(path.join(base,'candidate-manifest.json'),JSON.stringify({schemaVersion:1,status:'art_candidate_pending_review',skillId:'eldencombat:takers_flames',project:'takers-flames-eruption.bbmodel',authoring:'Blockbench native Texture.edit / Cube UV / Animation and Undo',runtimeIntegrated:false,layers:contract.layers.map(l=>({...l,frameCount:16,frameSize:[64,64],filter:'nearest',vertexTint:[255,255,255],frames:rows.filter(r=>r.layer===l.id).map(r=>({index:r.frame,file:r.file,sha256:r.sha256}))})),phases:contract.phases,previewOnlyCycleSeconds:1.6,clock:'Runtime must sample existing server-synchronized wave progress; no new lifetime or replay',protection:'baseline.json'},null,2));
console.log('PASS 48 native frames / 82 Undo entries / 16 animation samples / 48 reloaded captures / '+baseline.protection.length+' protected files unchanged.');
