(() => {
 const root='D:/codexout/mcmod/art/equipment-2d/mc-polish-20261022/',id=window.itemCaptureSpec?.id||'starscourge_greatsword',folder=root+'revisions/'+(window.itemCaptureSpec?.revision||window.starsCaptureRevision||'stars-sharp')+'/';
 const before=JSON.parse(fs.readFileSync(folder+'before.bbmodel','utf8'));
 const first=before.elements.filter(e=>e.name.startsWith(id+'__front__'));
 const min=[0,1].map(i=>Math.min(...first.map(e=>e.from[i]))),max=[0,1].map(i=>Math.max(...first.map(e=>e.to[i]))),center=min.map((x,i)=>(x+max[i])/2),extent=Math.max(max[0]-min[0],max[1]-min[1]);
 const v=Preview.all.find(v=>v.id==='mcp_offscreen_stars_sharp');
 window.starCapture={status:'running',captures:0,error:null};
 (async()=>{try{for(const phase of ['before','after']){
  if(Project.name===id){Project.saved=true;Project.close();}
  const path=phase==='before'?folder+'before.bbmodel':root+'items/'+id+'.bbmodel';
  Codecs.project.load(JSON.parse(fs.readFileSync(path,'utf8')),{path,name:id+'.bbmodel'});
  await new Promise(r=>setTimeout(r,500));Canvas.updateAll();
  for(const g of Group.all)g.mesh.visible=g.name.includes('|')||g.name==='front';
  for(const c of Cube.all)c.mesh.visible=c.name.startsWith(id+'__front__');
  for(const [name,offset] of [['front',[0,0,600]],['side',[160,60,400]]]){
   v.setProjectionMode(true);v.resize(480,480);v.setLockedAngle();v.camera.position.set(center[0]+offset[0],center[1]+offset[1],offset[2]);v.controls.target.set(center[0],center[1],0);v.camera.zoom=10/extent;v.camera.updateProjectionMatrix();v.controls.update();v.render();
   const data=v.canvas.toDataURL();Blockbench.writeFile(folder+phase+'-'+name+'.png',{content:data,savetype:'image'});
   if(phase==='after')Blockbench.writeFile(root+'qa/'+id+'-after-'+name+'.png',{content:data,savetype:'image'});
   starCapture.captures++;
  }
 }starCapture.status='complete';starCapture.reloaded=true;starCapture.undo=Undo.history.length;
 }catch(e){starCapture.status='failed';starCapture.error=e.stack;}
 Blockbench.writeFile(folder+'captures.json',{content:JSON.stringify(starCapture,null,2)});
 })();return 'Capturing reloaded old/new sword with matching cameras';
})()
