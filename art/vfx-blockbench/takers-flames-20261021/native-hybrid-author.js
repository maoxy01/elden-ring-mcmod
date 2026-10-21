(() => {
 const root='D:/codexout/mcmod/art/vfx-blockbench/takers-flames-20261021/hybrid/',c=Project.unhandled_root_fields.takers_hybrid_art,t=Texture.all.find(t=>t.uuid===c.palette);
 const palette=['#4b2026','#a52c24','#e65920','#fa982f','#ffe493','#342f32','#514a47','#817465','#63282b','#bf3b24','#f27525','#ffc958','#f9ecb5','#27282b','#443e3d','#695f56'];
 t.edit(canvas=>{const ctx=canvas.getContext('2d');for(let i=0;i<16;i++){ctx.fillStyle=palette[i];ctx.fillRect(i%4*8,Math.floor(i/4)*8,8,8);}for(const i of [5,6,7,13,14,15]){const x=i%4*8,y=Math.floor(i/4)*8;ctx.fillStyle=palette[i>=13?Math.max(13,i-1):Math.max(5,i-1)];ctx.fillRect(x+1,y+1,3,2);ctx.fillRect(x+4,y+5,3,2);}},{edit_name:'Paint native molten heat bands and coarse basalt tiles'});
 const elements=[],groups=[],animation=new Animation({name:'hybrid_eruption_preview',length:1.6,loop:'loop'}).add(false),schedule={vents:[],debris:[],plates:[]};
 Undo.initEdit({elements:[],outliner:true,animations:[animation]});
 const group=(name,origin)=>{const g=new Group({name,origin}).init();groups.push(g);return g;};
 function cube(name,g,from,to,material,rotation=[0,0,0]){
  const faces={};for(const f of ['north','south','east','west','up','down']){const m=typeof material==='number'?material:(material[f]??material.side),x=m%4*8,y=Math.floor(m/4)*8;faces[f]={uv:[x,y,x+8,y+8],texture:t.uuid};}
  const e=new Cube({name,from,to,origin:g.origin,rotation,box_uv:false,autouv:0,shade:false,faces}).addTo(g).init();elements.push(e);return e;
 }
 function key(g,channel,time,value,interpolation='linear'){animation.getBoneAnimator(g).addKeyframe({channel,time,interpolation,data_points:[{x:String(value[0]),y:String(value[1]),z:String(value[2])}]});}
 const vents=[[-31,-4,21,-9],[-16,3,35,7],[0,-2,47,-4],[16,4,33,9],[30,-5,25,-11]];
 for(let i=0;i<vents.length;i++){
  const [x,z,h,lean]=vents[i],g=group('lava_vent_'+i,[x,0,z]);
  const tiers=[[0,h*.42,9,10],[h*.34,h*.76,6,7],[h*.69,h,3,4]];
  for(let j=0;j<tiers.length;j++){const [bottom,top,w,depth]=tiers[j],shift=(j-1)*(i%2?1.2:-1.2),cx=x+shift;
   cube('molten_body_'+i+'_'+j,g,[cx-w/2,bottom,z-depth/2],[cx+w/2,top,z+depth/2],{side:1,north:2,south:2,up:3,down:0});
   cube('molten_core_'+i+'_'+j,g,[cx-w*.22,bottom+.3,z-depth/2-.15],[cx+w*.22,top-1,z+depth/2+.15],j<2?{side:3,north:4,south:4,up:3,down:2}:3);
  }
  const delay=i*.025;for(const [time,scale] of [[0,.02],[.15+delay,.25],[.4+delay,1],[.75+delay,.82],[1.1+delay,.4],[1.5,.03],[1.599,.001]])key(g,'scale',time,[1,scale,1]);
  for(const [time,angle] of [[0,0],[.4,lean],[.8,-lean*.35],[1.5,0]])key(g,'rotation',time,[i%2?5:-5,0,angle]);
  schedule.vents.push({name:g.name,origin:g.origin,maxHeightBlocks:h/16,modelElements:6});
 }
 const plates=[[-31,-15,8,8],[-22,12,10,7],[-10,-16,8,8],[4,15,9,8],[18,-15,9,9],[30,12,8,7],[-36,2,6,8],[36,-4,6,8]];
 for(let i=0;i<plates.length;i++){
  const [x,z,w,d]=plates[i],g=group('ground_plate_'+i,[x,0,z]);cube(g.name,g,[x-w/2,-1,z-d/2],[x+w/2,2,z+d/2],{side:5,up:7,down:13});
  key(g,'position',0,[0,-2,0]);key(g,'position',.35+i*.025,[0,1+i%3,0]);key(g,'position',.8,[0,2,0]);key(g,'position',1.35,[0,-1.5,0]);
  for(const [time,r] of [[0,0],[.45,(i%2?1:-1)*18],[.85,(i%2?1:-1)*10],[1.4,0]])key(g,'rotation',time,[r,0,r*.35]);
  schedule.plates.push({name:g.name,origin:g.origin});
 }
 for(let i=0;i<6;i++){
  const side=i<3?-1:1,x=side*(8+(i%3)*10),z=i%2?7:-8,g=group('flying_crust_'+i,[x,0,z]),w=i%2?5:4;
  cube(g.name,g,[x-w/2,0,z-2],[x+w/2,3+i%2,z+2],{side:6,up:7,down:9});
  const launch=.14+i*.025,apex=.52+i*.025,land=1.2+i*.025;
  for(const [time,pos] of [[0,[0,0,0]],[launch,[0,0,0]],[apex,[side*(6+i%3*2),18+i%3*5,(i%2?1:-1)*5]],[land,[side*(13+i%3*3),0,(i%2?1:-1)*9]],[1.59,[side*(13+i%3*3),-2,(i%2?1:-1)*9]]])key(g,'position',time,pos);
  for(const [time,angle] of [[0,0],[apex,80+20*i],[land,180+25*i]])key(g,'rotation',time,[angle,angle*.4,angle*.7]);
  key(g,'scale',0,[.01,.01,.01],'step');key(g,'scale',launch,[1,1,1],'step');key(g,'scale',1.48,[.01,.01,.01],'step');schedule.debris.push({name:g.name,origin:g.origin,launch,apex,land});
 }
 for(const d of c.ground){
  const g=group('ground_frame_'+String(d.index).padStart(2,'0'),[0,0,0]),faces={};for(const f of ['north','south','east','west','up','down'])faces[f]={uv:[0,0,64,64],texture:null};faces.up={uv:[0,0,64,64],texture:d.texture};
  elements.push(new Cube({name:g.name,from:[-40,.24,-32],to:[40,.24,32],box_uv:false,autouv:0,shade:false,faces}).addTo(g).init());
  key(g,'scale',0,d.index===0?[1,1,1]:[0,0,0],'step');if(d.index)key(g,'scale',d.index*.1,[1,1,1],'step');key(g,'scale',(d.index+1)*.1,[0,0,0],'step');
 }
 Undo.finishEdit('Model volumetric molten jets, lifted plates and flying crust with native animation',{elements,outliner:true,animations:[animation]});
 c.animation=animation.uuid;c.schedule=schedule;c.budget={solidBoxes:44,fireBoxes:30,groundPlates:8,flyingCrust:6,groundQuads:1,emberBurst:12,ashMaxAlive:6};c.particlePreviewFormat='Snowstorm/Bedrock authoring reference only; Java NeoForge needs client adaptation';c.sizeBlocks=[5,3.1,4];
 Canvas.updateAll();animation.select();Modes.options.animate.select();Timeline.setTime(.6);Animator.preview();
 Blockbench.writeFile(root+'textures/molten_and_basalt.png',{content:t.canvas.toDataURL('image/png'),savetype:'image'});
 Codecs.project.write(Codecs.project.compile({bitmaps:true,history:true}),root+'takers-flames-hybrid.bbmodel');
 Blockbench.writeFile(root+'authoring.json',{content:JSON.stringify({project:Project.uuid,version:Blockbench.version,elements:elements.length,textures:Texture.all.length,history:Undo.history.length,contract:c},null,2)});
 return {elements:elements.length,solidBoxes:44,textures:Texture.all.length,history:Undo.history.length,animation:animation.uuid};
})()
