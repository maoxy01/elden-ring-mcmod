import fs from 'node:fs/promises';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {BoxGeometry,Matrix4,Euler,Vector3,Box3} from 'three';
import assert from 'node:assert/strict';
import {smoothGeometry} from './weapon-smooth-surfaces.mjs';

const faces=['east','west','up','down','south','north'];
const round=n=>Math.round(n*1e8)/1e8;
const digest=bytes=>createHash('sha256').update(bytes).digest('hex');
export function cubeGeometry(c,model,normalized=false) {
  const geometry=new BoxGeometry(...c.to.map((n,i)=>n-c.from[i]));
  const pivot=c.pivot||c.from,center=c.to.map((n,i)=>(n+c.from[i])/2-pivot[i]);
  geometry.translate(...center);
  const matrix=new Matrix4().makeRotationFromEuler(new Euler(...(c.rotation||[0,0,0]).map(n=>n*Math.PI/180),'XYZ'));
  matrix.setPosition(...pivot);geometry.applyMatrix4(matrix);
  if(normalized)geometry.scale(1/model.unitsPerBlock,1/model.unitsPerBlock,1/model.unitsPerBlock);
  const uv=geometry.attributes.uv;
  for(let f=0;f<6;f++) {
    const rect=model.faceUVs[c.name][faces[f]];
    for(let j=0;j<4;j++) {
      const i=f*4+j,u=uv.getX(i),v=uv.getY(i);
      uv.setXY(i,rect[0]+u*(rect[2]-rect[0]),rect[3]-v*(rect[3]-rect[1]));
    }
  }
  return geometry;
}

export async function bakeAtlas(entries,{art,raw,size,stride,materials}) {
  const output=Buffer.alloc(raw.length),names=Object.keys(materials),tiles={};
  for(let y=0;y<size;y++)for(let x=0;x<size;x++) {
    const i=y*stride+x*4+1;output[i]=output[i+1]=output[i+2]=32;output[i+3]=255;
  }
  const gameMaterials=Object.fromEntries(names.map((name,i)=> {
    const x=1536+i%4*128,y=Math.floor(i/4)*128;
    for(let py=0;py<128;py++)for(let px=0;px<128;px++) {
      const original=materials[name].uv,src=(original[1]+py*4)*stride+(original[0]+px*4)*4+1;
      const dst=(y+py)*stride+(x+px)*4+1;raw.copy(output,dst,src,src+4);
    }
    return [name,{...materials[name],uv:[x,y,x+128,y+128]}];
  }));
  const reports=[];
  for(const [index,entry] of entries.entries()) {
    const sourcePath=path.join(art,'remembrance',`${entry.id}.model.json`),bytes=await fs.readFile(sourcePath);
    const source=JSON.parse(bytes),geometry=source.cubes.map(c=>cubeGeometry(c,source));
    const bounds=new Box3();
    for(const g of geometry){g.computeBoundingBox();bounds.union(g.boundingBox);}
    const margin=6,tileSize=384,scale=(tileSize-margin*2)/Math.max(bounds.max.x-bounds.min.x,bounds.max.y-bounds.min.y);
    const center=bounds.getCenter(new Vector3()),u=x=>tileSize/2+(x-center.x)*scale,v=y=>tileSize/2-(y-center.y)*scale;
    const pair={};
    for(const [view,sign] of [['south',1],['north',-1]]) {
      const number=index*2+(sign===1?0:1),ox=number%4*tileSize,oy=Math.floor(number/4)*tileSize;
      const zBuffer=new Float32Array(tileSize*tileSize).fill(-Infinity);
      const primary=source.materials[entry.model.cubes.find(c=>c.name.startsWith('blade_bevel'))?.material||'bronze'].color;
      for(let y=0;y<tileSize;y++)for(let x=0;x<tileSize;x++) {
        const p=(oy+y)*stride+(ox+x)*4+1;
        for(let channel=0;channel<3;channel++)output[p+channel]=primary[channel];
      }
      for(const g of geometry) {
        const positions=g.attributes.position,uvs=g.attributes.uv,indices=g.index.array;
        for(let i=0;i<indices.length;i+=3) {
          const verts=[indices[i],indices[i+1],indices[i+2]].map(n=>({
            x:sign===1?u(positions.getX(n)):tileSize-u(positions.getX(n)),y:v(positions.getY(n)),
            z:sign*positions.getZ(n),u:uvs.getX(n),v:uvs.getY(n)}));
          const [a,b,c]=verts,den=(b.y-c.y)*(a.x-c.x)+(c.x-b.x)*(a.y-c.y);
          if(Math.abs(den)<1e-8)continue;
          const minX=Math.max(0,Math.floor(Math.min(a.x,b.x,c.x))),maxX=Math.min(tileSize-1,Math.ceil(Math.max(a.x,b.x,c.x)));
          const minY=Math.max(0,Math.floor(Math.min(a.y,b.y,c.y))),maxY=Math.min(tileSize-1,Math.ceil(Math.max(a.y,b.y,c.y)));
          for(let y=minY;y<=maxY;y++)for(let x=minX;x<=maxX;x++) {
            const l=((b.y-c.y)*(x+.5-c.x)+(c.x-b.x)*(y+.5-c.y))/den;
            const m=((c.y-a.y)*(x+.5-c.x)+(a.x-c.x)*(y+.5-c.y))/den,n=1-l-m;
            if(l<-.00001||m<-.00001||n<-.00001)continue;
            const z=a.z*l+b.z*m+c.z*n,p=y*tileSize+x;
            if(z<zBuffer[p])continue;zBuffer[p]=z;
            const tx=Math.max(0,Math.min(size-1,Math.floor(a.u*l+b.u*m+c.u*n)));
            const ty=Math.max(0,Math.min(size-1,Math.floor(a.v*l+b.v*m+c.v*n)));
            const src=ty*stride+tx*4+1,dst=(oy+y)*stride+(ox+x)*4+1;
            raw.copy(output,dst,src,src+4);
          }
        }
      }
      pair[view]={origin:[ox,oy],scale,center:[center.x,center.y],size:tileSize};
    }
    entry.model.materials=gameMaterials;entry.model.textureSize=[size,size];
    entry.model.faceUVs=Object.fromEntries(entry.model.cubes.map(c=> {
      const tile=gameMaterials[c.material].uv,density=2;
      const rect=(a,b)=>[tile[0]+(c.from[a]+25)*density,tile[1]+(c.from[b]+16)*density,
        tile[0]+(c.to[a]+25)*density,tile[1]+(c.to[b]+16)*density];
      const values={north:rect(0,1),south:rect(0,1),east:rect(2,1),west:rect(2,1),up:rect(0,2),down:rect(0,2)};
      if(c.rotation.every(n=>Math.abs(n)<1e-7))for(const face of ['north','south']) {
        const {origin:[ox,oy]}=pair[face];
        values[face]=face==='south'?[ox+u(c.from[0]),oy+v(c.to[1]),ox+u(c.to[0]),oy+v(c.from[1])]:
          [ox+tileSize-u(c.to[0]),oy+v(c.to[1]),ox+tileSize-u(c.from[0]),oy+v(c.from[1])];
      }
      for(const uv of Object.values(values))assert(uv.every(Number.isFinite)&&uv[0]>=0&&uv[1]>=0&&uv[2]<=size&&uv[3]<=size&&uv[0]<uv[2]&&uv[1]<uv[3],`${entry.id}/${c.name}: invalid game UV`);
      return [c.name,values];
    }));
    entry.model.surfaceBake={method:'orthographic depth-tested albedo projection, front and back',
      sourceSha256:digest(bytes),projectedFaces:['north','south'],rotatedParts:'material tile fallback',
      normalMap:false,tiles:pair};
    reports.push({id:entry.model.id,sourceCubes:source.cubes.length,cubes:entry.model.cubes.length,
      reduction:1-entry.model.cubes.length/source.cubes.length,sourceSha256:digest(bytes)});
    geometry.forEach(g=>g.dispose());tiles[entry.id]=pair;
  }
  const clean=Buffer.from(output),denoised=Buffer.from(output),regions=[...Object.values(gameMaterials).map(m=>m.uv),
    ...Object.values(tiles).flatMap(pair=>Object.values(pair).map(t=>[...t.origin,t.origin[0]+t.size,t.origin[1]+t.size]))];
  const blockSize=entries[0].model.pixelConsolidation?.blockSize;
  if(blockSize) {
    assert(entries.every(e=>e.model.pixelConsolidation?.blockSize===blockSize));
    assert.equal(blockSize,2);
    let blocks=0;
    for(const [left,top,right,bottom] of regions)for(let y=top;y<bottom;y+=blockSize)for(let x=left;x<right;x+=blockSize) {
      const colors=[];
      for(let dy=0;dy<blockSize;dy++)for(let dx=0;dx<blockSize;dx++) {
        const i=(y+dy)*stride+(x+dx)*4+1;colors.push([output[i],output[i+1],output[i+2]]);
      }
      // Keep a coherent high-contrast rune/crack pair instead of averaging it into the metal.
      const distances=colors.map((c,i)=>colors.map((d,j)=>({j,delta:Math.max(...c.map((v,k)=>Math.abs(v-d[k])))})).filter(d=>d.j!==i));
      const coherent=distances.map((d,i)=>({i,near:d.filter(x=>x.delta<24).length,far:d.filter(x=>x.delta>48).length}))
        .filter(c=>c.near>=1&&c.far>=1).sort((a,b)=>b.far-a.far);
      let color=colors[0].map((_,c)=>Math.round(colors.reduce((n,v)=>n+v[c],0)/colors.length));
      if(coherent.length) {
        const mean=color;
        const best=coherent.sort((a,b)=>colors[b.i].reduce((n,v,c)=>n+Math.abs(v-mean[c]),0)-colors[a.i].reduce((n,v,c)=>n+Math.abs(v-mean[c]),0))[0];
        const cluster=colors.filter(c=>Math.max(...c.map((v,k)=>Math.abs(v-colors[best.i][k])))<24);
        color=color.map((_,c)=>Math.round(cluster.reduce((n,v)=>n+v[c],0)/cluster.length));
      }
      for(let dy=0;dy<blockSize;dy++)for(let dx=0;dx<blockSize;dx++) {
        const i=(y+dy)*stride+(x+dx)*4+1;
        for(let c=0;c<3;c++)clean[i+c]=color[c];
      }
      blocks++;
    }
    for(const entry of entries)entry.model.surfaceBake.pixelConsolidation={blockSize,filter:'none',
      sampling:'nearest',method:'tile-local 2x2 palette aggregation, preserve coherent high-contrast pairs'};
    return {raw:clean,materials:gameMaterials,reports,tiles,pixelConsolidation:{blockSize,blocks,filter:'none',smoothGeometry:false}};
  }
  let isolatedTexelsRemoved=0;
  for(const [left,top,right,bottom] of regions)for(let y=top+1;y<bottom-1;y++)for(let x=left+1;x<right-1;x++) {
    const at=y*stride+x*4+1,neighbors=[];let similar=0;
    for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++)if(dx||dy) {
      const i=(y+dy)*stride+(x+dx)*4+1;neighbors.push(i);
      if(Math.max(...[0,1,2].map(c=>Math.abs(output[i+c]-output[at+c])))<20)similar++;
    }
    if(similar>1)continue;
    const median=[0,1,2].map(c=>neighbors.map(i=>output[i+c]).sort((a,b)=>a-b)[4]);
    const cluster=neighbors.filter(i=>Math.max(...[0,1,2].map(c=>Math.abs(output[i+c]-median[c])))<20).length;
    if(cluster<6)continue;
    for(let c=0;c<3;c++)clean[at+c]=Math.round(output[at+c]*.15+median[c]*.85);isolatedTexelsRemoved++;
  }
  for(const [left,top,right,bottom] of regions)for(let y=top;y<bottom;y++)for(let x=left;x<right;x++) {
    const at=y*stride+x*4+1,sums=[0,0,0];let total=0;
    for(let dy=-2;dy<=2;dy++)for(let dx=-2;dx<=2;dx++) {
      const px=Math.max(left,Math.min(right-1,x+dx)),py=Math.max(top,Math.min(bottom-1,y+dy)),i=py*stride+px*4+1;
      const delta=Math.max(...[0,1,2].map(c=>Math.abs(clean[i+c]-clean[at+c])));
      if(delta>28)continue;
      const weight=(3-Math.abs(dx))*(3-Math.abs(dy))*(1-delta/29);
      for(let c=0;c<3;c++)sums[c]+=clean[i+c]*weight;total+=weight;
    }
    for(let c=0;c<3;c++)denoised[at+c]=Math.round(clean[at+c]*.12+sums[c]/total*.88);
  }
  const grain=buffer=> {
    let sum=0,count=0;
    for(const [left,top,right,bottom] of Object.values(gameMaterials).map(m=>m.uv))
      for(let y=top+2;y<bottom-2;y++)for(let x=left+2;x<right-2;x++) {
        const a=y*stride+x*4+1,b=a+4;
        for(let c=0;c<3;c++){sum+=Math.abs(buffer[a+c]-buffer[b+c]);count++;}
      }
    return sum/count;
  };
  const before=grain(output),after=grain(denoised);
  assert(after<before*.85,'Surface grain reduction below 15%');
  for(const entry of entries)entry.model.surfaceBake.denoise={method:'tile-local edge-preserving 5x5 albedo filter',
    radius:2,colorDifferenceLimit:28,blend:.88,preservesCoherentHighContrastMarkings:true,
    isolatedTexels:'suppress isolated one/two-pixel marks in otherwise consistent neighborhoods'};
  return {raw:denoised,materials:gameMaterials,reports,tiles,
    denoise:{materialNeighborDifferenceBefore:before,materialNeighborDifferenceAfter:after,reduction:1-after/before,isolatedTexelsRemoved}};
}

export function bakeMesh(model) {
  const pending=new Map(),quads=[];
  let removedFaces=0;
  const replaced=new Set((model.smoothSurfaces||[]).flatMap(s=>s.sourceCubes));
  for(const c of model.cubes) {
    if(replaced.has(c.name))continue;
    const g=cubeGeometry(c,model,true),p=g.attributes.position,n=g.attributes.normal,uv=g.attributes.uv;
    for(let f=0;f<6;f++) {
      const vertices=Array.from({length:4},(_,j)=> {
        const i=f*4+j;
        return {p:[p.getX(i),p.getY(i),p.getZ(i)].map(round),n:[n.getX(i),n.getY(i),n.getZ(i)].map(round),
          uv:[uv.getX(i)/model.textureSize[0],1-uv.getY(i)/model.textureSize[1]].map(round)};
      });
      const key=vertices.map(v=>v.p.join(',')).sort().join('|'),earlier=pending.get(key);
      if(earlier&&vertices[0].n.reduce((s,v,i)=>s+v*earlier.vertices[0].n[i],0)<-.999999) {
        earlier.removed=true;pending.delete(key);removedFaces+=2;
      } else {
        const quad={vertices,indices:Array.from(g.index.array.slice(f*6,f*6+6),i=>i-f*4),removed:false};
        quads.push(quad);pending.set(key,quad);
      }
    }
    g.dispose();
  }
  const positions=[],normals=[],uvs=[],indices=[],lookup=new Map();
  for(const q of quads)if(!q.removed) {
    const ids=q.vertices.map(v=> {
      const key=[...v.p,...v.n,...v.uv].join(',');
      if(lookup.has(key))return lookup.get(key);
      const id=positions.length/3;lookup.set(key,id);positions.push(...v.p);normals.push(...v.n);uvs.push(...v.uv);return id;
    });
    indices.push(...q.indices.map(i=>ids[i]));
  }
  const smoothing=[];
  for(const surface of model.smoothSurfaces||[]) {
    const g=smoothGeometry(surface,model,cubeGeometry),p=g.attributes.position,n=g.attributes.normal,uv=g.attributes.uv;
    const ids=new Map();
    for(const i of g.index.array) {
      if(ids.has(i))continue;
      const id=positions.length/3;ids.set(i,id);
      positions.push(...[p.getX(i),p.getY(i),p.getZ(i)].map(round));
      normals.push(...[n.getX(i),n.getY(i),n.getZ(i)].map(round));uvs.push(...[uv.getX(i),uv.getY(i)].map(round));
    }
    indices.push(...Array.from(g.index.array,i=>ids.get(i)));smoothing.push(g.userData);g.dispose();
  }
  return {schemaVersion:1,id:model.id,bone:'root',units:'blocks',texture:model.texture,positions,normals,uvs,indices,
    indexType:positions.length/3<=65535?'uint16':'uint32',vertexCount:positions.length/3,triangleCount:indices.length/3,
    materialGroups:1,removedFaces,culling:'only exact coincident quads with opposing normals; no AABB heuristic',
    smoothing:{replacedCubes:replaced.size,surfaces:smoothing,compatibilityFallback:'original connected cuboids'}};
}
