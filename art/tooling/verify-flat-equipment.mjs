import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {createRequire} from 'node:module';
import {Box3, Vector3} from 'three';
import {createAsset} from './player-scene.mjs';
import {connectedComponents} from './weapon-connectivity.mjs';
import {validateBakedMesh} from './weapon-mesh-validation.mjs';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const require=createRequire('C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json');
const {PNG}=require('pngjs');
const report=JSON.parse(await fs.readFile(path.join(root,'art/flat-equipment-report.json'),'utf8'));
const json=async file=>JSON.parse(await fs.readFile(file,'utf8'));
const utilityDesignBounds={
  'eldencombat:spear':{min:[-3,-12,-.5],max:[3,52,.5]},
  'eldencombat:shield':{min:[-6,-7,-.5],max:[6,7,.5]},
  'eldencombat:long_bow':{min:[-9,-29,-.5],max:[2,29,.5]}
};
function utilityDesign(model){
  const expected=utilityDesignBounds[model.id];assert(expected,model.id+': utility_v4 is not authorized for this asset');
  const refinement=model.flatProfile.refinement;
  assert.equal(refinement.revision,1);assert.equal(refinement.source,'art/tooling/spear-shield-bow-refinement.mjs');
  assert.deepEqual(refinement.designBoundsModelUnits,expected,model.id+': utility design contract mismatch');
  return expected;
}
const sha=bytes=>createHash('sha256').update(bytes).digest('hex');
const manifest=await json(path.join(report.backup,'backup-manifest.json'));
for(const f of manifest.files)assert.equal(sha(await fs.readFile(path.join(report.backup,f.path))),f.sha256);
const styleManifest=await json(path.join(report.styleBackup,'backup-manifest.json'));
for(const f of styleManifest.files)assert.equal(sha(await fs.readFile(path.join(report.styleBackup,f.path))),f.sha256);
for(const f of manifest.files.filter(f=>f.path.endsWith('.animations.json')||f.path.endsWith('.skin.json')||f.path.endsWith('hit-shapes.json')||/player_(classic|slim)\.model\.json$/.test(f.path)))
  assert.equal(sha(await fs.readFile(path.join(root,f.path))),f.sha256,'Untouched file: '+f.path);
const checked=[],collections=[];
for(const dir of ['art/weapons','art/remembrance-game','art/player']) {
  const entries=dir==='art/player'?['straight_sword','great_sword','shield','healing_flask'].map(id=>({model:id+'.model.json',blockbench:id+'.bbmodel'})):(await json(path.join(root,dir,'catalog.json'))).weapons;
  const atlas=PNG.sync.read(await fs.readFile(path.join(root,dir,dir==='art/player'?'equipment.png':'weapons.png')));
  assert.equal(atlas.width,dir==='art/player'?64:128);assert.equal(atlas.height,atlas.width);
  const previewEntries=[];
  for(const entry of entries) {
    const model=await json(path.join(root,dir,entry.model)),source=await json(path.join(report.backup,dir,entry.model));
    assert.equal(model.id,source.id);assert.equal(model.unitsPerBlock,16);
    assert.equal(model.cubes.length,entry.cubes??model.cubes.length);
    assert.equal(model.flatProfile.thicknessUnits,1);assert(!model.smoothSurfaces);
    assert.deepEqual(model.bones,source.bones.map(b=>({...b,pivot:[b.pivot[0],b.pivot[1],0]})));
    const bb=await json(path.join(root,dir,entry.blockbench));assert.equal(bb.elements.length,model.cubes.length);
    for(const [i,cube] of model.cubes.entries()) {
      assert.equal(cube.to[2]-cube.from[2],1);assert.deepEqual(cube.rotation,[0,0,0]);
      assert([...cube.from,...cube.to].every(Number.isFinite));
      assert(cube.to.every((v,a)=>v>cube.from[a]));
      assert([...cube.from.slice(0,2),...cube.to.slice(0,2)].every(v=>Number.isInteger(v*2)));
      assert.deepEqual(bb.elements[i].from,cube.from);assert.deepEqual(bb.elements[i].to,cube.to);
      assert(/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(bb.elements[i].uuid));
      for(const [face,uv] of Object.entries(model.faceUVs[cube.name])) {
        assert(uv.every(Number.isFinite)&&uv[0]>=0&&uv[1]>=0&&uv[2]<=atlas.width&&uv[3]<=atlas.height&&uv[2]>uv[0]&&uv[3]>uv[1]);
        assert.deepEqual(bb.elements[i].faces[face].uv,uv);
      }
    }
    const elementIds=new Set(bb.elements.map(e=>e.uuid)),groupIds=new Set(bb.groups.map(g=>g.uuid)),seen=new Set();
    function walk(children){for(const c of children){if(typeof c==='string'){assert(elementIds.has(c));assert(!seen.has(c));seen.add(c);}else{assert(groupIds.has(c.uuid));walk(c.children);}}}
    walk(bb.outliner);assert.equal(seen.size,elementIds.size);
    const asset=createAsset(model,null),before=createAsset({...source,bakedMeshPath:undefined},null);
    asset.root.updateMatrixWorld(true);before.root.updateMatrixWorld(true);
    const bounds=new Box3().setFromObject(asset.root,true),originalBounds=new Box3().setFromObject(before.root,true);
    if(model.flatProfile.refinement?.id==='utility_v4') {
      const authored=utilityDesign(model);
      for(const [a,axis]of ['x','y','z'].entries())for(const side of ['min','max'])
        assert(Math.abs(bounds[side][axis]-authored[side][a]/16)<1e-6,model.id+': authored bound '+axis+'/'+side);
      assert.equal(connectedComponents(model).length,1,model.id+': authored rest silhouette disconnected');
    }else for(const axis of ['x','y'])for(const side of ['min','max'])assert(Math.abs(bounds[side][axis]-originalBounds[side][axis])<=.09,model.id+': silhouette bound '+axis+'/'+side);
    let components=null;
    if(model.bones.length===1){components=connectedComponents(model).length;assert.equal(components,1,model.id+': detached part');}
    if(model.bakedMeshPath){const mesh=await json(path.join(root,dir,model.bakedMeshPath));validateBakedMesh(mesh);assert.equal(mesh.id,model.id);assert.equal(mesh.triangleCount,entry.triangles);}
    const size=bounds.getSize(new Vector3()),center=bounds.getCenter(new Vector3()),height=Math.max(size.y,size.x*1.5)*1.18;
    previewEntries.push({id:model.id.split(':')[1],frame:[center.x-height/3,center.x+height/3,center.y-height/2,center.y+height/2]});
    checked.push({id:model.id,legacy:dir==='art/player',cubes:model.cubes.length,components});
  }
  collections.push({dir,entries:previewEntries,page:dir==='art/player'?'equipment-flat-preview.html':'preview.html'});
}
assert.equal(checked.length,45);
const output=path.join(root,'art/flat-equipment-qa');await fs.mkdir(output,{recursive:true});
const results={status:'ASSET_VALIDATION_PASSED',backupFiles:manifest.files.length,styleBackupFiles:styleManifest.files.length,models:checked,inGameAccepted:false};
if(process.argv.includes('--browser')) {
  const {chromium}=require('playwright');
  const browser=await chromium.launch({channel:'msedge',headless:true,args:['--allow-file-access-from-files']});
  const errors=[],captures=[];
  try {
    const page=await browser.newPage({viewport:{width:1440,height:1024}});
    page.on('pageerror',e=>errors.push(e.message));
    for(const collection of collections) {
      await page.goto(pathToFileURL(path.join(root,collection.dir,collection.page)).href);
      await page.waitForFunction(()=>window.WEAPONS_QA?.textureLoaded);await page.waitForTimeout(200);
      assert.equal(await page.evaluate(()=>window.WEAPONS_QA.visibleModels),collection.entries.length);
      const name=collection.dir.split('/').at(-1);
      await page.screenshot({path:path.join(output,name+'-desktop.png'),fullPage:true});
      for(const entry of collection.entries)for(const angle of [0,Math.PI/2,Math.PI]) {
        const [left,right,bottom,top]=entry.frame,center=(left+right)/2,shift=center*(Math.cos(angle)-1);
        const data=await page.evaluate(args=>window.WEAPONS_CAPTURE(args),{...entry,angle,frame:[left+shift,right+shift,bottom,top]});
        const png=PNG.sync.read(Buffer.from(data.split(',')[1],'base64'));let pixels=0,edges=0;
        for(let y=0;y<png.height;y++)for(let x=0;x<png.width;x++) {
          const i=(y*png.width+x)*4;if(png.data[i]<100){pixels++;if(x<2||y<2||x>=png.width-2||y>=png.height-2)edges++;}
        }
        assert(pixels>10,entry.id+': blank angle '+angle);assert.equal(edges,0,entry.id+': clipped');
        captures.push({id:entry.id,angle,pixels});
      }
      const id=collection.dir==='art/remembrance-game'?'sacred_relic_sword':collection.dir==='art/weapons'?'great_shield':'healing_flask';
      await page.selectOption('#weapon',id);await page.waitForTimeout(100);
      await page.screenshot({path:path.join(output,name+'-detail.png'),fullPage:true});
      await page.setViewportSize({width:390,height:844});await page.selectOption('#weapon','all');await page.waitForTimeout(150);
      assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Mobile overflow');
      await page.screenshot({path:path.join(output,name+'-mobile.png'),fullPage:true});
      await page.setViewportSize({width:1440,height:1024});
    }
    assert.deepEqual(errors,[]);results.browser={status:'PASSED',captures:captures.length,views:captures,errors,desktop:[1440,1024],mobile:[390,844]};
  } finally {await browser.close();}
}
await fs.writeFile(path.join(output,'validation.json'),JSON.stringify(results,null,2)+'\n');
console.log(JSON.stringify({status:results.status,models:checked.length,backupFiles:manifest.files.length,browser:results.browser?.status,captures:results.browser?.captures}));
