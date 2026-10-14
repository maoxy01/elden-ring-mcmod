import { Vector3, Quaternion, Euler } from 'three';

export const palette={relic_metal:[125,119,92],relic_edge:[186,174,123],gold:[171,138,67],gold_light:[219,188,106],
  black_steel:[27,29,33],black_edge:[58,64,69],blood:[84,20,29],flesh:[133,55,47],
  scarlet:[196,42,50],bronze:[94,78,54],silver:[188,201,201],leather:[58,43,46],
  stone:[104,103,95],gold_rune:[241,195,69],curse:[118,76,123],iridescent:[88,130,151]};

export const previousCubeCounts={sacred_relic_sword:621,marikas_hammer:83,malikeths_black_blade:438,
  blasphemous_blade:494,hand_of_malenia:653,starscourge_greatsword:511,mohgwyns_sacred_spear:361,morgotts_cursed_sword:584};
export const textureResolution=2048;
const smooth=t=>{t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);};

export function createRemembrance({game=false,smoothGame=true}={}) {
const bakedLabels=new Set(['rib','tarnished_vein','organic_striation','tip_fuller','stone_hairline','stone_crystal',
  'guard_engraving','guard_flower','fractured_lamella','guard_inlay','blade_fissure','blade_crack',
  'finger_nail','blade_cinder','guard_relief','capillary','prosthetic_relief','grip_engraving','blade_engraving',
  'blade_script','prosthetic_rivet','polished_fuller','fine_leaf_script','prosthetic_emblem',
  'gravity_seal','gravity_seal_inner','seal_ray','gold_scroll','gravity_flower','filigree_vine','guard_medallion',
  'shaft_etch','ritual_etch','ritual_seal','prong_rune','shaft_glyph','iridescent_vein','cursed_crack',
  'guard_knot','blood_tributary','etched_edge','guard_scroll','eye_socket','face_nose','mouth','clavicle']);
function sculpt(id) {
  const cubes=[],surfaces=[];let serial=0;
  const record=(label,first,spec)=> {
    if(game&&smoothGame&&cubes.length>first)surfaces.push({name:`${label}_surface_${surfaces.length}`,
      sourceCubes:cubes.slice(first).map(c=>c.name),...spec});
  };
  const box=(label,from,to,material,rotation=[0,0,0],pivot=from)=> {
    if(game&&bakedLabels.has(label))return;
    cubes.push({name:`${label}_${serial++}`,bone:'root',from,to,material,rotation,pivot});
  };
  const rod=(label,a,b,width,depth,material)=> {
    if(game&&bakedLabels.has(label))return;
    const direction=new Vector3(...b).sub(new Vector3(...a)),length=direction.length();
    const q=new Quaternion().setFromUnitVectors(new Vector3(0,1,0),direction.normalize());
    const e=new Euler().setFromQuaternion(q,'XYZ');
    const rotation=[e.x,e.y,e.z].map(v=>v*180/Math.PI);
    // A five-strip chamfered cross-section replaces the old rectangular extrusions.
    const strips=game?1:5;
    for(let j=0;j<strips;j++) {
      const z0=-depth/2+j*depth/strips,z1=z0+depth/strips,half=width/2*(game?1:[.55,.88,1,.88,.55][j]);
      box(label,[a[0]-half,a[1],a[2]+z0],[a[0]+half,a[1]+length,a[2]+z1],material,rotation,a);
    }
  };
  const path=(label,points,width,depth,material)=> {
    if(game&&bakedLabels.has(label))return;
    const first=cubes.length;
    let indices=points.map((_,i)=>i);
    if(game&&points.length>6) {
      const keep=new Set([0,points.length-1]),tolerance=label==='twisted_blade'?.025:.075;
      const split=(a,b)=> {
        const start=new Vector3(...points[a]),end=new Vector3(...points[b]),line=end.clone().sub(start);
        let max=0,index=Math.floor((a+b)/2);
        for(let i=a+1;i<b;i++) {
          const p=new Vector3(...points[i]),t=Math.max(0,Math.min(1,p.clone().sub(start).dot(line)/(line.lengthSq()||1)));
          const error=p.distanceTo(start.clone().addScaledVector(line,t));
          if(error>max){max=error;index=i;}
        }
        if(b-a>1&&(max>tolerance||line.length()>1)) {keep.add(index);split(a,index);split(index,b);}
      };
      split(0,points.length-1);indices=[...keep].sort((a,b)=>a-b);
    }
    for(let n=1;n<indices.length;n++) {
      const i=indices[n];rod(label,points[indices[n-1]],points[i],
      typeof width==='function'?width(i/points.length):width,typeof depth==='function'?depth(i/points.length):depth,material);
    }
    record(label,first,{kind:'sweep',material,sections:indices.map(i=>({center:points[i],
      halfWidth:(typeof width==='function'?width(i/points.length):width)/2,
      halfDepth:(typeof depth==='function'?depth(i/points.length):depth)/2}))});
  };
  const loft=(label,knots,material)=> {
    const first=cubes.length,sections=[];
    for(let k=1;k<knots.length;k++) {
      const a=knots[k-1],b=knots[k];
      const step=game?.2:.06,strips=game?3:5;
      for(let y=a[0];y<b[0]-(game?1e-8:0);y+=step) {
        if(game) {
          const t=smooth((y-a[0])/(b[0]-a[0]));
          sections.push({center:[0,y,0],halfWidth:(a[1]+(b[1]-a[1])*t)/2,
            halfDepth:(a[2]+(b[2]-a[2])*t)/2});
        }
        const next=Math.min(b[0],y+step),t=smooth(((y+next)/2-a[0])/(b[0]-a[0]));
        const w=(a[1]+(b[1]-a[1])*t)/2,d=(a[2]+(b[2]-a[2])*t)/2;
        for(let j=0;j<strips;j++) {
          const z0=-d+j*2*d/strips,z1=z0+2*d/strips,h=w*(game?[.7,1,.7][j]:[.6,.9,1,.9,.6][j]);
          box(label,[-h,y,z0],[h,next,z1],material);
        }
      }
    }
    const last=knots.at(-1);
    sections.push({center:[0,last[0],0],halfWidth:last[1]/2,halfDepth:last[2]/2});
    record(label,first,{kind:'loft',material,sections});
  };
  const mount=(bladeBase,shoulderWidth,bladeDepth,metal,collar)=> {
    loft('structural_tang',[[1.65,1.12,1.04],[2.2,1.4,1.08],[bladeBase+.35,shoulderWidth,bladeDepth]],metal);
    loft('guard_socket',[[1.8,1.42,1.4],[2.1,1.46,1.4],[2.6,shoulderWidth+.2,bladeDepth+.22],
      [bladeBase+.2,shoulderWidth+.25,bladeDepth+.22]],collar);
  };
  const sphere=(label,center,radius,material,step=.35)=> {
    if(game&&bakedLabels.has(label))return;
    const first=cubes.length;
    step=game?radius*2/3:step*.5;
    for(let y=-radius;y<radius-(game?1e-8:0);y+=step) {
      const next=Math.min(radius,y+step),r=Math.sqrt(Math.max(.01,radius*radius-((y+next)/2)**2));
      const strips=game?3:9;
      for(let j=0;j<strips;j++) {
        const z0=-r+j*2*r/strips,z1=z0+2*r/strips,x=Math.sqrt(r*r-((z0+z1)/2)**2);
        box(label,[center[0]-x,center[1]+y,center[2]+z0],[center[0]+x,center[1]+next,center[2]+z1],material);
      }
    }
    record(label,first,{kind:'ellipsoid',material,center,radius});
  };
  const grip=(bottom=-6,top=2,material='leather',wrap='gold')=> {
    const first=cubes.length;
    rod('grip',[0,bottom,0],[0,top,0],1.38,1.38,material);
    record('grip',first,{kind:'cylinder',material,sections:[bottom,top].map(y=>({center:[0,y,0],halfWidth:.69,halfDepth:.69}))});
    const turns=(top-bottom)/.65,steps=Math.ceil(turns*14);
    path('spiral_binding',Array.from({length:steps+1},(_,i)=> {
      const a=i/steps*turns*Math.PI*2;
      const radius=game&&smoothGame?.705:.74;
      return [Math.cos(a)*radius,bottom+.15+i/steps*(top-bottom-.3),Math.sin(a)*radius];
    }),.11,.11,wrap);
    for(const y of [bottom,top-.3]) {
      ring('ferrule',[0,y,0],game?.77:.84,24,'gold','xz',.26);
      ring('ferrule_lip',[0,y+(game?.11:.2),0],game?.8:.86,24,'gold_light','xz',game?.1:.08);
    }
  };
  const ring=(label,center,radius,segments,material,plane='xy',thickness=.22)=> {
    if(game&&bakedLabels.has(label))return;
    if(game)segments=Math.min(segments,10);
    const points=Array.from({length:segments+1},(_,i)=> {
      const a=i*Math.PI*2/segments;
      return plane==='xy'?[center[0]+Math.sin(a)*radius,center[1]+Math.cos(a)*radius,center[2]]:
        [center[0]+Math.sin(a)*radius,center[1],center[2]+Math.cos(a)*radius];
    });
    path(label,points,thickness,thickness,material);
  };
  const blade=(start,end,center,width,thick,material,edge='relic_edge',step=.45,minimumHalf=.055)=> {
    const first=cubes.length,sections=[];
    step=game?Math.max(.32,step*2):step*.5;
    const section=y=> {
      const t=(y-start)/(end-start);
      return {center:[center(t),y,0],halfWidth:Math.max(minimumHalf,width(t)/2),halfDepth:thick(t),
        material:typeof material==='function'?material(t):material};
    };
    for(let y=start;y<end-(game?1e-8:0);y+=step) {
      if(game)sections.push(section(y));
      const next=Math.min(end,y+step),t=((y+next)/2-start)/(end-start),x=center(t),half=Math.max(minimumHalf,width(t)/2),d=thick(t);
      const metal=typeof material==='function'?material(t):material;
      const strips=game?3:5;
      for(let j=0;j<strips;j++) {
        const x0=x-half+j*2*half/strips,x1=x0+2*half/strips,z=d*(game?[.45,1,.45][j]:[.35,.73,1,.73,.35][j]);
        box('blade_bevel',[x0,y,-z],[x1,next,z],metal);
      }
      const e=Math.min(.13,half*.4);
      for(const side of [-1,1])box('blade_edge',[x+side*half-e/2,y,-d*.52],[x+side*half+e/2,next,d*.52],edge);
      if(game)rod('blade_core_bridge',[center((y-start)/(end-start)),y,0],
        [center((next-start)/(end-start)),next,0],Math.max(.025,Math.min(.1,half)),Math.min(.08,d),metal);
    }
    sections.push(section(end));
    record('blade',first,{kind:'blade',material:sections[0].material,edge,sections});
    return (x,y)=> {
      const t=Math.max(0,Math.min(1,(y-start)/(end-start))),h=Math.max(minimumHalf,width(t)/2);
      const strips=game?3:5,band=Math.max(0,Math.min(strips-1,Math.floor((x-center(t)+h)/(2*h)*strips)));
      if(game&&smoothGame)return thick(t)*(1-.55*Math.max(0,(Math.abs(x-center(t))/h-1/3)/(2/3)))+.025;
      return thick(t)*(game?[.45,1,.45][band]:[.35,.73,1,.73,.35][band])+.025;
    };
  };
  const rosette=(label,center,radius,material,petals=6)=> {
    if(game&&bakedLabels.has(label))return;
    ring(label,center,radius,48,material,'xy',.065);
    for(let p=0;p<petals;p++) {
      const a=p*Math.PI*2/petals;
      path(label,Array.from({length:17},(_,j)=> {
        const t=j/16,angle=a+Math.sin(t*Math.PI)*.42,r=radius*(.12+.8*Math.sin(t*Math.PI));
        return [center[0]+Math.sin(angle)*r,center[1]+Math.cos(angle)*r,center[2]];
      }),.055,.05,material);
    }
  };
  return {box,rod,path,sphere,grip,ring,blade,rosette,loft,mount,record,cubes,model:{schemaVersion:1,id:`eldencombat:${id}`,unitsPerBlock:16,
    textureSize:[textureResolution,textureResolution],texture:'weapons.png',rotationOrder:'XYZ',animationRotation:'additive_degrees',batchPreview:true,
    ...(game&&smoothGame?{smoothSurfaces:surfaces}:{}),
    ...(game&&!smoothGame?{pixelConsolidation:{blockSize:2,method:'2x2 source texels become one constant-color macro pixel; no blur'},geometryStyle:'cuboid hard edges'}:{}),
    refinement:game?{revision:smoothGame?2:3,profile:'game',sourceRevision:3,maximumCubes:1200,
      method:smoothGame?'continuous chamfered blade lofts, connected sweeps and smooth ellipsoids; cuboid compatibility fallback':
        'cuboid geometry with hard face normals and tile-local 2x2 macro pixels; no smooth surfaces'}:
      {revision:3,previousCubes:previousCubeCounts[id],minimumGeometryMultiplier:4},
    bones:[{name:'root',parent:null,pivot:[0,0,0],rotation:[0,0,0]}],cubes}};
}

const entries=[];
function finish(s,name,english,type,art,features,extra={}) {
  s.model.structuralChecks=[{id:'grip_to_blade_or_shaft',from:[0,1.5,0],to:[0,5.1,0],step:.025,radius:.1}];
  if(s.model.id.endsWith(':sacred_relic_sword')) {
    s.model.structuralChecks.push({id:'twist_to_tip',from:[0,24,0],to:[0,38,0],step:.025,radius:.025});
    s.model.profileChecks=[{id:'twist_tip_envelope',fromY:24,toY:36,step:.1,maxDelta:.075}];
  }
  entries.push({id:s.model.id.split(':')[1],name,english,group:type,proposedPreset:type==='刀'?'epicfight:uchigatana':
    type==='大矛'?'epicfight:spear':type==='槌'?'custom:blunt_1h':type==='特大剑'?'epicfight:greatsword':'custom:remembrance_sword',
    proposedArt:art,features,model:s.model,secondaryGrip:type==='大矛'?[0,-7,0]:type==='槌'?null:[0,-3.5,0],
    remembrance:true,detailTarget:'2048px atlas; chamfered cross-sections; sculpted relief and independently authored silhouette',...extra});
}

{
  const s=sculpt('sacred_relic_sword');
  s.grip(-5,1,'relic_metal','bronze');s.sphere('human_pommel',[0,-5.9,0],.9,'relic_metal',.22);
  s.rod('pommel_stem',[0,-6,0],[0,-4.7,0],.45,.45,'relic_metal');
  const torsoStep=game?.3:.09;
  for(let y=1;y<3.6;y+=torsoStep) {
    const t=(y-1)/2.6,w=.61-.13*Math.sin(t*Math.PI),d=.34+.13*Math.sin(t*Math.PI);
    for(let j=0;j<5;j++) {
      const z0=-d+j*2*d/5,z1=z0+2*d/5,half=w*[.6,.9,1,.9,.6][j];
      s.box('human_torso',[-half,y,z0],[half,Math.min(3.6,y+torsoStep),z1],'relic_metal');
    }
  }
  for(const sign of [-1,1]) {
    s.path('arm_guard',[[0,3.6,0],[sign*1.2,3.2,0],[sign*3.7,5,0],[sign*4.5,6.8,0]],.46,.5,'relic_metal');
    s.path('guard_edge',[[sign*.7,3.3,game?-.2:-.35],[sign*2.6,4.3,game?-.2:-.35],[sign*4.3,6.5,-.25]],.14,.14,'relic_edge');
    for(const side of [-1,1])for(let y=1.6;y<3.1;y+=.26)
      s.path('rib',[[0,y,side*.48],[sign*.2,y+.1,side*.48],[sign*.46,y+.16,side*.4]],.055,.055,'bronze');
  }
  const fade=t=>smooth((4.5+t*29.5-20)/14);
  const strandPoint=(t,strand)=> {
    const y=4.5+t*29.5,a=t*Math.PI*5.6+strand*Math.PI,r=(1.05-t*.4)*(1-fade(t));
    return [Math.cos(a)*r,y,Math.sin(a)*r*.42];
  };
  const coreWidth=y=> {
    if(y<=30)return 1.04-(y-4.5)*.016;
    const t=(y-30)/14;
    return (2*t**3-3*t*t+1)*.632+(t**3-2*t*t+t)*(-.224)+(-2*t**3+3*t*t)*.024+(t**3-t*t)*(-.7);
  };
  const coreDepth=y=>.18-(y-4.5)/39.5*.15;
  // Both strands fade into the same continuous core; there is no separately attached tip.
  for(let strand=0;strand<2;strand++) {
    const points=Array.from({length:149},(_,i)=>strandPoint(i/148,strand));
    s.path('twisted_blade',points,t=>.68*(1-t*.45)*(1-fade(t))+.035,
      t=>.48*(1-fade(t))+.045,strand?'relic_edge':'relic_metal');
    for(let i=2;i<points.length-2;i+=4) {
      const p=points[i],q=points[i+1];
      const d=.24*(1-fade(i/148))+.035;
      s.rod('tarnished_vein',[p[0],p[1],p[2]-d],[q[0],q[1],q[2]-d],.045,.045,'bronze');
    }
  }
  s.blade(4.5,44,()=>0,t=>coreWidth(4.5+t*39.5),t=>coreDepth(4.5+t*39.5),'relic_metal','relic_edge',.18,.008);
  s.path('blade_spine',Array.from({length:90},(_,i)=> {
    const y=24+i/89*19.5;return [0,y,-coreDepth(y)-.01];
  }),t=>.06*(1-t)+.018,.025,'relic_edge');
  s.sphere('human_head',[0,4,.16],.64,'relic_metal',.15);
  s.rod('neck',[0,3.25,0],[0,5.05,0],.56,.58,'relic_metal');
  for(const sign of [-1,1]) {
    s.box('eye_socket',[sign*.25-.09,4.13,.74],[sign*.25+.09,4.21,.8],'bronze');
    s.path('clavicle',[[0,3.27,.48],[sign*.3,3.4,.48],[sign*.6,3.28,.36]],.07,.065,'relic_edge');
    for(let f=0;f<5;f++)s.path('guard_finger',[[sign*4.15,6.25,0],[sign*(4.4+f*.11),6.6+f*.17,-.05],
      [sign*(4.35+f*.12),6.95+f*.15,-.09]],.075,.09,'relic_edge');
    for(let i=0;i<38;i++) {
      const y=5+i/38*24,t=(y-4.5)/29.5,[x,,z]=strandPoint(t,sign===1?1:0),d=.24*(1-fade(t))+.03;
      s.path('organic_striation',[[x-.1,y,z-d],[x-.025,y+.13,z-d-.02],[x+.07,y+.25,z-d]],.035,.03,'bronze');
    }
  }
  s.path('face_nose',[[0,4.3,.79],[0,4.03,.88],[.08,3.96,.77]],.1,.08,'relic_edge');
  s.path('mouth',[[-.18,3.84,.75],[0,3.82,.79],[.18,3.84,.75]],.055,.05,'bronze');
  for(const side of [-1,1])s.path('tip_fuller',Array.from({length:65},(_,i)=> {
    const t=i/64,y=28+t*15;return [side*coreWidth(y)*.12,y,side*(coreDepth(y)+.015)];
  }),.04,.035,'bronze');
  for(const sign of [-1,1])s.path('blade_root_shoulder',[[0,3.45,0],[sign*.55,4.05,0],[sign*1.05,4.8,0]],
    t=>.92-.2*t,.62,sign<0?'relic_edge':'relic_metal');
  finish(s,'神躯化剑','Sacred Relic Sword','大剑','wave_of_gold',['多层倒角扭转刃体','人形面部、肋骨与五指护手','螺旋缠柄','旧化细脊线']);
  s.model.rootRepair={revision:1,method:'two broad attached blade-root shoulders into torso, not a thin supporting post'};
  for(const sign of [-1,1])for(const [a,b] of [[[0,3.5,0],[sign*.55,4.05,0]],[[sign*.55,4.05,0],[sign*1.02,4.7,0]]])
    s.model.structuralChecks.push({id:`blade_root_${sign}_${a[1]}`,from:a,to:b,step:.025,radius:.04});
}
{
  const s=sculpt('marikas_hammer');s.grip(-5,2,'bronze','relic_edge');
  s.path('uneven_haft',[[0,2,0],[-.2,6,.1],[.25,10,-.1],[0,15,0],[.4,19,0]],1.25,1.3,'stone');
  for(let i=0;i<10;i++)s.box('haft_knob',[-.9+(i%2)*.3,2+i*1.4,-.7],[.5+(i%3)*.2,2.5+i*1.4,.7],'stone',[0,0,i%2?8:-8]);
  const stoneLayers=game?14:56,stoneBands=game?3:7,stoneStep=game?6.3/stoneLayers:.1125;
  const firstStone=s.cubes.length,stoneSections=[];
  for(let layer=0;layer<stoneLayers;layer++) {
    const old=game?layer*14/stoneLayers:layer/4,y=16+layer*stoneStep,w=old<3?4.5+old*.5:old>10?5.4-(old-10)*.6:6.3;
    for(let j=0;j<stoneBands;j++) {
      const z0=-2.6+j*5.2/stoneBands,z1=z0+5.2/stoneBands,chamfer=Math.abs((j-(stoneBands-1)/2)/((stoneBands-1)/2))**3*.65;
      const notch=.14*Math.sin(layer*1.8+j*2.6);
      s.box('chamfered_stone',[-w+chamfer+notch,y,z0],[w-chamfer-.25*(1+Math.sin(layer*.28))+notch,y+stoneStep,z1],'stone');
    }
    if(layer%5===0)s.box('broken_face',[w-.9,y,-2.8],[w+.1,y+.28,1.3],'bronze',[0,0,-7]);
    if(game) {
      const row=s.cubes.slice(firstStone).filter(c=>c.name.startsWith('chamfered_stone')&&Math.abs(c.from[1]-y)<1e-6);
      const left=Math.min(...row.map(c=>c.from[0])),right=Math.max(...row.map(c=>c.to[0]));
      stoneSections.push({center:[(left+right)/2,y+stoneStep/2,0],halfWidth:(right-left)/2,halfDepth:2.6});
    }
  }
  if(game) {
    stoneSections.unshift({...stoneSections[0],center:[stoneSections[0].center[0],16,0]});
    stoneSections.push({...stoneSections.at(-1),center:[stoneSections.at(-1).center[0],22.3,0]});
    s.record('stone_head',firstStone,{kind:'loft',profile:'stone',material:'stone',sections:stoneSections,
      sourceCubes:s.cubes.slice(firstStone).filter(c=>c.name.startsWith('chamfered_stone')).map(c=>c.name)});
  }
  for(const z of game?[-2.57,2.57]:[-2.72,2.72]) {
    s.path('rune_fracture',[[-4,16.7,z],[-1.7,18.1,z],[-2.2,19.3,z],[.3,20.2,z],[2.3,19.9,z],[4.5,21.3,z]],.18,.12,'gold_rune');
    s.path('rune_branch',[[-1.7,18.1,z],[.4,17.1,z],[2.5,17.5,z]],.15,.12,'gold_rune');
    s.path('rune_branch',[[.3,20.2,z],[-.5,21.6,z],[-1.8,22.4,z]],.2,.12,'gold_rune');
  }
  for(let i=0;i<7;i++) {
    const x=(-5.4+i*1.65)*(game?.8:1);
    s.rod('embedded_shard',[x,21.8,-.5],[x+(i%2?.8:-.5),23.4+(i%3)*.45,-.6],.32,.4,'gold_rune');
  }
  for(const sign of [-1,1])for(let i=0;i<28;i++) {
    const x=-4.4+(i%7)*1.4,y=16.8+Math.floor(i/7)*1.25,z=sign*2.63;
    s.path('stone_hairline',[[x,y,z],[x+.26,y+.15,z],[x+.52,y+.03,z],[x+.62,y+.34,z]],.035,.035,'bronze');
    s.box('stone_crystal',[x,y+.5,z-.07],[x+.13,y+.65,z+.07],i%4?'stone':'gold_light',[0,0,i*17],[x,y+.5,z]);
  }
  s.ring('head_socket',[0,16,0],1.05,32,'bronze','xz',.3);
  finish(s,'玛莉卡的石槌',"Marika's Hammer",'槌','gold_breaker',['多面破损石质槌头','细裂纹与矿物晶粒','嵌入的黄金碎片','螺旋缠柄与箍环']);
}
{
  const s=sculpt('malikeths_black_blade');s.grip(-7,2,'black_steel','gold');
  s.mount(2.9,4.15,1.28,'black_steel','gold');
  s.sphere('pommel',[0,-8,0],1.25,'gold',.3);
  s.path('guard',[[ -7,4.3,0],[-5,3.1,0],[-2,2.8,0],[0,3.5,0],[2,2.8,0],[5,3.1,0],[7,4.3,0]],.85,1.3,'gold');
  for(const sign of [-1,1]) {
    s.path('guard_scroll',[[sign*1.6,3.1,-.8],[sign*2.1,4.4,-.8],[sign*3.5,4.5,-.8],[sign*4.1,3.8,-.8],[sign*3.4,3.3,-.8]],.23,.18,'gold_light');
    s.ring('guard_engraving',[sign*5,3.5,-.7],.48,10,'bronze');
  }
  s.blade(2.9,47,t=>Math.sin(t*2.2)*1.1,t=>(7.1*(1-t*.58)+.6*Math.sin(t*51))*(.585+.415*smooth(t*44.1/1.5))*(t>.82?(1-t)/.18:1),
    t=>.64*(1-t*.6),'black_steel','black_edge',.4);
  for(let i=0;i<17;i++) {
    const y=6+i*2.1,x=game?Math.sin((y-2.9)/44.1*2.2)*1.1:1+Math.sin(i*.8)*1.2;
    s.box('broken_shard',[x-1.2,y,-.85],[x+1.2,y+2.5,.75],i%3?'black_steel':'black_edge',[0,0,(i%3-1)*9],[x,y,0]);
  }
  for(const z of [-.72,.72]) {
    s.path('blade_fissure',[[-1,5,z],[.4,11,z],[-.1,17,z],[1.5,23,z],[.5,30,z],[1.4,36,z],[1.2,42,z]],.13,.1,'blood');
    for(let i=0;i<7;i++)s.rod('blade_crack',[.4,10+i*4,z],[i%2?-.8:1.9,12+i*4,z],.12,.1,'blood');
  }
  for(const side of [-1,1]) {
    s.rosette('guard_flower',[side*2.5,3.55,-.84],.65,'bronze',5);
    for(let i=0;i<35;i++) {
      const y=6+i*1.02,t=(y-4.5)/42.5,x=Math.sin(t*2.2)*1.1,depth=.64*(1-t*.6);
      s.path('fractured_lamella',[[x-.9,y,-depth-.015],[x-.3,y+.2,-depth-.04],[x+.7,y+.08,-depth-.015]],.045,.035,'black_edge');
    }
    s.path('guard_inlay',Array.from({length:38},(_,i)=> {
      const t=i/37;return [side*(.7+5.8*t),3.25+Math.sin(t*7)*.24,-.69];
    }),.055,.05,'bronze');
  }
  finish(s,'玛利喀斯的黑剑',"Maliketh's Black Blade",'特大剑','destined_death',['层叠碎刃与多面倒角','破裂纹与细层理','金色花纹浮雕护手','暗红裂隙（非开启特效）']);
}
{
  const s=sculpt('blasphemous_blade');s.grip(-5.5,2,'blood','bronze');s.sphere('pommel',[0,-6.5,0],1,'bronze',.25);
  s.mount(2.9,2.2,.96,'blood','bronze');
  s.path('guard',[[-5,4,0],[-4,2.7,0],[0,3,0],[4,2.7,0],[5,4,0]],.65,.8,'bronze');
  const bloodFace=s.blade(2.9,37,t=>.12*Math.sin(t*6),t=>3.3*(2/3+smooth(t*34.1/1.2)/3)*(t>.86?(1-t)/.14:1),t=>.48*(1-t*.4),'blood','bronze');
  for(const sign of [-1,1]) {
    s.path('sinew',Array.from({length:50},(_,i)=> {
      const x=Math.sin(i*.55+sign)*1.2,y=4+i*.62;
      return [x,y,sign*(game?(.8+bloodFace(x,y)-.08)/2:.65)];
    }),.32,game?t=> {
      const i=t*50,x=Math.sin(i*.55+sign)*1.2,y=4+i*.62;
      return .8-bloodFace(x,y)+.08;
    }:.3,'flesh');
    for(let i=0;i<12;i++) {
      const y=5+i*2.4,x=Math.sin(i*2.6)*1.4,z=sign*(game?bloodFace(x,y)-.1:.7),dir=i%2?1:-1;
      const outerZ=sign*.7;
      s.path('attached_arm',[[x,y,z],[x+dir*.8,y+.5,outerZ+.2*sign],[x+dir*.6,y+1.3,outerZ+.3*sign]],.34,.33,'flesh');
      s.sphere('attached_palm',[x+dir*.6,y+1.25,outerZ+.3*sign],.23,'flesh',.16);
      for(let f=0;f<5;f++) {
        const start=[x+dir*.6,y+1.2,outerZ+.3*sign],middle=[x+dir*(.75+f*.13),y+1.5-f*.08,outerZ+.36*sign];
        s.path('attached_finger',[start,middle,[middle[0]+dir*.14,middle[1]+.19,middle[2]-.05*sign]],.09,.095,'flesh');
        s.rod('finger_nail',[middle[0]+dir*.1,middle[1]+.11,middle[2]-.01*sign],[middle[0]+dir*.14,middle[1]+.18,middle[2]-.01*sign],.04,.035,'bronze');
      }
    }
  }
  for(let i=0;i<14;i++)s.rod('blade_cinder',[-.5+(i%3)*.4,5+i*2.1,-.88],[.3+(i%2)*.5,5.8+i*2.1,-.88],.1,.08,'scarlet');
  for(const side of [-1,1]) {
    s.rosette('guard_relief',[side*2.4,3.05,-.47],.46,'gold',5);
    for(let i=0;i<28;i++) {
      const y=5+i*1.05,x=Math.sin(i*.93)*.9,z=side*(.48*(1-(y-3.4)/33.6*.4)+.1);
      s.path('capillary',[[x,y,z],[x+.18,y+.12,z+.03*side],[x-.05,y+.42,z],[x+.3,y+.7,z]],.035,.04,'scarlet');
    }
  }
  finish(s,'亵渎圣剑','Blasphemous Blade','大剑','takers_flames',['多层血色刃体','细血管与缠绕筋膜','掌部、五指与指甲','雕花旧铜护手']);
}
{
  const s=sculpt('hand_of_malenia');s.grip(-8,2,'gold','gold_light');
  s.box('prosthetic_cuff',[-1.1,-8.6,-.9],[1.1,-7.4,.9],'gold');
  for(const side of [-1,1])s.box('prosthetic_shell',[-.62,-7.6,side>0?.65:-.8],[.62,1.7,side>0?.8:-.65],'gold');
  for(const z of [-.81,.81]) {
    s.path('prosthetic_relief',[[-.7,-7.8,z],[-.4,-4.5,z],[.4,-1,z],[.6,1.5,z]],.12,.1,'bronze');
    for(let y=-7;y<1;y+=1.2)s.rod('grip_engraving',[-.6,y,z],[.6,y+.55,z],.1,.08,'gold_light');
  }
  const face=s.blade(2,44,t=>4.5*t*t,t=>1.65*(t>.91?(1-t)/.09:1),t=>.24*(1-t*.4),'gold','gold_light',.35);
  for(const z of [-.27,.27]) {
    const points=Array.from({length:70},(_,i)=> {const y=3+i/69*36,t=(y-2)/42,x=4.5*t*t-.42;return [x,y,Math.sign(z)*face(x,y)];});
    s.path('blade_engraving',points,.07,.06,'bronze');
    for(let i=0;i<25;i++) {
      const t=i/25,y=5+t*30,x=4.5*((y-2)/42)**2;
      s.path('blade_script',[[x-.22,y,z],[x+.15,y+.24,z],[x-.06,y+.47,z]].map(([x,y,z])=>[x,y,Math.sign(z)*face(x,y)]),.06,.055,'bronze');
    }
  }
  s.box('blade_mount',[-.86,1.7,-.42],[.86,3.8,.42],'gold_light');
  for(const side of [-1,1]) {
    for(let i=0;i<8;i++)s.sphere('prosthetic_rivet',[side*.52,-7+i*1.1,-.8],.075,'bronze',.06);
    s.path('polished_fuller',Array.from({length:120},(_,i)=> {
      const t=i/119,y=3+t*38,u=(y-2)/42;
      const x=4.5*u*u+.13;return [x,y,side*face(x,y)];
    }),.045,.035,'gold_light');
    for(let i=0;i<35;i++) {
      const y=4+i*1.04,t=(y-2)/42,x=4.5*t*t,z=side*(.24*(1-t*.4)+.025);
      s.path('fine_leaf_script',[[x-.16,y,z],[x+.03,y+.14,z],[x-.09,y+.32,z],[x+.16,y+.43,z]].map(([x,y])=>[x,y,side*face(x,y)]),.035,.03,'bronze');
    }
  }
  s.rosette('prosthetic_emblem',[0,-7.95,-.96],.53,'bronze',7);
  finish(s,'玛莲妮亚的义手刀','Hand of Malenia','刀','waterfowl_dance',['五层倒角长弧刃','义手铆钉与浮雕','连续刃槽与叶形细刻纹','细螺旋金属缠柄']);
}
{
  const s=sculpt('starscourge_greatsword');s.grip(-7.5,2,'black_steel','gold');s.sphere('pommel',[0,-8.4,0],1.2,'gold',.3);
  s.mount(2.9,4,1.32,'black_steel','gold');
  s.path('guard',[[-5,3.8,0],[-3,2.6,0],[0,3,0],[3,2.6,0],[5,3.8,0]],.9,1.3,'gold');
  const face=s.blade(2.9,39,t=>1.1*t+2.6*t*t,t=>6.4*(.625+.375*smooth(t*36.1/1.3))*(t>.81?(1-t)/.19:1),t=>.66*(1-t*.4),'black_steel','black_edge',.4);
  for(const z of [-.71,.71]) {
    const center=[.5,12,z];
    s.ring('gravity_seal',center,2,24,'gold', 'xy',.16);
    s.ring('gravity_seal_inner',center,1.3,18,'gold','xy',.14);
    for(let i=0;i<8;i++) {
      const a=i*Math.PI/4;
      s.rod('seal_ray',[center[0]+Math.sin(a)*1.35,12+Math.cos(a)*1.35,z],
        [center[0]+Math.sin(a)*2.4,12+Math.cos(a)*2.4,z],.14,.1,'gold');
    }
    for(let i=0;i<13;i++) {
      const y=5+i*2.2,t=(y-4)/35,x=1.1*t+2.6*t*t;
      s.path('gold_scroll',[[x-1.6,y,z],[x-.8,y+.8,z],[x,y+.3,z],[x+.8,y+.8,z],[x+1.6,y,z]].map(([x,y,z])=>[x,y,Math.sign(z)*face(x,y)]),.12,.1,'gold');
    }
  }
  for(const side of [-1,1]) {
    s.rosette('gravity_flower',[.5,12,side*.76],1.08,'gold_light',8);
    for(let i=0;i<18;i++) {
      const y=6+i*1.6,t=(y-4)/35,x=1.1*t+2.6*t*t,z=side*(.66*(1-t*.4)+.03);
      for(const flank of [-1,1])s.path('filigree_vine',Array.from({length:15},(_,j)=> {
        const u=j/14,xx=x+flank*(.65+.35*Math.sin(u*Math.PI*2)),yy=y+u*1.3;return [xx,yy,side*face(xx,yy)];
      }),.05,.04,'gold');
    }
    s.rosette('guard_medallion',[0,3.2,side*.73],.56,'bronze',6);
  }
  finish(s,'碎星大剑','Starscourge Greatsword','特大剑','starcaller_cry',['五层倒角黑色宽刃','黄金花纹、卷草与重力徽章','立体缠柄与雕花护手','双持独立手部插槽'],{pairedPreview:true,paired:true});
}
{
  const s=sculpt('mohgwyns_sacred_spear');s.grip(-8,3,'bronze','gold');
  s.box('shaft',[-.58,-14,-.58],[.58,31,.58],'bronze');
  for(let y=-13;y<30;y+=2.8) {
    s.ring('shaft_collar',[0,y,0],game?.64:.68,8,'gold','xz',.16);
    s.rod('shaft_etch',[ -.22,y,-.65],[.22,y+1.6,-.65],.08,.07,'gold_light');
  }
  s.path('trident_base',[[-4.5,32,0],[-2.7,28.5,0],[0,30.5,0],[2.7,28.5,0],[4.5,32,0]],.7,.8,'gold');
  for(const sign of [-1,1]) {
    s.path('outer_prong',[[sign*2.7,29,0],[sign*4.7,33,0],[sign*4.1,38,0],[sign*3.4,42,0]],t=>.7*(1-t*.6),.65,'gold');
    s.path('prong_edge',[[sign*4.7,33,game?-.3:-.4],[sign*4.1,38,game?-.3:-.4],[sign*3.4,42,-.2]],.16,.1,'gold_light');
    s.rod('trident_barb',[sign*4.4,35,0],[sign*5.7,37.2,0],.34,.34,'gold');
  }
  s.blade(29,46,()=>0,t=>1.4*(1-t)+.08,t=>.4*(1-t)+.06,'gold','gold_light',.35);
  for(let i=0;i<10;i++)s.rod('ritual_etch',[-.45,30+i*1.2,-.42],[.45,30.6+i*1.2,-.42],.08,.065,'bronze');
  s.sphere('ritual_gem',[0,29.5,-.75],.6,'blood',.2);
  for(const side of [-1,1]) {
    s.rosette('ritual_seal',[0,29.5,side*.86],.78,'gold_light',6);
    for(let i=0;i<15;i++) {
      const y=31+i*.75;
      s.path('prong_rune',[[-.17,y,side*.36],[.14,y+.18,side*.36],[-.05,y+.34,side*.36]],.035,.035,'bronze');
    }
    for(let i=0;i<25;i++) {
      const y=-12+i*1.58;
      s.path('shaft_glyph',[[-.23,y,side*.61],[0,y+.18,side*.64],[.23,y,side*.61],[0,y+.38,side*.64]],.045,.035,'gold_light');
    }
  }
  s.ring('gem_setting',[0,29.5,-.82],.67,48,'bronze','xy',.13);
  finish(s,'蒙格温圣矛',"Mohgwyn's Sacred Spear",'大矛','bloodboon_ritual',['倒角三叉刃与倒刺','长柄连续符纹与箍环','祭仪花纹浮雕','血色宝石与镶座']);
}
{
  const s=sculpt('morgotts_cursed_sword');s.grip(-6,2,'bronze','black_steel');s.sphere('pommel',[0,-6.8,0],.9,'black_steel',.25);
  s.mount(2.9,2.4,.88,'iridescent','bronze');
  s.path('guard',[[ -3.8,4.2,0],[-2.5,3.2,0],[0,3,0],[2.4,2.5,0],[3.7,3.8,0]],.45,.6,'bronze');
  const face=s.blade(2.9,43,t=>1.4*t+7.5*t*t,t=>(3.2+1.2*Math.sin(t*Math.PI))*(.75+.25*smooth(t*40.1/1.2))*(t>.83?(1-t)/.17:1),
    t=>.44*(1-t*.45),'iridescent','silver',.32);
  const centerAt=y=>{const t=(y-2.9)/(43-2.9);return 1.4*t+7.5*t*t;};
  for(const z of [-.5,.5]) {
    for(let i=0;i<70;i++) {
      const t=i/70,y=4.5+t*36,x=centerAt(y),w=(1-t)*.6+.12;
      const a=x+Math.sin(i*.8)*w,b=centerAt(y+.52)+Math.sin((i+1)*.8)*w;
      s.rod('iridescent_vein',[a,y,Math.sign(z)*face(a,y)],[b,y+.52,Math.sign(z)*face(b,y+.52)],
        .12,.08,['blood','curse','gold','iridescent'][Math.floor(i/7)%4]);
    }
    for(let i=0;i<18;i++) {
      const t=i/18,y=5+t*32,x=centerAt(y);
      const b=centerAt(y+1.3)+.75;
      s.rod('cursed_crack',[x-.9,y,Math.sign(z)*face(x-.9,y)],[b,y+1.3,Math.sign(z)*face(b,y+1.3)],.085,.075,'black_steel');
    }
  }
  for(const side of [-1,1]) {
    s.rosette('guard_knot',[side*1.8,3.12,side*.36],.38,'bronze',5);
    for(let i=0;i<42;i++) {
      const y=5+i*.8,t=(y-3.7)/39.3,x=centerAt(y),z=side*(.44*(1-t*.45)+.035);
      s.path('blood_tributary',[[x,y,z],[x+Math.sin(i)*.2,y+.21,z],[x-.35,y+.38,z]].map(([x,y])=>[x,y,side*face(x,y)]),.04,.035,i%4?'blood':'curse');
      s.rod('etched_edge',[x+.65,y,z*.7],[centerAt(y+.48)+.75,y+.48,z*.7],.035,.03,'silver');
    }
  }
  finish(s,'蒙葛特咒剑',"Morgott's Cursed Sword",'大曲剑','cursed_blood_slice',['细分虹彩倒角曲刃','分叉诅咒血纹与微刻痕','不对称雕花护手','金属螺旋缠柄']);
}

return entries;
}
export const weapons=createRemembrance();
export const arts=[
  ['wave_of_gold','黄金波动','黄金波动的扇形波，使用沿地面传播的独立命中体，检测遮挡。'],
  ['gold_breaker','黄金碎击','允许技能跃起与落槌爆发；腾空轨迹、落地点与黄金冲击的伤害在服务器分阶段校验。'],
  ['destined_death','命定之死','允许跃起回旋、下砸与短时命定之死效果；斩击和残留效果独立结算，不做处决。'],
  ['takers_flames','掠夺火焰','沿地面推进的火焰；确认命中后吸血，不能从重复判定无限回复。'],
  ['waterfowl_dance','水鸟乱舞','允许跃起蓄势与空中多段斩击，分段突进和转向限幅；每段有独立命中集合，结束或取消恢复正常重力。'],
  ['starcaller_cry','唤星','重力牵引与双剑跃起砸击，服务端约束位移、落地点和遮挡。'],
  ['bloodboon_ritual','授血仪式','最多三次仪式输入，各段有独立消耗与命中，检测墙体。'],
  ['cursed_blood_slice','咒血斩击','短距离前进斩击与后续血焰，可接第二段；允许技能专用跃进，不等同于普通空中攻击。']
].map(([id,name,description])=>({id,name,description,status:'design only; not implemented',replaceable:false,
  allowsSkillJump:['gold_breaker','destined_death','waterfowl_dance','starcaller_cry','cursed_blood_slice'].includes(id),
  source:'named weapon skill reference; self-authored motion design; skill jump attacks allowed, executions disabled'}));

export const referenceSources=[
  {title:'追忆与兑换奖励核对',url:'https://game8.co/games/Elden-Ring/archives/370962'},
  {title:'武器外观图鉴参考',url:'https://www.altchar.com/guides/elden-ring-list-of-all-boss-weapons-remembrance-weapons-aSxMH1L2z6M2'},
  {title:'神躯化剑外观与名称',url:'https://www.vgbaike.com/elden_ring/baike14853'},
  {title:'义手刀外观',url:'https://earth.gamerguides.com/elden-ring/database/weapons/katanas/hand-of-malenia'},
  {title:'玛莉卡石槌外观',url:'https://www.gamerguides.com/elden-ring/database/weapons/hammers/marikas-hammer'}
];
