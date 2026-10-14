import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { createAsset } from './player-scene.mjs';
import {installRenderProbe} from './weapon-render-probe.mjs';

const pack=window.WEAPON_PACK,viewport=document.querySelector('#viewport'),labels=document.querySelector('#labels'),select=document.querySelector('#weapon');
const focus=document.querySelector('#focus');
document.querySelector('h1').textContent=pack.title||'法环武器模型';document.title=pack.title||'法环武器模型审查';
if(pack.footer)document.querySelector('footer').textContent=pack.footer;
const collectionLink=document.createElement('a');collectionLink.href=pack.collection==='remembrance'?'../weapons/preview.html':pack.variant==='flat'?'../remembrance-game/preview.html':'../remembrance/preview.html';
collectionLink.textContent=pack.collection==='remembrance'?'主要类型':'追忆武器';collectionLink.style.color='#247369';document.querySelector('#collections').append(collectionLink);
if(pack.variant==='game') {
  document.querySelector('#collections').style.cssText='display:flex;gap:16px;flex-wrap:wrap';
  const sourceLink=document.createElement('a');sourceLink.href='../remembrance/preview.html';sourceLink.textContent='精细源版';
  sourceLink.style.color='#247369';document.querySelector('#collections').append(sourceLink);
}
if(pack.collection==='remembrance')document.querySelector('#mechanical').closest('label').style.display='none';
const renderer=new THREE.WebGLRenderer({antialias:true,preserveDrawingBuffer:true});
renderer.setPixelRatio(Math.min(devicePixelRatio,2));renderer.setClearColor(0xedf1f3);viewport.prepend(renderer.domElement);
const scene=new THREE.Scene(),camera=new THREE.OrthographicCamera(-5,5,5,-5,.01,100);
camera.position.set(0,0,30);const controls=new OrbitControls(camera,renderer.domElement);controls.enablePan=false;controls.enableZoom=true;
scene.add(new THREE.HemisphereLight(0xffffff,0x899b99,pack.variant==='flat'?1.25:2.6));
const key=new THREE.DirectionalLight(0xffffff,pack.variant==='flat'?1.5:2.5);key.position.set(-6,12,18);scene.add(key);
const texture=new THREE.TextureLoader().load(pack.texture,()=>{if(window.WEAPONS_QA)window.WEAPONS_QA.textureLoaded=true;});texture.magFilter=THREE.NearestFilter;texture.minFilter=THREE.NearestFilter;texture.colorSpace=THREE.SRGBColorSpace;
installRenderProbe(pack,texture);
const models=pack.weapons.map(entry=>{
  const asset=createAsset(entry.model,texture),display=new THREE.Group();display.add(asset.root);
  let companion=null;
  if(entry.pairedPreview) {
    companion=createAsset(entry.model,texture);display.add(companion.root);
    asset.root.position.x=-.4;companion.root.position.x=.4;
    asset.root.rotation.z=.07;companion.root.rotation.z=-.07;
    companion.root.rotation.y=Math.PI;
  }
  display.updateMatrixWorld(true);
  const bounds=new THREE.Box3().setFromObject(display,true),size=bounds.getSize(new THREE.Vector3()),center=bounds.getCenter(new THREE.Vector3());
  const holder=new THREE.Group();holder.add(display);display.position.sub(center);
  holder.rotation.set(.07,entry.group==='防具'?Math.PI-.4:-.4,0);scene.add(holder);
  const helper=new THREE.BoxHelper(display,0x287b73);helper.visible=false;scene.add(helper);
  return {entry,asset,companion,holder,helper,size};
});
select.innerHTML='<option value="all">全部武器 / 盾牌</option><option value="detail">精选细节</option>'+pack.weapons.map(w=>`<option value="${w.id}">${w.name}</option>`).join('');
if(pack.collection==='remembrance')select.options[0].textContent='首批追忆武器';
let selected='all',clock=new THREE.Clock(),columns=6;
function layout() {
  const width=viewport.clientWidth,gallery=selected==='all'||selected==='detail';
  focus.style.display=pack.collection==='remembrance'&&!gallery?'':'none';
  labels.style.display=pack.collection==='remembrance'&&!gallery&&focus.value!=='whole'?'none':'grid';
  columns=selected==='detail'?(width<600?2:3):width<600?2:width<1000?4:(pack.galleryColumns||6);
  const featured=pack.featured||['straight_sword','katana','halberd','great_hammer','long_bow','great_shield'];
  const chosen=models.filter(m=>selected==='all'||(selected==='detail'?featured.includes(m.entry.id):m.entry.id===selected));
  const rows=gallery?Math.ceil(chosen.length/columns):1;
  const height=gallery?rows*(selected==='detail'?(width<600?300:420):width<600?235:(pack.rowHeight||270)):Math.max(420,Math.min(720,innerHeight-180));
  viewport.style.height=height+'px';renderer.setSize(width,height);labels.replaceChildren();
  const cellW=selected==='detail'?5:(pack.cellWidth||3.5),viewW=gallery?columns*cellW:4.9*width/height;
  const viewH=viewW*height/width;
  camera.zoom=1;
  camera.left=-viewW/2;camera.right=viewW/2;camera.top=viewH/2;camera.bottom=-viewH/2;camera.updateProjectionMatrix();
  camera.position.set(0,0,30);controls.target.set(0,0,0);controls.update();controls.enabled=!gallery;controls.enablePan=!gallery;
  labels.style.gridTemplateColumns=`repeat(${gallery?columns:1},minmax(0,1fr))`;labels.style.gridTemplateRows=`repeat(${rows},1fr)`;
  for(const m of models){m.holder.visible=false;m.helper.visible=false;}
  chosen.forEach((m,i)=>{
    m.holder.visible=true;const s=gallery?Math.min((selected==='detail'?3.65:(pack.modelSpan||2.7))/Math.max(m.size.x,m.size.y,m.size.z),
      viewH/rows*.72/m.size.y,cellW*.82/Math.max(m.size.x,m.size.z)):
      Math.min(viewW*.72/Math.max(m.size.x,m.size.z),viewH*.72/m.size.y);
    m.holder.scale.setScalar(s);
    const row=Math.floor(i/columns),col=i%columns;
    m.holder.position.set(gallery?(col-(columns-1)/2)*cellW:0,
      gallery?viewH/2-(row+.5)*viewH/rows+viewH/rows*.11:.2,0);
    const label=document.createElement('div');label.className='label';
    const button=document.createElement('button');button.textContent=m.entry.name;button.onclick=()=>{select.value=m.entry.id;selected=m.entry.id;layout();};
    const span=document.createElement('span');span.textContent=`${m.entry.group} · ${pack.variant==='game'?m.entry.model.bakedMesh.triangleCount+' 三角形':m.entry.model.cubes.length+' 块'} · ${(m.size.y).toFixed(2)} 格`;
    label.append(button,span);labels.append(label);
  });
  if(!gallery&&pack.collection==='remembrance'&&focus.value!=='whole') {
    const m=chosen[0],anchors={marikas_hammer:[0,19,0],mohgwyns_sacred_spear:[0,35,0],
      starscourge_greatsword:[1,14,0],morgotts_cursed_sword:[2.4,22,0],hand_of_malenia:[1.2,23,0]};
    const point=focus.value==='grip'?[0,1,0]:anchors[m.entry.id]||[0,21,0];
    m.holder.updateMatrixWorld(true);
    const target=m.asset.root.localToWorld(new THREE.Vector3(...point.map(v=>v/16)));
    controls.target.copy(target);camera.position.copy(target).add(new THREE.Vector3(0,0,30));
    camera.zoom=focus.value==='grip'?3:2.6;camera.updateProjectionMatrix();controls.update();
  }
  const info=document.querySelector('#selected');
  if(gallery)info.textContent='';
  else {
    const entry=chosen[0].entry,art=pack.arts.find(a=>a.id===entry.proposedArt);
    info.textContent=pack.variant==='flat'?entry.name:pack.collection==='remembrance'?`${entry.name} · ${entry.features.join(' / ')} · 战技：${art.name}（未编码）`:
      `${entry.name} / ${entry.id} · 拟配战技：${art.name} · ${art.description}（方案，未编码）`;
  }
  window.WEAPONS_QA={selected,visibleModels:chosen.length,columns,width,height,textureLoaded:!!texture.image};
}
select.onchange=()=>{selected=select.value;focus.value='whole';layout();};focus.onchange=layout;addEventListener('resize',layout);layout();
function render() {
  const tick=clock.getElapsedTime()*20,animated=document.querySelector('#mechanical').checked;
  for(const m of models) {
    if(!m.holder.visible)continue;
    const clip=animated?m.entry.animations[0]:null;
    const t=clip?(clip.loop?tick%clip.lengthTicks:(Math.sin(tick/20)+1)*clip.lengthTicks/2):0;
    m.asset.pose(clip,t,clip?.positionTracks);m.companion?.pose(clip,t,clip?.positionTracks);
    m.helper.visible=document.querySelector('#bounds').checked;m.helper.update();
  }
  controls.update();renderer.render(scene,camera);requestAnimationFrame(render);
}
render();
