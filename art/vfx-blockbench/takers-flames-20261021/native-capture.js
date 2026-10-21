(() => {
 const root='D:/codexout/mcmod/art/vfx-blockbench/takers-flames-20261021/',contract=Project.unhandled_root_fields.takers_flames_art,animation=Animation.all.find(a=>a.uuid===contract.animation),view=Preview.all.find(v=>v.id==='mcp_offscreen_takers_eruption'),reference=Group.all.find(g=>g.uuid===contract.referenceGroup),snapshots=Outliner.elements.map(e=>({mesh:e.mesh,visible:e.mesh.visible})),referenceVisible=reference.mesh.visible,captures=[];
 if(!view||!animation)throw Error('Native viewport or animation missing');animation.select();Modes.options.animate.select();
 function shot(frame,kind){
  Timeline.setTime(frame*.1+.001);Animator.preview();reference.mesh.visible=kind==='comparison';
  for(const e of Outliner.elements)e.mesh.visible=kind==='ground'?e.name.startsWith('ground_rupture_'):true;
  view.setProjectionMode(true);view.resize(1280,800);view.setLockedAngle();
  const target=kind==='comparison'?[-52,18,0]:kind==='ground'?[0,0,0]:[0,19,0];
  const position=kind==='comparison'?[-12,135,260]:kind==='ground'?[0,240,.01]:[65,125,230];
  view.camera.position.fromArray(position);view.controls.target.fromArray(target);view.camera.zoom=kind==='comparison'?.14:.215;view.camera.updateProjectionMatrix();view.controls.update();
  const file='qa/'+kind+'-'+String(frame).padStart(2,'0')+'.png';view.screenshot({crop:false,width:1280,height:800},data=>Blockbench.writeFile(root+file,{content:data,savetype:'image'}));
  captures.push({frame,time:Timeline.time,kind,file,position,target,zoom:view.camera.zoom,source:'Native Blockbench reloaded project, orthographic WebGL'});
 }
 try{for(let i=0;i<16;i++){shot(i,'oblique');shot(i,'comparison');shot(i,'ground');}}
 finally{for(const s of snapshots)s.mesh.visible=s.visible;reference.mesh.visible=referenceVisible;Timeline.setTime(.601);Animator.preview();}
 Blockbench.writeFile(root+'native-captures.json',{content:JSON.stringify({project:Project.uuid,path:Project.save_path,reloaded:true,history:Undo.history.length,captures},null,2)});
 return {captured:captures.length,history:Undo.history.length,reloaded:Project.save_path};
})()
