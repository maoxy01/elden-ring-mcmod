(async()=>{
  const fs=require('fs'),base='D:/codexout/mcmod/tools/blockbench-pose/evidence';
  await EldenPoseEditor.openSkill('D:/codexout/mcmod','destined_death');
  const b=Project.unhandled_root_fields.elden_pose;
  b.root=base+'/source-fixture';
  EldenPoseEditor.selectStage(b.animations.ACTIVE);
  const a=Animation.all.find(a=>a.uuid===b.animations.ACTIVE),group=Group.all.find(g=>g.uuid===b.joints.rightArm);
  let k=a.getBoneAnimator(group).rotation[0];const keyId=k.uuid;
  const old=+k.data_points[0].x;
  Undo.initEdit({keyframes:[k]});k.set('x',old+11);Undo.finishEdit('验证旋转关键帧修改');
  if(+k.data_points[0].x!==old+11)throw Error('Keyframe edit failed');
  Undo.undo();k=a.getBoneAnimator(group).rotation.find(x=>x.uuid===keyId);if(+k.data_points[0].x!==old)throw Error('Native undo failed');
  Undo.redo();k=a.getBoneAnimator(group).rotation.find(x=>x.uuid===keyId);if(+k.data_points[0].x!==old+11)throw Error('Native redo failed');
  const result=EldenPoseEditor.saveSource();
  if(!result.backup||!fs.existsSync(result.backup))throw Error('Backup missing');
  if(JSON.stringify(EldenPoseEditor.Core.read(result.file))!==JSON.stringify(EldenPoseEditor.exportPose()))throw Error('Saved pose differs');
  const saved=fs.readFileSync(result.file);
  fs.appendFileSync(result.file,' ');
  let conflict=false;try{EldenPoseEditor.saveSource();}catch(e){conflict=/外部修改/.test(e.message);}
  if(!conflict)throw Error('External edit was not blocked');
  fs.writeFileSync(result.file,saved);
  Undo.initEdit({keyframes:[k]});k.interpolation='bezier';Undo.finishEdit('验证不支持的曲线');
  let rejected=false;try{EldenPoseEditor.exportPose();}catch(e){rejected=/linear/.test(e.message);}
  Undo.undo();if(!rejected)throw Error('Unsupported curve exported');
  const model=Codecs.project.compile({raw:true}),file=base+'/edited-destined-death.bbmodel';
  fs.writeFileSync(file,JSON.stringify(model));Project.save_path=file;Project.saved=true;
  Codecs.project.load(model,{path:file,no_file:true});
  await new Promise(r=>setTimeout(r,50));
  if(JSON.stringify(EldenPoseEditor.exportPose())!==JSON.stringify(EldenPoseEditor.Core.read(result.file)))throw Error('Reopened edit differs');
  const report={passed:true,nativeEdit:true,undo:true,redo:true,backup:true,externalConflict:true,unsupportedCurveRejected:true,editedProjectReopened:true,formalSourceUntouched:true};
  fs.writeFileSync(base+'/native-save.json',JSON.stringify(report,null,2));
  await EldenPoseEditor.openSkill('D:/codexout/mcmod','destined_death');
  return report;
})()
