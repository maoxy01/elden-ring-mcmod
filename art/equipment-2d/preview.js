const items=window.EQUIPMENT,byId=id=>document.getElementById(id),root=byId('equipment');
let left=false,ready=false;
const labels={front:'正面',back:'背面',standby:'待机',pulling_0:'拉弓Ⅰ',pulling_1:'拉弓Ⅱ',pulling_2:'满弓',blocking:'格挡'};
function loadImage(url){return new Promise((resolve,reject)=>{const im=new Image();im.onload=()=>resolve(im);im.onerror=()=>reject(Error(url));im.src=url})}
async function load(){
  for(const a of items){
    for(const state of a.variants)state.image=await loadImage(state.url);
    const section=document.createElement('section');section.dataset.id=a.id;
    section.innerHTML=`<div class="title"><h2>${a.name}</h2><span class="tag">${a.delivery==='provider_reference_only'?'原物品引用':'自有精灵样板'}</span></div><canvas class="stage" width="400" height="500" aria-label="${a.name}同比例握持"></canvas><div class="state"><select aria-label="${a.name}状态">${a.variants.map(s=>`<option value="${s.state}">${labels[s.state]}</option>`).join('')}${a.id==='shield'?'<option value="blocking">格挡</option>':''}</select><span>${a.worldLengthBlocks.toFixed(3)} 格 / 人高 ${a.ratioToPlayer.toFixed(2)}×</span></div><div class="pixels"><canvas class="magnified" width="256" height="256" aria-label="${a.name}像素与握点"></canvas><img class="native" alt="${a.name}原生像素"><div class="measure"></div></div><p class="origin">${a.providerId||a.source}</p>`;
    root.append(section);a.section=section;section.querySelector('select').onchange=()=>render();
  }
  ready=true;render();
}
function cross(ctx,x,y){ctx.strokeStyle='#e23d6d';ctx.lineWidth=1;ctx.beginPath();ctx.moveTo(x-7,y);ctx.lineTo(x+7,y);ctx.moveTo(x,y-7);ctx.lineTo(x,y+7);ctx.stroke();ctx.strokeRect(x-3,y-3,6,6)}
function drawItem(ctx,a,state,x,y,angle=0){
  const n=state.size[0],anchor=state.anchorPixels||a.anchorPixels||[n/2,n/2];
  const span=a.sourceSpanPixels||Math.hypot(state.boundsInclusive[2]-state.boundsInclusive[0]+1,state.boundsInclusive[3]-state.boundsInclusive[1]+1);
  const factor=a.worldLengthBlocks*90/span;
  ctx.save();ctx.translate(x,y);ctx.rotate(angle*Math.PI/180);ctx.scale(left?-factor:factor,factor);ctx.imageSmoothingEnabled=false;ctx.drawImage(state.image,-anchor[0],-anchor[1]);ctx.restore();
  if(byId('grip').checked)cross(ctx,x,y);
}
function drawStage(a,state,blocking){
  const ctx=a.section.querySelector('.stage').getContext('2d'),dark=document.body.classList.contains('dark');
  ctx.fillStyle=dark?'#363e44':'#d3dadd';ctx.fillRect(0,0,400,500);
  ctx.strokeStyle=dark?'#444f56':'#c2cdd0';ctx.lineWidth=1;
  for(let y=20;y<500;y+=45){ctx.beginPath();ctx.moveTo(0,y);ctx.lineTo(400,y);ctx.stroke()}
  const cx=200,feet=424,head=feet-162,arm=byId('slim').checked?13.5:18;
  ctx.fillStyle='#616b72';ctx.fillRect(cx-18,feet-63,15,63);ctx.fillRect(cx+3,feet-63,15,63);
  ctx.fillStyle='#969ea1';ctx.fillRect(cx-23,head+36,46,63);ctx.fillRect(cx-23-arm,head+37,arm,58);ctx.fillRect(cx+23,head+37,arm,58);
  ctx.fillStyle='#c0c8c5';ctx.fillRect(cx-18,head,36,36);
  const handX=cx+(left?-1:1)*(23+arm/2),handY=head+88;
  let angle=-90-a.axisImageDegrees;if(a.id==='shield')angle=0;
  if(left)angle=-angle;
  drawItem(ctx,a,state,blocking?cx:handX,handY,angle);
  ctx.fillStyle='#acb7b2';if(a.id!=='shield'||!blocking)ctx.fillRect(handX-5,handY-5,10,10);
  if(byId('grip').checked)cross(ctx,blocking?cx:handX,handY);
  ctx.strokeStyle=dark?'#9eaeb4':'#7b8e97';ctx.beginPath();ctx.moveTo(62,head);ctx.lineTo(62,feet);ctx.moveTo(58,head);ctx.lineTo(66,head);ctx.moveTo(58,feet);ctx.lineTo(66,feet);ctx.stroke();ctx.fillStyle=dark?'#c3d0d6':'#506671';ctx.font='12px system-ui';ctx.fillText('1.8',34,head+80);
}
function render(){
  if(!ready)return;
  for(const a of items){
    const selected=a.section.querySelector('select').value,state=a.variants.find(v=>v.state===selected)||a.variants[0];
    drawStage(a,state,selected==='blocking');
    const ctx=a.section.querySelector('.magnified').getContext('2d');ctx.imageSmoothingEnabled=false;ctx.fillStyle='#b8c4c7';ctx.fillRect(0,0,256,256);
    const factor=256/state.size[0];ctx.drawImage(state.image,0,0,256,256);
    const anchor=state.anchorPixels||a.anchorPixels;if(anchor&&byId('grip').checked)cross(ctx,anchor[0]*factor,anchor[1]*factor);
    a.section.querySelector('.native').src=state.image.src;
    a.section.querySelector('.measure').textContent=`${state.size.join('×')} px · ${state.colors} 色\n边界 [${state.boundsInclusive.join(', ')}]\n握点 ${anchor?anchor.join(', '):'待校准'}`;
  }
}
for(const mode of ['light','dark'])byId(mode).onclick=()=>{document.body.classList.toggle('dark',mode==='dark');byId('light').setAttribute('aria-pressed',mode==='light');byId('dark').setAttribute('aria-pressed',mode==='dark');render()};
for(const hand of ['right','left'])byId(hand).onclick=()=>{left=hand==='left';byId('right').setAttribute('aria-pressed',!left);byId('left').setAttribute('aria-pressed',left);render()};
byId('grip').onchange=render;byId('slim').onchange=render;
window.EQUIPMENT_QA={ready:()=>ready,render,items:()=>items.map(a=>({id:a.id,anchor:a.anchorPixels,states:a.variants.map(v=>({state:v.state,anchor:v.anchorPixels||a.anchorPixels}))}))};
load().catch(e=>{root.textContent='素材加载失败：'+e.message;console.error(e)});
