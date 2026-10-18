(() => {
 const root='D:/codexout/mcmod/art/equipment-2d/blockbench-all-items-20261018/';
 const contract=()=>Project.unhandled_root_fields.art_contract;
 function plan(){
  const plans=[];
  for(const a of contract().items){
   const group=Group.all.find(g=>g.uuid===a.group),origin=group.origin,angle=group.rotation[2]*Math.PI/180;
   function add(state,angleName,key){
    const stateGroup=Group.all.find(g=>g.uuid===state.group),points=[];
    for(const e of stateGroup.children)for(const x of [e.from[0],e.to[0]])for(const y of [e.from[1],e.to[1]]){const xx=x-origin[0],yy=y-origin[1];points.push([origin[0]+xx*Math.cos(angle)-yy*Math.sin(angle),origin[1]+xx*Math.sin(angle)+yy*Math.cos(angle)])}
    const lo=[0,1].map(k=>Math.min(...points.map(p=>p[k]))),hi=[0,1].map(k=>Math.max(...points.map(p=>p[k]))),cx=(lo[0]+hi[0])/2,cy=(lo[1]+hi[1])/2;
    plans.push({id:a.id,state:state.state,key,view:angleName,position:angleName==='oblique'?[cx+100,cy+30,240]:[cx,cy,angleName==='back'?-600:600],target:[cx,cy,0],zoom:18/(Math.max(hi[0]-lo[0],hi[1]-lo[1])*1.32),group:state.group});
   }
   add(a.states[0],'front',a.id+'-front');add(a.states[0],'oblique',a.id+'-oblique');
   for(const state of a.states.slice(1))add(state,state.state==='back'?'back':'front',a.id+'-'+state.state);
  }
  Blockbench.writeFile(root+'native-capture-plans.json',{content:JSON.stringify({width:720,height:720,projection:'orthographic',antialias:false,plans},null,2)});return {plans:plans.length};
 }
 function capture(label){
  let saved;Blockbench.read([root+'native-capture-plans.json'],{readtype:'text'},files=>saved=JSON.parse(files[0].content));
  const view=Preview.all.find(v=>v.id==='mcp_offscreen_all_items_review');if(!view)throw new Error('Native viewport missing');
  const snapshots=[];for(const a of contract().items)for(const s of a.states){const g=Group.all.find(g=>g.uuid===s.group);snapshots.push({group:g,visible:g.mesh.visible})}
  try{
   for(const p of saved.plans){
    const item=contract().items.find(a=>a.id===p.id);
    for(const s of item.states)Group.all.find(g=>g.uuid===s.group).mesh.visible=s.group===p.group;
    view.setProjectionMode(true);view.resize(720,720);view.setLockedAngle();view.camera.position.fromArray(p.position);view.controls.target.fromArray(p.target);view.camera.zoom=p.zoom;view.camera.updateProjectionMatrix();view.controls.update();
    view.screenshot({crop:false,width:720,height:720},data=>Blockbench.writeFile(root+'qa/'+label+'-'+p.key+'.png',{content:data,savetype:'image'}));
    for(const s of item.states)Group.all.find(g=>g.uuid===s.group).mesh.visible=s.group===item.states[0].group;
   }
  }finally{for(const s of snapshots)s.group.mesh.visible=s.visible}
  return {label,captured:saved.plans.length,project:Project.uuid,source:Project.save_path,renderer:'Native on-demand WebGL'};
 }
 window.NativeItemCapture={plan,capture};return {installed:true};
})()
