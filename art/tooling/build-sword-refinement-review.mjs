import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {createRequire} from 'node:module';
import {build} from 'esbuild';
import assert from 'node:assert/strict';
import {createAsset} from './player-scene.mjs';
const utility=process.argv.includes('--utility'),blades=process.argv.includes('--blades'),ids=blades?['dagger','thrusting_sword','heavy_thrusting_sword','curved_sword','katana','twinblade']:utility?['spear','shield','long_bow']:['straight_sword','great_sword','colossal_sword'];
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),out=path.join(root,'art/weapon-proportions',blades?'blades-review':utility?'utility-review':'sword-review');
await fs.mkdir(out,{recursive:true});
const read=async p=>JSON.parse(await fs.readFile(path.join(root,p),'utf8'));
const profiles=await read('art/weapon-proportions/profiles.json'),entries=[];
for(const id of ids){
  const entry=profiles.entries.find(e=>e.id==='eldencombat:'+id);
  for(const [version,prefix] of [['旧版','art/backups/'+(blades?'six-blades-before-refinement-20261012':utility?'spear-shield-bow-before-refinement-20261012':'swords-before-refinement-20261012')+'/'],['当前','']]){
    const model=await read(prefix+'art/weapons/'+id+'.model.json');if(model.bakedMeshPath)model.bakedMesh=await read(prefix+'art/weapons/'+model.bakedMeshPath);
    const asset=createAsset(model,null);asset.root.updateMatrixWorld(true);const bounds=asset.bounds();
    for(const [view,viewAngle] of [['正面',0],['侧面',Math.PI/2]])entries.push({...entry,
      id:entry.id+'__'+version+'__'+view,sourceId:entry.id,name:entry.name+' / '+version+' '+view,baseName:entry.name,
      reviewMultiplier:version==='旧版'&&view==='正面'?1:0,viewAngle,model,
      sourceBoundsBlocks:{min:bounds.min.toArray(),max:bounds.max.toArray()},sourceHeightBlocks:bounds.max.y-bounds.min.y,
      visualScale:entry.targetLengthBlocks/(bounds.max.y-bounds.min.y)});
  }
}
// Keep old/new frontal views adjacent, followed by the two thin side profiles.
for(let i=0;i<entries.length;i+=4)[entries[i+1],entries[i+2]]=[entries[i+2],entries[i+1]];
const texture='data:image/png;base64,'+(await fs.readFile(path.join(root,'art/weapons/weapons.png'))).toString('base64');
const pack={sizeReview:true,reviewTitle:blades?'六类刃器对照':utility?'矛盾弓版本对照':'三类剑版本对照',reviewColumns:4,summary:blades?'六类独立轮廓 · 同尺度新旧对照 · 实机待验':utility?'矛 / 中盾 / 长弓 · 同尺度旧版与当前新版 · 实机待验':'直剑当前为新版 · 大剑与特大剑当前为旧版 · 实机待验',entries,textures:{weapons:texture},
  player:{classic:await read('art/player/player_classic.model.json'),slim:await read('art/player/player_slim.model.json')}};
const bundle=(await build({entryPoints:[path.join(root,'art/tooling/weapon-proportions-viewer.mjs')],bundle:true,write:false,format:'iife',minify:true,target:'es2022'})).outputFiles[0].text;
const html=(await fs.readFile(path.join(root,'art/tooling/weapon-proportions-viewer.html'),'utf8')).replace('<head>','<head><base href="../">').replaceAll('武器人物比例',blades?'六类刃器精修对照':utility?'矛盾弓精修对照':'剑型版本对照');
await fs.writeFile(path.join(out,'preview.html'),html.replace('/* DATA */','window.PROPORTIONS='+JSON.stringify(pack).replaceAll('<','\\u003c')+';').replace('/* BUNDLE */',bundle.replaceAll('</script','<\\/script')));
if(process.argv.includes('--browser')){
  const require=createRequire('C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json'),{chromium}=require('playwright');
  const browser=await chromium.launch({channel:'msedge',headless:true,args:['--allow-file-access-from-files']});const errors=[],shots=[];
  try{
    const page=await browser.newPage({viewport:{width:1440,height:960}});page.on('pageerror',e=>errors.push(e.message));
    await page.goto(pathToFileURL(path.join(out,'preview.html')).href);await page.waitForFunction(()=>window.PROPORTIONS_QA?.().loaded);
    const qa=await page.evaluate(()=>window.PROPORTIONS_QA());assert.equal(qa.weapons.length,ids.length*4);
    for(let i=0;i<ids.length*4;i+=4)assert(Math.abs(qa.weapons[i].height-qa.weapons[i+1].height)<1e-8);
    await page.screenshot({path:path.join(out,'old-new-front-side.png'),fullPage:true});shots.push('old-new-front-side.png');
    for(const id of ids){
      await page.selectOption('#weapon','eldencombat:'+id);await page.selectOption('#mode','hold');await page.waitForTimeout(100);
      const name=id+'-grip.png';await page.screenshot({path:path.join(out,name),fullPage:true});shots.push(name);
    }
    await page.selectOption('#mode','compare');
    if(utility){await page.selectOption('#weapon','eldencombat:shield');await page.check('#reverse');await page.screenshot({path:path.join(out,'shield-back.png'),fullPage:true});shots.push('shield-back.png');await page.uncheck('#reverse');}
    await page.setViewportSize({width:390,height:844});
    await page.waitForFunction(()=>window.PROPORTIONS_QA().columns===1);
    assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));assert.deepEqual(errors,[]);
    await page.screenshot({path:path.join(out,'mobile.png'),fullPage:true});shots.push('mobile.png');
    await fs.writeFile(path.join(out,'validation.json'),JSON.stringify({status:'PASSED',shots,errors,inGameAccepted:false},null,2)+'\n');
  }finally{await browser.close();}
}
console.log(JSON.stringify({out,variants:entries.length}));
