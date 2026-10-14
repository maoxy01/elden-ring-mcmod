import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import assert from 'node:assert/strict';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const destination=path.join(root,'art/backups/remembrance-highdetail-20261011-001');
const sources=['art/remembrance','art/tooling','art/package.json','art/pnpm-lock.yaml',
  'art/pnpm-workspace.yaml','docs/REMEMBRANCE-ASSETS-HANDOFF.md','docs/EF-WEAPONS-IMPLEMENTATION-PLAN.md'];
const digest=bytes=>createHash('sha256').update(bytes).digest('hex');
await fs.mkdir(path.dirname(destination),{recursive:true});
await fs.mkdir(destination); // A collision must fail rather than overwrite an earlier backup.
const files=[];
async function copy(relative) {
  const source=path.join(root,relative),target=path.join(destination,relative),stat=await fs.stat(source);
  if(stat.isDirectory()) {
    await fs.mkdir(target,{recursive:true});
    for(const name of (await fs.readdir(source)).sort())await copy(path.join(relative,name));
  } else {
    await fs.mkdir(path.dirname(target),{recursive:true});
    await fs.copyFile(source,target,fs.constants.COPYFILE_EXCL);
    const bytes=await fs.readFile(source),copied=await fs.readFile(target);
    assert.equal(digest(bytes),digest(copied),relative);
    files.push({path:relative.replaceAll('\\','/'),bytes:bytes.length,sha256:digest(bytes)});
  }
}
for(const source of sources)await copy(source);
await fs.writeFile(path.join(destination,'backup-manifest.json'),JSON.stringify({
  schemaVersion:1,kind:'verified high-detail art snapshot',sourceRoot:root,
  excludes:['node_modules','game Java','build products'],files
},null,2)+'\n',{flag:'wx'});
console.log(JSON.stringify({status:'BACKUP_HASHES_PASS',destination,files:files.length,
  bytes:files.reduce((n,f)=>n+f.bytes,0)},null,2));
