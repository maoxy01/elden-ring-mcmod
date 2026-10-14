import fs from 'node:fs/promises';
import path from 'node:path';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const out = path.join(root, 'art/grace-pixel');
const require = createRequire('C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json');
const {PNG} = require('pngjs');
const palette = {
  '.': [0,0,0,0], d:[72,63,38,255], g:[175,138,51,255],
  y:[226,190,79,255], l:[252,231,153,255],
  k:[48,49,48,255], s:[100,105,103,255], w:[208,212,204,255],
  r:[175,89,79,255], a:[226,190,79,96], b:[252,231,153,192],
};
const blank = () => Array.from({length:16}, () => Array(16).fill('.'));
const put = (grid,x,y,c) => { if(x>=0 && x<16 && y>=0 && y<16) grid[y][x]=c; };
const rect = (grid,x,y,w,h,c) => { for(let j=y;j<y+h;j++)for(let i=x;i<x+w;i++)put(grid,i,j,c); };
const line = (grid,x0,y0,x1,y1,c) => {
  let dx=Math.abs(x1-x0), sx=x0<x1?1:-1, dy=-Math.abs(y1-y0), sy=y0<y1?1:-1, err=dx+dy;
  for(;;){put(grid,x0,y0,c);if(x0===x1&&y0===y1)break;const e=2*err;if(e>=dy){err+=dy;x0+=sx}if(e<=dx){err+=dx;y0+=sy}}
};
const assets=[];
const add=(id,kind,frames,extra={})=>assets.push({id:`eldencombat:${id}`,kind,file:`textures/${kind}/${id}.png`,width:16,height:16*frames.length,frameSize:[16,16],anchorPixels:kind==='map'?[8,8]:kind==='fx'?[8,14]:[0,0],frames:frames.map(g=>g.map(r=>r.join(''))),...extra});
function node(origin,discovered){
  const g=blank(), c=discovered?'y':'s', hi=discovered?'l':'w', shade=discovered?'d':'k';
  line(g,7,3,7,10,shade);line(g,8,2,8,11,c);line(g,9,3,9,8,hi);
  line(g,8,6,5,4,c);line(g,8,7,11,5,c);
  line(g,8,10,4,12,c);line(g,8,10,11,12,c);rect(g,5,13,6,1,shade);
  if(origin==='player'){rect(g,6,9,5,2,shade);line(g,6,9,8,7,hi);line(g,8,7,10,9,c);line(g,10,9,8,11,c);line(g,8,11,6,9,c)}
  return g;
}
function badge(g,status){
  rect(g,10,10,5,5,'k');
  if(status==='private'){rect(g,10,9,6,7,'k');rect(g,12,10,2,1,'w');rect(g,11,11,1,2,'w');rect(g,14,11,1,2,'w');rect(g,11,13,4,2,'w');put(g,13,13,'k')}
  if(status==='public'){line(g,12,11,12,13,'w');line(g,11,12,13,12,'w');put(g,12,10,'s');put(g,14,12,'s')}
  if(status==='invalid'){line(g,11,11,13,13,'r');line(g,11,13,13,11,'r')}
  return g;
}
for(const origin of ['ancient','player']){
  for(const status of ['undiscovered','discovered','public','private','invalid']){
    const discovered=status!=='undiscovered'&&status!=='invalid';
    const g=node(origin,discovered);
    if(['public','private','invalid'].includes(status))badge(g,status);
    add(`grace_${origin}_${status}`,'map',[g],{origin,state:status,visibleOnWorldMap:status!=='undiscovered'});
  }
}
const rest=blank();rect(rest,3,8,10,3,'s');rect(rest,4,7,3,1,'w');rect(rest,4,8,8,1,'w');line(rest,2,6,2,12,'k');line(rest,13,8,13,12,'k');add('grace_rest','ui',[rest]);
const travel=blank();rect(travel,3,7,9,2,'w');line(travel,9,4,12,7,'w');line(travel,12,8,9,11,'w');rect(travel,3,5,1,6,'s');add('grace_travel','ui',[travel]);
const ashes=blank();line(ashes,5,11,11,5,'s');line(ashes,6,10,12,4,'w');line(ashes,4,9,7,12,'y');line(ashes,3,12,4,13,'k');put(ashes,4,4,'s');put(ashes,6,3,'w');put(ashes,3,6,'s');add('grace_ashes','ui',[ashes],{semantic:'skill-management-entry',notRewardItem:true});
const flames=[];
for(let f=0;f<4;f++){
  const g=blank();rect(g,5,13,6,1,'a');rect(g,7,10,3,3,'y');rect(g,8,7,2,3,'b');
  line(g,8,10,6+(f%2),5+(f%2),'a');line(g,9,7,9+(f%2),3+(f%2),'b');
  put(g,10-(f%2),2+(f%2),'l');put(g,4+(f%2),8-f%2,'a');put(g,11,6+f%2,'a');
  rect(g,8,11,1,2,'l');flames.push(g);
}
add('grace_flame','fx',flames,{frameTicks:4,loop:true,worldSizeBlocks:[0.375,0.375],blend:'alpha',lighting:'optional-emissive',maxPlanesPerVisibleNode:2});
const ground=blank();line(ground,4,5,10,4,'a');line(ground,11,5,12,10,'a');line(ground,11,11,6,12,'a');line(ground,4,11,3,7,'a');rect(ground,6,7,4,2,'b');put(ground,8,6,'y');add('grace_ground','fx',[ground],{anchorPixels:[8,8],worldSizeBlocks:[0.5,0.5],surfaceOffsetBlocks:0.003,blend:'alpha',lighting:'optional-emissive'});

await fs.mkdir(out,{recursive:true});
const hashes=[];
for(const asset of assets){
  const png=new PNG({width:asset.width,height:asset.height});
  asset.frames.forEach((frame,f)=>frame.forEach((row,y)=>[...row].forEach((c,x)=>png.data.set(palette[c],((f*16+y)*16+x)*4))));
  const bytes=PNG.sync.write(png), target=path.join(out,asset.file);
  await fs.mkdir(path.dirname(target),{recursive:true});await fs.writeFile(target,bytes);
  hashes.push({file:asset.file,sha256:createHash('sha256').update(bytes).digest('hex'),bytes:bytes.length});
  const parsed=PNG.sync.read(bytes);assert(parsed.width===16);assert(parsed.height===16*asset.frames.length);
  for(let f=0;f<asset.frames.length;f++){
    let visible=0,transparent=0;
    for(let y=f*16;y<(f+1)*16;y++)for(let x=0;x<16;x++)parsed.data[(y*16+x)*4+3]?visible++:transparent++;
    assert(visible>10&&transparent>100,`${asset.id} frame${f}: empty or opaque background`);
  }
}
assert.equal(new Set(hashes.map(h=>h.sha256)).size,assets.length,'States must have distinct bytes');
const reuse=[];
for(const filename of ['smithing_stone_1.png','somber_smithing_stone_1.png','affinity_template_sacred.png','starlight_shard.png']){
  const file=`src/main/resources/assets/eldencombat/textures/item/${filename}`,bytes=await fs.readFile(path.join(root,file));
  const png=PNG.sync.read(bytes);reuse.push({file,width:png.width,height:png.height,sha256:createHash('sha256').update(bytes).digest('hex'),action:'reuse-existing-no-copy',notAshOfWar:filename.startsWith('affinity_')});
}
const manifest={schemaVersion:1,date:'2026-10-13',namespace:'eldencombat',runtimeIntegrated:false,inGameAccepted:false,provenance:{author:'Codex art department',method:'Original authored pixel grids; no third-party texture extraction',license:'CC0-1.0',source:'art/tooling/build-grace-pixel-art.mjs'},palette,assets,reuse,selection:{priority:['invalid','undiscovered','private-or-public','discovered'],originValues:['ancient','player'],undiscoveredMapVisible:false,permissionAuthority:'server',fallback:'hide unknown nodes; do not infer access from icon'},hashes};
await fs.writeFile(path.join(out,'assets.json'),JSON.stringify(manifest,null,2)+'\n');
await fs.writeFile(path.join(out,'LICENSE.txt'),'CC0 1.0 Universal (CC0-1.0)\nOriginal pixel grids authored for this project by Codex art department.\nNo third-party images or textures included.\nLicense text: https://creativecommons.org/publicdomain/zero/1.0/legalcode\n');
const labels={undiscovered:'未激活（仅列表）',discovered:'已发现',public:'公共',private:'私人',invalid:'失效'};
const native=`<aside style="margin-top:24px;border-top:1px solid #999;padding-top:16px;display:flex;gap:24px;flex-wrap:wrap">${['ancient','player'].flatMap(origin=>['private','public','invalid'].map(state=>`<div style="display:flex;gap:12px;align-items:center"><span>${origin==='ancient'?'古老':'自建'} · ${labels[state]}</span><img alt="16像素" src="textures/map/grace_${origin}_${state}.png" width="16" height="16" style="image-rendering:pixelated"><img alt="32像素" src="textures/map/grace_${origin}_${state}.png" width="32" height="32" style="image-rendering:pixelated"></div>`)).join('')}</aside>`;
const tiles=assets.map(a=>`<figure><div class="sprite ${a.kind==='fx'?'fx':''}" style="${a.frames.length>1?`background-image:url('${a.file}');background-size:96px ${a.height*6}px;animation:flame .8s steps(4) infinite;`:`background-image:url('${a.file}');background-size:96px 96px;`}"></div><figcaption>${a.kind==='map'?`${a.origin==='ancient'?'古老':'自建'} · ${labels[a.state]}`:({grace_rest:'休息',grace_travel:'旅行',grace_ashes:'战灰入口',grace_flame:'金色贴片 · 4帧',grace_ground:'地面贴片'}[a.id.split(':')[1]])}</figcaption></figure>`).join('');
const html=`<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>赐福像素资源</title><style>*{box-sizing:border-box}body{margin:0;font:14px system-ui;background:#ededeb;color:#292c2a;letter-spacing:0}header{padding:16px 24px;border-bottom:1px solid #aaa;display:flex;gap:20px;align-items:center;flex-wrap:wrap}h1{font-size:20px;margin:0}button{font:inherit;border:1px solid #999;background:#dedfdb;color:#292c2a;padding:6px 12px;cursor:pointer}button[aria-pressed=true]{background:#4b514e;color:white}main{padding:24px;max-width:1200px;margin:auto}section{display:grid;grid-template-columns:repeat(auto-fit,minmax(145px,1fr));gap:18px}figure{margin:0;display:flex;flex-direction:column;align-items:center;gap:12px;min-height:155px}figcaption{text-align:center} .sprite{width:96px;height:96px;image-rendering:pixelated;background-repeat:no-repeat}body.dark{background:#313734;color:#eee}footer{padding:20px 24px;border-top:1px solid #999;color:inherit}@keyframes flame{to{background-position:0 -384px}}body.paused .sprite{animation-play-state:paused!important}</style><header><h1>赐福像素资源</h1><button id="light" aria-pressed="true">浅色</button><button id="dark" aria-pressed="false">深色</button><button id="play" aria-pressed="false" title="暂停动画" aria-label="暂停动画">Ⅱ</button></header><main><section>${tiles}</section></main><footer>G-A1 · 原创资源草案 · 尚未游戏接入</footer><script>document.querySelector('#light').onclick=()=>{document.body.classList.remove('dark');light.setAttribute('aria-pressed','true');dark.setAttribute('aria-pressed','false')};document.querySelector('#dark').onclick=()=>{document.body.classList.add('dark');light.setAttribute('aria-pressed','false');dark.setAttribute('aria-pressed','true')};document.querySelector('#play').onclick=()=>{let p=document.body.classList.toggle('paused');play.setAttribute('aria-pressed',p);play.textContent=p?'▶':'Ⅱ';play.title=p?'播放动画':'暂停动画';play.setAttribute('aria-label',play.title)};</script></html>`;
await fs.writeFile(path.join(out,'preview.html'),html.replace('</section></main>',`</section>${native}</main>`));
const validation={date:manifest.date,assets:assets.length,mapStates:10,transparent:true,uniqueStateBytes:true,framePixelsValidated:true,runtimeIntegrated:false,browserVerified:false,sourceSha256:createHash('sha256').update(await fs.readFile(fileURLToPath(import.meta.url))).digest('hex')};
await fs.writeFile(path.join(out,'validation.json'),JSON.stringify(validation,null,2)+'\n');
console.log(`PASS ${assets.length} original sprites; 10 map states; 4-frame animation; no runtime resource writes.`);

if(process.argv.includes('--browser')){
  const {chromium}=require('playwright');const browser=await chromium.launch({channel:'msedge',headless:true});
  try{
    for(const viewport of [{width:1200,height:900},{width:390,height:844}]){
      const page=await browser.newPage({viewport});const errors=[];page.on('pageerror',e=>errors.push(String(e)));
      await page.goto(`file:///${path.join(out,'preview.html').replaceAll('\\','/')}`);await page.locator('#play').click();
      assert.equal(await page.locator('figure').count(),15);
      assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
      await page.screenshot({path:path.join(out,`preview-${viewport.width}-light.png`),fullPage:true});
      await page.locator('#dark').click();assert(await page.locator('body').evaluate(e=>e.classList.contains('dark')));
      await page.screenshot({path:path.join(out,`preview-${viewport.width}-dark.png`),fullPage:true});
      assert.deepEqual(errors,[]);await page.close();
    }
    validation.browserVerified=true;validation.browserViewports=[[1200,900],[390,844]];
    await fs.writeFile(path.join(out,'validation.json'),JSON.stringify(validation,null,2)+'\n');
    console.log('PASS Edge desktop/mobile layout and theme controls.');
  }finally{await browser.close()}
}
