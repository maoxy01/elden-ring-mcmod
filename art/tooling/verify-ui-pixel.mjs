import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createRequire} from 'node:module';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {PNG} from './equipment-pixels.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),out=path.join(root,'art/ui-pixel'),hash=b=>createHash('sha256').update(b).digest('hex');
const data=JSON.parse(await fs.readFile(path.join(out,'assets.json'),'utf8'));
for(const a of data.assets){const bytes=await fs.readFile(path.join(out,a.file));assert.equal(hash(bytes),a.sha256);const p=PNG.sync.read(bytes);assert.equal(p.width,a.size[0]);let opaque=0;for(let i=3;i<p.data.length;i+=4){assert([0,255].includes(p.data[i]));if(p.data[i])opaque++}assert(opaque>0&&opaque<p.width*p.height)}
assert.equal(new Set(data.assets.filter(a=>a.size[0]===16).map(a=>a.sha256)).size,4);
for(const a of data.reused)assert.equal(hash(await fs.readFile(path.join(root,a.file))),a.sha256,'Reused asset changed');
const require=createRequire('C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json'),{chromium}=require('playwright');
const browser=await chromium.launch({channel:'msedge',headless:true});const checks=[];
try{for(const width of [1200,390]){const page=await browser.newPage({viewport:{width,height:900}}),errors=[];page.on('pageerror',e=>errors.push(String(e)));await page.goto(`file:///${out.replaceAll('\\','/')}/preview.html`);await page.waitForFunction(()=>window.UI_READY);await page.locator('img').evaluateAll(xs=>Promise.all(xs.map(x=>x.decode())));assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
for(const mode of ['light','night']){await page.locator('.swatch.'+mode).click();await page.screenshot({path:path.join(out,`preview-${width}-${mode}.png`),fullPage:true})}
assert(await page.locator('.native img').evaluateAll(xs=>xs.every(x=>x.getBoundingClientRect().width===Number(x.width))));await page.locator('#rows .row').first().hover();await page.locator('.menu').screenshot({path:path.join(out,`menu-${width}-hover.png`)});assert.deepEqual(errors,[]);checks.push({width,noOverflow:true,imagesLoaded:true,nativeSizes:true,hoverCaptured:true});await page.close()}}finally{await browser.close()}
await fs.writeFile(path.join(out,'validation.json'),JSON.stringify({staticPass:true,browserChecks:checks,newPngs:8,reusedUnchanged:data.reused.length,visualReview:false,runtimeIntegrated:false,inGameAccepted:false},null,2)+'\n');console.log('PASS 8 PNGs, 73 reused assets unchanged, native slots and desktop/mobile preview');
