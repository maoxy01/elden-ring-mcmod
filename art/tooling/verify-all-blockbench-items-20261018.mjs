import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {PNG,stats,opaque} from './equipment-pixels.mjs';
import {assertSelectedArt} from './selected-equipment.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),dir='art/equipment-2d/blockbench-all-items-20261018/';
const bytes=f=>fs.readFile(path.join(root,f)),read=async f=>JSON.parse(await bytes(f)),hash=b=>createHash('sha256').update(b).digest('hex');
const source=await read(dir+'source-manifest.json'),report=await read(dir+'native-edit-report.json'),model=await read(dir+'all-items-optimized.bbmodel'),full=await read('art/equipment-2d/full/equipment-42.json'),plans=await read(dir+'native-capture-plans.json');
assert.equal(report.items.length,28);assert.equal(model.textures.length,94);assert(model.history.length>=94);assert.equal(report.items.flatMap(a=>a.states).length,94);
assert(report.authoring.batches.every(b=>b.status==='author_reviewed_native_fronts'));
for(const f of source.protection)assert.equal(hash(await bytes(f.file)),f.sha256,'Protected changed: '+f.file);
await assertSelectedArt();
let providerStates=0;
for(const a of full.assets){assert.equal(hash(await bytes(a.source)),a.sourceHash);if(a.delivery==='provider_reference_only')for(const v of a.variants){assert.equal(hash(await bytes(v.file)),v.sha256);providerStates++}}
const decode=t=>PNG.sync.read(Buffer.from(t.source.split(',')[1],'base64'));
function difference(a,b){assert.equal(a.width,b.width);assert.equal(a.height,b.height);let rgba=0,alpha=0;for(let i=0;i<a.data.length;i+=4){if(a.data[i+3]!==b.data[i+3])alpha++;if([0,1,2,3].some(k=>a.data[i+k]!==b.data[i+k]))rgba++}return {rgba,alpha}}
function imageStats(p){const q=new PNG({width:p.width,height:p.height});p.data.copy(q.data);let semi=0;for(let i=3;i<q.data.length;i+=4)if(q.data[i]){if(q.data[i]!==255)semi++;q.data[i]=255}return {...stats(q),semiTransparentPixels:semi}}
const assets=[],textureChecks=[];
for(const a of report.items){
 const old=full.assets.find(v=>v.id===a.id),independent=await read(dir+'items/'+a.id+'.bbmodel'),rootGroup=independent.groups.find(g=>g.uuid===a.group);
 assert(a.nativeDone);assert(Math.abs(a.worldLengthBlocks-old.worldLengthBlocks)<1e-9);assert.deepEqual(a.anchorPixels,old.anchorPixels);assert.deepEqual(rootGroup.origin,[0,0,0]);
 assert.equal(independent.textures.length,a.states.length);assert.equal(independent.art_contract.items.length,1);
 assert.equal(a.suggestedDisplay.uniformXYScale,a.worldLengthBlocks*a.states[0].size[0]/a.sourceSpanPixels);
 const variants=[],gui=[];
 for(const state of a.states){
  const texture=model.textures.find(t=>t.uuid===state.texture),embedded=decode(texture),b=await bytes(state.output),p=PNG.sync.read(b),before=PNG.sync.read(await bytes(state.before)),s=imageStats(p),diff=difference(before,p);
  assert.deepEqual(p.data,embedded.data,'Export/embedded '+a.id+' '+state.state);assert.deepEqual(s.size,state.size);assert(diff.rgba>0);assert.equal(s.components8.length,1);assert(s.transparentMargin);assert(opaque(p,...state.anchorPixels));
  const sourceSemi=imageStats(before).semiTransparentPixels;
  // Canvas import can quantize RGB under an existing fractional-alpha pixel.
  assert(Math.abs(diff.rgba-state.nativeReport.rgbaDiff)<=sourceSemi);assert.equal(diff.alpha,state.nativeReport.alphaDiff);
  if(a.id==='morgotts_cursed_sword'&&a.outlinePolicy!=='author_may_redesign_user_outline_20261018')assert.equal(diff.alpha,0);else assert.equal(s.semiTransparentPixels,0);
  const separateTexture=independent.textures.find(t=>t.uuid===state.texture);assert.deepEqual(decode(separateTexture).data,p.data);
  const cubes=model.elements.filter(e=>e.name.startsWith(a.id+'__'+state.state+'__native_')),coverage=new Uint8Array(p.width*p.height);
  const unit=a.worldLengthBlocks*16/(a.sourceSpanPixels*p.width/a.states[0].size[0]);assert.equal(cubes.length,state.nativeReport.afterElements);
  for(const e of cubes){
   assert.equal(e.to[2]-e.from[2],1);assert.equal(e.faces.south.texture,model.textures.indexOf(texture));
   const [x,y,xx,yy]=e.faces.south.uv;assert([x,y,xx,yy].every(Number.isInteger));
   assert(Math.abs((e.to[0]-e.from[0])-(xx-x)*unit)<.00003);assert(Math.abs((e.to[1]-e.from[1])-(yy-y)*unit)<.00003);
   for(let row=y;row<yy;row++)for(let col=x;col<xx;col++){assert(opaque(p,col,row));coverage[row*p.width+col]++}
   const ie=independent.elements.find(v=>v.uuid===e.uuid);assert(ie);assert.equal(ie.to[2]-ie.from[2],1);
   assert.equal(independent.textures[ie.faces.south.texture].uuid,state.texture);
   assert.deepEqual(ie.faces.south.uv,e.faces.south.uv);
  }
  for(let i=0;i<coverage.length;i++)assert.equal(coverage[i],p.data[i*4+3]>0?1:0,a.id+' UV coverage');
  const entry={state:state.state,file:'textures/'+path.basename(state.output),url:'textures/'+path.basename(state.output),before:'before/'+path.basename(state.before),sha256:hash(b),...s,anchorPixels:state.anchorPixels,delta:diff};
  (state.state.startsWith('gui_')?gui:variants).push(entry);textureChecks.push({id:a.id,state:state.state,...diff,elements:cubes.length,uvCoverage:true,thicknessUnits:1});
 }
 if(a.id.includes('shield')){const front=PNG.sync.read(await bytes(a.states[0].output)),back=PNG.sync.read(await bytes(a.states.find(s=>s.state==='back').output));assert.equal(difference(front,back).alpha,0)}
 assets.push({id:a.id,name:a.name,category:old.category,delivery:'own_sprite',model:'items/'+a.id+'.bbmodel',modelSha256:hash(await bytes(dir+'items/'+a.id+'.bbmodel')),sourceBasis:old.source,worldLengthBlocks:old.worldLengthBlocks,ratioToPlayer:old.ratioToPlayer,anchorPixels:a.anchorPixels,sourceSpanPixels:a.sourceSpanPixels,beforeSourceSpanPixels:a.originalSourceSpanPixels,projection:a.projection,suggestedDisplay:{...a.suggestedDisplay,uniformXYScale:old.worldLengthBlocks*a.states[0].size[0]/a.sourceSpanPixels},variants,gui,features:[a.designNote],batch:a.batch,outlineAlphaChanged:variants[0].delta.alpha>0,status:'independent_candidate_pending_human_review',runtimeIntegrated:false,inGameAccepted:false});
}
assert.equal(textureChecks.length,94);assert.equal(assets.length,28);
const captureChecks=[];
for(const plan of plans.plans){
 const a=PNG.sync.read(await bytes(dir+'qa/before-'+plan.key+'.png')),b=PNG.sync.read(await bytes(dir+'qa/after-'+plan.key+'.png')),diff=difference(a,b);
 let visible=0,border=0;for(let y=0;y<b.height;y++)for(let x=0;x<b.width;x++)if(b.data[(y*b.width+x)*4+3]){visible++;if(x===0||y===0||x===b.width-1||y===b.height-1)border++}
 assert(visible>100,'Blank capture '+plan.key);assert.equal(border,0,'Clipped capture '+plan.key);assert(diff.rgba>0,'Unchanged capture '+plan.key);
 captureChecks.push({id:plan.id,state:plan.state,view:plan.view,key:plan.key,changedPixels:diff.rgba,visiblePixels:visible,borderPixels:border});
}
assert.equal(plans.plans.length,122);
const result={date:'2026-10-18',nativeAuthoring:true,items:28,pngs:94,independentProjects:28,masterHistoryEntries:model.history.length,baseThicknessUnits:1,pixelAndUVChecks:true,sourceProtectedFiles:source.protection.length,sourceProtectedUnchanged:true,providerStatesUnchanged:providerStates,geometryChangedStates:textureChecks.filter(v=>v.alpha>0).length,primaryOutlinesChanged:assets.filter(a=>a.outlineAlphaChanged).length,retainedOutlineItems:assets.filter(a=>!a.outlineAlphaChanged).map(a=>({id:a.id,reason:a.id==='morgotts_cursed_sword'?'Explicit user hand-drawn outline lock':'Straight tower-shield silhouette retained; surface/UV rebuilt and reviewed'})),nativeCapturePairs:captureChecks.length,captureChecks,textureChecks,authorReviewedBatchFronts:true,humanAccepted:false,runtimeIntegrated:false,inGameAccepted:false};
const manifest={date:'2026-10-18',scope:'28 own items / all 94 states; 14 provider routes unchanged',assets,master:'all-items-optimized.bbmodel',backup:'before-current.bbmodel',nativeReport:'native-edit-report.json',validation:'validation.json',humanAccepted:false,runtimeIntegrated:false,inGameAccepted:false};
await fs.writeFile(path.join(root,dir,'manifest.json'),JSON.stringify(manifest,null,2)+'\n');await fs.writeFile(path.join(root,dir,'preview-data.js'),'window.ALL_ITEMS='+JSON.stringify(manifest)+';\n');await fs.writeFile(path.join(root,dir,'validation.json'),JSON.stringify(result,null,2)+'\n');
console.log('PASS 28 items / 94 native textures / 28 independent projects / 122 native paired views / '+source.protection.length+' protected files; '+result.geometryChangedStates+' states have silhouette edits, all 94 have authored changes.');
