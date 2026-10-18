(() => {
 const root='D:/codexout/mcmod/art/equipment-2d/blockbench-all-items-20261018/',ids=['starscourge_greatsword','morgotts_cursed_sword'];let plans;
 Blockbench.read([root+'native-capture-plans.json'],{readtype:'text'},f=>plans=JSON.parse(f[0].content).plans.filter(p=>ids.includes(p.id)));
 const view=Preview.all.find(v=>v.id==='mcp_offscreen_all_items_review'),items=Project.unhandled_root_fields.art_contract.items.filter(a=>ids.includes(a.id)),snapshots=items.flatMap(a=>a.states.map(s=>({g:Group.all.find(g=>g.uuid===s.group),visible:Group.all.find(g=>g.uuid===s.group).mesh.visible})));
 try{for(const p of plans){
  const a=items.find(a=>a.id===p.id);for(const s of a.states)Group.all.find(g=>g.uuid===s.group).mesh.visible=s.group===p.group;
  view.setProjectionMode(true);view.resize(720,720);view.setLockedAngle();view.camera.position.fromArray(p.position);view.controls.target.fromArray(p.target);view.camera.zoom=p.zoom;view.camera.updateProjectionMatrix();view.controls.update();
  view.screenshot({crop:false,width:720,height:720},data=>Blockbench.writeFile(root+'qa/after-'+p.key+'.png',{content:data,savetype:'image'}));
 }}finally{for(const s of snapshots)s.g.mesh.visible=s.visible;}
 return {captured:plans.length,project:Project.uuid,path:Project.save_path,history:Undo.history.length,reloaded:true};
})()
