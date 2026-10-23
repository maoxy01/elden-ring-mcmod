import {createRequire} from 'node:module';
import fs from 'node:fs';
import assert from 'node:assert/strict';
const require=createRequire('C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json');
const {chromium}=require('playwright');
const browser=await chromium.launch({channel:'msedge',headless:true,args:['--allow-file-access-from-files']});
const checks=[];
try{const page=await browser.newPage();for(const width of [1440,768,390]){
 await page.setViewportSize({width,height:900});await page.goto(new URL('./review.html',import.meta.url).href);
 await page.locator('details').evaluateAll(nodes=>nodes.forEach(n=>n.open=true));
 await page.evaluate(()=>Promise.all([...document.images].map(i=>i.decode())));
 assert.equal(await page.locator('section').count(),28);
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 await page.screenshot({path:new URL('./qa/browser-'+width+'.png',import.meta.url).pathname.replace(/^\/(?:([A-Z]:))/,'$1')});
 checks.push({width,items:28,images:await page.locator('img').count(),overflow:false});
}}finally{await browser.close();}
fs.writeFileSync(new URL('./browser-validation.json',import.meta.url),JSON.stringify(checks,null,2));console.log(checks);
