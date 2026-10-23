'use strict';
const test=require('node:test'), assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),os=require('node:os'),cp=require('node:child_process');
const Core=require('../core.cjs')(fs,path,crypto);
const root=process.env.ELDEN_PROJECT || 'D:/codexout/mcmod';
function native(pose) {
  return Object.entries(pose.stages).map(([stage,parts])=>({stage,animation:{animators:Object.fromEntries(Object.entries(parts).map(([joint,keys])=>[joint,{keyframes:keys.map(([tick,r])=>{const [x,y,z]=Core.toEditor(r);return {channel:'rotation',time:tick/20,interpolation:'linear',data_points:[{x,y,z}]};})}]))}}));
}
const jointIds=Object.fromEntries(Core.JOINTS.map(j=>[j,j]));
test('all eight skills round-trip and all eight admitted weapon packages resolve',()=>{
  const items=Core.catalog(root);assert.equal(items.length,8);
  for(const {id,itemId} of items) {
    const b=Core.load(root,id);
    assert.deepEqual(Core.exportTracks(b.original,native(b.original),jointIds),b.original);
    const weapon=Core.weapon(root,itemId);assert.ok(weapon.textures.length);assert.ok(weapon.display.thirdperson_lefthand);
  }
});
test('V0.2 skill weapons use the saved grip projects rather than old runtime routes',()=>{
  const pairs=JSON.parse(fs.readFileSync(path.join(root,'art/equipment-2d/V0.2/holds/mapping.json'),'utf8')).items;
  for(const {itemId} of Core.catalog(root)) {
    const row=pairs.find(r=>r.id===itemId && r.kind==='held');
    const file=path.join(root,'art/equipment-2d/V0.2/holds',row.project);
    const bb=JSON.parse(fs.readFileSync(file,'utf8')),weapon=Core.weapon(root,itemId,'v02');
    assert.equal(weapon.source.sha256,Core.hash(fs.readFileSync(file)));
    assert.deepEqual(weapon.display,bb.display);
    assert.equal(weapon.model.elements.length,bb.elements.length);
    assert.equal(weapon.textures[0].data,bb.textures[0].source);
    assert.equal(weapon.source.kind,'v02');assert.equal(weapon.source.runtimeParity,false);
    assert.notEqual(JSON.stringify(weapon.model),JSON.stringify(Core.weapon(root,itemId).model));
  }
});
test('unsupported channels, curves, expressions, duplicate times and foreign bones fail closed',()=>{
  const pose={stages:{ACTIVE:{rightArm:[[0,[0,0,0]],[5,[160,0,0]]]}}};
  for(const mutate of [
    a=>a[0].animation.animators.rightArm.keyframes[0].channel='position',
    a=>a[0].animation.animators.rightArm.keyframes[0].channel='scale',
    a=>a[0].animation.animators.rightArm.keyframes[0].interpolation='bezier',
    a=>a[0].animation.animators.rightArm.keyframes[0].data_points[0].x='math.sin(1)',
    a=>a[0].animation.animators.rightArm.keyframes[1].time=0,
    a=>{a[0].animation.animators.weapon=a[0].animation.animators.rightArm;},
    a=>a[0].animation.animators.rightArm.rotation_global=true,
    a=>a[0].animation.animators.rightArm.keyframes[0].data_points.push({x:0,y:0,z:0})
  ]) {const a=native(pose);mutate(a);assert.throws(()=>Core.exportTracks(pose,a,jointIds));}
});
test('stage overrides, empty-stage Java fallback, endpoint hold, neutral omissions',()=>{
  const pose={stages:{ACTIVE:{rightArm:[[2,[10,0,0]],[5,[20,0,0]]]},'ACTIVE#2':{body:[[0,[30,0,0]]]},RECOVERY:{}}};
  assert.deepEqual(Core.poseAt(pose,'RECOVERY',1,0).rightArm,[20,0,0]);
  assert.deepEqual(Core.poseAt(pose,'ACTIVE',2,0).body,[30,0,0]);
  assert.deepEqual(Core.poseAt(pose,'ACTIVE',1,0).leftArm,[0,0,0]);
  assert.deepEqual(Core.poseAt(pose,'ACTIVE',1,99).rightArm,[20,0,0]);
});
test('source writes are explicit, backed up, conflict checked and survive reopening',()=>{
  const temp=fs.mkdtempSync(path.join(os.tmpdir(),'elden-pose-test-'));
  try {
    fs.mkdirSync(path.join(temp,Core.POSES),{recursive:true});fs.mkdirSync(path.dirname(path.join(temp,Core.PARAMETERS)),{recursive:true});
    fs.copyFileSync(path.join(root,Core.PARAMETERS),path.join(temp,Core.PARAMETERS));
    const original=Core.catalog(root)[0], file=path.join(temp,Core.POSES,original.id+'.json');fs.copyFileSync(original.file,file);
    const before=fs.readFileSync(file),b=Core.load(temp,original.id);
    assert.ok(Core.save(b,b.original).unchanged);assert.deepEqual(fs.readFileSync(file),before);
    const updated=Core.clone(b.original),stage=Object.keys(updated.stages)[0],joint=Object.keys(updated.stages[stage])[0]; updated.stages[stage][joint][0][1][0]+=11;
    const result=Core.save(b,updated);assert.deepEqual(fs.readFileSync(result.backup),before);assert.deepEqual(Core.load(temp,b.id).original,updated);
    fs.appendFileSync(file,' ');const outside=fs.readFileSync(file);assert.throws(()=>Core.save(b,b.original),/外部修改/);assert.deepEqual(fs.readFileSync(file),outside);
  } finally {fs.rmSync(temp,{recursive:true,force:true});}
});
test('against actual Java parser/sampler and actual cast state at every flow tick',()=>{
  const work=path.join(__dirname,'../evidence/java');fs.mkdirSync(work,{recursive:true});
  const legacy=fs.readFileSync(path.join(root,'build/moddev/clientLegacyClasspath.txt'),'utf8').split(/\r?\n/);
  const gson=legacy.find(x=>x.includes('gson-2.10.1.jar'));
  const slf4j=legacy.find(x=>/slf4j-api.*jar/.test(x));
  const classpath=[gson,slf4j].filter(Boolean).join(path.delimiter);
  assert.ok(gson && fs.existsSync(gson),'Project Gson dependency is required for Java parity');
  const sources=[path.join(__dirname,'PoseOracle.java'),...['core/SkillPoseData.java','core/CoreLog.java','server/skills/RemembranceSkillSpec.java','server/skills/LocalSourceMotion.java','server/skills/SkillCastState.java'].map(s=>path.join(root,'src/main/java/com/dsh/eldencombat',s))];
  const build=cp.spawnSync('D:/JDK21/bin/javac.exe',['-encoding','UTF-8','-cp',classpath,'-d',work,...sources],{encoding:'utf8'});
  assert.equal(build.status,0,build.stderr);
  const run=cp.spawnSync('D:/JDK21/bin/java.exe',['-cp',work+path.delimiter+classpath,'PoseOracle',root],{encoding:'utf8',maxBuffer:16*1024*1024});assert.equal(run.status,0,run.stderr);
  const oracle=JSON.parse(run.stdout);let values=0;
  const all=Object.fromEntries(Core.catalog(root).map(s=>[s.id,Core.load(root,s.id)]));
  for(const row of oracle.samples) {
    const actual=Core.poseAt(all[row.id].original,row.stage,row.index,Math.max(0,row.tick));
    for(const joint of Core.JOINTS) for(let axis=0;axis<3;axis++) {assert.ok(Math.abs(actual[joint][axis]*Math.PI/180-row.parts[joint][axis])<2e-6,JSON.stringify({row,actual}));values++;}
  }
  for(const row of oracle.flows) {
    const state=Core.flowAt(Core.flow(all[row.id].spec,row.followup),row.tick);
    assert.deepEqual([state.stage,state.index,state.elapsed],[row.stage,row.index,row.elapsed],JSON.stringify(row));
  }
  fs.writeFileSync(path.join(work,'../java-parity.json'),JSON.stringify({skills:8,rotationComparisons:values,flowTicks:oracle.flows.length,javaSources:sources.slice(1),passed:true},null,2)+'\n');
});
