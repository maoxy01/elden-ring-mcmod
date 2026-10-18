(() => {
 const data=window.LEGENDARY,$=id=>document.getElementById(id),canvas=$('stage'),ctx=canvas.getContext('2d');
 const images=new Map(),reduce=matchMedia('(prefers-reduced-motion: reduce)'),state={id:data.assets[0].id,frame:13,playing:!reduce.matches,background:'dark',speed:1,previous:false};
 let elapsed=0,last=0;
 const load=file=>{if(!images.has(file)){const i=new Image();i.src=file;images.set(file,new Promise((resolve,reject)=>{i.onload=()=>resolve(i);i.onerror=()=>reject(new Error('Missing '+file))}))}return images.get(file)};
 const assets=new Map(),stages={};
 const selected=()=>data.assets.find(a=>a.id===state.id);
 function render(){
  const a=selected(),atlas=assets.get(a.id+(state.previous?':previous':''));if(!atlas)return;
  ctx.imageSmoothingEnabled=false;ctx.clearRect(0,0,256,192);
  if(state.background==='clear'){for(let y=0;y<192;y+=8)for(let x=0;x<256;x+=8){ctx.fillStyle=(x/8+y/8)%2?'#68716a':'#9aa29b';ctx.fillRect(x,y,8,8)}}else ctx.drawImage(stages[state.background],0,0);
  const r=a.frames[state.frame].rect;ctx.drawImage(atlas,...r,0,0,256,192);
  $('time').value=state.frame;$('frame').value=`${state.frame+1} / 24`;
 }
 function updateButton(){$('play').textContent=state.playing?'Ⅱ':'▶';$('play').ariaLabel=state.playing?'暂停':'播放';$('play').title=$('play').ariaLabel}
 function select(id,scroll=false){
  state.id=id;state.frame=selected().peak;elapsed=state.frame/23*selected().duration;$('spell').value=id;
  $('name').textContent=selected().name+(state.previous?' · 旧版':'');$('idea').textContent=state.previous?'旧版素材：供同时间、同场景对照。':selected().idea;$('motion').textContent=selected().motion;$('atlas').href=state.previous?selected().previousAtlas:selected().atlas;
  if(location.hash.slice(1)!==id)history.replaceState(null,'','#'+id);render();if(scroll)document.querySelector('.workbench').scrollIntoView({behavior:reduce.matches?'instant':'smooth'});
 }
 for(const a of data.assets){
  const option=document.createElement('option');option.value=a.id;option.textContent=a.name;$('spell').append(option);
  const figure=document.createElement('figure'),button=document.createElement('button'),img=document.createElement('img'),caption=document.createElement('figcaption'),name=document.createElement('strong'),type=document.createElement('span');
  img.src=a.poster;img.alt=a.name+'视觉峰值';img.width=256;img.height=192;button.type='button';button.title='审看'+a.name;button.ariaLabel='审看'+a.name;button.append(img);button.onclick=()=>select(a.id,true);name.textContent=a.name;type.textContent=a.type;caption.append(name,type);figure.append(button,caption);$('gallery').append(figure);
 }
 for(const s of data.sources){const figure=document.createElement('figure'),img=document.createElement('img'),cap=document.createElement('figcaption'),link=document.createElement('a');img.src=s.preview;img.alt=s.name+'提取纹理';img.width=128;img.height=128;link.href='../../'+s.source;link.textContent=s.name;cap.append(link,document.createTextNode(s.size.join(' × ')));figure.append(img,cap);$('references').append(figure)}
 $('spell').onchange=e=>select(e.target.value);
 $('background').onchange=e=>{state.background=e.target.value;render()};
 $('speed').onchange=e=>state.speed=+e.target.value;
 $('previous').onchange=e=>{state.previous=e.target.checked;const oldFrame=state.frame,oldElapsed=elapsed;select(state.id);state.frame=oldFrame;elapsed=oldElapsed;render()};
 $('play').onclick=()=>{state.playing=!state.playing;updateButton()};
 $('restart').onclick=()=>{elapsed=0;state.frame=0;render()};
 $('time').oninput=e=>{state.playing=false;state.frame=+e.target.value;elapsed=state.frame/23*selected().duration;updateButton();render()};
 function loop(time){if(last&&state.playing&&!document.hidden){elapsed+=(Math.min(time-last,80)/1000)*state.speed;const duration=selected().duration;state.frame=Math.min(23,Math.floor(elapsed%duration/duration*24));render()}last=time;requestAnimationFrame(loop)}
 window.ART_REVIEW={state,select,render,ready:false};
 Promise.all([load('stage-dark.png').then(i=>stages.dark=i),load('stage-light.png').then(i=>stages.light=i),...data.assets.flatMap(a=>[load(a.atlas).then(i=>assets.set(a.id,i)),load(a.previousAtlas).then(i=>assets.set(a.id+':previous',i))])]).then(()=>{
  const id=decodeURIComponent(location.hash.slice(1));select(data.assets.some(a=>a.id===id)?id:data.assets[0].id);updateButton();window.ART_REVIEW.ready=true;requestAnimationFrame(loop);
 }).catch(e=>{$('idea').textContent='素材加载失败：'+e.message;console.error(e)});
})();
