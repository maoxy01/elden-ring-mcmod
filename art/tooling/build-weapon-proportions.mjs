import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {build} from 'esbuild';
import {createAsset} from './player-scene.mjs';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const output=path.join(root,'art/weapon-proportions');
const read=async p=>JSON.parse(await fs.readFile(path.join(root,p),'utf8'));
const sha=b=>createHash('sha256').update(b).digest('hex');
const sources=new Map();
async function input(p) {
  const bytes=await fs.readFile(path.join(root,p)); sources.set(p,sha(bytes)); return bytes;
}
const targets=await read('art/weapon-proportions/targets.json');
const table=new Map(targets.weapons.map(w=>[w.id,w]));
assert.equal(table.size,41); assert.equal(targets.weapons.length,41);
const entries=[],preview=[],textures={};
for(const collection of ['weapons','remembrance-game']) {
  const dir='art/'+collection;
  const catalog=JSON.parse(await input(dir+'/catalog.json'));
  textures[collection]='data:image/png;base64,'+(await input(dir+'/weapons.png')).toString('base64');
  for(const w of catalog.weapons) {
    const id=w.id.split(':')[1],target=table.get(id); assert(target,id);
    const model=JSON.parse(await input(dir+'/'+w.model));
    if(model.bakedMeshPath)model.bakedMesh=JSON.parse(await input(dir+'/'+model.bakedMeshPath));
    const asset=createAsset(model,null); asset.root.updateMatrixWorld(true);
    const bounds=asset.bounds(),height=bounds.max.y-bounds.min.y;
    const length=target.ratio*targets.referencePlayerHeightBlocks,scale=length/height;
    assert(Number.isFinite(scale)&&scale>0&&height>0);
    assert.deepEqual(w.grip,[0,0,0]);
    const item=await read('src/main/resources/assets/eldencombat/models/item/'+id+'.json');
    const display=await read('src/main/resources/assets/eldencombat/models/'+item.parent.split(':')[1]+'.json');
    const displayScales=Object.fromEntries(['firstperson_righthand','firstperson_lefthand','thirdperson_righthand','thirdperson_lefthand'].map(view=>{
      const s=display.display[view].scale; assert(s.every(v=>v===s[0])); return [view,s[0]];
    }));
    const entry={id:w.id,name:w.name,collection,referenceWeapon:target.referenceWeapon,
      referenceUrl:targets.references[target.reference],confidence:target.confidence||'provisional_art_estimate',
      evidence:target.evidence||'Named reference for subsequent equipped-image calibration; numeric ratio is an authored draft, not a measured game dimension.',
      ratioToPlayer:target.ratio,referencePlayerHeightBlocks:targets.referencePlayerHeightBlocks,
      sourceModel:dir+'/'+w.model,sourceMesh:model.bakedMeshPath?dir+'/'+model.bakedMeshPath:null,
      sourceModelSha256:sources.get(dir+'/'+w.model),sourceBoundsBlocks:{min:bounds.min.toArray(),max:bounds.max.toArray()},
      sourceHeightBlocks:height,targetLengthBlocks:length,visualScale:scale,
      mainGripModelUnits:w.grip,secondaryGripModelUnits:w.secondaryGrip,
      scaledSecondaryGripBlocks:w.secondaryGrip?.map(v=>v/model.unitsPerBlock*scale)||null,
      currentDisplayScales:displayScales,
      suggestedLocalScaleForCurrentDisplay:Object.fromEntries(Object.entries(displayScales).map(([view,s])=>[view,scale/s])),
      paired:w.paired||false, runtimeIntegrated:false};
    entries.push(entry); preview.push({...entry,model});
    for(const mesh of asset.meshes)mesh.geometry.dispose();
  }
}
assert.equal(entries.length,41); assert.equal(new Set(entries.map(e=>e.id)).size,41);
const player={};
for(const type of ['classic','slim']) player[type]=JSON.parse(await input('art/player/player_'+type+'.model.json'));
const playerTexture='data:image/png;base64,'+(await input('art/player/player-preview.png')).toString('base64');
const profiles={schemaVersion:1,status:targets.status,referencePlayerHeightBlocks:targets.referencePlayerHeightBlocks,
  measurement:targets.measurement,geometryScaleBaked:false,runtimeIntegrated:false,entries};
await fs.writeFile(path.join(output,'profiles.json'),JSON.stringify(profiles,null,2)+'\n');
await fs.writeFile(path.join(output,'source-manifest.json'),JSON.stringify({files:[...sources].map(([file,sha256])=>({file,sha256}))},null,2)+'\n');
const pack={...profiles,entries:preview,textures,player,playerTexture};
const bundle=(await build({entryPoints:[path.join(root,'art/tooling/weapon-proportions-viewer.mjs')],bundle:true,write:false,format:'iife',minify:true,target:'es2022'})).outputFiles[0].text;
const html=await fs.readFile(path.join(root,'art/tooling/weapon-proportions-viewer.html'),'utf8');
await fs.writeFile(path.join(output,'preview.html'),html.replace('/* DATA */','window.PROPORTIONS='+JSON.stringify(pack).replaceAll('<','\\u003c')+';').replace('/* BUNDLE */',bundle.replaceAll('</script','<\\/script')));
const rows=entries.map(e=>`| ${e.name} | \`${e.id.split(':')[1]}\` | ${e.referenceWeapon} | ${e.ratioToPlayer.toFixed(2)} | ${e.targetLengthBlocks.toFixed(3)} | ${e.sourceHeightBlocks.toFixed(4)} | ${e.visualScale.toFixed(6)} | ${e.confidence==='provisional_art_estimate'?'待参考校准':'截图估算'} |`);
await fs.writeFile(path.join(output,'SCALE-TABLE.md'),`# 武器与人物比例草案\n\n由 targets.json 生成；41 件独立配置。参考人偶身高 1.8 格，不把碰撞箱高度等同于游戏实际可见身高。神躯化剑有已查看的持握截图，其余 40 件是待校准的美术初值。不是官方或源模型实测数据。\n\n目标长度 = 人物高度 × 比例；visualScale = 目标长度 / 当前模型静止 Y 跨度。原点、几何与图集均未改写。当前游戏尚未消费此表。\n\n| 武器 | ID | 对应法环参考 | 武器/人高 | 目标格数 | 当前格数 | 网格倍率 | 依据状态 |\n| --- | --- | --- | ---: | ---: | ---: | ---: | --- |\n${rows.join('\n')}\n`);
console.log(JSON.stringify({assets:entries.length,output,sourceFiles:sources.size,runtimeIntegrated:false}));
