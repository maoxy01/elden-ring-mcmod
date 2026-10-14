import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';
import {createRequire} from 'node:module';
import assert from 'node:assert/strict';
import {build} from 'esbuild';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const base='art/weapon-proportions',out=path.join(root,base,'size-review');
await fs.mkdir(out,{recursive:true});
const read=async file=>JSON.parse(await fs.readFile(path.join(root,file),'utf8'));
const digest=async file=>createHash('sha256').update(await fs.readFile(path.join(root,file))).digest('hex');
const baselineHashes={};
for(const file of ['targets.json','profiles.json','preview.html'])baselineHashes[file]=await digest(base+'/'+file);
const profiles=await read(base+'/profiles.json');
const ids=['dagger','straight_sword','colossal_sword','spear','shield','sacred_relic_sword'];
const entries=[],candidates=[];
for(const id of ids){
  const entry=profiles.entries.find(e=>e.id==='eldencombat:'+id);assert(entry);
  const model=await read(entry.sourceModel);if(entry.sourceMesh)model.bakedMesh=await read(entry.sourceMesh);
  for(const multiplier of [1,1.15,1.30]){
    const name=entry.name+' / '+(multiplier===1?'当前':multiplier===1.15?'+15%':'+30%');
    const candidate={id:entry.id+'__'+Math.round(multiplier*100),sourceId:entry.id,name,baseName:entry.name,
      reviewMultiplier:multiplier,visualScale:entry.visualScale*multiplier,
      targetLengthBlocks:entry.targetLengthBlocks*multiplier,ratioToPlayer:entry.ratioToPlayer*multiplier};
    candidates.push(candidate);entries.push({...entry,...candidate,model});
  }
}
const textures={};
for(const group of ['weapons','remembrance-game'])textures[group]='data:image/png;base64,'+(await fs.readFile(path.join(root,'art',group,'weapons.png'))).toString('base64');
const player={classic:await read('art/player/player_classic.model.json'),slim:await read('art/player/player_slim.model.json')};
const pack={sizeReview:true,entries,textures,player};
const bundle=(await build({entryPoints:[path.join(root,'art/tooling/weapon-proportions-viewer.mjs')],bundle:true,write:false,format:'iife',minify:true,target:'es2022'})).outputFiles[0].text;
const html=(await fs.readFile(path.join(root,'art/tooling/weapon-proportions-viewer.html'),'utf8')).replace('<head>','<head><base href="../">').replaceAll('武器人物比例','武器尺寸候选');
await fs.writeFile(path.join(out,'preview.html'),html.replace('/* DATA */','window.PROPORTIONS='+JSON.stringify(pack).replaceAll('<','\\u003c')+';').replace('/* BUNDLE */',bundle.replaceAll('</script','<\\/script')));
await fs.writeFile(path.join(out,'candidates.json'),JSON.stringify({status:'review_only',baselineHashes,candidates},null,2)+'\n');
const validation={status:'CANDIDATES_GENERATED',items:6,variants:18,baselineUnchanged:true,inGameAccepted:false};
if(process.argv.includes('--browser')){
  const require=createRequire('C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json');
  const {chromium}=require('playwright'),{PNG}=require('pngjs');
  const browser=await chromium.launch({channel:'msedge',headless:true,args:['--allow-file-access-from-files']});
  const errors=[],screenshots=[];
  try{
    const page=await browser.newPage({viewport:{width:1440,height:960}});page.on('pageerror',e=>errors.push(e.message));
    await page.goto(pathToFileURL(path.join(out,'preview.html')).href);await page.waitForFunction(()=>window.PROPORTIONS_QA?.().loaded);
    await page.screenshot({path:path.join(out,'all-desktop.png'),fullPage:true});screenshots.push('all-desktop.png');
    for(const id of ids){
      await page.selectOption('#weapon','eldencombat:'+id);await page.waitForTimeout(75);
      const qa=await page.evaluate(()=>window.PROPORTIONS_QA());assert.equal(qa.weapons.length,3);
      assert(qa.players.every(h=>Math.abs(h-1.8)<1e-6));
      assert(Math.abs(qa.weapons[1].height/qa.weapons[0].height-1.15)<1e-8);
      assert(Math.abs(qa.weapons[2].height/qa.weapons[0].height-1.30)<1e-8);
      assert(qa.weapons.every(w=>[...w.bounds.min.slice(0,2),...w.bounds.max.slice(0,2)].every(v=>v>-1&&v<1)));
      const png=PNG.sync.read(await page.locator('canvas').screenshot());
      // Weapons sit in the right half of each comparison cell; do not count the mannequin.
      for(let col=0;col<3;col++){
        let pixels=0;const cell=png.width/3;
        for(let y=0;y<png.height;y++)for(let x=Math.ceil((col+.5)*cell);x<Math.floor((col+1)*cell);x++){
          const p=(y*png.width+x)*4;if(png.data[p]<170&&png.data[p+1]<180)pixels++;
        }
        assert(pixels>20,id+' blank weapon');
      }
      const name=id+'.png';await page.screenshot({path:path.join(out,name),fullPage:true});screenshots.push(name);
    }
    await page.selectOption('#mode','hold');await page.waitForTimeout(75);
    const held=await page.evaluate(()=>window.PROPORTIONS_QA());
    assert(held.weapons.every(w=>w.grip.every((v,i)=>Math.abs(v-held.weapons[0].grip[i])<1e-10)));
    await page.screenshot({path:path.join(out,'sacred-grip.png'),fullPage:true});screenshots.push('sacred-grip.png');
    await page.selectOption('#mode','compare');await page.setViewportSize({width:390,height:844});await page.waitForTimeout(75);
    assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
    await page.screenshot({path:path.join(out,'sacred-mobile.png'),fullPage:true});screenshots.push('sacred-mobile.png');
    assert.deepEqual(errors,[]);validation.browser={status:'PASSED',screenshots,errors};
  }finally{await browser.close();}
}
for(const [file,hash]of Object.entries(baselineHashes))assert.equal(await digest(base+'/'+file),hash);
await fs.writeFile(path.join(out,'validation.json'),JSON.stringify(validation,null,2)+'\n');
console.log(JSON.stringify(validation));
