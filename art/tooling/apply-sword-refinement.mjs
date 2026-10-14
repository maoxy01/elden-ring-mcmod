import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {refinePixelSword} from './sword-pixel-refinement.mjs';
import {refineUtilityEquipment} from './spear-shield-bow-refinement.mjs';
import {refineBladeEquipment,bladeDesigns} from './blade-equipment-refinement.mjs';
import {bowAnimations,bowDrawData} from './long-bow-motion.mjs';
import {bakeMesh} from './game-weapon-bake.mjs';
import {validateBakedMesh} from './weapon-mesh-validation.mjs';
import {connectedComponents} from './weapon-connectivity.mjs';
import {build} from 'esbuild';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),dir=path.join(root,'art/weapons');
const utility=process.argv.includes('--utility');
const blades=process.argv.includes('--blades');
const backup=path.join(root,'art/backups',blades?'six-blades-before-refinement-20261012':utility?'spear-shield-bow-before-refinement-20261012':'swords-before-refinement-20261012');
const read=async file=>JSON.parse(await fs.readFile(file,'utf8'));
const write=async(file,value)=>fs.writeFile(file,JSON.stringify(value,null,2)+'\n');
const digest=bytes=>createHash('sha256').update(bytes).digest('hex');
const catalog=await read(path.join(dir,'catalog.json')),report=[];
for(const id of blades?Object.keys(bladeDesigns):utility?['spear','shield','long_bow']:['straight_sword']){
  const original=await read(path.join(backup,'art/weapons',id+'.model.json'));
  const model=blades?refineBladeEquipment(original):utility?refineUtilityEquipment(original):refinePixelSword(original);
  assert.deepEqual(model.bones,original.bones);assert(model.cubes.length<=320);
  let mesh=null;
  if(model.bones.length===1){mesh=bakeMesh(model);validateBakedMesh(mesh);assert.equal(connectedComponents(model).length,1);model.bakedMeshPath=id+'.mesh.json';}
  const bb=await read(path.join(backup,'art/weapons',id+'.bbmodel'));
  const uuid=name=>{const h=digest(Buffer.from(id+'/'+name));return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20,32)}`;};
  bb.elements=model.cubes.map(c=>({name:c.name,type:'cube',uuid:uuid(c.name),from:c.from,to:c.to,origin:c.pivot,rotation:c.rotation,
    inflate:0,autouv:0,box_uv:false,export:true,visibility:true,faces:Object.fromEntries(Object.entries(model.faceUVs[c.name]).map(([face,uv])=>[face,{uv,texture:0,rotation:0}]))}));
  const children=bone=>[...model.cubes.filter(c=>c.bone===bone).map(c=>uuid(c.name)),...model.bones.filter(b=>b.parent===bone).map(b=>({uuid:bb.groups.find(g=>g.name===b.name).uuid,isOpen:true,children:children(b.name)}))];
  bb.outliner=[{uuid:bb.groups.find(g=>g.name==='root').uuid,isOpen:true,children:children('root')}];
  await write(path.join(dir,id+'.model.json'),model);if(mesh)await write(path.join(dir,id+'.mesh.json'),mesh);await write(path.join(dir,id+'.bbmodel'),bb);
  const entry=catalog.weapons.find(e=>e.id===model.id);entry.cubes=model.cubes.length;if(mesh){entry.triangles=mesh.triangleCount;entry.vertices=mesh.vertexCount;}
  entry.flatProfile=model.flatProfile;entry.detailLevel=model.detailLevel;entry.modelStatus=(utility?'utility':'sword')+' pixel refinement 4; in-game acceptance pending';
  entry.geometryReduction=1-model.cubes.length/entry.sourceCubes;
  if(id==='long_bow'){await write(path.join(dir,'long_bow.animations.json'),bowAnimations);await write(path.join(dir,'long_bow.draw.json'),bowDrawData);entry.mechanicalClips=['bow_draw'];}
  report.push({id:model.id,beforeCubes:original.cubes.length,cubes:model.cubes.length,triangles:mesh?.triangleCount??model.cubes.length*12,connectedComponents:mesh?1:null,
    mechanicalBonesPreserved:true,files:[id+'.model.json',...(mesh?[id+'.mesh.json']:[]),id+'.bbmodel'],sha256:digest(await fs.readFile(path.join(dir,id+'.model.json')))});
}
await write(path.join(dir,'catalog.json'),catalog);
const weapons=[];
for(const record of catalog.weapons){
  const model=await read(path.join(dir,record.model));if(model.bakedMeshPath)model.bakedMesh=await read(path.join(dir,model.bakedMeshPath));
  const animations=record.id==='eldencombat:long_bow'?[]:await read(path.join(dir,record.id.split(':')[1]+'.animations.json')).then(a=>a.clips).catch(()=>[]);
  weapons.push({...record,id:record.id.split(':')[1],model,animations});
}
const bundle=(await build({entryPoints:[path.join(root,'art/tooling/weapons-viewer.mjs')],bundle:true,write:false,format:'iife',minify:true,target:'es2022'})).outputFiles[0].text;
const template=await fs.readFile(path.join(root,'art/tooling/weapons-viewer.html'),'utf8');
const pack={weapons,arts:(await read(path.join(dir,'weapon-arts.design.json'))).arts,texture:'data:image/png;base64,'+(await fs.readFile(path.join(dir,'weapons.png'))).toString('base64'),
  title:'武器装备 · 薄片像素版',collection:'types',variant:'flat',galleryColumns:6,footer:'33 件 · 直剑及矛盾弓新版，大剑与特大剑旧版 · 实机待验'};
await fs.writeFile(path.join(dir,'preview.html'),template.replace('/* DATA */','window.WEAPON_PACK='+JSON.stringify(pack).replaceAll('<','\\u003c')+';').replace('/* BUNDLE */',bundle.replaceAll('</script','<\\/script')));
await write(path.join(root,'art/weapon-proportions',blades?'blades-refinement-report.json':utility?'utility-refinement-report.json':'sword-refinement-report.json'),{status:'ASSETS_UPDATED',backup,report,textureChanged:false,runtimeSynced:false,inGameAccepted:false});
console.log(JSON.stringify(report));
