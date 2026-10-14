import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { deflateSync } from 'node:zlib';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import {bakeAtlas,bakeMesh} from './game-weapon-bake.mjs';
import {connectedComponents} from './weapon-connectivity.mjs';
const game=process.argv.includes('--game-remembrance');
const remembrance=game||process.argv.includes('--remembrance');
const collection=await import(game?'./remembrance-game.mjs':remembrance?'./remembrance.mjs':'./weapons.mjs');
const {weapons,arts}=collection;

const art=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..'),out=path.join(art,game?'remembrance-game':remembrance?'remembrance':'weapons');
await fs.mkdir(out,{recursive:true});
const uuid=label=>{
  const h=createHash('sha256').update(label).digest('hex');
  return `${h.slice(0,8)}-${h.slice(8,12)}-4${h.slice(13,16)}-a${h.slice(17,20)}-${h.slice(20,32)}`;
};
const size=remembrance?collection.textureResolution:512,tile=size/4,stride=size*4+1;
const palette=collection.palette||{steel:[151,175,181],steel_dark:[64,80,86],leather:[65,44,48],red:[138,39,55],amber:[210,163,60],
  wood:[110,75,46],bone:[214,222,218],stone:[84,97,103],crystal:[46,177,207],
  silver_edge:[214,234,237],engraving:[36,49,53],leather_wrap:[125,64,73],ivory:[235,231,208]};
let materials=Object.fromEntries(Object.entries(palette).map(([name,color],i)=>[name,{uv:[i%4*tile,Math.floor(i/4)*tile,i%4*tile+tile,Math.floor(i/4)*tile+tile],color}]));
function chunk(name,data) {
  const tag=Buffer.from(name),len=Buffer.alloc(4),tail=Buffer.alloc(4);len.writeUInt32BE(data.length);
  let crc=0xffffffff;
  for(const b of Buffer.concat([tag,data])) {crc^=b;for(let i=0;i<8;i++)crc=crc>>>1^((crc&1)?0xedb88320:0);}
  tail.writeUInt32BE((crc^0xffffffff)>>>0);return Buffer.concat([len,tag,data,tail]);
}
const raw=Buffer.alloc(size*stride),paletteNames=Object.keys(palette),paletteColors=Object.values(palette);
for(let y=0;y<size;y++)for(let x=0;x<size;x++) {
  const index=Math.floor(y/tile)*4+Math.floor(x/tile),name=paletteNames[index];
  let rgb=paletteColors[index]||[40,45,47];
  const u=x%tile,v=y%tile,noise=(((x*374761393+y*668265263)>>>0)%17-8)/4;
  let shade=noise;
  if(['steel','steel_dark','silver_edge','amber','relic_metal','relic_edge','gold','gold_light','black_steel','black_edge','silver','bronze'].includes(name)) {
    shade+=Math.max(0,1-Math.abs(u-tile*.38)/(tile*.38))*10;
    if((u*13+v*29)%307===0)shade+=12;
    if(remembrance&&(u*23+v*7)%719<2)shade-=20;
    if(remembrance&&['relic_metal','bronze'].includes(name))shade+=Math.sin(u*.055+Math.sin(v*.034))*6;
  } else if(name==='wood')shade+=Math.sin(u*.7+Math.sin(v*.065))*4;
  else if(name==='stone')shade+=(((Math.floor(u/4)*7+Math.floor(v/4)*13)%11)-5)*1.2;
  else if(name==='flesh'||name==='blood')shade+=Math.sin(u*.16+Math.sin(v*.07)*3)*4+(Math.abs(Math.sin(u*.11+v*.015))<.045?-15:0);
  else if(name==='curse'||name==='iridescent') {
    shade+=Math.sin(u*.045+v*.06)*7;
    if(remembrance) {
      const a=(Math.sin(u*.031+v*.033)+1)/2,b=(Math.sin(u*.025-v*.041)+1)/2;
      rgb=palette.curse.map((v,i)=>v*(1-a)+(palette.iridescent[i]*(1-b)+palette.relic_metal[i]*b)*a);
    }
  }
  else if(name==='red'||name?.startsWith('leather'))shade+=(u+v)%7===0?3:-1;
  else if(name==='crystal')shade+=Math.floor(u/32)*5-Math.floor(v/32)*4;
  if(remembrance) {
    const grain=Math.sin(u*1.27+v*.16)*Math.sin(v*1.83-u*.13);
    if(['gold','gold_light','bronze','silver','relic_metal','relic_edge','black_steel','black_edge'].includes(name)) {
      shade+=grain*1.7+Math.sin(u*.32+Math.sin(v*.012))*1.8;
      if((u*17+v*31)%1741<2)shade-=11;
    } else if(name==='stone') {
      shade+=Math.sin(u*.087+Math.sin(v*.037)*4)*Math.sin(v*.054)*4;
      if((u*97+v*41)%997<3)shade+=10;
    } else if(name==='flesh'||name==='blood')shade+=Math.sin(u*.54+Math.sin(v*.17))*2;
    else if(name==='curse'||name==='iridescent')shade+=grain*2;
    else if(name==='leather')shade+=Math.sin(u*.89)*Math.sin(v*.93)*2;
  }
  const offset=y*stride+x*4+1;
  for(let c=0;c<3;c++)raw[offset+c]=Math.max(0,Math.min(255,rgb[c]+shade));raw[offset+3]=255;
}
let textureRaw=raw,gameBake=null;
if(game) {
  gameBake=await bakeAtlas(weapons,{art,raw,size,stride,materials});
  textureRaw=gameBake.raw;materials=gameBake.materials;
}
const header=Buffer.alloc(13);header.writeUInt32BE(size);header.writeUInt32BE(size,4);header[8]=8;header[9]=6;
const png=Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',header),chunk('IDAT',deflateSync(textureRaw)),chunk('IEND',Buffer.alloc(0))]);
await fs.writeFile(path.join(out,'weapons.png'),png);
const write=(name,value)=>fs.writeFile(path.join(out,name),JSON.stringify(value,null,2)+'\n');
const keys=(...frames)=>frames.map(([tick,value])=>({tick,value}));
function clips(entry) {
  // Bowstring deformation needs a connected-endpoint rig, not a translated rigid bar.
  if(entry.model.bones.some(b=>b.name==='string'))return [];
  const chain=entry.model.bones.filter(b=>b.name.startsWith('chain_'));
  if(chain.length)return [{id:'chain_swing',lengthTicks:24,loop:true,tracks:Object.fromEntries(chain.map((b,i)=>[b.name,
    keys([0,[0,0,-25-i*5]],[12,[0,0,25+i*5]],[24,[0,0,-25-i*5]])])),events:[]}];
  const lash=entry.model.bones.filter(b=>b.name.startsWith('lash_'));
  if(lash.length)return [{id:'whip_extend',lengthTicks:22,loop:false,tracks:Object.fromEntries(lash.map((b,i)=>[b.name,
    keys([0,[0,0,i?25:0]],[9,[0,0,i?-30:0]],[15,[0,0,i?-35:0]],[22,[0,0,i?25:0]])])),events:[]}];
  return [];
}
function blockbench(model,animations,mesh=null) {
  const boneId=Object.fromEntries(model.bones.map(b=>[b.name,uuid(`${model.id}/bone/${b.name}`)]));
  const cubeId=Object.fromEntries(model.cubes.map(c=>[c.name,uuid(`${model.id}/cube/${c.name}`)]));
  const meshId=uuid(`${model.id}/continuous-game-mesh`);
  const children=name=>[...(mesh&&name==='root'?[meshId]:[]),...model.cubes.filter(c=>c.bone===name).map(c=>cubeId[c.name]),
    ...model.bones.filter(b=>b.parent===name).map(b=>({uuid:boneId[b.name],isOpen:true,children:children(b.name)}))];
  return {meta:{format_version:'5.0',model_format:'free',box_uv:false},name:model.id.split(':')[1],model_identifier:model.id,
    resolution:{width:size,height:size},
    elements:model.cubes.map(c=>({name:c.name,type:'cube',uuid:cubeId[c.name],from:c.from,to:c.to,origin:c.pivot||c.from,
      rotation:c.rotation||[0,0,0],inflate:0,autouv:0,box_uv:false,export:!mesh,visibility:!mesh,
      faces:Object.fromEntries(['north','east','south','west','up','down'].map(d=>[d,{uv:model.faceUVs?.[c.name]?.[d]||materials[c.material].uv,texture:0,rotation:0}]))})).concat(mesh?[{
        name:'continuous_game_mesh',type:'mesh',uuid:meshId,origin:[0,0,0],rotation:[0,0,0],export:true,visibility:true,
        vertices:Object.fromEntries(Array.from({length:mesh.vertexCount},(_,i)=>[`v${i}`,mesh.positions.slice(i*3,i*3+3).map(p=>p*model.unitsPerBlock)])),
        faces:Object.fromEntries(Array.from({length:mesh.triangleCount},(_,i)=> {
          const ids=mesh.indices.slice(i*3,i*3+3);
          return [`f${i}`,{vertices:ids.map(j=>`v${j}`),texture:0,
            uv:Object.fromEntries(ids.map(j=>[`v${j}`,[mesh.uvs[j*2]*size,(1-mesh.uvs[j*2+1])*size]]))}];
        }))}]:[]),
    groups:model.bones.map(b=>({name:b.name,origin:b.pivot,rotation:b.rotation,uuid:boneId[b.name],export:true,visibility:true})),
    outliner:[{uuid:boneId.root,isOpen:true,children:children('root')}],
    textures:[{name:'weapons.png',id:'0',uuid:uuid('eldencombat/weapons/texture'),relative_path:'weapons.png',mode:'bitmap',
      render_mode:'default',particle:false,visible:true,saved:true,width:size,height:size,uv_width:size,uv_height:size,
      source:`data:image/png;base64,${png.toString('base64')}`}],
    animations:animations.map(a=>({uuid:uuid(model.id+'/'+a.id),name:a.id,loop:a.loop?'loop':'once',length:a.lengthTicks/20,snapping:20,
      animators:Object.fromEntries(model.bones.filter(b=>a.tracks[b.name]||a.positionTracks?.[b.name]).map(b=>[boneId[b.name],{
        name:b.name,type:'bone',keyframes:[...['rotation','position'].flatMap(channel=>(channel==='rotation'?a.tracks[b.name]:a.positionTracks?.[b.name]||[])?.map(k=>({
          uuid:uuid(`${model.id}/${a.id}/${b.name}/${channel}/${k.tick}`),channel,time:k.tick/20,color:-1,interpolation:'linear',
          data_points:[{x:String(k.value[0]),y:String(k.value[1]),z:String(k.value[2])}]}))||[])]}]))}))};
}
assert.equal(weapons.length,remembrance?8:33);assert.equal(new Set(weapons.map(w=>w.id)).size,weapons.length);
const manifest=[];
for(const entry of weapons) {
  const model=entry.model;model.materials=materials;model.textureSize=[size,size];
  if(remembrance&&!game) {
    const density=7,offset=[25,16,15];
    model.faceUVs=Object.fromEntries(model.cubes.map(c=> {
      const tileUV=materials[c.material].uv;
      const rect=(u,v)=>[tileUV[0]+(c.from[u]+offset[u])*density,tileUV[1]+(c.from[v]+offset[v])*density,
        tileUV[0]+(c.to[u]+offset[u])*density,tileUV[1]+(c.to[v]+offset[v])*density];
      return [c.name,{north:rect(0,1),south:rect(0,1),east:rect(2,1),west:rect(2,1),up:rect(0,2),down:rect(0,2)}];
    }));
    model.texelDensityPerUnit=density;
    for(const c of model.cubes)for(const uv of Object.values(model.faceUVs[c.name])) {
      const tileUV=materials[c.material].uv;
      assert(uv[0]>=tileUV[0]&&uv[1]>=tileUV[1]&&uv[2]<=tileUV[2]&&uv[3]<=tileUV[3],`${entry.id}/${c.name}: UV outside material tile`);
    }
  }
  const names=new Set(model.bones.map(b=>b.name));assert.equal(names.size,model.bones.length);
  assert.equal(new Set(model.cubes.map(c=>c.name)).size,model.cubes.length);
  for(const b of model.bones)assert(!b.parent||names.has(b.parent));
  for(const c of model.cubes) {
    assert(names.has(c.bone));assert(materials[c.material]);
    assert(c.from.every((v,i)=>Number.isFinite(v)&&Number.isFinite(c.to[i])&&v<c.to[i]),`${entry.id}/${c.name}: invalid bounds`);
    assert([...(c.rotation||[0,0,0]),...(c.pivot||c.from)].every(Number.isFinite));
  }
  assert(arts.some(a=>a.id===entry.proposedArt));
  if(remembrance&&!game)assert(model.cubes.length>=collection.previousCubeCounts[entry.id]*4,`${entry.id}: detail increase below 4x target`);
  let mesh=null;
  if(game) {
    assert(model.cubes.length<=1200,`${entry.id}: game geometry budget exceeded`);
    assert.equal(connectedComponents(model).length,1,`${entry.id}: detached geometry`);
    assert(gameBake.reports.find(r=>r.id===model.id).reduction>=.8,`${entry.id}: reduction below 80%`);
    mesh=bakeMesh(model);model.bakedMeshPath=`${entry.id}.mesh.json`;
    model.detailLevel=model.pixelConsolidation?'game profile revision 3: hard cuboid faces and 2x2 macro pixels':
      'game profile revision 2: continuous chamfered surfaces and denoised baked albedo';
    await write(model.bakedMeshPath,mesh);
  }
  const animations=clips(entry);
  await write(`${entry.id}.model.json`,model);await write(`${entry.id}.bbmodel`,blockbench(model,animations,model.smoothSurfaces?.length?mesh:null));
  if(animations.length)await write(`${entry.id}.animations.json`,{schemaVersion:1,ticksPerSecond:20,rotationOrder:'XYZ',rotationMode:'additive_degrees',clips:animations});
  else await fs.rm(path.join(out,`${entry.id}.animations.json`),{force:true});
  manifest.push({id:`eldencombat:${entry.id}`,name:entry.name,group:entry.group,model:`${entry.id}.model.json`,blockbench:`${entry.id}.bbmodel`,
    texture:'weapons.png',bones:model.bones.length,cubes:model.cubes.length,
    grip:[0,0,0],secondaryGrip:remembrance?entry.secondaryGrip:['great_sword','colossal_sword','heavy_thrusting_sword','curved_greatsword','katana','twinblade','greataxe','great_hammer'].includes(entry.id)?[0,-3.5,0]:
      ['spear','great_spear','halberd','scythe','colossal_weapon','glintstone_staff'].includes(entry.id)?[0,-7,0]:null,
    proposedEpicFightPreset:entry.proposedPreset,defaultArt:`eldencombat:${entry.proposedArt}`,mechanicalClips:animations.map(a=>a.id),
    modelStatus:game?`game revision ${model.refinement.revision}; runtime visual acceptance pending`:'refined prototype; not installed in Minecraft',combatStatus:'design only',
    detailLevel:model.detailLevel||entry.detailTarget,features:entry.features,english:entry.english,
    remembrance:!!entry.remembrance,paired:!!entry.paired,replaceableArt:!remembrance,
    ...(remembrance&&!game?{previousCubes:collection.previousCubeCounts[entry.id],geometryMultiplier:model.cubes.length/collection.previousCubeCounts[entry.id]}:{}),
    ...(game?{mesh:model.bakedMeshPath,sourceCubes:gameBake.reports.find(r=>r.id===model.id).sourceCubes,
      geometryReduction:gameBake.reports.find(r=>r.id===model.id).reduction,
      triangles:mesh.triangleCount,vertices:mesh.vertexCount,materialGroups:mesh.materialGroups}:{}),
    damageParts:model.cubes.filter(c=>/blade|tip|head|edge|spike|ball|lash|knuckles/.test(c.name)).map(c=>c.name)});
  if(game)model.bakedMesh=mesh; // Only the offline preview embeds mesh bytes; the model file references them.
}
await write('catalog.json',{schemaVersion:1,unitsPerBlock:16,handedness:'right-handed',weaponAxis:'+Y',origin:'main grip',
  format:'custom cuboid skeleton; not vanilla baked item JSON or Epic Fight player animation JSON',weapons:manifest});
await write('weapon-arts.design.json',{schemaVersion:1,status:'design only; no game code or player animation included',
  executions:false,specialJumpAttacks:true,skillJumpAttacksAllowed:true,arts});
await write('validation.json',{status:'passed',models:weapons.length,artsDesigned:arts.length,texture:`${size}x${size} RGBA`,maxBones:Math.max(...weapons.map(w=>w.model.bones.length)),
  gameplayImplemented:false,...(remembrance&&!game?{minimumGeometryMultiplier:4,comparison:manifest.map(w=>({id:w.id,previousCubes:w.previousCubes,cubes:w.cubes,multiplier:w.geometryMultiplier}))}:{}),
  ...(game?{profile:'game',maximumCubes:1200,minimumReduction:.8,comparison:manifest.map(w=>({id:w.id,
    sourceCubes:w.sourceCubes,cubes:w.cubes,reduction:w.geometryReduction,triangles:w.triangles,vertices:w.vertices,materialGroups:w.materialGroups})),
    connectedComponentsPerModel:1,surfaceBake:gameBake.reports,denoise:gameBake.denoise,pixelConsolidation:gameBake.pixelConsolidation,
    smoothSurfaces:weapons.some(w=>w.model.smoothSurfaces?.length),revision:weapons[0].model.refinement.revision,
    unverifiedPerformance:true}:{}),
  unverified:['Minecraft hand alignment','battle-art code','player animations','multiplayer']});
const bundle=await build({entryPoints:[path.join(art,'tooling/weapons-viewer.mjs')],bundle:true,write:false,format:'iife',minify:true,target:'es2022'});
const template=await fs.readFile(path.join(art,'tooling/weapons-viewer.html'),'utf8');
if(remembrance)await write('references.json',{sources:collection.referenceSources,usage:'visual reference only; no downloaded game textures or meshes'});
const data=JSON.stringify({weapons:weapons.map(w=>({...w,animations:clips(w)})),arts,texture:`data:image/png;base64,${png.toString('base64')}`,
  title:game?'追忆武器 · 游戏版':remembrance?'追忆武器':'法环武器模型',collection:remembrance?'remembrance':'types',
  variant:game?'game':'source',
  galleryColumns:remembrance?4:6,cellWidth:remembrance?4.5:3.5,rowHeight:remembrance?380:270,modelSpan:remembrance?3.25:2.7,
  featured:remembrance?['sacred_relic_sword','malikeths_black_blade','blasphemous_blade','hand_of_malenia']:null,
  footer:game?'8 件 · 游戏版 R3 · 2×2 像素合并 · 战技由 dsh 实施':remembrance?'首批 8 件 · 2048 像素图集 · 专属战技由 dsh 实施':null}).replaceAll('<','\\u003c');
await fs.writeFile(path.join(out,'preview.html'),template.replace('/* DATA */',`window.WEAPON_PACK=${data};`).replace('/* BUNDLE */',bundle.outputFiles[0].text.replaceAll('</script','<\\/script')));
await fs.copyFile(path.join(art,'node_modules/three/LICENSE'),path.join(out,'THREE-LICENSE.txt'));
console.log(`WEAPONS_ART_PASS: ${weapons.length} models, ${arts.length} battle-art designs`);
if(game)console.log(JSON.stringify(manifest.map(w=>({id:w.id,cubes:w.cubes,triangles:w.triangles,reduction:w.geometryReduction})),null,2));
