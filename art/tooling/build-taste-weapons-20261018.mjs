import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash,randomUUID} from 'node:crypto';
import assert from 'node:assert/strict';
import {PNG,shapes,line,project,stats,opaque} from './equipment-pixels.mjs';
import {refuseSelectedRegeneration} from './selected-equipment.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),dir=path.join(root,'art/equipment-2d/taste-weapons-20261018');
const hash=b=>createHash('sha256').update(b).digest('hex'),read=async f=>JSON.parse(await fs.readFile(path.join(root,f),'utf8'));
const write=async(f,b)=>{await fs.mkdir(path.dirname(path.join(dir,f)),{recursive:true});await fs.writeFile(path.join(dir,f),b)};
const data=await read('art/equipment-2d/full/equipment-42.json');
const ids=['curved_sword','curved_greatsword','twinblade','marikas_hammer','mohgwyns_sacred_spear'];
await refuseSelectedRegeneration(ids);
async function walk(p){let a=[];for(const e of await fs.readdir(p,{withFileTypes:true})){const f=path.join(p,e.name);a.push(...(e.isDirectory()?await walk(f):[f]))}return a}
const protectedFiles=[];for(const base of ['art/equipment-2d/full','art/item-thickness','art/equipment-2d/user-edits'])for(const f of await walk(path.join(root,base)))protectedFiles.push({file:path.relative(root,f).replaceAll('\\','/'),sha256:hash(await fs.readFile(f))});
const steel=[[49,61,65],[91,109,112],[144,164,165],[203,214,205]],gold=[[70,60,42],[124,106,63],[178,156,93],[218,199,127]],stone=[[56,58,54],[90,92,80],[125,126,108],[154,152,129]];
const poly=(points,color)=>({bone:'root',points,color}),strip=(x1,y1,x2,y2,w,c)=>line([x1,y1],[x2,y2],w,c);
function blade(ps,low,high,wide=1,sign=1){
 const n=56;for(let i=0;i<n;i++){
  const t=i/n,u=(i+1)/n,center=v=>sign*(.1+5*v*v),width=v=>wide*(1.8-.25*v)*(v<.7?1:(1-v)/.3);
  for(let lane=0;lane<3;lane++){const a=-1+lane*2/3,b=-1+(lane+1)*2/3;ps.push(poly([[center(t)+a*width(t),low+t*(high-low)],[center(t)+b*width(t),low+t*(high-low)],[center(u)+b*width(u),low+u*(high-low)],[center(u)+a*width(u),low+u*(high-low)]],steel[lane+1]))}
 }
}
function design(id,base){
 if(id==='curved_sword'||id==='curved_greatsword'){
  const big=id==='curved_greatsword',low=big?-8:-5,high=big?36:25,ps=[];
  ps.push(strip(0,low,0,3,1.8,[58,43,35]),strip(-3,2,3,2,1.5,gold[1]));blade(ps,3,high,big?2.4:1.05);
  ps.push(strip(0,low,0,low+1,2.6,gold[1]),strip(-3,2,-4,4,1,gold[2]));return ps;
 }
 if(id==='twinblade'){
  const ps=[strip(0,-5,0,5,2.2,[62,43,35]),strip(-3,-5,3,-5,1,gold[1]),strip(-3,5,3,5,1,gold[1])];
  for(const sign of [-1,1]){ps.push(poly([[-1.6,sign*5],[1.8,sign*5],[1.2,sign*20],[0,sign*26],[-2.6,sign*19]],steel[1]));ps.push(poly([[0,sign*5],[1.8,sign*5],[1.2,sign*20],[0,sign*26]],steel[3]));}
  return ps;
 }
 if(id==='marikas_hammer'){
  const ps=[strip(0,-6,0,17,2.2,gold[1]),strip(-.6,-5,-.6,16,.7,gold[2])];
  ps.push(poly([[-7,14],[6,14],[8,17],[7,24],[-6,24],[-8,21],[-8,16]],stone[0]),poly([[-7,16],[5,16],[5,23],[-6,23],[-7,21]],stone[1]),poly([[-7,16],[-5,14],[6,14],[8,17],[5,18]],stone[2]));
  ps.push(strip(-5,22,-1,20,.7,gold[2]),strip(-1,20,1,16,.7,gold[3]),strip(-1,20,5,21,.7,gold[2]),strip(3,23,3,25,1,gold[2]),strip(-3,23,-3,25,1,gold[2]));return ps;
 }
 const ps=[strip(0,-14,0,30,1.8,gold[0]),strip(.4,-14,.4,29,.7,gold[2])];
 ps.push(poly([[-1.2,28],[1.2,28],[1,43],[0,46],[-1,43]],gold[2]));
 for(const sign of [-1,1]){ps.push(strip(sign,29,sign*5,34,1.8,gold[1]),poly([[sign*4.3,33],[sign*6,34],[sign*5.5,42],[sign*4,45],[sign*4.2,38]],gold[2]));ps.push(strip(sign*4.7,35,sign*4.7,41,.5,gold[3]))}
 ps.push(strip(-2,29,2,29,2,gold[2]));return ps;
}
function bbmodel(a,v,png){
 const elements=[],textureUUID=randomUUID(),n=png.width;const used=new Set();
 for(let y=0;y<n;y++)for(let x=0;x<n;x++){
  if(!opaque(png,x,y)||used.has(y*n+x))continue;let w=1;while(x+w<n&&opaque(png,x+w,y)&&!used.has(y*n+x+w))w++;let h=1;
  outer:while(y+h<n){for(let xx=x;xx<x+w;xx++)if(!opaque(png,xx,y+h)||used.has((y+h)*n+xx))break outer;h++}
  for(let yy=y;yy<y+h;yy++)for(let xx=x;xx<x+w;xx++)used.add(yy*n+xx);
  const unit=16/n,faces={};for(const face of ['north','south','east','west','up','down'])faces[face]={uv:face==='south'?[x,y,x+w,y+h]:face==='north'?[x+w,y,x,y+h]:[x,y,x+1,y+1],texture:0};
  elements.push({name:'slab_'+elements.length,type:'cube',uuid:randomUUID(),from:[x*unit,(n-y-h)*unit,7.5],to:[(x+w)*unit,(n-y)*unit,8.5],faces});
 }
 return {meta:{format_version:'4.12',model_format:'java_block',box_uv:false},name:a.name+' Taste Candidate',resolution:{width:n,height:n},elements,outliner:elements.map(e=>e.uuid),textures:[{name:a.id+'.png',uuid:textureUUID,id:'0',mode:'bitmap',source:'data:image/png;base64,'+PNG.sync.write(png).toString('base64'),width:n,height:n,uv_width:n,uv_height:n}],display:{},unhandled_root_fields:{art_contract:{worldLengthBlocks:a.worldLengthBlocks,anchorPixels:v.anchorPixels,baseThicknessUnits:1,runtimeIntegrated:false}}};
}
const candidates=[];
for(const id of ids){
 const a=data.assets.find(a=>a.id===id),source=await fs.readFile(path.join(root,a.source)),base=shapes(JSON.parse(source)),ps=design(id,base),variants=[];let candidateSpan=a.sourceSpanPixels;
 await write('before/'+id+'.model.json',source);
 for(const v of [...a.variants,...a.gui]){
  const prior=await fs.readFile(path.join(root,'art/equipment-2d/full',v.file)),name=path.basename(v.file);await write('before/'+name,prior);
  const r=project(ps,v.size[0],45,[...base,...ps]),st=stats(r.png);assert.equal(st.components8.length,1,id+' floating component');assert(st.transparentMargin,id+' clipped');
  assert(opaque(r.png,...v.anchorPixels),id+' grip mismatch '+v.state);
  const b=PNG.sync.write(r.png);await write('sprites/'+name,b);variants.push({...v,...st,file:'sprites/'+name,url:'sprites/'+name,before:'before/'+name,beforeHash:hash(prior),sha256:hash(b)});
  if(v.state==='front'){const ys=base.flatMap(p=>p.points.map(p=>p[1]));candidateSpan=(Math.max(...ys)-Math.min(...ys))*r.scale;await write('blockbench/'+id+'.bbmodel',JSON.stringify(bbmodel(a,v,r.png),null,2))}
 }
 candidates.push({...a,beforeSourceSpanPixels:a.sourceSpanPixels,sourceSpanPixels:candidateSpan,variants,gui:[],status:'independent_candidate_not_selected',features:['结构轮廓重画','低饱和块面','握点/长度/厚度保留'],model:'blockbench/'+id+'.bbmodel',runtimeIntegrated:false});
}
for(const f of protectedFiles)assert.equal(hash(await fs.readFile(path.join(root,f.file))),f.sha256);
const result={date:'2026-10-18',scope:'five independent weapon art candidates',candidates,audit:[],protectedFiles,runtimeIntegrated:false,humanAccepted:false,validation:{protectedUnchanged:true}};
await write('manifest.json',JSON.stringify(result,null,2)+'\n');await write('preview-data.js','window.IDENTITY='+JSON.stringify(result)+';\n');
console.log('PASS five candidates, 15 PNGs, five 1-unit bbmodels, '+protectedFiles.length+' protected files unchanged');
