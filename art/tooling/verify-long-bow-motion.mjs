import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {Vector3} from 'three';
import {createAsset,sample} from './player-scene.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const json=async file=>JSON.parse(await fs.readFile(path.join(root,file),'utf8'));
const model=await json('art/weapons/long_bow.model.json'),original=await json('art/backups/spear-shield-bow-before-refinement-20261012/art/weapons/long_bow.model.json');
const data=await json('art/weapons/long_bow.draw.json'),clip=(await json('art/weapons/long_bow.animations.json')).clips[0];
assert.deepEqual(model.bones,original.bones);assert.equal(clip.loop,false);assert.equal(clip.lengthTicks,20);
const asset=createAsset(model,null),poses=[];
for(const tick of [0,10,20]){
  asset.pose(clip,tick);
  const endpoints=['upper','lower'].map(side=>{
    const point=data.string[side];const p=asset.bones.get(point.bone).localToWorld(new Vector3(...point.pointRelativeToPivotModelUnits.map(v=>v/16)));
    const local=new Vector3(...point.pointRelativeToPivotModelUnits.map((v,i)=>v+model.bones.find(b=>b.name===point.bone).pivot[i]));
    assert(model.cubes.some(c=>c.bone===point.bone&&c.from.every((v,i)=>v<=local.getComponent(i))&&c.to.every((v,i)=>v>=local.getComponent(i))),side+' endpoint missing limb attachment');
    return p.toArray();
  });
  assert(asset.bones.get('root').getWorldPosition(new Vector3()).length()<1e-8);
  const center=sample(data.string.centerTrack,tick).map(v=>v/16);
  assert(center[0]>endpoints[0][0]||tick===0);assert(Math.abs(endpoints[0][1]+endpoints[1][1])<1e-8);
  poses.push({tick,upper:endpoints[0],center,lower:endpoints[1]});
}
await fs.writeFile(path.join(root,'art/weapon-proportions/bow-motion-validation.json'),JSON.stringify({status:'KEY_POSES_VERIFIED',poses,rootUnchanged:true,bonesUnchanged:true,runtimeIntegrated:false},null,2)+'\n');
console.log(JSON.stringify({status:'KEY_POSES_VERIFIED',poses:poses.length,bonesUnchanged:true}));
