(() => {
 const root='D:/codexout/mcmod/art/equipment-2d/mc-polish-20261022/';
 const files=fs.readdirSync(root+'items').filter(n=>n.endsWith('.bbmodel'));
 const view=Preview.all.find(v=>v.id==='mcp_offscreen_mc_polish');
 window.mcIsolated={status:'running',captures:0,error:null};
 (async()=>{try{for(const file of files)for(const phase of ['before','after']){
  const folder=phase==='before'?'before':'items',m=JSON.parse(fs.readFileSync(root+folder+'/'+file,'utf8'));
  Codecs.project.load(m,{path:root+folder+'/'+file,name:file});await new Promise(r=>setTimeout(r,300));
  Canvas.updateAll();
  const state=Texture.all[0].name.split('__')[1].replace('.png','');
  for(const g of Group.all)g.mesh.visible=g.name.includes('|')||g.name===state;
  const first=Texture.all[0].uuid,box=new THREE.Box3();
  for(const c of Cube.all){c.mesh.visible=Object.values(c.faces).some(f=>f.texture===first);if(c.mesh.visible)box.expandByObject(c.mesh);}
  const center=box.getCenter(new THREE.Vector3()),size=box.getSize(new THREE.Vector3());
  for(const [name,offset] of [['front',[0,0,600]],['side',[160,60,400]]]){
   view.setProjectionMode(true);view.resize(480,480);view.setLockedAngle();view.camera.position.set(center.x+offset[0],center.y+offset[1],center.z+offset[2]);view.controls.target.copy(center);view.camera.zoom=10/Math.max(size.x,size.y,1);view.camera.updateProjectionMatrix();view.controls.update();view.render();
   Blockbench.writeFile(root+'qa/'+m.name+'-'+phase+'-'+name+'.png',{content:view.canvas.toDataURL(),savetype:'image'});mcIsolated.captures++;
  }
  Project.saved=true;Project.close();
 }mcIsolated.status='complete';}catch(e){mcIsolated.status='failed';mcIsolated.error=e.stack;}
 Blockbench.writeFile(root+'native-isolated-captures.json',{content:JSON.stringify(mcIsolated,null,2)});
 })();return 'Capturing one state at a time';
})()
