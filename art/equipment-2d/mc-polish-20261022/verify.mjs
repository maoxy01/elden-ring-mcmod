import fs from 'node:fs';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {PNG} from '../../tooling/equipment-pixels.mjs';
const root=new URL('./',import.meta.url),read=f=>fs.readFileSync(new URL(f,root)),json=f=>JSON.parse(read(f));
const source=new URL('../blockbench-all-items-20261018/items/',root);
const hash=b=>createHash('sha256').update(b).digest('hex');
const entries=json('baseline.json'),results=[];
const decode=t=>PNG.sync.read(Buffer.from(t.source.split(',')[1],'base64'));
for(const entry of entries){
 const raw=fs.readFileSync(new URL(entry.file,source));assert.equal(hash(raw),entry.sha256);
 const old=JSON.parse(raw),m=json('items/'+entry.file),sharpened=m.name==='starscourge_greatsword'&&!!m.unhandled_root_fields.stars_sharpen,remade=m.name==='great_spear'&&!!m.unhandled_root_fields.great_spear_remake,narrowed=m.name==='curved_greatsword'&&!!m.unhandled_root_fields.curved_width,rebuilt=sharpened||remade||narrowed;assert.equal(m.textures.length,old.textures.length);if(!rebuilt)assert.equal(m.elements.length,old.elements.length);assert(m.history.length>=1);
 assert.equal(m.unhandled_root_fields.art_contract.items.length,1);
 let alpha=0,changed=0;
 for(let j=0;j<m.textures.length;j++){
  const t=m.textures[j],a=decode(old.textures[j]),b=decode(t),p=PNG.sync.read(read('textures/'+t.name));assert.deepEqual(b.data,p.data);assert.equal(a.width,b.width,t.name);assert.equal(a.height,b.height,t.name);
  for(let i=0;i<a.data.length;i+=4){if(a.data[i+3]!==b.data[i+3])alpha++;if([0,1,2,3].some(k=>a.data[i+k]!==b.data[i+k]))changed++;}
 }
 if(!['test_flask','starscourge_greatsword','great_spear','curved_greatsword'].includes(m.name))assert.equal(alpha,0,m.name+' silhouette changed');assert(changed>0);
 if(sharpened){
  const r=m.unhandled_root_fields.stars_sharpen,a=m.unhandled_root_fields.art_contract.items[0],b=old.unhandled_root_fields.art_contract.items.find(x=>x.id===m.name);
  assert.equal(a.worldLengthBlocks,b.worldLengthBlocks);assert.deepEqual(a.anchorPixels,b.anchorPixels);assert.deepEqual(a.suggestedDisplay,b.suggestedDisplay);
  for(const s of r.states){assert.deepEqual(s.beforeExtent,s.afterExtent);const ti=m.textures.findIndex(t=>t.name===s.texture),state=s.state,elements=m.elements.filter(e=>e.name.startsWith('starscourge_greatsword__'+state+'__'));assert(elements.length>0);for(const e of elements)for(const f of Object.values(e.faces))assert.equal(f.texture,ti);}
  if(m.unhandled_root_fields.stars_width){
   const revision=m.unhandled_root_fields.stars_width,previous=json('revisions/stars-width-5of6/before.bbmodel');assert(Math.abs(revision.requestedWidthRatio-5/6)<.00001);
   for(const s of revision.states){
    const ti=m.textures.findIndex(t=>t.name===s.texture),oldImage=decode(previous.textures[ti]),image=decode(m.textures[ti]),anchor=a.states.find(x=>x.state===s.state).anchorPixels;
    let minBefore=Infinity,maxBefore=-Infinity,minAfter=Infinity,maxAfter=-Infinity;
    const pixels=new Set();
    for(let y=0;y<image.height;y++)for(let x=0;x<image.width;x++){
     const i=(y*image.width+x)*4,v=(x+.5-anchor[0]-y-.5+anchor[1])/Math.SQRT2/(image.width/64);
     if(oldImage.data[i+3]){minBefore=Math.min(minBefore,x-y);maxBefore=Math.max(maxBefore,x-y);}
     if(image.data[i+3]){minAfter=Math.min(minAfter,x-y);maxAfter=Math.max(maxAfter,x-y);pixels.add(y*image.width+x);}
     if(v<11)assert.deepEqual(image.data.subarray(i,i+4),oldImage.data.subarray(i,i+4),'Grip/guard pixel changed');
    }
    assert.equal(minBefore,minAfter);assert.equal(maxBefore,maxAfter);
    let components=0;while(pixels.size){components++;const q=[pixels.values().next().value];pixels.delete(q[0]);while(q.length){const index=q.pop(),x=index%image.width,y=Math.floor(index/image.width);for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){const xx=x+dx,yy=y+dy,key=yy*image.width+xx;if(xx>=0&&yy>=0&&xx<image.width&&yy<image.height&&pixels.delete(key))q.push(key);}}}
    assert.equal(components,1,'Disconnected blade');for(const row of s.sections)assert.equal(row.after,Math.max(1,Math.round(row.before*5/6)));
   }
  }
 }
 if(remade||narrowed){
  const previous=json('revisions/'+(narrowed?'curved-greatsword-width-5of6':'great-spear-remake')+'/before.bbmodel'),a=m.unhandled_root_fields.art_contract.items[0],b=previous.unhandled_root_fields.art_contract.items[0];
  assert.equal(a.worldLengthBlocks,b.worldLengthBlocks);assert.deepEqual(a.anchorPixels,b.anchorPixels);assert.deepEqual(a.suggestedDisplay,b.suggestedDisplay);
  for(let ti=0;ti<m.textures.length;ti++){
   const t=m.textures[ti],s=a.states.find(s=>s.texture===t.uuid),image=decode(t),before=decode(previous.textures[ti]);assert(s);const geometry=m.elements.filter(e=>e.name.startsWith(m.name+'__'+s.state+'__'));assert(geometry.length>0);for(const e of geometry)for(const f of Object.values(e.faces))assert.equal(f.texture,ti);
   if(narrowed){
    const r=m.unhandled_root_fields.curved_width,sections=r.states.find(row=>row.state===s.state).sections;assert(Math.abs(r.requestedWidthRatio-5/6)<.00001);
    for(const row of sections){assert(Math.abs(row.after-Math.max(1,row.before*row.rowRatio))<=.5001);assert(row.after<=row.before);}
    for(let y=0;y<image.height;y++)for(let x=0;x<image.width;x++){
     const v=(x+.5-s.anchorPixels[0]-y-.5+s.anchorPixels[1])/Math.SQRT2/(image.width/r.basePixels),i=(y*image.width+x)*4;
     if(v<r.bladeStart)assert.deepEqual(image.data.subarray(i,i+4),before.data.subarray(i,i+4),'Curved sword grip changed');
    }
   }
   const span=p=>{let lo=Infinity,hi=-Infinity;for(let y=0;y<p.height;y++)for(let x=0;x<p.width;x++)if(p.data[(y*p.width+x)*4+3]){lo=Math.min(lo,x-y);hi=Math.max(hi,x-y);}return [lo,hi];};assert.deepEqual(span(image),span(before),'Spear length changed');
   const cells=new Set();for(let i=3;i<image.data.length;i+=4)if(image.data[i])cells.add((i-3)/4);
   let components=0;while(cells.size){components++;const q=[cells.values().next().value];cells.delete(q[0]);while(q.length){const index=q.pop(),x=index%image.width,y=Math.floor(index/image.width);for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){const xx=x+dx,yy=y+dy,key=yy*image.width+xx;if(xx>=0&&yy>=0&&xx<image.width&&yy<image.height&&cells.delete(key))q.push(key);}}}assert.equal(components,1,'Spear has floating pixels');
  }
 }
 for(let j=0;j<m.elements.length;j++){
  const a=old.elements[j],b=m.elements[j];if(!rebuilt){for(const axis of [0,1]){assert.equal(a.from[axis],b.from[axis]);assert.equal(a.to[axis],b.to[axis]);}assert.deepEqual(b.faces,a.faces);}assert(Math.abs(b.to[2]-b.from[2]-1)<.001);
 }
 for(const phase of ['before','after'])for(const angle of ['front','side']){const p=PNG.sync.read(read('qa/'+m.name+'-'+phase+'-'+angle+'.png'));assert(p.data.some((v,i)=>i%4===3&&v>0));}
 results.push({id:m.name,states:m.textures.map(t=>t.name),alphaChanged:alpha,changedPixels:changed,history:m.history.length,sourceUnchanged:true});
}
const result={items:results.length,states:results.reduce((n,r)=>n+r.states.length,0),sourceUnchanged:true,thicknessUnits:1,xyGeometryUnchangedExcept:['starscourge_greatsword','great_spear','curved_greatsword'],worldLengthsPreserved:true,embeddedPngMatches:true,results};
fs.writeFileSync(new URL('validation.json',root),JSON.stringify(result,null,2));
const names=Object.fromEntries(json('../blockbench-all-items-20261018/manifest.json').assets.map(a=>[a.id,a.name]));
const cards=results.map(r=>`<section id="${r.id}"><h2>${names[r.id]||r.id}</h2><div class="pair"><figure><img src="qa/${r.id}-before-front.png"><figcaption>原版</figcaption></figure><figure><img src="qa/${r.id}-after-front.png"><figcaption>候选</figcaption></figure><figure><img src="qa/${r.id}-after-side.png"><figcaption>斜侧面</figcaption></figure></div><a href="items/${r.id}.bbmodel">Blockbench 工程</a><details><summary>全部状态 (${r.states.length})</summary><div class="states">${r.states.map(n=>`<figure><img src="textures/${n}"><figcaption>${n.replace(r.id+'__','').replace('.png','')}</figcaption></figure>`).join('')}</div></details></section>`).join('');
fs.writeFileSync(new URL('review.html',root),`<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>MC 物品候选审查</title><style>*{box-sizing:border-box}body{margin:0;background:#f3f5f4;color:#202926;font:16px system-ui;letter-spacing:0}header{padding:20px 24px;border-bottom:1px solid #bdc7c3}h1{font-size:24px;margin:0}main{max-width:1200px;margin:auto;padding:16px}section{padding:20px 0;border-bottom:1px solid #bdc7c3}h2{font-size:20px;margin:0 0 12px}.pair{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}figure{margin:0;text-align:center;min-width:0}img{width:100%;aspect-ratio:1;object-fit:contain;image-rendering:pixelated;background:#a6b3ae}figcaption{font-size:14px;padding:6px;overflow-wrap:anywhere}a{color:#276154;display:inline-block;margin:12px 0}summary{cursor:pointer;padding:10px 0}.states{display:flex;flex-wrap:wrap;gap:12px}.states figure{width:120px}.states img{width:120px}footer{padding:24px;color:#52635c}@media(max-width:540px){main{padding:10px}.pair{gap:5px}h2{font-size:18px}header{padding:16px}}</style><header><h1>MC 物品候选 · 28 件</h1></header><main>${cards}</main><footer>独立候选，未同步游戏资源。</footer></html>`);
console.log(JSON.stringify({...result,results:undefined}));
