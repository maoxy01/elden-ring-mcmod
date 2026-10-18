const D=window.ALL_ITEMS,$=id=>document.getElementById(id),cache=new Map();let current=D.assets[0],generation=0;
const allStates=()=>[...current.variants,...current.gui];
async function load(src){if(!cache.has(src)){const i=new Image();i.src=src;await i.decode();cache.set(src,i)}return cache.get(src)}
function proportion(canvas,image,v,before){
 const ctx=canvas.getContext('2d'),css=getComputedStyle(document.body),left=$('left').checked;ctx.imageSmoothingEnabled=false;ctx.fillStyle=css.getPropertyValue('--render');ctx.fillRect(0,0,400,400);
 ctx.fillStyle=css.getPropertyValue('--person');ctx.fillRect(180,228,40,58);ctx.fillRect(180,286,17,56);ctx.fillRect(203,286,17,56);ctx.fillRect(164,228,16,58);ctx.fillRect(220,228,16,58);ctx.fillStyle=css.getPropertyValue('--skin');ctx.fillRect(183,198,34,30);
 const x=left?172:228,y=274,span=before?current.beforeSourceSpanPixels:current.sourceSpanPixels,k=current.worldLengthBlocks*80/span,angle=current.projection.angleDegrees;
 ctx.save();ctx.translate(x,y);ctx.rotate((left?1:-1)*angle*Math.PI/180);ctx.scale(left?-k:k,k);ctx.drawImage(image,-v.anchorPixels[0],-v.anchorPixels[1]);ctx.restore();ctx.fillStyle=css.getPropertyValue('--skin');ctx.fillRect(x-3,y-3,6,6);
}
async function render(){
 const token=++generation,a=current,v=allStates().find(v=>v.state===$('state').value)||a.variants[0],view=$('view').value,isPrimary=v===a.variants[0];
 $('view').querySelector('[value=oblique]').disabled=!isPrimary;$('view').querySelector('[value=proportion]').disabled=v.state.startsWith('gui_')||v.state==='back';
 $('left').disabled=view!=='proportion';
 if(view==='oblique'&&!isPrimary||view==='proportion'&&(v.state.startsWith('gui_')||v.state==='back')){$('view').value='native';return render()}
 document.body.classList.toggle('dark',$('dark').checked);$('name').textContent=a.name;$('facts').textContent=a.worldLengthBlocks.toFixed(3)+' 格 · 原版厚度 · '+v.state;$('note').textContent=a.features[0];$('model').href=a.model;
 const nodes=[];
 for(const before of [true,false]){
  const side=document.createElement('div');side.className='side';const h=document.createElement('h3');h.textContent=before?'当前定版':'优化候选';const media=document.createElement('div');media.className='media';
  if(view==='proportion'){
   const canvas=document.createElement('canvas');canvas.width=400;canvas.height=400;canvas.setAttribute('aria-label','与1.8格参考人偶的登记比例对照');proportion(canvas,await load(before?v.before:v.url),v,before);media.append(canvas);
  }else{
   const key=view==='oblique'?a.id+'-oblique':isPrimary?a.id+'-front':a.id+'-'+v.state;
   const src=view==='texture'?(before?v.before:v.url):'qa/'+(before?'before-':'after-')+key+'.png';
   const img=(await load(src)).cloneNode();img.alt=a.name+' '+v.state+' '+(before?'修改前':'优化后');if(view==='texture')img.className='texture';media.append(img);
  }
  const native=document.createElement('div');native.className='native';for(const gui of a.gui){const img=document.createElement('img');img.src=before?gui.before:gui.url;img.width=gui.size[0];img.height=gui.size[1];img.alt=a.name+' '+gui.state;native.append(img)}side.append(h,media,native);nodes.push(side);
 }
 if(token!==generation)return;$('pair').replaceChildren(...nodes);for(const b of document.querySelectorAll('#gallery button'))b.setAttribute('aria-pressed',String(b.dataset.id===a.id));window.REVIEW_READY=a.id+':'+v.state+':'+view;
}
function choose(id){current=D.assets.find(a=>a.id===id);$('item').value=id;$('state').replaceChildren();for(const v of allStates())$('state').add(new Option(v.state,v.state));history.replaceState(null,'','#'+id);render().catch(e=>{window.REVIEW_ERROR=e.message;console.error(e)})}
function gallery(){
 $('gallery').replaceChildren();for(const a of D.assets){if($('batch').value!=='all'&&String(a.batch)!==$('batch').value)continue;const b=document.createElement('button');b.dataset.id=a.id;b.title='查看'+a.name;b.setAttribute('aria-pressed',String(a.id===current.id));const img=document.createElement('img');img.src='qa/after-'+a.id+'-front.png';img.alt=a.name;const title=document.createElement('strong');title.textContent=a.name;const size=document.createElement('span');size.textContent=a.worldLengthBlocks.toFixed(3)+' 格 · '+(a.variants.length+a.gui.length)+' 个状态';b.append(img,title,size);b.onclick=()=>choose(a.id);$('gallery').append(b)}
}
for(const a of D.assets)$('item').add(new Option(a.name,a.id));$('item').onchange=()=>choose($('item').value);$('state').onchange=render;$('view').onchange=render;$('left').onchange=render;$('dark').onchange=render;$('batch').onchange=gallery;gallery();choose(D.assets.some(a=>a.id===location.hash.slice(1))?location.hash.slice(1):'sacred_relic_sword');
