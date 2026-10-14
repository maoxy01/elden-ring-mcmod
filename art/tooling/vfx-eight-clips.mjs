// Editable pixel geometry for the five additional remembrance skills.
export const palettes={
  gold_impact:[[79,63,34],[162,113,31],[226,168,43],[255,211,90],[255,246,185]],
  flame_front:[[65,20,25],[133,29,23],[215,56,22],[250,125,35],[255,210,96]],
  gravity_pull:[[30,26,43],[78,53,109],[137,93,177],[189,153,215],[236,215,246]],
  gravity_impact:[[30,26,43],[78,53,109],[137,93,177],[189,153,215],[236,215,246]],
  blood_ritual:[[56,16,29],[118,22,43],[186,37,53],[235,79,69],[255,166,115]],
  cursed_slash:[[71,28,43],[141,39,66],[205,68,97],[245,124,121],[255,206,171]],
  blood_flame:[[59,17,28],[136,25,48],[202,48,61],[245,96,69],[255,181,120]],
};
const floorPlane={basisU:[1,0,0],basisV:[0,0,1],normal:[0,1,0],cameraFacing:false,space:'entity-local horizontal; PNG +Y maps to -Z',heightAboveGroundBlocks:.04};
const upright={basisU:[1,0,0],basisV:[0,1,0],normal:[0,0,1],cameraFacing:false,space:'caster-local vertical; PNG +Y maps downward',attachOffsetBlocks:[0,1.05,.35]};
const defaults=(windup,active,recovery,visual)=>({windupTicks:windup,activeTicks:active,recoveryTicks:recovery,visualEntityTicks:visual});
export const definitions=[
  {id:'gold_impact',skillId:'eldencombat:gold_breaker',kind:'GOLD_IMPACT',name:'黄金碎击',frameSize:[64,64],anchorPixels:[32,32],worldSizeBlocks:[7,7],sourceDefaults:defaults(8,16,5,12),plane:floorPlane,event:'landingImpact, once after supported landing; never guessed from windup',debris:{existingGroundState:true,maxPieces:12,style:'radial lift and fall; use sampled block material',changesBlocks:false}},
  {id:'flame_front',skillId:'eldencombat:takers_flames',kind:'FLAME_FRONT',name:'掠夺火焰',frameSize:[64,64],anchorPixels:[32,58],worldSizeBlocks:[5,3.5],sourceDefaults:{...defaults(9,4,5,40),waveSpeedBlocksPerTick:.65,effectRangeBlocks:12,effectLifespanUpperBoundTicks:40,baseRadiusBlocks:2.5,maxRadiusBlocks:2.5},plane:{...upright,space:'wave-local upright across travel; bottom attached to lane terrain',attachOffsetBlocks:[0,0,0]},laneContract:{lanes:16,sourcePixelsPerLane:4,worldWidth:'2 * visualRadius(partialTick)',worldHeightBlocks:3.5,uvRangeForLane:'[lane/16, (lane+1)/16]',blockedMask:'omit lane if ((blockedLanes >>> lane) & 1) != 0',formationDistanceBlocks:4,edgeSpeedFactor:.55,lagGridBlocks:.125,lag:'same WaveFront local offsets as current server',doNotDrawAsSingleQuad:true,animationClock:'visualProgress(partialTick)'},event:'wave entity spawn on ACTIVE; terrain and wall discard authoritative'},
  {id:'gravity_pull',skillId:'eldencombat:starcaller_cry',kind:'GRAVITY_PULL',name:'唤星 · 聚引',frameSize:[64,64],anchorPixels:[32,32],worldSizeBlocks:[10,10],sourceDefaults:defaults(8,6,5,12),plane:floorPlane,event:'stageIndex == 1 onActive',debris:{existingGroundState:true,maxPieces:10,style:'hover then draw inward',changesBlocks:false}},
  {id:'gravity_impact',skillId:'eldencombat:starcaller_cry',kind:'GRAVITY_IMPACT',name:'唤星 · 坠击',frameSize:[64,64],anchorPixels:[32,32],worldSizeBlocks:[8,8],sourceDefaults:defaults(6,8,5,12),plane:floorPlane,event:'stageIndex > 1 landingImpact, once; requires real followup input',debris:{existingGroundState:true,maxPieces:12,style:'outward ground fracture with lift and fall',changesBlocks:false}},
  {id:'blood_ritual',skillId:'eldencombat:bloodboon_ritual',kind:'BLOOD_RITUAL',name:'授血仪式',frameSize:[64,64],anchorPixels:[32,32],worldSizeBlocks:[8,8],sourceDefaults:defaults(6,4,2,16),plane:{...floorPlane,heightAboveGroundBlocks:2.4},event:'onActive for each confirmed input stage; phase=stageIndex-1',phaseVariants:[{phase:0,scale:1},{phase:1,scale:1.04},{phase:2,scale:1.08}],shape:'square blood sigil; broken second square, corner notches, falling flecks; no circular ring'},
  {id:'cursed_slash',skillId:'eldencombat:cursed_blood_slice',kind:'CURSED_SLASH',name:'咒血斩击 · 刀痕',frameSize:[64,64],anchorPixels:[32,36],worldSizeBlocks:[6,4.5],phaseRollDegrees:[-26,28,-26],sourceDefaults:defaults(8,6,5,8),plane:upright,event:'onActive for each input stage; phase follows cast.stageIndex modulo 3'},
  {id:'blood_flame',skillId:'eldencombat:cursed_blood_slice',kind:'BLOOD_FLAME',name:'咒血斩击 · 延烧',frameSize:[64,64],anchorPixels:[32,56],worldSizeBlocks:[4,3],sourceDefaults:defaults(8,6,5,12),plane:{...upright,space:'fixed entity-local at delayed origin; never follows owner',attachOffsetBlocks:[0,0,0]},event:'activeTick elapsed == 4 if reached; current followup active=4 may never enter this branch; combat owner must decide event semantics'},
];

export function renderExtra(id,frame,{PNG,set,upscale,clamp,noise}){
  const png=new PNG({width:32,height:32}),p=(frame+.5)/16,pal=palettes[id];
  const fade=p<.66?1:clamp((1-p)/.34);
  const put=(x,y,col,alpha=255)=>set(png,x,y,pal[col],alpha*fade);
  if(id==='gold_impact'||id==='gravity_pull'||id==='gravity_impact'){
    const pull=id==='gravity_pull';
    const radius=pull?13-10*p:2+11*Math.min(1,p/.62);
    for(let y=1;y<31;y++)for(let x=1;x<31;x++){
      const dx=x-15.5,dy=y-15.5,a=Math.atan2(dy,dx),r=Math.hypot(dx,dy);
      // Quantized, irregular sector radii keep this a broken block-built perimeter.
      const sector=Math.floor((a+Math.PI)*8/Math.PI);
      const edge=Math.round(radius)+(sector*7%5-2)*.65;
      const ring=Math.abs(r-edge),gap=sector%7===0&&noise(x,y,0)>.35;
      if(ring<1.4&&!gap&&!(p>.72&&noise(x,y,frame)>.55))put(x,y,ring<.55?4:2,ring<.55?255:224);
      const axis=Math.abs(Math.sin(a*5+Math.floor(r/3)*.30));
      if(!pull&&r<edge-1.5&&r>1&&axis<.10&&noise(x,y,0)>.25)put(x,y,2,p<.7?160:96);
      if(pull&&r>edge+1.5&&r<14&&axis<.17&&noise(x,y,frame)>.5)put(x,y,3,160);
      if(!pull&&r<2.5&&p<.36)put(x,y,4);
    }
    for(let i=0;i<10;i++){
      const a=i*2.399+.2,r=pull?14-11*p:3+10*p;
      const x=Math.round(15.5+Math.cos(a)*r),y=Math.round(15.5+Math.sin(a)*r);
      put(x,y,i%3===0?4:1,224);if(i%2===0)put(x+1,y,2,160);
    }
  }else if(id==='blood_ritual'){
    const extent=9+Math.min(3,Math.floor(p*10));
    for(let y=1;y<31;y++)for(let x=1;x<31;x++){
      const dx=Math.abs(x-15.5),dy=Math.abs(y-15.5),r=Math.max(dx,dy);
      const corner=Math.min(dx,dy)>extent-3;
      if(Math.abs(r-extent)<.8&&(p<.72||noise(x,y,frame)<.52))put(x,y,corner?4:2,255);
      if(Math.abs(r-(extent-3))<.7&&((x+y+frame)%7<4))put(x,y,1,160);
      if(corner&&r<extent+2&&Math.abs(dx-dy)<1)put(x,y,3,224);
      const drop=(y+frame*2)%11;
      if((x===6||x===13||x===20||x===25)&&drop<2&&r<extent-2)put(x,y,drop===0?3:1,160);
    }
  }else if(id==='cursed_slash'){
    const head=(-130+250*p)*Math.PI/180,extent=2.5;
    for(let y=1;y<31;y++)for(let x=1;x<31;x++){
      const dx=(x-16)/13,dy=(y-18)/10,r=Math.hypot(dx,dy);
      const behind=((head-Math.atan2(dy,dx))%(Math.PI*2)+Math.PI*2)%(Math.PI*2);
      if(behind>extent)continue;
      const taper=Math.sin(behind/extent*Math.PI),thickness=.16*taper;
      if(r<1&&r>1-thickness)put(x,y,4,255);
      else if(r<1-thickness&&r>1-thickness-.21*taper&&noise(x,y,frame)>.25)put(x,y,noise(x,y,frame)>.7?3:1,224);
    }
  }else{
    const front=id==='flame_front';
    for(let x=front?0:2;x<(front?32:30);x++){
      const center=1-Math.abs(x-15.5)/16;
      const base=front?28:27;
      const tongue=front?5+Math.floor(center*11)+(x*7+frame*3)%7:3+Math.floor(center*13)+(x*3+frame*2)%5;
      const height=Math.max(2,Math.floor(tongue*(.55+Math.min(.45,p*3))));
      for(let y=base-height;y<=base;y++){
        const t=(base-y)/height,n=noise(x,y,frame);
        if(t>.65&&n>.66)continue;
        if(p>.75&&t>.25&&n>.6)continue;
        const col=t<.22?4:t<.50?3:t<.77?2:1;
        put(x,y,col,t>.77?160:255);
      }
      if((x+frame)%7===0){const y=base-height-2-frame%3;if(y>1)put(x,y,3,160)}
    }
  }
  return upscale(png,2);
}
