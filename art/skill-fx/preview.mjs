import * as THREE from 'three';
import {OrbitControls} from 'three/addons/controls/OrbitControls.js';

const entries=[['GOLD_WAVE','黄金波动'],['GOLD_IMPACT','黄金碎击'],['DEATH_SLASH','命定之死'],['FLAME_FRONT','掠夺火焰'],
  ['WATERFOWL','水鸟乱舞'],['GRAVITY_PULL','唤星'],['BLOOD_RITUAL','授血仪式'],['CURSED_SLASH','咒血斩击']];
const names=new Map([...entries,['GRAVITY_IMPACT','唤星 · 重击'],['BLOOD_FLAME','咒血 · 滞留火焰']]);
const pack=new Map(window.SKILL_FX_PACK.map(e=>[e.kind,e.frames]));
const strips=new Map(window.SKILL_FX_PACK.map(e=>[e.kind,e.sprites]));
const explosion=window.SKILL_FX_EXPLOSION.map(url=>{
  const texture=new THREE.TextureLoader().load(url);texture.magFilter=THREE.NearestFilter;
  texture.minFilter=THREE.NearestFilter;texture.colorSpace=THREE.SRGBColorSpace;return texture;
});
const viewport=document.querySelector('#viewport'),labels=document.querySelector('#labels'),select=document.querySelector('#effect');
select.add(new Option('全部战技','all'));for(const [id,name] of names)select.add(new Option(name,id));
const renderer=new THREE.WebGLRenderer({antialias:true,preserveDrawingBuffer:true});renderer.setPixelRatio(Math.min(devicePixelRatio,2));
renderer.setScissorTest(true);renderer.setClearColor(0xe9eef1);viewport.prepend(renderer.domElement);
const scene=new THREE.Scene();scene.background=new THREE.Color(0xe9eef1);
const ground=new THREE.Mesh(new THREE.PlaneGeometry(40,40),new THREE.MeshBasicMaterial({color:0xd3dce0}));
ground.rotation.x=-Math.PI/2;ground.position.y=-.05;scene.add(ground);
const grid=new THREE.GridHelper(40,40,0xb0bec5,0xbcc8ce);scene.add(grid);
const axes=new THREE.Group();
// Neutral block mannequin gives the effects scale; it is not an animation preview.
for(const [x,y,z,w,h,d,color] of [[0,1.7,0,.5,.5,.5,0xb0b8bd],[0,1.05,0,.55,.8,.3,0x547b86],
  [-.19,.35,0,.25,.7,.3,0x53645a],[.19,.35,0,.25,.7,.3,0x53645a],[-.42,1.05,0,.25,.75,.28,0x8f9b9f],[.42,1.05,0,.25,.75,.28,0x8f9b9f]]) {
  const m=new THREE.Mesh(new THREE.BoxGeometry(w,h,d),new THREE.MeshBasicMaterial({color}));m.position.set(x,y,z);axes.add(m);
}scene.add(axes);
const fx=new THREE.Group();scene.add(fx);const cube=new THREE.BoxGeometry(1,1,1),pool=[];
const spritePool=[],plane=new THREE.PlaneGeometry(1,1);
const camera=new THREE.PerspectiveCamera(42,1,.05,100);camera.position.set(8,6,10);camera.lookAt(0,1.2,0);
const controls=new OrbitControls(camera,renderer.domElement);controls.target.set(0,1.2,0);controls.enablePan=false;controls.minDistance=5;controls.maxDistance=28;
const time=document.querySelector('#time'),playing=document.querySelector('#playing'),frame=document.querySelector('#frame');
time.addEventListener('input',()=>{playing.checked=false;});
select.addEventListener('change',()=>{resize();});
function resize(){
  const all=select.value==='all',mobile=innerWidth<=700;
  viewport.style.height=all?(mobile?'1600px':'780px'):'min(82vh,900px)';
  labels.style.display=all?'grid':'none';labels.replaceChildren();
  if(all)for(const [,name]of entries){const el=document.createElement('div');el.className='label';el.textContent=name;labels.append(el);}
  renderer.setSize(viewport.clientWidth,viewport.clientHeight,false);controls.enabled=!all;
  if(!all){
    const wave=select.value==='GOLD_WAVE'||select.value==='FLAME_FRONT';
    const slash=['WATERFOWL','DEATH_SLASH','CURSED_SLASH'].includes(select.value);
    controls.target.set(0,1.2,wave?6:slash?.7:0);camera.position.set(wave?16:slash?4.5:8,wave?13:slash?3.3:6,wave?22:slash?5.5:10);
    camera.lookAt(controls.target);controls.update();
  }
}
function drawBoxes(boxes){
  while(pool.length<boxes.length){const m=new THREE.Mesh(cube,new THREE.MeshBasicMaterial({transparent:true,depthWrite:false,side:THREE.DoubleSide}));pool.push(m);fx.add(m);}
  for(let i=0;i<pool.length;i++){
    const mesh=pool[i],b=boxes[i];mesh.visible=!!b;if(!b)continue;
    mesh.position.set((b[0]+b[3])/2,(b[1]+b[4])/2,(b[2]+b[5])/2);
    mesh.scale.set(b[3]-b[0],b[4]-b[1],b[5]-b[2]);mesh.material.color.setHex(b[6]);mesh.material.opacity=b[7];
  }
}
function drawStrips(items){
  while(spritePool.length<items.length){const m=new THREE.Mesh(plane,new THREE.MeshBasicMaterial({transparent:true,depthWrite:false,side:THREE.DoubleSide}));spritePool.push(m);fx.add(m);}
  for(let i=0;i<spritePool.length;i++){
    const mesh=spritePool[i],s=items[i];mesh.visible=!!s;if(!s)continue;
    mesh.position.set(s[0],s[1],s[2]);mesh.scale.set(s[3],s[4],1);
    mesh.quaternion.copy(camera.quaternion);mesh.rotateZ(s[5]*Math.PI/180);
    mesh.material.color.setHex(s[6]);mesh.material.opacity=s[7];mesh.material.map=explosion[s[8]];
  }
}
let previousSignature='';
function render(ms){
  requestAnimationFrame(render);
  if(playing.checked)time.value=String(Math.floor((ms%1700)/1700*20));
  const index=Number(time.value);frame.value=`${index*5}%`;
  const w=viewport.clientWidth,h=viewport.clientHeight,all=select.value==='all',columns=innerWidth<=700?2:4;
  const views=all?entries:[[select.value,names.get(select.value)]];
  for(let i=0;i<views.length;i++){
    const x=all?(i%columns)*w/columns:0,y=all?h-(Math.floor(i/columns)+1)*h/(8/columns):0;
    const vw=all?w/columns:w,vh=all?h/(8/columns):h;
    renderer.setViewport(x,y,vw,vh);renderer.setScissor(x,y,vw,vh);camera.aspect=vw/vh;
    if(all){camera.position.set(9,7,11).multiplyScalar(Math.max(1,.85/camera.aspect));camera.lookAt(0,1.6,0);}
    camera.updateProjectionMatrix();drawBoxes(pack.get(views[i][0])[index]);drawStrips(strips.get(views[i][0])[index]);
    fx.position.z=!all&&(views[i][0]==='GOLD_WAVE'||views[i][0]==='FLAME_FRONT')?index/20*16:0;
    renderer.render(scene,camera);
  }
  const signature=views.map(([id])=>id).join(',')+':'+index;
  if(signature!==previousSignature){window.FX_PREVIEW={frame:index,effects:views.map(([id])=>({id,boxes:pack.get(id)[index].length,sprites:strips.get(id)[index].length})),canvas:{width:renderer.domElement.width,height:renderer.domElement.height}};previousSignature=signature;}
}
window.addEventListener('resize',resize);resize();requestAnimationFrame(render);
