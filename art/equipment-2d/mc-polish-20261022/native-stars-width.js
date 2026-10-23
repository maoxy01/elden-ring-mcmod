(() => {
 const root='D:/codexout/mcmod/art/equipment-2d/mc-polish-20261022/';
 const spec=window.bladeWidthSpec||{id:'starscourge_greatsword',revision:'stars-width-5of6',basePixels:64,bladeStart:11,ratio:5/6,metadata:'stars_width'};
 const id=spec.id,folder=root+'revisions/'+spec.revision+'/';
 const retry=spec.retryFromBackup===true&&Project.save_path===folder+'before.bbmodel';
 if(Project.name!==id&&!retry)throw Error('Select the saved '+id+' candidate first');
 if(fs.existsSync(folder+'before.bbmodel')&&!retry)throw Error('Width revision already exists; do not apply twice');
 Project.name=id;
 fs.mkdirSync(folder,{recursive:true});
 if(!fs.existsSync(folder+'before.bbmodel'))fs.copyFileSync(root+'items/'+id+'.bbmodel',folder+'before.bbmodel');
 const contract=Project.unhandled_root_fields.art_contract.items[0],ratio=spec.ratio;
 const report={id:contract.id,date:'2026-10-22',requestedWidthRatio:ratio,basePixels:spec.basePixels,bladeStart:spec.bladeStart,blendLength:spec.blendLength||0,alignment:spec.alignment||'center',method:'Nearest-neighbor transverse resampling along each diagonal blade section; integer pixel widths',states:[]};
 for(const t of Texture.all){
  const n=t.width,state=t.name.split('__')[1].replace('.png',''),s=contract.states.find(s=>s.state===state),anchor=s.anchorPixels,k=n/spec.basePixels;
  const old=Cube.all.filter(c=>c.name.startsWith(id+'__'+state+'__')),group=old[0].parent;
  const sample=old[0],uv=sample.faces.south.uv,unit=(sample.to[0]-sample.from[0])/Math.abs(uv[2]-uv[0]);
  const ox=sample.from[0]-Math.min(uv[0],uv[2])*unit,oy=sample.to[1]+Math.min(uv[1],uv[3])*unit;
  const before=t.canvas.getContext('2d').getImageData(0,0,n,n).data.slice(),sections=[];
  Blockbench.writeFile(folder+t.name,{content:t.canvas.toDataURL(),savetype:'image'});
  Undo.initEdit({textures:[t],bitmap:true,elements:old,outliner:true});
  t.edit(canvas=>{
   const ctx=canvas.getContext('2d'),im=ctx.getImageData(0,0,n,n),d=im.data,bins=new Map();
   for(let y=0;y<n;y++)for(let x=0;x<n;x++)if(before[(y*n+x)*4+3]){
    const v=(x+.5-anchor[0]-y-.5+anchor[1])/Math.SQRT2/k;
    if(v<spec.bladeStart)continue;
    const key=x-y,list=bins.get(key)||[];list.push({x,y,i:(y*n+x)*4});bins.set(key,list);
   }
   for(const [diagonal,list] of bins){
    list.sort((a,b)=>a.x-b.x);
    const count=list.length,v=(diagonal-anchor[0]+anchor[1])/Math.SQRT2/k,blend=spec.blendLength?Math.min(1,Math.max(0,(v-spec.bladeStart)/spec.blendLength)):1;
    const rowRatio=1-(1-ratio)*blend,target=Math.max(1,Math.round(count*rowRatio)),center=(list[0].x+list.at(-1).x)/2,start=spec.alignment==='cutting_edge'?list.at(-1).x-target+1:Math.round(center-(target-1)/2);
    for(const p of list)d.fill(0,p.i,p.i+4);
    for(let j=0;j<target;j++){
     const x=start+j,y=x-diagonal,p=list[target===1?Math.floor(count/2):Math.round(j*(count-1)/(target-1))];
     d.set(before.slice(p.i,p.i+4),(y*n+x)*4);
    }
    sections.push({diagonal,before:count,after:target,rowRatio});
   }
   ctx.putImageData(im,0,0);
  },{no_undo:true});
  const after=t.canvas.getContext('2d').getImageData(0,0,n,n).data,created=[];
  for(const e of old)e.remove();
  for(let y=0;y<n;y++)for(let x=0;x<n;){
   if(!after[(y*n+x)*4+3]){x++;continue;}let w=1;while(x+w<n&&after[(y*n+x+w)*4+3])w++;
   const faces={};for(const f of ['north','south','east','west','up','down'])faces[f]={uv:['north','south'].includes(f)?[x,y,x+w,y+1]:[x,y,x+1,y+1],texture:t.uuid};
   created.push(new Cube({name:id+'__'+state+'__slim_'+created.length,from:[ox+x*unit,oy-(y+1)*unit,-.5],to:[ox+(x+w)*unit,oy-y*unit,.5],box_uv:false,autouv:0,faces}).addTo(group).init());x+=w;
  }
  Undo.finishEdit(id+' blade width '+ratio+': '+state,{textures:[t],elements:created,outliner:true});
  s.group=group.uuid;s.texture=t.uuid;
  const beforeSum=sections.reduce((n,s)=>n+s.before,0),afterSum=sections.reduce((n,s)=>n+s.after,0);
  report.states.push({state,texture:t.name,width:n,beforePixels:beforeSum,afterPixels:afterSum,averageRasterWidthRatio:afterSum/beforeSum,sections});
  Blockbench.writeFile(root+'textures/'+t.name,{content:t.canvas.toDataURL(),savetype:'image'});
 }
 contract.group=Group.all.find(g=>g.name.includes('|')).uuid;
 contract.revisions??=[];contract.revisions.push({name:spec.revision+'-20261022',widthRatio:ratio,rounding:'integer pixels',bladeOnly:true});
 contract.designNote='Blade transverse width reduced to 5/6 with pixel rounding; pointed tip and steel bevel retained';
 Project.unhandled_root_fields[spec.metadata]=report;
 Canvas.updateAll();Codecs.project.write(Codecs.project.compile({bitmaps:true,history:true}),root+'items/'+id+'.bbmodel');
 Blockbench.writeFile(folder+'native-report.json',{content:JSON.stringify(report,null,2)});
 return {id:report.id,requestedWidthRatio:ratio,states:report.states.map(s=>({state:s.state,averageRasterWidthRatio:s.averageRasterWidthRatio})),history:Undo.history.length};
})()
