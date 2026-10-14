import {OBB} from 'three/addons/math/OBB.js';
import {Vector3,Matrix3,Matrix4,Euler,Box3} from 'three';

export function connectedComponents(model) {
  const nodes=model.cubes.map(c=> {
    const pivot=new Vector3(...(c.pivot||c.from)),matrix=new Matrix4().makeRotationFromEuler(
      new Euler(...c.rotation.map(n=>n*Math.PI/180),'XYZ'));
    const center=new Vector3(...c.from).add(new Vector3(...c.to)).multiplyScalar(.5)
      .sub(pivot).applyMatrix4(matrix).add(pivot);
    const half=new Vector3(...c.to).sub(new Vector3(...c.from)).multiplyScalar(.5);
    const obb=new OBB(center,half,new Matrix3().setFromMatrix4(matrix));
    const corners=Array.from({length:8},(_,i)=>new Vector3(
      c[i&1?'to':'from'][0],c[i&2?'to':'from'][1],c[i&4?'to':'from'][2]).sub(pivot).applyMatrix4(matrix).add(pivot));
    return {name:c.name,obb,bounds:new Box3().setFromPoints(corners).expandByScalar(1e-6)};
  });
  const parents=nodes.map((_,i)=>i),find=i=>parents[i]===i?i:(parents[i]=find(parents[i]));
  const order=nodes.map((_,i)=>i).sort((a,b)=>nodes[a].bounds.min.y-nodes[b].bounds.min.y);
  for(let n=0;n<order.length;n++)for(let m=n+1;m<order.length;m++) {
    const a=order[n],b=order[m];
    if(nodes[b].bounds.min.y>nodes[a].bounds.max.y)break;
    if(nodes[a].bounds.intersectsBox(nodes[b].bounds)&&nodes[a].obb.intersectsOBB(nodes[b].obb,1e-7))parents[find(b)]=find(a);
  }
  const groups=new Map();
  nodes.forEach((n,i)=> {const root=find(i);if(!groups.has(root))groups.set(root,[]);groups.get(root).push(n.name);});
  return [...groups.values()].sort((a,b)=>b.length-a.length);
}
