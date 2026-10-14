import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),entries=[];
for(const [id,center,yRange]of [['spear',[0,0,0],[-8,3]],['shield',[0,-.5,-.5],[-.75,-.25]],['long_bow',[0,-.5,0],[-1.5,1.5]]]){
  const file='art/weapons/'+id+'.model.json',bytes=await fs.readFile(path.join(root,file)),m=JSON.parse(bytes);
  assert(m.bones.every(b=>b.rotation.every(v=>v===0)));
  const contains=p=>m.cubes.some(c=>c.from.every((v,i)=>v<=p[i])&&c.to.every((v,i)=>v>=p[i]));assert(contains(center));
  for(let y=yRange[0];y<=yRange[1];y+=.25)assert(contains([center[0],y,center[2]]));
  entries.push({id:m.id,file,sha256:createHash('sha256').update(bytes).digest('hex'),unitsPerBlock:16,assetOriginModelUnits:[0,0,0],
    gripCenterModelUnits:center,gripCenterBlocks:center.map(v=>v/16),safeGripYModelUnits:yRange,centerInsideRestGeometry:true,
    note:id==='shield'?'Back-face leather strap is painted within the plate, not a protruding handle. Runtime palm alignment still required.':'Rest-pose handle center line, not a runtime display transform.',inGamePalmAlignmentVerified:false});
}
await fs.writeFile(path.join(root,'art/weapon-proportions/utility-grip-regions.json'),JSON.stringify({schemaVersion:1,entries},null,2)+'\n');
console.log(JSON.stringify({grips:entries.length,centersVerified:true}));
