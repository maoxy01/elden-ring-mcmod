import {BufferGeometry,Float32BufferAttribute,Vector3,Quaternion,Box3,SphereGeometry} from 'three';
import assert from 'node:assert/strict';

const vector=p=>new Vector3(...p);
function sourceBounds(surface,model,geometryForCube) {
  const names=new Set(surface.sourceCubes),bounds=new Box3();
  for(const c of model.cubes)if(names.has(c.name)) {
    const g=geometryForCube(c,model);g.computeBoundingBox();bounds.union(g.boundingBox);g.dispose();
  }
  return bounds;
}

export function smoothGeometry(surface,model,geometryForCube) {
  const bounds=sourceBounds(surface,model,geometryForCube),positions=[],indices=[],uvs=[];
  const materialUv=(p,material,face)=> {
    if(surface.kind==='blade'&&face!=='edge') {
      const view=face==='north'?'north':'south',tile=model.surfaceBake.tiles[view];
      const x=tile.size/2+(p.x-tile.center[0])*tile.scale;
      return [(tile.origin[0]+(view==='south'?x:tile.size-x))/model.textureSize[0],
        1-(tile.origin[1]+tile.size/2-(p.y-tile.center[1])*tile.scale)/model.textureSize[1]];
    }
    const tile=model.materials[material].uv;
    return [(tile[0]+(p.x+25)*2)/model.textureSize[0],1-(tile[1]+(p.y+16)*2)/model.textureSize[1]];
  };
  let maximumBoundsAdjustment=0,sideVertices=0;
  const clamp=p=> {
    const q=p.clone().clamp(bounds.min,bounds.max);
    maximumBoundsAdjustment=Math.max(maximumBoundsAdjustment,p.distanceTo(q));return q;
  };
  const add=(p,material,face)=> {
    p=clamp(p);const id=positions.length/3;positions.push(...p.toArray());uvs.push(...materialUv(p,material,face));return id;
  };
  const triangle=(a,b,c,outward)=> {
    const p=i=>vector(positions.slice(i*3,i*3+3)),cross=p(b).sub(p(a)).cross(p(c).sub(p(a)));
    assert(cross.lengthSq()>1e-16,`${surface.name}: collapsed surface triangle`);
    if(cross.dot(outward)<0)indices.push(a,c,b);else indices.push(a,b,c);
  };
  if(surface.kind==='ellipsoid') {
    const center=bounds.getCenter(new Vector3()),extent=bounds.getSize(new Vector3()).multiplyScalar(.5);
    const g=new SphereGeometry(1,8,6);g.scale(extent.x,extent.y,extent.z);g.translate(...center.toArray());
    const p=g.attributes.position;
    for(let i=0;i<p.count;i++)add(new Vector3().fromBufferAttribute(p,i),surface.material,'south');
    indices.push(...g.index.array);g.dispose();
  } else {
    const sections=surface.sections;
    assert(sections.length>=2);
    const rings=sections.map((s,i)=> {
      const h=s.halfWidth,d=s.halfDepth;
      const coords=surface.kind==='cylinder'?Array.from({length:16},(_,j)=>[Math.cos(j*Math.PI/8)*h,Math.sin(j*Math.PI/8)*d]):
        surface.kind==='sweep'?[[-h,0],[-h*.65,-d],[h*.65,-d],[h,0],[h*.65,d],[-h*.65,d]]:
        surface.profile==='stone'?[[-h,-d*.68],[-Math.max(.01,h-.65),-d],[Math.max(.01,h-.65),-d],[h,-d*.68],
          [h,d*.68],[Math.max(.01,h-.65),d],[-Math.max(.01,h-.65),d],[-h,d*.68]]:
        [[-h,-d*.45],[-h/3,-d],[h/3,-d],[h,-d*.45],[h,d*.45],[h/3,d],[-h/3,d],[-h,d*.45]];
      const q=new Quaternion();
      if(surface.kind==='sweep') {
        const closed=vector(sections[0].center).distanceTo(vector(sections.at(-1).center))<1e-8;
        const prev=closed&&(i===0||i===sections.length-1)?sections.length-2:Math.max(0,i-1);
        const next=closed&&(i===0||i===sections.length-1)?1:Math.min(i+1,sections.length-1);
        const tangent=vector(sections[next].center).sub(vector(sections[prev].center)).normalize();
        q.setFromUnitVectors(new Vector3(0,1,0),tangent);
      }
      return coords.map(([x,z])=>clamp(new Vector3(x,0,z).applyQuaternion(q).add(vector(s.center))));
    });
    const count=rings[0].length;
    sideVertices=sections.length*count*2;
    for(let face=0;face<count;face++) {
      const next=(face+1)%count,side=face<3?'north':'south';
      const edge=surface.kind==='blade'&&(face===3||face===7);
      const ids=rings.map((ring,i)=>[add(ring[face],edge?surface.edge:sections[i].material||surface.material,edge?'edge':side),
        add(ring[next],edge?surface.edge:sections[i].material||surface.material,edge?'edge':side)]);
      for(let i=1;i<rings.length;i++) {
        const outward=rings[i][face].clone().add(rings[i][next]).multiplyScalar(.5).sub(vector(sections[i].center));
        const [a,b]=ids[i-1],[c,d]=ids[i];triangle(a,c,b,outward);triangle(b,c,d,outward);
      }
    }
    const closed=surface.kind==='sweep'&&vector(sections[0].center).distanceTo(vector(sections.at(-1).center))<1e-8;
    for(const end of closed?[]:[0,rings.length-1]) {
      const ring=rings[end],outward=vector(sections[end].center)
        .sub(vector(sections[end===0?1:end-1].center)).normalize();
      const ids=ring.map(p=>add(p,sections[end].material||surface.material,'south'));
      for(let i=1;i<count-1;i++)triangle(ids[0],ids[i],ids[i+1],outward);
    }
  }
  const g=new BufferGeometry();
  g.setAttribute('position',new Float32BufferAttribute(positions,3));g.setAttribute('uv',new Float32BufferAttribute(uvs,2));
  g.setIndex(indices);g.computeVertexNormals();
  // Curved tubes share shading normals across longitudinal facets, but keep their end caps sharp.
  if(surface.kind==='sweep'||surface.kind==='cylinder') {
    const normals=g.attributes.normal,shared=new Map();
    for(let i=0;i<sideVertices;i++) {
      const key=positions.slice(i*3,i*3+3).map(n=>Math.round(n*1e6)).join(',');
      if(!shared.has(key))shared.set(key,{sum:new Vector3(),ids:[]});
      const group=shared.get(key);group.sum.add(new Vector3().fromBufferAttribute(normals,i));group.ids.push(i);
    }
    for(const group of shared.values()) {
      group.sum.normalize();for(const i of group.ids)normals.setXYZ(i,...group.sum.toArray());
    }
  }
  g.scale(1/model.unitsPerBlock,1/model.unitsPerBlock,1/model.unitsPerBlock);
  g.userData={name:surface.name,kind:surface.kind,maximumBoundsAdjustment,triangles:indices.length/3};return g;
}

