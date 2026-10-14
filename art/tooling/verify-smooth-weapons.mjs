import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {Box3,Vector3} from 'three';
import {cubeGeometry} from './game-weapon-bake.mjs';
import {smoothGeometry} from './weapon-smooth-surfaces.mjs';
import {validateBakedMesh} from './weapon-mesh-validation.mjs';

const art=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..'),out=path.join(art,'remembrance-game');
const baseline=path.join(art,'backups/remembrance-game-before-smoothing-20261011-001');
const read=async p=>JSON.parse(await fs.readFile(p));
const catalog=await read(path.join(out,'catalog.json')),results=[];

function closedSurface(g,index=g.index.array) {
  const p=g.attributes.position,edges=new Map(),key=i=>[p.getX(i),p.getY(i),p.getZ(i)].map(n=>Math.round(n*1e5)).join(',');
  for(let i=0;i<index.length;i+=3) {
    const ids=Array.from(index.slice(i,i+3),key);
    for(let j=0;j<3;j++) {
      const a=ids[j],b=ids[(j+1)%3];assert.notEqual(a,b,'Collapsed welded edge');
      const k=[a,b].sort().join('|');edges.set(k,(edges.get(k)||0)+1);
    }
  }
  assert([...edges.values()].every(n=>n===2),'Open or nonmanifold continuous surface');
}

function unionProbe(mesh,model) {
  const p=mesh.positions,triangles=[];
  for(let i=0;i<mesh.indices.length;i+=3) {
    const [a,b,c]=mesh.indices.slice(i,i+3).map(j=>p.slice(j*3,j*3+3));
    const den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1]);
    if(Math.abs(den)>1e-12)triangles.push({a,b,c,den,minX:Math.min(a[0],b[0],c[0]),maxX:Math.max(a[0],b[0],c[0]),
      minY:Math.min(a[1],b[1],c[1]),maxY:Math.max(a[1],b[1],c[1])});
  }
  const contains=point=> {
    const [x,y,z]=point.map((v,i)=>v/model.unitsPerBlock+(i===0?1.137e-8:i===1?2.791e-8:0));let winding=0;
    for(const {a,b,c,den,minX,maxX,minY,maxY} of triangles) {
      if(x<minX||x>maxX||y<minY||y>maxY)continue;
      const l=((b[1]-c[1])*(x-c[0])+(c[0]-b[0])*(y-c[1]))/den;
      const m=((c[1]-a[1])*(x-c[0])+(a[0]-c[0])*(y-c[1]))/den,n=1-l-m;
      if(l<0||m<0||n<0)continue;
      if(a[2]*l+b[2]*m+c[2]*n>=z)winding+=Math.sign(den);
    }
    return winding>0;
  };
  let samples=0;
  for(const check of model.structuralChecks) {
    const a=new Vector3(...check.from),b=new Vector3(...check.to),steps=Math.ceil(a.distanceTo(b)/check.step);
    for(let i=0;i<=steps;i++)for(const [x,z] of [[0,0],[check.radius,0],[-check.radius,0],[0,check.radius],[0,-check.radius]]) {
      const p=a.clone().lerp(b,i/steps).add(new Vector3(x,0,z)).toArray();
      assert(contains(p),`${model.id}/${check.id}: actual mesh open joint at ${p}`);samples++;
    }
  }
  assert(!contains([500,500,500]),'Outside-point negative test');return {samples,contains};
}

for(const w of catalog.weapons) {
  const model=await read(path.join(out,w.model)),mesh=await read(path.join(out,w.mesh));
  const before=await read(path.join(baseline,'art/remembrance-game',w.mesh));
  validateBakedMesh(mesh);assert(mesh.triangleCount<=before.triangleCount,`${w.id}: increased rendering geometry`);
  assert.equal(model.refinement.revision,2);assert(model.smoothSurfaces.length>0);
  const bounds=new Box3();
  for(const c of model.cubes) {
    const g=cubeGeometry(c,model,true);g.computeBoundingBox();bounds.union(g.boundingBox);g.dispose();
  }
  bounds.expandByScalar(.002);
  for(let i=0;i<mesh.positions.length;i+=3)assert(bounds.containsPoint(new Vector3(...mesh.positions.slice(i,i+3))),`${w.id}: mesh outside runtime bounds`);
  for(const surface of model.smoothSurfaces) {
    const g=smoothGeometry(surface,model,cubeGeometry);closedSurface(g);
    assert(g.userData.maximumBoundsAdjustment<=.06,`${w.id}/${surface.name}: excessive profile adjustment`);
    assert.throws(()=>closedSurface(g,g.index.array.slice(3)),/Open or nonmanifold/);g.dispose();
  }
  const jointSamples=unionProbe(mesh,model).samples;
  let bindingContactSamples=0;
  const grip=model.smoothSurfaces.find(s=>s.kind==='cylinder');
  const binding=model.smoothSurfaces.find(s=>s.name.startsWith('spiral_binding_'));
  for(const surface of [grip,binding])assert(surface,`${w.id}: missing connected grip/binding`);
  const occupancy=surface=> {
    const g=smoothGeometry(surface,model,cubeGeometry),data={positions:Array.from(g.attributes.position.array),indices:Array.from(g.index.array)};
    const {contains}=unionProbe(data,{...model,structuralChecks:[]});g.dispose();return contains;
  };
  const gripContains=occupancy(grip),bindingContains=occupancy(binding);
  for(const section of binding.sections.slice(1,-1)) {
    const [x,y,z]=section.center,r=Math.hypot(x,z),point=[x/r*.67,y,z/r*.67];
    assert(gripContains(point)&&bindingContains(point),`${w.id}: binding does not contact smooth grip at ${point}`);
    bindingContactSamples++;
  }
  results.push({id:w.id,beforeTriangles:before.triangleCount,triangles:mesh.triangleCount,
    triangleReduction:1-mesh.triangleCount/before.triangleCount,closedSurfaces:model.smoothSurfaces.length,
    actualMeshJointSamples:jointSamples,bindingContactSamples,boundsPassed:true});
  console.log(`${w.id}: ${before.triangleCount} -> ${mesh.triangleCount}, ${jointSamples} actual-mesh joint probes`);
}
const designBytes=await fs.readFile(path.join(out,'weapon-arts.design.json'));
assert.deepEqual(designBytes,await fs.readFile(path.join(baseline,'art/remembrance-game/weapon-arts.design.json')),'Skill design changed during art task');
const validation=await read(path.join(out,'validation.json'));assert(validation.denoise.reduction>=.15);
const report={status:'passed',revision:2,method:'continuous closed lofts/sweeps; actual triangle-union axial joint probes',
  limitations:'Axial probes do not prove all decorative parts globally intersect; cuboid connectivity and visual close-ups are separate checks.',
  models:results,denoise:validation.denoise,skillDesignUnchanged:true,
  skillDesignSha256:createHash('sha256').update(designBytes).digest('hex'),gameplayVerified:false,performanceMeasured:false};
await fs.writeFile(path.join(out,'smoothing-validation.json'),JSON.stringify(report,null,2)+'\n');
console.log('SMOOTH_WEAPONS_PASS');
