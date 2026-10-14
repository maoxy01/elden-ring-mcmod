import * as THREE from 'three';
import {createAsset} from './player-scene.mjs';

const pack=window.PROPORTIONS,viewport=document.querySelector('#viewport'),labels=document.querySelector('#labels');
const choice=document.querySelector('#weapon'),mode=document.querySelector('#mode'),skin=document.querySelector('#skin'),angle=document.querySelector('#angle');
const reverse=document.querySelector('#reverse');
const renderer=new THREE.WebGLRenderer({antialias:true,preserveDrawingBuffer:true});
renderer.setPixelRatio(Math.min(devicePixelRatio,2));renderer.setClearColor(0xedf1f3);viewport.prepend(renderer.domElement);
const scene=new THREE.Scene(),camera=new THREE.OrthographicCamera(-1,1,1,-1,.01,100);
camera.position.z=30;scene.add(new THREE.HemisphereLight(0xffffff,0x899b99,1.25));
const key=new THREE.DirectionalLight(0xffffff,1.5);key.position.set(-6,12,18);scene.add(key);
const loading=[];
function texture(url){const t=new THREE.TextureLoader().load(url);t.magFilter=THREE.NearestFilter;t.minFilter=THREE.NearestFilter;t.colorSpace=THREE.SRGBColorSpace;loading.push(t);return t;}
const textures=Object.fromEntries(Object.entries(pack.textures).map(([k,v])=>[k,texture(v)]));
const entries=pack.entries.map(entry=>{
  const asset=createAsset(entry.model,textures[entry.collection]),group=new THREE.Group(),turn=new THREE.Group();
  const players={classic:createAsset(pack.player.classic,null),slim:createAsset(pack.player.slim,null)};
  asset.root.scale.setScalar(entry.visualScale);turn.add(asset.root,players.classic.root,players.slim.root);group.add(turn);scene.add(group);
  const ruler=new THREE.LineSegments(new THREE.BufferGeometry().setFromPoints([
    new THREE.Vector3(-1.4,0,0),new THREE.Vector3(1.4,0,0),
    new THREE.Vector3(-1.4,1.8,0),new THREE.Vector3(1.4,1.8,0)
  ]),new THREE.LineBasicMaterial({color:0xc8d4d7}));group.add(ruler);
  return {entry,asset,players,group,turn};
});
const featured=['dagger','straight_sword','colossal_sword','spear','shield','sacred_relic_sword'];
choice.innerHTML='<option value="remembrance">追忆武器</option><option value="featured">六类对照</option><option value="all">全部 41 件</option>'+entries.map(e=>`<option value="${e.entry.id}">${e.entry.name}</option>`).join('');
if(pack.sizeReview)choice.innerHTML='<option value="all">'+(pack.reviewTitle||'六类尺寸候选')+'</option>'+entries.filter(e=>e.entry.reviewMultiplier===1).map(e=>`<option value="${e.entry.sourceId}">${e.entry.baseName}</option>`).join('');
let active=[],columns=1,rows=1,frame=0;
function layout(){
  const width=viewport.clientWidth;
  active=entries.filter(e=>choice.value==='all'||choice.value==='remembrance'&&e.entry.collection==='remembrance-game'||choice.value==='featured'&&featured.includes(e.entry.id.split(':')[1])||choice.value===(pack.sizeReview?e.entry.sourceId:e.entry.id));
  columns=Math.min(active.length,Math.max(1,Math.floor(width/300)));rows=Math.ceil(active.length/columns);
  if(pack.sizeReview){columns=width>=900?(pack.reviewColumns||3):1;rows=Math.ceil(active.length/columns);}
  const cellWidth=3.4,cellHeight=pack.sizeReview?4.7:4.3,pixelsPerBlock=Math.min(width/(columns*cellWidth),140);
  const worldWidth=width/pixelsPerBlock,worldHeight=rows*cellHeight,height=Math.round(pixelsPerBlock*worldHeight);
  viewport.style.height=height+'px';renderer.setSize(width,height);
  Object.assign(camera,{left:-worldWidth/2,right:worldWidth/2,top:worldHeight/2,bottom:-worldHeight/2});camera.updateProjectionMatrix();
  labels.replaceChildren();labels.style.gridTemplateColumns=`repeat(${columns},minmax(0,1fr))`;labels.style.gridTemplateRows=`repeat(${rows},1fr)`;
  for(const e of entries)e.group.visible=false;
  for(const [i,e] of active.entries()){
    e.group.visible=true;e.group.position.set((i%columns-(columns-1)/2)*cellWidth,worldHeight/2-(Math.floor(i/columns)+1)*cellHeight+.6,0);
    e.turn.rotation.y=Number(angle.value)*Math.PI/180;
    const player=e.players[skin.value];for(const [type,p] of Object.entries(e.players)){p.root.visible=type===skin.value;p.root.position.set(mode.value==='compare'?-.75:0,0,0);p.pose(null,0);}
    e.asset.root.rotation.set(0,(e.entry.viewAngle||0)+(reverse?.checked?Math.PI:0),0);
    if(mode.value==='compare')e.asset.root.position.set(.65,-e.entry.sourceBoundsBlocks.min[1]*e.entry.visualScale,0);
    else {
      // This is an art grip fixture, not the Minecraft display/animation pipeline.
      player.root.updateMatrixWorld(true);
      e.asset.root.position.copy(player.bones.get('hand_r').getWorldPosition(new THREE.Vector3()));
      e.turn.worldToLocal(e.asset.root.position);e.asset.root.position.z+=.15;
      e.asset.root.rotation.z=e.entry.id==='eldencombat:twinblade'?Math.PI/2:.10;
    }
    const label=document.createElement('div');label.className='label';
    const link=document.createElement('a');link.href='#'+e.entry.id;link.textContent=e.entry.name;link.onclick=event=>{event.preventDefault();choice.value=pack.sizeReview?e.entry.sourceId:e.entry.id;layout();};
    const detail=document.createElement('span');detail.textContent=`${e.entry.ratioToPlayer.toFixed(2)} 倍人高 / ${e.entry.targetLengthBlocks.toFixed(2)} 格`;
    label.append(link,detail);labels.append(label);
  }
  document.querySelector('#summary').textContent=`人物基准 1.80 格 · ${active.length} 件 · 比例草案，待实机校准${active.length===1&&active[0].entry.paired?' · 碎星按单把计':''}`;
  if(pack.sizeReview)document.querySelector('#summary').textContent=pack.summary||'人物基准 1.80 格 · 当前 / +15% / +30% · 尺寸候选，未定稿';
  scene.updateMatrixWorld(true);renderer.render(scene,camera);
}
for(const control of [choice,mode,skin,angle,reverse].filter(Boolean))control.addEventListener('input',layout);
addEventListener('resize',layout);layout();
function render(){renderer.render(scene,camera);frame++;requestAnimationFrame(render);}
render();
window.PROPORTIONS_QA=()=>({loaded:loading.every(t=>!!t.image),frame,mode:mode.value,skin:skin.value,columns,rows,
  weapons:active.map(e=>({id:e.entry.id,scale:e.asset.root.scale.x,height:e.entry.sourceHeightBlocks*e.asset.root.scale.x,
    grip:e.asset.root.position.toArray(),bounds:(()=>{const b=new THREE.Box3().setFromObject(e.asset.root,true);return {min:b.min.clone().project(camera).toArray(),max:b.max.clone().project(camera).toArray()};})()})),
  players:active.map(e=>new THREE.Box3().setFromObject(e.players[skin.value].root,true).getSize(new THREE.Vector3()).y)});
