import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {createRequire} from 'node:module';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {PNG} from './equipment-pixels.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),dir=path.join(root,'art/legendary-spells');
const require=createRequire('C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json');
const {chromium}=require('playwright');
const sha=b=>createHash('sha256').update(b).digest('hex');
const read=f=>fs.readFile(path.join(dir,f));
const m=JSON.parse(await read('manifest.json'));
const report={date:'2026-10-17',revision:m.revision,scope:'seven_spell_art_preview_only',staticPass:false,browserPass:false,inGameAccepted:false,runtimeIntegrated:false,sourceHashesChecked:0,protectedFilesChecked:0,frameSamples:0,constructionGridChecked:0,uniqueContentFrames:0,nonemptyFrameSamples:0,browser:[],screenshots:[],warnings:['Some spells share transparent start/end samples; unique count is reported separately.','Composite side-view frames are art storyboards, not production billboard routes.','Original game texture derivatives are not cleared for redistribution.']};
assert.equal(m.assets.length,7);assert.equal(m.runtimeIntegrated,false);
const backupDir=path.join(root,m.previousVersionBackup),backupReceipt=JSON.parse(await fs.readFile(path.join(backupDir,'receipt.json'),'utf8'));
for(const f of backupReceipt.files)assert.equal(sha(await fs.readFile(path.join(backupDir,f.file))),f.sha256,'Backup changed '+f.file);
report.backupFilesVerified=backupReceipt.files.length;
for(const s of m.sources){assert.equal(sha(await fs.readFile(path.join(root,s.source))),s.sha256);assert.equal(sha(await read(s.preview)),s.previewSha256);report.sourceHashesChecked++}
for(const f of m.protectedFiles){assert.equal(sha(await fs.readFile(path.join(root,f.file))),f.sha256,`Protected change ${f.file}`);report.protectedFilesChecked++}
for(const a of m.assets){
 assert.equal(a.frames.length,24);const atlasBytes=await read(a.atlas);assert.equal(sha(atlasBytes),a.atlasSha256);const atlas=PNG.sync.read(atlasBytes);assert.deepEqual([atlas.width,atlas.height],[1536,768]);
 const unique=new Set();let nonempty=0;
 for(const f of a.frames){
  const bytes=await read(f.file);assert.equal(sha(bytes),f.sha256);unique.add(f.sha256);const p=PNG.sync.read(bytes);assert.deepEqual([p.width,p.height],[256,192]);
  const colors=new Set();let count=0;
  assert.equal(m.constructionGridPixels,4);
  for(let y=0;y<p.height;y+=4)for(let x=0;x<p.width;x+=4){const first=p.data.subarray((y*p.width+x)*4,(y*p.width+x)*4+4);for(let j=0;j<4;j++)for(let k=0;k<4;k++){const i=((y+j)*p.width+x+k)*4;assert(first.equals(p.data.subarray(i,i+4)),'Sub-grid noise or smoothing')}}
  report.constructionGridChecked++;
  for(let y=0;y<p.height;y++){
   assert(p.data.subarray(y*p.width*4,(y+1)*p.width*4).equals(atlas.data.subarray(((f.rect[1]+y)*atlas.width+f.rect[0])*4,((f.rect[1]+y)*atlas.width+f.rect[0]+p.width)*4)),'Atlas cell mismatch');
   for(let x=0;x<p.width;x++){const i=(y*p.width+x)*4,alpha=p.data[i+3];if(alpha){count++;colors.add(p.data.subarray(i,i+3).toString('hex'));assert(x>0&&y>0&&x<p.width-1&&y<p.height-1,'Frame clipped at edge');assert([80,128,160,200,230,255].includes(alpha),'Unexpected smoothed alpha')}}
  }
  assert(colors.size<=6,'Outside finite palette');assert.equal(count,f.opaqueOrTranslucentPixels);if(count)nonempty++;report.frameSamples++;
 }
 assert.equal(unique.size,a.uniqueFrames);assert.equal(nonempty,a.nonemptyFrames);assert(nonempty>=22);report.uniqueContentFrames+=unique.size;report.nonemptyFrameSamples+=nonempty;
 await read(a.poster);await read(a.lightPoster);
 assert.equal(sha(await read(a.previousAtlas)),a.previousAtlasSha256);
 const old=await fs.readFile(path.join(root,m.previousVersionBackup,'art/legendary-spells/atlases',a.id+'.png'));assert.equal(sha(old),a.previousAtlasSha256);
 const pair=PNG.sync.read(await read(a.comparison)),poster=PNG.sync.read(await read(a.poster)),before=PNG.sync.read(await read('previous/'+a.id+'.png'));
 assert.deepEqual([pair.width,pair.height],[512,192]);for(let y=0;y<192;y++){assert(pair.data.subarray(y*2048,y*2048+1024).equals(before.data.subarray(y*1024,(y+1)*1024)));assert(pair.data.subarray(y*2048+1024,(y+1)*2048).equals(poster.data.subarray(y*1024,(y+1)*1024)))}
 assert.equal(a.frames.at(-1).opaqueOrTranslucentPixels,0,'Cycle must finish transparent');
}
const html=(await read('preview.html')).toString();
for(const [,link] of html.matchAll(/(?:href|src)="([^"]+)"/g)){if(link.startsWith('#'))continue;await fs.access(path.resolve(dir,link))}
report.staticPass=true;
if(process.argv.includes('--static')){await fs.writeFile(path.join(dir,'static-validation.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));process.exit(0)}
await fs.mkdir(path.join(dir,'qa'),{recursive:true});
const browser=await chromium.launch({channel:'msedge',headless:true,args:['--allow-file-access-from-files']});
const errors=[];
try{
 const page=await browser.newPage();page.on('pageerror',e=>errors.push(e.message));
 for(const width of [1440,768,390]){
  await page.setViewportSize({width,height:width===390?844:width===768?1024:1000});
  await page.goto(pathToFileURL(path.join(dir,'preview.html')).href);await page.waitForFunction(()=>window.ART_REVIEW?.ready);
  await page.evaluate(()=>{window.ART_REVIEW.state.playing=false});
  const bad=await page.evaluate(()=>({overflow:document.documentElement.scrollWidth>innerWidth,broken:[...document.images].filter(i=>!i.complete||!i.naturalWidth).map(i=>i.src)}));assert.equal(bad.overflow,false);assert.deepEqual(bad.broken,[]);
  for(const a of m.assets){
   await page.selectOption('#spell',a.id);assert.equal(await page.locator('#name').textContent(),a.name);
   await page.locator('#time').fill('6');const before=await page.locator('#stage').screenshot();
   await page.locator('#time').fill('15');const after=await page.locator('#stage').screenshot();assert(!before.equals(after),`Frozen ${a.id}`);
   await page.evaluate(id=>{window.ART_REVIEW.select(id);window.ART_REVIEW.state.playing=false;window.ART_REVIEW.render()},a.id);
   const pixels=await page.evaluate(()=>{const d=document.querySelector('#stage').getContext('2d').getImageData(0,0,256,192).data;const c=new Set();for(let i=0;i<d.length;i+=4)c.add([d[i],d[i+1],d[i+2]].join(','));return c.size});assert(pixels>12,'Blank canvas');
   const current=await page.locator('#stage').screenshot();await page.locator('#previous').check();assert.equal(await page.evaluate(()=>window.ART_REVIEW.state.frame),a.peak);const old=await page.locator('#stage').screenshot();assert(!current.equals(old),'Comparison toggle did not change rendered pixels');await page.locator('#previous').uncheck();assert.equal(await page.locator('#name').textContent(),a.name);
   if(width!==768||a.id==='comet_azur'){
    const file=`qa/${a.id}-${width}-dark.png`;await page.screenshot({path:path.join(dir,file)});report.screenshots.push(file);
   }
   if(width===1440){await page.selectOption('#background','light');const file=`qa/${a.id}-${width}-light.png`;await page.screenshot({path:path.join(dir,file)});report.screenshots.push(file);await page.selectOption('#background','dark')}
  }
  await page.selectOption('#background','clear');await page.locator('#time').fill('12');
  assert.equal(await page.evaluate(()=>window.ART_REVIEW.state.background),'clear');await page.selectOption('#speed','0.5');assert.equal(await page.evaluate(()=>window.ART_REVIEW.state.speed),.5);
  await page.locator('#restart').click();assert.equal(await page.evaluate(()=>window.ART_REVIEW.state.frame),0);
  await page.locator('#play').click();const f0=await page.evaluate(()=>window.ART_REVIEW.state.frame);await page.waitForTimeout(600);const f1=await page.evaluate(()=>window.ART_REVIEW.state.frame);assert.notEqual(f0,f1,'Playback did not advance');await page.locator('#play').click();
  await page.locator('.gallery button').nth(3).click();assert.equal(await page.evaluate(()=>window.ART_REVIEW.state.id),m.assets[3].id);
  await page.waitForTimeout(500);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  report.browser.push({width,spellsChecked:7,canvasNonblank:true,frameChanges:true,oldNewTogglePreservesFrame:true,backgroundsChecked:width===1440?['dark','light','clear']:['dark','clear'],playPauseScrubReplaySpeed:true,gallerySelection:true,noHorizontalOverflow:true});
 }
 await page.setViewportSize({width:1440,height:1000});await page.selectOption('#background','dark');await page.evaluate(()=>{window.ART_REVIEW.state.playing=false});await page.screenshot({path:path.join(dir,'qa/collection-1440.png'),fullPage:true});report.screenshots.push('qa/collection-1440.png');
 const reduced=await browser.newPage({reducedMotion:'reduce',viewport:{width:390,height:844}});await reduced.goto(pathToFileURL(path.join(dir,'preview.html')).href);await reduced.waitForFunction(()=>window.ART_REVIEW?.ready);assert.equal(await reduced.evaluate(()=>window.ART_REVIEW.state.playing),false);report.reducedMotionStartsPaused=true;await reduced.close();
 assert.deepEqual(errors,[]);report.browserErrors=errors;report.browserPass=true;
}finally{await browser.close()}
await fs.writeFile(path.join(dir,'validation.json'),JSON.stringify(report,null,2)+'\n');
console.log(`PASS ${report.frameSamples} frames, ${report.uniqueContentFrames} unique contents, ${report.sourceHashesChecked} source hashes, ${report.protectedFilesChecked} protected files, 7 spells at 1440/768/390, ${report.screenshots.length} screenshots. No runtime/game acceptance.`);
