(function () {
 const root='D:/codexout/mcmod/art/equipment-2d/blockbench-holds-20261021/grip-audit/worn-firstperson/';
 const read=path=>new Promise(resolve=>Blockbench.read([path],{readtype:'text'},files=>resolve(files[0])));
 function matrix(job,swing,equip){
  const sign=job.arm==='right'?1:-1,m=new THREE.Matrix4(),f=Math.sqrt(swing),sin=Math.sin,pi=Math.PI;
  const t=(x,y,z)=>m.multiply(new THREE.Matrix4().makeTranslation(x*16,y*16,z*16));
  const r=(axis,degrees)=>m.multiply(new THREE.Matrix4()['makeRotation'+axis](degrees*pi/180));
  // Minecraft 1.21.1 ItemInHandRenderer.renderPlayerArm, in its original order.
  t(sign*(-.3*sin(f*pi)+.64000005),.4*sin(f*2*pi)-.6-equip*.6,-.4*sin(swing*pi)-.71999997);
  r('Y',sign*45);r('Y',sign*sin(f*pi)*70);r('Z',sign*sin(swing*swing*pi)*-20);
  t(sign*-1,3.6,3.5);r('Z',sign*120);r('X',200);r('Y',sign*-135);t(sign*5.6,0,0);
  // renderHand zeroes X rotation; setupAnim(age=0, standing) leaves the Z bob.
  t(-sign*5/16,2/16,0);r('Z',sign*.1*180/pi);
  m.multiply(new THREE.Matrix4().makeScale(1,-1,1));
  m.multiply(new THREE.Matrix4().makeTranslation(...job.authoredPivot.map(v=>-v)));
  return m;
 }
 async function capture(preview,path){
  return new Promise((resolve,reject)=>preview.screenshot({crop:false},data=>{
   const img=new Image();img.onload=()=>{
    Blockbench.writeFile(path,{content:data,savetype:'image'});
    resolve({path,width:img.width,height:img.height});
   };img.onerror=reject;img.src=data;
  }));
 }
 async function review(item){
  const jobs=JSON.parse((await read(root+'jobs.json')).content).jobs.filter(j=>j.item===item),results=[];
  for(const job of jobs){
   const file=await read(job.path);Codecs.project.load(JSON.parse(file.content),{path:file.path,no_file:true});
   Modes.options.edit.select();Canvas.updateAll();
   for(let i=0;i<100&&Texture.all.some(t=>!t.img.complete||!t.img.naturalWidth);i++)await new Promise(r=>setTimeout(r,20));
   if(Texture.all.some(t=>!t.img.complete||!t.img.naturalWidth))throw Error('Texture not ready');
   const model=Project.model_3d,original=model.matrix.clone(),auto=model.matrixAutoUpdate;
   const preview=Preview.all.find(p=>p.id==='mcp_offscreen_worn_fp_review');
   if(!preview||preview.width!==1600||preview.height!==1000)throw Error('Create the native 1600x1000 offscreen view first');
   const camera=preview.camera;
   const originalCamera={position:camera.position.clone(),quaternion:camera.quaternion.clone(),fov:camera.fov,target:preview.controls.target.clone()};
   try{
    for(const [state,swing,equip] of [['rest',0,0],['swing',.35,0],['equipping',0,.5]]){
     const applied=matrix(job,swing,equip);model.matrixAutoUpdate=false;model.matrix.copy(applied);model.matrixWorldNeedsUpdate=true;
     camera.position.set(0,0,0);preview.controls.target.set(0,0,-16);camera.lookAt(0,0,-16);camera.fov=70;camera.updateProjectionMatrix();
     await new Promise(r=>setTimeout(r,100));
     const shot=await capture(preview,root+'qa/'+job.id+'_'+state+'_1600x1000.png');
     if(shot.width!==1600||shot.height!==1000)throw Error('Unexpected viewport crop');
     results.push({...job,state,swing,equip,...shot,matrix:applied.toArray()});
    }
   }finally{
    model.matrix.copy(original);model.matrixAutoUpdate=auto;model.matrixWorldNeedsUpdate=true;
    camera.position.copy(originalCamera.position);camera.quaternion.copy(originalCamera.quaternion);camera.fov=originalCamera.fov;
    preview.controls.target.copy(originalCamera.target);camera.updateProjectionMatrix();Project.saved=true;
   }
  }
  const previous=JSON.parse((await read(root+'native-review-1600x1000.json')).content).items;
  const items=previous.filter(r=>r.item!==item).concat(results);
  Blockbench.writeFile(root+'native-review-1600x1000.json',{content:JSON.stringify({items,reviewOnly:true,skinReference:'Author neutral arm; not live player skin',gameStarted:false,runtimeAccepted:false,humanAccepted:false},null,2)});
  return {item,projects:jobs.length,captures:results.length,sourceAssetsChanged:false};
 }
 window.WornFP={matrix,review};return {ready:true};
})()
