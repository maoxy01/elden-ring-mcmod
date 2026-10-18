import {randomUUID} from 'node:crypto';
import {PNG,opaque} from './equipment-pixels.mjs';

// Editable silhouette slabs; runtime uses vanilla generated models instead.
export function slabProject(asset,variant,png){
 const elements=[],used=new Set(),n=png.width;
 for(let y=0;y<n;y++)for(let x=0;x<n;x++){
  if(!opaque(png,x,y)||used.has(y*n+x))continue;
  let w=1;while(x+w<n&&opaque(png,x+w,y)&&!used.has(y*n+x+w))w++;
  let h=1;outer:while(y+h<n){for(let xx=x;xx<x+w;xx++)if(!opaque(png,xx,y+h)||used.has((y+h)*n+xx))break outer;h++}
  for(let yy=y;yy<y+h;yy++)for(let xx=x;xx<x+w;xx++)used.add(yy*n+xx);
  const unit=16/n,faces={};
  for(const face of ['north','south','east','west','up','down'])faces[face]={uv:face==='south'?[x,y,x+w,y+h]:face==='north'?[x+w,y,x,y+h]:[x,y,x+1,y+1],texture:0};
  elements.push({name:'slab_'+elements.length,type:'cube',uuid:randomUUID(),from:[x*unit,(n-y-h)*unit,7.5],to:[(x+w)*unit,(n-y)*unit,8.5],faces});
 }
 return {meta:{format_version:'4.12',model_format:'java_block',box_uv:false},name:asset.name+' Art Review',resolution:{width:n,height:n},elements,outliner:elements.map(e=>e.uuid),textures:[{name:asset.id+'_'+variant.state+'.png',uuid:randomUUID(),id:'0',mode:'bitmap',source:'data:image/png;base64,'+PNG.sync.write(png).toString('base64'),width:n,height:n,uv_width:n,uv_height:n}],unhandled_root_fields:{art_contract:{state:variant.state,worldLengthBlocks:asset.worldLengthBlocks,anchorPixels:variant.anchorPixels,baseThicknessUnits:1,worldScaleRequiresSeparateXYAndZ:true,runtimeIntegrated:false}}};
}
