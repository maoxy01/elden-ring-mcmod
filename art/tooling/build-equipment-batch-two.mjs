import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {PNG,shapes,line,project,stats,opaque} from './equipment-pixels.mjs';
import {maleniaBlade} from './malenia-blade-profile.mjs';
import {refuseSelectedRegeneration} from './selected-equipment.mjs';
await refuseSelectedRegeneration(['flail','heavy_thrusting_sword','hand_of_malenia','malikeths_black_blade']);
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),out=path.join(root,'art/equipment-2d/identity-batch-two');
const read=async f=>JSON.parse(await fs.readFile(path.join(root,f),'utf8')),hash=b=>createHash('sha256').update(b).digest('hex');
await fs.mkdir(path.join(out,'sprites'),{recursive:true});await fs.mkdir(path.join(out,'before'),{recursive:true});await fs.mkdir(path.join(out,'evidence'),{recursive:true});await fs.mkdir(path.join(out,'models'),{recursive:true});
const data=await read('art/equipment-2d/full/equipment-42.json'),candidates=[],protectedFiles=[];
const selected=['flail','heavy_thrusting_sword','hand_of_malenia','malikeths_black_blade'];
const files=new Set(['art/equipment-2d/full/equipment-42.json','art/equipment-2d/full/publishable-assets.json','art/equipment-2d/full/display-overrides.json','art/equipment-2d/user-edits/selected.json','art/equipment-2d/shape-review/selection.json']);
for(const a of data.assets){for(const v of [...a.variants,...a.gui])files.add(a.delivery==='provider_reference_only'?v.file:'art/equipment-2d/full/'+v.file);if(['sacred_relic_sword','great_sword','colossal_sword','blasphemous_blade','shield','morgotts_cursed_sword'].includes(a.id))files.add(a.source)}
for(const f of files)protectedFiles.push({file:f,sha256:hash(await fs.readFile(path.join(root,f)))});
const features={flail:['柄、链、锤头三区','交错金属链环','暗头与冷灰链节'],heavy_thrusting_sword:['加厚刃根','纵向暗脊与尖刺刃','环抱式护手'],hand_of_malenia:['连续弯长金刃','刃根束环','暗刻槽与浅金刃缘'],malikeths_black_blade:['层叠黑岩刃','浅灰断面','贴刃金裂与宽护手']};
const refs={hand_of_malenia:'dist/法环武器图标/武器/10322_玛莲妮亚的义手刀.png',malikeths_black_blade:'dist/法环武器图标/武器/10122_玛利喀斯的黑剑.png'};
for(const id of selected){
 const a=data.assets.find(a=>a.id===id);assert.equal(a.delivery,'own_sprite');const sourceBytes=await fs.readFile(path.join(root,a.source)),base=shapes(JSON.parse(sourceBytes));let ps=[...base];
 if(id==='flail'){
   ps=base.filter(p=>p.points.every(p=>p[1]<=12)||p.points.every(p=>p[1]>=22));
   ps.push(line([0,11],[0,23],.6,[69,77,80]));
   for(let i=0;i<3;i++){const y=13+i*3,w=i%2?.7:1.7,c=i%2?[147,155,155]:[105,118,124];ps.push(line([-w,y],[w,y],.7,c),line([w,y],[w,y+3],.7,c),line([w,y+3],[-w,y+3],.7,c),line([-w,y+3],[-w,y],.7,c))}
 }
 if(id==='heavy_thrusting_sword'){
   ps.push(line([0,7],[0,28],.75,[48,57,63]),line([-1.6,6],[-1.6,12],.75,[177,188,187]),line([1.6,6],[1.6,12],.75,[103,119,123]));
   ps.push(line([-4.5,3],[-3.5,-.5],.8,[117,112,95]),line([-3.5,-.5],[-1,-1],.8,[161,151,119]),line([4.5,3],[3.5,-.5],.8,[117,112,95]),line([3.5,-.5],[1,-1],.8,[161,151,119]));
 }
 if(id==='hand_of_malenia'){
   ps=maleniaBlade(base);
 }
 if(id==='malikeths_black_blade'){
   ps=base.map(p=>{const y=p.points.reduce((s,p)=>s+p[1],0)/4;return {...p,color:y>6?p.color.map((v,i)=>Math.min(100,Math.round(v*1.05+(i===2?5:2)))):p.color}});
   for(let i=0;i<5;i++){const y=9+i*6,x=-2+i*.45;ps.push(line([x,y],[x+3.7,y+2],.9,[66,74,82]),line([x+3.7,y+2],[x+1.9,y+3],.65,[38,45,50]));}
   ps.push(line([.3,7],[1,19],.55,[133,105,52]),line([1,19],[-.5,27],.55,[133,105,52]));
 }
 const variants=[];
 for(const v of [...a.variants,...a.gui]){
   const name=path.basename(v.file),before=await fs.readFile(path.join(root,'art/equipment-2d/full',v.file));await fs.writeFile(path.join(out,'before',name),before);
   const projected=project(ps,v.size[0],45,base),st=stats(projected.png);assert.equal(st.components8.length,1,id+' disconnected');assert(st.transparentMargin,id+' clipped');assert(opaque(projected.png,...v.anchorPixels),id+' grip');
   const bytes=PNG.sync.write(projected.png);await fs.writeFile(path.join(out,'sprites',name),bytes);variants.push({...v,file:'sprites/'+name,url:'sprites/'+name,before:'before/'+name,beforeHash:hash(before),sha256:hash(bytes),...st});
 }
 candidates.push({...a,sourceHash:hash(sourceBytes),variants,gui:[],features:features[id],reference:refs[id]||null,referenceHash:refs[id]?hash(await fs.readFile(path.join(root,refs[id]))):null,referencePolicy:refs[id]?'Structure/color reference only; no extracted pixels':'Own category design, not a named Elden Ring weapon',status:'unselected_candidate',thickness:{units:1,blocks:1/16,ZIndependentOfXY:true},runtimeIntegrated:false,inGameAccepted:false});
}
const star=data.assets.find(a=>a.id==='starscourge_greatsword'),pub=await read('art/equipment-2d/full/publishable-assets.json'),starFiles=[];
for(const v of [...star.variants,...star.gui]){const bytes=await fs.readFile(path.join(root,'art/equipment-2d/full',v.file)),actual=hash(bytes);starFiles.push({file:v.file,actual,assetManifest:v.sha256,whitelist:pub.include.find(p=>p.file===v.file)?.sha256,match:actual===v.sha256});await fs.writeFile(path.join(out,'evidence',path.basename(v.file)),bytes)}
const ballista=data.assets.find(a=>a.id==='ballista'),overrides=await read('art/equipment-2d/full/display-overrides.json');
const gripStates=[];for(const v of ballista.variants){const p=PNG.sync.read(await fs.readFile(path.join(root,'art/equipment-2d/full',v.file)));gripStates.push({state:v.state,anchorPixels:v.anchorPixels,opaque:opaque(p,...v.anchorPixels),calibrationNoteAnchorPixels:[12.5,19.5],calibrationNoteAnchorOpaque:opaque(p,12.5,19.5)})}
assert(gripStates.every(s=>s.opaque));
const models=[];
for(const a of candidates)for(const v of a.variants){const name=path.basename(v.file,'.png'),file=`models/${name}.json`,model={parent:'minecraft:item/generated',textures:{layer0:`eldencombat:identity_batch_two/${name}`}};await fs.writeFile(path.join(out,file),JSON.stringify(model,null,2)+'\n');models.push({file,sha256:hash(await fs.readFile(path.join(out,file))),texture:v.file,textureHash:v.sha256,baseThicknessUnits:1,displayPolicy:'Reuse item XY/grip calibration; local Z follows vanilla independently'})}
const result={date:'2026-10-16',candidates,audit:[],models,publication:{ownDerivedPixelPngs:candidates.flatMap(a=>a.variants.map(v=>({file:v.file,sha256:v.sha256}))),provenance:'Project-owned designs, modified palette and structure; no extracted reference image pixels copied',excluded:['before/','evidence/','preview screenshots'],selection:'None selected; do not replace active full sprites automatically'},protectedFiles,sideChecks:{starscourge:{status:'blocked_pending_provenance_and_selection',files:starFiles,reason:'Current bitmap protected. Historical receipts record hashes but do not establish author or user selection of latest edit. No whitelist change.'},ballista:{file:'art/equipment-2d/full/display-overrides.json',sha256:protectedFiles.find(p=>p.file.endsWith('display-overrides.json')).sha256,override:overrides.overrides.ballista,worldLengthBlocks:ballista.worldLengthBlocks,sourceSpanPixels:ballista.sourceSpanPixels,states:gripStates,decision:'Keep all calibrated transforms and existing grip. Opaque anchor alone does not prove physical hand placement; no unverified offset.'}},validation:{browser:false,protectedUnchanged:true,humanBlindTest:false,inGameAccepted:false}};
for(const f of protectedFiles)assert.equal(hash(await fs.readFile(path.join(root,f.file))),f.sha256);
await fs.writeFile(path.join(out,'manifest.json'),JSON.stringify(result,null,2)+'\n');await fs.writeFile(path.join(out,'preview-data.js'),'window.IDENTITY='+JSON.stringify(result)+';\n');
const html=await fs.readFile(path.join(root,'art/equipment-2d/identity-review/preview.html'),'utf8');
await fs.writeFile(path.join(out,'preview.html'),html.replaceAll('武器辨识度对照','辨识度第二批').replace('42 项筛选','本批候选').replace('ART-WEAPON-IDENTITY-REVIEW-20261016.md','ART-NEXT-BATCH-HANDOFF-20261016.md').replace('main.append(s);for','if(data.audit.length)main.append(s);for'));
console.log('PASS 4 independent candidates / 12 PNGs; protected current art/overrides unchanged; starscourge provenance blocked.');
