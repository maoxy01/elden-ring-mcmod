import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
export const selectionFile='art/equipment-2d/full/selected-art.json';
export async function assertSelectedArt(){
 let d;try{d=JSON.parse(await fs.readFile(path.join(root,selectionFile),'utf8'))}catch(e){if(e.code==='ENOENT')return null;throw e}
 for(const a of d.assets)for(const f of a.files){
  assert.equal(createHash('sha256').update(await fs.readFile(path.join(root,f.file))).digest('hex'),f.sha256,'Selected art changed: '+f.file);
  const preserved=f.candidate||f.snapshot;assert(preserved,'Selected art needs a candidate or immutable snapshot');
  assert.equal(createHash('sha256').update(await fs.readFile(path.join(root,preserved))).digest('hex'),f.sha256,'Selected candidate/snapshot changed: '+preserved);
 }
 return d;
}
export async function refuseSelectedRegeneration(ids){
 const d=await assertSelectedArt();if(!d)return;
 const blocked=d.assets.filter(a=>!ids||ids.includes(a.id));
 if(blocked.length)throw new Error('Selected art is protected: '+blocked.map(a=>a.id).join(', ')+'. Do not regenerate; use an explicitly authorized selection revision.');
}
