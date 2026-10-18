import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {PNG,stats,opaque} from './equipment-pixels.mjs';
import {selectionFile,assertSelectedArt} from './selected-equipment.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),full='art/equipment-2d/full/',batch='art/equipment-2d/identity-batch-two/',receiptFile=batch+'starscourge-selection.json';
const bytes=f=>fs.readFile(path.join(root,f)),read=async f=>JSON.parse(await bytes(f)),hash=b=>createHash('sha256').update(b).digest('hex'),write=(f,d)=>fs.writeFile(path.join(root,f),JSON.stringify(d,null,2)+'\n');
const expected={front:'270c60fe9edff3b0de34263dc2251da19f4fc47995f9c585646537e6ce009a43',gui_16:'75613912fbe9d2aff913321e60602ddeaaa2afd661a784d23a6758670dc39de1',gui_32:'c3dabb266a7044811738bb5189bca81b3fe929887129f3ab74f313da59f64fa3'};
const data=await read(full+'equipment-42.json'),pub=await read(full+'publishable-assets.json'),t=await read('art/item-thickness/manifest.json'),d=await read(batch+'manifest.json'),selection=await assertSelectedArt(),a=data.assets.find(a=>a.id==='starscourge_greatsword');assert(selection);
for(const v of [...a.variants,...a.gui])assert.equal(hash(await bytes(full+v.file)),expected[v.state],'Later edit detected; retain it and re-confirm selection');
async function audit94(){
 const all=data.assets.filter(a=>a.delivery==='own_sprite').flatMap(a=>[...a.variants,...a.gui].map(v=>({id:a.id,...v}))),diffs=[];
 assert.equal(all.length,94);assert.equal(pub.include.length,94);assert.equal(new Set(all.map(v=>v.file)).size,94);assert.equal(new Set(pub.include.map(v=>v.file)).size,94);
 for(const v of all){const actual=hash(await bytes(full+v.file)),p=pub.include.find(f=>f.file===v.file);if(actual!==v.sha256||actual!==p?.sha256)diffs.push({file:v.file,actual,manifest:v.sha256,whitelist:p?.sha256??null})}
 return {checkedPngs:94,differences:diffs};
}
if(!process.argv.includes('--verify')){
 assert(!selection.assets.some(a=>a.id==='starscourge_greatsword'),'Already selected; use --verify');
 for(const f of d.protectedFiles)assert.equal(hash(await bytes(f.file)),f.sha256,'Protected baseline changed: '+f.file);
 const priorAsset=structuredClone(a),auditBefore=await audit94(),backup=batch+'starscourge-backup-'+Date.now(),before=new Map();
 const changed=[full+'equipment-42.json',full+'publishable-assets.json',full+'preview-data.js',selectionFile,batch+'manifest.json',batch+'preview-data.js'];
 const preserve=[...changed,...a.variants.map(v=>full+v.file),...a.gui.map(v=>full+v.file)];
 const tracked=new Set([...preserve,...d.protectedFiles.map(f=>f.file),...data.assets.map(a=>a.source),'art/item-thickness/manifest.json',...t.models.map(m=>'art/item-thickness/'+m.file)]);
 for(const f of tracked)before.set(f,hash(await bytes(f)));
 for(const f of preserve){const target=path.join(root,backup,f);await fs.mkdir(path.dirname(target),{recursive:true});await fs.copyFile(path.join(root,f),target,fs.constants.COPYFILE_EXCL)}
 const authorization={date:'2026-10-16',threadId:'01a1305c-e06b-7541-998a-8c553450b0ae',question:'碎星大剑目前只有手持贴图与旧清单不一致。全套预览中的当前磁盘版，是否就是你希望保留的版本？确认后只同步清单，不再改图。',quote:'是，采用当前磁盘版',evidence:'Coordinator relayed direct human answer in this art task; no invented message ID',scope:'Current three on-disk PNGs; metadata synchronization only, not new artwork or game acceptance'};
 const files=[];
 for(const v of [...a.variants,...a.gui]){
  const raw=await bytes(full+v.file),p=PNG.sync.read(raw),actual=hash(raw),priorManifestHash=v.sha256;assert.deepEqual([p.width,p.height],v.size);
  Object.assign(v,stats(p));v.sha256=actual;pub.include.find(f=>f.file===v.file).sha256=actual;
  files.push({id:a.id,state:v.state,file:full+v.file,snapshot:backup+'/'+full+v.file,sha256:actual,priorManifestHash,gripOpaque:opaque(p,...v.anchorPixels)});
 }
 a.artRevision={date:'2026-10-16',status:'user_selected_art_current',authorization,receipt:receiptFile,provenance:'Historical edit author not established; current on-disk version explicitly selected by user',runtimeIntegrated:false,inGameAccepted:false};
 selection.assets.push({id:a.id,authorization,worldLengthBlocks:a.worldLengthBlocks,anchorPixels:a.anchorPixels,files});(selection.revisions??=[]).push({authorization,backup,receipt:receiptFile});
 d.sideChecks.starscourge={status:'user_selected_art_metadata_synced',authorization,receipt:receiptFile,files:files.map(f=>({file:f.file.slice(full.length),actual:f.sha256,assetManifest:f.sha256,whitelist:f.sha256,match:true})),reason:'Explicit human selection resolves the current art choice. Historical edit authorship remains unknown; PNGs, GUI, proportions and runtime routes untouched.'};
 await write(full+'equipment-42.json',data);await write(full+'publishable-assets.json',pub);await write(selectionFile,selection);await fs.writeFile(path.join(root,full+'preview-data.js'),'window.EQUIPMENT = '+JSON.stringify(data.assets)+';\n');
 const transitions=[];for(const f of d.protectedFiles){const actual=hash(await bytes(f.file));if(actual!==f.sha256){assert(changed.includes(f.file));transitions.push({file:f.file,before:f.sha256,after:actual});f.sha256=actual}}
 (d.validation.protectionBaselineHistory??=[]).push(d.validation.protectionBaselineRevision);d.validation.protectionBaselineRevision={reason:'Human selected unchanged Starscourge disk version',receipt:receiptFile,changedFiles:transitions};
 await write(batch+'manifest.json',d);await fs.writeFile(path.join(root,batch+'preview-data.js'),'window.IDENTITY='+JSON.stringify(d)+';\n');
 const auditAfter=await audit94();assert.deepEqual(auditAfter.differences,[],'Other metadata differences remain; report without changing their pixels');
 for(const [f,sha256] of before)if(!changed.includes(f))assert.equal(hash(await bytes(f)),sha256,'Unrelated file changed: '+f);
 await assertSelectedArt();
 await write(receiptFile,{date:'2026-10-16',authorization,backup,files,assetBefore:priorAsset,assetAfter:a,auditBefore,auditAfter,protectedTransitions:transitions,beforeSnapshot:[...before].map(([file,sha256])=>({file,sha256})),expectedChangedFiles:changed,thickness:'Existing three model texture hashes already match; manifest and model bytes unchanged.',artSelectionConfirmed:true,metadataSynchronized:true,runtimeIntegrated:false,inGameAccepted:false});
 console.log('PASS Starscourge metadata selected; PNGs unchanged; publication audit 94/94 consistent. Backup: '+backup);
}else{
 const r=await read(receiptFile);assert.deepEqual(a,r.assetAfter);assert.equal(d.sideChecks.starscourge.status,'user_selected_art_metadata_synced');assert.deepEqual(await audit94(),r.auditAfter);
 const priorData=await read(r.backup+'/'+full+'equipment-42.json'),priorPub=await read(r.backup+'/'+full+'publishable-assets.json'),priorSelection=await read(r.backup+'/'+selectionFile);
 for(const item of data.assets)if(item.id!==a.id)assert.deepEqual(item,priorData.assets.find(p=>p.id===item.id),'Other equipment metadata changed: '+item.id);
 for(const item of selection.assets.filter(s=>s.id!==a.id))assert.deepEqual(item,priorSelection.assets.find(p=>p.id===item.id),'Other selection changed: '+item.id);
 for(const f of pub.include)if(!f.file.includes('starscourge_greatsword'))assert.deepEqual(f,priorPub.include.find(p=>p.file===f.file));
 assert.equal((await bytes(full+'preview-data.js')).toString(),'window.EQUIPMENT = '+JSON.stringify(data.assets)+';\n');assert.equal((await bytes(batch+'preview-data.js')).toString(),'window.IDENTITY='+JSON.stringify(d)+';\n');
 for(const f of r.files){assert.equal(hash(await bytes(f.file)),f.sha256);assert.equal(hash(await bytes(f.snapshot)),f.sha256);const m=t.models.find(m=>m.id==='eldencombat:starscourge_greatsword'&&m.state===f.state);assert.equal(m.textures[0].source,f.file);assert.equal(m.textures[0].sha256,f.sha256);assert.equal(m.thicknessUnits,1);assert.equal(hash(await bytes('art/item-thickness/'+m.file)),m.sha256)}
 for(const k of ['worldLengthBlocks','sourceSpanPixels','anchorPixels','suggestedDisplay','projection','leftHand','axisImageDegrees'])assert.deepEqual(a[k],r.assetBefore[k]);
 let unchanged=0;for(const f of r.beforeSnapshot)if(!r.expectedChangedFiles.includes(f.file)){assert.equal(hash(await bytes(f.file)),f.sha256,'Unrelated file changed: '+f.file);unchanged++}
 for(const f of d.protectedFiles)assert.equal(hash(await bytes(f.file)),f.sha256);
 await write(batch+'starscourge-validation.json',{date:'2026-10-16',receipt:receiptFile,publishedPngsChecked:94,manifestWhitelistDifferences:[],starscourgePngsUnchanged:3,otherSnapshotFilesUnchanged:unchanged,otherAssetMetadataUnchanged:41,priorFourSelectionsUnchanged:true,modelJsonAndThicknessManifestUnchanged:true,gripsAndLengthUnchanged:true,selectedAssetsProtected:selection.assets.length,artSelectionConfirmed:true,metadataSynchronized:true,historicalEditAuthor:'unknown',browserRun:false,runtimeIntegrated:false,inGameAccepted:false});
 console.log('PASS 94 PNG hashes match manifest/whitelist; 3 Starscourge PNGs and thickness references exact; '+unchanged+' unrelated snapshots unchanged; 5 selected assets protected. Game not verified.');
}
