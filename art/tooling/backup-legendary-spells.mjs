import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const dest=path.join(root,'art/backups','legendary-spells-pre-block-'+Date.now());
const inputs=['art/legendary-spells','art/tooling/build-legendary-spells.mjs','art/tooling/verify-legendary-spells.mjs','docs/LEGENDARY-SPELLS-ART-HANDOFF-20261016.md'];
async function walk(p){const s=await fs.stat(p);if(!s.isDirectory())return [p];let a=[];for(const e of await fs.readdir(p))a.push(...await walk(path.join(p,e)));return a}
const files=[];
for(const input of inputs)for(const src of await walk(path.join(root,input))){const file=path.relative(root,src),b=await fs.readFile(src),to=path.join(dest,file);await fs.mkdir(path.dirname(to),{recursive:true});await fs.writeFile(to,b);files.push({file:file.replaceAll('\\','/'),sha256:createHash('sha256').update(b).digest('hex')})}
await fs.writeFile(path.join(dest,'receipt.json'),JSON.stringify({reason:'user requested Minecraft block-form art revision',files},null,2)+'\n');
console.log(JSON.stringify({backup:dest,files:files.length}));
