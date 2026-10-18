import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {PNG,project,line,stats,opaque,set} from './equipment-pixels.mjs';
import {slabProject} from './equipment-slab.mjs';
import {assertSelectedArt} from './selected-equipment.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
throw new Error('Shelved by user correction: optimize through Blockbench native tools; do not regenerate these scripted candidates.');
const out='art/equipment-2d/item-polish-20261018/',full='art/equipment-2d/full/';
const ids=['claw','colossal_weapon','fist','glintstone_staff','great_shield','great_spear','sacred_seal','small_shield','whip','great_bow','ballista','test_flask'];
const hash=b=>createHash('sha256').update(b).digest('hex'),read=async f=>JSON.parse(await fs.readFile(path.join(root,f)));
const write=async(f,b)=>{await fs.mkdir(path.dirname(path.join(root,out,f)),{recursive:true});await fs.writeFile(path.join(root,out,f),b)};
const json=(f,d)=>write(f,JSON.stringify(d,null,2)+'\n');
const data=await read(full+'equipment-42.json'),selected=await assertSelectedArt();
assert(ids.every(id=>!selected.assets.some(a=>a.id===id)),'Do not overwrite a newly selected revision');
try{const old=await read(out+'manifest.json');assert(!old.humanAccepted,'This review has been adopted; regeneration is blocked')}catch(e){if(e.code!=='ENOENT')throw e}
async function walk(dir){const all=[];for(const e of await fs.readdir(path.join(root,dir),{withFileTypes:true})){const f=dir+'/'+e.name;if(e.isDirectory())all.push(...await walk(f));else all.push(f)}return all}
const protectedFiles=[];
for(const base of [full.slice(0,-1),'art/item-thickness','art/equipment-2d/user-edits','src/main'])for(const file of await walk(base))protectedFiles.push({file,sha256:hash(await fs.readFile(path.join(root,file)))});
const metal=[[39,47,48],[77,92,93],[134,155,154],[201,215,205]],gold=[[64,58,42],[111,98,62],[163,146,89],[211,194,125]],wood=[[43,33,29],[77,51,39],[120,82,54],[160,121,80]],blue=[[22,56,64],[32,93,107],[70,149,163],[161,211,201]];
const poly=(points,color)=>({bone:'art',points,color}),rect=(x,y,w,h,c)=>poly([[x,y],[x+w,y],[x+w,y+h],[x,y+h]],c),seg=(x,y,xx,yy,w,c)=>line([x,y],[xx,yy],w,c);
const notes={
 claw:'三刃明确分层，刃尖连续收束；手背底座与刀根相连，去除碎斑。',
 colossal_weapon:'大锤头改成完整深铁块面，顶部斜面和铆带分明，长柄保留重武器比例。',
 fist:'拳套突出四指关节和手腕皮革，不再像握柄或缩小的盾牌。',
 glintstone_staff:'辉石使用深青、湖蓝与浅青晶面，杖身保留木色，晶体嵌入叉座。',
 great_shield:'大盾改成截角塔盾，钢边、暗青面与旧金徽饰形成大色块，正背轮廓一致。',
 great_spear:'矛头改成渐尖的长叶形，套筒与杆一体连接，保留独立长度。',
 sacred_seal:'印记明确手指状分叉与金属座，不画悬浮光圈，微小物品仍保留实心握点。',
 small_shield:'小盾保留八角扣盾轮廓，凸起盾脐与木板分区，避免密集锯齿。',
 whip:'鞭梢改为连续S形与递减宽度，手柄到鞭根衔接；静态姿态不冒充攻击动作。',
 great_bow:'大弓强化弓臂和握柄区别，弦从两端连续连接，四个拉弦状态共用画框。',
 ballista:'弩炮强化木托、弩臂铁套与箭槽；五个装填状态共用握点，不改变已校准姿态。',
 test_flask:'药瓶改为阶梯肩、收颈和厚瓶底；金色液体与深红封蜡区分，保留透明背景。'
};
function design(id,state){
 if(id==='claw'){
  const ps=[poly([[-3.8,-2.5],[3.8,-2.5],[3.8,4.3],[-3.8,4.3]],wood[0]),rect(-3,-1.5,6,4.8,wood[2]),rect(-3,2.5,6,1.8,metal[1])];
  for(const [i,x] of [-2.6,0,2.6].entries()){
   const top=16.5-i*.8;ps.push(poly([[x-1,3],[x+.8,3],[x+.7,top-3],[x-.3,top],[x-1.3,top-2]],metal[1]),poly([[x-.2,4],[x+.8,4],[x+.7,top-3],[x-.3,top]],metal[3]));
  }return ps;
 }
 if(id==='colossal_weapon')return [seg(0,-11,0,29,2.9,wood[0]),seg(-.5,-10,-.5,25,1.3,wood[2]),rect(-2,-11,4,2,gold[1]),poly([[-8,22],[7,22],[9,25],[8,34],[-7,35],[-9,31],[-9,25]],metal[0]),poly([[-7,24],[6,24],[6,32],[-7,32]],metal[1]),poly([[-9,25],[-6,22],[7,22],[9,25],[6,27]],metal[2]),rect(-5,24,1.4,8,metal[2]),rect(3.7,24,1.4,8,metal[2]),rect(-1,25,2,4,gold[1])];
 if(id==='fist')return [poly([[-3.7,-2.6],[3,-2.6],[3.5,3],[2.6,6],[-3.8,6],[-4,1]],metal[0]),rect(-3,-2,5.5,2.5,wood[1]),rect(-2.6,0,5,3.8,metal[1]),rect(-3,3.3,1.6,2.5,metal[2]),rect(-.8,3.3,1.6,2.5,metal[3]),rect(1.4,3.3,1.4,2.5,metal[2]),rect(-3,0,5,1,gold[1])];
 if(id==='glintstone_staff')return [seg(0,-11,0,35,1.9,wood[0]),seg(-.35,-10,-.35,33,.8,wood[2]),rect(-1.5,-11,3,2,gold[1]),rect(-1.4,29,2.8,2.4,gold[2]),seg(-.5,32,-3,37,1.5,gold[1]),seg(.5,32,3,37,1.5,gold[1]),poly([[-2.8,34],[-3.4,38],[-1.4,41],[1,40.5],[3,37],[1.8,33.8]],blue[0]),poly([[-2.8,38],[-1.4,41],[-.4,36],[1.8,34],[-1.8,34.5]],blue[2]),poly([[-1.4,41],[1,40.5],[2.5,37],[-.4,36]],blue[1]),seg(-1.4,39,-.7,37,.7,blue[3]),rect(-1.6,32.5,3.2,2,gold[2])];
 if(id==='great_spear')return [seg(0,-11,0,41,1.9,wood[0]),seg(-.4,-10,-.4,40,.75,wood[2]),rect(-1.4,-11,2.8,1.5,metal[1]),rect(-1.6,37,3.2,3.5,gold[1]),poly([[-.9,39],[-2.6,44],[-1.7,50],[0,55],[1.7,50],[2.6,44],[.9,39]],metal[1]),poly([[0,40],[0,55],[-1.7,50],[-2.6,44]],metal[3]),seg(0,41,0,51,.65,metal[2])];
 if(id==='great_shield')return [poly([[-6.5,10.3],[6.5,10.3],[8.2,8.7],[8.2,-8.7],[6,-10.2],[-6,-10.2],[-8.2,-8.7],[-8.2,8.7]],metal[0]),poly([[-6,8.7],[6,8.7],[6.7,7.8],[6.7,-7.8],[5.3,-8.7],[-5.3,-8.7],[-6.7,-7.8],[-6.7,7.8]],metal[2]),rect(-5.6,-7.5,11.2,15.2,[38,64,65]),rect(-4.9,-7,4,14,[44,77,76]),rect(-.7,-7,1.4,14,gold[1]),poly([[-3.5,2],[0,5.5],[3.5,2],[0,-2]],gold[1]),poly([[-2,2],[0,4.1],[2,2],[0,-.3]],gold[2]),rect(-4.5,-4.5,9,1.2,gold[1])];
 if(id==='small_shield')return [poly([[-3.1,5.4],[3.1,5.4],[5.4,3.1],[5.4,-3.1],[3.1,-5.4],[-3.1,-5.4],[-5.4,-3.1],[-5.4,3.1]],metal[0]),poly([[-2.8,4.7],[2.8,4.7],[4.7,2.8],[4.7,-2.8],[2.8,-4.7],[-2.8,-4.7],[-4.7,-2.8],[-4.7,2.8]],metal[2]),poly([[-2.3,3.8],[2.3,3.8],[3.8,2.3],[3.8,-2.3],[2.3,-3.8],[-2.3,-3.8],[-3.8,-2.3],[-3.8,2.3]],wood[1]),rect(-2.5,-3.5,1.7,7,wood[2]),rect(1,-3.5,1.7,7,wood[2]),poly([[-1.7,-1],[-1,1.7],[1.3,1.7],[2,.2],[.8,-1.8]],metal[1]),poly([[-1.7,-1],[-1,1.7],[.3,1.7],[.3,-.5]],metal[3])];
 if(id==='sacred_seal')return [rect(-1.5,-1.3,3,3.5,gold[0]),rect(-1.1,-1.1,2.2,3.5,gold[2]),poly([[-3,2],[-3,5.6],[-1.5,7.2],[-1.2,8.7],[0,8.7],[.4,7.3],[2.2,8.7],[3.1,7.6],[2.3,5.3],[1,4.6],[1,2]],gold[1]),poly([[-2.3,2.5],[-2.3,5.3],[-.9,6.5],[-.7,8.2],[-.1,8.2],[.1,5.7],[1.6,7.2],[2.1,7.5],[1.5,5.7],[0,4.5],[0,2.5]],gold[3])];
 if(id==='whip'){
  const ps=[seg(0,-3.5,0,6,2.1,wood[0]),seg(-.35,-3,-.35,5.5,.8,wood[2]),rect(-1.4,-3.5,2.8,1.4,gold[1]),rect(-1.35,4.5,2.7,1.3,gold[1])];
  const points=[[0,5],[.1,10],[-1,15],[-4,19],[-8.5,22],[-12.5,26],[-15,31],[-16.5,36.5]];
  for(let i=0;i<points.length-1;i++){const [x,y]=points[i],[xx,yy]=points[i+1],w=1.8-i*.13;ps.push(seg(x,y,xx,yy,w,wood[1]));if(i<5)ps.push(seg(x-.2,y,xx-.2,yy,w*.4,wood[3]))}return ps;
 }
 if(id==='great_bow'){
  const t=({standby:0,pulling_0:.3,pulling_1:.65,pulling_2:1})[state]??0,ps=[];
  for(const sign of [-1,1]){
   const pts=[[0,sign*3],[-1,sign*12],[-4,sign*23],[-9,sign*32],[-11.5-3*t,sign*35]];
   for(let i=0;i<pts.length-1;i++){const [x,y]=pts[i],[xx,yy]=pts[i+1];ps.push(seg(x-2*t*i/3,y,xx-2*t*(i+1)/3,yy,3.8-i*.55,wood[0]),seg(x-2*t*i/3-.3,y,xx-2*t*(i+1)/3-.3,yy,1.8-i*.25,wood[2]))}
   ps.push(seg(-8-2*t,sign*29,-11.5-5*t,sign*35,1.8,metal[2]));
  }
  ps.push(seg(0,-4,0,4,3.6,wood[0]),seg(-.5,-4,-.5,4,1.8,gold[1]));
  const tip=-11.5-5*t,center=-11.5-14*t;
  ps.push(seg(tip,-35,center,0,.9,[173,168,143]),seg(center,0,tip,35,.9,[173,168,143]));
  if(t)ps.push(seg(center,0,7,0,1.05,wood[3]),poly([[5,-1.8],[9,0],[5,1.8]],metal[2]));
  return ps;
 }
 if(id==='ballista'){
  const t=({standby:0,loading_0:.3,loading_1:.65,loading_2:1,loaded:1})[state]??0;
  const ps=[poly([[-2.7,-7],[2.7,-7],[3,17],[1.4,20],[-1.4,20],[-3,17]],wood[0]),rect(-1.9,-6,3.8,23,wood[1]),rect(-1.4,-4,1,20,wood[3]),rect(-1,3,2,13,metal[0]),seg(-18,10,-7,13,4.2,metal[0]),seg(-7,13,0,11,4.2,metal[0]),seg(0,11,7,13,4.2,metal[0]),seg(7,13,18,10,4.2,metal[0]),seg(-18,10,-7,13,2,metal[2]),seg(-7,13,0,11,2,metal[1]),seg(0,11,7,13,2,metal[1]),seg(7,13,18,10,2,metal[2]),rect(-2.6,-1,5.2,2.2,gold[1]),rect(-2.3,7.8,4.6,3,gold[1])];
  ps.push(seg(-18,10,0,10-11*t,.95,[174,164,131]),seg(0,10-11*t,18,10,.95,[174,164,131]));
  if(state==='loaded')ps.push(seg(0,-1,0,20,1.15,wood[3]),poly([[-1.8,18],[0,23],[1.8,18]],metal[3]));return ps;
 }
 assert.equal(id,'test_flask');return [poly([[-.8,3.9],[.8,3.9],[.8,2.5],[1.4,2],[1.9,1.3],[1.9,-1.3],[1.3,-2.1],[-1.3,-2.1],[-1.9,-1.3],[-1.9,1.3],[-1.4,2],[-.8,2.5]],gold[0]),rect(-.65,2.7,1.3,1.4,[104,48,41]),rect(-.9,2.1,1.8,.6,gold[2]),poly([[-.9,1.8],[.9,1.8],[1.45,.9],[1.45,-1.1],[.9,-1.55],[-.9,-1.55],[-1.45,-1.1],[-1.45,.9]],gold[1]),poly([[-1.25,.7],[.9,.7],[1.25,.3],[1.25,-1],[.7,-1.4],[-.8,-1.4],[-1.25,-.8]],gold[2]),rect(-1.1,-.5,.5,1.5,gold[3]),rect(-.35,1,.8,.45,gold[2])];
}
function shieldBack(p,anchor){
 const b=new PNG({width:p.width,height:p.height});
 for(let y=0;y<p.height;y++)for(let x=0;x<p.width;x++)if(opaque(p,x,y)){
  const edge=[[-1,0],[1,0],[0,-1],[0,1]].some(([dx,dy])=>!opaque(p,x+dx,y+dy));
  const u=(x-anchor[0])*16/p.width,v=(y-anchor[1])*16/p.height;
  let c=edge?metal[1]:wood[Math.floor((u+8)/2)%2?1:2];
  if(Math.abs(v)<1.1&&Math.abs(u)<3.8)c=wood[0];if(Math.abs(u)<.65&&Math.abs(v)<2)c=gold[1];set(b,x,y,c);
 }return b;
}
const candidates=[],audit=[];
for(const a of data.assets){
 const action=a.delivery!=='own_sprite'?'provider_unchanged':ids.includes(a.id)?'new_candidate':'selected_or_user_art_preserved';
 audit.push({id:a.id,name:a.name,action,worldLengthBlocks:a.worldLengthBlocks,reason:action==='new_candidate'?notes[a.id]:action==='provider_unchanged'?'Complete provider route retained; no copied or modified provider pixels':'Previously selected or user-authored version preserved',states:[...a.variants,...a.gui].map(v=>({state:v.state,file:a.delivery==='own_sprite'?full+v.file:v.file,sha256:v.sha256}))});
 if(action!=='new_candidate')continue;
 await write('before/'+a.id+'.model.json',await fs.readFile(path.join(root,a.source)));
 const variants=[],models=[],n=a.variants[0].size[0],angle=a.projection.angleDegrees;let candidateSpan=a.sourceSpanPixels;
 for(const v of [...a.variants,...a.gui]){
  const old=await fs.readFile(path.join(root,full,v.file));assert.equal(hash(old),v.sha256);
  const state=v.state.startsWith('gui_')?a.variants[0].state:v.state;
  let polys=design(a.id,state==='back'?'front':state);
  if(a.id==='test_flask')polys=polys.map(p=>({...p,points:p.points.map(([x,y])=>[x,y-1.3])}));
  let scale=v.size[0]/n/a.projection.modelUnitsPerPixel;
  if(v.state.startsWith('gui_')){
   const r=angle*Math.PI/180;
   for(const [x,y] of polys.flatMap(p=>p.points))for(const [axis,c] of [[0,x*Math.cos(r)+y*Math.sin(r)],[1,x*Math.sin(r)-y*Math.cos(r)]]){
    if(c>0)scale=Math.min(scale,(v.size[0]-1-v.anchorPixels[axis])/c);
    if(c<0)scale=Math.min(scale,(1-v.anchorPixels[axis])/c);
   }
  }
  const r=project(polys,v.size[0],angle,polys,{scale,origin:v.anchorPixels});
  const png=state==='back'?shieldBack(r.png,v.anchorPixels):r.png,s=stats(png);
  assert.equal(s.components8.length,1,a.id+' '+v.state+' floating');assert(s.transparentMargin,a.id+' '+v.state+' clipped');assert(opaque(png,...v.anchorPixels),a.id+' '+v.state+' grip');
  const file='sprites/'+path.basename(v.file),b=PNG.sync.write(png);await write(file,b);await write('before/'+path.basename(v.file),old);
  await json('designs/'+a.id+'_'+v.state+'.json',{polygons:polys,angleDegrees:angle,frame:{origin:v.anchorPixels,scale},back:state==='back',baseThicknessUnits:1});
  const vv={...v,...s,file,url:file,before:'before/'+path.basename(v.file),beforeHash:hash(old),sha256:hash(b)};variants.push(vv);
  const model='models/'+a.id+'_'+v.state+'.json',m={parent:'minecraft:item/generated',textures:{layer0:'eldencombat:item_polish_20261018/'+a.id+'_'+v.state}};
  await json(model,m);models.push({file:model,sha256:hash(Buffer.from(JSON.stringify(m,null,2)+'\n')),texture:file,textureHash:vv.sha256,baseThicknessUnits:1});
  if(v.state===a.variants[0].state){const ys=polys.flatMap(p=>p.points.map(p=>p[1]));candidateSpan=(Math.max(...ys)-Math.min(...ys))*scale;await json('blockbench/'+a.id+'.bbmodel',slabProject(a,vv,png))}
 }
 candidates.push({...a,sourceSpanPixels:candidateSpan,suggestedDisplay:{...a.suggestedDisplay,uniformXYScale:a.worldLengthBlocks*n/candidateSpan},variants,gui:[],features:[notes[a.id]],status:'independent_candidate_not_selected',model:'blockbench/'+a.id+'.bbmodel',models,projectionPolicy:'Held states share original pixel lattice and grips; per-item world scale derives from the new base silhouette span; GUI fits independently and never sets world length',beforeSourceSpanPixels:a.sourceSpanPixels,runtimeIntegrated:false,inGameAccepted:false});
}
for(const f of protectedFiles)assert.equal(hash(await fs.readFile(path.join(root,f.file))),f.sha256,'Protected changed: '+f.file);
await assertSelectedArt();
const manifest={date:'2026-10-18',scope:'All 42 equipment entries audited; 12 own-art candidates; 16 own selections and 14 provider routes unchanged',counts:{total:42,own:28,provider:14,candidates:12,preservedOwn:16,pngs:candidates.reduce((n,a)=>n+a.variants.length,0)},candidates,audit,protectedFiles,reference:{tool:'Blockbench',source:'https://github.com/JannisX11/blockbench',style:'https://www.blockbench.net/wiki/guides/minecraft-style-guide/',borrowed:'Native bbmodel and vanilla generated model formats; no external pixels or source code copied'},validation:{static:true,protectedUnchanged:true},humanAccepted:false,runtimeIntegrated:false,inGameAccepted:false};
await json('manifest.json',manifest);await write('preview-data.js','window.ITEM_POLISH='+JSON.stringify(manifest)+';\n');
console.log('PASS '+manifest.counts.pngs+' PNGs, 12 bbmodels, 42-entry audit; '+protectedFiles.length+' protected files unchanged');
