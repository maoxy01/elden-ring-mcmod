(() => {
 const root='D:/codexout/mcmod/art/equipment-2d/blockbench-all-items-20261018/',contract=Project.unhandled_root_fields.art_contract,ids=['starscourge_greatsword','morgotts_cursed_sword'];
 const items=contract.items.filter(a=>ids.includes(a.id));let baseline;
 Blockbench.read([root+'before-current.bbmodel'],{readtype:'text'},f=>baseline=JSON.parse(f[0].content));
 window.R2Finalize={status:'loading native baseline images',completed:0,checks:[]};
 function components(d,n){const set=new Set();for(let i=3;i<d.length;i+=4)if(d[i])set.add((i-3)/4);let count=0;while(set.size){count++;const q=[set.values().next().value];set.delete(q[0]);while(q.length){const i=q.pop(),x=i%n,y=Math.floor(i/n);for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){const xx=x+dx,yy=y+dy;if(xx>=0&&yy>=0&&xx<n&&yy<n&&set.delete(yy*n+xx))q.push(yy*n+xx);}}}return count;}
 function save(){
  for(const a of items){
   for(const s of a.states){const t=Texture.all.find(t=>t.uuid===s.texture);Blockbench.writeFile('D:/codexout/mcmod/'+s.output,{content:t.canvas.toDataURL('image/png'),savetype:'image'});}
   const group=Group.all.find(g=>g.uuid===a.group),children=[group];group.forEachChild(e=>children.push(e));
   const model=Codecs.project.compile({raw:true,bitmaps:true,collection_only:{getAllChildren:()=>children}}),used=[...new Set(model.elements.flatMap(e=>Object.values(e.faces||{}).map(f=>f.texture)).filter(v=>Number.isInteger(v)))].sort((a,b)=>a-b),map=new Map(used.map((v,i)=>[v,i]));
   model.textures=used.map(i=>model.textures[i]);for(const e of model.elements)for(const f of Object.values(e.faces||{}))if(Number.isInteger(f.texture))f.texture=map.get(f.texture);
   const offset=group.origin;for(const e of model.elements)for(const k of ['from','to','origin'])if(e[k])e[k]=e[k].map((v,i)=>v-offset[i]);for(const g of model.groups)if(g.origin)g.origin=g.origin.map((v,i)=>v-offset[i]);
   model.name=a.id;model.art_contract={...contract,items:[a]};delete model.art_contract.batches;
   Blockbench.writeFile(root+'items/'+a.id+'.bbmodel',{content:compileJSON(model)});
  }
  Codecs.project.write(Codecs.project.compile({bitmaps:true,history:true}),root+'all-items-optimized.bbmodel');
  Blockbench.writeFile(root+'native-edit-report.json',{content:JSON.stringify({project:Project.uuid,items:contract.items,authoring:contract.authoring,undoEntries:Undo.history.length},null,2)});
  window.R2Finalize.status='saved native master, two independent projects and six PNGs';
 }
 for(const a of items)for(const s of a.states){
  const image=new Image();image.onload=()=>{
   try{
    const t=Texture.all.find(t=>t.uuid===s.texture),n=t.width,cv=document.createElement('canvas');cv.width=n;cv.height=n;const cx=cv.getContext('2d');cx.drawImage(image,0,0);const before=cx.getImageData(0,0,n,n).data,after=t.canvas.getContext('2d').getImageData(0,0,n,n).data;
    let rgbaDiff=0,alphaDiff=0;for(let i=0;i<after.length;i+=4){if(after[i+3]!==before[i+3])alphaDiff++;if([0,1,2,3].some(k=>after[i+k]!==before[i+k]))rgbaDiff++;}
    const count=components(after,n);if(count!==1)throw Error(a.id+' '+s.state+' disconnected '+count);
    Object.assign(s.nativeReport,{rgbaDiff,alphaDiff,components:count});window.R2Finalize.checks.push({id:a.id,state:s.state,rgbaDiff,alphaDiff,components:count});window.R2Finalize.completed++;if(window.R2Finalize.completed===6)save();
   }catch(e){window.R2Finalize.status='ERROR '+e.message;}
  };image.onerror=()=>window.R2Finalize.status='ERROR baseline image loading';image.src=baseline.textures.find(t=>t.uuid===s.texture).source;
 }
 return window.R2Finalize;
})()
