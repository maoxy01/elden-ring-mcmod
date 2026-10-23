(() => {
 const root='D:/codexout/mcmod/art/equipment-2d/mc-polish-20261022/';
 const source='D:/codexout/mcmod/art/equipment-2d/blockbench-all-items-20261018/items/';
 const wait=ms=>new Promise(r=>setTimeout(r,ms));
 const steel=['252c2e','48585a','788b8a','a7bab1','d4dfce'];
 const dark=['14191d','293237','455158','738082'];
 const gold=['493a24','786139','ae8c45','dfbd6b','f1d99a'];
 const wood=['302720','594030','88613e','b18b54'];
 const red=['351a21','63252b','9b3d38','cb6850'];
 const teal=['173c47','2b697a','58a3ab','a2d6cf'];
 const bone=['4c4934','807a53','b1a674','dfd0a0'];
 const palettes={
  ballista:[...dark,...wood,...bone],blasphemous_blade:[...red,...gold],
  claw:[...steel,...wood],colossal_sword:[...steel,...gold,...wood],
  colossal_weapon:[...dark,...steel.slice(1,3),...wood,...gold.slice(0,2)],
  curved_greatsword:[...steel,...gold,...wood],curved_sword:[...steel,...gold,...wood],
  fist:[...steel,...wood],flail:[...steel,...wood],
  glintstone_staff:[...teal,...gold,...wood],great_bow:[...wood,...bone],
  great_shield:[...dark,...red,...gold],great_spear:[...steel,...wood,...gold.slice(1,3)],
  great_sword:[...steel,...gold,...wood],hand_of_malenia:[...gold,...bone.slice(0,2)],
  heavy_thrusting_sword:[...steel,...wood],malikeths_black_blade:[...dark,...red.slice(0,2),...gold.slice(0,3)],
  marikas_hammer:[...dark,...bone.slice(0,2),...gold],mohgwyns_sacred_spear:[...gold,...red.slice(0,2),...wood.slice(0,2)],
  morgotts_cursed_sword:['171c26','344551','64838c','90b4b7','403554','68537a','a27a9b','bda3b8','4a5950','929c72',...gold.slice(0,3)],
  sacred_relic_sword:[...bone,...gold.slice(1,4)],sacred_seal:[...bone,...gold,...teal.slice(0,2),...wood.slice(0,2)],
  shield:[...dark,...red.slice(0,3),...gold.slice(0,4),'a8aaa1'],small_shield:[...dark,...wood,...gold,...teal.slice(0,2)],
  starscourge_greatsword:[...dark,...gold],test_flask:[...gold,...red.slice(1,3)],
  twinblade:[...steel,...wood,...gold.slice(0,3)],whip:[...wood,...bone.slice(0,2)]
 };
 const rgb=h=>[0,2,4].map(i=>parseInt(h.slice(i,i+2),16));
 const write=(path,data)=>Blockbench.writeFile(root+path,{content:typeof data==='string'?data:JSON.stringify(data,null,2)});
 const png=(path,canvas)=>Blockbench.writeFile(root+path,{content:canvas.toDataURL(),savetype:'image'});
 const files=fs.readdirSync(source).filter(n=>n.endsWith('.bbmodel')).sort();
 for(const d of ['before','items','textures','before-textures','qa'])fs.mkdirSync(root+d,{recursive:true});
 const baseline=files.map(file=>({file,sha256:crypto.createHash('sha256').update(fs.readFileSync(source+file)).digest('hex')}));
 write('baseline.json',baseline);
 const previous=fs.existsSync(root+'native-report.json')?JSON.parse(fs.readFileSync(root+'native-report.json','utf8')).items:[];
 window.mcPolish={status:'running',done:previous.length,total:files.length,items:previous,error:null};
 const view=Preview.all.find(v=>v.id==='mcp_offscreen_mc_polish');
 async function capture(id,phase){
  const states=Cube.all.map(c=>[c,c.visibility]);
  const first=Texture.all[0];
  for(const c of Cube.all)c.visibility=Object.values(c.faces).some(f=>f.texture===first.uuid);
  Canvas.updateAll();
  const box=new THREE.Box3();for(const c of Cube.all)if(c.visibility)box.expandByObject(c.mesh);
  const center=box.getCenter(new THREE.Vector3()),size=box.getSize(new THREE.Vector3());
  for(const [name,offset] of [['front',[0,0,600]],['side',[160,60,400]]]){
   view.setProjectionMode(true);view.resize(480,480);view.setLockedAngle();
   view.camera.position.set(center.x+offset[0],center.y+offset[1],center.z+offset[2]);
   view.controls.target.copy(center);view.camera.zoom=11/Math.max(size.x,size.y,1);view.camera.updateProjectionMatrix();view.controls.update();
   view.render();
   Blockbench.writeFile(root+'qa/'+id+'-'+phase+'-'+name+'.png',{content:view.canvas.toDataURL('image/png'),savetype:'image'});
  }
  for(const [c,visible] of states)c.visibility=visible;Canvas.updateAll();
 }
 (async()=>{try{
  for(const file of files){
   const raw=fs.readFileSync(source+file,'utf8'),model=JSON.parse(raw),id=model.name;
   if(previous.some(item=>item.id===id))continue;
   if(!fs.existsSync(root+'before/'+file))fs.copyFileSync(source+file,root+'before/'+file);
   Codecs.project.load(model,{path:source+file,name:file});
   await wait(500);
   for(let attempt=0;attempt<100&&!Texture.all.every(t=>t.img.complete&&t.img.naturalWidth);attempt++)await wait(30);
   if(!Texture.all.every(t=>t.img.complete&&t.img.naturalWidth))throw Error(id+' textures not ready');
   if(!Texture.all.every((t,i)=>t.canvas.width===model.textures[i].width))throw Error(id+' canvas dimensions not ready');
   await capture(id,'before');
   const report={id,states:[],thicknessFixed:0,undoBefore:Undo.history.length};
   const palette=palettes[id].map(rgb);
   Undo.initEdit({textures:Texture.all.slice(),bitmap:true,elements:Cube.all.slice()});
   for(const t of Texture.all){
    const old=t.canvas.getContext('2d').getImageData(0,0,t.width,t.height);
    png('before-textures/'+t.name,t.canvas);
    let changed=0,alphaChanged=0;const beforeColors=new Set(),afterColors=new Set();
    t.edit(canvas=>{
     const ctx=canvas.getContext('2d'),im=ctx.getImageData(0,0,t.width,t.height),d=im.data;
     for(let i=0;i<d.length;i+=4){if(!d[i+3])continue;beforeColors.add(Array.from(d.slice(i,i+3)).join(','));
      let best=palette[0],distance=Infinity;
      for(const p of palette){const dr=p[0]-d[i],dg=p[1]-d[i+1],db=p[2]-d[i+2],score=dr*dr*.3+dg*dg*.5+db*db*.2;if(score<distance){best=p;distance=score;}}
      d.set(best,i);
     }
     // The neck is already covered by the source cube's UV rectangle.
     if(id==='test_flask'){
      const scale=t.width/32;
      for(let y=Math.floor(8*scale);y<Math.ceil(12*scale);y++)for(let x=Math.floor(13*scale);x<Math.ceil(19*scale);x++){
       const i=(y*t.width+x)*4;d.set([...rgb(x<15*scale?'dfbd6b':'786139'),255],i);
      }
     }
     for(let i=0;i<d.length;i+=4){if([0,1,2,3].some(k=>d[i+k]!==old.data[i+k]))changed++;if(d[i+3]!==old.data[i+3])alphaChanged++;if(d[i+3])afterColors.add(Array.from(d.slice(i,i+3)).join(','));}
     ctx.putImageData(im,0,0);
    },{no_undo:true});
    png('textures/'+t.name,t.canvas);
    report.states.push({name:t.name,width:t.width,height:t.height,changed,alphaChanged,beforeColors:beforeColors.size,afterColors:afterColors.size});
   }
   for(const c of Cube.all)if(c.to[2]-c.from[2]>1.001){const mid=(c.from[2]+c.to[2])/2;c.from[2]=mid-.5;c.to[2]=mid+.5;report.thicknessFixed++;}
   Undo.finishEdit('Minecraft material planes and one-unit item thickness');Canvas.updateAll();
   await capture(id,'after');
   const contract=Project.unhandled_root_fields.art_contract;
   if(contract?.items){contract.items=contract.items.filter(a=>a.id===id);delete contract.batches;}
   Project.unhandled_root_fields.mc_polish={source:source+file,date:'2026-10-22',status:'candidate',alphaPolicy:id==='test_flask'?'neck repair':'unchanged',report};
   Codecs.project.write(Codecs.project.compile({bitmaps:true,history:true}),root+'items/'+file);
   report.undoAfter=Undo.history.length;mcPolish.items.push(report);mcPolish.done++;write('native-report.json',mcPolish);
   Project.saved=true;Project.close();await wait(10);
  }
  mcPolish.status='complete';write('native-report.json',mcPolish);
 }catch(e){mcPolish.status='failed';mcPolish.error=e.stack;write('native-report.json',mcPolish);}})();
 return {status:'started',count:files.length,root};
})()
