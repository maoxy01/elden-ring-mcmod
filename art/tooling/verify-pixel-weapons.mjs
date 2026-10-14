import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createRequire} from 'node:module';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {bakeMesh} from './game-weapon-bake.mjs';
import {validateBakedMesh} from './weapon-mesh-validation.mjs';
import {validateStructure} from './weapon-structure.mjs';
import {connectedComponents} from './weapon-connectivity.mjs';

const art=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..'),out=path.join(art,'remembrance-game');
const backup=path.join(art,'backups/remembrance-smooth-before-pixel-20261011-001');
const require=createRequire(path.join(process.argv[2],'package.json')),{PNG}=require('pngjs');
const read=async p=>JSON.parse(await fs.readFile(p)),hash=b=>createHash('sha256').update(b).digest('hex');
const catalog=await read(path.join(out,'catalog.json')),atlas=PNG.sync.read(await fs.readFile(path.join(out,'weapons.png')));
const models=[];let pixelBlocks=0;
for(const w of catalog.weapons) {
  const model=await read(path.join(out,w.model)),mesh=await read(path.join(out,w.mesh));
  assert.equal(model.refinement.revision,3);assert(!model.smoothSurfaces?.length);
  assert.equal(model.pixelConsolidation.blockSize,2);validateBakedMesh(mesh);
  const expected=JSON.parse(JSON.stringify(bakeMesh(model)));
  for(const field of ['positions','normals','uvs','indices'])assert.deepEqual(mesh[field],expected[field]);
  for(let t=0;t<mesh.indices.length;t+=3) {
    const normals=mesh.indices.slice(t,t+3).map(i=>mesh.normals.slice(i*3,i*3+3));
    assert.deepEqual(normals[0],normals[1]);assert.deepEqual(normals[0],normals[2]);
  }
  assert.equal(connectedComponents(model).length,1);validateStructure(model);
  if(w.id.endsWith(':sacred_relic_sword')) {
    const broken={...model,cubes:model.cubes.filter(c=>!c.name.startsWith('blade_root_shoulder_'))};
    assert.throws(()=>validateStructure(broken),/open joint/,'Removing the visible root shoulders must fail');
  }
  const before=await read(path.join(backup,'art/remembrance',w.model)),sourceBytes=await fs.readFile(path.join(art,'remembrance',w.model));
  const source=JSON.parse(sourceBytes);
  if(w.id.endsWith(':sacred_relic_sword')) {
    assert(source.rootRepair);validateStructure(source);
    const oldNames=new Set(before.cubes.map(c=>c.name));
    assert.deepEqual(source.cubes.filter(c=>oldNames.has(c.name)),before.cubes,'Unrelated original geometry changed');
    assert(source.cubes.filter(c=>!oldNames.has(c.name)).every(c=>c.name.startsWith('blade_root_shoulder_')));
    assert.throws(()=>validateStructure({...source,cubes:before.cubes}),/open joint/);
  } else assert.deepEqual(source,before,`${w.id}: unrelated high-detail source changed`);
  models.push({id:w.id,cubes:w.cubes,triangles:w.triangles,hardFaceNormals:true,connected:true});
}
const first=await read(path.join(out,catalog.weapons[0].model));
const regions=[...Object.values(first.materials).map(m=>m.uv)];
for(const w of catalog.weapons) {
  const m=await read(path.join(out,w.model));
  for(const tile of Object.values(m.surfaceBake.tiles))regions.push([...tile.origin,tile.origin[0]+tile.size,tile.origin[1]+tile.size]);
}
for(const [left,top,right,bottom] of regions)for(let y=top;y<bottom;y+=2)for(let x=left;x<right;x+=2) {
  const p=(y*atlas.width+x)*4,color=atlas.data.subarray(p,p+4);
  for(let dy=0;dy<2;dy++)for(let dx=0;dx<2;dx++) {
    const i=((y+dy)*atlas.width+x+dx)*4;assert.deepEqual(atlas.data.subarray(i,i+4),color,'Macro pixel is not constant');
  }
  pixelBlocks++;
}
for(const collection of ['remembrance','remembrance-game'])assert.deepEqual(
  await fs.readFile(path.join(art,collection,'weapon-arts.design.json')),
  await fs.readFile(path.join(backup,'art',collection,'weapon-arts.design.json')),'Skill design changed');
assert.deepEqual(await fs.readFile(path.join(art,'remembrance/weapons.png')),
  await fs.readFile(path.join(backup,'art/remembrance/weapons.png')),'Source atlas changed');
const rootModel='sacred_relic_sword.model.json';
const report={status:'passed',revision:3,geometry:'cuboids only; constant per-face normals; no smooth lofts',
  texture:{blockSize:2,smallPixelsPerMacroPixel:4,filter:'none',pixelBlocks,atlas:[atlas.width,atlas.height]},models,
  skillDesignUnchanged:true,sourceRepair:{model:rootModel,
    beforeSha256:hash(await fs.readFile(path.join(backup,'art/remembrance',rootModel))),
    afterSha256:hash(await fs.readFile(path.join(art,'remembrance',rootModel))),
    changes:'only broad blade-root shoulders and their structural checks',otherSevenModelsUnchanged:true,sourceAtlasUnchanged:true},
  smoothBackup:'art/backups/remembrance-smooth-before-pixel-20261011-001/backup-manifest.json',
  gameplayVerified:false,performanceMeasured:false};
await fs.writeFile(path.join(out,'pixel-validation.json'),JSON.stringify(report,null,2)+'\n');
console.log(`PIXEL_WEAPONS_PASS: ${models.length} cuboid models, ${pixelBlocks} constant 2x2 blocks, visible root repair verified`);
