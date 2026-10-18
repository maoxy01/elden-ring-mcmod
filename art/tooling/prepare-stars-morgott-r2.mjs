import fs from 'node:fs/promises';
import path from 'node:path';
import {createHash} from 'node:crypto';
const base='D:/codexout/mcmod/art/equipment-2d/blockbench-all-items-20261018';
const dest=path.join(base,'revisions/stars-morgott-r2');
const ids=['starscourge_greatsword','morgotts_cursed_sword'];
const report=JSON.parse(await fs.readFile(path.join(base,'native-edit-report.json')));
const files=['all-items-optimized.bbmodel','native-edit-report.json','manifest.json','preview-data.js','validation.json'];
for(const a of report.items.filter(a=>ids.includes(a.id))){
 files.push('items/'+a.id+'.bbmodel');
 for(const s of a.states)files.push('textures/'+path.basename(s.output));
 for(const k of ['front','oblique','gui_16','gui_32'])files.push('qa/after-'+a.id+'-'+k+'.png');
}
const receipt={files:[],otherCandidates:[],request:'User releases Morgott silhouette lock; revise two candidate items only.'};
for(const f of files){const b=await fs.readFile(path.join(base,f));const out=path.join(dest,f);await fs.mkdir(path.dirname(out),{recursive:true});try{await fs.writeFile(out,b,{flag:'wx'});}catch(e){if(e.code!=='EEXIST')throw e;}receipt.files.push({file:f,sha256:createHash('sha256').update(b).digest('hex')});}
for(const a of report.items.filter(a=>!ids.includes(a.id)))for(const f of ['items/'+a.id+'.bbmodel',...a.states.map(s=>'textures/'+path.basename(s.output))])receipt.otherCandidates.push({file:f,sha256:createHash('sha256').update(await fs.readFile(path.join(base,f))).digest('hex')});
await fs.writeFile(path.join(dest,'backup-receipt.json'),JSON.stringify(receipt,null,2),{flag:'wx'});
console.log('Backed up two items, master and metadata; recorded '+receipt.otherCandidates.length+' unrelated candidate files.');
