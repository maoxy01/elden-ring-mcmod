import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createRequire} from 'node:module';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {palettes as extraPalettes,definitions as extraDefinitions,renderExtra} from './vfx-eight-clips.mjs';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const out=path.join(root,'art/vfx-2d');
const require=createRequire('C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json');
const {PNG}=require('pngjs');
const hash=b=>createHash('sha256').update(b).digest('hex');
const clamp=(v,a=0,b=1)=>Math.max(a,Math.min(b,v));
const noise=(x,y,f)=>((x*13+y*37+f*17+x*y*3)%29)/29;
const tau=Math.PI*2;
const palettes={
  ...extraPalettes,
  waterfowl:[[90,112,119],[149,176,181],[213,231,232],[248,252,243]],
  death_slash:[[24,19,26],[100,20,37],[193,33,58],[247,71,82],[255,159,141]],
  gold_wave:[[91,65,24],[159,107,27],[218,163,41],[249,207,79],[255,239,166]],
};
const alphaLevels=[0,24,48,96,160,224,255];
const quantAlpha=value=>alphaLevels.reduce((best,v)=>Math.abs(v-value)<Math.abs(best-value)?v:best,0);
function set(png,x,y,rgb,alpha=255){
  if(x<0||y<0||x>=png.width||y>=png.height)return;
  const i=(y*png.width+x)*4;png.data.set([...rgb,quantAlpha(alpha)],i);
}
function crescent(kind,frame){
  const png=new PNG({width:32,height:32}),p=(frame+.5)/16,death=kind==='death_slash';
  const cx=16,cy=18,rx=death?13:12,ry=death?11:10;
  const head=(-150+(death?200:310)*p)*Math.PI/180;
  const extent=(death?2.75:2.05)*Math.min(1,.32+p*4);
  const fade=p<.66?1:clamp((1-p)/.34);
  for(let y=1;y<31;y++)for(let x=1;x<31;x++){
    const dx=(x+.5-cx)/rx,dy=(y+.5-cy)/ry,r=Math.hypot(dx,dy);
    const behind=((head-Math.atan2(dy,dx))%tau+tau)%tau;
    if(behind>extent)continue;
    const along=behind/extent,taper=Math.pow(Math.sin(Math.PI*along),.62);
    const thick=(death?.36:.20)*taper;
    const inner=1-thick;
    if(r>=inner&&r<=1){
      const edge=(1-r)/Math.max(.01,thick),n=noise(x,y,frame);
      if(p>.70&&n>(1-p)*3.5)continue;
      const col=death?(edge<.22?3:edge<.38?2:0):(edge<.30?3:edge<.72?2:1);
      set(png,x,y,palettes[kind][col],255*fade);
    }else if(death&&r<inner&&r>inner-.19&&along>.15){
      // Pixel tongues sit behind the black blade; they are not a blurred halo.
      const tongue=Math.sin(along*27-frame*.7)*.045;
      if(r>inner-.08+tongue&&noise(x,y,frame)>.30)set(png,x,y,palettes[kind][behind<extent*.65?2:1],160*fade);
    }else if(!death&&r<inner&&r>inner-.10&&along>.4&&noise(x,y,frame)>.72){
      set(png,x,y,palettes[kind][0],96*fade);
    }
  }
  if(frame>3){
    for(let i=0;i<(death?4:2);i++){
      const a=head-extent*.7-i*.18,rad=.83+(i%2)*.08;
      const x=Math.round(cx+Math.cos(a)*rx*rad),y=Math.round(cy+Math.sin(a)*ry*rad);
      set(png,x,y,palettes[kind][death?4:3],96*fade);
    }
  }
  return upscale(png,2);
}
function gold(frame){
  const png=new PNG({width:32,height:16}),p=(frame+.5)/16;
  const fade=p<.78?1:clamp((1-p)/.22);
  for(let x=0;x<32;x++){
    // Every pair of logical columns is an independently clipped world lane.
    const leading=4+(noise(x,1,frame)>.80?1:0);
    set(png,x,leading,palettes.gold_wave[4],255*fade);
    set(png,x,leading+1,palettes.gold_wave[3],255*fade);
    set(png,x,leading+2,palettes.gold_wave[2],224*fade);
    for(let y=leading+3;y<15;y++){
      const age=(y-leading-3)/9,n=noise(x,y,frame);
      if(n>(.84-age*.65))continue;
      set(png,x,y,palettes.gold_wave[age<.3?2:1],(age<.3?160:96*(1-age))*fade);
    }
  }
  return upscale(png,2);
}
function upscale(source,factor){
  const dest=new PNG({width:source.width*factor,height:source.height*factor});
  for(let y=0;y<dest.height;y++)for(let x=0;x<dest.width;x++){
    const i=(Math.floor(y/factor)*source.width+Math.floor(x/factor))*4;
    dest.data.set(source.data.subarray(i,i+4),(y*dest.width+x)*4);
  }
  return dest;
}
const definitions=[
  {id:'waterfowl',skillId:'eldencombat:waterfowl_dance',kind:'WATERFOWL',name:'水鸟乱舞',frameSize:[64,64],anchorPixels:[32,36],worldSizeBlocks:[6,4.5],phaseRollDegrees:[-18,22,-8],sourceDefaults:{windupTicks:10,activeTicks:36,recoveryTicks:5,burstStartsActiveTicks:[0,12,24],visualEntityTicks:8,effectRadiusBlocks:3},plane:{basisU:[1,0,0],basisV:[0,1,0],normal:[0,0,1],cameraFacing:false,space:'caster-local; follow owner position; server-synced yaw; phase roll applied within plane',attachOffsetBlocks:[0,1.05,.35]}},
  {id:'death_slash',skillId:'eldencombat:destined_death',kind:'DEATH_SLASH',name:'命定之死',frameSize:[64,64],anchorPixels:[32,36],worldSizeBlocks:[7,5],phaseRollDegrees:[-28,20,-12],sourceDefaults:{windupTicks:8,activeTicks:24,recoveryTicks:5,burstStartsActiveTicks:[0,8,16],visualEntityTicks:8,effectRadiusBlocks:3.5},plane:{basisU:[1,0,0],basisV:[0,1,0],normal:[0,0,1],cameraFacing:false,space:'caster-local; follow owner position; server-synced yaw; phase roll applied within plane',attachOffsetBlocks:[0,1.1,.35]}},
  {id:'gold_wave',skillId:'eldencombat:wave_of_gold',kind:'GOLD_WAVE',name:'黄金波动',frameSize:[64,32],anchorPixels:[32,8],worldSizeBlocks:[6,2.8],sourceDefaults:{windupTicks:10,activeTicks:4,recoveryTicks:5,waveSpeedBlocksPerTick:.9,effectRangeBlocks:16,effectLifespanUpperBoundTicks:30,baseRadiusBlocks:3,maxRadiusBlocks:4.2},plane:{basisU:[1,0,0],basisV:[0,0,1],normal:[0,1,0],cameraFacing:false,space:'wave entity-local; +X lateral, +Z forward; PNG +Y maps to -Z',heightAboveGroundBlocks:.02},laneContract:{lanes:16,sourcePixelsPerLane:4,formationDistanceBlocks:4,edgeSpeedFactor:.55,lagGridBlocks:.125,worldWidth:'2 * visualRadius(partialTick)',worldDepthBlocks:2.8,uvRangeForLane:'[lane/16, (lane+1)/16]',blockedMask:'omit lane if ((blockedLanes >>> lane) & 1) != 0',lag:'floor(min(max(visualTravel,0),4) * .45 * abs(u)^2 / .125) * .125',stableGeometryAfterFormation:true,animationClock:'visualProgress(partialTick), never clamped profileTravel alone',doNotDrawAsSingleQuad:true}},
];
definitions.push(...extraDefinitions);
const skillCount=new Set(definitions.map(d=>d.skillId)).size;
assert.equal(skillCount,8);
await fs.mkdir(out,{recursive:true});
const hashes=[],checks=[],sources=[];
for(const relative of ['src/main/java/com/dsh/eldencombat/server/skills/RemembranceSkillSpec.java','src/main/java/com/dsh/eldencombat/server/skills/RemembranceRuntime.java','src/main/java/com/dsh/eldencombat/effects/WaveFront.java','src/main/java/com/dsh/eldencombat/client/effects/SkillSlashSpriteRenderer.java','src/main/java/com/dsh/eldencombat/client/effects/GoldenWaveRenderer.java','src/main/resources/assets/eldencombat/routes/2d.json']){
  sources.push({file:relative,sha256:hash(await fs.readFile(path.join(root,relative)))});
}
for(const def of definitions){
  const frames=[],raw=[],padding=2,cellW=def.frameSize[0]+padding*2,cellH=def.frameSize[1]+padding*2;
  const atlas=new PNG({width:cellW*4,height:cellH*4});
  let minVisible=Infinity,maxVisible=0;
  for(let i=0;i<16;i++){
    const png=def.id==='gold_wave'?gold(i):['waterfowl','death_slash'].includes(def.id)?crescent(def.id,i):renderExtra(def.id,i,{PNG,set,upscale,clamp,noise});
    const bytes=PNG.sync.write(png),file=`textures/particle/${def.id}/frame_${String(i).padStart(2,'0')}.png`;
    await fs.mkdir(path.dirname(path.join(out,file)),{recursive:true});await fs.writeFile(path.join(out,file),bytes);
    hashes.push({file,sha256:hash(bytes),bytes:bytes.length});raw.push(hash(bytes));
    const x=(i%4)*cellW+padding,y=Math.floor(i/4)*cellH+padding;
    for(let py=0;py<png.height;py++)for(let px=0;px<png.width;px++){
      const src=(py*png.width+px)*4;atlas.data.set(png.data.subarray(src,src+4),((y+py)*atlas.width+x+px)*4);
    }
    let visible=0,transparent=0;
    for(let py=0;py<png.height;py++)for(let px=0;px<png.width;px++){
      const k=(py*png.width+px)*4,a=png.data[k+3];a?visible++:transparent++;
      assert(alphaLevels.includes(a),'Unexpected smooth alpha');
      const macro=(Math.floor(py/2)*2*png.width+Math.floor(px/2)*2)*4;
      assert.deepEqual(png.data.subarray(k,k+4),png.data.subarray(macro,macro+4),'2x2 macro pixel changed');
      if(px===0||py===0||px===png.width-1||py===png.height-1){if(!def.laneContract)assert.equal(a,0,`${def.id}: clipped at frame border`)}
    }
    assert(visible>0&&transparent>png.width*png.height*.35,`${def.id} frame ${i}: empty frame or opaque background`);
    minVisible=Math.min(minVisible,visible);maxVisible=Math.max(maxVisible,visible);
    if(def.laneContract)for(let lane=0;lane<16;lane++){
      let found=false;for(let py=0;py<png.height;py++)for(let px=lane*4;px<(lane+1)*4;px++)found ||= png.data[(py*png.width+px)*4+3]>0;
      assert(found,`Missing wave lane ${lane} frame ${i}`);
    }
    frames.push({index:i,file,spriteId:`eldencombat:${def.id}/frame_${String(i).padStart(2,'0')}`,atlasRectPixels:[x,y,png.width,png.height],sampleProgress:(i+.5)/16});
  }
  assert.equal(new Set(raw).size,16,`${def.id}: repeated frames`);
  const atlasFile=`atlases/${def.id}.png`,atlasBytes=PNG.sync.write(atlas);
  await fs.mkdir(path.join(out,'atlases'),{recursive:true});await fs.writeFile(path.join(out,atlasFile),atlasBytes);
  hashes.push({file:atlasFile,sha256:hash(atlasBytes),bytes:atlasBytes.length});
  def.assetId=`eldencombat:vfx2d/${def.id}`;def.frameCount=16;def.frames=frames;
  def.anchorNormalized=def.anchorPixels.map((v,i)=>v/def.frameSize[i]);
  def.atlas={file:atlasFile,width:atlas.width,height:atlas.height,columns:4,rows:4,paddingPixels:padding,notRuntimeAtlas:true};
  def.sampling={filter:'nearest',pixelBlockSize:2,logicalFrameSize:def.frameSize.map(n=>n/2),frameIndex:'min(15, floor(clamp(progress,0,1) * 16))',partialTickRecommended:true,normalLifetimeExit:'draw nothing when progress >= 1; obey entity discard earlier'};
  def.palette=palettes[def.id];def.vertexTint=[255,255,255];def.tintChannels={bakedRgb:true,alpha:'opacity only',separateRecolorMask:null,warning:'Do not multiply colored RGB by bone/death/obsidian palette again'};
  def.timeline={normalizedStages:[{stage:'appear',from:0,to:.18,frames:[0,2]},{stage:'main',from:.18,to:.65,frames:[3,10]},{stage:'dissolve',from:.65,to:1,frames:[11,15]}],damageTriggers:false,hitFeedbackIncluded:false,opacity:'authored quantized alpha; no extra per-tick multiplier required',burstPlayback:def.laneContract?'sample wave entity progress; independent of cast recovery':`restart per confirmed event; normalize over visual entity lifetime (${def.sourceDefaults.visualEntityTicks} ticks); no synthetic followup input`};
  def.lod={normalMainPlanes:def.laneContract?16:1,core:'same frame and plane; omit secondary decorations, never explosion or boxes',hidden:'draw nothing'};
  def.provenance={method:'original parameterized pixel raster source, no provider pixels copied',license:'CC0-1.0',source:extraDefinitions.includes(def)?'art/tooling/vfx-eight-clips.mjs':'art/tooling/build-vfx-2d.mjs',influences:'Minecraft hard-edge finite palette and existing server event/profile contracts, not copied game textures'};
  checks.push({id:def.assetId,uniqueFrames:16,minVisiblePixels:minVisible,maxVisiblePixels:maxVisible,transparentBackground:true,macroPixels:true,allLanesPresent:def.laneContract?true:null});
}
const sourceFile='art/tooling/build-vfx-2d.mjs',sourceBytes=await fs.readFile(path.join(root,sourceFile));
hashes.push({file:sourceFile,scope:'workspace',sha256:hash(sourceBytes),bytes:sourceBytes.length});
for(const file of ['art/tooling/vfx-eight-clips.mjs','art/vfx-2d/preview.html','art/vfx-2d/preview-eight.js']){
  const bytes=await fs.readFile(path.join(root,file));hashes.push({file,scope:'workspace',sha256:hash(bytes),bytes:bytes.length});
}
const manifest={schemaVersion:1,date:'2026-10-13',status:'offline_samples_pending_art_review_and_runtime_integration',runtimeIntegrated:false,inGameAccepted:false,frameRateContract:'16 unique frames per 8-tick slash requires partial-tick sampling; tick-only progress cannot expose every frame',assets:definitions,hashes,sourceContracts:sources,sourceAudit:{existingFormalFrames:'Shared minecraft:explosion_0..15, rejected as main slash; old files left unchanged',existingArt:'art/skill-fx contains rendered previews, not replacement frame sequences',candidates:[{url:'https://foozlecc.itch.io/lucifer-effects',license:'CC0-1.0 declared by author page',version:'Lucifer Effects 1.0',status:'page reviewed; browser access/download not completed; package contents/license/hashes not verified; no pixels reused'},{url:'https://kenney.nl/assets/particle-pack',license:'CC0-1.0 declared by author page',version:'1.0',status:'80 512px particles; not admitted or downloaded; no main slash selected'},{url:'https://henrysoftware.itch.io/pixel-effects',license:'CC0-1.0 declared for assets',status:'paid download, not purchased or used'}],paidGeneration:false,thirdPartyPixelsIncluded:false},scope:{only:['WATERFOWL','DEATH_SLASH','GOLD_WAVE'],notChanged:['damage','FP','hit_windows','burst_count','skill_duration','Java','src/main/resources','Gradle','build','equipment','grace','map','UI']}};
manifest.schemaVersion=2;
manifest.status='eight_skill_art_delivery_pending_runtime_integration';
manifest.skillCount=skillCount;
manifest.clipCount=definitions.length;
manifest.scope.only=definitions.map(d=>d.kind);
manifest.scope.authorization='User requested all eight remembrance skill effects on 2026-10-13; supersedes three-sample limit.';
manifest.reviewScreenshots={status:'old preview-1440 images describe the backed-up three-sample batch, not this batch',currentBrowserVerification:false};
await fs.writeFile(path.join(out,'manifest.json'),JSON.stringify(manifest,null,2)+'\n');
await fs.writeFile(path.join(out,'pixel-source.json'),JSON.stringify({schemaVersion:1,source:sourceFile,sha256:hash(sourceBytes),canvasPixelBlock:2,alphaLevels,palettes,parameters:definitions.map(({id,frameSize,anchorPixels})=>({id,frameSize,anchorPixels})),negativeDesign:['no explosion retexture','no smooth blur','no stacked cross strips','no camera-facing slash','no monolithic unclipppable golden wave']},null,2)+'\n');
await fs.writeFile(path.join(out,'LICENSE.txt'),'CC0 1.0 Universal (CC0-1.0)\nOriginal sprites authored for this project by Codex art department.\nNo third-party pixels included; source candidates are not bundled.\nLegal text: https://creativecommons.org/publicdomain/zero/1.0/legalcode\n');
const lag=(u,d)=>Math.floor(clamp(d,0,4)*.45*Math.abs(u)**2/.125)*.125;
for(let i=0;i<16;i++){const u=-1+(i+.5)*2/16;assert.equal(lag(u,4),lag(u,16),'Wave front keeps deforming after formation')}
await fs.writeFile(path.join(out,'preview-data.js'),'window.VFX_ASSETS = '+JSON.stringify(definitions)+';\n');
await fs.writeFile(path.join(out,'validation.json'),JSON.stringify({date:manifest.date,staticPass:true,skills:skillCount,assets:definitions.length,sequencePngs:definitions.length*16,reviewAtlases:definitions.length,checks,waveFormationStable:true,browserVerified:false,runtimeIntegrated:false,inGameAccepted:false},null,2)+'\n');
console.log(`PASS ${skillCount} skills / ${definitions.length} clips / ${definitions.length*16} unique-per-clip frames / alpha, macro-pixel, lane checks; no runtime writes.`);
