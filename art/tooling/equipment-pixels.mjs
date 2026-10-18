import {createRequire} from 'node:module';
import assert from 'node:assert/strict';
const require=createRequire('C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json');
export const {PNG}=require('pngjs');
export function set(p,x,y,color){x=Math.floor(x);y=Math.floor(y);if(x>=0&&y>=0&&x<p.width&&y<p.height)p.data.set([...color,255],(y*p.width+x)*4)}
export function opaque(p,x,y){return x>=0&&y>=0&&x<p.width&&y<p.height&&p.data[(Math.floor(y)*p.width+Math.floor(x))*4+3]>0}
export function stats(p){
  const pts=[],colors=new Set(),pending=new Set();
  for(let y=0;y<p.height;y++)for(let x=0;x<p.width;x++){const i=(y*p.width+x)*4;assert([0,255].includes(p.data[i+3]));if(p.data[i+3]){pts.push([x,y]);pending.add(y*p.width+x);colors.add(p.data.subarray(i,i+3).join(','))}}
  assert(pts.length);const components=[];
  while(pending.size){let count=0;const queue=[pending.values().next().value];pending.delete(queue[0]);while(queue.length){const index=queue.pop(),x=index%p.width,y=Math.floor(index/p.width);count++;for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){const xx=x+dx,yy=y+dy;if(xx<0||yy<0||xx>=p.width||yy>=p.height)continue;const v=yy*p.width+xx;if(pending.delete(v))queue.push(v)}}components.push(count)}
  return {size:[p.width,p.height],boundsInclusive:[Math.min(...pts.map(p=>p[0])),Math.min(...pts.map(p=>p[1])),Math.max(...pts.map(p=>p[0])),Math.max(...pts.map(p=>p[1]))],opaquePixels:pts.length,colors:colors.size,components8:components.sort((a,b)=>b-a),transparentMargin:pts.every(([x,y])=>x>0&&y>0&&x<p.width-1&&y<p.height-1)};
}
export function shapes(source){
  const bones=new Map(source.bones.map(b=>[b.name,b]));
  function transform(point,name,visited=new Set()){
    if(!name)return point;assert(!visited.has(name),'Bone cycle');visited.add(name);const b=bones.get(name);assert(b);
    const [rx,ry,rz]=b.rotation||[0,0,0];assert(!rx&&!ry,'Non-planar bone needs mesh projection');const r=rz*Math.PI/180,dx=point[0]-b.pivot[0],dy=point[1]-b.pivot[1];
    return transform([b.pivot[0]+dx*Math.cos(r)-dy*Math.sin(r),b.pivot[1]+dx*Math.sin(r)+dy*Math.cos(r)],b.parent,visited);
  }
  return [...source.cubes].sort((a,b)=>a.to[2]-b.to[2]).map(b=>{
    assert((b.rotation||[]).every(v=>v===0));
    return {bone:b.bone,color:source.materials[b.material].color,points:[[b.from[0],b.from[1]],[b.to[0],b.from[1]],[b.to[0],b.to[1]],[b.from[0],b.to[1]]].map(p=>transform(p,b.bone))};
  });
}
export function line(a,b,width,color){const dx=b[0]-a[0],dy=b[1]-a[1],d=Math.hypot(dx,dy),nx=-dy/d*width/2,ny=dx/d*width/2;return {bone:'art_overlay',color,points:[[a[0]+nx,a[1]+ny],[b[0]+nx,b[1]+ny],[b[0]-nx,b[1]-ny],[a[0]-nx,a[1]-ny]]}}
function inside(x,y,ps){let result=false;for(let i=0,j=ps.length-1;i<ps.length;j=i++){const a=ps[i],b=ps[j];if((a[1]>y)!==(b[1]>y)&&x<(b[0]-a[0])*(y-a[1])/(b[1]-a[1])+a[0])result=!result}return result}
export function project(polygons,n,angle,boundsPolygons=polygons){
  const r=angle*Math.PI/180,c=Math.cos(r),s=Math.sin(r),map=([x,y])=>[x*c+y*s,x*s-y*c];
  const all=boundsPolygons.flatMap(p=>p.points.map(map)),lo=[0,1].map(a=>Math.min(...all.map(p=>p[a]))),hi=[0,1].map(a=>Math.max(...all.map(p=>p[a])));
  const margin=n===16?1:2,scale=(n-2*margin)/Math.max(hi[0]-lo[0],hi[1]-lo[1]),origin=[0,1].map(a=>(n-(hi[a]-lo[a])*scale)/2-lo[a]*scale);
  const mapped=polygons.map(p=>({...p,points:p.points.map(map).map(p=>p.map((v,a)=>v*scale+origin[a]))}));
  const png=new PNG({width:n,height:n});
  // Coverage chooses one existing palette color, never interpolated or antialiased pixels.
  for(let y=0;y<n;y++)for(let x=0;x<n;x++){
    const hits=new Map();
    for(let sy=0;sy<4;sy++)for(let sx=0;sx<4;sx++){const p=mapped.findLast(p=>inside(x+(sx+.5)/4,y+(sy+.5)/4,p.points));if(p){const k=p.color.join(',');hits.set(k,(hits.get(k)||0)+1)}}
    if(hits.size){const color=[...hits].sort((a,b)=>b[1]-a[1])[0][0].split(',').map(Number);set(png,x,y,color)}
  }
  return {png,origin,scale,anchor:origin.map(v=>Math.floor(v)+.5)};
}
export function backShield(front,anchor){
  const p=new PNG({width:front.width,height:front.height}),n=p.width;
  for(let y=0;y<n;y++)for(let x=0;x<n;x++)if(opaque(front,x,y)){
    const edge=[[-1,0],[1,0],[0,-1],[0,1]].some(([dx,dy])=>!opaque(front,x+dx,y+dy));
    let color=edge?[65,69,66]:x%6===0?[65,49,36]:[107,81,53];
    if(Math.abs(y-anchor[1])<n*.09&&Math.abs(x-anchor[0])<n*.25)color=[65,46,33];
    if(Math.abs(x-anchor[0])<n*.05&&Math.abs(y-anchor[1])<n*.16)color=[152,132,90];set(p,x,y,color);
  }return p;
}
export function nearestGrip(p,expected,maxDistance=3){
  let best=null,d=Infinity;
  for(let y=0;y<p.height;y++)for(let x=0;x<p.width;x++)if(opaque(p,x,y)){const distance=Math.hypot(x+.5-expected[0],y+.5-expected[1]);if(distance<d){d=distance;best=[x+.5,y+.5]}}
  assert(d<=maxDistance,`Grip ${expected} has no solid pixel within ${maxDistance}`);return best;
}
