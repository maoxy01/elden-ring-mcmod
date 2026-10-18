(() => {
 const root='D:/codexout/mcmod/art/equipment-2d/blockbench-all-items-20261018/';
 let receipt;Blockbench.read([root+'native-edit-report.json'],{readtype:'text'},f=>receipt=JSON.parse(f[0].content));
 const contract=Project.unhandled_root_fields.art_contract;
 contract.items=receipt.items;
 const ids=['starscourge_greatsword','morgotts_cursed_sword'];
 const notes={starscourge_greatsword:'宽阔黑铁分面、连续刃口、旧金重力纹章与分枝刻线；单把剑、不合成双剑。',morgotts_cursed_sword:'已解除手绘轮廓锁：重画连续弯刃与收尖；紫红、青钢、琥珀虹彩按刃面分带，护手到剑根实体贴合。'};
 const star=[[-5.7,7.5],[-6.1,13],[-5.5,23],[-4.8,34],[-3.6,44],[-1.3,53],[1.8,58],[4.2,61],[8,56],[9.1,49],[8.4,38],[7.4,27],[6.7,16],[5.4,7.5]];
 const cursed=[[-2.3,6],[-3,13],[-3.1,22],[-2.6,31],[-1.4,39],[.7,47],[3.6,54],[7.5,59],[11.2,62],[9.8,56],[8.9,49],[7.4,41],[5.6,33],[4.4,24],[3.9,15],[3.3,7]];
 function inside(x,y,poly){let yes=false;for(let i=0,j=poly.length-1;i<poly.length;j=i++)if((poly[i][1]>y)!==(poly[j][1]>y)&&x<(poly[j][0]-poly[i][0])*(y-poly[i][1])/(poly[j][1]-poly[i][1])+poly[i][0])yes=!yes;return yes;}
 function dist(x,y,a,b){const dx=b[0]-a[0],dy=b[1]-a[1],t=Math.max(0,Math.min(1,((x-a[0])*dx+(y-a[1])*dy)/(dx*dx+dy*dy)));return Math.hypot(x-a[0]-dx*t,y-a[1]-dy*t);}
 function edge(x,y,poly){return Math.min(...poly.map((p,i)=>dist(x,y,p,poly[(i+1)%poly.length])));}
 const color=h=>[1,3,5].map(i=>parseInt(h.slice(i,i+2),16));
 const span=(d,n,anchor)=>{const v=[];for(let y=0;y<n;y++)for(let x=0;x<n;x++)if(d[(y*n+x)*4+3])v.push((x+.5-anchor[0]-y-.5+anchor[1])/Math.SQRT2);return Math.max(...v)-Math.min(...v)+Math.SQRT2;};
 const changes=[];
 for(const id of ids){
  const a=contract.items.find(a=>a.id===id),poly=id===ids[0]?star:cursed;
  a.revisions??=[];a.revisions.push({name:'stars-morgott-r2',previousNote:a.designNote,userAuthorization:'2026-10-18: do not retain my outline',outlineLockReleased:id===ids[1]});
  a.designNote=notes[id];if(id===ids[1])a.outlinePolicy='author_may_redesign_user_outline_20261018';
  for(const s of a.states){
   const t=Texture.all.find(t=>t.uuid===s.texture),n=t.width,k=n/64,anchor=s.anchorPixels,group=Group.all.find(g=>g.uuid===s.group),old=[...group.children],before=t.canvas.getContext('2d').getImageData(0,0,n,n).data.slice();
   if(!before.some((v,i)=>i%4===3&&v))throw new Error('Texture not loaded');
   Undo.initEdit({textures:[t],bitmap:true,elements:old,outliner:true});
   t.edit(canvas=>{
    const ctx=canvas.getContext('2d'),im=ctx.getImageData(0,0,n,n),d=im.data;
    for(let y=0;y<n;y++)for(let x=0;x<n;x++){
     const i=(y*n+x)*4,u=(x+.5-anchor[0]+y+.5-anchor[1])/Math.SQRT2/k,v=(x+.5-anchor[0]-y-.5+anchor[1])/Math.SQRT2/k;
     if(v<6)continue;
     d.fill(0,i,i+4);
     if(id===ids[0]&&v<9.2&&Math.abs(u)<2.6){d.set([...color(v<7.6?'#9b8249':'#343b37'),255],i);continue;}
     if(!inside(u,v,poly))continue;
     let c;
     if(id===ids[0]){
      const center=1+v*.055,delta=u-center,e=edge(u,v,poly);
      c=color(e<.9?'#66716c':e<1.7?'#3f4948':delta<-.9?'#20292b':delta<1.1?'#333c3c':'#171e23');
      const lines=[[[0,13],[0,24]],[[0,24],[2,29]],[[2,29],[1,35]],[[1,35],[3,41]],[[3,41],[2,49]],[[0,24],[-2,27]],[[-2,27],[0,30]],[[1,35],[-1,38]],[[-1,38],[1,41]],[[3,41],[5,44]],[[5,44],[3,47]]];
      const ring=Math.abs(Math.hypot((u-.3)*1.1,v-19)-3.1)<.42;
      const rune=lines.some(([p,q])=>dist(u,v,p,q)<(n===16?1.05:.47));
      if(e>1.65&&(ring||rune))c=color(u<1?'#c3a45b':'#937540');
      if(e>1.65&&v>16&&v<22&&Math.abs(u-.3)<.5)c=color('#e0c581');
     }else{
      const center=v<30?.5:(v-30)*.19+.5,delta=u-center,e=edge(u,v,poly);
      const band=v<20?['#4c375b','#78516c','#b77c8c']:v<35?['#463f69','#70618e','#a7a6b1']:v<48?['#36565a','#65857c','#a1ac8a']:['#685a49','#aa8b59','#d5b77b'];
      c=color(e<.8?'#98a79e':delta<-.9?band[2]:delta<1.1?band[1]:band[0]);
      if(e>.95&&delta>1.7&&v>12&&v<53)c=color(v<25?'#4b6671':v<38?'#86516f':'#93704c');
      if(e>1.1&&Math.abs(delta+.3)<.5&&v>10&&v<55)c=color(v<28?'#b5859f':v<42?'#91aba8':'#bca177');
      if(e>1.3&&v>13&&v<51&&Math.abs(delta-1.1-Math.sin(v*.25)*.9)<.3)c=color('#b76968');
     }
     d.set([...c,255],i);
    }
    ctx.putImageData(im,0,0);
   },{no_undo:true});
   const after=t.canvas.getContext('2d').getImageData(0,0,n,n).data;
   if(s===a.states[0]){a.sourceSpanPixels*=span(after,n,anchor)/span(before,n,anchor);a.suggestedDisplay.uniformXYScale=a.worldLengthBlocks*n/a.sourceSpanPixels;a.projection.modelUnitsPerPixel=a.worldLengthBlocks*16/a.sourceSpanPixels;}
   const itemGroup=Group.all.find(g=>g.uuid===a.group),origin=itemGroup.origin,unit=a.worldLengthBlocks*16/(a.sourceSpanPixels*n/a.states[0].size[0]),created=[],used=new Set();
   for(const e of old)e.remove();
   for(let y=0;y<n;y++)for(let x=0;x<n;x++){
    if(!after[(y*n+x)*4+3]||used.has(y*n+x))continue;let w=1;while(x+w<n&&after[(y*n+x+w)*4+3]&&!used.has(y*n+x+w))w++;
    let h=1;outer:while(y+h<n){for(let xx=x;xx<x+w;xx++)if(!after[((y+h)*n+xx)*4+3]||used.has((y+h)*n+xx))break outer;h++;}
    for(let yy=y;yy<y+h;yy++)for(let xx=x;xx<x+w;xx++)used.add(yy*n+xx);
    const faces={};for(const f of ['north','south','east','west','up','down'])faces[f]={uv:f==='south'?[x,y,x+w,y+h]:f==='north'?[x+w,y,x,y+h]:[x,y,x+1,y+1],texture:t.uuid};
    created.push(new Cube({name:id+'__'+s.state+'__native_'+created.length,from:[origin[0]+(x-anchor[0])*unit,origin[1]+(anchor[1]-y-h)*unit,-.5],to:[origin[0]+(x+w-anchor[0])*unit,origin[1]+(anchor[1]-y)*unit,.5],box_uv:false,autouv:0,faces}).addTo(group).init());
   }
   Undo.finishEdit('R2 native silhouette and material: '+a.name+' '+s.state,{textures:[t],elements:created,outliner:true});
   const r={...s.nativeReport,previousRevision:s.nativeReport,note:notes[id],operations:['Native polygon/pencil blade redraw','Native stepped metal planes and authored motif','Native UV and one-unit Cube rebuild'],beforeElements:old.length,afterElements:created.length,geometryRebuiltInNativeCube:true,outlineLockReleased:id===ids[1]};
   s.nativeReport=r;changes.push({id,state:s.state,elements:created.length});
  }
 }
 Canvas.updateAll();contract.authoring={...receipt.authoring,pixelPolicy:'Hard pixel edges; user released Morgott silhouette lock 2026-10-18; authored curved blades',runtimeIntegrated:false,humanAccepted:false};
 window.TwoWeaponRevision={contract,changes,root};
 return {nativeEdited:true,changes,history:Undo.history.length};
})()
