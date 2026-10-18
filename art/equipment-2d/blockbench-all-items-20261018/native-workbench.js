(() => {
 const contract=Project.unhandled_root_fields.art_contract;
 const root='D:/codexout/mcmod/art/equipment-2d/blockbench-all-items-20261018/';
 const specs={
  sacred_relic_sword:{edge:.18,root:.19,ridge:'#d3c78e',tint:[4,3,-2],note:'保留人形扭转与分叉，整理刃边和实体颈座；骨金分面。'},
  marikas_hammer:{edge:.3,root:.16,tint:[-3,1,2],note:'石面与旧金裂隙分开，槌头颈座加固，减少碎石噪点。'},
  malikeths_black_blade:{edge:.18,root:.19,ridge:'#5d6262',tint:[-2,1,3],dark:true,note:'保留黑剑锯裂特征和朝向，减轻无意刃缘突变，黑铁与暗红分层。'},
  blasphemous_blade:{edge:.25,root:.21,tint:[3,-2,-1],flesh:true,ridge:'#a25b4f',note:'保留新版亵渎造型，平顺刃尖及连接，肉质暗红与旧金护手分层。'},
  hand_of_malenia:{edge:.52,root:.15,ridge:'#dac68f',tint:[3,2,-3],note:'弧刃连续收尖，刃脊与刃口分面，刀根到义手一体衔接。'},
  starscourge_greatsword:{edge:.4,root:.22,tint:[-2,1,4],dark:true,note:'保留单刃版与金纹，不在图里合成双剑；宽刃边缘和实体剑格修整。'},
  mohgwyns_sacred_spear:{edge:.24,root:.22,tint:[2,-1,-3],note:'三叉各自保留尖端，整理叉根与颈座，旧金与深色长柄区分。'},
  morgotts_cursed_sword:{edge:0,root:0,tint:[0,0,0],iridescent:true,note:'用户手绘外轮廓与alpha逐像素锁定；仅改内部刃脊与虹彩材质。'},
  claw:{edge:.3,root:0,tint:[-2,3,5],ridge:'#c4d3d0',note:'保留三刃间隙，修整外侧刃线，手背底座、皮带和钢刃分区。'},
  colossal_sword:{edge:.3,root:.2,tint:[0,3,4],ridge:'#b8c7c5',note:'沿用指定旧版特大剑，整理宽刃收尖与剑格，不换成其它款式。'},
  colossal_weapon:{edge:.15,root:.18,tint:[-1,2,3],note:'保留重锤结构，加强槌头套箍、长柄和金属端面的层次。'},
  curved_greatsword:{edge:.4,root:.18,tint:[-1,2,5],ridge:'#bacbc7',note:'以刚采用的大曲剑为底，平顺弧刃与宽窄转换，保留重型宽刃。'},
  curved_sword:{edge:.48,root:.17,tint:[1,4,5],ridge:'#cdd8ce',note:'保留窄曲剑比例，弧刃连续收尖，剑根不悬浮。'},
  fist:{edge:.18,root:0,tint:[1,2,3],note:'整理指节外缘与腕口，指节金属、手背皮革和束带区分。'},
  flail:{edge:.14,root:0,tint:[0,2,4],note:'保留已选连枷，链节和槌头不抹成实心板，金属链与皮柄明确分离。'},
  great_spear:{edge:.45,root:.19,tint:[0,2,5],ridge:'#c1d0cd',note:'长叶矛尖连续收束，矛头套筒和杆过渡，仍保留独立长度。'},
  great_sword:{edge:.32,root:.2,tint:[1,3,4],ridge:'#c6d2cc',note:'沿用指定旧版大剑，修刃尖与肩部连接，强化刃面而不改成特大剑。'},
  heavy_thrusting_sword:{edge:.34,root:.17,tint:[-1,3,5],ridge:'#c8d4d0',note:'保留已选护手与窄长刺刃，刃脊、刃尖和护手根部修整。'},
  twinblade:{edge:.36,root:.15,tint:[-1,3,5],ridge:'#c3d1cd',double:true,note:'上下双刃分别平顺收尖，中央握柄不缩短，保留已选版。'},
  whip:{edge:.22,root:0,tint:[4,1,-2],note:'保留静态鞭形，修边缘突阶和鞭根色块，不冒充攻击动画。'},
  glintstone_staff:{edge:.17,root:.15,tint:[-2,3,6],gem:true,note:'晶体与叉座贴合，蓝青晶面、旧金金属和深木长柄分区。'},
  sacred_seal:{edge:.2,root:0,tint:[4,2,-1],note:'印记分叉和实心座区分，骨金与小块辉石不混成一团。'},
  great_shield:{edge:0,root:0,tint:[1,2,2],shield:true,note:'修盾缘与底角突阶，正背遮罩一致，盾面和盾脐大块明暗。'},
  shield:{edge:0,root:0,tint:[2,0,-1],shield:true,note:'保留用户认可中盾，上下同一布面色系，底部按较大像素节奏过渡。'},
  small_shield:{edge:0,root:0,tint:[1,2,0],shield:true,note:'扣盾边缘和盾脐分层，正背保持同轮廓，减少无意碎边。'},
  great_bow:{edge:.12,root:0,tint:[2,2,-1],bow:true,note:'四个拉弦姿态分别修接缝，握柄位置相同，弦、弓臂、箭杆材质区分。'},
  ballista:{edge:.1,root:0,tint:[-1,2,3],bow:true,note:'五个装填姿态整理弩臂端面和箭槽，不用PNG旋转修已有姿态校准。'},
  test_flask:{edge:0,root:0,tint:[3,2,-1],flask:true,note:'修肩部与收颈转折，封蜡、瓶身、金液和瓶底分面，不做光滑渐变。'}
 };
 const batches=[['sacred_relic_sword','marikas_hammer','malikeths_black_blade','blasphemous_blade','hand_of_malenia','starscourge_greatsword','mohgwyns_sacred_spear','morgotts_cursed_sword'],['claw','colossal_sword','colossal_weapon','curved_greatsword','curved_sword','fist','flail','great_spear','great_sword','heavy_thrusting_sword','twinblade','whip'],['glintstone_staff','sacred_seal','great_shield','shield','small_shield','great_bow','ballista','test_flask']];
 const clamp=(v,l=0,h=255)=>Math.max(l,Math.min(h,v));
 const rgb=hex=>[1,3,5].map(i=>parseInt(hex.slice(i,i+2),16));
 function pixels(texture){return texture.canvas.getContext('2d').getImageData(0,0,texture.width,texture.height)}
 function components(d,n){const pending=new Set();for(let i=0;i<d.length;i+=4)if(d[i+3])pending.add(i/4);const groups=[];while(pending.size){const q=[pending.values().next().value],g=[];pending.delete(q[0]);while(q.length){const i=q.pop();g.push(i);const x=i%n,y=Math.floor(i/n);for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){const xx=x+dx,yy=y+dy;if(xx>=0&&yy>=0&&xx<n&&yy<n){const k=yy*n+xx;if(pending.delete(k))q.push(k)}}}groups.push(g)}return groups.sort((a,b)=>b.length-a.length)}
 function axisSpan(data,n,anchor,angle){const c=Math.cos(angle),s=Math.sin(angle),values=[];for(let y=0;y<n;y++)for(let x=0;x<n;x++)if(data[(y*n+x)*4+3])values.push((x+.5-anchor[0])*s-(y+.5-anchor[1])*c);return Math.max(...values)-Math.min(...values)+Math.abs(c)+Math.abs(s)}
 function nativePaint(a,state){
  const spec=specs[a.id],texture=Texture.all.find(t=>t.uuid===state.texture),n=texture.width,angle=a.projection.angleDegrees*Math.PI/180,c=Math.cos(angle),sn=Math.sin(angle),anchor=state.anchorPixels;
  const original=pixels(texture),before=new Uint8ClampedArray(original.data),oldElements=[...Group.all.find(g=>g.uuid===state.group).children];
  if(!before.some((v,i)=>i%4===3&&v>0))throw new Error(a.id+' '+state.state+' texture is not loaded yet');
  Undo.initEdit({textures:[texture],bitmap:true,elements:oldElements,outliner:true});
  const uv=(x,y)=>[(x+.5-anchor[0])*c+(y+.5-anchor[1])*sn,(x+.5-anchor[0])*sn-(y+.5-anchor[1])*c];
  const primary=state.state===a.states[0].state;
  const report={id:a.id,state:state.state,texture:texture.uuid,operations:[],beforeElements:oldElements.length,note:spec.note};
  texture.edit(canvas=>{
   const ctx=canvas.getContext('2d'),image=ctx.getImageData(0,0,n,n),d=image.data,bins=new Map();
   let vmax=0;
   for(let y=0;y<n;y++)for(let x=0;x<n;x++)if(before[(y*n+x)*4+3]){const [u,v]=uv(x,y),k=Math.floor(v);vmax=Math.max(vmax,v);const b=bins.get(k)||{lo:Infinity,hi:-Infinity};b.lo=Math.min(b.lo,u);b.hi=Math.max(b.hi,u);bins.set(k,b)}
   const safeCopy=(x,y)=>{for(let radius=1;radius<=2;radius++)for(let yy=-radius;yy<=radius;yy++)for(let xx=-radius;xx<=radius;xx++){const nx=x+xx,ny=y+yy;if(nx>0&&ny>0&&nx<n-1&&ny<n-1){const i=(ny*n+nx)*4;if(before[i+3])return [...before.slice(i,i+3),255]}}return null};
   if(spec.edge&&n>=32){
    for(let y=1;y<n-1;y++)for(let x=1;x<n-1;x++){
     const [u,v]=uv(x,y),t=Math.abs(v)/vmax;if(t<.28||t>.95||v<0&&!spec.double)continue;
     const k=Math.floor(v),b=bins.get(k);if(!b||b.hi-b.lo<1.3)continue;
     const around=[-2,-1,0,1,2].map(i=>bins.get(k+i)).filter(Boolean);
     let lo=b.lo*(1-spec.edge)+around.reduce((s,b)=>s+b.lo,0)/around.length*spec.edge;
     let hi=b.hi*(1-spec.edge)+around.reduce((s,b)=>s+b.hi,0)/around.length*spec.edge;
     lo=clamp(lo,b.lo-.65,b.lo+.65);hi=clamp(hi,b.hi-.65,b.hi+.65);
     const i=(y*n+x)*4,inside=u>=lo-.08&&u<=hi+.08;
     if(before[i+3]&&!inside&&(Math.abs(u-b.lo)<.8||Math.abs(u-b.hi)<.8))d[i+3]=0;
     if(!before[i+3]&&inside&&(u<b.lo||u>b.hi)){const p=safeCopy(x,y);if(p)d.set(p,i)}
    }
    report.operations.push('Local blade/limb contour fairing; motif holes retained');
   }
   if(spec.root){
    for(let y=1;y<n-1;y++)for(let x=1;x<n-1;x++){
     const [u,v]=uv(x,y),t=v/vmax,i=(y*n+x)*4;
     if(t>.06&&t<spec.root&&Math.abs(u)<.72&&!d[i+3]){const p=safeCopy(x,y);if(p)d.set(p,i)}
    }
    report.operations.push('Solid root/neck seam reinforcement');
   }
   if(spec.shield||spec.flask){
    const rows=[];
    for(let y=0;y<n;y++){const xs=[];for(let x=0;x<n;x++)if(before[(y*n+x)*4+3])xs.push(x);if(xs.length)rows.push({y,lo:Math.min(...xs),hi:Math.max(...xs)})}
    const low=rows[0].y,high=rows.at(-1).y;
    for(const row of rows){
     if(row.y<low+(high-low)*.64||row.y>=high-1)continue;
     const peers=rows.filter(r=>Math.abs(r.y-row.y)<=1),targetLo=Math.round(peers.reduce((v,r)=>v+r.lo,0)/peers.length),targetHi=Math.round(peers.reduce((v,r)=>v+r.hi,0)/peers.length);
     for(const [x,target] of [[row.lo,targetLo],[row.hi,targetHi]]){
      if(Math.abs(x-target)!==1)continue;
      if(x<target&&x===row.lo||x>target&&x===row.hi)d[(row.y*n+x)*4+3]=0;
      else{const color=safeCopy(target,row.y);if(color)d.set(color,(row.y*n+target)*4)}
     }
    }
    report.operations.push(spec.shield?'Shield lower-outline step cleanup':'Bottle shoulder/base step cleanup');
   }
   if(spec.shield&&state.state==='back'){
    const front=Texture.all.find(t=>t.uuid===a.states[0].texture),mask=pixels(front).data;
    for(let i=0;i<d.length;i+=4){if(!mask[i+3])d[i+3]=0;else if(!d[i+3]){d[i]=105;d[i+1]=78;d[i+2]=51;d[i+3]=255}}
    report.operations.push('Back alpha matched to front');
   }
   const grip=(Math.floor(anchor[1])*n+Math.floor(anchor[0]))*4;d.set(before.slice(grip,grip+4),grip);
   let groups=components(d,n);
   while(groups.length>1){
    const target=new Set(groups.slice(1).flat()),queue=[...groups[0]],previous=new Map(queue.map(i=>[i,null]));let found=null;
    for(let at=0;at<queue.length&&found===null;at++){
     const index=queue[at],x=index%n,y=Math.floor(index/n);
     for(let dy=-1;dy<=1&&found===null;dy++)for(let dx=-1;dx<=1;dx++){
      const xx=x+dx,yy=y+dy,k=yy*n+xx;
      if(xx<0||yy<0||xx>=n||yy>=n||previous.has(k)||!before[k*4+3])continue;
      previous.set(k,index);queue.push(k);if(target.has(k)){found=k;break}
     }
    }
    if(found===null)throw new Error(a.id+' has an unsupported new component');
    for(let k=found;k!==null;k=previous.get(k))if(!d[k*4+3])d.set(before.slice(k*4,k*4+4),k*4);
    groups=components(d,n);
   }
   const colors=[];
   for(let i=0;i<before.length;i+=4)if(before[i+3]){const v=[...before.slice(i,i+3)];if(!colors.some(c=>c.every((n,k)=>n===v[k])))colors.push(v)}
   const anchors=[];
   for(const color of colors){if(!anchors.some(a=>Math.hypot(...a.map((v,i)=>(v-color[i])*(i===1?1.2:1)))<15))anchors.push(color)}
   for(let y=0;y<n;y++)for(let x=0;x<n;x++){
    const i=(y*n+x)*4;if(!d[i+3])continue;const [u,v]=uv(x,y),t=v/vmax;
    const old=[...d.slice(i,i+3)],nearest=anchors.reduce((a,b)=>Math.hypot(...b.map((v,k)=>v-old[k]))<Math.hypot(...a.map((v,k)=>v-old[k]))?b:a,anchors[0]);
    let color=nearest.map((v,k)=>clamp(Math.round(v+spec.tint[k])));
    const max=Math.max(...old),min=Math.min(...old),lum=.2126*old[0]+.7152*old[1]+.0722*old[2];
    if(spec.iridescent){
     color=old.map((v,k)=>clamp(Math.round(v+[2,1,3][k])));
     if(t>.23&&t<.94&&lum>42&&lum<215){const iridescence=t<.42?[19,-1,13]:t<.62?[2,8,18]:t<.8?[14,5,18]:[13,9,0];color=old.map((v,k)=>clamp(Math.round(v*1.04+iridescence[k])))}
    }else if(spec.flesh&&old[0]>old[1]*1.3&&old[0]>55){color=[clamp(old[0]+9),clamp(old[1]-3),clamp(old[2]+3)]}
    else if(spec.dark&&lum<90&&max-min<28)color=[clamp(old[0]-2),clamp(old[1]+1),clamp(old[2]+5)];
    if(spec.gem&&old[2]>old[0]*1.08&&t>.65)color=[clamp(old[0]-6),clamp(old[1]+10),clamp(old[2]+11)];
    if(spec.shield&&old[0]>old[1]*1.35&&old[1]>15)color=lum<75?[101,49,43]:[123,61,51];
    if(spec.ridge&&t>.3&&t<.9&&max-min<36&&lum>90){const b=bins.get(Math.floor(v));if(b&&Math.abs(u-(b.lo+(b.hi-b.lo)*.65))<.44)color=rgb(spec.ridge)}
    d[i]=color[0];d[i+1]=color[1];d[i+2]=color[2];
   }
   if(spec.flask){
    for(let y=1;y<n-1;y++)for(let x=1;x<n-1;x++){
     const i=(y*n+x)*4;if(!d[i+3])continue;
     if(y<n*.25&&before[i]>before[i+1]*1.1){d[i]=105;d[i+1]=50;d[i+2]=42}
     if(x>=n*.37&&x<n*.42&&y>n*.43&&y<n*.7){d[i]=192;d[i+1]=172;d[i+2]=112}
    }
    report.operations.push('Wax cap and deliberate bottle highlight block');
   }
   if(spec.iridescent)for(let i=3;i<d.length;i+=4)d[i]=before[i];
   ctx.putImageData(image,0,0);report.operations.push(spec.iridescent?'Authored multihue interior; exact alpha lock':'Per-item material palette clustering and authored facet accents');
  },{no_undo:true});
  const after=pixels(texture).data;let rgbaDiff=0,alphaDiff=0;
  for(let i=0;i<after.length;i+=4){if(after[i+3]!==before[i+3])alphaDiff++;if([0,1,2,3].some(k=>after[i+k]!==before[i+k]))rgbaDiff++}
  report.rgbaDiff=rgbaDiff;report.alphaDiff=alphaDiff;
  report.components=components(after,n).length;
  if(!rgbaDiff||report.components!==1){Undo.cancelEdit();throw new Error(a.id+' '+state.state+' invalid native result: changed='+rgbaDiff+', components='+report.components)}
  if(primary){a.originalSourceSpanPixels=a.sourceSpanPixels;a.sourceSpanPixels=a.originalSourceSpanPixels*axisSpan(after,n,anchor,angle)/axisSpan(before,n,anchor,angle);a.suggestedDisplay.uniformXYScale=a.worldLengthBlocks*n/a.sourceSpanPixels}
  const stateGroup=Group.all.find(g=>g.uuid===state.group),itemGroup=Group.all.find(g=>g.uuid===a.group),unit=a.worldLengthBlocks*16/(a.sourceSpanPixels*n/a.states[0].size[0]),origin=itemGroup.origin,created=[],used=new Set();
  for(const element of oldElements)element.remove();
  for(let y=0;y<n;y++)for(let x=0;x<n;x++){
   if(!after[(y*n+x)*4+3]||used.has(y*n+x))continue;let w=1;while(x+w<n&&after[(y*n+x+w)*4+3]&&!used.has(y*n+x+w))w++;
   let h=1;outer:while(y+h<n){for(let xx=x;xx<x+w;xx++)if(!after[((y+h)*n+xx)*4+3]||used.has((y+h)*n+xx))break outer;h++}
   for(let yy=y;yy<y+h;yy++)for(let xx=x;xx<x+w;xx++)used.add(yy*n+xx);
   const faces={};for(const face of ['north','south','east','west','up','down'])faces[face]={uv:face==='south'?[x,y,x+w,y+h]:face==='north'?[x+w,y,x,y+h]:[x,y,x+1,y+1],texture:texture.uuid};
   const cube=new Cube({name:a.id+'__'+state.state+'__native_'+created.length,from:[origin[0]+(x-anchor[0])*unit,origin[1]+(anchor[1]-y-h)*unit,-.5],to:[origin[0]+(x+w-anchor[0])*unit,origin[1]+(anchor[1]-y)*unit,.5],box_uv:false,autouv:0,faces}).addTo(stateGroup).init();created.push(cube);
  }
  report.afterElements=created.length;report.geometryRebuiltInNativeCube=true;
  Undo.finishEdit('Optimize '+a.name+' / '+state.state,{textures:[texture],elements:created,outliner:true});
  state.nativeReport=report;return report;
 }
 function save(){
  for(const a of contract.items.filter(a=>a.nativeDone))for(const state of a.states){const t=Texture.all.find(t=>t.uuid===state.texture);Blockbench.writeFile('D:/codexout/mcmod/'+state.output,{content:t.canvas.toDataURL('image/png'),savetype:'image'})}
  contract.authoring={tool:'Blockbench 5.2.2 native Texture.edit / Cube / UV',batches:contract.batches||[],pixelPolicy:'No soft edges; masks changed only by explicit local contour/root operations; Morgott alpha locked',runtimeIntegrated:false,humanAccepted:false};
  Project.name='All Items Native Optimized';Codecs.project.write(Codecs.project.compile({bitmaps:true,history:true}),root+'all-items-optimized.bbmodel');
  Blockbench.writeFile(root+'native-edit-report.json',{content:JSON.stringify({project:Project.uuid,items:contract.items,authoring:contract.authoring,undoEntries:Undo.history.length},null,2)});
 }
 function batch(index){
  const ids=batches[index];if(!ids)throw new Error('Unknown batch');const records=[];
  for(const id of ids){const a=contract.items.find(a=>a.id===id);if(a.nativeDone)continue;a.designNote=specs[id].note;
   for(const state of a.states)records.push(nativePaint(a,state));a.nativeDone=true;a.batch=index+1;
  }
  Canvas.updateAll();(contract.batches??=[]).push({index:index+1,ids,states:records.length,status:'native_edits_saved_pending_visual_review'});save();
  return {batch:index+1,items:ids.length,states:records.length,geometryEdited:records.filter(r=>r.alphaDiff>0).length,records};
 }
 function exportItems(){
  for(const a of contract.items){
   if(!a.nativeDone)throw new Error('Incomplete item '+a.id);
   const group=Group.all.find(g=>g.uuid===a.group),children=[group];group.forEachChild(e=>children.push(e));
   const model=Codecs.project.compile({raw:true,bitmaps:true,collection_only:{getAllChildren:()=>children}}),used=[...new Set(model.elements.flatMap(e=>Object.values(e.faces||{}).map(f=>f.texture)).filter(v=>Number.isInteger(v)))].sort((a,b)=>a-b);
   const map=new Map(used.map((v,i)=>[v,i]));model.textures=used.map(i=>model.textures[i]);
   for(const e of model.elements)for(const f of Object.values(e.faces||{}))if(Number.isInteger(f.texture))f.texture=map.get(f.texture);
   const offset=group.origin;
   for(const e of model.elements){for(const key of ['from','to','origin'])if(e[key])e[key]=e[key].map((v,k)=>v-offset[k])}
   for(const g of model.groups)if(g.origin)g.origin=g.origin.map((v,k)=>v-offset[k]);
   model.name=a.id;model.art_contract={...contract,items:[a]};delete model.art_contract.batches;
   Blockbench.writeFile(root+'items/'+a.id+'.bbmodel',{content:compileJSON(model)});
  }
  return {items:contract.items.length,textures:contract.items.reduce((n,a)=>n+a.states.length,0)};
 }
 function capture(ids,tag){
  const view=Preview.all.find(v=>v.id==='mcp_offscreen_all_items_review');if(!view)throw new Error('Create native all_items_review viewport first');
  const files=[];
  for(const id of ids){const a=contract.items.find(a=>a.id===id),group=Group.all.find(g=>g.uuid===a.group),state=Group.all.find(g=>g.uuid===a.states[0].group),origin=group.origin,angle=group.rotation[2]*Math.PI/180,points=[];
   for(const e of state.children)for(const x of [e.from[0],e.to[0]])for(const y of [e.from[1],e.to[1]]){const xx=x-origin[0],yy=y-origin[1];points.push([origin[0]+xx*Math.cos(angle)-yy*Math.sin(angle),origin[1]+xx*Math.sin(angle)+yy*Math.cos(angle)])}
   const lo=[0,1].map(k=>Math.min(...points.map(p=>p[k]))),hi=[0,1].map(k=>Math.max(...points.map(p=>p[k]))),cx=(lo[0]+hi[0])/2,cy=(lo[1]+hi[1])/2;
   view.setProjectionMode(true);view.resize(720,720);view.setLockedAngle();view.camera.position.set(cx,cy,600);view.controls.target.set(cx,cy,0);view.camera.zoom=18/(Math.max(hi[0]-lo[0],hi[1]-lo[1])*1.28);view.camera.updateProjectionMatrix();view.controls.update();
   const file='qa/'+tag+'-'+id+'.png';view.screenshot({crop:false,width:720,height:720},data=>Blockbench.writeFile(root+file,{content:data,savetype:'image'}));files.push(file);
  }
  return {captured:files.length,files};
 }
 window.NativeItemWorkbench={batch,save,exportItems,capture,batches,specs};
 return {installed:true,project:Project.uuid,items:contract.items.length,batches:batches.map(b=>b.length)};
})()
