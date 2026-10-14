export const bladeDesigns={
  dagger:{grip:[-3,2],bottom:-5,top:14,guard:5,reference:'Dagger'},
  thrusting_sword:{grip:[-3,2],bottom:-5,top:28,guard:5,reference:'Estoc'},
  heavy_thrusting_sword:{grip:[-6,2],bottom:-8,top:35,guard:5,reference:'Great Epee'},
  curved_sword:{grip:[-3,2],bottom:-5,top:25,guard:5,reference:'Scimitar'},
  katana:{grip:[-6,2],bottom:-8,top:34,guard:3,reference:'Uchigatana'},
  twinblade:{grip:[-4,4],bottom:-26,top:26,guard:4,reference:'Twinblade'}
};
export function refineBladeEquipment(model){
  const id=model.id.split(':')[1],d=bladeDesigns[id];if(!d)return model;
  const pixels=new Map(),paint=(x,y,material)=>pixels.set(x+','+y,{x,y,material});
  const rect=(x0,y0,x1,y1,m)=>{for(let y=y0;y<y1;y++)for(let x=x0;x<x1;x++)paint(x,y,m);};
  rect(-1,d.grip[0],1,d.grip[1],'leather');
  for(let y=d.grip[0]+1;y<d.grip[1]-1;y++)paint(y%2?-1:0,y,'leather_wrap');
  if(id!=='twinblade'){
    rect(-1,d.bottom,1,d.grip[0],'steel_dark');rect(-2,d.bottom+1,2,d.grip[0],'steel');
    rect(-1,d.grip[0],1,d.grip[0]+1,'amber');
  }
  const start=id==='twinblade'?5:id==='thrusting_sword'?6:id==='heavy_thrusting_sword'?5:4;
  rect(-1,d.grip[1]-1,1,start+1,'steel');
  rect(-d.guard,d.grip[1],d.guard,d.grip[1]+1,'steel_dark');
  if(id==='thrusting_sword'){
    rect(-4,3,-3,5,'steel');rect(3,3,4,5,'steel');rect(-3,5,3,6,'steel');
  }else if(id==='heavy_thrusting_sword'){
    rect(-5,3,-3,4,'steel');rect(2,3,5,4,'steel');rect(-3,4,3,5,'steel');
  }else if(id==='katana'){
    rect(-2,3,2,4,'steel');paint(-1,3,'amber');
  }else{
    paint(-d.guard,3+(id==='twinblade'?2:0),'steel');paint(d.guard-1,3+(id==='twinblade'?2:0),'steel');
  }
  for(let y=start;y<d.top;y++){
    let center=0,half=1;
    if(id==='dagger')half=y<10?2:y<12?1.5:1;
    if(id==='thrusting_sword')half=y<24?1.5:1;
    if(id==='heavy_thrusting_sword')half=y<29?2:1;
    if(id==='curved_sword'){center=Math.floor(3*((y-start)/(d.top-start))**2);half=y<19?2:1;}
    if(id==='katana'){center=y>24?1:0;half=y<29?1.5:1;}
    if(id==='twinblade')half=y<9?3:y<21?2:1;
    const left=center+Math.floor(-half),right=center+Math.ceil(half);
    for(let x=left;x<right;x++){
      if(y===d.top-1&&x<center)continue;
      let material=x===left?'steel_dark':x===right-1?'silver_edge':x<center?'steel':'steel__edge';
      if(id==='heavy_thrusting_sword'&&x===-1&&y>8&&y<27)material='engraving';
      paint(x,y,material);if(id==='twinblade')paint(-x-1,-y-1,material);
    }
  }
  if(id==='twinblade'){rect(-4,-5,4,-4,'steel_dark');paint(-4,-6,'steel');paint(3,-6,'steel');}
  const cubes=[],faceUVs={},ordered=[...pixels.values()].sort((a,b)=>a.y-b.y||a.x-b.x);
  for(let i=0;i<ordered.length;){
    const first=ordered[i];let end=i+1;
    while(end<ordered.length&&ordered[end].y===first.y&&ordered[end].x===ordered[end-1].x+1&&ordered[end].material===first.material)end++;
    const name='blade_v4_'+cubes.length,material=first.material;
    cubes.push({name,bone:'root',from:[first.x,first.y,-.5],to:[ordered[end-1].x+1,first.y+1,.5],material,pivot:[0,0,0],rotation:[0,0,0]});
    const uv=key=>model.materials[key].uv.map((v,j)=>v+(j<2?.5:-.5));
    const shadow=model.materials[material+'__shadow']?material+'__shadow':'steel_dark';
    faceUVs[name]={north:uv(material),south:uv(material),east:uv(shadow),west:uv(shadow),up:uv(material),down:uv(shadow)};i=end;
  }
  return {...model,cubes,faceUVs,detailLevel:'thin differentiated blades refinement 4',flatProfile:{...model.flatProfile,pixelCount:pixels.size,
    method:'authored per-class stepped blade and continuous grip',refinement:{id:'blade_v4',revision:1,referenceWeapon:d.reference,source:'art/tooling/blade-equipment-refinement.mjs'}}};
}
