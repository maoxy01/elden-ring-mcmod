(() => {
 const root='D:/codexout/mcmod/art/vfx-blockbench/takers-flames-20261021/',contract=Project.unhandled_root_fields.takers_flames_art;
 if(!contract||Texture.all.length!==64)throw Error('Dedicated native canvases required');
 const palette={rim:'#4b2026',red:'#a52c24',lava:'#e65920',orange:'#fa982f',hot:'#ffe493',rock:'#342f32',rockFace:'#514a47',rockTop:'#817465',ember:'#944332',ash:'#5c5450'};
 function paint(ctx,layer,frame){
  const p=frame/15,rect=(x,y,w,h,c)=>{ctx.fillStyle=palette[c]||c;const left=Math.max(1,Math.round(x)),top=Math.max(1,Math.round(y)),right=Math.min(31,Math.round(x+w)),bottom=Math.min(31,Math.round(y+h));if(right>left&&bottom>top)ctx.fillRect(left*2,top*2,(right-left)*2,(bottom-top)*2);};
  const line=(points,w,c)=>{for(let a=1;a<points.length;a++){const [x,y]=points[a-1],[xx,yy]=points[a],steps=Math.max(Math.abs(xx-x),Math.abs(yy-y));for(let k=0;k<=steps;k++)rect(x+(xx-x)*k/steps-w/2,y+(yy-y)*k/steps-w/2,w,w,c);}};
  const envelope=p<.27?.12+p/.27*.88:p<.64?1:Math.max(.045,(1-p)/.36);
  if(layer==='flame_front'){
   rect(2,27,28,2,p>.85?'ember':'rim');rect(4,27,24,2,p>.85?'red':'lava');
   const heights=[10,17,24,15,25,19,23,12,18];
   for(let j=0;j<heights.length;j++){
    const stagger=[0,1,-1,2,0,-2,1,0,-1][j],h=Math.max(1,Math.round((heights[j]+stagger*(frame%3-1))*envelope)),x=2+j*3;
    rect(x,28-h,3,h+1,'rim');rect(x,29-h,2,h-1,'red');
    rect(x,30-h,2,h-3,p>.88?'ember':'lava');
    if(h>7){rect(x,32-h,1,h-6,'orange');rect(x,28-h,1,2,'red');}
    if(h>12)rect(x,34-h,1,h-10,'hot');
   }
   if(frame>=2&&frame<=11){
    line([[9,26],[6,19],[3,16]],2,'red');line([[21,26],[25,18],[29,15]],2,'red');
    line([[9,26],[6,21]],1,'orange');line([[21,26],[25,20]],1,'orange');
   }
   for(const [x,y,w] of [[3,27,4],[11,28,3],[19,27,4],[27,28,3]]){rect(x,y,w,2,'rock');rect(x,y,w-1,1,'rockFace');}
  }
  if(layer==='ground_rupture'){
   const path=[[15,28],[17,24],[14,20],[17,16],[15,12],[18,8],[16,3]],branches=[[[14,20],[9,20],[5,16]],[[17,16],[23,15],[28,11]],[[15,12],[10,10],[7,6]],[[17,24],[23,25],[27,28]]];
   const growth=Math.min(1,(frame+1)/5),visible=pts=>pts.map(([x,y])=>[x,y]).filter(([x,y])=>y>=28-26*growth);
   const points=visible(path);if(points.length>1){line(points,5,'rock');line(points,3,frame>11?'rim':'red');line(points,1,frame>12?'ember':'hot');}
   for(const b of branches){if(b[0][1]<28-26*growth)continue;line(b,3,'rock');line(b,1,frame>10?'ember':'orange');}
   const plates=[[9,23,4,3],[20,19,5,3],[9,13,3,4],[21,7,4,3],[21,25,3,3],[6,17,3,2]];
   for(let i=0;i<plates.length;i++){const [x,y,w,h]=plates[i];if(y<28-26*growth)continue;rect(x,y,w,h,'rock');rect(x,y,w-1,h-1,frame>11?'rockFace':'rockTop');}
   if(frame>12){for(const [x,y,w,h] of [[3,2,12,8],[18,11,12,5],[2,25,10,6]]){ctx.clearRect(x*2,y*2,w*2,h*2);}}
  }
  if(layer==='eruption_debris'){
   const chunks=[[-1,5,4,3],[1,5,4,3],[-1,10,3,3],[1,11,3,4],[-1,15,3,2],[1,17,3,2]];
   for(let j=0;j<chunks.length;j++){
    const [side,speed,w,h]=chunks[j],q=Math.max(0,Math.min(1,(p-.04-j*.025)/.82));if(q<=0||q>=1)continue;
    const x=16+side*(3+speed*.45*q)-w/2,y=27-(j%2?20:25)*4*q*(1-q)+q*2;
    rect(x,y,w,h,'rock');rect(x,y,w-1,1,'rockTop');rect(x,y+1,1,h-1,'rockFace');if(q<.56)rect(x+1,y+h-1,w-1,1,'lava');
   }
   if(frame>10&&frame<15){rect(6,27,3,1,'ash');rect(24,28,3,1,'ash');}
  }
 }
 const painted=[];
 for(const d of contract.defs.filter(d=>d.layer!=='before')){
  const t=Texture.all.find(t=>t.uuid===d.texture);
  t.edit(canvas=>{const ctx=canvas.getContext('2d');ctx.clearRect(0,0,64,64);ctx.imageSmoothingEnabled=false;paint(ctx,d.layer,d.index);},{edit_name:'Paint '+d.layer+' '+d.index+' hard-edge eruption frame'});
  Blockbench.writeFile(root+'textures/'+d.layer+'/frame_'+String(d.index).padStart(2,'0')+'.png',{content:t.canvas.toDataURL('image/png'),savetype:'image'});painted.push(d.name);
 }
 const elements=[],groups=[],sequence=new Group({name:'candidate_sequence',origin:[0,0,0]}).init(),reference=new Group({name:'before_reference',origin:[-104,0,0]}).init();groups.push(sequence,reference);
 Undo.initEdit({elements:[],outliner:true,animations:[]});
 function plane(d,parent){
  const floor=d.layer==='ground_rupture',before=d.layer==='before',offset=before?-104:0,faces={};
  for(const f of ['north','south','east','west','up','down'])faces[f]={uv:[0,0,64,64],texture:null};
  faces[floor?'up':'south']={uv:[0,0,64,64],texture:d.texture};
  const e=new Cube({name:d.name,from:floor?[-40,.24,-32]:[-40+offset,-5.25,d.layer==='eruption_debris'?.5:0],to:floor?[40,.24,32]:[40+offset,50.75,d.layer==='eruption_debris'?.5:0],box_uv:false,autouv:0,shade:false,faces}).addTo(parent).init();elements.push(e);return e;
 }
 const animation=new Animation({name:'takers_flames_art_preview',length:1.6,loop:'loop'}).add(false);
 for(const kind of ['candidate','before'])for(let i=0;i<16;i++){
  const g=new Group({name:kind+'_frame_'+String(i).padStart(2,'0'),origin:[0,0,0]}).addTo(kind==='candidate'?sequence:reference).init();groups.push(g);
  for(const d of contract.defs.filter(d=>d.index===i&&(kind==='before'?d.layer==='before':d.layer!=='before')))plane(d,g);
  const animator=animation.getBoneAnimator(g),key=(time,value)=>animator.addKeyframe({channel:'scale',time,interpolation:'step',data_points:[{x:String(value),y:String(value),z:String(value)}]});
  key(0,i===0?1:0);if(i)key(i*.1,1);key((i+1)*.1,0);
 }
 Undo.finishEdit('Build native eruption planes, UV and sixteen-frame comparison animation',{elements,outliner:true,animations:[animation]});
 contract.animation=animation.uuid;contract.group=sequence.uuid;contract.referenceGroup=reference.uuid;
 contract.layers=[{id:'flame_front',role:'upright magma eruption',size:[5,3.5],anchor:[32,58],plane:'XY',offset:[0,0,0],replacementFor:'eldencombat:vfx2d/flame_front'},{id:'ground_rupture',role:'terrain cracks and lifted crust',size:[5,4],anchor:[32,32],plane:'XZ',offset:[0,.015,0],newRouteRequired:true},{id:'eruption_debris',role:'six coarse flying crust pieces',size:[5,3.5],anchor:[32,58],plane:'XY',offset:[0,0,.03125],newRouteRequired:true}];
 contract.phases=[{frames:[0,3],name:'open ground and start eruption'},{frames:[4,9],name:'magma peaks and ground plates lift'},{frames:[10,12],name:'chunks descend and magma shrinks'},{frames:[13,15],name:'ember cracks and ash subside'}];
 Canvas.updateAll();animation.select();Modes.options.animate.select();Timeline.setTime(.6);Animator.preview();
 Codecs.project.write(Codecs.project.compile({bitmaps:true,history:true}),root+'takers-flames-eruption.bbmodel');
 Blockbench.writeFile(root+'native-authoring.json',{content:JSON.stringify({project:Project.uuid,version:Blockbench.version,elements:elements.length,textures:Texture.all.length,history:Undo.history.length,painted,contract},null,2)});
 return {saved:true,project:Project.uuid,painted:painted.length,textures:Texture.all.length,elements:elements.length,animations:Animation.all.length,history:Undo.history.length};
})()
