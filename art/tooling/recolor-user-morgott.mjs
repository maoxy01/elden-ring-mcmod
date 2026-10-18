import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {PNG,stats} from './equipment-pixels.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),full=path.join(root,'art/equipment-2d/full'),review=path.join(root,'art/equipment-2d/shape-review'),user=path.join(root,'art/equipment-2d/user-edits');
const rich=process.argv.includes('--iridescent'),interior=rich||process.argv.includes('--interior');
const hash=b=>createHash('sha256').update(b).digest('hex'),read=async f=>JSON.parse(await fs.readFile(f,'utf8')),write=async(f,d)=>fs.writeFile(f,JSON.stringify(d,null,2)+'\n');
const palette={'96,110,98':[53,91,84],'103,83,111':[85,48,91],'126,101,135':[151,76,113],'128,138,142':[157,169,172],'105,113,116':[92,108,121],'39,39,38':[34,32,37],'71,81,73':[33,57,54],'117,134,120':[96,147,120],'29,29,28':[25,23,28],'78,84,86':[42,50,61],'129,116,80':[125,96,67],'80,80,55':[78,64,47],'62,62,17':[63,54,38],'73,71,65':[68,61,57],'21,15,11':[21,15,16],'53,53,62':[42,39,53],'157,142,98':[165,137,92],'0,0,0':[0,0,0],'98,96,88':[90,86,82],'76,61,82':[49,32,53],'67,57,41':[60,45,40],'141,123,82':[139,109,73],'103,111,114':[91,106,120],'22,13,13':[22,13,19],'123,98,131':[145,71,108],'48,48,46':[44,40,44],'104,91,61':[99,75,54]};
const data=await read(path.join(full,'equipment-42.json')),pub=await read(path.join(full,'publishable-assets.json')),d=await read(path.join(review,'manifest.json')),selection=await read(path.join(review,'selection.json')),registry=await read(path.join(user,'selected.json'));
const a=data.assets.find(a=>a.id==='morgotts_cursed_sword'),r=d.candidates.find(a=>a.id==='morgotts_cursed_sword'),front=a.variants.find(v=>v.state==='front'),sourceHash=hash(await fs.readFile(path.join(full,front.file))),backup=path.join(user,'morgotts_cursed_sword',(rich?'iridescent-before-':interior?'interior-before-':'color-before-')+sourceHash);
assert.equal(sourceHash,interior?registry.assets.find(m=>m.id===a.id).sha256:'7ad2a4d0e10dcd1cae0ba8bf516f3e25a8682d0274cd0b8f10ca78998f9d2418','User drawing changed; inspect newest save before recoloring');
await fs.mkdir(backup,{recursive:true});
const tasks=[];
for(const v of [...a.variants,...a.gui]){
 const file=path.join(full,v.file),bytes=await fs.readFile(file);assert.equal(hash(bytes),v.sha256);const p=PNG.sync.read(bytes),old=Buffer.from(p.data);let changes=0;
 if(interior){
   const points=[];for(let y=0;y<p.height;y++)for(let x=0;x<p.width;x++)if(p.data[(y*p.width+x)*4+3]>=128){const dx=x+.5-v.anchorPixels[0],dy=y+.5-v.anchorPixels[1];points.push({x,y,s:(dx-dy)/Math.sqrt(2),u:(dx+dy)/Math.sqrt(2)})}
   const end=Math.max(...points.map(q=>q.s)),start=end*.16;
   for(const q of points){if(q.s<start)continue;const slice=points.filter(t=>Math.abs(t.s-q.s)<.8),lo=Math.min(...slice.map(t=>t.u)),hi=Math.max(...slice.map(t=>t.u)),u=hi===lo?.2:(q.u-lo)/(hi-lo),t=(q.s-start)/(end-start);
     let rgb=u<.16?[177,183,177]:u<.35?[106,115,120]:u>.86?[35,32,41]:[57,51,65];
     const vein=.60+.12*Math.sin(t*7+.4),width=p.width===16?.25:.15;
     if(u>=.35&&u<=.86&&Math.abs(u-vein)<width)rgb=t<.36?[126,57,84]:t<.64?[129,107,69]:[55,108,99];
     if(rich){
       const oil=[[142,39,67],[168,56,117],[107,62,164],[55,87,158],[47,141,155],[90,146,63],[177,141,63],[166,85,52]];
       const phase=t*1.8+u*.9+.19*Math.sin(t*14+u*7),band=Math.floor((phase-Math.floor(phase))*oil.length);
       rgb=oil[band].map(v=>Math.round(v*(u<.4?.95:.78)));
       if(Math.sin(phase*Math.PI*4)<-.3)rgb=oil[band].map((v,i)=>Math.round(v*.28+[36,31,40][i]));
       if(u<.16)rgb=Math.sin(t*17)>.3?[143,168,180]:[78,101,121];
       if(u>.93)rgb=[39,30,42];
     }
     const i=(q.y*p.width+q.x)*4;for(let c=0;c<3;c++){if(p.data[i+c]!==rgb[c])changes++;p.data[i+c]=rgb[c]}
   }
 }else for(let i=0;i<p.data.length;i+=4){if(!p.data[i+3])continue;const rgb=palette[p.data.subarray(i,i+3).join(',')];assert(rgb,'Unmapped color in current drawing');for(let c=0;c<3;c++){if(p.data[i+c]!==rgb[c])changes++;p.data[i+c]=rgb[c]}}
 for(let i=3;i<p.data.length;i+=4)assert.equal(p.data[i],old[i],'Alpha mask changed');assert(changes>0);
 const q=PNG.sync.read(PNG.sync.write(p));for(let i=3;i<q.data.length;i+=4)q.data[i]=q.data[i]>=128?255:0;
 tasks.push({v,bytes,result:PNG.sync.write(p),stats:stats(q),changedChannels:changes});
}
for(const [base,tag,files] of [[full,'full',['equipment-42.json','publishable-assets.json','preview-data.js','validation.json',...tasks.map(t=>t.v.file)]],[review,'review',['manifest.json','selection.json','preview-data.js',...r.variants.flatMap(v=>[v.file,v.before])]], [user,'user',['selected.json']]])for(const f of files){const target=path.join(backup,tag,f);await fs.mkdir(path.dirname(target),{recursive:true});try{await fs.copyFile(path.join(base,f),target,fs.constants.COPYFILE_EXCL)}catch(e){if(e.code!=='EEXIST')throw e}}
// Recheck all inputs immediately before committing this RGB-only revision.
for(const t of tasks)assert.equal(hash(await fs.readFile(path.join(full,t.v.file))),hash(t.bytes));
for(const t of tasks){const v=t.v,old=r.variants.find(w=>w.state===v.state);await fs.writeFile(path.join(review,old.before),t.bytes);await fs.writeFile(path.join(full,v.file),t.result);Object.assign(v,t.stats,{sha256:hash(t.result)});await fs.writeFile(path.join(review,old.file),t.result);Object.assign(old,t.stats,{sha256:v.sha256,beforeHash:hash(t.bytes)});pub.include.find(p=>p.file===v.file).sha256=v.sha256;selection.selectedArtFiles.find(s=>s.id===a.id&&s.state===v.state).sha256=v.sha256;}
const colorRevision={date:'2026-10-16',scope:interior?'Internal blade fill repainted by longitudinal sections: bevel, metal face and winding oil vein; every alpha byte unchanged':'RGB only; every alpha byte, canvas, coordinates, grips and length unchanged',palette:rich?'interleaved wine/magenta/violet/blue/cyan/green/ochre/copper oil bands':'dark metal, wine violet, restrained verdigris and silver edge',originalUserMasterHash:registry.assets.find(m=>m.id===a.id).originalUserDrawingHash||sourceHash,previousMasterHash:sourceHash,selectedMasterHash:front.sha256};
a.artRevision={...a.artRevision,masterHash:front.sha256,colorRevision};r.artRevision=a.artRevision;r.features=rich?['用户版型逐像素不变','紫红蓝青铜绿金橙交错虹彩','明暗刃面与油膜反光']:interior?['用户版型逐像素不变','内部重画刃面、暗脊与弯曲油彩纹','非旧色块逐色替换']:['用户轮廓逐像素保留','深色金属与紫红铜绿油彩','仅RGB替换'];
const m=registry.assets.find(m=>m.id===a.id);m.originalUserDrawingHash=m.originalUserDrawingHash||sourceHash;m.sha256=front.sha256;m.files=[...a.variants,...a.gui].map(v=>({file:v.file,sha256:v.sha256}));m.colorRevision=colorRevision;
await write(path.join(full,'equipment-42.json'),data);await write(path.join(full,'publishable-assets.json'),pub);await fs.writeFile(path.join(full,'preview-data.js'),'window.EQUIPMENT = '+JSON.stringify(data.assets)+';\n');
d.validation.browser=false;d.validation.rgbOnlyAlphaIdentical=true;await write(path.join(review,'manifest.json'),d);await write(path.join(review,'selection.json'),selection);await fs.writeFile(path.join(review,'preview-data.js'),'window.IDENTITY='+JSON.stringify({candidates:d.candidates,audit:d.audit})+';\n');await write(path.join(user,'selected.json'),registry);
const validation=await read(path.join(full,'validation.json'));validation.browserVerified=false;validation.userRevision=a.artRevision;await write(path.join(full,'validation.json'),validation);
for(const t of tasks){const p=PNG.sync.read(await fs.readFile(path.join(full,t.v.file))),original=PNG.sync.read(t.bytes);for(let i=3;i<p.data.length;i+=4)assert.equal(p.data[i],original.data[i])}
await write(path.join(backup,'color-receipt.json'),{...colorRevision,files:tasks.map(t=>({file:t.v.file,beforeHash:hash(t.bytes),afterHash:hash(t.result),changedChannels:t.changedChannels,alphaIdentical:true})),runtimeIntegrated:false});
const thicknessFile=path.join(root,'art/item-thickness/manifest.json'),thickness=await read(thicknessFile);
for(const model of thickness.models)for(const texture of model.textures){const changed=tasks.find(t=>'art/equipment-2d/full/'+t.v.file===texture.source);if(changed)texture.sha256=hash(changed.result)}
await write(thicknessFile,thickness);
console.log('PASS recolored 3 images; every alpha byte identical; canvas/grips/world length unchanged; original user PNG backed up.');
