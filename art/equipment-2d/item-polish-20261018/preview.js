const D=window.ITEM_POLISH,$=id=>document.getElementById(id),cache=new Map();
let current=D.audit.find(a=>a.action==='new_candidate'),generation=0;
const own=D.audit.filter(a=>a.action!=='provider_unchanged');
async function load(src){if(!cache.has(src)){const image=new Image();image.src=src;await image.decode();cache.set(src,image)}return cache.get(src)}
function candidate(){return D.candidates.find(a=>a.id===current.id)}
function variants(){return candidate()?.variants||current.states}
function url(v,before=false){return candidate()?(before?v.before:v.url):'../../../'+v.file}
function draw(canvas,image,a,v,before){
 const ctx=canvas.getContext('2d'),css=getComputedStyle(document.body),left=$('left').checked;
 ctx.imageSmoothingEnabled=false;ctx.fillStyle=css.getPropertyValue('--surface');ctx.fillRect(0,0,300,420);
 ctx.fillStyle=css.getPropertyValue('--figure');ctx.fillRect(130,224,40,58);ctx.fillRect(130,282,17,62);ctx.fillRect(153,282,17,62);ctx.fillRect(114,225,16,58);ctx.fillRect(170,225,16,58);
 ctx.fillStyle=css.getPropertyValue('--skin');ctx.fillRect(133,200,34,24);
 if(!a)return;
 const span=before?a.beforeSourceSpanPixels:a.sourceSpanPixels,k=a.worldLengthBlocks*80/span;
 const x=left?122:178,y=268,angle=a.projection.angleDegrees;
 ctx.save();ctx.translate(x,y);ctx.rotate((left?1:-1)*angle*Math.PI/180);ctx.scale(left?-k:k,k);ctx.drawImage(image,-v.anchorPixels[0],-v.anchorPixels[1]);ctx.restore();
 ctx.fillStyle=css.getPropertyValue('--skin');ctx.fillRect(x-3,y-3,6,6);
}
async function render(){
 const token=++generation,a=candidate(),v=variants().find(v=>v.state===$('state').value)||variants()[0];
 document.body.classList.toggle('dark',$('dark').checked);$('name').textContent=current.name;
 $('selection').textContent=a?'新候选，未替换定版':'保留已定版';
 $('facts').textContent=current.worldLengthBlocks.toFixed(3)+' 格 · 原版局部厚度 · '+v.state;
 $('changes').textContent=a?a.features[0]:'保留当前版本的轮廓、配色与选版，不覆盖手绘素材。';
 $('model').hidden=!a;if(a)$('model').href=a.model;
 const nodes=[];
 for(const before of a?[true,false]:[false]){
  const side=document.createElement('div');side.className='side';const title=document.createElement('h3');title.textContent=before?'当前定版':a?'优化候选':'当前定版';
  const visual=document.createElement('div');visual.className='visual';const image=await load(url(v,before));
  const img=image.cloneNode();img.className='zoom';img.alt=current.name+' '+v.state+(before?'当前':'候选');
  const canvas=document.createElement('canvas');canvas.width=300;canvas.height=420;canvas.className='stage';canvas.setAttribute('aria-label','登记比例对照，不是游戏实机');draw(canvas,image,a,v,before);
  visual.append(img,canvas);side.append(title,visual);
  const native=document.createElement('div');native.className='native';
  for(const size of [16,32]){const gui=variants().find(v=>v.state==='gui_'+size);if(gui){const i=document.createElement('img');i.src=url(gui,before);i.width=size;i.height=size;i.alt=current.name+' '+size+' 像素';native.append(i)}}
  side.append(native);nodes.push(side);
 }
 if(token!==generation)return;
 $('comparison').replaceChildren(...nodes);$('comparison').classList.toggle('single',!a);
 for(const b of document.querySelectorAll('.entry'))b.setAttribute('aria-pressed',String(b.dataset.id===current.id));
 window.ITEM_READY=current.id+':'+v.state;
}
function select(id){current=own.find(a=>a.id===id);$('item').value=id;$('state').replaceChildren();for(const v of variants()){$('state').add(new Option(v.state,v.state))}render().catch(e=>{window.ITEM_ERROR=e.message;console.error(e)});history.replaceState(null,'','#'+id)}
function gallery(){
 const filter=$('filter').value;$('collection').replaceChildren();
 for(const a of own){const c=D.candidates.find(c=>c.id===a.id);if(filter==='new'&&!c||filter==='preserved'&&c)continue;
  const b=document.createElement('button');b.className='entry';b.dataset.id=a.id;b.title='查看'+a.name;b.setAttribute('aria-pressed',String(a.id===current.id));
  const img=document.createElement('img');img.src=c?c.variants[0].url:'../../../'+a.states[0].file;img.alt=a.name;
  const title=document.createElement('strong');title.textContent=a.name;const status=document.createElement('span');status.textContent=c?'新候选':'保留定版';b.append(img,title,status);b.onclick=()=>select(a.id);$('collection').append(b);
 }
}
for(const a of own)$('item').add(new Option(a.name,a.id));
for(const a of D.audit.filter(a=>a.action==='provider_unchanged')){const span=document.createElement('span');span.textContent=a.name;$('providers').append(span)}
$('item').onchange=()=>select($('item').value);$('state').onchange=render;$('dark').onchange=render;$('left').onchange=render;$('filter').onchange=gallery;
gallery();select(own.some(a=>a.id===location.hash.slice(1))?location.hash.slice(1):current.id);
