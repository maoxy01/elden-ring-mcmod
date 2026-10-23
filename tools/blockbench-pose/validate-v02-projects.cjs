'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..'),out=path.join(root,'art/equipment-2d/V0.2/skill-actions');
const study=path.join(root,'local-research/elden-source-motion-20261021-arm70-v02');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const rows=read(path.join(out,'manifest.json')).items,report=[];
for(const row of rows) {
  const m=read(path.join(out,row.project)),original=read(path.join(root,row.sourceProject));
  const b=m.unhandled_root_fields.elden_pose,source=b.weapon.source;
  const grip=read(path.join(root,source.file));
  assert.equal(sha(path.join(root,source.file)),source.sha256);
  assert.deepEqual(m.animations,original.animations,'Existing editable keyframes must survive');
  assert.deepEqual(b.original,original.unhandled_root_fields.elden_pose.original);
  assert.deepEqual(b.weapon.display,grip.display);
  assert.deepEqual(m.groups,original.groups,'Keep every bone and pivot');
  const weaponIds=new Set();
  function visit(nodes) {for(const node of nodes)if(typeof node==='object'){if(node.uuid===b.weaponGroup)node.children.forEach(id=>weaponIds.add(id));visit(node.children||[]);}}
  visit(m.outliner);
  const cubes=m.elements.filter(e=>weaponIds.has(e.uuid));
  assert.equal(cubes.length,grip.elements.length);
  const oldBody=original.elements.filter(e=>e.name.endsWith('_reference'));
  assert.deepEqual(m.elements.filter(e=>e.name.endsWith('_reference')),oldBody);
  for(const[i,cube]of cubes.entries()) {
    const wanted=grip.elements[i];
    assert.deepEqual(cube.from,wanted.from);assert.deepEqual(cube.to,wanted.to);
    for(const[side,face]of Object.entries(cube.faces)) {
      const expected=wanted.faces[side].uv.map((v,j)=>v*16/(j%2?grip.resolution.height:grip.resolution.width));
      assert.deepEqual(face.uv,expected);
      assert.equal(m.textures[face.texture].source,grip.textures[0].source);
      assert.equal(m.textures[face.texture].uv_width,16);
    }
  }
  report.push({skill:row.skill,cubes:cubes.length,modelTextureGripMatched:true,bonesAndAnimationDataUnchanged:true});
}
let checkedInputs=0;
for(const manifest of [path.join(out,'qa/input-hashes.json'),path.join(study,'input-hashes.json')])
  for(const[file,hash]of Object.entries(read(manifest))){assert.equal(sha(path.join(root,file)),hash);checkedInputs++;}
const times=(a,b)=>a.map(row=>b[0].map((_,j)=>row.reduce((n,v,k)=>n+v*b[k][j],0)));
const radians=d=>d*Math.PI/180;
function rotation([x,y,z]) {
  [x,y,z]=[x,y,z].map(radians);const c=Math.cos,s=Math.sin;
  return times(times([[c(z),-s(z),0],[s(z),c(z),0],[0,0,1]],[[c(y),0,s(y)],[0,1,0],[-s(y),0,c(y)]]),[[1,0,0],[0,c(x),-s(x)],[0,s(x),c(x)]]);
}
let clips=0,maxError=0;
for(const row of read(path.join(study,'manifest.json')).skills) {
  const m=read(path.join(study,'projects',row.skill+'.bbmodel'));
  const original=read(path.join(root,row.sourceMotionProject));
  const grip=read(path.join(root,row.sourceProject));
  assert.deepEqual(m.animations,original.animations);
  assert.deepEqual(m.elements.slice(0,original.elements.length),original.elements);
  const group=m.groups.find(g=>g.uuid===row.weaponGroup),arm=m.groups.find(g=>g.uuid===row.armGroup);
  const r=rotation(group.rotation),cubes=m.elements.slice(original.elements.length);
  assert.equal(cubes.length,grip.elements.length);
  let parent;
  function visit(nodes,uuid) {for(const n of nodes)if(typeof n==='object'){if(n.uuid===group.uuid)parent=uuid;visit(n.children||[],n.uuid);}}
  visit(m.outliner);assert.equal(parent,arm.uuid);
  for(const[i,cube]of cubes.entries())for(const field of ['from','to']) {
    const p=grip.elements[i][field],offset=cube[field].map((v,j)=>v-group.origin[j]);
    const actual=r.map((axis,j)=>axis.reduce((n,v,k)=>n+v*offset[k],0)+group.origin[j]-arm.origin[j]);
    const wanted=row.itemToArm.slice(0,3).map(axis=>axis[3]+p.reduce((n,v,k)=>n+v*axis[k],0));
    for(let j=0;j<3;j++)maxError=Math.max(maxError,Math.abs(actual[j]-wanted[j]));
    for(const face of Object.values(cube.faces))assert.equal(m.textures[face.texture].source,grip.textures[0].source);
  }
  clips+=m.animations.length;
}
assert(maxError<1e-7);assert.equal(clips,15);
const result={editorProjects:8,sourceMotionProjects:8,sourceMotionClips:clips,
  modelTextureGripMatched:true,animationsUnchanged:true,inputHashChecks:checkedInputs,
  sourceWeaponTransformMaxErrorModelUnits:maxError,
  nativeChecks:{batchReopenPending:true,sessionEvidence:'native-session-note.json'},
  runtimeInstalled:false,humanVisualAccepted:false,rows:report};
fs.writeFileSync(path.join(out,'qa/validation.json'),JSON.stringify(result,null,2)+'\n');
console.log(JSON.stringify(result));
