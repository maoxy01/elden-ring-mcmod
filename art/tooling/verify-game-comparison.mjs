import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {createRequire} from 'node:module';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {build} from 'esbuild';
import {Box3,Vector3,Matrix4} from 'three';
import {cubeGeometry} from './game-weapon-bake.mjs';

const art=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const require=createRequire(path.join(process.argv[2],'package.json')),{chromium}=require('playwright'),{PNG}=require('pngjs');
const out=path.join(art,'remembrance-game'),qa=path.join(out,'qa');await fs.mkdir(qa,{recursive:true});
const snapshot=path.join(art,'backups/remembrance-highdetail-20261011-001');
const backup=JSON.parse(await fs.readFile(path.join(snapshot,'backup-manifest.json')));
const catalog=JSON.parse(await fs.readFile(path.join(out,'catalog.json'))),results=[];
const first=JSON.parse(await fs.readFile(path.join(out,catalog.weapons[0].model)));
const pixel=!!first.pixelConsolidation;
const pixelReport=pixel?JSON.parse(await fs.readFile(path.join(out,'pixel-validation.json'))):null;
if(pixel)assert.equal(pixelReport.status,'passed');
const sourceChangedFiles=[];
for(const item of backup.files) {
  const bytes=await fs.readFile(path.join(snapshot,item.path));
  assert.equal(createHash('sha256').update(bytes).digest('hex'),item.sha256,`Backup changed: ${item.path}`);
  if(item.path.startsWith('art/remembrance/')) {
    const current=await fs.readFile(path.join(art,'..',item.path));
    if(!current.equals(bytes)) {
      sourceChangedFiles.push(item.path);
      const name=item.path.slice('art/remembrance/'.length);
      const allowed=pixel&&(['sacred_relic_sword.model.json','sacred_relic_sword.bbmodel','catalog.json',
        'validation.json','preview-validation.json','preview.html'].includes(name)||name.startsWith('previews/'));
      assert(allowed,`Unexpected high-detail source change: ${item.path}`);
      if(name==='sacred_relic_sword.model.json') {
        assert.equal(createHash('sha256').update(bytes).digest('hex'),pixelReport.sourceRepair.beforeSha256);
        assert.equal(createHash('sha256').update(current).digest('hex'),pixelReport.sourceRepair.afterSha256);
      }
    }
  }
}
const baselineRoot=path.join(art,'backups/remembrance-game-before-smoothing-20261011-001');
const baselineManifest=JSON.parse(await fs.readFile(path.join(baselineRoot,'backup-manifest.json')));
for(const item of baselineManifest.files)assert.equal(createHash('sha256').update(await fs.readFile(path.join(baselineRoot,item.path))).digest('hex'),item.sha256);
const bundle=await build({stdin:{contents:'import {installRenderProbe} from "./weapon-render-probe.mjs"; import {TextureLoader,NearestFilter,SRGBColorSpace} from "three"; const t=new TextureLoader().load(window.WEAPON_PACK.texture); t.magFilter=t.minFilter=NearestFilter; t.colorSpace=SRGBColorSpace; installRenderProbe(window.WEAPON_PACK,t);',
  resolveDir:path.join(art,'tooling')},bundle:true,write:false,format:'iife',minify:true});
const html=await fs.readFile(path.join(art,'remembrance/preview.html'),'utf8');
const sourceProbe=path.join(qa,'source-probe.html');
await fs.writeFile(sourceProbe,html.replace('</body>',`<script>${bundle.outputFiles[0].text.replaceAll('</script','<\\/script')}</script></body>`));
const baselineHtml=await fs.readFile(path.join(baselineRoot,'art/remembrance-game/preview.html'),'utf8');
const baselineProbe=path.join(qa,'before-smoothing-probe.html');
await fs.writeFile(baselineProbe,baselineHtml.replace('</body>',`<script>${bundle.outputFiles[0].text.replaceAll('</script','<\\/script')}</script></body>`));
if(pixel) {
  const smoothRoot=path.join(art,'backups/remembrance-smooth-before-pixel-20261011-001');
  const manifest=JSON.parse(await fs.readFile(path.join(smoothRoot,'backup-manifest.json')));
  for(const file of manifest.files)assert.equal(createHash('sha256').update(await fs.readFile(path.join(smoothRoot,file.path))).digest('hex'),file.sha256);
}
const browser=await chromium.launch({channel:'msedge',headless:true,
  args:['--use-angle=swiftshader','--enable-unsafe-swiftshader','--allow-file-access-from-files']});
const errors=[];
function compare(a,b) {
  const first=PNG.sync.read(a),second=PNG.sync.read(b);assert.equal(first.data.length,second.data.length);
  let intersection=0,union=0;
  for(let i=0;i<first.data.length;i+=4) {
    const x=first.data[i]<128,y=second.data[i]<128;
    if(x&&y)intersection++;if(x||y)union++;
  }
  assert(union>500);return intersection/union;
}
try {
  const source=await browser.newPage(),game=await browser.newPage(),baseline=await browser.newPage();
  for(const p of [source,game,baseline]) {
    p.on('pageerror',e=>errors.push(e.message));
    await p.addInitScript(()=>{window.requestAnimationFrame=()=>0;});
  }
  await source.goto(pathToFileURL(sourceProbe).href);await game.goto(pathToFileURL(path.join(out,'preview.html')).href);
  await baseline.goto(pathToFileURL(baselineProbe).href);
  for(const p of [source,game,baseline])await p.waitForFunction(()=>window.WEAPONS_CAPTURE&&window.WEAPONS_QA?.textureLoaded);
  for(const w of catalog.weapons) {
    const id=w.id.split(':')[1],high=JSON.parse(await fs.readFile(path.join(art,'remembrance',w.model)));
    const low=JSON.parse(await fs.readFile(path.join(out,w.model)));
    for(const [view,angle] of [['front',0],['oblique',.65],['side',Math.PI/2],['back',Math.PI]]) {
      const bounds=new Box3(),rotation=new Matrix4().makeRotationY(angle);
      for(const model of [high,low])for(const cube of model.cubes) {
        const geometry=cubeGeometry(cube,model,true);geometry.applyMatrix4(rotation);geometry.computeBoundingBox();
        bounds.union(geometry.boundingBox);geometry.dispose();
      }
      const center=bounds.getCenter(new Vector3()),extent=bounds.getSize(new Vector3());
      const h=Math.max(extent.y*1.12,extent.x*1.12*768/512),width=h*512/768;
      const options={id,angle,frame:[center.x-width/2,center.x+width/2,center.y-h/2,center.y+h/2]};
      const capture=async(p,opts)=>Buffer.from((await p.evaluate(o=>window.WEAPONS_CAPTURE(o),opts)).split(',')[1],'base64');
      const a=await capture(source,options),b=await capture(game,options),cuboids=await capture(game,{...options,baked:false});
      const before=await capture(baseline,options);
      const silhouetteIoU=compare(a,b),bakedMeshIoU=compare(b,cuboids),beforeSmoothingIoU=compare(before,b);
      console.log(`${id}/${view}: source=${silhouetteIoU.toFixed(4)}, before=${beforeSmoothingIoU.toFixed(4)}, fallback=${bakedMeshIoU.toFixed(4)}`);
      results.push({id:w.id,view,silhouetteIoU,bakedMeshIoU,beforeSmoothingIoU});
      await fs.writeFile(path.join(qa,`${id}-${view}-source.png`),a);
      await fs.writeFile(path.join(qa,`${id}-${view}-game.png`),b);
      if(!low.smoothSurfaces?.length)assert(bakedMeshIoU>=.995,`${id}/${view}: baked mesh differs from cuboids: ${bakedMeshIoU}`);
      const threshold=view==='side'?.75:view==='oblique'?.85:.9;
      assert(silhouetteIoU>=threshold,`${id}/${view}: silhouette below ${threshold}: ${silhouetteIoU}`);
      assert(beforeSmoothingIoU>=threshold,`${id}/${view}: smoothing changed silhouette beyond ${threshold}: ${beforeSmoothingIoU}`);
      if(view==='oblique') {
        await fs.writeFile(path.join(qa,`${id}-before-shaded.png`),await capture(baseline,{...options,shaded:true}));
        await fs.writeFile(path.join(qa,`${id}-after-shaded.png`),await capture(game,{...options,shaded:true}));
      }
    }
  }
  assert.deepEqual(errors,[]);
} finally {await browser.close();}
const report={status:'passed',backupFilesVerified:backup.files.length,sourceAssetsUnchanged:sourceChangedFiles.length===0,
  sourceChangedFiles,sourceRepair:pixelReport?.sourceRepair,
  beforeSmoothingFilesVerified:baselineManifest.files.length,
  views:['front','oblique','side','back'],resolution:[512,768],
  thresholds:{front:.9,oblique:.85,side:.75,back:.9,beforeSmoothing:'same four thresholds as source'},
  compatibilityNote:pixel?'R3 restores cuboids: baked mesh must match cuboid silhouette >= .995; source root correction is explicitly hashed and other source models are unchanged.':
    'R2 replaces stepped cuboids with continuous surfaces; fallback IoU is diagnostic, not an equality gate.',
  metric:'aligned opaque silhouette intersection over union, no paired-preview offsets',results,errors,
  gameplayVerified:false,performanceMeasured:false};
await fs.writeFile(path.join(out,'comparison-validation.json'),JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report,null,2));
