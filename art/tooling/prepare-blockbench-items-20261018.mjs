import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash,randomUUID} from 'node:crypto';
import assert from 'node:assert/strict';
import {PNG} from './equipment-pixels.mjs';
import {slabProject} from './equipment-slab.mjs';
import {assertSelectedArt} from './selected-equipment.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const allItems=process.argv.includes('--all');
const dir=allItems?'art/equipment-2d/blockbench-all-items-20261018':'art/equipment-2d/blockbench-polish-20261018';
if(allItems){try{await fs.access(path.join(root,dir,'input-current.bbmodel'));throw new Error('All-item source snapshot already exists; resume the native project instead of overwriting it')}catch(e){if(e.code!=='ENOENT')throw e}}
const data=JSON.parse(await fs.readFile(path.join(root,'art/equipment-2d/full/equipment-42.json')));
await assertSelectedArt();
const ids=['claw','colossal_weapon','fist','glintstone_staff','great_shield','great_spear','sacred_seal','small_shield','whip','great_bow','ballista','test_flask'];
const hash=b=>createHash('sha256').update(b).digest('hex');
const elements=[],textures=[],outliner=[],items=[];
const own=data.assets.filter(a=>a.delivery==='own_sprite');
assert.equal(own.length,28);
await fs.mkdir(path.join(root,dir,'before'),{recursive:true});
await fs.mkdir(path.join(root,dir,'textures'),{recursive:true});
await fs.mkdir(path.join(root,dir,'qa'),{recursive:true});
if(allItems)await fs.mkdir(path.join(root,dir,'items'),{recursive:true});
for(const [i,a] of own.entries()){
 const cell=[(i%5)*62,-Math.floor(i/5)*86,0],group={name:a.id+' | '+a.name,uuid:randomUUID(),origin:cell,rotation:[0,0,a.projection.angleDegrees],children:[]};
 const editable=allItems||ids.includes(a.id),states=editable?[...a.variants,...a.gui]:[a.variants[0]],record={id:a.id,name:a.name,editable,group:group.uuid,worldLengthBlocks:a.worldLengthBlocks,sourceSpanPixels:a.sourceSpanPixels,anchorPixels:a.anchorPixels,source:a.source,projection:a.projection,suggestedDisplay:a.suggestedDisplay,category:a.category,states:[]};
 for(const [j,v] of states.entries()){
  const file='art/equipment-2d/full/'+v.file,b=await fs.readFile(path.join(root,file));assert.equal(hash(b),v.sha256);
  await fs.copyFile(path.join(root,file),path.join(root,dir,'before',path.basename(v.file)));
  const png=PNG.sync.read(b),slab=slabProject(a,v,png),textureIndex=textures.length;
  const texture={...slab.textures[0],name:a.id+'__'+v.state+'.png',id:String(textureIndex)};textures.push(texture);
  const origin=v.anchorPixels,unit=a.worldLengthBlocks*16/(a.sourceSpanPixels*v.size[0]/a.variants[0].size[0]);
  const state={name:v.state,uuid:randomUUID(),origin:cell,visibility:j===0,children:[]};
  for(const e of slab.elements){
   const from=[e.from[0]*png.width/16,(16-e.to[1])*png.height/16],to=[e.to[0]*png.width/16,(16-e.from[1])*png.height/16];
   e.from=[cell[0]+(from[0]-origin[0])*unit,cell[1]+(origin[1]-to[1])*unit,-.5];
   e.to=[cell[0]+(to[0]-origin[0])*unit,cell[1]+(origin[1]-from[1])*unit,.5];
   e.name=a.id+'__'+v.state+'__'+e.name;
   for(const f of Object.values(e.faces))f.texture=textureIndex;
   state.children.push(e.uuid);elements.push(e);
  }
  group.children.push(state);
  record.states.push({state:v.state,texture:texture.uuid,group:state.uuid,source:file,sha256:v.sha256,before:dir+'/before/'+path.basename(v.file),output:dir+'/textures/'+path.basename(v.file),anchorPixels:v.anchorPixels,size:v.size});
 }
 outliner.push(group);items.push(record);
}
const project={meta:{format_version:'4.12',model_format:'free',box_uv:false},name:allItems?'Blockbench All Items 20261018':'Blockbench Item Polish 20261018',resolution:{width:32,height:32},elements,textures,outliner,unhandled_root_fields:{art_contract:{referencePlayerHeightBlocks:1.8,thicknessUnits:1,items,source:'Current full assets, not the shelved scripted candidates',runtimeIntegrated:false,humanAccepted:false}}};
await fs.writeFile(path.join(root,dir,'input-current.bbmodel'),JSON.stringify(project));
const protection=[];
async function walk(base){for(const e of await fs.readdir(path.join(root,base),{withFileTypes:true})){const f=base+'/'+e.name;if(e.isDirectory())await walk(f);else protection.push({file:f,sha256:hash(await fs.readFile(path.join(root,f)))})}}
for(const p of ['art/equipment-2d/full','art/item-thickness','art/equipment-2d/user-edits','src/main'])await walk(p);
await fs.writeFile(path.join(root,dir,'source-manifest.json'),JSON.stringify({date:'2026-10-18',items,protection,scriptedCandidatesShelved:'art/equipment-2d/item-polish-20261018',nativeEdited:false,runtimeIntegrated:false,humanAccepted:false},null,2)+'\n');
console.log('Prepared current source import only: '+own.length+' items, '+textures.length+' textures, '+elements.length+' slabs; no texture optimization performed outside Blockbench.');
