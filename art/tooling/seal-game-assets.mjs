import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
const art=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..'),out=path.join(art,'remembrance-game');
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
const json=async name=>JSON.parse(await fs.readFile(path.join(out,name)));
if(process.argv.includes('--verify')) {
  const manifest=await json('asset-manifest.json');
  for(const file of manifest.files)assert.equal(hash(await fs.readFile(path.join(out,file.path))),file.sha256,file.path);
  console.log(`GAME_ASSET_HASHES_PASS: ${manifest.files.length} files`);
} else {
  const catalog=await json('catalog.json'),data=await json('validation.json'),preview=await json('preview-validation.json');
  const comparison=await json('comparison-validation.json');
  const pixel=!!data.pixelConsolidation,artReportName=pixel?'pixel-validation.json':'smoothing-validation.json';
  const artReport=await json(artReportName);
  for(const result of [data,preview,comparison,artReport])assert.equal(result.status,'passed');
  assert.equal(artReport.models.length,8);assert.equal(artReport.skillDesignUnchanged,true);
  assert.equal(preview.profile,'game');assert.equal(comparison.results.length,32);
  assert.equal(catalog.weapons.length,8);assert.equal(preview.connectedComponentsPerModel,1);
  for(const w of catalog.weapons) {
    assert.equal(path.basename(w.model),w.model);assert.equal(path.basename(w.mesh),w.mesh);
    const model=await json(w.model),mesh=await json(w.mesh);
    assert.equal(model.cubes.length,w.cubes);assert.equal(mesh.triangleCount,w.triangles);
    const source=await fs.readFile(path.join(art,'remembrance',w.model));
    assert.equal(hash(source),model.surfaceBake.sourceSha256,'Surface bake source changed');
  }
  const names=['README.md','catalog.json','weapons.png','weapon-arts.design.json','references.json','THREE-LICENSE.txt',
    'validation.json','preview-validation.json','comparison-validation.json',artReportName,
    ...catalog.weapons.flatMap(w=>[w.model,w.blockbench,w.mesh])];
  const files=[];
  for(const name of names.sort()) {
    const bytes=await fs.readFile(path.join(out,name));files.push({path:name,bytes:bytes.length,sha256:hash(bytes)});
  }
  const tools=['remembrance.mjs','remembrance-game.mjs','build-weapons.mjs','game-weapon-bake.mjs','weapon-smooth-surfaces.mjs','verify-smooth-weapons.mjs','player-scene.mjs',
    'weapon-connectivity.mjs','weapon-structure.mjs','weapon-mesh-validation.mjs','verify-weapons.cjs','verify-pixel-weapons.mjs',
    'verify-game-comparison.mjs','weapon-render-probe.mjs','weapons-viewer.mjs','weapons-viewer.html'];
  const generators=[];
  for(const name of tools)generators.push({path:`art/tooling/${name}`,sha256:hash(await fs.readFile(path.join(art,'tooling',name)))});
  await fs.writeFile(path.join(out,'asset-manifest.json'),JSON.stringify({schemaVersion:1,
    kind:'project-native Minecraft art candidate; not a canonical GLB package',profile:'game',date:'2026-10-11',
    provenance:{authoring:'Self-authored MC-style fan art; no extracted game meshes or textures',
      method:pixel?'cuboid hard faces, tile-local 2x2 macro pixels without blur, broad attached Sacred Relic root repair':
        'continuous closed blade lofts/sweeps, smooth normals and tile-local edge-preserving albedo denoising',
      sourceBackup:'art/backups/remembrance-highdetail-20261011-001/backup-manifest.json',
      gameBeforeSmoothingBackup:'art/backups/remembrance-game-before-smoothing-20261011-001/backup-manifest.json',
      smoothBackup:pixel?'art/backups/remembrance-smooth-before-pixel-20261011-001/backup-manifest.json':undefined,
      licenseStatus:'No new redistribution license granted by this manifest',generators},
    models:8,gameplayVerified:false,performanceMeasured:false,
    excludedFromEngineResources:['Blockbench editing files','preview.html','qa','backups','validation reports'],files
  },null,2)+'\n');
  console.log(`GAME_ASSET_SEALED: ${files.length} files`);
}
