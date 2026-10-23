(() => {
 const root='D:/codexout/mcmod/art/equipment-2d/mc-polish-20261022/',folder=root+'revisions/great-spear-remake/';
 if(Project.name!=='great_spear')throw Error('Open great_spear first');
 fs.mkdirSync(folder,{recursive:true});
 if(fs.existsSync(folder+'before.bbmodel'))throw Error('Backup already exists; select it explicitly before redoing the candidate');
 fs.copyFileSync(root+'items/great_spear.bbmodel',folder+'before.bbmodel');
 const contract=Project.unhandled_root_fields.art_contract.items[0],report={id:'great_spear',date:'2026-10-22',design:'Long leaf head, center ridge, continuous cutting edges, reinforced socket, ash shaft and leather grip',states:[]};
 const rgb=h=>[0,2,4].map(i=>parseInt(h.slice(i,i+2),16));
 for(const t of Texture.all){
  const n=t.width,k=n/32,state=t.name.split('__')[1].replace('.png',''),s=contract.states.find(s=>s.state===state),anchor=s.anchorPixels;
  const old=Cube.all.filter(c=>c.name.startsWith('great_spear__'+state+'__')),group=old[0].parent,uv=old[0].faces.south.uv;
  const unit=(old[0].to[0]-old[0].from[0])/Math.abs(uv[2]-uv[0]),ox=old[0].from[0]-Math.min(uv[0],uv[2])*unit,oy=old[0].to[1]+Math.min(uv[1],uv[3])*unit;
  const before=t.canvas.getContext('2d').getImageData(0,0,n,n).data.slice();
  const coord=(x,y)=>({u:(x+.5-anchor[0]+y+.5-anchor[1])/Math.SQRT2/k,v:(x+.5-anchor[0]-y-.5+anchor[1])/Math.SQRT2/k});
  let lo=Infinity,hi=-Infinity;const endpoints=[];
  for(let y=0;y<n;y++)for(let x=0;x<n;x++)if(before[(y*n+x)*4+3]){const p=coord(x,y);lo=Math.min(lo,p.v);hi=Math.max(hi,p.v);endpoints.push({x,y,...p});}
  Blockbench.writeFile(folder+t.name,{content:t.canvas.toDataURL(),savetype:'image'});
  Undo.initEdit({textures:[t],bitmap:true,elements:old,outliner:true});
  t.edit(canvas=>{
   const ctx=canvas.getContext('2d'),im=ctx.getImageData(0,0,n,n),d=im.data;d.fill(0);
   const widthAt=v=>{
    const nodes=[[18,1.15],[20.5,3.1],[23,3.65],[25.5,2.85],[28.5,1.45],[hi,.15]];
    for(let i=1;i<nodes.length;i++)if(v<=nodes[i][0]){const [x0,w0]=nodes[i-1],[x1,w1]=nodes[i],f=(v-x0)/(x1-x0);return w0+(w1-w0)*f;}return .15;
   };
   for(let y=0;y<n;y++)for(let x=0;x<n;x++){
    const i=(y*n+x)*4,{u,v}=coord(x,y);if(v<lo-.001||v>hi+.001)continue;let color=null;
    if(v<lo+1.6){if(Math.abs(u)<1.05)color=u<0?'39484a':'718580';}
    else if(v<15.8){
     if(Math.abs(u)<.94){color=u<-.24?'33271e':u<.28?'937347':'66503b';
      if(v>-3.8&&v<3.6)color=u<0?'302a25':Math.floor((v+4)/1.4)%2?'6f5942':'544536';
     }
    }else if(v<18.8){
     if(Math.abs(u)<(v<17.6?1.35:1.65))color=u<-.25?'584528':u<.6?'c3a462':'8f7442';
    }
    if(v>=18){
     const half=widthAt(v);if(Math.abs(u)<=half){
      const edge=half-Math.abs(u);
      color=edge<.7?'b4c6c2':Math.abs(u)<.53?'dde6d3':u<0?'637d80':'344d59';
      if(v<19.1&&Math.abs(u)<1.2)color=u<0?'718580':'344d59';
     }
    }
    if(color)d.set([...rgb(color),255],i);
   }
   // Keep the original axial endpoints on the center line at every resolution.
   for(const v of [lo,hi]){
    const candidates=endpoints.filter(p=>Math.abs(p.v-v)<.001).sort((a,b)=>Math.abs(a.u)-Math.abs(b.u)),p=candidates[0];
    d.set([...rgb(v===hi?'dde6d3':'39484a'),255],(p.y*n+p.x)*4);
   }
   ctx.putImageData(im,0,0);
  },{no_undo:true});
  const after=t.canvas.getContext('2d').getImageData(0,0,n,n).data,created=[];
  for(const e of old)e.remove();
  for(let y=0;y<n;y++)for(let x=0;x<n;){
   if(!after[(y*n+x)*4+3]){x++;continue;}let w=1;while(x+w<n&&after[(y*n+x+w)*4+3])w++;
   const faces={};for(const f of ['north','south','east','west','up','down'])faces[f]={uv:['north','south'].includes(f)?[x,y,x+w,y+1]:[x,y,x+1,y+1],texture:t.uuid};
   created.push(new Cube({name:'great_spear__'+state+'__remake_'+created.length,from:[ox+x*unit,oy-(y+1)*unit,-.5],to:[ox+(x+w)*unit,oy-y*unit,.5],autouv:0,box_uv:false,faces}).addTo(group).init());x+=w;
  }
  Undo.finishEdit('Great spear: leaf head, socket and shaft '+state,{textures:[t],elements:created,outliner:true});
  s.group=group.uuid;s.texture=t.uuid;
  report.states.push({state,texture:t.name,width:n,axialMin:lo,axialMax:hi,modelUnitsPerPixel:unit,beforeElements:old.length,afterElements:created.length});
  Blockbench.writeFile(root+'textures/'+t.name,{content:t.canvas.toDataURL(),savetype:'image'});
 }
 contract.group=Group.all.find(g=>g.name.includes('|')).uuid;contract.designNote=report.design;contract.revisions??=[];contract.revisions.push({name:'great-spear-remake-20261022',silhouetteRebuilt:true,worldLengthPreserved:true,gripPreserved:true});
 Project.unhandled_root_fields.great_spear_remake=report;
 Canvas.updateAll();Codecs.project.write(Codecs.project.compile({bitmaps:true,history:true}),root+'items/great_spear.bbmodel');
 Blockbench.writeFile(folder+'native-report.json',{content:JSON.stringify(report,null,2)});
 return report;
})()
