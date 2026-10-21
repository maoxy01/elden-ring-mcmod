(function(){
 const root='D:/codexout/mcmod/art/equipment-2d/blockbench-holds-20261021/';
 const work=root+'grip-audit/use-poses/';
 const cache=new Map();
 async function read(path){return new Promise(resolve=>Blockbench.read([path],{readtype:'text'},files=>resolve(files[0])));}
 function prefix(job){
  const m=new THREE.Matrix4(),sign=job.context.endsWith('lefthand')?-1:1,rad=Math.PI/180;
  if(job.context.startsWith('thirdperson'))return m;
  const t=(x,y,z)=>m.multiply(new THREE.Matrix4().makeTranslation(x*16,y*16,z*16));
  const r=(axis,d)=>m.multiply(new THREE.Matrix4()['makeRotation'+axis](d*rad));
  const p=job.progress;
  if(job.kind==='bow'&&job.state!=='standby'){
   t(sign*-.2785682,.18344387,.15731531);r('X',-13.935);r('Y',sign*35.3);r('Z',sign*-9.785);
   t(0,0,p*.04);m.multiply(new THREE.Matrix4().makeScale(1,1,1+p*.2));r('Y',sign*-45);
  }else if(job.kind==='crossbow'&&job.state.startsWith('loading')){
   t(sign*-.4785682,-.094387,.05731531);r('X',-11.935);r('Y',sign*65.3);r('Z',sign*-9.785);
   t(0,0,p*.04);m.multiply(new THREE.Matrix4().makeScale(1,1,1+p*.2));r('Y',sign*-45);
  }else if(job.kind==='crossbow'&&job.state==='loaded'){
   t(sign*-.641864,0,0);r('Y',sign*10);
  }
  return m;
 }
 function posePlayer(job){
  if(!job.context.startsWith('thirdperson'))return ()=>{};
  const player=displayReferenceObjects.refmodels.player;
  if(displayReferenceObjects.active!==player||player.variant!=='steve')throw Error('Use the native wide-arm player reference');
  const left=job.context.endsWith('lefthand'),sign=left?-1:1,holding={x:-Math.PI/10,y:0,z:0},other={x:0,y:0,z:0};
  if(job.kind==='shield'&&job.state==='blocking'){holding.x=-.9424779;holding.y=-sign*Math.PI/6;}
  if(job.kind==='bow'&&job.state!=='standby'){holding.x=-Math.PI/2;holding.y=-sign*.1;other.x=-Math.PI/2;other.y=sign*.5;}
  if(job.kind==='crossbow'&&job.state.startsWith('loading')){
   holding.x=-.97079635;holding.y=-sign*.8;other.x=holding.x+( -Math.PI/2-holding.x)*job.progress;other.y=sign*(.4+(.85-.4)*job.progress);
  }
  if(job.kind==='crossbow'&&job.state==='loaded'){holding.x=-Math.PI/2+.1;holding.y=-sign*.3;other.x=-1.5;other.y=sign*.6;}
  const F=new THREE.Matrix4().makeRotationZ(Math.PI);
  const bone=p=>new THREE.Matrix4().makeRotationFromEuler(new THREE.Euler(p.x,p.y,p.z,'ZYX'));
  const snapshots=[];
  for(const mesh of player.model.children.filter(m=>m.visible&&m.name.match(/^(right|left)_arm/))){
   const right=mesh.name.startsWith('right'),s=right?1:-1,p=right!==left?holding:other;
   snapshots.push({mesh,geometry:mesh.geometry,position:mesh.position.clone(),quaternion:mesh.quaternion.clone(),auto:mesh.matrixAutoUpdate});
   mesh.geometry=mesh.geometry.clone();mesh.geometry.translate(-s,0,0);
   mesh.position.set(s*5,22,0);mesh.quaternion.setFromRotationMatrix(F.clone().multiply(bone(p)).multiply(F));
   mesh.matrixAutoUpdate=true;mesh.updateMatrix();
  }
  const area=DisplayMode.display_area,original=area.matrix.clone(),auto=area.matrixAutoUpdate;
  const matrix=new THREE.Matrix4().makeTranslation(sign*5,22,0).multiply(F).multiply(bone(holding))
   .multiply(new THREE.Matrix4().makeRotationX(-Math.PI/2)).multiply(new THREE.Matrix4().makeRotationY(Math.PI))
   .multiply(new THREE.Matrix4().makeTranslation(sign,2,-10));
  area.matrixAutoUpdate=false;area.matrix.copy(matrix);area.matrixWorldNeedsUpdate=true;
  return ()=>{
   for(const s of snapshots){s.mesh.geometry.dispose();s.mesh.geometry=s.geometry;s.mesh.position.copy(s.position);s.mesh.quaternion.copy(s.quaternion);s.mesh.matrixAutoUpdate=s.auto;s.mesh.updateMatrix();}
   area.matrix.copy(original);area.matrixAutoUpdate=auto;area.matrixWorldNeedsUpdate=true;
  };
 }
 async function capture(name){
  return new Promise((resolve,reject)=>Preview.selected.screenshot({crop:false},data=>{
   const img=new Image();img.onload=()=>{
    const canvas=document.createElement('canvas');canvas.width=img.width;canvas.height=img.height;
    const ctx=canvas.getContext('2d');ctx.drawImage(img,0,0);const pixels=ctx.getImageData(0,0,img.width,img.height).data;
    let count=0,x0=img.width,y0=img.height,x1=0,y1=0;
    for(let y=0;y<img.height;y++)for(let x=0;x<img.width;x++){
     const i=(y*img.width+x)*4;if(pixels[i+3]&&Math.max(pixels[i],pixels[i+1],pixels[i+2])>45){count++;x0=Math.min(x0,x);y0=Math.min(y0,y);x1=Math.max(x1,x);y1=Math.max(y1,y);}
    }
    const path=work+'qa/'+name+'.png';Blockbench.writeFile(path,{content:data,savetype:'image'});
    resolve({path,width:img.width,height:img.height,visiblePixels:count,nonempty:count>=12,bounds:count?[x0,y0,x1,y1]:null});
   };img.onerror=()=>reject(Error('Capture decode failed'));img.src=data;
  }));
 }
 async function review(kind){
  const plan=JSON.parse((await read(work+'jobs.json')).content),results=[];
  for(const job of plan.jobs.filter(j=>kind==='all'||j.kind===kind)){
   let project=cache.get(job.path);
   if(project&&ModelProject.all.includes(project)){project.select();}else{
    const file=await read(job.path);Codecs.project.load(JSON.parse(file.content),{path:file.path,no_file:true});project=Project;cache.set(job.path,project);
   }
   for(let i=0;i<100&&Texture.all.some(t=>!t.img.complete||!t.img.naturalWidth);i++)await new Promise(r=>setTimeout(r,20));
   if(Texture.all.some(t=>!t.img.complete||!t.img.naturalWidth))throw Error('Texture not ready: '+job.id);
   Modes.options.display.select();DisplayMode.load(job.context);Canvas.updateAll();
   const base=DisplayMode.display_base;base.updateMatrix();const original=base.matrix.clone(),auto=base.matrixAutoUpdate;
   const settings=JSON.stringify(Project.display_settings[job.context].export());
   const restorePlayer=posePlayer(job);
   try{
    if(job.context.startsWith('thirdperson')){
     const camera=Preview.selected.camera;
     if(job.kind==='shield'){
      const sign=job.context.endsWith('lefthand')?-1:1,side=job.side==='front'?1:-1;
      const world=DisplayMode.display_area.matrix.clone().multiply(original);
      const centre=new THREE.Vector3().applyMatrix4(world),normal=new THREE.Vector3(0,0,1).transformDirection(world);
      const tangent=normal.clone().cross(new THREE.Vector3(0,1,0)).normalize();
      const ray=normal.multiplyScalar(side*.8).add(tangent.multiplyScalar(sign*.6)).add(new THREE.Vector3(0,.3,0)).normalize();
      camera.position.copy(centre.clone().add(ray.multiplyScalar(50)));Preview.selected.controls.target.copy(centre);camera.lookAt(centre);camera.fov=55;
     }else{
      camera.position.set(-56,40,-56);Preview.selected.controls.target.set(0,20,0);camera.lookAt(0,20,0);camera.fov=70;
     }
     camera.updateProjectionMatrix();Preview.selected.controls.update();
    }
    const use=prefix(job);base.matrixAutoUpdate=false;base.matrix.copy(use.clone().multiply(original));base.matrixWorldNeedsUpdate=true;
    await new Promise(r=>setTimeout(r,80));
    if(Project!==project)throw Error('Active project changed during native pose review');
    const shot=await capture(job.id+'_'+job.context+'_'+(job.side||'use'));
    if(JSON.stringify(Project.display_settings[job.context].export())!==settings)throw Error('Preview changed saved display');
    results.push({...job,...shot,prefixMatrix:use.toArray(),savedDisplayUnchanged:true});
   }finally{restorePlayer();base.matrix.copy(original);base.matrixAutoUpdate=auto;base.matrixWorldNeedsUpdate=true;DisplayMode.updateDisplayBase();}
   Project.saved=true;
  }
  const persistedFile=await read(work+'native-review.json'),persisted=persistedFile?JSON.parse(persistedFile.content).items:[];
  window.UsePoseResults=persisted.filter(r=>kind!=='all'&&r.kind!==kind).concat(results);
  Blockbench.writeFile(work+'native-review.json',{content:JSON.stringify({items:window.UsePoseResults,
   vanillaSourceSha256:plan.vanillaSourceSha256,scope:plan.scope,nonemptyIsNotVisualAcceptance:true,
   microShakeOmitted:true,runtimeAccepted:false,gameStarted:false,humanAccepted:false},null,2)});
  return {kind,captures:results.length,empty:results.filter(r=>!r.nonempty).map(r=>r.id+'/'+r.context+'/'+r.side),savedDisplayUnchanged:true};
 }
 window.UsePose={review};return {ready:true};
})()
