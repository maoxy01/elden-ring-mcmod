import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const before=path.join(root,'art/backups/swords-before-refinement-20261012');
const rejected=path.join(root,'art/backups/large-swords-refinement-rejected-20261012');
const read=async file=>JSON.parse(await fs.readFile(file,'utf8'));
const sha=bytes=>createHash('sha256').update(bytes).digest('hex');
const current=await read(path.join(root,'art/weapons/catalog.json')),original=await read(path.join(before,'art/weapons/catalog.json'));
const results=[];
for(const id of ['great_sword','colossal_sword']){
  for(const extension of ['model.json','mesh.json','bbmodel']){
    const relative='art/weapons/'+id+'.'+extension,active=path.join(root,relative),saved=path.join(rejected,relative);
    await fs.mkdir(path.dirname(saved),{recursive:true});
    try{await fs.copyFile(active,saved,fs.constants.COPYFILE_EXCL);}catch(e){if(e.code!=='EEXIST')throw e;}
    const old=await fs.readFile(path.join(before,relative));await fs.copyFile(path.join(before,relative),active);
    assert.equal(sha(await fs.readFile(active)),sha(old));results.push({file:relative,sha256:sha(old)});
  }
  const index=current.weapons.findIndex(e=>e.id==='eldencombat:'+id);
  current.weapons[index]=original.weapons.find(e=>e.id==='eldencombat:'+id);
}
await fs.writeFile(path.join(root,'art/weapons/catalog.json'),JSON.stringify(current,null,2)+'\n');
await fs.writeFile(path.join(root,'art/weapon-proportions/large-sword-version-selection.json'),JSON.stringify({date:'2026-10-12',userChoice:'great_sword and colossal_sword use pre-refinement versions; straight_sword keeps new version',source:before,rejectedBackup:rejected,results,runtimeSynced:false},null,2)+'\n');
console.log(JSON.stringify({restored:results.length,source:before,rejectedBackup:rejected}));
