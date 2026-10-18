import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {PNG,shapes,project,stats,opaque} from './equipment-pixels.mjs';
import {maleniaBlade,maleniaProfile} from './malenia-blade-profile.mjs';
import {refuseSelectedRegeneration} from './selected-equipment.mjs';
await refuseSelectedRegeneration(['hand_of_malenia']);
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),out=path.join(root,'art/equipment-2d/identity-batch-two'),hash=b=>createHash('sha256').update(b).digest('hex'),read=async f=>JSON.parse(await fs.readFile(f,'utf8'));
const d=await read(path.join(out,'manifest.json')),a=d.candidates.find(a=>a.id==='hand_of_malenia'),sourceBytes=await fs.readFile(path.join(root,a.source));assert.equal(hash(sourceBytes),a.sourceHash);
for(const f of d.protectedFiles)assert.equal(hash(await fs.readFile(path.join(root,f.file))),f.sha256,'Protected source changed before editing');
const base=shapes(JSON.parse(sourceBytes)),ps=maleniaBlade(base),backup=path.join(out,'before-malenia-curve-'+a.variants[0].sha256.slice(0,12));await fs.mkdir(backup,{recursive:true});
for(const f of ['manifest.json','preview-data.js',...a.variants.map(v=>v.file),...a.variants.map(v=>v.before)]){const target=path.join(backup,f);await fs.mkdir(path.dirname(target),{recursive:true});try{await fs.copyFile(path.join(out,f),target,fs.constants.COPYFILE_EXCL)}catch(e){if(e.code!=='EEXIST')throw e}}
for(const v of a.variants){
 const previous=await fs.readFile(path.join(out,v.file));assert.equal(hash(previous),v.sha256);const p=project(ps,v.size[0],45,base),s=stats(p.png);assert.equal(s.components8.length,1);assert(s.transparentMargin);assert(opaque(p.png,...v.anchorPixels));
 await fs.writeFile(path.join(out,v.before),previous);const bytes=PNG.sync.write(p.png);await fs.writeFile(path.join(out,v.file),bytes);Object.assign(v,s,{beforeHash:hash(previous),sha256:hash(bytes)});
 d.models.find(m=>m.texture===v.file).textureHash=v.sha256;d.publication.ownDerivedPixelPngs.find(f=>f.file===v.file).sha256=v.sha256;
}
a.features=['刃根顺接连续弧线','刀尖渐细无突鼓','保留金属刃面与浅金刃缘'];a.curveProfile=maleniaProfile;a.curveRevision='2026-10-16';d.validation.browser=false;d.validation.authorVisualReview=false;
for(const f of d.protectedFiles)assert.equal(hash(await fs.readFile(path.join(root,f.file))),f.sha256,'Protected source changed');
await fs.writeFile(path.join(out,'manifest.json'),JSON.stringify(d,null,2)+'\n');await fs.writeFile(path.join(out,'preview-data.js'),'window.IDENTITY='+JSON.stringify(d)+';\n');
console.log('PASS Malenia held/GUI16/GUI32 candidate updated; continuous taper, solid grips and protected sources checked.');
