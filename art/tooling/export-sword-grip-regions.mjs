import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const entries=[];
const blades=process.argv.includes('--blades');
for(const id of blades?['dagger','thrusting_sword','heavy_thrusting_sword','curved_sword','katana','twinblade']:['straight_sword','great_sword','colossal_sword']){
  const file='art/weapons/'+id+'.model.json',bytes=await fs.readFile(path.join(root,file));
  const model=JSON.parse(bytes);
  const semanticFile='art/backups/flat-equipment-before-20261012/'+file;
  const semantic=JSON.parse(await fs.readFile(path.join(root,semanticFile),'utf8'));
  const grip=semantic.cubes.find(c=>c.name==='grip');assert(grip);
  assert.equal(model.unitsPerBlock,16);assert.equal(model.bones.length,1);
  const center=[(grip.from[0]+grip.to[0])/2,(grip.from[1]+grip.to[1])/2,0];
  const interval=[grip.from[1]+.5,grip.to[1]-.5];
  const contains=p=>model.cubes.some(c=>c.from.every((v,i)=>v<=p[i])&&c.to.every((v,i)=>v>=p[i]));
  const samples=[];
  for(let y=interval[0];y<=interval[1];y+=.25){
    assert(contains([0,y,0]),id+' has a gap at '+y);
    const hit=model.cubes.filter(c=>c.from[0]<=0&&c.to[0]>=0&&c.from[1]<=y&&c.to[1]>=y);
    samples.push({y,minX:Math.min(...hit.map(c=>c.from[0])),maxX:Math.max(...hit.map(c=>c.to[0])),
      minZ:Math.min(...hit.map(c=>c.from[2])),maxZ:Math.max(...hit.map(c=>c.to[2]))});
  }
  assert(contains(center));assert(contains([0,0,0]));
  entries.push({id:'eldencombat:'+id,model:file,modelSha256:createHash('sha256').update(bytes).digest('hex'),
    semanticSource:semanticFile,unitsPerBlock:16,lengthAxis:'+Y',plateNormal:'+Z',assetOriginModelUnits:[0,0,0],
    handleCenterModelUnits:center,handleCenterBlocks:center.map(v=>v/16),safeGripCenterLineModelUnits:{x:0,y:interval,z:0},
    originInsideCurrentGeometry:true,centerInsideCurrentGeometry:true,continuousCenterLine:true,samples,
    inGamePalmAlignmentVerified:false});
}
const out=path.join(root,'art/weapon-proportions',blades?'blades-grip-regions.json':'sword-grip-regions.json');
await fs.writeFile(out,JSON.stringify({schemaVersion:1,measurement:'Current flat geometry; named pre-flat grip identifies the semantic handle interval. Not a runtime hand transform.',entries},null,2)+'\n');
console.log(JSON.stringify({weapons:entries.length,originInsideGeometry:true,continuousCenterLines:true,out}));
