import assert from 'node:assert/strict';
import { Vector3, Euler, Matrix4, Box3 } from 'three';

const edges=[[0,1],[0,2],[0,4],[1,3],[1,5],[2,3],[2,6],[3,7],[4,5],[4,6],[5,7],[6,7]];

export function validateStructure(model) {
  assert(model.bones.length===1&&model.bones[0].rotation.every(v=>v===0),'Structural probe expects a static root');
  const solids=model.cubes.map(c=> {
    const pivot=new Vector3(...(c.pivot||c.from)),rotation=new Matrix4().makeRotationFromEuler(
      new Euler(...(c.rotation||[0,0,0]).map(v=>v*Math.PI/180),'XYZ'));
    const matrix=new Matrix4().makeTranslation(...pivot.toArray()).multiply(rotation)
      .multiply(new Matrix4().makeTranslation(...pivot.clone().negate().toArray()));
    const local=new Box3(new Vector3(...c.from),new Vector3(...c.to));
    const corners=Array.from({length:8},(_,i)=>new Vector3(
      c[i&1?'to':'from'][0],c[i&2?'to':'from'][1],c[i&4?'to':'from'][2]).applyMatrix4(matrix));
    return {name:c.name,local:local.expandByScalar(1e-6),inverse:matrix.clone().invert(),
      corners,bounds:new Box3().setFromPoints(corners).expandByScalar(1e-6)};
  });
  const contains=p=>solids.some(s=>s.bounds.containsPoint(p)&&s.local.containsPoint(p.clone().applyMatrix4(s.inverse)));
  const results=[];
  for(const check of model.structuralChecks||[]) {
    const from=new Vector3(...check.from),to=new Vector3(...check.to),steps=Math.ceil(from.distanceTo(to)/check.step);
    let samples=0;
    for(let i=0;i<=steps;i++) {
      const p=from.clone().lerp(to,i/steps);
      for(const [x,z] of [[0,0],[check.radius,0],[-check.radius,0],[0,check.radius],[0,-check.radius]]) {
        const point=p.clone().add(new Vector3(x,0,z));
        assert(contains(point),`${model.id}/${check.id}: open joint at ${point.toArray().map(n=>n.toFixed(3))}`);
        samples++;
      }
    }
    results.push({id:check.id,samples,status:'solid along five axial probe lines'});
  }
  for(const check of model.profileChecks||[]) {
    const parts=solids.filter(s=>/^(blade_bevel|blade_edge|twisted_blade)_/.test(s.name));
    let previous=null,maxWidthDelta=0,maxDepthDelta=0;
    const steps=Math.ceil((check.toY-check.fromY)/check.step);
    for(let i=0;i<=steps;i++) {
      const y=check.fromY+i*(check.toY-check.fromY)/steps,section=new Box3();
      for(const part of parts) {
        if(y<part.bounds.min.y||y>part.bounds.max.y)continue;
        for(const [a,b] of edges) {
          const p=part.corners[a],q=part.corners[b],dy=q.y-p.y;
          if(Math.abs(dy)<1e-10) {
            if(Math.abs(y-p.y)<1e-6){section.expandByPoint(p);section.expandByPoint(q);}
          } else {
            const t=(y-p.y)/dy;
            if(t>=0&&t<=1)section.expandByPoint(p.clone().lerp(q,t));
          }
        }
      }
      assert(!section.isEmpty(),`${model.id}/${check.id}: empty blade section`);
      const size=section.getSize(new Vector3());
      if(previous) {
        const dw=Math.abs(size.x-previous.x),dd=Math.abs(size.z-previous.z);
        maxWidthDelta=Math.max(maxWidthDelta,dw);maxDepthDelta=Math.max(maxDepthDelta,dd);
        assert(dw<=check.maxDelta&&dd<=check.maxDelta,`${model.id}/${check.id}: abrupt section change at ${y.toFixed(2)}`);
      }
      previous=size;
    }
    results.push({id:check.id,samples:steps+1,maxWidthDelta,maxDepthDelta,status:'section envelope continuous within tolerance'});
  }
  return results;
}
