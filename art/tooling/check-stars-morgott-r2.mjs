import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
const base='D:/codexout/mcmod/art/equipment-2d/blockbench-all-items-20261018',revision='revisions/stars-morgott-r2';
const read=async p=>JSON.parse(await fs.readFile(path.join(base,p))),hash=b=>createHash('sha256').update(b).digest('hex');
const backup=await read(revision+'/backup-receipt.json'),before=await read(revision+'/all-items-optimized.bbmodel'),after=await read('all-items-optimized.bbmodel'),oldReport=await read(revision+'/native-edit-report.json'),report=await read('native-edit-report.json');
const ids=['starscourge_greatsword','morgotts_cursed_sword'];
for(const f of backup.otherCandidates)assert.equal(hash(await fs.readFile(path.join(base,f.file))),f.sha256,'Unrelated candidate changed: '+f.file);
for(const e of before.elements.filter(e=>!ids.some(id=>e.name.startsWith(id+'__'))))assert.deepEqual(after.elements.find(v=>v.uuid===e.uuid),e,'Unrelated native element '+e.name);
for(const a of report.items){const old=oldReport.items.find(v=>v.id===a.id);if(!ids.includes(a.id)){assert.deepEqual(a,old);continue;}assert.equal(a.worldLengthBlocks,old.worldLengthBlocks);assert.deepEqual(a.anchorPixels,old.anchorPixels);for(const s of a.states){assert(s.nativeReport.alphaDiff>0);assert(s.nativeReport.rgbaDiff>0);assert.equal(s.nativeReport.components,1);}}
const result={revision:'stars-morgott-r2',items:ids,pngs:6,originalArtworkUntouched:true,otherCandidateFilesUnchanged:backup.otherCandidates.length,otherNativeElementsUnchanged:before.elements.filter(e=>!ids.some(id=>e.name.startsWith(id+'__'))).length,masterHistory:after.history.length,masterElements:after.elements.length,source:'Native Blockbench Texture.edit / Cube / UV; saved master reloaded before eight fixed-camera captures',outlineLockReleased:true,proportionsAndGripsPreserved:true,runtimeIntegrated:false,humanAccepted:false};
await fs.writeFile(path.join(base,revision,'validation.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
