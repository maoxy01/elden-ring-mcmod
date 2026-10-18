import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {PNG,stats,shapes,line,project,backShield,nearestGrip,opaque} from './equipment-pixels.mjs';
import {refuseSelectedRegeneration} from './selected-equipment.mjs';
await refuseSelectedRegeneration();
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),out=path.join(root,'art/equipment-2d/full');
try{await fs.access(path.join(root,'art/equipment-2d/user-edits/selected.json'));throw new Error('User-edited sprites are protected. Use their synchronization tool; bulk regeneration would overwrite manual work.')}catch(e){if(e.code!=='ENOENT')throw e}
const read=async f=>JSON.parse(await fs.readFile(path.join(root,f),'utf8')),hash=b=>createHash('sha256').update(b).digest('hex');
const write=async(f,obj)=>fs.writeFile(path.join(out,f),JSON.stringify(obj,null,2)+'\n');
await fs.mkdir(path.join(out,'sprites'),{recursive:true});
const mapping=await read('art/equipment-reuse/equipment-mapping-42.json'),profiles=(await read('art/weapon-proportions/profiles.json')).entries;
const providers=(await read('art/equipment-reuse/research-only-full/evidence.json')).items;
const oldSelection=await read('art/weapon-proportions/large-sword-version-selection.json');
for(const f of oldSelection.results)assert.equal(hash(await fs.readFile(path.join(root,f.file))),f.sha256,'Selected old sword changed');
const assets=[],published=[];
async function save(id,state,png,anchor){
  const file=`sprites/${id}_${state}.png`,bytes=PNG.sync.write(png);await fs.writeFile(path.join(out,file),bytes);
  const a={state,file,url:file,sha256:hash(bytes),...stats(png),anchorPixels:anchor};published.push({file,sha256:a.sha256});return a;
}
function artStates(id,base){
  if(id==='test_flask'){
    const palette={d:[70,52,45],c:[106,91,60],g:[143,123,81],h:[174,150,99],l:[156,150,132]};
    const rows=['....ddd....','....dcd....','....ccc....','....ghg....','...cghgc...','..cglhggc..','..cglhggc..','..cghgggc..','..cgggggc..','...cgggc...','....ccc....'];
    const ps=[];for(let y=0;y<rows.length;y++)for(let x=0;x<rows[y].length;x++){const color=palette[rows[y][x]];if(color){const xx=(x-5.5)*.55,yy=(4-y)*.55;ps.push({bone:'art_overlay',color,points:[[xx,yy],[xx+.55,yy],[xx+.55,yy+.55],[xx,yy+.55]]})}}
    return [{state:'full',polygons:ps}];
  }
  if(id==='great_bow')return [0,.3,.65,1].map((t,i)=>{
    const ps=base.filter(p=>p.bone!=='string').map(p=>({...p,points:p.points.map(([x,y])=>[x-4*t*Math.max(0,(Math.abs(y)-4)/32),y])}));
    const tipX=-11.5-4*t,center=[-11.5-14*t,0];
    ps.push(line([tipX,-35],center,.85,[168,164,145]),line(center,[tipX,35],.85,[168,164,145]));
    if(i){ps.push(line([center[0],0],[8,0],1.1,[153,128,85]),line([7,-1.5],[9,0],1.2,[185,193,183]),line([9,0],[7,1.5],1.2,[185,193,183]))}
    return {state:i?`pulling_${i-1}`:'standby',polygons:ps,artDrawFraction:t};
  });
  if(id==='ballista')return [0,.3,.65,1,1].map((t,i)=>{
    const ps=base.filter(p=>p.bone!=='string'),center=[0,10.5-12*t];
    ps.push(line([-19.5,10.5],center,.9,[170,159,128]),line(center,[19.5,10.5],.9,[170,159,128]));
    if(i===4){ps.push(line([0,-3],[0,22],1.5,[175,142,87]),line([-2,20],[0,24],1.4,[184,194,194]),line([0,24],[2,20],1.4,[184,194,194]))}
    return {state:i===4?'loaded':i?`loading_${i-1}`:'standby',polygons:ps,artDrawFraction:t};
  });
  return [{state:id==='test_flask'?'full':'front',polygons:base}];
}
// These are local-image grip candidates, not first/third-person game calibration.
const providerGrips={straight_sword:[3.5,12.5],axe:[3.5,12.5],short_bow:[5.5,7.5],crossbow:[9.5,11.5],dagger:[3.5,12.5],thrusting_sword:[4.5,11.5],katana:[3.5,12.5],greataxe:[4.5,11.5],hammer:[4.5,11.5],great_hammer:[4.5,11.5],spear:[12.5,19.5],halberd:[12.5,19.5],scythe:[12.5,19.5],long_bow:[3.5,7.5]};
for(const entry of mapping.mappings){
  const id=entry.id.split(':')[1],profile=profiles.find(p=>p.id===entry.id),provider=providers.find(p=>p.id===id),source=entry.existingAsset.file;
  const sourceBytes=await fs.readFile(path.join(root,source));assert.equal(hash(sourceBytes),entry.existingAsset.sha256,'Source changed since mapping');
  const model=JSON.parse(sourceBytes),shield=id.includes('shield'),variants=[],gui=[];
  const a={id,name:entry.name,category:entry.category,source,sourceHash:hash(sourceBytes),delivery:provider?'provider_reference_only':'own_sprite',strict2d:true,legacySelectionPreserved:['great_sword','colossal_sword'].includes(id),worldLengthBlocks:profile?.targetLengthBlocks??.375,ratioToPlayer:profile?.ratioToPlayer??.375/1.8,ratioEvidence:profile?.confidence??'unmeasured_flask_source_height_draft',variants,gui,axisImageDegrees:shield||id==='test_flask'?-90:-45,interfaces:[],runtimeIntegrated:false,inGameAccepted:false};
  if(provider){
    assert(provider.strict2d);a.providerId=provider.providerId;a.providerEvidence='art/equipment-reuse/research-only-full/evidence.json';a.providerJarSha256=provider.sourceJarSha256;a.installed=null;a.runtimeRegistryVerified=false;
    a.publication='Complete original provider reference only; research PNGs excluded from publication';
    for(const v of provider.variants){
      const texture=v.images.find(t=>t.layer==='layer0');assert(texture);const bytes=await fs.readFile(path.join(root,texture.file));assert.equal(hash(bytes),texture.sha256);const png=PNG.sync.read(bytes);
      const expected=providerGrips[id].map(x=>x);let anchor=nearestGrip(png,expected,1);
      variants.push({state:v.state,file:texture.file,url:'../../../'+texture.file,sha256:texture.sha256,...stats(png),anchorPixels:anchor,providerModel:v.modelId,predicate:v.predicate,providerDisplay:v.display,allLayers:v.images.map(i=>i.textureId)});
    }
    a.axisImageDegrees=-45;a.anchorPixels=variants[0].anchorPixels;
    const p=PNG.sync.read(await fs.readFile(path.join(root,variants[0].file))),coordinates=[];
    for(let y=0;y<p.height;y++)for(let x=0;x<p.width;x++)if(opaque(p,x,y))coordinates.push((x-y)/Math.sqrt(2));
    a.sourceSpanPixels=Math.max(...coordinates)-Math.min(...coordinates)+Math.sqrt(2);
    a.interfaces.push('Resolve original model, custom loader, all textures, overrides and predicates; installation and registry unverified');
    if(id==='hammer')a.shapeNote='Flanged mace category choice, not a literal hammer head; existing reference is a mace.';
  }else{
    const base=shapes(model),states=artStates(id,base),all=states.flatMap(s=>s.polygons),angle=shield||id==='test_flask'?0:45,n=entry.category==='remembrance'?64:32;
    for(const s of states){const p=project(s.polygons,n,angle,all),anchor=nearestGrip(p.png,p.anchor);variants.push({...await save(id,s.state,p.png,anchor),artDrawFraction:s.artDrawFraction});if(!a.anchorPixels){a.anchorPixels=anchor;const ys=states[0].polygons.flatMap(b=>b.points.map(p=>p[1]));a.sourceSpanPixels=(Math.max(...ys)-Math.min(...ys))*p.scale;a.projection={angleDegrees:angle,modelUnitsPerPixel:1/p.scale,boneRestRotationsApplied:true}}}
    if(shield){const p=PNG.sync.read(await fs.readFile(path.join(out,variants[0].file)));variants.push(await save(id,'back',backShield(p,a.anchorPixels),a.anchorPixels));a.blockPose={front:'front',back:'back',pivot:a.anchorPixels,needsTwoSidedMaterial:true};a.interfaces.push('Two-sided front/back material and blocking pose; blocking is a pose, not a duplicate PNG')}
    for(const n of [16,32]){
      let ps=states[0].polygons;
      // GUI-only core reinforcement keeps the characteristic twisted guard and held length intact.
      if(id==='sacred_relic_sword')ps=[...ps,line([0,2],[0,41],n===16?1.8:1.2,[181,174,118]),line([-.8,3],[.4,13],1.6,[205,194,132])];
      const p=project(ps,n,angle,all);gui.push(await save(id,`gui_${n}`,p.png,nearestGrip(p.png,p.anchor)));
    }
    a.publication='Project-owned derivative; no provider pixels';
    if(id==='great_bow'||id==='ballista')a.interfaces.push('Bind named authored art states to agreed gameplay predicates; art fractions are not charge times');
    if(id==='ballista')a.interfaces.push('Loaded bolt is visual only; supported ammunition and firework state undecided');
    if(id==='test_flask'){a.interfaces.push('Full bottle only; empty/consumption/refill behavior undecided');a.artChanges='Own stepped bottle shoulders and neck using existing model palette; not a new gameplay state';}
    if(id==='sacred_relic_sword')a.artChanges='GUI-only 16/32px blade core/root reinforcement; held sprite and world length unchanged';
    if(id==='whip'||id==='flail')a.interfaces.push('Rest-pose sprite only; articulated attack motion is outside the sprite asset');
    if(id==='starscourge_greatsword')a.interfaces.push('Single-blade sprite; paired placement belongs to the runtime, not a two-blades-in-one icon');
  }
  a.anchorMethod=provider?'Provider grip pixel candidate; inspect in contact sheet, game calibration pending':'Projected local grip with at most 3px opaque-pixel snapping, game calibration pending';
  a.leftHand={anchorPixels:[variants[0].size[0]-a.anchorPixels[0],a.anchorPixels[1]],previewMirrorOnly:true,runtime:'Preserve provider left display or calibrate own left hand independently'};
  a.suggestedDisplay={status:'proposal_not_game_verified',uniformXYScale:a.worldLengthBlocks*variants[0].size[0]/a.sourceSpanPixels,anchorLocalModelUnits:[a.anchorPixels[0]/variants[0].size[0]*16-8,8-a.anchorPixels[1]/variants[0].size[0]*16,0],gui:'Separate 16/32 assets when provided; never use GUI fit to set world length',firstPerson:'Per-item camera offset and palm calibration required',thirdPerson:'Map anchor to palm after a single item-axis rotation'};
  a.interfaces.push('Per-item display, GUI selection, both hands, classic/slim, first/third-person validation');
  assets.push(a);
}
assert.equal(assets.length,42);assert.equal(assets.filter(a=>a.delivery==='own_sprite').length,28);
await write('equipment-42.json',{date:'2026-10-15',counts:{total:42,own:28,provider:14},referencePlayerHeightBlocks:1.8,assets});
await write('publishable-assets.json',{date:'2026-10-15',include:published,exclude:['All research-only-full provider textures','Previews/screenshots containing provider artwork'],runtimeIntegrated:false});
await write('validation.json',{staticPass:false,oldSelectedModelsUnchanged:true,counts:{items:42,ownPngs:published.length,providerModelStates:providers.reduce((n,p)=>n+p.variants.length,0)},disconnectedOwn:assets.filter(a=>a.delivery==='own_sprite').flatMap(a=>[...a.variants,...a.gui].filter(v=>v.components8.length>1).map(v=>({id:a.id,state:v.state,components:v.components8}))),browserVerified:false,visualReview:false,runtimeIntegrated:false,inGameAccepted:false});
await fs.writeFile(path.join(out,'preview-data.js'),'window.EQUIPMENT = '+JSON.stringify(assets)+';\n');
const updated={...mapping,date:'2026-10-15',counts:{ordinary:33,remembrance:8,flask:1,total:42,ownSpriteDeliveries:28,completeProviderMappings:14,remainingArtEntries:0},fullDelivery:'art/equipment-2d/full/equipment-42.json',note:'Art files/provider definitions delivered. Dependency installation, runtime integration and game acceptance remain separate and unverified.',mappings:mapping.mappings.map(m=>{const a=assets.find(a=>'eldencombat:'+a.id===m.id);return {...m,delivery:{...m.delivery,method:a.delivery,artStatus:'full_art_delivery_pending_human_review',inGameAccepted:false},selectedProvider:a.providerId||null,fullAssetManifest:'art/equipment-2d/full/equipment-42.json#'+a.id,size:{...m.size,grip:a.anchorPixels,targetLengthBlocks:a.worldLengthBlocks},missingStates:a.interfaces,nextAction:'Runtime owner reviews full asset manifest and completes interfaces; do not mark routes ready automatically'}})};
await fs.writeFile(path.join(root,'art/equipment-reuse/equipment-mapping-42.json'),JSON.stringify(updated,null,2)+'\n');
await fs.writeFile(path.join(root,'art/equipment-reuse/equipment-mapping-42.md'),'# 装备2D交付映射（42项）\n\n2026-10-15。28项自有素材、14项完整提供方映射。素材交付不等于依赖已安装或游戏验收；旧候选字段为历史研究，实际选择见 selectedProvider 和 fullAssetManifest。\n\n| 项目ID | 名称 | 本次交付 | 状态数 | 目标长度（格） |\n|---|---|---|---|---|\n'+assets.map(a=>`| ${a.id} | ${a.name} | ${a.providerId||'自有PNG'} | ${a.variants.length}${a.blockPose?'+格挡姿态':''} | ${a.worldLengthBlocks.toFixed(3)} |`).join('\n')+'\n');
console.log('Generated 42 entries, 28 own sprites with GUI variants, 14 fully traced provider mappings. Inspect validation before acceptance.');
