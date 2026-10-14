import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createRequire} from 'node:module';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const out=path.join(root,'art/equipment-2d'),reuse=path.join(root,'art/equipment-reuse');
const require=createRequire('C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json');
const {PNG}=require('pngjs');
const hash=b=>createHash('sha256').update(b).digest('hex');
const read=async f=>JSON.parse(await fs.readFile(path.join(root,f),'utf8'));
const write=async(f,data)=>{await fs.mkdir(path.dirname(f),{recursive:true});await fs.writeFile(f,JSON.stringify(data,null,2)+'\n')};
const definitions=(await read('art/equipment-reuse/definition-evidence.json')).definitions;
const evidence=await read('art/equipment-reuse/research-only-six/evidence.json');
const targets=(await read('art/weapon-proportions/profiles.json')).entries;
const routes=await read('src/main/resources/assets/eldencombat/routes/2d.json');
const oldSelection=await read('art/weapon-proportions/large-sword-version-selection.json');
for(const selected of oldSelection.results)assert.equal(hash(await fs.readFile(path.join(root,selected.file))),selected.sha256,'Selected old sword changed');
const proofs=new Map(definitions.map(d=>[d.itemId,d]));
const names=Object.fromEntries(targets.map(t=>[t.id.split(':')[1],t.name]));names.test_flask='药瓶';
const ownFiles=[],representatives=[];
const sw='spartan_weaponry_unofficial:',ss='simplyswords:';
const candidateIds={dagger:sw+'iron_dagger',straight_sword:'minecraft:iron_sword',great_sword:ss+'iron_claymore',colossal_sword:sw+'iron_greatsword',thrusting_sword:sw+'iron_rapier',curved_sword:sw+'iron_saber',katana:sw+'iron_katana',twinblade:ss+'iron_twinblade',axe:'minecraft:iron_axe',greataxe:sw+'iron_battleaxe',hammer:sw+'iron_flanged_mace',great_hammer:sw+'iron_battle_hammer',colossal_weapon:sw+'iron_battle_hammer',spear:sw+'iron_spear',great_spear:sw+'iron_lance',halberd:sw+'iron_halberd',scythe:sw+'iron_scythe',fist:sw+'cestus',short_bow:'minecraft:bow',long_bow:sw+'longbow_iron_strengthened',crossbow:'minecraft:crossbow',ballista:sw+'heavy_crossbow_iron_strengthened',shield:'spartan_shields_unofficial:iron_basic_shield',great_shield:'spartan_shields_unofficial:iron_tower_shield',claw:'vanilla_claws:iron_claws'};
const approximate=new Set(['great_sword','colossal_sword','curved_sword','hammer','colossal_weapon','great_spear','short_bow','ballista']);
const remembrances=new Set(['sacred_relic_sword','marikas_hammer','malikeths_black_blade','blasphemous_blade','hand_of_malenia','starscourge_greatsword','mohgwyns_sacred_spear','morgotts_cursed_sword']);
function stats(png){
  const points=[],colors=new Set();let alphaInvalid=0;
  for(let y=0;y<png.height;y++)for(let x=0;x<png.width;x++){
    const i=(y*png.width+x)*4,a=png.data[i+3];if(a!==0&&a!==255)alphaInvalid++;
    if(a){points.push([x,y]);colors.add(png.data.subarray(i,i+4).join(','))}
  }
  assert(points.length>0);assert.equal(alphaInvalid,0);
  const pending=new Set(points.map(([x,y])=>y*png.width+x)),components=[];
  while(pending.size){const queue=[pending.values().next().value];pending.delete(queue[0]);let size=0;
    while(queue.length){const index=queue.pop(),x=index%png.width,y=Math.floor(index/png.width);size++;
      for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){if(x+dx<0||x+dx>=png.width||y+dy<0||y+dy>=png.height)continue;const n=(y+dy)*png.width+x+dx;if(pending.delete(n))queue.push(n)}
    }components.push(size);
  }
  const xs=points.map(p=>p[0]),ys=points.map(p=>p[1]);
  return {size:[png.width,png.height],boundsInclusive:[Math.min(...xs),Math.min(...ys),Math.max(...xs),Math.max(...ys)],opaquePixels:points.length,colors:colors.size,alpha:[0,255],components8:components.sort((a,b)=>b-a),transparentMargin:points.every(([x,y])=>x>0&&y>0&&x<png.width-1&&y<png.height-1)};
}
function set(png,x,y,rgb){if(x<0||y<0||x>=png.width||y>=png.height)return;png.data.set([...rgb,255],(y*png.width+x)*4)}
async function projected(id,file,n,angle){
  const source=await read(file),r=angle*Math.PI/180,c=Math.cos(r),s=Math.sin(r);
  assert(source.cubes.every(b=>(b.rotation||[0,0,0]).every(v=>v===0)),'Rotated source needs real mesh projection');
  const corners=source.cubes.flatMap(b=>[[b.from[0],b.from[1]],[b.from[0],b.to[1]],[b.to[0],b.from[1]],[b.to[0],b.to[1]]]);
  const map=([x,y])=>[x*c+y*s,x*s-y*c],mapped=corners.map(map);
  const min=[0,1].map(a=>Math.min(...mapped.map(p=>p[a]))),max=[0,1].map(a=>Math.max(...mapped.map(p=>p[a])));
  const scale=(n-4)/Math.max(max[0]-min[0],max[1]-min[1]);
  const origin=[0,1].map(a=>(n-(max[a]-min[a])*scale)/2-min[a]*scale);
  const png=new PNG({width:n,height:n});
  const cubes=[...source.cubes].sort((a,b)=>a.to[2]-b.to[2]);
  for(let y=0;y<n;y++)for(let x=0;x<n;x++){
    const u=(x+.5-origin[0])/scale,v=(y+.5-origin[1])/scale;
    const mx=u*c+v*s,my=u*s-v*c;
    const cube=cubes.findLast(b=>mx>=b.from[0]&&mx<b.to[0]&&my>=b.from[1]&&my<b.to[1]);
    if(cube)set(png,x,y,source.materials[cube.material].color);
  }
  return {png,anchor:origin.map(v=>Math.floor(v)+.5),source:file,sourceHash:hash(await fs.readFile(path.join(root,file))),angle,scale,modelUnitsPerPixel:1/scale};
}
async function saveOwn(id,state,png){
  const file=`sprites/${id}_${state}.png`,bytes=PNG.sync.write(png);await fs.mkdir(path.join(out,'sprites'),{recursive:true});await fs.writeFile(path.join(out,file),bytes);
  const result={state,file,url:file,sha256:hash(bytes),...stats(png)};ownFiles.push(result);return result;
}
for(const [id,n,angle] of [['great_sword',32,45],['shield',32,0],['sacred_relic_sword',64,45]]){
  const source=`art/${id==='sacred_relic_sword'?'remembrance-game':'weapons'}/${id}.model.json`;
  const projectedAsset=await projected(id,source,n,angle),{png,anchor}=projectedAsset;
  assert.equal(stats(png).components8.length,1,`${id}: disconnected projected silhouette`);
  const variants=[await saveOwn(id,'front',png)];
  if(id==='shield'){
    const back=new PNG({width:n,height:n});
    for(let y=0;y<n;y++)for(let x=0;x<n;x++)if(png.data[(y*n+x)*4+3]){
      const boundary=[[-1,0],[1,0],[0,-1],[0,1]].some(([dx,dy])=>!png.data[((y+dy)*n+x+dx)*4+3]);
      let color=boundary?[65,69,66]:(x%6===0?[65,49,36]:[107,81,53]);
      if(Math.abs(y-anchor[1])<3&&Math.abs(x-anchor[0])<8)color=[65,46,33];
      if(Math.abs(x-anchor[0])<1.6&&Math.abs(y-anchor[1])<5)color=[152,132,90];
      set(back,x,y,color);
    }
    variants.push(await saveOwn(id,'back',back));
    // Blocking reuses the front pixels; its pose is separate, not a fake third texture.
  }
  const target=targets.find(t=>t.id===`eldencombat:${id}`);
  representatives.push({id,name:names[id],delivery:'own_sprite_draft',strict2d:true,source,sourceHash:projectedAsset.sourceHash,projection:{angleDegrees:angle,modelUnitsPerPixel:projectedAsset.modelUnitsPerPixel},variants,anchorPixels:anchor,anchorMethod:'Projected model local origin; independent hand calibration pending',axisImageDegrees:angle-90,worldLengthBlocks:target.targetLengthBlocks,ratioToPlayer:target.ratioToPlayer,ratioEvidence:target.confidence,sourceSpanPixels:target.sourceHeightBlocks*16*projectedAsset.scale,leftHand:{anchorPixels:[n-anchor[0],anchor[1]],mirrorInPreview:true,runtime:'use explicit left-hand transform; do not mirror UV automatically'},blockPose:id==='shield'?{frontTexture:variants[0].file,backTexture:variants[1].file,pivot:anchor,angleDegrees:0,frontFacing:true,needsTwoSidedMaterial:true}:null,legacySelectionPreserved:id==='great_sword',stateReady:id!=='shield'?true:'art_front_back_and_pose_ready_runtime_pending'});
}
for(const [id,provider] of [['straight_sword','minecraft:iron_sword'],['spear',sw+'iron_spear'],['long_bow',sw+'longbow_iron_strengthened']]){
  const items=evidence.entries.filter(e=>e.itemId===provider),variants=[];
  for(const entry of items){const bytes=await fs.readFile(path.join(root,entry.file));assert.equal(hash(bytes),entry.sha256);const png=PNG.sync.read(bytes);variants.push({state:entry.state,file:entry.file,url:'../../'+entry.file,sha256:entry.sha256,predicate:entry.predicate,...stats(png)})}
  const target=targets.find(t=>t.id===`eldencombat:${id}`);
  representatives.push({id,name:names[id],delivery:'provider_reference_only',strict2d:true,providerId:provider,source:items[0].sourceJar,sourceHash:items[0].sourceJarSha256,variants,anchorPixels:null,anchorMethod:'Pixel inspection required before finalizing grip',axisImageDegrees:id==='long_bow'?-90:-45,worldLengthBlocks:target.targetLengthBlocks,ratioToPlayer:target.ratioToPlayer,ratioEvidence:target.confidence,providerDisplay:proofs.get(provider).modelChain.find(c=>c.display)?.display||null,leftHand:{mirrorInPreview:true,runtime:'Preserve provider left-hand display and predicates'},stateReady:true,publication:'Reference complete provider; never package local research snapshots'});
}
const measuredAnchors={straight_sword:[3.5,12.5],spear:[12.5,19.5],long_bow:[3.5,7.5]};
for(const a of representatives){
  if(measuredAnchors[a.id]){
    a.anchorPixels=measuredAnchors[a.id];a.anchorMethod='Opaque grip-region pixel selected from nearest-neighbor inspection; not in-game calibration';
    a.axisImageDegrees=-45;
    const png=PNG.sync.read(await fs.readFile(path.join(root,a.variants[0].file))),coordinates=[];
    const r=a.axisImageDegrees*Math.PI/180;
    for(let y=0;y<png.height;y++)for(let x=0;x<png.width;x++)if(png.data[(y*png.width+x)*4+3])coordinates.push((x+.5)*Math.cos(r)+(y+.5)*Math.sin(r));
    a.sourceSpanPixels=Math.max(...coordinates)-Math.min(...coordinates)+Math.abs(Math.cos(r))+Math.abs(Math.sin(r));
  }
  const n=a.variants[0].size[0];
  a.leftHand.anchorPixels=[n-a.anchorPixels[0],a.anchorPixels[1]];
  a.suggestedDisplay={status:'proposal_not_game_verified',worldLengthBlocks:a.worldLengthBlocks,sourceAxisPixels:a.sourceSpanPixels,uniformXYScale:a.worldLengthBlocks*n/a.sourceSpanPixels,anchorLocalModelUnits:[a.anchorPixels[0]/n*16-8,8-a.anchorPixels[1]/n*16,0],firstPerson:'calibrate independent per-item camera offset; keep relative proportions',thirdPerson:'translate transformed anchor to palm; apply item axis rotation once',leftHand:'reflect position and axis about hand-space X; preserve provider-specific left display',gui:'fit in slot only; GUI fit never sets held world size',providerPolicy:a.providerDisplay?'Preserve complete provider display first; compare target profile before any per-item override':'generated thin sprite; proposed z scale 0.5 gives 1/32 block thickness'};
  for(const v of a.variants){
    v.anchorPixels=[...a.anchorPixels];const png=PNG.sync.read(await fs.readFile(a.delivery==='provider_reference_only'?path.join(root,v.file):path.join(out,v.file)));
    assert(png.data[(Math.floor(v.anchorPixels[1])*png.width+Math.floor(v.anchorPixels[0]))*4+3],`${a.id} ${v.state}: transparent grip`);
  }
}
const order=['straight_sword','great_sword','spear','shield','long_bow','sacred_relic_sword'];representatives.sort((a,b)=>order.indexOf(a.id)-order.indexOf(b.id));
const mappings=[];
for(const route of routes.equipment){
  const id=route.id.split(':')[1],candidate=candidateIds[id]||null,proof=proofs.get(candidate),target=targets.find(t=>t.id===route.id),rep=representatives.find(r=>r.id===id);
  const group=id==='test_flask'?'flask':remembrances.has(id)?'remembrance':'ordinary';
  const existing=id==='test_flask'?'art/player/healing_flask.model.json':`art/${group==='remembrance'?'remembrance-game':'weapons'}/${id}.model.json`;
  await fs.access(path.join(root,existing));
  const sourceOnly=!!candidate&&!proof;
  const strict=proof?(proof.explicitElements===0&&!String(proof.rootParent).includes('builtin/entity')):sourceOnly?null:!!rep;
  let missing=[];
  if(route.kind==='ranged'&&!rep)missing=['independent state textures or complete provider overrides','held alignment for loading/pulling/charged'];
  if(route.kind==='shield'&&!rep)missing=['front/back/hand anchor','blocking pose'];
  if(!candidate&&!rep)missing.push('independent 2D silhouette');
  if(id==='fist')missing.push('cestus candidate contains six 3D elements; strict 2D still missing');
  if(sourceOnly&&!rep)missing.push('exact release JAR, all parent/state PNGs, version/license binding');
  if(id==='shield')missing=['runtime two-sided front/back material','blocking property and pose'];
  if(id==='long_bow')missing=['runtime provider pulling predicates and input adapter'];
  const protectedOld=['great_sword','colossal_sword'].includes(id);
  mappings.push({id:route.id,name:names[id],category:group,existingAsset:{file:existing,sha256:hash(await fs.readFile(path.join(root,existing))),selectedLegacy:protectedOld},candidate:{itemId:candidate,precision:proof?proof.idEvidence:sourceOnly?'pinned source registration/recipe only, not release/installed ID':'no exact provider confirmed',match:approximate.has(id)?'approximate_category_not_equivalent':candidate?'category_candidate':'none',version:proof?(candidate.startsWith('minecraft:')?'Minecraft 1.21.1 / NeoForge resources 21.1.219':proof.jar):candidate?.startsWith('spartan_shields')?'source commit a2a0eb62a98a2fa22d23e624948b1fa541048ce1':sourceOnly?'source commit bcad24f8166c0106fdd294bdb833f29fdc5757a0 / declares 1.2':null,sha256:proof?.jarSha256||null,evidence:proof?'art/equipment-reuse/definition-evidence.json':sourceOnly?'art/equipment-reuse/candidate-source-evidence.json':null,strict2d:strict,installed:false,runtimeRegistryVerified:false},delivery:{method:rep?.delivery||(candidate&&strict?'complete_provider_dependency_candidate':'own_design_pixel_conversion_pending'),representative:!!rep,artStatus:rep?'representative_delivered':candidate?'candidate_only_not_asset_delivery':'not_started',runtimeRouteStatus:route.status,inGameAccepted:false,copyPermission:rep?.delivery==='own_sprite_draft'?'project-owned derivative':candidate?.startsWith('minecraft:')?'reference engine resources; no redistributed Mojang PNG':candidate?'whole-provider dependency only; per-asset redistribution not admitted':'project-owned source available; sprite not authored',protectedOldModel:protectedOld},size:{targetLengthBlocks:target?.targetLengthBlocks??null,ratioToPlayer:target?.ratioToPlayer??null,evidence:target?.confidence||'not_calibrated',grip:rep?.anchorPixels||null,suggestion:rep?'See six-representatives.json; webpage anchor is not in-game proof':'retain per-item profile; grip must be measured on chosen 2D PNG, do not reuse 3D compensation'},missingStates:missing,interfaces:[candidate&&!rep?.delivery?.startsWith('own')?'external_model admission adapter / actual item registry':'sprite admission / per-item display',...(route.kind==='ranged'?['state predicates']:[]),...(route.kind==='shield'?['front/back and blocking transform']:[]),'first/third-person, both hands, classic/slim validation'],nextAction:rep?'Review representative, then code owner admits assets/reference':approximate.has(id)?'confirm category/shape before adopting approximate provider':candidate?'verify complete dependency and state art, then adapt':'convert existing project-owned design to sprite after representative review'});
}
for(const m of mappings){
  const candidate=m.candidate.itemId,rep=representatives.find(a=>`eldencombat:${a.id}`===m.id);
  m.candidate.installed=null;
  if(!candidate)m.candidate.strict2d=null;
  m.delivery.deliveredStrict2d=rep?true:null;
  m.candidate.sourceUrl=candidate?.startsWith('spartan_weaponry_unofficial:')?'https://www.curseforge.com/minecraft/mc-mods/spartan-weaponry-unofficial/files/8717810':candidate?.startsWith('simplyswords:')?'https://www.curseforge.com/minecraft/mc-mods/simply-swords/files/8746001':candidate?.startsWith('spartan_shields')?'https://github.com/Mai-xiyu/SpartanShields-NeoForge/tree/a2a0eb62a98a2fa22d23e624948b1fa541048ce1':candidate?.startsWith('vanilla_claws:')?'https://github.com/DakotaPride/vanilla-claws-forge/tree/bcad24f8166c0106fdd294bdb833f29fdc5757a0':null;
  m.candidate.licenseEvidence=candidate?.startsWith('spartan_weaponry_unofficial:')?'Apache-2.0 declaration, exact release metadata; per-asset exceptions not cleared, use full dependency':candidate?.startsWith('simplyswords:')?'Timefall Development License 1.2 per existing audit; complete authorized modpack dependency only, no extracted artwork':candidate?.startsWith('minecraft:')?'Minecraft engine resources; reference original resource locations, local PNG excluded from own distribution':candidate?'pinned source only; release and asset-license binding pending':null;
  m.candidate.stateVerification=rep?.delivery==='provider_reference_only'?'representative PNG states and source predicates inspected':'base definition/candidate only; state texture completeness not verified';
}
assert.equal(mappings.length,42);assert.equal(mappings.filter(m=>m.category==='ordinary').length,33);assert.equal(mappings.filter(m=>m.category==='remembrance').length,8);
await write(path.join(out,'six-representatives.json'),{date:'2026-10-14',runtimeIntegrated:false,inGameAccepted:false,referencePlayerHeightBlocks:1.8,representatives});
await write(path.join(reuse,'equipment-mapping-42.json'),{date:'2026-10-14',source:'src/main/resources/assets/eldencombat/routes/2d.json',sourceHash:hash(await fs.readFile(path.join(root,'src/main/resources/assets/eldencombat/routes/2d.json'))),counts:{ordinary:33,remembrance:8,flask:1,total:42,representativeDeliveries:6,remaining:36},note:'Candidate IDs are release/source evidence, never proof of installation; pending runtime routes stay pending. Selected old great/colossal sword models untouched.',mappings});
await fs.writeFile(path.join(reuse,'equipment-mapping-42.md'),'# 装备2D逐项映射（42项）\n\n2026-10-14。发布包/源码候选不代表已安装；全部正式装备2D路由仍 pending。近似类别不算等价覆盖。完整字段见 equipment-mapping-42.json。\n\n| 项目ID | 名称 | 候选真实ID | 适配性质 | 严格2D候选 | 本批状态 |\n|---|---|---|---|---|---|\n'+mappings.map(m=>`| ${m.id} | ${m.name} | ${m.candidate.itemId||'无确认候选'} | ${m.candidate.match==='approximate_category_not_equivalent'?'近似，待确认':m.candidate.itemId?'类别候选':'自有设计补缺'} | ${!m.candidate.itemId?'无外部候选':m.candidate.strict2d===null?'待发布包验证':m.candidate.strict2d?'是':'否/缺口'} | ${m.delivery.representative?'六类代表交付':'待逐项交付'}${m.delivery.protectedOldModel?'；保留选定旧版':''} |`).join('\n')+'\n');
await write(path.join(out,'validation.json'),{staticPass:true,oldSelectedModelsUnchanged:true,representatives:6,mappings:42,ownPngs:ownFiles.length,providerResearchPngs:evidence.entries.length,states:representatives.map(r=>({id:r.id,variants:r.variants.map(v=>({state:v.state,bounds:v.boundsInclusive,components8:v.components8}))})),browserVerified:false,visualReview:false,runtimeIntegrated:false,inGameAccepted:false});
await fs.writeFile(path.join(out,'preview-data.js'),'window.EQUIPMENT = '+JSON.stringify(representatives)+';\n');
await write(path.join(out,'publishable-assets.json'),{date:'2026-10-14',license:'Project-owned derivatives of existing project models; no external pixels',include:ownFiles.map(f=>({file:f.file,sha256:f.sha256})),exclude:['all art/equipment-reuse/research-only-six snapshots','preview screenshots containing provider artwork'],runtimeIntegrated:false});
console.log('PASS 42 mappings, 6 representatives, 4 own PNGs, 6 provider research states, opaque anchors; selected old swords unchanged.');
