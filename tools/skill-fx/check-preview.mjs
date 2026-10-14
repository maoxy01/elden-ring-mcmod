import fs from 'node:fs/promises';
import path from 'node:path';
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
const require=createRequire('C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json');
const budget=JSON.parse(await fs.readFile('art/skill-fx/budget-report.json','utf8'));
if(budget.schemaVersion!==1||budget.status!=='passed'||budget.effects.length!==11||budget.samples<1000)
  throw Error('Missing/incomplete VFX budget gate');
for(const effect of budget.effects) {
  if(effect.phases.length!==3)throw Error('Missing VFX phase: '+effect.kind);
  for(const phase of effect.phases) {
    for(const key of ['boxes','quads','submittedVertices','sprites','debris'])
      if(!Number.isInteger(phase[key])||phase[key]<0)throw Error('Invalid budget count: '+effect.kind+'/'+key);
    if(phase.boxes>96||phase.quads!==phase.boxes*6||phase.submittedVertices>phase.quads*4+phase.sprites*4||phase.debris>12
      ||phase.sprites>(effect.kind==='WATERFOWL'?12:6))throw Error('VFX budget exceeded: '+effect.kind);
  }
}
if(budget.eightEntitySubmittedVerticesUpperEstimate>budget.limits.frameVertices)throw Error('Eight-entity VFX budget exceeded');
for(const source of ['SkillEffectMesh','SkillSlashSprites']) {
  const code=await fs.readFile('src/main/java/com/dsh/eldencombat/effects/'+source+'.java','utf8');
  if(/0x[0-9a-fA-F]{6,8}\b/.test(code))throw Error(source+': hard-coded RGB color bypasses the palette');
}
const {chromium}=require('playwright'),{PNG}=require('pngjs');
const root=path.resolve('art/skill-fx'),browser=await chromium.launch({channel:'msedge',headless:true,args:['--allow-file-access-from-files']});
const page=await browser.newPage({viewport:{width:1440,height:1024},deviceScaleFactor:1});
const errors=[];page.on('pageerror',error=>errors.push(error.message));
const results=[];
async function snapshot(name){
  const target=path.join(root,name+'.png');await page.screenshot({path:target,fullPage:true});
  const png=PNG.sync.read(await fs.readFile(target));let colorful=0;
  for(let y=100;y<png.height;y++)for(let x=0;x<png.width;x++){
    const i=(y*png.width+x)*4,r=png.data[i],g=png.data[i+1],b=png.data[i+2];
    if(Math.max(r,g,b)-Math.min(r,g,b)>50)colorful++;
  }
  const state=await page.evaluate(()=>window.FX_PREVIEW);
  if(!state?.effects.every(e=>e.boxes+e.sprites>0))throw Error(name+': geometry not displayed');
  const frame=state.frame;
  await page.locator('#time').evaluate(input=>{input.max='20';input.value='20';});
  await page.waitForFunction(()=>window.FX_PREVIEW?.frame===20);
  const empty=PNG.sync.read(await page.screenshot({fullPage:true}));let changed=0;
  for(let y=100;y<png.height;y++)for(let x=0;x<png.width;x++){
    const i=(y*png.width+x)*4;
    if(Math.max(...[0,1,2].map(c=>Math.abs(png.data[i+c]-empty.data[i+c])))>8)changed++;
  }
  await page.locator('#time').evaluate((input,frame)=>{input.max='19';input.value=String(frame);},frame);
  await page.waitForFunction(frame=>window.FX_PREVIEW?.frame===frame,frame);
  if(changed<80)throw Error(name+': effect canvas is blank');
  const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth);
  if(overflow)throw Error(name+': horizontal overflow');
  results.push({name,colorful,changed,state});
}
try{
  await page.goto(pathToFileURL(path.join(root,'preview.html')).href);
  await page.waitForFunction(()=>window.FX_PREVIEW?.effects.length===8);
  if(await page.locator('#effect option').filter({hasText:'横斩'}).count())throw Error('Removed golden swing is still selectable');
  if(await page.evaluate(()=>window.SKILL_FX_PACK.some(e=>e.kind==='GOLD_SWING')))throw Error('Removed golden swing is still exported');
  await page.locator('#playing').uncheck();await page.locator('#time').fill('4');
  await page.waitForFunction(()=>window.FX_PREVIEW?.frame===4);await snapshot('overview-desktop');
  for(const id of ['WATERFOWL','DEATH_SLASH','CURSED_SLASH']){
    await page.locator('#effect').selectOption(id);
    await page.locator('#time').fill('8');
    await page.waitForFunction(id=>window.FX_PREVIEW?.effects[0]?.id===id&&window.FX_PREVIEW.frame===8,id);
    await snapshot(id.toLowerCase()+'-vanilla-desktop');
    if(!await page.evaluate(()=>window.FX_PREVIEW.effects[0].sprites>0))throw Error(id+': textured strips missing');
  }
  for(const id of ['GOLD_IMPACT','GRAVITY_PULL','GRAVITY_IMPACT']){
    await page.locator('#effect').selectOption(id);
    await page.locator('#time').fill('8');
    await page.waitForFunction(id=>window.FX_PREVIEW?.effects[0]?.id===id&&window.FX_PREVIEW.frame===8,id);
    await snapshot(id.toLowerCase()+'-irregular-desktop');
  }
  await page.locator('#effect').selectOption('BLOOD_RITUAL');
  await page.waitForFunction(()=>window.FX_PREVIEW?.effects[0]?.id==='BLOOD_RITUAL');await snapshot('blood-ritual-desktop');
  await page.locator('#time').fill('12');await page.waitForFunction(()=>window.FX_PREVIEW?.frame===12);
  await page.locator('#playing').check();const first=await page.evaluate(()=>window.FX_PREVIEW.frame);
  await page.waitForFunction(frame=>window.FX_PREVIEW.frame!==frame,first);
  await page.locator('#playing').uncheck();
  await page.mouse.move(650,420);await page.mouse.down();await page.mouse.move(760,460,{steps:10});await page.mouse.up();
  await page.locator('#effect').selectOption('GOLD_WAVE');
  await page.locator('#time').fill('12');await page.waitForFunction(()=>window.FX_PREVIEW?.effects[0]?.id==='GOLD_WAVE'&&window.FX_PREVIEW.frame===12);
  await snapshot('gold-wave-propagation-desktop');
  const fixedFront=await page.evaluate(()=>{
    const frames=window.SKILL_FX_PACK.find(e=>e.kind==='GOLD_WAVE').frames;
    return JSON.stringify(frames[5])===JSON.stringify(frames[12])&&JSON.stringify(frames[12])===JSON.stringify(frames[19]);
  });
  if(!fixedFront)throw Error('gold wave keeps deforming after formation');
  await page.locator('#time').fill('19');await page.waitForFunction(()=>window.FX_PREVIEW.frame===19);
  await snapshot('gold-wave-fixed-front-desktop');
  await page.setViewportSize({width:390,height:844});await page.locator('#effect').selectOption('all');
  await page.locator('#time').fill('4');await page.waitForFunction(()=>window.FX_PREVIEW?.effects.length===8&&window.FX_PREVIEW.frame===4);
  await snapshot('overview-mobile');
  if(errors.length)throw Error(errors.join('\n'));
  await fs.writeFile(path.join(root,'qa.json'),JSON.stringify({status:'passed',scope:'Browser render of native geometry and strips with local vanilla textures, NOT in-game shader/animation validation',results,errors},null,2)+'\n');
  console.log(JSON.stringify({status:'passed',images:results.map(e=>e.name),errors}));
}finally{await browser.close();}
