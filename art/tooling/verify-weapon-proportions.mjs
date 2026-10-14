import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {createRequire} from 'node:module';
import {fileURLToPath,pathToFileURL} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const output=path.join(root,'art/weapon-proportions');
const json=async file=>JSON.parse(await fs.readFile(path.join(output,file),'utf8'));
const profiles=await json('profiles.json'),manifest=await json('source-manifest.json');
assert.equal(profiles.entries.length,41);assert.equal(new Set(profiles.entries.map(e=>e.id)).size,41);
for(const item of manifest.files)assert.equal(createHash('sha256').update(await fs.readFile(path.join(root,item.file))).digest('hex'),item.sha256,item.file+' changed since export');
for(const e of profiles.entries){
  assert(Number.isFinite(e.visualScale)&&e.visualScale>0);
  assert(Math.abs(e.sourceHeightBlocks*e.visualScale-e.ratioToPlayer*profiles.referencePlayerHeightBlocks)<1e-8);
  assert.deepEqual(e.mainGripModelUnits,[0,0,0]);
  for(const [view,s] of Object.entries(e.suggestedLocalScaleForCurrentDisplay))
    assert(Math.abs(s*e.currentDisplayScales[view]-e.visualScale)<1e-8);
}
const length=id=>profiles.entries.find(e=>e.id==='eldencombat:'+id).targetLengthBlocks;
assert(length('dagger')<length('straight_sword')&&length('straight_sword')<length('colossal_sword')&&length('colossal_sword')<length('spear'));
assert(length('small_shield')<length('shield')&&length('shield')<length('great_shield'));
const result={status:'ART_PROFILE_CHECKS_PASSED',assets:41,sourceFilesUnchanged:manifest.files.length,
  equippedImageEstimates:1,provisionalEstimates:40,runtimeIntegrated:false,inGameAccepted:false};
if(process.argv.includes('--browser')){
  const require=createRequire('C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json');
  const {chromium}=require('playwright'),{PNG}=require('pngjs');
  const browser=await chromium.launch({channel:'msedge',headless:true,args:['--allow-file-access-from-files']});
  const errors=[],captures=[],checks=[];
  try{
    const page=await browser.newPage({viewport:{width:1440,height:1024}});
    page.on('pageerror',e=>errors.push(e.message));
    await page.goto(pathToFileURL(path.join(output,'preview.html')).href);
    await page.waitForFunction(()=>window.PROPORTIONS_QA?.().loaded);
    for(const viewport of [{width:1440,height:1024},{width:390,height:844}]){
      await page.setViewportSize(viewport);
      for(const selection of ['remembrance','featured','all']){
        await page.selectOption('#weapon',selection);
        await page.waitForTimeout(100);
        const qa=await page.evaluate(()=>window.PROPORTIONS_QA());
        assert.equal(qa.weapons.length,selection==='all'?41:selection==='featured'?6:8);
        assert(qa.players.every(h=>Math.abs(h-1.8)<1e-6));
        assert(qa.weapons.every(w=>[...w.bounds.min.slice(0,2),...w.bounds.max.slice(0,2)].every(v=>v>-1&&v<1)),'Clipped model');
        assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Page overflow');
        const canvas=await page.locator('canvas').screenshot(),png=PNG.sync.read(canvas);
        // Count colored pixels per cell, excluding the neutral background and rulers.
        for(let i=0;i<qa.weapons.length;i++){
          let count=0;
          const x0=Math.floor((i%qa.columns)*png.width/qa.columns),x1=Math.floor((i%qa.columns+1)*png.width/qa.columns);
          const y0=Math.floor(Math.floor(i/qa.columns)*png.height/qa.rows),y1=Math.floor((Math.floor(i/qa.columns)+1)*png.height/qa.rows);
          for(let y=y0;y<y1;y++)for(let x=x0;x<x1;x++){const p=(y*png.width+x)*4;if(png.data[p]<170&&png.data[p+1]<180)count++;}
          assert(count>40,qa.weapons[i].id+' blank canvas cell');
        }
        const name=selection+'-'+viewport.width+'.png';
        await page.screenshot({path:path.join(output,name),fullPage:true});captures.push(name);checks.push({width:viewport.width,selection,weapons:qa.weapons.length});
      }
    }
    await page.setViewportSize({width:1280,height:900});
    await page.selectOption('#weapon','eldencombat:sacred_relic_sword');
    await page.selectOption('#mode','hold');
    for(const skin of ['classic','slim']){
      await page.selectOption('#skin',skin);await page.locator('#angle').fill('25');await page.locator('#angle').dispatchEvent('input');
      await page.waitForTimeout(100);const qa=await page.evaluate(()=>window.PROPORTIONS_QA());
      assert.equal(qa.skin,skin);assert.equal(qa.mode,'hold');assert.equal(qa.weapons.length,1);
      assert(qa.weapons[0].grip.every(Number.isFinite));
      const name='grip-'+skin+'.png';await page.screenshot({path:path.join(output,name),fullPage:true});captures.push(name);
    }
    assert.deepEqual(errors,[]);result.browser={status:'PASSED',captures,checks,errors};
  }finally{await browser.close();}
}
await fs.writeFile(path.join(output,'validation.json'),JSON.stringify(result,null,2)+'\n');
console.log(JSON.stringify(result));
