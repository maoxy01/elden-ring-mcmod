import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {createRequire} from 'node:module';
import {Vector3, Matrix4, Euler} from 'three';
import {build} from 'esbuild';
import {bakeMesh,cubeGeometry} from './game-weapon-bake.mjs';
import {validateBakedMesh} from './weapon-mesh-validation.mjs';
import {equipmentMaterialStyle} from './equipment-material-style.mjs';
import {refinePixelSword} from './sword-pixel-refinement.mjs';
import {refineUtilityEquipment} from './spear-shield-bow-refinement.mjs';
import {refineBladeEquipment} from './blade-equipment-refinement.mjs';
import {bowAnimations,bowDrawData} from './long-bow-motion.mjs';

const require=createRequire('C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json');
const {PNG}=require('pngjs');
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const snapshot=path.join(root,'art/backups/flat-equipment-before-20261012');
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
const write=async(file,data)=>{await fs.mkdir(path.dirname(file),{recursive:true});await fs.writeFile(file,JSON.stringify(data,null,2)+'\n');};
const manifest=[];
async function backup(relative,base=snapshot) {
  const source=path.join(root,relative),target=path.join(base,relative);
  if((await fs.stat(source)).isDirectory()) {
    await fs.mkdir(target,{recursive:true});
    for(const name of (await fs.readdir(source)).sort())await backup(path.join(relative,name),base);
  } else {
    await fs.mkdir(path.dirname(target),{recursive:true});await fs.copyFile(source,target,fs.constants.COPYFILE_EXCL);
    const bytes=await fs.readFile(source);assert.equal(hash(bytes),hash(await fs.readFile(target)));
    manifest.push({path:relative.replaceAll('\\','/'),sha256:hash(bytes),bytes:bytes.length});
  }
}
try {await fs.access(path.join(snapshot,'backup-manifest.json'));}
catch {
  await fs.mkdir(snapshot); // A partial backup is never silently reused or overwritten.
  for(const dir of ['art/weapons','art/remembrance-game','art/player'])await backup(dir);
  await write(path.join(snapshot,'backup-manifest.json'),{kind:'equipment before thin pixel conversion',files:manifest});
}
for(const file of JSON.parse(await fs.readFile(path.join(snapshot,'backup-manifest.json'),'utf8')).files)
  assert.equal(hash(await fs.readFile(path.join(snapshot,file.path))),file.sha256,'Backup integrity: '+file.path);
const styleSnapshot=path.join(root,'art/backups/flat-equipment-v2-before-materials-20261012');
try {await fs.access(path.join(styleSnapshot,'backup-manifest.json'));}
catch {
  await fs.mkdir(styleSnapshot);manifest.length=0;
  for(const dir of ['art/weapons','art/remembrance-game','art/player'])await backup(dir,styleSnapshot);
  await write(path.join(styleSnapshot,'backup-manifest.json'),{kind:'thin pixel revision 2 before catalogue material correction',files:manifest});
}
const step=1, thickness=1;
function texturePalette(materials,png) {
  const palette=structuredClone(materials),counts=new Map();
  for(let i=0;i<png.data.length;i+=16) {
    if(png.data[i+3]<128)continue;
    const color=[0,1,2].map(c=>Math.min(255,Math.round(png.data[i+c]/32)*32)),key=color.join(',');
    counts.set(key,(counts.get(key)||0)+1);
  }
  for(const [key] of [...counts].sort((a,b)=>b[1]-a[1])) {
    const color=key.split(',').map(Number);
    if(Object.values(palette).some(m=>m.color.reduce((n,v,i)=>n+(v-color[i])**2,0)<24**2))continue;
    palette['albedo_'+Object.keys(palette).length]={color};if(Object.keys(palette).length>=64)break;
  }
  const cache=new Map(),entries=Object.entries(palette);
  const sample=(u,v)=> {
    const x=Math.max(0,Math.min(png.width-1,Math.floor(u))),y=Math.max(0,Math.min(png.height-1,Math.floor(v))),p=(y*png.width+x)*4;
    const color=[png.data[p],png.data[p+1],png.data[p+2]],key=color.join(',');
    if(!cache.has(key))cache.set(key,entries.reduce((best,[name,m])=> {
      const distance=m.color.reduce((n,c,i)=>n+(c-color[i])**2,0);return distance<best.distance?{name,distance}:best;
    },{name:entries[0][0],distance:Infinity}).name);
    return cache.get(key);
  };
  sample.palette=palette;return {palette,sample};
}
const cross=(a,b,c)=>(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);
function hull(points) {
  const sorted=[...new Map(points.map(p=>[p.join(','),p])).values()].sort((a,b)=>a[0]-b[0]||a[1]-b[1]);
  const chain=items=>{const out=[];for(const p of items){while(out.length>1&&cross(out.at(-2),out.at(-1),p)<=0)out.pop();out.push(p);}return out;};
  const a=chain(sorted),b=chain([...sorted].reverse());return [...a.slice(0,-1),...b.slice(0,-1)];
}
function touches(poly,x,y) {
  const square=[[x,y],[x+step,y],[x+step,y+step],[x,y+step]];
  const axes=[[1,0],[0,1],...poly.map((p,i)=>{const q=poly[(i+1)%poly.length];return [q[1]-p[1],p[0]-q[0]];})];
  for(const [a,b] of axes) {
    const p=poly.map(v=>v[0]*a+v[1]*b),s=square.map(v=>v[0]*a+v[1]*b);
    if(Math.max(...p)<=Math.min(...s)+1e-8||Math.max(...s)<=Math.min(...p)+1e-8)return false;
  }
  return true;
}
function flatModel(source,sample,style) {
  const model=structuredClone(source),layers=new Map();
  for(const c of source.cubes) {
    const pivot=new Vector3(...(c.pivot||c.from));
    const rotation=new Matrix4().makeRotationFromEuler(new Euler(...(c.rotation||[0,0,0]).map(n=>n*Math.PI/180),'XYZ'));
    const corners=Array.from({length:8},(_,i)=>new Vector3(...c.from.map((v,a)=>(i&(1<<a))?c.to[a]:v)).sub(pivot).applyMatrix4(rotation).add(pivot));
    const poly=hull(corners.map(p=>[p.x,p.y]));
    if(poly.length<3)continue;
    const near=Math.min(...corners.map(p=>p.z)),far=Math.max(...corners.map(p=>p.z));
    let triangles=[];
    if(sample) {
      const faceUVs=source.faceUVs||Object.fromEntries(source.cubes.map(c=>[c.name,Object.fromEntries(['north','south','east','west','up','down'].map(f=>[f,source.materials[c.material].uv]))]));
      const geometry=cubeGeometry(c,{...source,faceUVs}),positions=geometry.attributes.position,uvs=geometry.attributes.uv,indices=geometry.index.array;
      for(let i=0;i<indices.length;i+=3) {
        const verts=Array.from(indices.slice(i,i+3),n=>({x:positions.getX(n),y:positions.getY(n),z:positions.getZ(n),u:uvs.getX(n),v:uvs.getY(n)}));
        const [a,b,d]=verts,den=(b.y-d.y)*(a.x-d.x)+(d.x-b.x)*(a.y-d.y);
        if(Math.abs(den)>1e-7)triangles.push({verts,den});
      }
      geometry.dispose();
    }
    if(!layers.has(c.bone))layers.set(c.bone,new Map());
    const pixels=layers.get(c.bone);
    for(let x=Math.floor(Math.min(...poly.map(p=>p[0]))/step);x<Math.ceil(Math.max(...poly.map(p=>p[0]))/step);x++)
      for(let y=Math.floor(Math.min(...poly.map(p=>p[1]))/step);y<Math.ceil(Math.max(...poly.map(p=>p[1]))/step);y++) {
        if(!touches(poly,x*step,y*step))continue;
        const key=x+','+y,p=pixels.get(key)||{x,y,near:Infinity,far:-Infinity};
        let front=c.material,back=c.material,zNear=near,zFar=far;
        const hits=[];
        for(const {verts:[a,b,d],den} of triangles) {
          const px=(x+.5)*step,py=(y+.5)*step,l=((b.y-d.y)*(px-d.x)+(d.x-b.x)*(py-d.y))/den;
          const m=((d.y-a.y)*(px-d.x)+(a.x-d.x)*(py-d.y))/den,n=1-l-m;
          if(l>=-1e-7&&m>=-1e-7&&n>=-1e-7)hits.push({z:a.z*l+b.z*m+d.z*n,material:sample(a.u*l+b.u*m+d.u*n,a.v*l+b.v*m+d.v*n)});
        }
        if(hits.length){hits.sort((a,b)=>a.z-b.z);zNear=hits[0].z;front=hits[0].material;zFar=hits.at(-1).z;back=hits.at(-1).material;}
        if(zNear<=p.near){p.near=zNear;p.front=front;}
        if(zFar>=p.far){p.far=zFar;p.back=back;}
        pixels.set(key,p);
      }
  }
  const cubes=[],faceMaterials={};let pixelCount=0;
  for(const [bone,pixels] of layers) {
    // A vanilla-style shield is a continuous panel, not an open ornamental frame.
    if(source.id.endsWith('shield')) {
      const values=[...pixels.values()],left=Math.min(...values.map(p=>p.x))-1,right=Math.max(...values.map(p=>p.x))+1;
      const bottom=Math.min(...values.map(p=>p.y))-1,top=Math.max(...values.map(p=>p.y))+1,outside=new Set(),queue=[[left,bottom]];
      for(let i=0;i<queue.length;i++) {
        const [x,y]=queue[i],key=x+','+y;
        if(x<left||x>right||y<bottom||y>top||outside.has(key)||pixels.has(key))continue;
        outside.add(key);queue.push([x-1,y],[x+1,y],[x,y-1],[x,y+1]);
      }
      const material=source.materials.wood?'wood':source.materials.leather?'leather':Object.keys(source.materials)[0];
      for(let y=bottom+1;y<top;y++)for(let x=left+1;x<right;x++) {
        const key=x+','+y;if(!outside.has(key)&&!pixels.has(key))pixels.set(key,{x,y,front:material,back:material});
      }
      const colors=sample?.palette||source.materials;
      for(const p of pixels.values())for(const face of ['front','back']) {
        if(!colors[p[face]].color.every(v=>v>=180))continue;
        const edge=[[p.x-1,p.y],[p.x+1,p.y],[p.x,p.y-1],[p.x,p.y+1]].some(([x,y])=>!pixels.has(x+','+y));
        p[face]=edge&&source.materials.steel_dark?'steel_dark':material;
      }
    }
    style.paint(pixels,source.id);
    pixelCount+=pixels.size;
    const key=p=>p.front+'|'+p.back+'|'+p.side,used=new Set();
    for(const pixel of [...pixels.values()].sort((a,b)=>a.y-b.y||a.x-b.x)) {
      if(used.has(pixel.x+','+pixel.y))continue;
      const same=(x,y)=>{const p=pixels.get(x+','+y);return p&&!used.has(x+','+y)&&key(p)===key(pixel);};
      let width=1,height=1;
      while(same(pixel.x+width,pixel.y))width++;
      while(Array.from({length:width},(_,i)=>same(pixel.x+i,pixel.y+height)).every(Boolean))height++;
      for(let y=0;y<height;y++)for(let x=0;x<width;x++)used.add((pixel.x+x)+','+(pixel.y+y));
      const name='flat_'+bone+'_'+cubes.length;
      cubes.push({name,bone,from:[pixel.x*step,pixel.y*step,-thickness/2],
        to:[(pixel.x+width)*step,(pixel.y+height)*step,thickness/2],material:pixel.front,rotation:[0,0,0],pivot:[0,0,0]});
      faceMaterials[name]={front:pixel.front,back:pixel.back,side:pixel.side};
    }
  }
  model.cubes=cubes;
  for(const b of model.bones){assert.equal(b.rotation[0],0);assert.equal(b.rotation[1],0);b.pivot[2]=0;}
  for(const name of ['bakedMesh','bakedMeshPath','smoothSurfaces','faceUVs','structuralChecks','refinement','surfaceQuality','pixelConsolidation','surfaceBake','profileChecks','rootRepair'])delete model[name];
  model.flatProfile={revision:3,pixelStep:step,thicknessUnits:thickness,sourceCubes:source.cubes.length,
    sourceSha256:hash(Buffer.from(JSON.stringify(source))),pixelCount,method:'conservative XY silhouette; two-sided material pixels merged into rectangles',
    origin:'main grip preserved',geometryUnitsUnchanged:true,style:'one-unit pixel slab; catalogue-informed aged material ramps'};
  model.detailLevel='thin two-sided pixel equipment';model.geometryStyle='one-unit pixel slabs';model.batchPreview=true;
  return {model,faceMaterials};
}
function atlas(materials,size) {
  const names=Object.keys(materials),columns=2**Math.ceil(Math.log2(Math.max(4,Math.ceil(Math.sqrt(names.length))))),tile=size/columns,png=new PNG({width:size,height:size}),mapped={};
  assert(Number.isInteger(tile)&&tile>=2,'Atlas palette capacity');
  names.forEach((name,i)=>{const x=i%columns*tile,y=Math.floor(i/columns)*tile;mapped[name]={color:materials[name].color,uv:[x,y,x+tile,y+tile]};});
  for(let y=0;y<size;y++)for(let x=0;x<size;x++) {
    const name=names[Math.floor(y/tile)*columns+Math.floor(x/tile)],color=name?materials[name].color:[32,38,42],i=(y*size+x)*4;
    png.data[i]=color[0];png.data[i+1]=color[1];png.data[i+2]=color[2];png.data[i+3]=255;
  }
  return {materials:mapped,png:PNG.sync.write(png)};
}
function finish({model,faceMaterials},sheet) {
  model.materials=sheet.materials;model.textureSize=[sheet.size,sheet.size];
  model.faceUVs=Object.fromEntries(model.cubes.map(c=>[c.name,Object.fromEntries(['north','south','east','west','up','down'].map(face=>
    [face,sheet.materials[face==='south'?faceMaterials[c.name].back:face==='north'?faceMaterials[c.name].front:faceMaterials[c.name].side].uv
      .map((v,i)=>v+(i<2?.5:-.5))]))]));
}
function blockbench(model,original,png) {
  const ids=Object.fromEntries(model.bones.map(b=>[b.name,original.groups.find(g=>g.name===b.name).uuid]));
  const cubeId=c=>{const h=hash(Buffer.from(model.id+'/'+c.name));return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20,32)}`;};
  const children=bone=>[...model.cubes.filter(c=>c.bone===bone).map(cubeId),...model.bones.filter(b=>b.parent===bone).map(b=>({uuid:ids[b.name],isOpen:true,children:children(b.name)}))];
  return {...original,resolution:{width:model.textureSize[0],height:model.textureSize[1]},
    elements:model.cubes.map(c=>({name:c.name,type:'cube',uuid:cubeId(c),from:c.from,to:c.to,origin:c.pivot,rotation:c.rotation,
      inflate:0,autouv:0,box_uv:false,export:true,visibility:true,faces:Object.fromEntries(['north','south','east','west','up','down'].map(f=>[f,{uv:model.faceUVs[c.name][f],texture:0,rotation:0}]))})),
    groups:original.groups.map(g=>({...g,origin:model.bones.find(b=>b.name===g.name).pivot})),outliner:[{uuid:ids.root,isOpen:true,children:children('root')}],
    textures:original.textures.map(t=>({...t,width:model.textureSize[0],height:model.textureSize[1],uv_width:model.textureSize[0],uv_height:model.textureSize[1],source:'data:image/png;base64,'+png.toString('base64')}))};
}
const reports=[],previews=[];
const bundle=(await build({entryPoints:[path.join(root,'art/tooling/weapons-viewer.mjs')],bundle:true,write:false,format:'iife',minify:true,target:'es2022'})).outputFiles[0].text;
const template=await fs.readFile(path.join(root,'art/tooling/weapons-viewer.html'),'utf8');
for(const directory of ['art/weapons','art/remembrance-game']) {
  const before=path.join(snapshot,directory),out=path.join(root,directory);
  const catalog=JSON.parse(await fs.readFile(path.join(before,'catalog.json'),'utf8'));
  const sources=await Promise.all(catalog.weapons.map(w=>fs.readFile(path.join(before,w.model),'utf8').then(JSON.parse)));
  const sourceTexture=PNG.sync.read(await fs.readFile(path.join(before,'weapons.png'))),{palette,sample}=texturePalette(sources[0].materials,sourceTexture);
  const style=equipmentMaterialStyle(palette,sources[0].materials),size=128,sheet={...atlas(style.materials,size),size};
  await fs.writeFile(path.join(out,'weapons.png'),sheet.png);
  const weapons=[];
  for(let i=0;i<sources.length;i++) {
    const record=catalog.weapons[i],result=flatModel(sources[i],sample,style);finish(result,sheet);
    const model=refineBladeEquipment(refineUtilityEquipment(refinePixelSword(result.model)));
    if(model.bones.length===1) {
      const mesh=bakeMesh(model);validateBakedMesh(mesh);
      model.bakedMeshPath=record.id.split(':')[1]+'.mesh.json';await write(path.join(out,model.bakedMeshPath),mesh);
    }
    await write(path.join(out,record.model),model);
    if(model.id==='eldencombat:long_bow'){await write(path.join(out,'long_bow.animations.json'),bowAnimations);await write(path.join(out,'long_bow.draw.json'),bowDrawData);record.mechanicalClips=['bow_draw'];}
    const original=JSON.parse(await fs.readFile(path.join(before,record.blockbench),'utf8'));
    await write(path.join(out,record.blockbench),blockbench(model,original,sheet.png));
    record.cubes=model.cubes.length;record.detailLevel=model.detailLevel;record.modelStatus='thin aged-material pixel revision 3; in-game acceptance pending';
    record.features=['薄片正反面像素轮廓','1 像素单位厚度','主握持点与骨骼保留'];
    record.damageParts=[];record.flatProfile=model.flatProfile;
    record.sourceCubes=sources[i].cubes.length;
    record.geometryReduction=1-model.cubes.length/sources[i].cubes.length;
    if(model.bakedMeshPath){const mesh=JSON.parse(await fs.readFile(path.join(out,model.bakedMeshPath),'utf8'));record.mesh=model.bakedMeshPath;record.triangles=mesh.triangleCount;record.vertices=mesh.vertexCount;}
    const animations=model.id==='eldencombat:long_bow'?[]:await fs.readFile(path.join(before,record.id.split(':')[1]+'.animations.json'),'utf8').then(JSON.parse).then(a=>a.clips).catch(()=>[]);
    const display=structuredClone(model);if(model.bakedMeshPath)display.bakedMesh=JSON.parse(await fs.readFile(path.join(out,model.bakedMeshPath),'utf8'));
    weapons.push({...record,id:record.id.split(':')[1],model:display,animations,pairedPreview:!!record.paired});
    reports.push({id:model.id,beforeCubes:sources[i].cubes.length,cubes:model.cubes.length,thicknessUnits:thickness,bones:model.bones.length});
  }
  catalog.style='thin two-sided pixel equipment';await write(path.join(out,'catalog.json'),catalog);
  const arts=JSON.parse(await fs.readFile(path.join(before,'weapon-arts.design.json'),'utf8')).arts;
  const remembrance=directory.endsWith('remembrance-game');
  const pack={weapons,arts,texture:'data:image/png;base64,'+sheet.png.toString('base64'),title:remembrance?'追忆武器 · 薄片像素版':'武器装备 · 薄片像素版',
    collection:remembrance?'remembrance':'types',variant:'flat',galleryColumns:remembrance?4:6,cellWidth:remembrance?4.5:3.5,modelSpan:remembrance?3.25:2.7,
    footer:weapons.length+' 件 · 薄片像素版 · 128 像素图集'};
  await fs.writeFile(path.join(out,'preview.html'),template.replace('/* DATA */','window.WEAPON_PACK='+JSON.stringify(pack).replaceAll('<','\\u003c')+';')
    .replace('/* BUNDLE */',bundle.replaceAll('</script','<\\/script')));
  previews.push({directory,models:weapons.length});
}
const legacy=['straight_sword','great_sword','shield','healing_flask'];
const legacySource=JSON.parse(await fs.readFile(path.join(snapshot,'art/player/healing_flask.model.json'),'utf8'));
const legacyStyle=equipmentMaterialStyle(legacySource.materials,legacySource.materials);
const legacySheet={...atlas(legacyStyle.materials,64),size:64};
await fs.writeFile(path.join(root,'art/player/equipment.png'),legacySheet.png);
const legacyEntries=[];
for(const id of legacy) {
  const file='art/player/'+id+'.model.json',source=JSON.parse(await fs.readFile(path.join(snapshot,file),'utf8'));
  const result=flatModel(source,undefined,legacyStyle);finish(result,legacySheet);await write(path.join(root,file),result.model);
  const original=JSON.parse(await fs.readFile(path.join(snapshot,'art/player',id+'.bbmodel'),'utf8'));
  await write(path.join(root,'art/player',id+'.bbmodel'),blockbench(result.model,original,legacySheet.png));
  legacyEntries.push({id,name:({straight_sword:'直剑',great_sword:'大剑',shield:'盾牌',healing_flask:'圣杯瓶'})[id],group:id==='healing_flask'?'装备':'旧版装备',model:result.model,animations:[],features:[]});
  reports.push({id:source.id,legacy:true,beforeCubes:source.cubes.length,cubes:result.model.cubes.length,thicknessUnits:thickness});
}
const legacyPack={weapons:legacyEntries,arts:[],texture:'data:image/png;base64,'+legacySheet.png.toString('base64'),
  title:'装备 · 薄片像素版',collection:'types',variant:'flat',galleryColumns:4,footer:'4 件 · 薄片像素版 · 64 像素图集'};
await fs.writeFile(path.join(root,'art/player/equipment-flat-preview.html'),template.replace('/* DATA */','window.WEAPON_PACK='+JSON.stringify(legacyPack).replaceAll('<','\\u003c')+';').replace('/* BUNDLE */',bundle.replaceAll('</script','<\\/script')));
for(const file of ['player_classic.model.json','player_slim.model.json'])assert.equal(hash(await fs.readFile(path.join(root,'art/player',file))),hash(await fs.readFile(path.join(snapshot,'art/player',file))));
await write(path.join(root,'art/flat-equipment-report.json'),{status:'generated',backup:snapshot,styleBackup:styleSnapshot,scope:'41 weapons/shields plus 4 legacy equipment assets; player/Boss unchanged',
  reports,previews,beforeCubes:reports.reduce((n,r)=>n+r.beforeCubes,0),cubes:reports.reduce((n,r)=>n+r.cubes,0),inGameAccepted:false});
console.log(JSON.stringify({status:'FLAT_EQUIPMENT_GENERATED',models:reports.length,beforeCubes:reports.reduce((n,r)=>n+r.beforeCubes,0),cubes:reports.reduce((n,r)=>n+r.cubes,0),snapshot},null,2));
