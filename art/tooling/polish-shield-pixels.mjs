import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {PNG,stats,opaque} from './equipment-pixels.mjs';
import {shieldPixels} from './shield-pixel-profile.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),out=path.join(root,'art/equipment-2d/shape-review'),full=path.join(root,'art/equipment-2d/full');
const hash=b=>createHash('sha256').update(b).digest('hex'),read=async p=>JSON.parse(await fs.readFile(p,'utf8'));
const d=await read(path.join(out,'manifest.json')),a=d.candidates.find(a=>a.id==='shield'),backup=path.join(out,'backup-before-shield-pixel-polish');await fs.mkdir(backup,{recursive:true});
for(const f of ['manifest.json','preview-data.js','selection.json']){try{await fs.copyFile(path.join(out,f),path.join(backup,f),fs.constants.COPYFILE_EXCL)}catch(e){if(e.code!=='EEXIST')throw e}}
for(const v of a.variants){
 const file=path.basename(v.file),current=await fs.readFile(path.join(full,'sprites',file));
 await fs.mkdir(path.join(backup,'sprites'),{recursive:true});await fs.mkdir(path.join(backup,'before'),{recursive:true});
 for(const dir of ['sprites','before'])try{await fs.copyFile(path.join(out,dir,file),path.join(backup,dir,file),fs.constants.COPYFILE_EXCL)}catch(e){if(e.code!=='EEXIST')throw e}
 await fs.writeFile(path.join(out,'before',file),current);
 const p=shieldPixels(v.size[0],v.state==='back'),st=stats(p);assert.equal(st.components8.length,1);assert(opaque(p,...v.anchorPixels));assert(st.transparentMargin);
 const bytes=PNG.sync.write(p);await fs.writeFile(path.join(out,v.file),bytes);Object.assign(v,st,{sha256:hash(bytes),beforeHash:hash(current)});
 if(p.width===32)for(let y=0;y<32;y+=2)for(let x=0;x<32;x+=2)for(const [dx,dy] of [[1,0],[0,1],[1,1]])assert(p.data.subarray((y*32+x)*4,(y*32+x)*4+4).equals(p.data.subarray(((y+dy)*32+x+dx)*4,((y+dy)*32+x+dx)*4+4)),'Inconsistent coarse pixel block');
}
a.features=['上下统一红金灰配色','16像素母版与2倍大像素轮廓','正反同轮廓'];a.artRevision='uniform_palette_coarse_edge';d.validation.browser=false;
await fs.writeFile(path.join(out,'manifest.json'),JSON.stringify(d,null,2)+'\n');await fs.writeFile(path.join(out,'preview-data.js'),'window.IDENTITY='+JSON.stringify({candidates:d.candidates,audit:d.audit})+';\n');
console.log('PASS shield4 PNGs, same palette top/bottom, 2x2 block consistency, opaque grips, connected matching silhouette.');
