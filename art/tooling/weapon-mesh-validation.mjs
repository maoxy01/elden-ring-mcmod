import assert from 'node:assert/strict';
export function validateBakedMesh(mesh) {
  assert.equal(mesh.bone,'root');assert.equal(mesh.units,'blocks');assert.equal(mesh.materialGroups,1);
  const {positions:p,normals:n,uvs:uv,indices}=mesh;
  assert(p.length%3===0&&indices.length%3===0);
  assert.equal(p.length,mesh.vertexCount*3);assert.equal(n.length,p.length);assert.equal(uv.length,mesh.vertexCount*2);
  assert.equal(indices.length,mesh.triangleCount*3);
  assert([...p,...n,...uv].every(Number.isFinite));assert(uv.every(v=>v>=0&&v<=1));
  assert(indices.every(i=>Number.isInteger(i)&&i>=0&&i<mesh.vertexCount));
  for(let i=0;i<n.length;i+=3)assert(Math.abs(Math.hypot(n[i],n[i+1],n[i+2])-1)<1e-5,'Invalid normal');
  for(let i=0;i<indices.length;i+=3) {
    const [a,b,c]=indices.slice(i,i+3).map(n=>n*3),u=[0,1,2].map(j=>p[b+j]-p[a+j]),v=[0,1,2].map(j=>p[c+j]-p[a+j]);
    const cross=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]];
    assert(Math.hypot(...cross)>1e-14,'Degenerate triangle');
    assert(cross.reduce((s,x,j)=>s+x*n[a+j],0)>0,'Inverted winding');
  }
}
