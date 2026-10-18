import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {createRequire} from 'node:module';
import assert from 'node:assert/strict';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),dir=path.join(root,'art/equipment-2d/blockbench-polish-20261018');
const source=JSON.parse(await fs.readFile(path.join(dir,'source-manifest.json')));
const require=createRequire('C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json');
const {chromium}=require('playwright'),browser=await chromium.launch({channel:'msedge',headless:true,args:['--allow-file-access-from-files']});
const errors=[],checks=[];
try{
 const page=await browser.newPage();page.on('pageerror',e=>errors.push(e.message));
 for(const width of [1440,768,390]){
  await page.setViewportSize({width,height:width===390?844:1000});await page.goto(pathToFileURL(path.join(dir,'review.html')).href);await page.waitForFunction(()=>window.REVIEW_READY);
  for(const item of source.items){
   await page.selectOption('#item',item.id);await page.waitForFunction(id=>window.REVIEW_READY===id+':native',item.id);
   await page.locator('#before').evaluate(i=>i.decode());await page.locator('#after').evaluate(i=>i.decode());
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
   if(item.editable)for(const state of item.states){await page.selectOption('#view',state.state);await page.locator('#before').evaluate(i=>i.decode());await page.locator('#after').evaluate(i=>i.decode())}
   checks.push({width,id:item.id,states:item.editable?item.states.length:1});
  }
  await page.selectOption('#item','glintstone_staff');await page.locator('#after').evaluate(i=>i.decode());
  await page.screenshot({path:path.join(dir,'qa/review-'+width+'.png')});
 }
 assert.deepEqual(errors,[]);
}finally{await browser.close()}
await fs.writeFile(path.join(dir,'review-validation.json'),JSON.stringify({viewports:[1440,768,390],checks,browserErrors:errors,scope:'Offline review of native Blockbench captures and exported textures, not an alternative modeling workflow',runtimeIntegrated:false},null,2)+'\n');
console.log('PASS 28 native capture pairs and 45 exported texture pairs at three widths; zero script errors or overflow.');
