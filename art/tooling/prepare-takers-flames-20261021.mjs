import fs from 'node:fs/promises';
import path from 'node:path';
import {createHash} from 'node:crypto';
const root='D:/codexout/mcmod',out=path.join(root,'art/vfx-blockbench/takers-flames-20261021');
await fs.mkdir(out,{recursive:true});
for(const d of ['before/flame_front','textures/flame_front','textures/ground_rupture','textures/eruption_debris','qa','requests'])await fs.mkdir(path.join(out,d),{recursive:true});
const hash=b=>createHash('sha256').update(b).digest('hex'),files=[];
async function walk(p){for(const entry of await fs.readdir(path.join(root,p),{withFileTypes:true})){const file=p+'/'+entry.name;if(entry.isDirectory())await walk(file);else files.push({file,sha256:hash(await fs.readFile(path.join(root,file)))});}}
for(const p of ['src','art/vfx-2d','art/equipment-2d/full'])await walk(p);
const m=JSON.parse(await fs.readFile(path.join(root,'art/vfx-2d/manifest.json'))),original=m.assets.find(a=>a.id==='flame_front');
for(const f of original.frames)await fs.copyFile(path.join(root,'art/vfx-2d',f.file),path.join(out,'before/flame_front',path.basename(f.file)));
await fs.writeFile(path.join(out,'baseline.json'),JSON.stringify({date:'2026-10-21',original,protection:files,runtimeIntegrated:false},null,2),{flag:'wx'});
console.log('Backed up 16 source flame frames; protected '+files.length+' current source/resource/art files.');
