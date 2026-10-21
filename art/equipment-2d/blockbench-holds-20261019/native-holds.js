(() => {
 const root='D:/codexout/mcmod/art/equipment-2d/blockbench-holds-20261019/';
 const angles=values=>values.map(v=>v*Math.PI/180);
 function matrix(slot,left){
  const sign=left?-1:1,r=angles(slot.rotation),q=new THREE.Quaternion().setFromEuler(new THREE.Euler(r[0],sign*r[1],sign*r[2],'XYZ'));
  return new THREE.Matrix4().compose(new THREE.Vector3(sign*slot.translation[0],slot.translation[1],slot.translation[2]),q,new THREE.Vector3(...slot.scale));
 }
 function adjust(context,{rotationDelta=0,scaleFactor=1,palmY,palmZ}={}){
  const source=Project.unhandled_root_fields.hold_source,slot=Project.display_settings[context],left=context.endsWith('lefthand'),sign=left?-1:1;
  const grip=new THREE.Vector3(16*source.anchorPixels[0]/source.canvas[0]-8,8-16*source.anchorPixels[1]/source.canvas[1],0);
  const target=grip.clone().applyMatrix4(matrix(slot,left));
  if(palmY!==undefined)target.y=16*palmY;
  if(palmZ!==undefined)target.z=16*palmZ;
  const rotation=slot.rotation.slice(),scale=slot.scale.slice();
  rotation[2]+=rotationDelta;
  scale[0]*=scaleFactor;scale[1]*=scaleFactor;scale[2]=.68;
  const rotated=grip.clone().applyMatrix4(matrix({rotation,scale,translation:[0,0,0]},left));
  const translation=target.clone().sub(rotated).toArray();translation[0]*=sign;
  Undo.initEdit({display_slots:[context]});
  slot.extend({rotation,scale,translation});DisplayMode.load(context);DisplayMode.updateDisplayBase();
  Undo.finishEdit('Calibrate '+source.id+' '+context+' around authored grip');
  const landed=grip.clone().applyMatrix4(matrix(slot,left));
  if(landed.distanceTo(target)>1e-6)throw Error('Grip drift: '+source.id);
  return {id:source.id,context,display:slot.export(),grip:landed.toArray(),undoEntries:Undo.history.length};
 }
 function save(){
  const id=Project.unhandled_root_fields.hold_source.id;
  const path=root+'projects/'+id+'.bbmodel';
  Codecs.project.write(Codecs.project.compile({bitmaps:true,history:true}),path);
  const exported=Codecs.java_block.compile({raw:true});
  Blockbench.writeFile(root+'exports/'+id+'.json',{content:JSON.stringify(exported,null,2)});
  Project.saved=true;
  return {id,path,display:exported.display,undoEntries:Undo.history.length};
 }
 function capture(name){
  const path=root+'qa/'+name+'.png';
  return new Promise((resolve,reject)=>Preview.selected.screenshot({crop:false},data=>{
   const image=new Image();image.onload=()=>{
    const canvas=document.createElement('canvas');canvas.width=image.width;canvas.height=image.height;
    const context=canvas.getContext('2d');context.drawImage(image,0,0);const pixels=context.getImageData(0,0,image.width,image.height).data;
    let foreground=0;for(let i=0;i<pixels.length;i+=4)if(pixels[i+3]&&Math.max(pixels[i],pixels[i+1],pixels[i+2])>45)foreground++;
    if(foreground<8){reject(Error('Empty native capture: '+name));return;}
    window.EldenHoldPixels=window.EldenHoldPixels||{};window.EldenHoldPixels[name]={width:image.width,height:image.height,foreground};
    Blockbench.writeFile(path,{content:data,savetype:'image'});resolve(path);
   };image.onerror=()=>reject(Error('Capture decode failed: '+name));image.src=data;
  }));
 }
 async function load(id,folder='prepared'){
  await new Promise(resolve=>Blockbench.read([root+folder+'/'+id+'.bbmodel'],{readtype:'text'},files=>{
   Codecs.project.load(JSON.parse(files[0].content),{path:files[0].path,no_file:true});
   Project.name='EC_Hold_'+id;resolve();
  }));
  Modes.options.display.select();
  for(let i=0;i<100&&Texture.all.some(t=>!t.img.complete||!t.img.naturalWidth);i++)await new Promise(resolve=>setTimeout(resolve,20));
  if(Texture.all.some(t=>!t.img.complete||!t.img.naturalWidth))throw Error('Texture not loaded: '+id);
  Canvas.updateAll();await new Promise(resolve=>setTimeout(resolve,40));
  return Project;
 }
 const tune={
  claw:{palmY:-.10,rotationDelta:-3},fist:{palmY:-.10,rotationDelta:-3},sacred_seal:{palmY:-.10,rotationDelta:0},
  great_spear:{palmY:-.18,rotationDelta:-5},glintstone_staff:{palmY:-.18,rotationDelta:-4},
  mohgwyns_sacred_spear:{palmY:-.18,rotationDelta:-4},twinblade:{palmY:.04,rotationDelta:0},
  ballista:{palmY:.08,rotationDelta:0},morgotts_cursed_sword:{rotationDelta:-2},
  malikeths_black_blade:{rotationDelta:-5},curved_sword:{rotationDelta:-3},curved_greatsword:{rotationDelta:-3},
  great_shield:{palmY:.08,palmZ:-.25,rotationDelta:0},shield:{palmY:.08,palmZ:-.25,rotationDelta:0},
  small_shield:{palmY:.08,palmZ:-.25,rotationDelta:0},test_flask:{rotationDelta:0}
 };
 async function runBatch(ids){
  const results=[];
  for(const id of ids){
   await load(id);
   const settings=tune[id]||{rotationDelta:-5};
   const right=adjust('firstperson_righthand',settings);
   const left=adjust('firstperson_lefthand',{...settings,rotationDelta:-(settings.rotationDelta||0)});
   const captures=[];
   for(const context of ['firstperson_righthand','firstperson_lefthand','thirdperson_righthand','thirdperson_lefthand']){
    DisplayMode.load(context);captures.push(await capture(id+'_'+context));
   }
   DisplayMode.loadFirstRight();
   const saved=save();results.push({id,right,left,captures,undoEntries:saved.undoEntries});
   const current=Project;current.saved=true;await current.close();
  }
  window.EldenHoldLog=(window.EldenHoldLog||[]).filter(r=>!ids.includes(r.id)).concat(results);
  Blockbench.writeFile(root+'native-calibration-log.json',{content:JSON.stringify(window.EldenHoldLog,null,2)});
  Blockbench.writeFile(root+'native-capture-pixels.json',{content:JSON.stringify(window.EldenHoldPixels||{},null,2)});
  return results.map(r=>({id:r.id,captures:r.captures.length,undoEntries:r.undoEntries}));
 }
 function matchLeftFacing(){
  const source=Project.unhandled_root_fields.hold_source,right=Project.display_settings.firstperson_righthand,left=Project.display_settings.firstperson_lefthand;
  const grip=new THREE.Vector3(16*source.anchorPixels[0]/source.canvas[0]-8,8-16*source.anchorPixels[1]/source.canvas[1],0);
  const palm=grip.clone().applyMatrix4(matrix(right,false));palm.x*=-1;
  const rotation=[right.rotation[0],right.rotation[1],-right.rotation[2]],scale=right.scale.slice();
  const translation=palm.clone().sub(grip.clone().applyMatrix4(matrix({rotation,scale,translation:[0,0,0]},true))).toArray();translation[0]*=-1;
  Undo.initEdit({display_slots:['firstperson_lefthand']});left.extend({rotation,scale,translation});
  DisplayMode.loadFirstLeft();DisplayMode.updateDisplayBase();Undo.finishEdit('Expose '+source.id+' authored face in left hand without mirroring texture');
  return {id:source.id,grip:palm.toArray(),display:left.export()};
 }
 async function leftBatch(ids){
  const results=[];
  for(const id of ids){await load(id,'projects');const changed=matchLeftFacing();await capture(id+'_firstperson_lefthand');const saved=save();results.push({id,left:changed,undoEntries:saved.undoEntries});const current=Project;current.saved=true;await current.close();}
  for(const change of results){const old=(window.EldenHoldLog||[]).find(r=>r.id===change.id);if(old){old.left=change.left;old.undoEntries=change.undoEntries;}}
  Blockbench.writeFile(root+'native-calibration-log.json',{content:JSON.stringify(window.EldenHoldLog||[],null,2)});
  Blockbench.writeFile(root+'native-capture-pixels.json',{content:JSON.stringify(window.EldenHoldPixels||{},null,2)});
  return results.map(r=>({id:r.id,undoEntries:r.undoEntries}));
 }
 window.EldenHold={root,adjust,save,capture,load,runBatch,matchLeftFacing,leftBatch};
 return {ready:true,project:Project.name,source:Project.unhandled_root_fields.hold_source};
})()
