const designs={
  straight_sword:{grip:[-3,2],bottom:-5,guardTop:3,guardHalf:5,top:22,width:y=>y<16?2:y<19?1.5:1},
};

export function refinePixelSword(model){
  const id=model.id.split(':')[1],design=designs[id];if(!design)return model;
  const pixels=new Map(),paint=(x,y,material)=>pixels.set(x+','+y,{x,y,material});
  const rect=(x0,y0,x1,y1,material)=>{for(let y=y0;y<y1;y++)for(let x=x0;x<x1;x++)paint(x,y,material);};
  const [gripBottom,gripTop]=design.grip;
  rect(-1,gripBottom,1,gripTop,'leather');
  for(let y=gripBottom+1;y<gripTop-1;y++)paint(y%2===0?-1:0,y,'leather_wrap');
  rect(-1,design.bottom,1,gripBottom,'steel_dark');
  rect(-2,design.bottom+1,2,gripBottom,'steel');
  paint(-1,design.bottom+1,'amber');
  rect(-1,gripBottom,1,gripBottom+1,'amber');rect(-1,gripTop-1,1,gripTop,'steel');
  rect(-design.guardHalf,gripTop,design.guardHalf,gripTop+1,'steel_dark');
  rect(-design.guardHalf+1,gripTop+1,design.guardHalf-1,design.guardTop,'steel');
  for(const sign of [-1,1]){
    paint(sign<0?-design.guardHalf:design.guardHalf-1,gripTop+1,'steel');
    paint(sign<0?-design.guardHalf+1:design.guardHalf-2,gripTop,'amber');
  }
  rect(-1,gripTop,1,design.guardTop+2,'steel');
  for(let y=design.guardTop;y<design.top;y++){
    const half=design.width(y),left=Math.floor(-half),right=Math.ceil(half);
    for(let x=left;x<right;x++){
      let material=x===left?'steel_dark':x===right-1?'silver_edge':x<0?'steel':'steel__edge';
      if(id==='great_sword'&&x===-1&&y>8&&y<24)material='steel_dark';
      if(id==='colossal_sword'&&x===-1&&y>7&&y<30)material='engraving';
      if(y===design.top-1&&x<0)continue;
      paint(x,y,material);
    }
  }
  const cubes=[],faceUVs={};
  // Merge horizontal material runs; preserve the authored staircase silhouette.
  for(let y=design.bottom;y<design.top;y++){
    const row=[...pixels.values()].filter(p=>p.y===y).sort((a,b)=>a.x-b.x);
    for(let i=0;i<row.length;){
      const first=row[i];let end=i+1;
      while(end<row.length&&row[end].x===row[end-1].x+1&&row[end].material===first.material)end++;
      const name='sword_v4_'+cubes.length,material=first.material;
      cubes.push({name,bone:'root',from:[first.x,y,-.5],to:[row[end-1].x+1,y+1,.5],material,pivot:[0,0,0],rotation:[0,0,0]});
      const uv=key=>model.materials[key].uv.map((v,j)=>v+(j<2?.5:-.5));
      const shade=model.materials[material+'__shadow']?material+'__shadow':'steel_dark';
      faceUVs[name]={north:uv(material),south:uv(material),east:uv(shade),west:uv(shade),up:uv(material),down:uv(shade)};i=end;
    }
  }
  return {...model,cubes,faceUVs,flatProfile:{...model.flatProfile,pixelCount:pixels.size,method:'authored staircase sword silhouette with directional material pixels',
    refinement:{revision:1,id:'sword_v4',source:'art/tooling/sword-pixel-refinement.mjs',features:['staircase blade taper','directional steel faces','connected guard socket','woven grip','faceted pommel']}},
    detailLevel:'thin aged pixel sword refinement 4'};
}
