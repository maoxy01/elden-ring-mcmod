// Offline art fixture. Landing/followup timestamps are illustrative, not gameplay events.
const assets=window.VFX_ASSETS,byId=id=>document.getElementById(id),root=byId('effects');
const cap=(v,a=0,b=1)=>Math.max(a,Math.min(b,v));
const lag=(u,d)=>Math.floor(cap(d,0,4)*.45*u*u/.125)*.125;
const skills=[
  {id:'wave_of_gold',name:'黄金波动',clips:['gold_wave'],events:[['gold_wave',10,18]],cycle:44},
  {id:'gold_breaker',name:'黄金碎击',clips:['gold_impact'],events:[['gold_impact',16,12]],cycle:44},
  {id:'destined_death',name:'命定之死',clips:['death_slash'],events:[['death_slash',8,8,0],['death_slash',16,8,1],['death_slash',24,8,2]],cycle:44},
  {id:'takers_flames',name:'掠夺火焰',clips:['flame_front'],events:[['flame_front',9,19]],cycle:44},
  {id:'waterfowl_dance',name:'水鸟乱舞',clips:['waterfowl'],events:[['waterfowl',10,8,0],['waterfowl',22,8,1],['waterfowl',34,8,2]],cycle:56},
  {id:'starcaller_cry',name:'唤星',clips:['gravity_pull','gravity_impact'],events:[['gravity_pull',8,12],['gravity_impact',30,12]],cycle:56},
  {id:'bloodboon_ritual',name:'授血仪式',clips:['blood_ritual'],events:[['blood_ritual',6,16,0],['blood_ritual',24,16,1],['blood_ritual',42,16,2]],cycle:72},
  {id:'cursed_blood_slice',name:'咒血斩击',clips:['cursed_slash','blood_flame'],events:[['cursed_slash',8,8,1],['blood_flame',12,12,1],['cursed_slash',30,8,2],['blood_flame',34,12,2]],cycle:60},
];
const assetMap=new Map(assets.map(a=>[a.id,a]));
let playing=true,mode='full',tick=0,last=performance.now(),loaded=false;
function image(src){return new Promise((resolve,reject)=>{const i=new Image();i.onload=()=>resolve(i);i.onerror=()=>reject(new Error(src));i.src=src})}
async function load(){
  for(const a of assets)a.images=await Promise.all(a.frames.map(f=>image(f.file)));
  for(const skill of skills){
    const section=document.createElement('section');section.dataset.effect=skill.id;
    const options=skill.clips.map(id=>`<option value="${id}">${assetMap.get(id).name}</option>`).join('');
    section.innerHTML=`<div class="title"><h2>${skill.name}</h2><span class="stage"></span></div><canvas class="scene" width="432" height="360" aria-label="${skill.name}"></canvas><div class="time"></div><div class="native"><img class="raw" alt="${skill.name}当前帧"><select class="clip-select" aria-label="${skill.name}素材">${options}</select></div>${skill.clips.map(id=>{const a=assetMap.get(id);return `<details><summary>${a.name} · ${a.frameCount} 帧</summary><img class="atlas" alt="${a.name}图集" src="${a.atlas.file}"><div class="frames">${a.frames.map(f=>`<div><img alt="${a.name}第${f.index}帧" src="${f.file}"><span>${String(f.index).padStart(2,'0')}</span></div>`).join('')}</div></details>`}).join('')}`;
    root.append(section);skill.section=section;skill.ctx=section.querySelector('canvas').getContext('2d');
    section.querySelector('.clip-select').onchange=()=>{mode='clip';updateMode();tick=0;render()};
  }
  loaded=true;render();requestAnimationFrame(animate);
}
function background(ctx){
  const dark=document.body.classList.contains('dark');ctx.fillStyle=dark?'#23292c':'#bdc7b7';ctx.fillRect(0,0,432,360);
  ctx.strokeStyle=dark?'#30383a':'#adbba6';ctx.lineWidth=1;
  for(let x=0;x<432;x+=24){ctx.beginPath();ctx.moveTo(x,0);ctx.lineTo(x,360);ctx.stroke()}
  for(let y=0;y<360;y+=24){ctx.beginPath();ctx.moveTo(0,y);ctx.lineTo(432,y);ctx.stroke()}
}
function player(ctx,x,floor,scale=1){
  ctx.save();ctx.translate(x,floor);ctx.scale(scale,scale);ctx.fillStyle='#50565c';ctx.fillRect(-8,-28,6,28);ctx.fillRect(2,-28,6,28);ctx.fillStyle='#8d9397';ctx.fillRect(-10,-52,20,24);ctx.fillRect(-16,-51,6,23);ctx.fillRect(10,-51,6,23);ctx.fillStyle='#c1c7bf';ctx.fillRect(-9,-70,18,18);ctx.fillStyle='#dde1d3';ctx.fillRect(17,-58,3,33);ctx.fillStyle='#8e774d';ctx.fillRect(14,-27,9,3);ctx.restore();
}
function sample(skill){
  const selected=assetMap.get(skill.section.querySelector('.clip-select').value);
  const clipDuration=selected.laneContract?(selected.id==='gold_wave'?18:19):selected.sourceDefaults.visualEntityTicks;
  const t=tick%(mode==='clip'?clipDuration:skill.cycle);
  const events=mode==='clip'?[[selected.id,0,clipDuration,0]]:skill.events;
  const active=[];
  for(const [id,start,duration,phase=0] of events){
    const age=t-start,a=assetMap.get(id);if(age<0||age>=duration)continue;
    let progress=age/duration,travel=0;
    if(a.laneContract){travel=age*a.sourceDefaults.waveSpeedBlocksPerTick;progress=Math.max(travel/a.sourceDefaults.effectRangeBlocks,age/a.sourceDefaults.effectLifespanUpperBoundTicks)}
    if(progress>=1)continue;
    active.push({id,age,progress,phase,travel,frame:Math.min(15,Math.floor(progress*16))});
  }
  const current=active.at(-1);
  return {t,active,id:current?.id||selected.id,phase:current?.phase??0,frame:current?.frame??0,visible:active.length>0};
}
function sprite(ctx,a,s,width,height){
  ctx.imageSmoothingEnabled=false;
  ctx.drawImage(a.images[s.frame],-a.anchorNormalized[0]*width,-a.anchorNormalized[1]*height,width,height);
}
function debris(ctx,s,pull,unit){
  // The game's ground-state blocks are represented by moving rectangles here.
  for(let i=0;i<10;i++){
    const angle=i*2.399,r=(pull?4-2.8*s.progress:1+2.7*s.progress)*unit;
    const lift=Math.sin(Math.PI*s.progress)*(pull?26:38);
    ctx.fillStyle=i%2?'#666e65':'#929784';ctx.globalAlpha=Math.min(1,(1-s.progress)*4);
    ctx.fillRect(216+Math.cos(angle)*r,252+Math.sin(angle)*r*.42-lift,7,6);
    ctx.fillStyle='#b4b99e';ctx.fillRect(216+Math.cos(angle)*r,252+Math.sin(angle)*r*.42-lift,7,2);
  }
  ctx.globalAlpha=1;
}
function drawGround(ctx,a,s){
  const unit=31,view=Number(byId('view').value),ratio=Math.max(.12,.52*Math.cos(view*Math.PI/180));
  ctx.save();ctx.translate(216,a.id==='blood_ritual'?158:252);ctx.scale(1,ratio);
  const phaseScale=a.phaseVariants?.[s.phase]?.scale||1;
  sprite(ctx,a,s,a.worldSizeBlocks[0]*unit*phaseScale,a.worldSizeBlocks[1]*unit*phaseScale);ctx.restore();
  if(a.debris)debris(ctx,s,a.id==='gravity_pull',unit);
  if(a.id==='blood_ritual'&&s.progress>.2){
    ctx.fillStyle='#cf344b';ctx.globalAlpha=cap((1-s.progress)*2);
    for(let i=0;i<8;i++){const y=160+((s.progress*110+i*19)%88);ctx.fillRect(126+i*26,y,4,7)}ctx.globalAlpha=1;
  }
}
function drawSlash(ctx,a,s){
  const unit=38,narrow=Math.max(.08,Math.cos(Number(byId('view').value)*Math.PI/180));
  ctx.save();ctx.translate(216,a.id==='blood_flame'?280:230);ctx.scale(narrow,1);
  ctx.rotate((a.phaseRollDegrees?.[s.phase]||0)*Math.PI/180);
  sprite(ctx,a,s,a.worldSizeBlocks[0]*unit,a.worldSizeBlocks[1]*unit);ctx.restore();
}
function drawWave(ctx,a,s){
  const gold=a.id==='gold_wave',unit=16,origin=312;
  const radius=a.sourceDefaults.baseRadiusBlocks+(a.sourceDefaults.maxRadiusBlocks-a.sourceDefaults.baseRadiusBlocks)*cap(s.travel/4);
  const blocked=byId('blocked').checked?((1<<4)|(1<<5)|(1<<6)):0;
  ctx.imageSmoothingEnabled=false;
  for(let lane=0;lane<16;lane++){
    if((blocked>>>lane)&1)continue;
    const u=-1+(lane+.5)*2/16,x0=(lane/16*2-1)*radius,y=origin-(s.travel-lag(u,s.travel))*unit;
    const height=(gold?2.8:3.5)*unit;
    ctx.drawImage(a.images[s.frame],lane*4,0,4,a.frameSize[1],216+x0*unit,y-a.anchorNormalized[1]*height,2*radius/16*unit+.15,height);
  }
}
function render(){
  if(!loaded)return;
  for(const skill of skills){
    const s=sample(skill),ctx=skill.ctx;skill.lastSample=s;background(ctx);
    const wave=skill.clips.some(id=>assetMap.get(id).laneContract);
    player(ctx,216,wave?328:280,wave?.46:1);
    for(const item of s.active){const a=assetMap.get(item.id);if(a.laneContract)drawWave(ctx,a,item);else if(a.plane.normal[1])drawGround(ctx,a,item);else drawSlash(ctx,a,item)}
    skill.section.querySelector('.stage').textContent=s.visible?assetMap.get(s.id).name:'待发';
    skill.section.querySelector('.time').textContent=`${s.t.toFixed(1)} tick · ${s.visible?`阶段 ${s.phase+1} · 帧 ${String(s.frame).padStart(2,'0')}`:'—'}`;
    skill.section.querySelector('.raw').src=assetMap.get(s.id).images[s.frame].src;
  }
}
function animate(now){
  const dt=Math.min(.1,(now-last)/1000);last=now;if(playing)tick+=dt*20*Number(byId('rate').value);
  if(playing)byId('time').value=String(Math.round((tick%72)/72*1000));render();requestAnimationFrame(animate);
}
function setPlaying(value){playing=value;byId('play').textContent=value?'Ⅱ':'▶';byId('play').title=value?'暂停':'播放';byId('play').setAttribute('aria-label',byId('play').title)}
function updateMode(){for(const id of ['full','clip'])byId(id).setAttribute('aria-pressed',id===mode)}
byId('play').onclick=()=>setPlaying(!playing);byId('reset').onclick=()=>{tick=0;render()};
for(const id of ['full','clip'])byId(id).onclick=()=>{mode=id;updateMode();tick=0;render()};
for(const id of ['light','dark'])byId(id).onclick=()=>{document.body.classList.toggle('dark',id==='dark');byId('light').setAttribute('aria-pressed',id==='light');byId('dark').setAttribute('aria-pressed',id==='dark');render()};
byId('time').oninput=()=>{setPlaying(false);tick=Number(byId('time').value)/1000*72;render()};
byId('blocked').onchange=render;byId('view').onchange=render;
window.VFX_QA={ready:()=>loaded,setTick:value=>{setPlaying(false);tick=value;render()},setMode:value=>byId(value).click(),snapshot:()=>skills.map(s=>({skill:s.id,...s.lastSample})),waveLags:travel=>Array.from({length:16},(_,lane)=>lag(-1+(lane+.5)*2/16,travel))};
load().catch(error=>{root.innerHTML=`<p class="error">素材未载入：${error.message}</p>`;console.error(error)});
