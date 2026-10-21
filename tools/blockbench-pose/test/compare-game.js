(async()=>{
  const fs=require('fs'),base='D:/codexout/mcmod/tools/blockbench-pose/evidence';
  const game=JSON.parse(fs.readFileSync(base+'/game-live.json','utf8'));
  let maxError=0,pointComparisons=0;
  for(const id of [...new Set(game.samples.map(s=>s.id))]) {
    await EldenPoseEditor.openSkill('D:/codexout/mcmod',id);
    const b=Project.unhandled_root_fields.elden_pose;
    for(const row of game.samples.filter(s=>s.id===id)) {
      const state=EldenPoseEditor.Core.resolve(b.original,row.stage,row.index);
      const animation=Animation.all.find(a=>a.uuid===b.animations[state.key]);
      EldenPoseEditor.selectStage(animation.uuid);
      const tick=state.frozen?Math.max(...Object.values(state.parts).map(keys=>keys.at(-1)[0])):row.tick;
      Timeline.setTime(tick/20);Animator.preview();
      b.hand=row.hand;EldenPoseEditor.draw();
      const weapon=Group.all.find(g=>g.uuid===b.weaponGroup);
      for(const point of [[0,0,0],[16,0,0],[0,16,0],[0,0,16],[8,8,8]]) {
        const preview=new THREE.Vector3(...point).applyMatrix4(weapon.mesh.matrixWorld).multiplyScalar(1/.9375);
        const runtime=new THREE.Vector3(...point).multiplyScalar(1/16).applyMatrix4(new THREE.Matrix4().fromArray(row.itemToModel));
        const wanted=new THREE.Vector3(-runtime.x*16,24-runtime.y*16,runtime.z*16);
        const error=preview.distanceTo(wanted);maxError=Math.max(maxError,error);pointComparisons++;
        if(error>1e-4) throw Error(JSON.stringify({id:row.id,stage:row.stage,index:row.index,tick:row.tick,hand:row.hand,error,preview:preview.toArray(),wanted:wanted.toArray()}));
      }
    }
  }
  const report={passed:true,gameSamples:game.samples.length,pointComparisons,maxErrorModelUnits:maxError,toleranceModelUnits:1e-4};
  fs.writeFileSync(base+'/game-blockbench-parity.json',JSON.stringify(report,null,2));
  await EldenPoseEditor.openSkill('D:/codexout/mcmod','destined_death');
  const b=Project.unhandled_root_fields.elden_pose;EldenPoseEditor.selectStage(b.animations.ACTIVE);Timeline.setTime(.1);Animator.preview();
  return report;
})()
