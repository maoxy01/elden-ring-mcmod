(() => {
 const root='D:/codexout/mcmod/art/vfx-blockbench/takers-flames-20261021/hybrid/',c=Project.unhandled_root_fields.takers_hybrid_art,animation=Animation.all.find(a=>a.uuid===c.animation),view=Preview.all.find(v=>v.id==='mcp_offscreen_takers_eruption'),records=[];
 animation.select();Modes.options.animate.select();
 const positions={oblique:[80,110,220],side:[230,95,32],back:[-80,110,-220]};
 for(const name of ['oblique','side','back'])for(let i=0;i<16;i++){
  Timeline.setTime(0);Animator.preview();Timeline.setTime(i*.1+.001);Animator.preview();
  view.setProjectionMode(true);view.resize(1280,800);view.setLockedAngle();view.camera.position.fromArray(positions[name]);view.controls.target.set(0,19,0);view.camera.zoom=.215;view.camera.updateProjectionMatrix();view.controls.update();
  const file='qa/'+name+'-'+String(i).padStart(2,'0')+'.png';view.screenshot({crop:false,width:1280,height:800},data=>Blockbench.writeFile(root+file,{content:data,savetype:'image'}));records.push({frame:i,time:Timeline.time,view:name,file,position:positions[name],target:[0,19,0],zoom:.215});
 }
 Timeline.setTime(.601);Animator.preview();
 Blockbench.writeFile(root+'captures.json',{content:JSON.stringify({project:Project.uuid,path:Project.save_path,reloaded:true,captures:records},null,2)});
 return {captures:records.length,reloaded:Project.save_path};
})()
