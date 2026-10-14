import * as THREE from 'three';
import {createAsset} from './player-scene.mjs';

export function installRenderProbe(pack,texture) {
  let renderer=null;
  const scene=new THREE.Scene(),cache=new Map(),material=new THREE.MeshBasicMaterial({color:0x000000});
  const ambient=new THREE.HemisphereLight(0xffffff,0x899b99,pack.variant==='flat'?1.25:2.6),light=new THREE.DirectionalLight(0xffffff,pack.variant==='flat'?1.5:2.5);
  light.position.set(-6,12,18);
  const camera=new THREE.OrthographicCamera(-1,1,1,-1,.01,100);camera.position.set(0,0,30);
  window.WEAPONS_CAPTURE=({id,angle=0,frame,baked=true,shaded=false})=> {
    const key=`${id}/${baked}/${shaded}`;
    if(!cache.has(key)) {
      const model={...pack.weapons.find(w=>w.id===id).model};
      if(!baked)delete model.bakedMesh;
      const asset=createAsset(model,texture),old=new Set();
      if(!shaded)asset.root.traverse(n=>{if(n.isMesh){old.add(n.material);n.material=material;}});
      old.forEach(m=>m.dispose());cache.set(key,asset);
    }
    const asset=cache.get(key);scene.clear();asset.root.rotation.y=angle;scene.add(asset.root);
    if(shaded)scene.add(ambient,light);
    if(!renderer) {
      renderer=new THREE.WebGLRenderer({antialias:false,preserveDrawingBuffer:true});
      renderer.setSize(512,768);renderer.setClearColor(0xffffff);
    }
    const [left,right,bottom,top]=frame;
    Object.assign(camera,{left,right,bottom,top});camera.updateProjectionMatrix();renderer.render(scene,camera);
    return renderer.domElement.toDataURL('image/png');
  };
}
