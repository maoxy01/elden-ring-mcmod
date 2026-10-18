import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {PNG,shapes,project,stats,opaque,backShield} from './equipment-pixels.mjs';
import {shieldPixels} from './shield-pixel-profile.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),out=path.join(root,'art/equipment-2d/shape-review');
const read=async f=>JSON.parse(await fs.readFile(path.join(root,f),'utf8')),hash=b=>createHash('sha256').update(b).digest('hex');
const full=await read('art/equipment-2d/full/equipment-42.json'),review=await read('art/equipment-2d/identity-review/manifest.json');
await fs.mkdir(path.join(out,'sprites'),{recursive:true});await fs.mkdir(path.join(out,'before'),{recursive:true});
const candidates=[];
function quad(points,color){return {bone:'root',points,color}}
function sourceColor(base,x,y){const p=base.findLast(p=>{const xs=p.points.map(v=>v[0]),ys=p.points.map(v=>v[1]);return x>=Math.min(...xs)&&x<Math.max(...xs)&&y>=Math.min(...ys)&&y<Math.max(...ys)});return p?.color}
for(const id of ['blasphemous_blade','morgotts_cursed_sword','shield']){
 const a=full.assets.find(a=>a.id===id),model=await read(a.source),base=shapes(model),variants=[];
 let ps=base;
 if(id==='morgotts_cursed_sword'){
   ps=base.filter(p=>p.points.every(v=>v[1]<=5));
   // A continuous curved centreline and taper, sampled to solid pixels at export.
   const c=t=>.5+2*t+6*t*t,w=t=>2.5*Math.pow(1-t,.7)+1.7*Math.sin(Math.PI*t);
   const steps=100;
   for(let i=0;i<steps;i++)for(let lane=0;lane<6;lane++){
     const t=i/steps,tt=(i+1)/steps,y=5+39*t,yy=5+39*tt,u=lane/6*2-1,uu=(lane+1)/6*2-1;
     const row=model.cubes.filter(b=>b.from[1]<=y+.1&&b.to[1]>y+.1),lo=Math.min(...row.map(b=>b.from[0])),hi=Math.max(...row.map(b=>b.to[0]));
     const color=sourceColor(base,lo+(hi-lo)*(lane+.5)/6,y+.1)||[103,95,104];
     ps.push(quad([[c(t)+w(t)*u,y],[c(t)+w(t)*uu,y],[c(tt)+w(tt)*uu,yy],[c(tt)+w(tt)*u,yy]],color));
   }
 }
 for(const v of [...a.variants,...a.gui]){
   const filename=path.basename(v.file),before=await fs.readFile(path.join(root,'art/equipment-2d/full',v.file));await fs.writeFile(path.join(out,'before',filename),before);
   let png;
   if(id==='blasphemous_blade'){
     const chosen=review.candidates.find(c=>c.id===id).variants.find(c=>c.state===v.state);const bytes=await fs.readFile(path.join(root,'art/equipment-2d/identity-review',chosen.file));assert.equal(hash(bytes),chosen.sha256);png=PNG.sync.read(bytes);
   }else if(id==='shield'){
     png=shieldPixels(v.size[0],v.state==='back');
   }else{
     png=project(ps,v.size[0],id==='shield'?0:45,base).png;
     if(v.state==='back')png=backShield(png,v.anchorPixels);
   }
   const st=stats(png);assert.equal(st.components8.length,1,id+' '+v.state+' disconnected');assert(st.transparentMargin);assert(opaque(png,...v.anchorPixels));
   const bytes=PNG.sync.write(png);await fs.writeFile(path.join(out,'sprites',filename),bytes);
   variants.push({...v,file:'sprites/'+filename,url:'sprites/'+filename,before:'before/'+filename,beforeHash:hash(before),sha256:hash(bytes),...st});
 }
 candidates.push({...a,variants,gui:[],features:id==='blasphemous_blade'?['用户选用新版','贴附肉红肢体']:id==='morgotts_cursed_sword'?['旧版材质与护手','连续弧形刃线','向尖端渐收']:['下缘连续曲线','保留十字与盾面','正反同轮廓'],status:id==='blasphemous_blade'?'user_selected':'requested_shape_revision',thickness:{units:1,blocks:.0625,displayZIndependentOfXY:true}});
}
await fs.writeFile(path.join(out,'manifest.json'),JSON.stringify({date:'2026-10-16',candidates,audit:review.audit,protectedFiles:review.protectedFiles,validation:{connected:true,opaqueGrips:true,browser:false,inGameAccepted:false}},null,2)+'\n');
await fs.writeFile(path.join(out,'preview-data.js'),'window.IDENTITY='+JSON.stringify({candidates,audit:review.audit})+';\n');
const html=await fs.readFile(path.join(root,'art/equipment-2d/identity-review/preview.html'),'utf8');
await fs.writeFile(path.join(out,'preview.html'),html.replace('武器辨识度对照','刃线与盾缘修订').replace('id="reveal" type="checkbox"','id="reveal" type="checkbox" checked').replace('ctx.rotate((left?1:-1)*Math.PI/4)','ctx.rotate(a.id===\'shield\'?0:(left?1:-1)*Math.PI/4)').replace('ART-WEAPON-IDENTITY-REVIEW-20261016.md','ART-CURVE-REVISION-20261016.md'));
console.log('PASS 3 revisions, 10 PNGs, original dimensions and anchors; source art untouched.');
