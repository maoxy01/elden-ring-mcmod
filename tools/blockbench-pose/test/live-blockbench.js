(async()=>{
  const fs=require('fs');
  const root='D:/codexout/mcmod';
  const evidence='C:/Users/30869/OneDrive/文档/ChatGPT/mc模组制作/blockbench-pose/evidence';
  const rows=[];
  const preexisting=new Set(ModelProject.all.map(p=>p.uuid));
  const first=await EldenPoseEditor.openSkill(root,'wave_of_gold');
  let firstUsed=false;
  for(const item of EldenPoseEditor.Core.catalog(root)) {
    const result=firstUsed?await EldenPoseEditor.openSkill(root,item.id):first;
    firstUsed=true;
    const b=Project.unhandled_root_fields.elden_pose;
    if(result.weaponError) throw Error(item.id+': '+result.weaponError);
    if(JSON.stringify(EldenPoseEditor.exportPose())!==JSON.stringify(b.original)) throw Error('Native round-trip differs: '+item.id);
    const joints=Object.fromEntries(Object.entries(b.joints).map(([name,id])=>[name,Group.all.find(g=>g.uuid===id)]));
    for(const group of Object.values(joints)) if(group.parent!=='root') throw Error('Player hierarchy is not vanilla');
    EldenPoseEditor.selectStage(b.animations.ACTIVE);
    let comparisons=0;
    for(const tick of [0,1,2.5,4,8]) {
      Timeline.setTime(tick/20);Animator.preview();
      const expected=EldenPoseEditor.Core.poseAt(b.original,'ACTIVE',1,tick);
      for(const [name,group] of Object.entries(joints)) {
        const wanted=EldenPoseEditor.Core.toEditor(expected[name]);
        for(let i=0;i<3;i++) {if(Math.abs(group.mesh.rotation[['x','y','z'][i]]-wanted[i]*Math.PI/180)>1e-7)throw Error('Native rotation differs: '+item.id+'/'+name+'/'+tick);comparisons++;}
      }
    }
    for(const hand of ['right','left']) {
      b.hand=hand;EldenPoseEditor.draw();
      const group=Group.all.find(g=>g.uuid===b.weaponGroup);
      if(group.mesh.parent!==joints[hand==='right'?'rightArm':'leftArm'].mesh)throw Error('Wrong hand parent');
      const m=group.mesh.matrix.elements;
      const thickness=Math.hypot(m[8],m[9],m[10])*(b.depth.max-b.depth.min);
      if(Math.abs(thickness-.85)>1e-7)throw Error('Wrong local thickness: '+thickness);
    }
    b.hand='right';b.followup=true;
    EldenPoseEditor.refresh();EldenPoseEditor.selectStage(b.flowAnimation);
    for(const tick of [0,b.spec.windupTicks,b.spec.windupTicks+2]) {
      Timeline.setTime(tick/20);Animator.preview();
      const state=EldenPoseEditor.Core.flowAt(EldenPoseEditor.Core.flow(b.spec,true),tick);
      const expected=EldenPoseEditor.Core.poseAt(b.original,state.stage,state.index,state.elapsed);
      for(const [name,group] of Object.entries(joints)) for(let i=0;i<3;i++) if(Math.abs(group.mesh.rotation[['x','y','z'][i]]-EldenPoseEditor.Core.toEditor(expected[name])[i]*Math.PI/180)>1e-7) throw Error('Flow rotation differs');
    }
    const model=Codecs.project.compile({raw:true});
    if(model.unhandled_root_fields?.elden_pose?.id!==item.id)throw Error('Binding lost on compile');
    fs.mkdirSync(evidence+'/projects',{recursive:true});
    fs.writeFileSync(evidence+'/projects/'+item.id+'.bbmodel',JSON.stringify(model));
    const originalProject=Project;
    if(preexisting.has(originalProject.uuid)) throw Error('Refusing to close any pre-existing project');
    originalProject.save_path=evidence+'/projects/'+item.id+'.bbmodel';originalProject.saved=true;
    Codecs.project.load(model,{path:evidence+'/projects/'+item.id+'.bbmodel',no_file:true});
    await new Promise(r=>setTimeout(r,50));
    if(JSON.stringify(EldenPoseEditor.exportPose())!==JSON.stringify(b.original))throw Error('Reopen changed pose: '+item.id);
    rows.push({id:item.id,weapon:b.spec.itemId,nativeRotationComparisons:comparisons,roundtrip:true,reopen:true,leftRight:true,thickness:.85,flow:true});
    if(preexisting.has(Project.uuid)) throw Error('Refusing to close any pre-existing project');
    Project.save_path=evidence+'/projects/'+item.id+'.bbmodel';Project.saved=true;
    await Project.close();await originalProject.close();
  }
  fs.writeFileSync(evidence+'/blockbench-live.json',JSON.stringify({version:Blockbench.version,skills:rows,passed:true},null,2));
  await EldenPoseEditor.openSkill(root,'destined_death');
  const b=Project.unhandled_root_fields.elden_pose;
  EldenPoseEditor.selectStage(b.animations.ACTIVE);Timeline.setTime(.1);Animator.preview();
  return {passed:true,skills:rows};
})()
