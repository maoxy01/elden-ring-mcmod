export function refineUtilityEquipment(model){
  const id=model.id.split(':')[1];if(!['spear','shield','long_bow'].includes(id))return model;
  const pixels=new Map();
  const paint=(x,y,material,bone='root',back=material)=>pixels.set(x+','+y,{x,y,material,bone,back});
  if(id==='spear'){
    for(let y=-12;y<38;y++)for(let x=-1;x<1;x++){
      let material=y>=-10&&y<4?(y%3===0?'leather_wrap':'leather'):(x<0?'wood':'wood__edge');
      if([-12,-11,-10,4,5,34,35,36,37].includes(y))material=x<0?'steel_dark':'steel';
      paint(x,y,material);
    }
    for(let y=37;y<52;y++){
      const half=y<39?1:y<41?2:y<47?3:y<49?2:1;
      for(let x=-half;x<half;x++){
        if(y===51&&x<0)continue;
        paint(x,y,x===-half?'steel_dark':x===half-1?'silver_edge':x<0?'steel':'steel__edge');
      }
    }
    for(let x=-2;x<2;x++)paint(x,35,x<0?'steel_dark':'steel');
  }else if(id==='shield'){
    for(let y=-7;y<7;y++){
      const half=y<-4?y+8:y>4?5:6;
      for(let x=-half;x<half;x++){
        const edge=x===-half||x===half-1||y===-7||y===6;
        let material=edge?'steel_dark':(x===-half+1||x===half-2?'steel':'red');
        if(!edge&&(x===-1||x===0||y===1||y===2))material='amber';
        if(!edge&&((y===4||y===-3)&&(x===-half+2||x===half-3)))material='silver_edge';
        const back=edge?'steel_dark':x===-1||x===0?'wood__shadow':y===-1||y===0?'leather':(x%3===0?'wood__edge':'wood');
        paint(x,y,material,'root',back);
      }
    }
  }else{
    for(let y=-29;y<29;y++){
      const bone=y>=3?'bow_upper':y<-3?'bow_lower':'root';
      const x=-1-Math.round(8*Math.sin(Math.PI*Math.min(29,Math.abs(y))/29));
      paint(x,y,'wood',bone);paint(x+1,y,'wood__edge',bone);
      paint(1,y,'ivory','string');
      if(Math.abs(y)>26){paint(x,y,'bone',bone);paint(x+1,y,'bone',bone);}
    }
    for(let y=-3;y<3;y++)for(let x=-2;x<2;x++)paint(x,y,y===-3||y===2?'steel':y%2===0?'leather_wrap':'leather');
    paint(0,28,'bone','bow_upper');paint(1,28,'bone','bow_upper');
    paint(0,-29,'bone','bow_lower');paint(1,-29,'bone','bow_lower');
  }
  const cubes=[],faceUVs={},ordered=[...pixels.values()].sort((a,b)=>a.y-b.y||a.x-b.x);
  for(let i=0;i<ordered.length;){
    const first=ordered[i];let end=i+1;
    while(end<ordered.length&&ordered[end].y===first.y&&ordered[end].x===ordered[end-1].x+1&&ordered[end].material===first.material&&ordered[end].back===first.back&&ordered[end].bone===first.bone)end++;
    const name='utility_v4_'+cubes.length,material=first.material;
    cubes.push({name,bone:first.bone,from:[first.x,first.y,-.5],to:[ordered[end-1].x+1,first.y+1,.5],material,pivot:[0,0,0],rotation:[0,0,0]});
    const uv=key=>model.materials[key].uv.map((v,j)=>v+(j<2?.5:-.5));
    const shade=model.materials[material+'__shadow']?material+'__shadow':'steel_dark';
    faceUVs[name]={north:uv(first.back),south:uv(material),east:uv(shade),west:uv(shade),up:uv(material),down:uv(shade)};i=end;
  }
  return {...model,cubes,faceUVs,detailLevel:'thin utility equipment refinement 4',flatProfile:{...model.flatProfile,pixelCount:pixels.size,
    method:'authored utility silhouette and front/back material pixels',refinement:{revision:1,id:'utility_v4',source:'art/tooling/spear-shield-bow-refinement.mjs',
      designBoundsModelUnits:({spear:{min:[-3,-12,-.5],max:[3,52,.5]},shield:{min:[-6,-7,-.5],max:[6,7,.5]},long_bow:{min:[-9,-29,-.5],max:[2,29,.5]}})[id]}}};
}
