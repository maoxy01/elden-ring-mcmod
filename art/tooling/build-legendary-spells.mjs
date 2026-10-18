import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {PNG} from './equipment-pixels.mjs';
import {createBlockPainters} from './legendary-block-painters.mjs';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const out=path.join(root,'art/legendary-spells');
const sources=path.join(root,'dist/法环特效素材');
const sha=b=>createHash('sha256').update(b).digest('hex');
const json=async p=>JSON.parse(await fs.readFile(p,'utf8'));
const write=async(p,b)=>{await fs.mkdir(path.dirname(p),{recursive:true});await fs.writeFile(p,b)};
const save=async(p,d)=>write(p,JSON.stringify(d,null,2)+'\n');
const textureIndex=await json(path.join(sources,'贴图索引.json'));
const spellIndex=await json(path.join(sources,'魔法与祷告索引.json'));
const W=256,H=192,N=24;
const previousRoot=path.join(root,'art/backups/legendary-spells-pre-block-1792166004895/art/legendary-spells');
const previousManifest=await json(path.join(previousRoot,'manifest.json'));
const specs=[
 {id:'comet_azur',magicId:4200,name:'彗星亚兹勒',en:'Comet Azur',type:'魔法',duration:3.2,peak:13,colors:['#173f4b','#227b88','#40b9bd','#83e6da','#d2fff0','#f0fff3'],textures:['s64040_a','s34041_a'],idea:'青白主束、旋流束壳、沿轴抽动的碎光。',motion:'蓄能聚合 → 光束贯通 → 持续流动 → 收束消散',world:[11,5.5],layers:['束芯/束壳：沿施法方向的交叉条带','蓄能：杖尖局部贴片','束端：命中点独立贴片']},
 {id:'stars_of_ruin',magicId:4220,name:'毁灭流星',en:'Stars of Ruin',type:'魔法',duration:3,peak:15,colors:['#263a66','#345f9d','#508dcd','#89c3ed','#d2ecfa','#f2ffff'],textures:['s64040_a','s34060_a'],idea:'十二条分批离手、弯折汇聚的蓝白彗尾。',motion:'星核聚合 → 分批射出 → 各自追踪 → 细尾消散',world:[10,6],layers:['单枚流星：独立 billboard 与尾带','十二路轨迹不烘成一整块平面']},
 {id:'founding_rain',magicId:4210,name:'创星雨',en:'Founding Rain of Stars',type:'魔法',duration:4,peak:15,colors:['#263e55','#355f77','#548da3','#83bed0','#c1e4e9','#edffff'],textures:['s64040_a','s34030_a'],idea:'上层厚星云，下层错拍垂落的晶亮星雨。',motion:'云团铺开 → 雨带递增 → 持续落雨 → 云团退散',world:[8,7],layers:['云层：两层水平贴片','星雨：多条竖向条带','落点：随地形裁剪的局部闪光']},
 {id:'ranni_dark_moon',magicId:4361,name:'菈妮的暗月',en:"Ranni's Dark Moon",type:'魔法',duration:3.6,peak:12,colors:['#1c2e43','#2d4a63','#49768a','#6c9aa8','#a4c9cc','#e0f0de'],textures:['s89011_a','s34070_a'],idea:'有暗部与陨坑的冷月，细霜流环抱月面。',motion:'暗月凝结 → 缓慢离手 → 霜尾拖曳 → 月面崩解',world:[7,6],layers:['月面：需要体积替代或多视向贴片，不是永远朝相机的圆盘','霜雾：月体外缘局部贴片','碎霜：末段独立层']},
 {id:'fell_god_flame',magicId:6110,name:'恶神火焰',en:'Flame of the Fell God',type:'祷告',duration:3.8,peak:11,colors:['#502929','#9b3828','#d65b2c','#ef963d','#ffd173','#fff0b0'],textures:['s31041_a','s34031_a'],idea:'暗红火核与翻卷明焰，爆燃后留下破碎火床。',motion:'火球孕育 → 缓慢漂移 → 延时爆燃 → 地面残焰',world:[8,5],layers:['火核：体积替代或多视向贴片','翻焰：局部朝向片','残焰：地面采样并裁剪，不改方块']},
 {id:'elden_stars',magicId:6720,name:'艾尔登流星',en:'Elden Stars',type:'祷告',duration:4,peak:15,colors:['#655131','#a07e38','#d8b85a','#f3d98a','#fff1bc','#fffbe1'],textures:['s42071_a','s34041_a'],idea:'一枚金色主星，细流星从主轨迹不断分枝。',motion:'金核生成 → 主星游走 → 子星分流 → 金尘收尾',world:[10,6],layers:['主星：独立贴片','子星：独立轨迹与短尾带','枝脉：只表现，不用于服务端判定']},
 {id:'greyoll_roar',magicId:7090,name:'桂奥尔的咆哮',en:"Greyoll's Roar",type:'祷告',duration:3.2,peak:13,colors:['#493b3a','#735553','#a36b61','#ce9383','#e7b5a1','#f7d7b7'],textures:['s00003_r','s34031_a'],idea:'有角、有颌骨的苍老龙首，张口推出破碎声压。',motion:'龙首显形 → 张颌聚压 → 声浪扩散 → 首尾退散',world:[10,6],layers:['龙首：自绘侧影，仅当前审看视角；多视角需补绘','声压：两层不规则扩散条带','地面碎屑：纯表现，不自动破坏方块']}
];

// Keep accepted equipment, shipped VFX art, and extracted source bytes outside this write set.
async function walk(p){let a=[];for(const e of await fs.readdir(p,{withFileTypes:true})){const f=path.join(p,e.name);a.push(...(e.isDirectory()?await walk(f):[f]))}return a}
const protectedRoots=['art/equipment-2d/full','art/item-thickness','art/vfx-2d'];
const protectedFiles=[];
for(const p of protectedRoots)for(const f of await walk(path.join(root,p)))protectedFiles.push({file:path.relative(root,f).replaceAll('\\','/'),sha256:sha(await fs.readFile(f))});
const sourceNames=[...new Set(specs.flatMap(s=>s.textures))];
const maps=new Map(),sourceRecords=[];
for(const name of sourceNames){
 const entry=textureIndex.find(t=>t.name===name&&t.bundle==='本体');assert(entry,`Missing texture ${name}`);
 const b=await fs.readFile(path.join(sources,entry.png));assert.equal(sha(b),entry.png_sha256);
 const p=PNG.sync.read(b);maps.set(name,p);
 const thumb=new PNG({width:128,height:128});
 for(let y=0;y<128;y++)for(let x=0;x<128;x++){const i=(Math.floor(y/128*p.height)*p.width+Math.floor(x/128*p.width))*4;thumb.data.set(p.data.subarray(i,i+4),(y*128+x)*4)}
 const file=`references/${name}.png`;await write(path.join(out,file),PNG.sync.write(thumb));
 sourceRecords.push({name,source:'dist/法环特效素材/'+entry.png,sha256:sha(b),size:[p.width,p.height],preview:file,previewSha256:sha(PNG.sync.write(thumb)),usage: name==='s89011_a'?'v2 coarse crater-tone samples; v1 comparison uses spherical moon sampling':name==='s31041_a'?'8x8 atlas, explicitly select one cell':name==='s42071_a'?'8x8 atlas, select one cell for gold detail':name==='s00003_r'?'4x4 shared red-channel mask in source references; not a detailed texture or dragon mesh':'coarse material modulation/reference; not complete FXR playback'});
}
const clamp=(v,a=0,b=1)=>Math.max(a,Math.min(b,v));
const smooth=v=>{v=clamp(v);return v*v*(3-2*v)};
const frac=v=>v-Math.floor(v);
const noise=(x,y,k=0)=>frac(Math.sin(x*127.1+y*311.7+k*71.3)*43758.5453);
const rgb=h=>h.match(/[a-f\d]{2}/gi).map(v=>parseInt(v,16));
function sample(name,u,v,cell){
 const p=maps.get(name);let xx=frac(u),yy=frac(v);
 if(cell!==undefined){xx=(cell%8+xx)/8;yy=(Math.floor(cell/8)+yy)/8}
 const i=(Math.min(p.height-1,Math.floor(yy*p.height))*p.width+Math.min(p.width-1,Math.floor(xx*p.width)))*4;
 return (p.data[i]*.25+p.data[i+1]*.55+p.data[i+2]*.2)/255;
}
class Paint {
 constructor(spec){this.png=new PNG({width:W,height:H});this.pal=spec.colors.map(rgb)}
 dot(x,y,c,a=255){x=Math.round(x);y=Math.round(y);if(x<1||y<1||x>=W-1||y>=H-1)return;const i=(y*W+x)*4,col=Array.isArray(c)?c:this.pal[clamp(Math.floor(c),0,5)];this.png.data.set([...col,a],i)}
 rect(x,y,w,h,c,a=255){for(let j=Math.round(y);j<y+h;j++)for(let i=Math.round(x);i<x+w;i++)this.dot(i,j,c,a)}
 line(x,y,xx,yy,c,width=1,a=255){const n=Math.ceil(Math.max(Math.abs(xx-x),Math.abs(yy-y)));for(let j=0;j<=n;j++){const t=j/Math.max(n,1);this.rect(x+(xx-x)*t-width/2,y+(yy-y)*t-width/2,width,width,c,a)}}
 poly(pts,c,a=255){const ys=pts.map(p=>p[1]);for(let y=Math.floor(Math.min(...ys));y<=Math.ceil(Math.max(...ys));y++){const xs=[];for(let i=0;i<pts.length;i++){const p=pts[i],q=pts[(i+1)%pts.length];if((p[1]>y)!==(q[1]>y))xs.push(p[0]+(y-p[1])*(q[0]-p[0])/(q[1]-p[1]))}xs.sort((a,b)=>a-b);for(let i=0;i+1<xs.length;i+=2)for(let x=Math.ceil(xs[i]);x<xs[i+1];x++)this.dot(x,y,c,a)}}
 disc(cx,cy,r,c,a=255){for(let y=-r;y<=r;y++)for(let x=-r;x<=r;x++)if(x*x+y*y<=r*r)this.dot(cx+x,cy+y,c,a)}
 star(x,y,r=3,c=5){this.line(x-r,y,x+r,y,c);this.line(x,y-r,x,y+r,c);this.rect(x-1,y-1,3,3,c)}
 trail(points,width=2){for(let i=1;i<points.length;i++){this.line(...points[i-1],...points[i],1,width+3,128);this.line(...points[i-1],...points[i],3,width);if(i>points.length/2)this.line(...points[i-1],...points[i],5,1)}}
 ring(cx,cy,rx,ry,phase,c=3){for(let i=0;i<68;i++){if((i+Math.floor(phase*11))%11<3)continue;const a=i/68*Math.PI*2,b=(i+1)/68*Math.PI*2,r=1+Math.sin(i*2.9)*.045;this.line(cx+Math.cos(a)*rx*r,cy+Math.sin(a)*ry*r,cx+Math.cos(b)*rx*r,cy+Math.sin(b)*ry*r,c,2)}}
}
function azur(p,t){
 const grow=smooth(t/.22),fade=1-smooth((t-.77)/.23),end=75+grow*159;
 for(let x=65;x<end;x++){
  const v=(x-65)/170,cy=111-v*30,th=(7+6*Math.sin(v*Math.PI))*(.7+.3*fade);
  for(let y=cy-th-5;y<cy+th+5;y++){
   const d=Math.abs(y-cy)/th,n=sample('s34041_a',v*.65-t*.8,y*.026+t*.15),n2=sample('s64040_a',v*.6,y*.033);
   if(d<1.13+n*.4&&noise(Math.floor(x/3),Math.floor(y/2),0)>.08+(1-fade)*.7){const k=d<.28?5:d<.55?4:d<.82?3:n+n2>.45?2:1;p.dot(x,y,k,d>1.2?128:255)}
  }
 }
 for(let k=0;k<5;k++)for(let j=0;j<45;j++){const v=j/44,x=70+v*160*grow,cy=110-v*28,offset=Math.sin(v*15+t*15+k*1.3)*(8+k);if(j>0)p.line(x-3,cy+offset,x,cy+offset+1,k%2?3:2,1,200)}
 p.ring(73,110,11*grow,24*grow,t,3);p.star(74,109,5+grow*5);
 for(let k=0;k<20;k++){const v=frac(k*.137+t*.8),x=79+v*153*grow,y=111-v*29+Math.sin(k*4)*22;p.line(x-4,y+1,x,y,3,1)}
}
function ruin(p,t){
 const o=[69,110];
 for(let k=0;k<12;k++){
  const v=clamp((t-.08-k*.016)/.67);if(v<=0||v>=1)continue;
  const trajectory=u=>{const x=o[0]+u*153,y=o[1]+Math.sin(u*Math.PI)*(-72+k*12)+u*(-30+k%3*13);return [x,y]};
  const pts=[];for(let j=0;j<18;j++)pts.push(trajectory(Math.max(0,v-j*.013)));pts.reverse();p.trail(pts,k%3===0?2:1);const [x,y]=trajectory(v);p.disc(x,y,3,3);p.star(x,y,3,5);
  for(let j=1;j<pts.length;j++){const [xx,yy]=pts[j],n=sample('s34060_a',j/19+k*.1,t*.5);if(n>.26)p.dot(xx,yy+2,3);if(sample('s64040_a',xx*.007,yy*.009)>.48)p.dot(xx,yy,4)}
 }
 const r=12*(1-smooth(t/.45));if(r>0){p.ring(...o,r*.6,r*1.2,t,3);p.star(...o,3)}
}
function rain(p,t){
 const e=smooth(t/.22)*(1-smooth((t-.76)/.24));
 for(let y=25;y<64;y++)for(let x=76;x<223;x++){
  const dx=(x-150)/72,dy=(y-45)/17,n=sample('s64040_a',x*.007+t*.04,y*.018),m=sample('s34030_a',x*.008-t*.05,y*.023);
  if(dx*dx+dy*dy<(1+.2*Math.sin(x*.27))*e&&n>.04){p.dot(x,y,n>.52?4:m>.25?3:1,n<.2?160:255);if(n>.74&&noise(x,y)>.82)p.star(x,y,1,5)}
 }
 if(t>.16&&t<.91)for(let k=0;k<25;k++){
  const f=frac(t*2.1+k*.337),x=85+noise(k,7)*128,y=53+f*98;
  const len=8+noise(k,3)*16;
  p.line(x,y-len,x-2,y,1,3,160);p.line(x,y-len*.7,x-2,y,3,1);p.rect(x-2,y,2,3,5);
  if(f>.83){const yy=149+(k%3)*3;p.line(x-4,yy,x+4,yy,3);p.star(x,yy,2,4)}
 }
}
function moon(p,t){
 const e=smooth(t/.2)*(1-smooth((t-.8)/.2)),r=31*e,cx=109+66*smooth((t-.2)/.6),cy=72+12*t;
 if(r>.1)for(let y=-Math.ceil(r);y<=r;y++)for(let x=-Math.ceil(r);x<=r;x++){
  const d=(x*x+y*y)/(r*r);if(d>1)continue;
  const z=Math.sqrt(1-d),u=(Math.atan2(x/r,z)/Math.PI+.5+t*.04),v=Math.acos(-y/r)/Math.PI;
  const m=sample('s89011_a',u,v),light=.17+.65*clamp(-x/r*.55-y/r*.35+z*.5);
  const k=d>.94?3:Math.floor(clamp(m*.7+light*.4)*5);p.dot(cx+x,cy+y,k);
 }
 for(let k=0;k<3;k++){const rx=r+5+k*5,ry=r*.35+k*3;for(let j=0;j<42;j++){const a=j/42*Math.PI*2+t*2+k,noisev=sample('s34070_a',j/42,t*.2+k*.2);if(noisev<.16||j%9<2)continue;const x=cx+Math.cos(a)*rx,y=cy+Math.sin(a)*ry+Math.cos(a)*7;p.rect(x,y,2,1,3+k%2,200)}}
 if(t>.72)for(let k=0;k<15;k++){const a=k*2.4,rr=r+(t-.72)*80;p.line(cx+Math.cos(a)*rr,cy+Math.sin(a)*rr,cx+Math.cos(a)*(rr+3),cy+Math.sin(a)*(rr+3),4,2)}
}
function flame(p,t){
 const burst=smooth((t-.62)/.2),e=smooth(t/.2)*(1-smooth((t-.62)/.16)),cx=105+68*clamp(t/.65),cy=86+3*Math.sin(t*9),r=23*e;
 if(r>0)for(let y=-Math.ceil(r+10);y<r+10;y++)for(let x=-Math.ceil(r+10);x<r+10;x++){
  const a=Math.atan2(y,x),d=Math.hypot(x,y)/Math.max(1,r),n=sample('s31041_a',x/70+.5,y/70+.5,Math.floor(t*50)%64),f=sample('s34031_a',x*.027+t*.4,y*.024-t*.3);
  if(d<1.08+Math.sin(a*7+t*13)*.12+f*.2){const curl=Math.sin(a*4-d*11+t*9)*.5+.5;const k=d<.72?Math.floor(clamp(n*1.8+curl*.35)*4):d<1?2+Math.floor(clamp(f*.9+curl*.4)*3):2;p.dot(cx+x,cy+y,k)}
 }
 if(e>.2){p.disc(cx,cy,r*.21,1);p.line(cx-4,cy,cx+4,cy,4,2);p.disc(cx,cy,2,5)}
 if(t>.61){
  p.ring(174,148,18+burst*46,5+burst*12,t,2);
  for(let k=0;k<15;k++){const x=133+noise(k,8)*83,y=143+(k%3)*6,h=(7+noise(k,6)*28)*(1-smooth((t-.8)/.2));
   p.poly([[x-5,y],[x-3,y-h*.7],[x+2,y-h],[x+1,y-h*.4],[x+5,y]],2);p.poly([[x-2,y],[x,y-h*.7],[x+3,y]],4);
  }
  if(t<.82)for(let k=0;k<12;k++){const a=k*.51,rr=burst*47;p.line(174+Math.cos(a)*rr,131+Math.sin(a)*rr*.65,174+Math.cos(a)*(rr+6),131+Math.sin(a)*(rr+6)*.65,3,2)}
 }
}
function elden(p,t){
 const v=clamp(t/.85),head=u=>[74+148*u,101-35*Math.sin(u*Math.PI)+10*u];
 const pts=[];for(let i=0;i<52;i++)pts.push(head(Math.max(0,v-i*.012)));p.trail(pts.reverse(),2);
 for(let k=0;k<3;k++)for(let j=1;j<44;j++){const u=Math.max(0,v-j*.012),[x,y]=head(u),[xx,yy]=head(Math.max(0,u-.009)),off=Math.sin(u*15+t*6+k*2)*5;p.line(xx,yy+off,x,y+off,2+k%2,1,200)}
 const [hx,hy]=head(v);p.star(hx,hy,6,5);p.disc(hx,hy,3,4);
 for(let k=0;k<13;k++){
  const b=.09+k*.04;if(v<=b)continue;const f=clamp((v-b)/.36);if(f>=1)continue;
  const [sx,sy]=head(b),side=k%2?1:-1;
  const trail=[];for(let j=0;j<26;j++){const u=Math.max(0,f-j*.018),x=sx+u*(54+k%3*9),y=sy+side*Math.sin(u*Math.PI*.8)*(23+k%4*9)+u*18;trail.push([x,y])}p.trail(trail.reverse(),1);p.star(...trail.at(-1),2,5);
 }
 for(let k=0;k<26;k++){const u=frac(k*.317+t*.3);if(u>v)continue;const [x,y]=head(u),m=sample('s42071_a',u,t+k*.02,k%64);if(m>.2)p.dot(x,y+Math.sin(k*8)*12,3,200)}
}
function roar(p,t){
 const e=smooth(t/.18)*(1-smooth((t-.65)/.27));
 if(e>.05){
  const dx=(1-e)*-15,shift=pts=>pts.map(([x,y])=>[x+dx,y]);
  p.poly(shift([[61,109],[68,81],[70,59],[93,43],[112,50],[122,67],[145,73],[153,85],[144,97],[114,96],[98,105],[89,123]]),1,230);
  p.poly(shift([[72,63],[60,37],[82,47],[91,51],[92,28],[103,47],[113,37],[114,60],[124,70],[106,77],[88,77]]),2);
  p.poly(shift([[90,82],[115,76],[144,80],[148,85],[116,90],[103,97]]),3);
  const jaw=7+smooth((t-.2)/.25)*17;
  p.poly(shift([[107,95],[141,98],[145,103+jaw*.3],[116,106+jaw],[95,101+jaw*.4]]),2);
  p.poly(shift([[111,94],[144,91],[132,105+jaw*.3],[113,105+jaw*.6]]),0);
  p.line(114+dx,98,132+dx,102+jaw*.3,3,3);
  p.line(109+dx,72,118+dx,70,4,2);p.dot(116+dx,71,5);
  p.poly(shift([[74,61],[87,56],[97,61],[104,71],[92,75],[79,72]]),2);
  p.line(82+dx,57,93+dx,62,3);p.line(93+dx,62,99+dx,70,3);
  for(let row=0;row<6;row++)for(let col=0;col<5;col++){
   const x=75+col*5+(row%2)*2,y=73+row*4;if(x>100&&y>86)continue;
   p.poly(shift([[x,y],[x+3,y-1],[x+4,y+1],[x+1,y+3]]),noise(col,row)> .5?2:1);
   if((row+col)%3===0)p.line(x+dx,y,x+dx+2,y-1,3);
  }
  p.line(104+dx,67,113+dx,64,0,2);p.line(113+dx,64,123+dx,69,0);
  for(let k=0;k<6;k++){const x=114+k*5;p.poly(shift([[x,88],[x+3,89],[x+1,94+(k%2)*2]]),5)}
  for(let k=0;k<32;k++){const x=74+noise(k,2)*36,y=58+noise(k,5)*35;if(sample('s00003_r',x*.02,y*.02)>.35)p.line(x+dx,y,x+dx+3,y-1,k%2?3:0)}
 }
 if(t>.25&&t<.94){const v=clamp((t-.25)/.6);for(let k=0;k<3;k++){const f=frac(v*1.5-k*.22);if(f<.06)continue;const x=125+f*90;p.ring(x,111,8+f*13,12+f*44,t+k,4-k);}}
 if(t>.4)for(let k=0;k<17;k++){const v=frac(t*.8+k*.13),x=115+v*110,y=145+Math.sin(k*5)*10-v*8;p.rect(x,y,2+k%3,2,k%2?1:2)}
}
const previousPainters={comet_azur:azur,stars_of_ruin:ruin,founding_rain:rain,ranni_dark_moon:moon,fell_god_flame:flame,elden_stars:elden,greyoll_roar:roar};
const painters=createBlockPainters({sample,noise,clamp,smooth,frac});
const blockIdeas={comet_azur:'棱面束芯、阶梯束壳、大块流动亮片。',stars_of_ruin:'十二枚晶块星核，厚实阶梯尾迹分批汇聚。',founding_rain:'层叠云块、错拍落下的晶片雨。',ranni_dark_moon:'八向切面冷月、大块陨坑与碎霜板片。',fell_god_flame:'暗红块状火核、矩形焰舌与破碎火床。',elden_stars:'金色主晶核、厚尾迹与分枝晶块。',greyoll_roar:'厚重阶梯颌骨、块状牙齿与断裂声压板。'};
function environment(light=false){
 const p=new Paint({colors:[]});const sky=rgb(light?'#bfcac5':'#222a2b'),ground=rgb(light?'#829281':'#48554b');
 p.rect(0,0,W,H,sky);p.poly([[0,120],[37,111],[74,118],[103,112],[161,125],[218,111],[256,118],[256,192],[0,192]],ground);
 for(let k=0;k<150;k++){const x=noise(k,2)*256,y=123+noise(k,4)*69;p.rect(x,y,3+noise(k,5)*13,2,light?[111,128,111]:[61,74,64])}
 for(let k=0;k<9;k++){const x=k*35-17,y=158+(k%3)*6;p.poly([[x,y],[x+18,y-5],[x+35,y+1],[x+16,y+7]],light?[145,157,137]:[79,91,77]);p.poly([[x,y],[x+16,y+7],[x+16,y+19],[x,y+11]],light?[104,119,101]:[49,62,52]);p.poly([[x+16,y+7],[x+35,y+1],[x+35,y+13],[x+16,y+19]],light?[124,139,119]:[61,76,61])}
 return p.png;
}
function mannequin(p){
 const q=new Paint({colors:[]});q.png=p;
 q.rect(52,102,8,10,[94,114,105]);q.rect(54,104,6,7,[185,166,139]);q.rect(52,101,9,3,[66,76,71]);
 q.rect(51,113,10,14,[100,120,108]);q.rect(56,113,5,14,[65,85,76]);q.line(59,116,68,111,[174,151,119],4);
 q.rect(51,127,4,14,[61,75,72]);q.rect(57,127,4,14,[72,86,81]);q.line(69,116,74,93,[120,98,67],2);q.star(74,93,1,[201,198,160]);
}
function composite(base,over){const p=new PNG({width:base.width,height:base.height});p.data.set(base.data);for(let i=0;i<p.data.length;i+=4){const a=over.data[i+3]/255;if(!a)continue;for(let c=0;c<3;c++)p.data[i+c]=Math.round(over.data[i+c]*a+p.data[i+c]*(1-a));p.data[i+3]=255}return p}
const dark=environment(false),light=environment(true);mannequin(dark);mannequin(light);
await write(path.join(out,'stage-dark.png'),PNG.sync.write(dark));await write(path.join(out,'stage-light.png'),PNG.sync.write(light));
const assets=[];
for(const spec of specs){
 spec.idea=blockIdeas[spec.id];
 const original=spellIndex.find(s=>s.id===spec.magicId);assert(original);assert.equal(original.name_en,spec.en);
 for(const name of spec.textures)assert(original.pngs.some(f=>f.endsWith('/'+name+'.png')),`${spec.id} source association`);
 const atlas=new PNG({width:W*6,height:H*4}),frames=[],counts=[];
 for(let f=0;f<N;f++){
  const p=new Paint(spec);painters[spec.id](p,f/(N-1));
  if(f>=21)for(let i=3;i<p.png.data.length;i+=4){p.png.data[i]=Math.min(p.png.data[i],[160,80,0][f-21]);if(!p.png.data[i])p.png.data.fill(0,i-3,i+1)}
  const b=PNG.sync.write(p.png),file=`frames/${spec.id}/${String(f).padStart(2,'0')}.png`;
  await write(path.join(out,file),b);let n=0;for(let i=3;i<p.png.data.length;i+=4)if(p.png.data[i])n++;counts.push(n);
  for(let y=0;y<H;y++)p.png.data.copy(atlas.data,((Math.floor(f/6)*H+y)*atlas.width+(f%6)*W)*4,y*W*4,(y+1)*W*4);
  frames.push({index:f,file,sha256:sha(b),rect:[f%6*W,Math.floor(f/6)*H,W,H],opaqueOrTranslucentPixels:n});
  if(f===spec.peak){
   const current=composite(dark,p.png),old=new Paint(spec);previousPainters[spec.id](old,f/(N-1));
   const oldBytes=PNG.sync.write(composite(dark,old.png)),saved=await fs.readFile(path.join(previousRoot,`posters/${spec.id}.png`));
   assert.equal(sha(oldBytes),sha(saved),'Old comparison must match actual archived version');
   await write(path.join(out,`previous/${spec.id}.png`),saved);
   const pair=new PNG({width:W*2,height:H});const before=PNG.sync.read(saved);
   for(let y=0;y<H;y++){before.data.copy(pair.data,y*W*8,y*W*4,(y+1)*W*4);current.data.copy(pair.data,y*W*8+W*4,y*W*4,(y+1)*W*4)}
   await write(path.join(out,`comparisons/${spec.id}.png`),PNG.sync.write(pair));
   await write(path.join(out,`posters/${spec.id}.png`),PNG.sync.write(current));await write(path.join(out,`posters/${spec.id}-light.png`),PNG.sync.write(composite(light,p.png)));
  }
 }
 const uniqueFrames=new Set(frames.map(f=>f.sha256)).size;
 assert(uniqueFrames>=N-2,`${spec.id}: unexpected repeated frames (${uniqueFrames})`);
 const atlasFile=`atlases/${spec.id}.png`,b=PNG.sync.write(atlas);await write(path.join(out,atlasFile),b);
 const old=previousManifest.assets.find(a=>a.id===spec.id),oldAtlas=await fs.readFile(path.join(previousRoot,old.atlas));assert.equal(sha(oldAtlas),old.atlasSha256);
 const previousAtlas=`previous/${spec.id}-atlas.png`;await write(path.join(out,previousAtlas),oldAtlas);
 assets.push({...spec,frameSize:[W,H],frameCount:N,atlas:atlasFile,atlasSha256:sha(b),previousAtlas,previousAtlasSha256:sha(oldAtlas),comparison:`comparisons/${spec.id}.png`,poster:`posters/${spec.id}.png`,lightPoster:`posters/${spec.id}-light.png`,anchorPixels:[74,110],sampleStageBlocks:[256/22.22,192/22.22],sampleOnly:true,sourceEffectIds:original.extracted_effect_ids,sourceMappingScope:original.mapping_scope,frames,nonemptyFrames:counts.filter(n=>n>0).length,uniqueFrames});
}
for(const f of protectedFiles)assert.equal(sha(await fs.readFile(path.join(root,f.file))),f.sha256,`Protected change ${f.file}`);
for(const s of sourceRecords)assert.equal(sha(await fs.readFile(path.join(root,s.source))),s.sha256);
const manifest={schemaVersion:2,date:'2026-10-17',revision:'block-forms-v2',previousVersionBackup:path.relative(root,path.resolve(previousRoot,'../..')).replaceAll('\\','/'),constructionGridPixels:4,status:'art_direction_review_only',runtimeIntegrated:false,humanAccepted:false,redistribution:'local_research_only_unlicensed_game_derivatives',sourceRoot:'dist/法环特效素材',sourceIndexSha256:sha(await fs.readFile(path.join(sources,'魔法与祷告索引.json'))),pixelPolicy:'4px construction grid; large planar silhouettes; finite palette; no per-pixel noise, blur or antialiasing',atlasPolicy:'6x4 frames, no padding; offline review atlas only, not production texture-stitch admission',timingPolicy:'24 authored samples across each illustrative cycle; no authority over server durations, hitboxes or FP',assets,sources:sourceRecords,protectedFiles};
await save(path.join(out,'manifest.json'),manifest);
await write(path.join(out,'preview-data.js'),'window.LEGENDARY='+JSON.stringify(manifest)+';\n');
await fs.copyFile(path.join(root,'art/.taste-skill/direction.json'),path.join(out,'taste-direction.json'));
console.log(`PASS seven designs, ${N*7} transparent frame samples (${assets.map(a=>a.uniqueFrames).join('/')} unique by spell), seven atlases, ${sourceRecords.length} source hashes verified, ${protectedFiles.length} existing art files unchanged. No game integration.`);
