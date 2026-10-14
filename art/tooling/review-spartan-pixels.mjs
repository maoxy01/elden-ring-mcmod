import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createRequire} from 'node:module';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const out=path.join(root,'docs/research-evidence/spartan-art-20261013');
const require=createRequire('C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json');
const {PNG}=require('pngjs');
const evidence=JSON.parse(await fs.readFile(path.join(out,'evidence.json'),'utf8'));
const stats=[];
for(const sample of evidence.samples){
  const bytes=await fs.readFile(path.join(out,sample.researchPng));
  assert.equal(createHash('sha256').update(bytes).digest('hex'),sample.textureSha256);
  const png=PNG.sync.read(bytes),colors=new Map(),alpha=new Set();let minX=png.width,minY=png.height,maxX=-1,maxY=-1,transparent=0;
  for(let y=0;y<png.height;y++)for(let x=0;x<png.width;x++){
    const rgba=Array.from(png.data.subarray((y*png.width+x)*4,(y*png.width+x)*4+4));alpha.add(rgba[3]);
    if(!rgba[3]){transparent++;continue}minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);
    const key=rgba.join(',');colors.set(key,(colors.get(key)||0)+1);
  }
  const result={id:sample.recipeResult?.id||null,basename:sample.resourceBasename,width:png.width,height:png.height,visibleBoundsInclusive:[minX,minY,maxX,maxY],visibleColors:colors.size,alphaValues:[...alpha].sort((a,b)=>a-b),transparentPixels:transparent,topColors:[...colors].sort((a,b)=>b[1]-a[1]).slice(0,6),palmContactVerified:false};
  stats.push(result);console.log(JSON.stringify(result));
}
await fs.writeFile(path.join(out,'pixel-stats.json'),JSON.stringify({date:'2026-10-13',jarSha256:evidence.jarSha256,stats,runtimeIntegrated:false},null,2)+'\n');
const figures=evidence.samples.map((s,i)=>`<figure><img src="${s.researchPng}" width="${stats[i].width*6}" height="${stats[i].height*6}" alt="${s.resourceBasename}"><figcaption>${s.resourceBasename}<br>${stats[i].width}×${stats[i].height} · ${stats[i].visibleColors}色</figcaption></figure>`).join('');
await fs.writeFile(path.join(out,'preview.html'),`<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Spartan 发布包像素取证</title><style>*{box-sizing:border-box}body{margin:0;background:#d9ddda;color:#292e2b;font:14px system-ui;letter-spacing:0}h1{font-size:20px;padding:20px;margin:0;border-bottom:1px solid #999}main{padding:24px;display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:24px}figure{margin:0;display:flex;align-items:center;flex-direction:column;gap:16px;min-height:250px}img{image-rendering:pixelated;max-width:100%;object-fit:contain}figcaption{text-align:center;overflow-wrap:anywhere}footer{padding:20px;border-top:1px solid #999}</style><h1>Spartan 1.2.3 · 发布包原始贴图</h1><main>${figures}</main><footer>仅研究 · 未准入运行素材 · 原作者 ObliviousSpartan / xwerffx · 移植 Mai_xiyu<br>官方文件：<a href="${evidence.officialFile}">CurseForge 文件页</a> · 许可声明 Apache License v2.0，独立素材例外未放行</footer></html>`);
if(process.argv.includes('--browser')){
  const {chromium}=require('playwright'),browser=await chromium.launch({channel:'msedge',headless:true});
  try{const page=await browser.newPage({viewport:{width:1100,height:900}});await page.goto(`file:///${path.join(out,'preview.html').replaceAll('\\','/')}`);assert(await page.locator('img').evaluateAll(imgs=>imgs.every(i=>i.complete&&i.naturalWidth>0)));await page.screenshot({path:path.join(out,'research-preview.png'),fullPage:true})}finally{await browser.close()}
}
