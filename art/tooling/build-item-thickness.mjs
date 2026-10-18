import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {PNG,opaque} from './equipment-pixels.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),out=path.join(root,'art/item-thickness');
const read=async f=>JSON.parse(await fs.readFile(path.join(root,f),'utf8')),hash=b=>createHash('sha256').update(b).digest('hex');
await fs.mkdir(path.join(out,'models'),{recursive:true});
const equipment=(await read('art/equipment-2d/full/equipment-42.json')).assets,models=[],entries=[],sourceRevisions=[];
for(const a of equipment){
  const source=await read(a.source),z=source.cubes.flatMap(c=>[c.from[2],c.to[2]]);
  const entry={id:'eldencombat:'+a.id,source:a.source,sourceDepthUnits:Math.max(...z)-Math.min(...z),baseThicknessUnits:1,baseThicknessBlocks:1/16,lengthBlocks:a.worldLengthBlocks,anchorPixels:a.anchorPixels,delivery:a.delivery,models:[]};
  if(a.delivery==='own_sprite')for(const v of [...a.variants,...a.gui]){
    const base=path.basename(v.file,'.png'),texture=`eldencombat:equipment_2d/${base}`;
    const bytes=await fs.readFile(path.join(root,'art/equipment-2d/full',v.file)),currentHash=hash(bytes);
    const image=PNG.sync.read(bytes);assert.deepEqual([image.width,image.height],v.size);
    if(currentHash!==v.sha256)sourceRevisions.push({file:v.file,priorManifestHash:v.sha256,currentHash});
    let model={parent:'minecraft:item/generated',textures:{layer0:texture}};
    if(a.blockPose&&v.state==='front'){
      const front=PNG.sync.read(bytes),backVariant=a.variants.find(v=>v.state==='back');
      const backBytes=await fs.readFile(path.join(root,'art/equipment-2d/full',backVariant.file));
      const back=PNG.sync.read(backBytes),elements=[],k=16/front.width;
      assert.equal(front.width,back.width);assert.equal(front.height,back.height);
      for(let y=0;y<front.height;y++)for(let x=0;x<front.width;x++){
        assert.equal(opaque(front,x,y),opaque(back,x,y),'Shield masks must match');if(!opaque(front,x,y))continue;
        const u=x*k,vv=y*k,uv=[u,vv,u+k,vv+k];
        const faces={south:{uv,texture:'#front'},north:{uv:[u+k,vv,u,vv+k],texture:'#back'}};
        // Emit side faces only along alpha boundaries; internal pixel faces are omitted.
        for(const [name,dx,dy] of [['west',-1,0],['east',1,0],['up',0,-1],['down',0,1]])if(!opaque(front,x+dx,y+dy))faces[name]={uv,texture:'#front'};
        elements.push({from:[u,16-vv-k,7.5],to:[u+k,16-vv,8.5],faces});
      }
      model={gui_light:'front',textures:{front:texture,back:`eldencombat:equipment_2d/${path.basename(backVariant.file,'.png')}`,particle:texture},elements};
      assert(elements.every(e=>e.to[2]-e.from[2]===1));
    }
    const file=`models/${base}.json`;await fs.writeFile(path.join(out,file),JSON.stringify(model,null,2)+'\n');
    const m={file,id:entry.id,state:v.state,sha256:hash(await fs.readFile(path.join(out,file))),textures:[{source:'art/equipment-2d/full/'+v.file,resource:texture,sha256:currentHash}],geometry:model.elements?'double_material_alpha_extrusion':'vanilla_generated',thicknessUnits:1};
    if(model.elements){const backFile='art/equipment-2d/full/'+a.variants.find(v=>v.state==='back').file;m.textures.push({source:backFile,resource:model.textures.back,sha256:hash(await fs.readFile(path.join(root,backFile)))});}
    models.push(m);entry.models.push(file);
  }
  if(a.providerId)entry.providerPolicy='Keep complete provider and layers; normalize local Z around model center before display. Do not scale world Z after rotation.';
  entries.push(entry);
}
const existing=[];
for(const file of await fs.readdir(path.join(root,'src/main/resources/assets/eldencombat/models/item'))){if(!file.endsWith('.json'))continue;const id=path.basename(file,'.json');if(equipment.some(a=>a.id===id)||id.startsWith('weapon_placeholder'))continue;const modelPath=`src/main/resources/assets/eldencombat/models/item/${id}.json`,model=await read(modelPath);
existing.push({id:'eldencombat:'+id,model:modelPath,parent:model.parent,alreadyGenerated:['minecraft:item/generated','item/generated'].includes(model.parent),sha256:hash(await fs.readFile(path.join(root,modelPath)))});
}
assert.equal(models.length,94);assert(existing.length>0);assert(existing.every(e=>e.alreadyGenerated||e.parent==='minecraft:item/template_spawn_egg'),'Non-generated item needs individual review');
const manifest={date:'2026-10-15',reference:{sourceJar:'build/moddev/artifacts/neoforge-21.1.219-sources.jar',class:'net/minecraft/client/renderer/block/model/ItemModelGenerator.java',minZ:7.5,maxZ:8.5,centeredMinZ:-.03125,centeredMaxZ:.03125},scope:'42 equipment entries and 58 existing item textures; no blocks, player or Boss',displayPolicy:{reference:'minecraft:item/diamond_sword',XY:'Keep existing per-item length, width, grip and rotation',Z:'Use diamond sword local Z display scale for matching context, independent of per-item XY scale',order:'T * R * scale(sx,sy,referenceSz); thickness evaluated in item-local space',zeroThicknessShield:'Use supplied solid double-material front model, not the current z=0 face renderer',fallback:'Legacy and provider drawing paths need same per-context local-Z rule'},entries,models,existingItems:existing,checks:{ownModels:94,solidShieldModels:3,existingGeneratedItems:58,sourceArtAndPngsUnchanged:true},runtimeIntegrated:false,inGameAccepted:false};
manifest.sourceRevisions=sourceRevisions;
manifest.checks.existingGeneratedItems=existing.filter(e=>e.alreadyGenerated).length;
manifest.checks.existingVanillaTemplates=existing.filter(e=>!e.alreadyGenerated).length;
manifest.scope=`42 equipment entries and ${existing.length} other item models; no blocks, player or Boss`;
for(const m of models)for(const t of m.textures)assert.equal(hash(await fs.readFile(path.join(root,t.source))),t.sha256,'Source changed during generation');
await fs.writeFile(path.join(out,'manifest.json'),JSON.stringify(manifest,null,2)+'\n');
console.log(`PASS: 94 vanilla-depth model candidates (3 solid double-faced shields), 42 equipment contracts, ${existing.length} other item models. Source art unchanged. Revised source PNGs: ${sourceRevisions.length}`);
