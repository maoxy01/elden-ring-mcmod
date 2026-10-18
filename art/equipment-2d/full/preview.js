const items=window.EQUIPMENT,$=id=>document.getElementById(id);let selected=items.find(a=>a.id==='sacred_relic_sword'),ready=false;
const labels={front:'正面',back:'背面',standby:'待机',full:'满瓶',blocking:'格挡',loaded:'已装填',pulling_0:'拉弓Ⅰ',pulling_1:'拉弓Ⅱ',pulling_2:'满弓',loading_0:'装填Ⅰ',loading_1:'装填Ⅱ',loading_2:'装填Ⅲ'};
const stateLabel=s=>labels[s]||s.replace(/^.*_pulling_/,'拉弓 ').replace(/^.*_blocking$/,'格挡').replace(/^.*_throwing$/,'投掷').replace(/^crossbow_arrow$/,'箭矢').replace(/^crossbow_firework$/,'烟花');
const loadImage=url=>new Promise((resolve,reject)=>{const im=new Image();im.onload=()=>resolve(im);im.onerror=()=>reject(Error(url));im.src=url});
function cross(ctx,x,y){ctx.strokeStyle='#e84270';ctx.lineWidth=1;ctx.beginPath();ctx.moveTo(x-6,y);ctx.lineTo(x+6,y);ctx.moveTo(x,y-6);ctx.lineTo(x,y+6);ctx.stroke()}
function image(canvas,v,anchor=false){const ctx=canvas.getContext('2d');ctx.clearRect(0,0,canvas.width,canvas.height);ctx.imageSmoothingEnabled=false;ctx.drawImage(v.image,0,0,canvas.width,canvas.height);if(anchor&&$('grip').checked)cross(ctx,v.anchorPixels[0]*canvas.width/v.size[0],v.anchorPixels[1]*canvas.height/v.size[1])}
function slots(el,a){for(const n of [16,32])image(el.querySelector('.s'+n),a.gui.find(v=>v.state===`gui_${n}`)||a.variants[0])}
function stage(canvas,a,v,blocking=false){
  const ctx=canvas.getContext('2d'),dark=document.body.classList.contains('dark'),left=document.querySelector('[name=hand]:checked').value==='left';ctx.imageSmoothingEnabled=false;
  ctx.fillStyle=dark?'#39423d':'#d6dfd9';ctx.fillRect(0,0,400,500);ctx.strokeStyle=dark?'#46544b':'#c3d0c7';for(let y=20;y<500;y+=45){ctx.beginPath();ctx.moveTo(0,y);ctx.lineTo(400,y);ctx.stroke()}
  const cx=200,feet=424,head=262,arm=$('slim').checked?13.5:18,hx=cx+(left?-1:1)*(23+arm/2),hy=head+88;
  ctx.fillStyle='#58685e';ctx.fillRect(cx-18,feet-63,15,63);ctx.fillRect(cx+3,feet-63,15,63);ctx.fillStyle='#96a69c';ctx.fillRect(cx-23,head+36,46,63);ctx.fillRect(cx-23-arm,head+37,arm,58);ctx.fillRect(cx+23,head+37,arm,58);ctx.fillStyle='#c2ccc5';ctx.fillRect(cx-18,head,36,36);
  const anchor=v.anchorPixels,factor=a.worldLengthBlocks*90/a.sourceSpanPixels;let angle=(-90-a.axisImageDegrees)*(left?-1:1);if(a.blockPose)angle=0;
  ctx.save();ctx.translate(blocking?cx:hx,hy);ctx.rotate(angle*Math.PI/180);ctx.scale(left?-factor:factor,factor);ctx.drawImage(v.image,-anchor[0],-anchor[1]);ctx.restore();
  ctx.fillStyle='#adbeb1';if(!blocking)ctx.fillRect(hx-5,hy-5,10,10);if($('grip').checked)cross(ctx,blocking?cx:hx,hy);
}
function select(a){selected=a;$('name').textContent=a.name;$('state').innerHTML=a.variants.map(v=>`<option value="${v.state}">${stateLabel(v.state)}</option>`).join('')+(a.blockPose?'<option value="blocking">格挡</option>':'');render()}
function render(){if(!ready)return;const a=selected,v=a.variants.find(v=>v.state===$('state').value)||a.variants[0];stage(document.querySelector('#detail .stage'),a,v,$('state').value==='blocking');image(document.querySelector('.pixels'),v,true);slots($('detail'),a);
  $('facts').innerHTML=`<p>${a.delivery==='own_sprite'?'自有素材':'原提供方引用'} · ${a.worldLengthBlocks.toFixed(3)} 格 · 人高 ${a.ratioToPlayer.toFixed(2)}×</p><p>${v.size.join('×')} px · ${v.colors} 色<br>握点 ${v.anchorPixels.join(', ')}<br>${a.providerId||a.source}</p><p>比例：${a.ratioEvidence==='provisional_art_estimate'?'美术估值，待校准':a.ratioEvidence}</p><p>${a.artRevision?.status==='user_selected_art_current'?'用户已选 · 当前美术':'美术候选'} · 未实机验收</p>`;
  const cat=$('category').value,scaled=$('scale').checked;
  for(const b of items){b.element.hidden=cat!=='all'&&cat!==b.category;b.element.setAttribute('aria-pressed',b===a);b.element.querySelector('.thumb').hidden=scaled;b.element.querySelector('.stage').hidden=!scaled;if(scaled)stage(b.element.querySelector('.stage'),b,b.variants[0]);else image(b.element.querySelector('.thumb'),b.variants[0],true);slots(b.element,b)}
}
async function load(){for(const a of items){for(const v of [...a.variants,...a.gui])v.image=await loadImage(v.url);const b=document.createElement('button');b.className='item';b.dataset.id=a.id;b.innerHTML=`<canvas class="thumb" width="128" height="128"></canvas><canvas class="stage" width="400" height="500"></canvas><h3>${a.name}</h3><div class="tag">${a.worldLengthBlocks.toFixed(3)} 格 · ${a.delivery==='own_sprite'?'自有':'引用'}</div><div class="slots"><span>16</span><canvas class="s16 slot" width="16" height="16"></canvas><span>32</span><canvas class="s32 slot" width="32" height="32"></canvas></div>`;b.onclick=()=>select(a);$('gallery').append(b);a.element=b}ready=true;select(selected)}
for(const e of document.querySelectorAll('input, #category, #state'))e.onchange=render;
for(const b of document.querySelectorAll('[data-theme]'))b.onclick=()=>{document.body.classList.toggle('dark',b.dataset.theme==='dark');render()};
window.EQUIPMENT_QA={ready:()=>ready,select:id=>select(items.find(a=>a.id===id)),render};
load().catch(e=>{console.error(e);$('name').textContent='素材加载失败：'+e.message});
