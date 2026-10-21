(async()=>{
  const fs=require('fs'),base='C:/Users/30869/OneDrive/文档/ChatGPT/mc模组制作/blockbench-pose/evidence';
  fs.mkdirSync(base+'/previews',{recursive:true});
  const ids=['wave_of_gold','gold_breaker','destined_death','takers_flames','waterfowl_dance','starcaller_cry','bloodboon_ritual','cursed_blood_slice'];
  const originals=new Set(ModelProject.all.map(p=>p.uuid));
  for(const id of ids) {
    await EldenPoseEditor.openSkill('D:/codexout/mcmod',id);
    const b=Project.unhandled_root_fields.elden_pose;
    EldenPoseEditor.selectStage(b.animations.ACTIVE);Timeline.setTime(.1);Animator.preview();
    Preview.selected.loadAnglePreset({projection:'perspective',position:[65,45,-95],target:[0,20,0]});
    Preview.selected.controls.update();
    await new Promise(resolve=>Preview.selected.screenshot({width:1000,height:800},data=>{fs.writeFileSync(base+'/previews/'+id+'.png',Buffer.from(data.split(',')[1],'base64'));resolve();}));
    const file=base+'/projects/'+id+'.bbmodel';
    fs.writeFileSync(file,Codecs.project.compile());
    Project.save_path=file;Project.saved=true;
    if(originals.has(Project.uuid))throw Error('Refusing to close existing project');
    await Project.close();
  }
  const file=base+'/projects/destined_death.bbmodel';
  Codecs.project.load(JSON.parse(fs.readFileSync(file,'utf8')),{path:file});
  await new Promise(r=>setTimeout(r,50));
  const b=Project.unhandled_root_fields.elden_pose;EldenPoseEditor.selectStage(b.animations.ACTIVE);Timeline.setTime(.1);Animator.preview();
  return {previews:ids.length,projects:ids.length,selected:b.id};
})()
