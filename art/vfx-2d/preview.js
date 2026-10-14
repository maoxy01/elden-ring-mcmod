// This fixture follows existing event timing but does not simulate attacks or collision.
const specs=[
  {id:'waterfowl',name:'水鸟乱舞',size:[64,64],anchor:[32,36],world:[6,4.5],roll:[-18,22,-8],windup:10,active:36,recovery:5,starts:[0,12,24]},
  {id:'death_slash',name:'命定之死',size:[64,64],anchor:[32,36],world:[7,5],roll:[-28,20,-12],windup:8,active:24,recovery:5,starts:[0,8,16]},
  {id:'gold_wave',name:'黄金波动',size:[64,32],anchor:[32,8],windup:10,active:4,recovery:5,starts:[]},
];
const root=document.querySelector('#effects');
let playing=true,mode='full',tick=0,last=performance.now(),loaded=false;
const cap=(v,a=0,b=1)=>Math.max(a,Math.min(b,v));
const byId=id=>document.getElementById(id);
const lag=(u,d)=>Math.floor(cap(d,0,4)*.45*u*u/.125)*.125;
function image(src){return new Promise((resolve,reject)=>{const i=new Image();i.onload=()=>resolve(i);i.onerror=()=>reject(new Error(src));i.src=src})}
async function load(){
  for(const spec of specs){
    const section=document.createElement('section');section.dataset.effect=spec.id;
    section.innerHTML=`<div class="title"><h2>${spec.name}</h2><span class="stage"></span></div><canvas class="scene" width="432" height="360" aria-label="${spec.name}动态夹具"></canvas><div class="time"></div><div class="native"><img class="raw" alt="${spec.name}原始像素尺寸"><span>${spec.size[0]}×${spec.size[1]} px · 16 帧</span></div><img class="atlas" alt="${spec.name}图集" src="atlases/${spec.id}.png"><div class="frames">${Array.from({length:16},(_,f)=>`<div><img alt="${spec.name}第${f}帧" src="textures/particle/${spec.id}/frame_${String(f).padStart(2,'0')}.png"><span>${String(f).padStart(2,'0')}</span></div>`).join('')}</div>`;
    root.append(section);spec.section=section;spec.canvas=section.querySelector('canvas');spec.ctx=spec.canvas.getContext('2d');
    spec.images=await Promise.all(Array.from({length:16},(_,f)=>image(`textures/particle/${spec.id}/frame_${String(f).padStart(2,'0')}.png`)));
  }
  loaded=true;render();requestAnimationFrame(animate);
}
function background(ctx,w,h){
  const dark=document.body.classList.contains('dark');ctx.fillStyle=dark?'#303c30':'#b7c4ad';ctx.fillRect(0,0,w,h);
  ctx.strokeStyle=dark?'#394639':'#a8b79e';ctx.lineWidth=1;
  for(let x=0;x<w;x+=24){ctx.beginPath();ctx.moveTo(x,0);ctx.lineTo(x,h);ctx.stroke()}
  for(let y=0;y<h;y+=24){ctx.beginPath();ctx.moveTo(0,y);ctx.lineTo(w,y);ctx.stroke()}
}
function player(ctx,x,floor,scale=1){
  ctx.save();ctx.translate(x,floor);ctx.scale(scale,scale);ctx.fillStyle='#535a55';ctx.fillRect(-8,-28,6,28);ctx.fillRect(2,-28,6,28);ctx.fillStyle='#909991';ctx.fillRect(-10,-52,20,24);ctx.fillRect(-16,-51,6,23);ctx.fillRect(10,-51,6,23);ctx.fillStyle='#bac3b8';ctx.fillRect(-9,-70,18,18);ctx.fillStyle='#616b62';ctx.fillRect(-16,-34,6,6);ctx.fillRect(10,-34,6,6);ctx.fillStyle='#d8e0d3';ctx.fillRect(17,-58,3,33);ctx.fillStyle='#8e774d';ctx.fillRect(14,-27,9,3);ctx.restore();
}
function sample(spec){
  const gold=spec.id==='gold_wave';
  const duration=mode==='clip'?(gold?18:8):spec.windup+(gold?18:spec.active+spec.recovery)+12;
  const t=tick%duration,activeAge=mode==='clip'?t:t-spec.windup;
  let stage=mode==='clip'?'单次特效':t<spec.windup?'起势':t<spec.windup+spec.active?'ACTIVE':t<spec.windup+spec.active+spec.recovery?'后摇':'IDLE';
  if(gold){
    const age=activeAge,progress=cap(Math.max(age*.9/16,age/30));
    return {t,stage,age,progress,visible:age>=0&&age<18,frame:Math.min(15,Math.floor(progress*16)),phase:0,travel:Math.max(0,Math.min(16,age*.9))};
  }
  let phase=0,age=mode==='clip'?t:-1;
  if(mode==='full')for(let i=0;i<3;i++){const a=activeAge-spec.starts[i];if(a>=0&&a<8){phase=i;age=a;break}}
  const progress=cap(age/8);return {t,stage,age,phase,progress,frame:Math.min(15,Math.floor(progress*16)),visible:age>=0&&age<8};
}
function drawSlash(spec,s){
  const {ctx}=spec,w=432,h=360,unit=38;
  background(ctx,w,h);player(ctx,w/2,h*.76);
  if(!s.visible)return;
  const narrow=Math.max(.08,Math.cos(Number(byId('view').value)*Math.PI/180));
  ctx.save();ctx.translate(w/2,h*.76-1.05*unit);ctx.scale(narrow,1);ctx.rotate(spec.roll[s.phase]*Math.PI/180);
  ctx.imageSmoothingEnabled=false;ctx.drawImage(spec.images[s.frame],-spec.anchor[0]/64*spec.world[0]*unit,-spec.anchor[1]/64*spec.world[1]*unit,spec.world[0]*unit,spec.world[1]*unit);ctx.restore();
}
function drawWave(spec,s){
  const {ctx}=spec,w=432,h=360,unit=16,origin=h-44;
  background(ctx,w,h);player(ctx,w/2,origin+16,.46);
  if(!s.visible)return;
  const radius=3+(4.2-3)*cap(s.travel/4),blocked=byId('blocked').checked?((1<<4)|(1<<5)|(1<<6)):0;
  ctx.imageSmoothingEnabled=false;
  for(let lane=0;lane<16;lane++){
    if((blocked>>>lane)&1)continue;
    const u=-1+(lane+.5)*2/16,behind=lag(u,s.travel),x0=(lane/16*2-1)*radius;
    const y=origin-(s.travel-behind)*unit;
    ctx.drawImage(spec.images[s.frame],lane*4,0,4,32,w/2+x0*unit,y-.25*2.8*unit,2*radius/16*unit,2.8*unit);
  }
}
function render(){
  if(!loaded)return;
  for(const spec of specs){
    const s=sample(spec);spec.lastSample=s;spec.id==='gold_wave'?drawWave(spec,s):drawSlash(spec,s);
    spec.section.querySelector('.stage').textContent=s.stage;
    spec.section.querySelector('.time').textContent=spec.id==='gold_wave'?`${s.t.toFixed(1)} tick · 波前 ${s.travel.toFixed(1)} 格`:`${s.t.toFixed(1)} tick · burst ${s.phase+1} · 帧 ${s.visible?String(s.frame).padStart(2,'0'):'—'}`;
    spec.section.querySelector('.raw').src=spec.images[s.frame].src;
  }
}
function animate(now){
  const elapsed=Math.min(.1,(now-last)/1000);last=now;
  if(playing)tick+=elapsed*20*Number(byId('rate').value);
  if(playing)byId('time').value=String(Math.round((tick%63)/63*1000));
  render();requestAnimationFrame(animate);
}
function setPlaying(value){playing=value;byId('play').textContent=value?'Ⅱ':'▶';byId('play').title=value?'暂停':'播放';byId('play').setAttribute('aria-label',byId('play').title)}
byId('play').onclick=()=>setPlaying(!playing);
byId('reset').onclick=()=>{tick=0;render()};
for(const selected of ['full','clip'])byId(selected).onclick=()=>{mode=selected;tick=0;byId('full').setAttribute('aria-pressed',selected==='full');byId('clip').setAttribute('aria-pressed',selected==='clip');render()};
for(const selected of ['light','dark'])byId(selected).onclick=()=>{document.body.classList.toggle('dark',selected==='dark');byId('light').setAttribute('aria-pressed',selected==='light');byId('dark').setAttribute('aria-pressed',selected==='dark');render()};
byId('time').oninput=()=>{setPlaying(false);tick=Number(byId('time').value)/1000*63;render()};
byId('blocked').onchange=render;byId('view').onchange=render;
window.VFX_QA={ready:()=>loaded,setTick:value=>{setPlaying(false);tick=value;render()},setMode:value=>{byId(value).click()},snapshot:()=>specs.map(s=>({id:s.id,...s.lastSample})),waveLags:travel=>Array.from({length:16},(_,lane)=>lag(-1+(lane+.5)*2/16,travel))};
load().catch(error=>{root.innerHTML=`<p class="error">素材未载入：${error.message}</p>`;console.error(error)});
