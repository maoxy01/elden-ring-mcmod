import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createRequire} from 'node:module';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
const batchTwo=process.argv.includes('--batch-two');
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),out=path.join(root,batchTwo?'art/equipment-2d/identity-batch-two':process.argv.includes('--shape')?'art/equipment-2d/shape-review':'art/equipment-2d/identity-review'),hash=b=>createHash('sha256').update(b).digest('hex');
const data=JSON.parse(await fs.readFile(path.join(out,'manifest.json'),'utf8'));
const captures=batchTwo?path.join(out,'qa-'+Date.now()):out;await fs.mkdir(captures,{recursive:true});
assert.equal(data.audit.length,batchTwo?0:42);assert.equal(data.candidates.length,batchTwo?4:3);
for(const m of data.models||[])assert.equal(hash(await fs.readFile(path.join(out,m.file))),m.sha256);
const selected=new Map();
if(process.argv.includes('--shape')){try{const s=JSON.parse(await fs.readFile(path.join(out,'selection.json'),'utf8'));for(const f of s.selectedArtFiles)selected.set('art/equipment-2d/full/'+f.file,f.sha256)}catch(e){if(e.code!=='ENOENT')throw e}}
const focus=process.argv.find(v=>v.startsWith('--focus='))?.slice(8);
if(focus)assert(data.audit.some(a=>a.id===focus),'Unknown review focus');
for(const a of data.audit.filter(a=>!focus||a.id===focus))for(const f of a.currentPngs)assert.equal(hash(await fs.readFile(path.join(root,f.file))),selected.get(f.file)||f.sha256,'Current source changed');
for(const a of data.candidates){if(a.sourceHash)assert.equal(hash(await fs.readFile(path.join(root,a.source))),a.sourceHash);for(const v of a.variants){assert.equal(hash(await fs.readFile(path.join(out,v.file))),v.sha256);assert.equal(hash(await fs.readFile(path.join(out,v.before))),v.beforeHash);assert.notEqual(v.sha256,v.beforeHash)}}
for(const f of data.protectedFiles)assert.equal(hash(await fs.readFile(path.join(root,f.file))),f.sha256);
const require=createRequire('C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json'),{chromium}=require('playwright'),browser=await chromium.launch({channel:'msedge',headless:true});
const checks=[];try{for(const width of [1280,390]){const page=await browser.newPage({viewport:{width,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(String(e)));await page.goto(`file:///${out.replaceAll('\\','/')}/preview.html`);await page.waitForFunction(()=>window.IDENTITY_READY);await page.locator('img').evaluateAll(xs=>Promise.all(xs.map(x=>x.decode())));await page.locator('#reveal').uncheck();assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));assert(await page.locator('.name').evaluateAll(xs=>xs.every(x=>getComputedStyle(x).display==='none')));assert(await page.locator('.native img').evaluateAll(xs=>xs.every(x=>x.getBoundingClientRect().width===Number(x.width))));
for(const mode of ['no','yes']){await page.locator(`[data-dark=${mode}]`).click();await page.screenshot({path:path.join(captures,`review-${width}-${mode==='no'?'light':'dark'}-hidden.png`),fullPage:true})}
await page.locator('#reveal').check();await page.locator('#left').check();await page.screenshot({path:path.join(captures,`review-${width}-revealed-left.png`),fullPage:true});
if(width===1280){for(let i=0;i<data.candidates.length;i++)await page.locator('#main>section').nth(i).screenshot({path:path.join(captures,`candidate-${i+1}.png`)});
if(batchTwo){await page.evaluate(async()=>{const im=new Image();im.src='../full/sprites/ballista_standby.png';await im.decode();const c=document.createElement('canvas');c.id='ballista-grip';c.width=440;c.height=410;const ctx=c.getContext('2d');ctx.fillStyle='#d6ded9';ctx.fillRect(0,0,440,410);ctx.imageSmoothingEnabled=false;ctx.drawImage(im,48,24,320,320);ctx.strokeStyle='#9eafa4';for(let i=0;i<=32;i+=4){ctx.beginPath();ctx.moveTo(48+i*10,24);ctx.lineTo(48+i*10,344);ctx.moveTo(48,24+i*10);ctx.lineTo(368,24+i*10);ctx.stroke();ctx.fillStyle='#34493c';ctx.font='11px monospace';ctx.fillText(i,45+i*10,14);ctx.fillText(i,24,28+i*10)}for(const [px,py,color] of [[7.5,24.5,'#d22e60'],[12.5,19.5,'#16816b']]){const x=48+px*10,y=24+py*10;ctx.strokeStyle=color;ctx.strokeRect(x-15,y-15,30,30);ctx.beginPath();ctx.moveTo(x-8,y);ctx.lineTo(x+8,y);ctx.moveTo(x,y-8);ctx.lineTo(x,y+8);ctx.stroke()}ctx.fillStyle='#d22e60';ctx.fillText('manifest grip: (7.5, 24.5)',48,370);ctx.fillStyle='#16816b';ctx.fillText('override note: (12.5, 19.5)',48,390);document.body.prepend(c)});await page.locator('#ballista-grip').screenshot({path:path.join(out,'ballista-grip.png')});await page.locator('#ballista-grip').evaluate(e=>e.remove())}}
assert.deepEqual(errors,[]);checks.push({width,noOverflow:true,native16And32:true,hiddenNames:true,revealedNames:true,leftHandCapture:true});await page.close()}}finally{await browser.close()}
const audited=data.audit.filter(a=>!focus||a.id===focus).map(a=>a.id);
data.validation.browser=true;data.validation.captureDirectory=path.relative(root,captures).replaceAll('\\','/');data.validation.sourceHashScope=batchTwo?'candidates_4_and_protected_snapshots_not_42_asset_audit':focus||'all_42';data.validation.sourceAuditEntriesChecked=audited.length;data.validation.browserChecks=checks;
data.validation.staticHashCoverage={auditedIds:audited,candidateCount:data.candidates.length,candidatePngs:data.candidates.reduce((n,a)=>n+a.variants.length,0),modelFiles:(data.models||[]).length,protectedSnapshots:data.protectedFiles.length,note:'Protected snapshots verify unchanged bytes against a recorded baseline; they are not a fresh per-asset source/metadata audit.'};
await fs.writeFile(path.join(out,'manifest.json'),JSON.stringify(data,null,2)+'\n');
await fs.writeFile(path.join(out,'preview-data.js'),'window.IDENTITY='+JSON.stringify(data)+';\n');
console.log(`PASS ${audited.length} source-audit entries, ${data.candidates.length} candidates, ${data.protectedFiles.length} protected snapshots; distinct PNG changes, native16/32, hidden/revealed names, desktop/mobile and left-hand capture.`);
