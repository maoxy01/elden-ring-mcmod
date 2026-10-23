(() => {
 const root='D:/codexout/mcmod/art/equipment-2d/mc-polish-20261022/';
 const folder=root+'revisions/stars-sharp/';
 if(!Texture.all[0]?.name.startsWith('starscourge_greatsword__'))throw Error('Wrong active project');
 Project.name='starscourge_greatsword';
 fs.mkdirSync(folder,{recursive:true});
 if(!fs.existsSync(folder+'before.bbmodel'))fs.copyFileSync(root+'items/starscourge_greatsword.bbmodel',folder+'before.bbmodel');
 const contract=Project.unhandled_root_fields.art_contract.items[0];
 const report={date:'2026-10-22',id:contract.id,operation:'Pointed tip and continuous single cutting bevel',states:[],source:root+'items/starscourge_greatsword.bbmodel'};
 const color=h=>[0,2,4].map(i=>parseInt(h.slice(i,i+2),16));
 const span=(d,n,a)=>{let lo=Infinity,hi=-Infinity;for(let y=0;y<n;y++)for(let x=0;x<n;x++)if(d[(y*n+x)*4+3]){const v=(x+.5-a[0]-y-.5+a[1])/Math.SQRT2;lo=Math.min(lo,v);hi=Math.max(hi,v);}return {lo,hi,span:hi-lo+Math.SQRT2};};
 for(const t of Texture.all){
  const n=t.width,k=n/64,state=t.name.split('__')[1].replace('.png',''),s=contract.states.find(s=>s.state===state),anchor=s.anchorPixels;
  if(t.canvas.width!==n)throw Error('Texture not loaded');
  const old=Cube.all.filter(c=>c.name.startsWith('starscourge_greatsword__'+state+'__')),group=old[0].parent;
  const sample=old.find(c=>Math.abs(c.faces.south.uv[2]-c.faces.south.uv[0])>0),uv=sample.faces.south.uv;
  const unit=(sample.to[0]-sample.from[0])/Math.abs(uv[2]-uv[0]),ox=sample.from[0]-Math.min(uv[0],uv[2])*unit,oy=sample.to[1]+Math.min(uv[1],uv[3])*unit;
  const before=t.canvas.getContext('2d').getImageData(0,0,n,n).data.slice(),extent=span(before,n,anchor),vmax=extent.hi/k;
  Blockbench.writeFile(folder+t.name,{content:t.canvas.toDataURL(),savetype:'image'});
  Undo.initEdit({textures:[t],bitmap:true,elements:old,outliner:true});
  let changed=0,alphaChanged=0,goldKept=0;
  t.edit(canvas=>{
   const ctx=canvas.getContext('2d'),im=ctx.getImageData(0,0,n,n),d=im.data;
   const coord=(x,y)=>({u:(x+.5-anchor[0]+y+.5-anchor[1])/Math.SQRT2/k,v:(x+.5-anchor[0]-y-.5+anchor[1])/Math.SQRT2/k});
   const tipStart=vmax-15,tipPixels=[];
   for(let y=0;y<n;y++)for(let x=0;x<n;x++){
    const i=(y*n+x)*4;if(!d[i+3])continue;const {u,v}=coord(x,y);
    if(v>=vmax-.1)tipPixels.push({i,u});
    if(v>tipStart){
     const remaining=(vmax-v)/15;
     const left=-10.4*remaining-.36/k,right=5.8*remaining+.36/k;
     if(u<left||u>right)d.fill(0,i,i+4);
    }
   }
   // Preserve the axial endpoint with one supported pixel, keeping world length.
   tipPixels.sort((a,b)=>Math.abs(a.u)-Math.abs(b.u));
   if(tipPixels[0])d.set(before.slice(tipPixels[0].i,tipPixels[0].i+4),tipPixels[0].i);
   const bins=new Map();
   for(let y=0;y<n;y++)for(let x=0;x<n;x++)if(d[(y*n+x)*4+3]){
    const key=x-y,{u,v}=coord(x,y);if(v<11)continue;const b=bins.get(key)||{lo:Infinity,hi:-Infinity};b.lo=Math.min(b.lo,u);b.hi=Math.max(b.hi,u);bins.set(key,b);
   }
   for(let y=0;y<n;y++)for(let x=0;x<n;x++){
    const i=(y*n+x)*4;if(!d[i+3])continue;const {u,v}=coord(x,y),b=bins.get(x-y);if(v<11||!b)continue;
    const isGold=d[i]>d[i+2]*1.35&&d[i+1]>d[i+2]*1.12&&d[i]>65;
    if(isGold){goldKept++;continue;}
    const fromEdge=(b.hi-u)*k,fromSpine=(u-b.lo)*k;
    let shade;
    if(fromEdge<.9)shade='9eafb0';
    else if(fromEdge<2.15)shade='526a73';
    else if(fromSpine<1)shade='151b20';
    else if(fromEdge<3.3)shade='303f47';
    else shade=u<(b.lo+b.hi)*.5?'222d34':'29353c';
    d.set([...color(shade),255],i);
   }
   for(let i=0;i<d.length;i+=4){if([0,1,2,3].some(j=>d[i+j]!==before[i+j]))changed++;if(d[i+3]!==before[i+3])alphaChanged++;}
   ctx.putImageData(im,0,0);
  },{no_undo:true});
  const after=t.canvas.getContext('2d').getImageData(0,0,n,n).data,created=[];
  for(const c of old)c.remove();
  for(let y=0;y<n;y++)for(let x=0;x<n;){
   if(!after[(y*n+x)*4+3]){x++;continue;}let w=1;while(x+w<n&&after[(y*n+x+w)*4+3])w++;
   const faces={};for(const face of ['north','south','east','west','up','down'])faces[face]={uv:['north','south'].includes(face)?[x,y,x+w,y+1]:[x,y,x+1,y+1],texture:t.uuid};
   created.push(new Cube({name:'starscourge_greatsword__'+state+'__sharp_'+created.length,from:[ox+x*unit,oy-(y+1)*unit,-.5],to:[ox+(x+w)*unit,oy-y*unit,.5],box_uv:false,autouv:0,faces}).addTo(group).init());x+=w;
  }
  Undo.finishEdit('Starscourge sharp tip and steel bevel: '+state,{textures:[t],elements:created,outliner:true});
  s.group=group.uuid;s.texture=t.uuid;
  const entry={state,texture:t.name,width:n,changedPixels:changed,alphaChanged,goldKept,beforeExtent:extent,afterExtent:span(after,n,anchor),modelUnitsPerPixel:unit,beforeElements:old.length,afterElements:created.length};
  report.states.push(entry);
  Blockbench.writeFile(root+'textures/'+t.name,{content:t.canvas.toDataURL(),savetype:'image'});
 }
 contract.group=Group.all.find(g=>g.name.includes('|')).uuid;
 contract.revisions??=[];contract.revisions.push({name:'mc-sharp-20261022',note:'Narrow steel cutting bevel and pointed tip; original axial extent retained'});
 contract.designNote='Pointed tip, narrow continuous steel bevel, dark spine, original gold motif and heavy blade proportions';
 Project.unhandled_root_fields.stars_sharpen=report;
 Canvas.updateAll();
 Codecs.project.write(Codecs.project.compile({bitmaps:true,history:true}),root+'items/starscourge_greatsword.bbmodel');
 Blockbench.writeFile(folder+'native-report.json',{content:JSON.stringify(report,null,2)});
 return report;
})()
