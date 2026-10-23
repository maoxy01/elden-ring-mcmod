import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {PNG} from './equipment-pixels.mjs';

const project='D:/codexout/mcmod',base='art/equipment-2d/blockbench-all-items-20261018',candidate='art/equipment-2d/mc-polish-20261022',worn='art/equipment-2d/worn-hand-gear-20261021',destination='art/equipment-2d/V0.2';
const absolute=p=>path.join(project,p),read=p=>fs.readFileSync(absolute(p)),json=p=>JSON.parse(read(p)),hash=b=>createHash('sha256').update(b).digest('hex');
const names=new Map(json(base+'/manifest.json').assets.map(a=>[a.id,a.name]));
const originalIds=new Set(['colossal_sword','heavy_thrusting_sword']),wornIds=new Set(['claw','fist']);
const ids=fs.readdirSync(absolute(candidate+'/items')).filter(f=>f.endsWith('.bbmodel')).map(f=>f.slice(0,-8)).sort();
assert.equal(ids.length,28);
assert(!fs.existsSync(absolute(destination)),'V0.2 already exists; refuse to overwrite');
const wornManifest=json(worn+'/manifest.json'),entries=[],copies=[],sourceHashes=new Map();
function planCopy(source,target){
 const bytes=read(source),sha256=hash(bytes);sourceHashes.set(source,sha256);
 const existing=copies.find(f=>f.target===target);
 if(existing){assert.equal(existing.sha256,sha256,'Conflicting shared file '+target);return;}
 copies.push({source,target,sha256,bytes:bytes.length});
}
function samePixels(t,source){
 assert(t.source?.startsWith('data:image/png;base64,'),'Missing embedded bitmap '+t.name);
 const image=PNG.sync.read(Buffer.from(t.source.split(',')[1],'base64')),exported=PNG.sync.read(read(source));
 assert.equal(image.width,exported.width);assert.equal(image.height,exported.height);assert.deepEqual(image.data,exported.data,'Embedded/export mismatch '+source);
 return {width:image.width,height:image.height};
}
for(const id of ids){
 const selection=wornIds.has(id)?'dsh_worn':originalIds.has(id)?'original':'candidate';
 const sourceRoot=selection==='dsh_worn'?worn:selection==='original'?base:candidate;
 const filename=selection==='dsh_worn'?id+'_worn.bbmodel':id+'.bbmodel',modelSource=sourceRoot+(selection==='dsh_worn'?'/':'/items/')+filename,model=json(modelSource);
 const entry={id,name:names.get(id),selection,sourceModel:modelSource,model:'items/'+filename,modelSha256:hash(read(modelSource)),textures:[],icons:[],previews:[],elements:model.elements.length,exportedElements:model.elements.filter(e=>e.export!==false).length,meshCount:model.elements.filter(e=>e.type==='mesh').length};
 planCopy(modelSource,entry.model);
 if(selection==='dsh_worn'){
  assert.equal(entry.modelSha256,wornManifest.items[id].sha256);assert.equal(model.textures.length,1);
  const source=worn+'/textures/worn_atlas.png',dimensions=samePixels(model.textures[0],source);planCopy(source,'textures/worn_atlas.png');
  entry.textures.push({file:'textures/worn_atlas.png',source,sha256:hash(read(source)),...dimensions});
  for(const size of [16,32]){const source=worn+'/icons/'+id+'_gui_'+size+'.png',file='icons/'+id+'_gui_'+size+'.png';planCopy(source,file);entry.icons.push({file,source,sha256:hash(read(source))});}
  entry.renderKind='worn_hand_gear';entry.bindingContract='contracts/WEAR-CONTRACT.md';entry.armPivotRight=wornManifest.items[id].arm_pivot_right;entry.armPivotLeft=wornManifest.items[id].arm_pivot_left;
  for(const [view,name] of [['front','hand_front'],['side','hand_out']]){const source=worn+'/qa/'+id+'_'+name+'.png',target='preview/'+id+'-'+view+'.png';planCopy(source,target);entry.previews.push({view,file:target,source});}
 }else{
  const contract=model.unhandled_root_fields.art_contract.items.find(a=>a.id===id);assert(contract);
  entry.renderKind='pixel_item';entry.worldLengthBlocks=contract.worldLengthBlocks;entry.anchorPixels=contract.anchorPixels;entry.suggestedDisplay=contract.suggestedDisplay;entry.revisions=contract.revisions||[];
  for(const t of model.textures){
   const state=t.name.split('__')[1].replace('.png',''),source=selection==='original'?sourceRoot+'/textures/'+id+'_'+state+'.png':sourceRoot+'/textures/'+t.name,file='textures/'+t.name,dimensions=samePixels(t,source);
   planCopy(source,file);entry.textures.push({state,file,source,sha256:hash(read(source)),...dimensions});
  }
  for(const view of ['front','side']){const source=candidate+'/qa/'+id+'-'+(selection==='original'?'before':'after')+'-'+view+'.png',target='preview/'+id+'-'+view+'.png';planCopy(source,target);entry.previews.push({view,file:target,source});}
 }
 entries.push(entry);
}
planCopy(worn+'/WEAR-CONTRACT.md','contracts/WEAR-CONTRACT.md');
planCopy(worn+'/manifest.json','contracts/dsh-worn-source-manifest.json');
assert.equal(entries.filter(e=>e.selection==='dsh_worn').length,2);assert.equal(entries.filter(e=>e.selection==='original').length,2);assert.equal(entries.filter(e=>e.selection==='candidate').length,24);

fs.mkdirSync(absolute(destination));
for(const copy of copies){const target=absolute(destination+'/'+copy.target);fs.mkdirSync(path.dirname(target),{recursive:true});fs.copyFileSync(absolute(copy.source),target,fs.constants.COPYFILE_EXCL);assert.equal(hash(fs.readFileSync(target)),copy.sha256);}
const manifest={schema:'equipment-art-selection/v1',version:'V0.2',date:'2026-10-22',scope:'28 selected custom equipment assets',sourceMeaning:{dsh_worn:'DSH wearable claw/fist models',original:'Original column in mc-polish review; blockbench-all-items-20261018/items',candidate:'Latest mc-polish-20261022 candidates'},runtimeInstalled:false,items:entries,files:copies};
fs.writeFileSync(absolute(destination+'/manifest.json'),JSON.stringify(manifest,null,2),{flag:'wx'});
const labels={dsh_worn:'dsh 穿戴版',original:'对照页原版',candidate:'最新候选'};
const table=entries.map(e=>`| ${e.name} | ${e.id} | ${labels[e.selection]} | [${path.basename(e.model)}](${e.model}) |`).join('\n');
const readme=`# V0.2 装备美术选版\n\n按2026-10-22用户选版整理，包含28件可编辑Blockbench工程及其贴图。所有工程按源文件原样复制，未重新绘制或转换。\n\n- 抓钩（钩爪）与拳套：dsh的穿戴模型，含左右手几何、共用图集、16/32图标和挂点说明。\n- 特大剑与重刺剑：本轮对照页“原版”，来自 blockbench-all-items-20261018/items；“原版”不是Minecraft自带物品，也不是更早的before历史快照。\n- 其余24件：mc-polish-20261022最新候选，包括碎星收窄版、大矛重做版和大曲剑收窄版。\n\n## 文件\n\n- items/：28份工程；钩爪和拳套保留claw_worn、fist_worn文件名，运行物品ID仍分别为claw、fist。\n- textures/：26件薄片物品的88张状态贴图，以及1张dsh共用图集。\n- icons/：钩爪、拳套各16/32两张图标。\n- preview/：28件的正面和斜侧面参考截图，共56张；dsh两件来自其原生验收截图。\n- contracts/：dsh源挂点说明和源清单，作为原样参考。\n- manifest.json：逐件选版、来源、相对路径、比例、握点和SHA256。\n- validation.json：复制一致性、嵌入贴图和外置PNG一致性、源文件未变检查。\n\n## 选版清单\n\n| 名称 | 物品ID | 采用版本 | 工程 |\n|---|---|---|---|\n${table}\n\n## 使用边界\n\n此目录是选定美术资产合集，未修改正式游戏资源、战斗代码或整合包安装内容。钩爪和拳套为立体穿戴件，保留dsh原有厚度、左右臂枢轴与export=false的参考假人；使用时只导出实际装备组，详见contracts/WEAR-CONTRACT.md。其余26件保留各自的长度、握点、状态贴图与原生撤销历史（源文件具有时）。\n\n选版不等于运行接线或实机验收。工程内已有嵌入PNG，外置PNG同时附带，源目录和用户原稿均保留。\n`;
fs.writeFileSync(absolute(destination+'/README.md'),readme,{flag:'wx'});
for(const [source,expected] of sourceHashes)assert.equal(hash(read(source)),expected,'Source changed '+source);
const result={date:'2026-10-22',items:entries.length,selected:{dsh:2,original:2,candidate:24},models:copies.filter(f=>f.target.endsWith('.bbmodel')).length,materialAndStatePngs:copies.filter(f=>f.target.startsWith('textures/')).length,wornIcons:copies.filter(f=>f.target.startsWith('icons/')).length,previews:copies.filter(f=>f.target.startsWith('preview/')).length,allCopiedFilesHashMatch:true,embeddedExternalPngPixelsMatch:true,sourceFilesChecked:sourceHashes.size,sourceFilesUnchanged:true,runtimeInstalled:false};
fs.writeFileSync(absolute(destination+'/validation.json'),JSON.stringify(result,null,2),{flag:'wx'});
console.log(JSON.stringify({folder:destination,...result},null,2));
