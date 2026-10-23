'use strict';
// Replace only the embedded weapon in existing editable skill projects.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..'),Core=require('./core.cjs')(fs,path,crypto);
const destination=path.join(root,'art/equipment-2d/V0.2/skill-actions');
const read=file=>JSON.parse(fs.readFileSync(file,'utf8').replace(/^\uFEFF/,''));
const sha=file=>Core.hash(fs.readFileSync(file));
const pairs=read(path.join(root,'art/equipment-2d/V0.2/holds/mapping.json')).items;
const rows=[],inputs={};
for(const skill of Core.catalog(root)) {
  const target=path.join(destination,'projects',skill.id+'.bbmodel');
  assert(!fs.existsSync(target),'Preserve an existing edited skill project: '+target);
  const liveBackup=path.join(destination,'qa/before-live/cursed_blood_slice-81d79556.bbmodel');
  const source=skill.id==='cursed_blood_slice' && fs.existsSync(liveBackup)?liveBackup:path.join(__dirname,'evidence/projects',skill.id+'.bbmodel');
  const model=read(source),b=model.unhandled_root_fields.elden_pose;
  assert.equal(b.sourceHash,sha(skill.file),'Source pose changed; do not silently rebase an editable project');
  const originalAnimations=JSON.stringify(model.animations),original=b.original;
  assert.deepEqual(original,Core.load(root,skill.id).original);
  inputs[path.relative(root,source).replace(/\\/g,'/')]=sha(source);
  inputs[path.relative(root,skill.file).replace(/\\/g,'/')]=sha(skill.file);
  const pair=pairs.find(r=>r.id===skill.itemId && r.kind==='held');
  const gripFile=path.join(root,'art/equipment-2d/V0.2/holds',pair.project);
  inputs[path.relative(root,gripFile).replace(/\\/g,'/')]=sha(gripFile);
  const weapon=Core.weapon(root,skill.itemId,'v02');
  let weaponNode;
  function find(nodes){for(const node of nodes)if(typeof node==='object'){if(node.uuid===b.weaponGroup){weaponNode=node;return;}find(node.children||[]);}}
  find(model.outliner);assert(weaponNode,'Missing existing weapon bone');
  assert(weaponNode.children.every(c=>typeof c==='string'),'Unexpected nested weapon bones');
  const oldIds=new Set(weaponNode.children),removed=model.elements.filter(e=>oldIds.has(e.uuid));
  assert.equal(removed.length,oldIds.size);
  const textureIds=new Set(removed.flatMap(c=>Object.values(c.faces).filter(f=>f.texture!==null&&f.texture!==false).map(f=>f.texture)));
  assert.equal(textureIds.size,1);const textureIndex=[...textureIds][0];
  assert.equal(typeof textureIndex,'number');
  assert(!model.elements.filter(e=>!oldIds.has(e.uuid)).some(e=>Object.values(e.faces).some(f=>f.texture===textureIndex)),'Weapon texture shared with player');
  const oldTexture=model.textures[textureIndex];
  model.textures[textureIndex]={...oldTexture,name:skill.itemId+'__'+weapon.state+'.png',
    source:weapon.textures[0].data,path:'',relative_path:'',uv_width:16,uv_height:16,
    width:read(gripFile).textures[0].width,height:read(gripFile).textures[0].height};
  const geometry=weapon.model.elements.map(e=>({name:'weapon_part',type:'cube',uuid:crypto.randomUUID(),
    from:e.from,to:e.to,origin:[0,0,0],rotation:[0,0,0],inflate:0,box_uv:false,locked:true,export:true,
    faces:Object.fromEntries(Object.entries(e.faces).map(([side,f])=>[side,{...f,rotation:f.rotation||0,texture:textureIndex}]))}));
  model.elements=model.elements.filter(e=>!oldIds.has(e.uuid)).concat(geometry);
  weaponNode.children=geometry.map(c=>c.uuid);
  b.weapon=weapon;delete b.weaponError;
  b.depth={min:Math.min(...geometry.map(c=>c.from[2])),max:Math.max(...geometry.map(c=>c.to[2]))};
  const signature=JSON.parse(b.sceneSignature);
  signature.cubes=signature.cubes.filter(c=>!oldIds.has(c[0])).concat(geometry.map(c=>[
    c.uuid,b.weaponGroup,c.from,c.to,c.origin,c.rotation,c.inflate,
    Object.fromEntries(Object.entries(c.faces).map(([side,f])=>[side,[f.uv,oldTexture.uuid,f.rotation]]))]));
  b.sceneSignature=JSON.stringify(signature);
  assert.equal(JSON.stringify(model.animations),originalAnimations);
  assert.deepEqual(b.original,original);
  model.name+='-V0.2武器';delete model.history;delete model.undo_history;
  fs.writeFileSync(target,JSON.stringify(model));
  rows.push({skill:skill.id,itemId:skill.itemId,project:path.relative(destination,target).replace(/\\/g,'/'),
    sourceProject:path.relative(root,source).replace(/\\/g,'/'),sourceSha256:sha(source),
    weaponSource:weapon.source,geometry:geometry.length,animations:model.animations.length,
    animationsUnchanged:true,displayCopiedVerbatim:true,runtimeInstalled:false,nativeReopenPending:true});
}
for(const[file,hash]of Object.entries(inputs))assert.equal(sha(path.join(root,file)),hash);
fs.writeFileSync(path.join(destination,'manifest.json'),JSON.stringify({schema:'skill-weapons-v02/v1',items:rows,runtimeInstalled:false},null,2)+'\n');
fs.writeFileSync(path.join(destination,'qa/input-hashes.json'),JSON.stringify(inputs,null,2)+'\n');
console.log(JSON.stringify({projects:rows.length,animationsUnchanged:true,inputFilesUnchanged:Object.keys(inputs).length,nativeReopenPending:true}));
