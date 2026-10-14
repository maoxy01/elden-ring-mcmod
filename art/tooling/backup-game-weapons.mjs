import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const beforePixel=process.argv.includes('--before-pixel');
const destination=path.join(root,beforePixel?'art/backups/remembrance-smooth-before-pixel-20261011-001':
  'art/backups/remembrance-game-before-smoothing-20261011-001');
const sources=['art/remembrance-game',...(beforePixel?['art/remembrance']:[]),'art/tooling','docs/GAME-WEAPONS-ASSETS-HANDOFF.md'];
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
await fs.mkdir(destination); // Refuse to overwrite a previous snapshot.
const files=[];
async function copy(relative) {
  const source=path.join(root,relative),target=path.join(destination,relative);
  if((await fs.stat(source)).isDirectory()) {
    await fs.mkdir(target,{recursive:true});
    for(const name of (await fs.readdir(source)).sort())await copy(path.join(relative,name));
  } else {
    await fs.mkdir(path.dirname(target),{recursive:true});
    await fs.copyFile(source,target,fs.constants.COPYFILE_EXCL);
    const bytes=await fs.readFile(source);
    assert.equal(hash(bytes),hash(await fs.readFile(target)));
    files.push({path:relative.replaceAll('\\','/'),bytes:bytes.length,sha256:hash(bytes)});
  }
}
for(const source of sources)await copy(source);
await fs.writeFile(path.join(destination,'backup-manifest.json'),JSON.stringify({
  schemaVersion:1,kind:beforePixel?'smooth game edition and source before pixel consolidation/root repair':'game weapons before continuous-surface smoothing',files
},null,2)+'\n',{flag:'wx'});
console.log(JSON.stringify({status:'BACKUP_HASHES_PASS',destination,files:files.length},null,2));
