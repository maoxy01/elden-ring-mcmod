(function () {
 const root='D:/codexout/mcmod/art/equipment-2d/blockbench-holds-20261021/';
 const contexts=['firstperson_righthand','firstperson_lefthand','thirdperson_righthand','thirdperson_lefthand'];
 async function load(id) {
  const path=root+'projects/'+id+'.bbmodel';
  {
   await new Promise(resolve=>Blockbench.read([path],{readtype:'text'},files=>{
    Codecs.project.load(JSON.parse(files[0].content),{path:files[0].path,no_file:true});resolve();
   }));
  }
  for(let i=0;i<100&&Texture.all.some(t=>!t.img.complete||!t.img.naturalWidth);i++) await new Promise(r=>setTimeout(r,20));
  if(Texture.all.some(t=>!t.img.complete||!t.img.naturalWidth)) throw Error('Texture not ready: '+id);
  if(Format.id!=='java_block') throw Error('Use worn review for arm attachment: '+id);
  Modes.options.display.select(); Canvas.updateAll();
  return {id,cubes:Cube.all.length,source:Project.unhandled_root_fields.hold_source};
 }
 function adjust(context,delta) {
  const slot=Project.display_settings[context];
  const left=context.endsWith('lefthand'), sign=left?-1:1, source=Project.unhandled_root_fields.hold_source;
  const grip=new THREE.Vector3(16*source.anchorPixels[0]/source.canvas[0]-8,8-16*source.anchorPixels[1]/source.canvas[1],0);
  function matrix(value){return new THREE.Matrix4().compose(new THREE.Vector3(sign*value.translation[0],value.translation[1],value.translation[2]),new THREE.Quaternion().setFromEuler(new THREE.Euler(value.rotation[0]*Math.PI/180,sign*value.rotation[1]*Math.PI/180,sign*value.rotation[2]*Math.PI/180,'XYZ')),new THREE.Vector3(...value.scale));}
  const target=grip.clone().applyMatrix4(matrix(slot));
  target.add(new THREE.Vector3(sign*(delta.translation?.[0]||0),delta.translation?.[1]||0,delta.translation?.[2]||0));
  const rotation=slot.rotation.map((v,i)=>v+(delta.rotation?.[i]||0)),scale=slot.scale.map((v,i)=>v*(delta.scale?.[i]||1));
  const translation=target.clone().sub(grip.clone().applyMatrix4(matrix({rotation,scale,translation:[0,0,0]}))).toArray();translation[0]*=sign;
  Undo.initEdit({display_slots:[context]});
  slot.extend({translation,rotation,scale});
  DisplayMode.load(context);DisplayMode.updateDisplayBase();
  Undo.finishEdit('Latest-model native grip calibration: '+context);
  if(grip.clone().applyMatrix4(matrix(slot)).distanceTo(target)>1e-5)throw Error('Native palm drift');
  return slot.export();
 }
 function capture(name) {
  const path=root+'qa/'+name+'.png';
  return new Promise((resolve,reject)=>Preview.selected.screenshot({crop:false},data=>{
   const img=new Image();img.onload=()=>{
    const canvas=document.createElement('canvas');canvas.width=img.width;canvas.height=img.height;
    const ctx=canvas.getContext('2d');ctx.drawImage(img,0,0);const pixels=ctx.getImageData(0,0,img.width,img.height).data;
    let visible=0;for(let i=0;i<pixels.length;i+=4) if(pixels[i+3]&&Math.max(pixels[i],pixels[i+1],pixels[i+2])>45)visible++;
    if(visible<12){reject(Error('Empty native capture: '+name));return;}
    Blockbench.writeFile(path,{content:data,savetype:'image'});
    resolve({path,width:img.width,height:img.height,visible});
   };img.onerror=()=>reject(Error('PNG decode failed'));img.src=data;
  }));
 }
 function save(id) {
  const compiled=Codecs.project.compile({bitmaps:true,history:true});
  Blockbench.writeFile(root+'projects/'+id+'.bbmodel',{content:typeof compiled==='string'?compiled:JSON.stringify(compiled)});
  const exported=Codecs.java_block.compile({raw:true});
  Blockbench.writeFile(root+'exports/'+id+'.json',{content:JSON.stringify(exported,null,2)});
  Project.saved=true;
  return {display:exported.display,undoEntries:Undo.history.length};
 }
 async function review(ids,tune={}) {
  const results=[];
  for(const id of ids){
   const loaded=await load(id);
   for(const [context,delta] of Object.entries(tune[id]||{})) adjust(context,delta);
   const captures=[];
   for(const context of contexts){DisplayMode.load(context);Canvas.updateAll();await new Promise(r=>setTimeout(r,80));captures.push(await capture(id+'_'+context));}
   const saved=save(id);results.push({...loaded,...saved,captures});
  }
  const persisted=await new Promise(resolve=>Blockbench.read([root+'native-review.json'],{readtype:'text'},files=>resolve(files[0]?JSON.parse(files[0].content).items:[])));
  window.LatestHoldResults=persisted.filter(r=>!ids.includes(r.id)).concat(results);
  Blockbench.writeFile(root+'native-review.json',{content:JSON.stringify({items:window.LatestHoldResults,gameStarted:false,humanAccepted:false},null,2)});
  return results.map(r=>({id:r.id,cubes:r.cubes,captures:r.captures.length,undoEntries:r.undoEntries}));
 }
 window.LatestHold={load,adjust,capture,save,review};
 return {ready:true};
})()
