import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createRequire} from 'node:module';
import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import assert from 'node:assert/strict';
import {PNG,opaque,stats} from './equipment-pixels.mjs';
import {assertSelectedArt} from './selected-equipment.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),full='art/equipment-2d/full/',batch='art/equipment-2d/identity-batch-two/';
const bytes=f=>fs.readFile(path.join(root,f)),read=async f=>JSON.parse(await bytes(f)),hash=b=>createHash('sha256').update(b).digest('hex');
const selected=await assertSelectedArt();assert(selected);
const receipt=await read(batch+'selection.json'),data=await read(full+'equipment-42.json'),pub=await read(full+'publishable-assets.json'),t=await read('art/item-thickness/manifest.json'),d=await read(batch+'manifest.json');
const maleniaReceipt=await read(batch+'malenia-selection.json'),receipts=[receipt,maleniaReceipt],files=receipts.flatMap(r=>r.selectedArtFiles.map(f=>({...f,backup:r.backup})));
const old=await read(receipt.backup+'/'+full+'equipment-42.json'),oldPub=await read(receipt.backup+'/'+full+'publishable-assets.json'),oldT=await read(receipt.backup+'/art/item-thickness/manifest.json');
const batchIds=['flail','heavy_thrusting_sword','malikeths_black_blade','hand_of_malenia'],ids=selected.assets.filter(a=>batchIds.includes(a.id)).map(a=>a.id);assert.deepEqual(ids,batchIds);assert.equal(files.length,12);
const laterStarscourge=selected.assets.find(a=>a.id==='starscourge_greatsword');
if(laterStarscourge){const r=await read(batch+'starscourge-selection.json');assert.deepEqual(data.assets.find(a=>a.id===laterStarscourge.id),r.assetAfter)}
const uiDiffs=[
 {file:full+'preview.html',before:'<a href="../preview.html">六类样板</a>',after:'<a href="../identity-batch-two/preview.html#hand_of_malenia">义手刀修改对照</a> · <a href="selected-art.json">本批已选版本</a> · <a href="../preview.html">六类样板</a>'},
 {file:full+'preview.js',before:'美术候选 · 未实机验收',after:"${a.artRevision?.status==='user_selected_art_current'?'用户已选 · 当前美术':'美术候选'} · 未实机验收"}
];
for(const f of uiDiffs){const text=(await bytes(f.file)).toString('utf8');assert.equal(text.split(f.after).length,2);assert.equal(hash(Buffer.from(text.replace(f.after,f.before))),receipt.beforeSnapshot.find(x=>x.file===f.file).sha256)}
for(const f of files){
 assert.equal(hash(await bytes(f.file)),f.sha256);assert.equal(hash(await bytes(f.candidate)),f.sha256);assert.equal(hash(await bytes(f.backup+'/'+f.file)),f.previousSha256);
 const a=data.assets.find(a=>a.id===f.id),v=[...a.variants,...a.gui].find(v=>v.state===f.state),p=pub.include.find(p=>full+p.file===f.file),m=t.models.find(m=>m.id==='eldencombat:'+f.id&&m.state===f.state);
 assert.equal(v.sha256,f.sha256);assert.equal(p.sha256,f.sha256);assert.equal(m.textures[0].sha256,f.sha256);assert.equal(m.textures[0].source,f.file);assert.equal(m.thicknessUnits,1);
 const model=await read('art/item-thickness/'+m.file);assert.equal(model.parent,'minecraft:item/generated');assert.equal(model.textures.layer0,m.textures[0].resource);assert.equal(hash(await bytes('art/item-thickness/'+m.file)),m.sha256);
 const png=PNG.sync.read(await bytes(f.file));assert.deepEqual([png.width,png.height],v.size);assert(opaque(png,...v.anchorPixels));assert.equal(stats(png).components8.length,1);
}
for(const a of data.assets){const prior=old.assets.find(x=>x.id===a.id);if(!ids.includes(a.id)&&a.id!==laterStarscourge?.id)assert.deepEqual(a,prior,'Unrelated metadata changed: '+a.id);else if(ids.includes(a.id)){
 for(const k of ['source','sourceHash','worldLengthBlocks','ratioToPlayer','anchorPixels','sourceSpanPixels','suggestedDisplay','leftHand','projection','axisImageDegrees'])assert.deepEqual(a[k],prior[k],a.id+' '+k);
 for(const v of [...a.variants,...a.gui])assert.deepEqual(v.anchorPixels,[...prior.variants,...prior.gui].find(x=>x.state===v.state).anchorPixels);
 assert.equal(a.artRevision.status,'user_selected_art_current');assert.equal(d.candidates.find(x=>x.id===a.id).status,'user_selected_art_current');
}}
for(const p of pub.include)if(!files.some(f=>f.file===full+p.file)){const later=laterStarscourge?.files.find(f=>f.file===full+p.file);if(later)assert.equal(p.sha256,later.sha256);else assert.deepEqual(p,oldPub.include.find(x=>x.file===p.file))}
assert.deepEqual(t.entries,oldT.entries);assert.deepEqual(t.sourceRevisions,oldT.sourceRevisions);
for(const m of t.models){const prior=oldT.models.find(x=>x.file===m.file);if(!ids.some(id=>m.id==='eldencombat:'+id))assert.deepEqual(m,prior);else assert.deepEqual({...m,textures:prior.textures},prior)}
const allowed=new Set([...receipts.flatMap(r=>r.expectedChangedFiles),...uiDiffs.map(f=>f.file)]);let unchanged=0;
for(const f of receipt.beforeSnapshot)if(!allowed.has(f.file)){assert.equal(hash(await bytes(f.file)),f.sha256,'Unrelated snapshot changed: '+f.file);unchanged++}
const latestAllowed=new Set([...maleniaReceipt.expectedChangedFiles,...uiDiffs.map(f=>f.file)]);
for(const f of maleniaReceipt.beforeSnapshot)if(!latestAllowed.has(f.file))assert.equal(hash(await bytes(f.file)),f.sha256,'Changed after first three selections: '+f.file);
for(const f of d.protectedFiles)assert.equal(hash(await bytes(f.file)),f.sha256,'Protected snapshot: '+f.file);
assert.equal((await bytes(full+'preview-data.js')).toString(),'window.EQUIPMENT = '+JSON.stringify(data.assets)+';\n');
assert.equal((await bytes(batch+'preview-data.js')).toString(),'window.IDENTITY='+JSON.stringify(d)+';\n');
assert.equal(d.candidates.find(a=>a.id==='hand_of_malenia').status,'user_selected_art_current');
assert.equal(d.sideChecks.starscourge.status,laterStarscourge?'user_selected_art_metadata_synced':'blocked_pending_provenance_and_selection');
// Exercise refusal before either generator can write; inspect all receipt snapshots again afterwards.
const guards=[];for(const entry of ['build-equipment-full.mjs','build-equipment-batch-two.mjs','refine-malenia-curve.mjs']){
 const r=spawnSync(process.execPath,[path.join(root,'art/tooling',entry)],{cwd:root,encoding:'utf8'});assert.equal(r.status,1);const blocked=entry==='refine-malenia-curve.mjs'?['hand_of_malenia']:ids;assert(r.stderr.includes('Selected art is protected: '+blocked.join(', ')));guards.push({entry,refusedBeforeWrites:true});
}
await assertSelectedArt();for(const f of receipt.beforeSnapshot)if(!allowed.has(f.file))assert.equal(hash(await bytes(f.file)),f.sha256);
if(process.argv.includes('--check-only')){console.log('PASS four-weapon selection checks; later explicit Starscourge selection verified separately; no browser or evidence rewrite.');process.exit(0)}
const dir=batch+'selection-qa-'+Date.now();await fs.mkdir(path.join(root,dir),{recursive:true});
const result={date:'2026-10-16',scope:{selectedIds:ids,pngs:12,modelTextureReferences:12,unchangedSnapshotFiles:unchanged,sourceAuditEntries:0,note:'No fresh all-42 source/metadata audit; unrelated snapshots preserved. Any later explicit Starscourge selection is verified against its separate receipt.'},staticPass:true,guards,browser:[],authorVisualReview:false,runtimeIntegrated:false,inGameAccepted:false};
if(!process.argv.includes('--static')){
 const require=createRequire('C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json'),{chromium}=require('playwright'),browser=await chromium.launch({channel:'msedge',headless:true});
 try{for(const width of [1280,390]){
  const page=await browser.newPage({viewport:{width,height:900}}),errors=[];page.on('pageerror',e=>errors.push(String(e)));await page.goto('file:///'+path.join(root,full+'preview.html').replaceAll('\\','/'));await page.waitForFunction(()=>window.EQUIPMENT_QA?.ready());
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.locator('#grip').uncheck();
  for(const id of ids){
   await page.locator(`[data-id="${id}"]`).click();assert.match(await page.locator('#facts').innerText(),/用户已选 · 当前美术/);
   for(const [state,selector] of [['front','.thumb'],['gui_16','.s16'],['gui_32','.s32']]){
    const f=files.find(f=>f.id===id&&f.state===state),url='file:///'+path.join(root,f.candidate).replaceAll('\\','/');
    const target=`[data-id="${id}"] ${selector}`,actual=PNG.sync.read(await page.locator(target).screenshot());
    // Render the independent candidate at the same canvas position, size and background.
    await page.evaluate(async({url,target})=>{const im=new Image();im.src=url;await im.decode();const old=document.querySelector(target),c=old.cloneNode(false);c.id='expected-pixels';const ctx=c.getContext('2d');ctx.imageSmoothingEnabled=false;ctx.drawImage(im,0,0,c.width,c.height);window.selectionQaOriginal=old;old.replaceWith(c)},{url,target});
    const expected=PNG.sync.read(await page.locator('#expected-pixels').screenshot());await page.locator('#expected-pixels').evaluate(e=>{e.replaceWith(window.selectionQaOriginal);delete window.selectionQaOriginal});
    assert.equal(actual.width,expected.width);assert.equal(actual.height,expected.height);assert(actual.data.equals(expected.data),id+' rendered '+state+' differs from selected candidate');
   }
   await page.locator('#detail').screenshot({path:path.join(root,dir,`${id}-${width}-right.png`)});
   await page.locator('[value=left]').check();await page.locator('#slim').check();await page.locator('[data-theme=dark]').click();await page.locator('#detail').screenshot({path:path.join(root,dir,`${id}-${width}-left-dark.png`)});
   await page.locator('[value=right]').check();await page.locator('#slim').uncheck();await page.locator('[data-theme=light]').click();
  }
  const link=page.getByRole('link',{name:'义手刀修改对照'});assert(await link.count());await link.click();await page.waitForFunction(()=>window.IDENTITY_READY);await page.locator('#reveal').check();
  assert.match(await page.locator('#hand_of_malenia .details').innerText(),/已同步当前美术/);assert.match(await page.locator('#flail .details').innerText(),/已同步当前美术/);
  await page.locator('#hand_of_malenia').screenshot({path:path.join(root,dir,`malenia-selected-comparison-${width}.png`)});assert.deepEqual(errors,[]);
  result.browser.push({width,noOverflow:true,selectedPixelComparisons:12,leftSlimDarkCaptured:true,maleniaSelectedComparisonLink:true});await page.close();
 }}finally{await browser.close()}
}
result.captureDirectory=dir;await fs.writeFile(path.join(root,batch+'selection-validation.json'),JSON.stringify(result,null,2)+'\n');
console.log('PASS selected 12 PNGs, metadata/whitelist/thickness references, '+unchanged+' unchanged snapshots, '+guards.length+' regeneration guards; '+result.browser.length+' browser viewports. Evidence: '+dir);
