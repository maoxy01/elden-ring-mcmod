import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {PNG} from './equipment-pixels.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),out=path.join(root,'art/ui-pixel');
await fs.mkdir(path.join(out,'textures'),{recursive:true});
const hash=b=>createHash('sha256').update(b).digest('hex');
const colors={d:[43,47,49],s:[107,122,129],w:[224,229,218],g:[183,149,77],b:[63,127,208],l:[159,195,240]};
const blank=()=>Array.from({length:16},()=>Array(16).fill('.'));
const put=(p,x,y,c)=>{if(x>=0&&y>=0&&x<16&&y<16)p[y][x]=c};
function line(p,x,y,xx,yy,c){const n=Math.max(Math.abs(xx-x),Math.abs(yy-y));for(let i=0;i<=n;i++)put(p,Math.round(x+(xx-x)*i/n),Math.round(y+(yy-y)*i/n),c)}
const slash=blank();
for(const [x,y,xx,yy] of [[4,3,10,3],[10,3,13,6],[13,6,13,10],[13,10,10,12],[10,12,5,12],[5,12,2,9],[2,9,2,6]])line(slash,x,y,xx,yy,'s');
line(slash,5,2,10,2,'w');line(slash,12,5,14,7,'w');line(slash,14,7,11,7,'w');line(slash,5,10,10,5,'w');line(slash,4,9,6,11,'g');line(slash,3,12,4,11,'g');
const thrust=blank();line(thrust,3,12,11,4,'s');line(thrust,4,12,12,4,'w');line(thrust,10,4,13,2,'w');line(thrust,13,2,12,6,'w');line(thrust,3,10,5,12,'g');line(thrust,2,13,3,12,'g');line(thrust,2,7,6,3,'s');line(thrust,8,13,12,9,'s');
const stamp=blank();line(stamp,8,12,8,3,'w');line(stamp,9,12,9,3,'s');line(stamp,8,3,10,5,'w');line(stamp,8,3,6,5,'w');line(stamp,6,10,11,10,'g');line(stamp,3,12,6,12,'s');line(stamp,3,11,3,13,'w');line(stamp,2,14,7,14,'g');line(stamp,11,8,13,5,'s');line(stamp,13,5,13,2,'w');
const fp=blank();line(fp,8,2,4,8,'b');line(fp,8,2,12,8,'b');line(fp,4,8,8,13,'b');line(fp,12,8,8,13,'b');for(let y=5;y<=10;y++)line(fp,7,y,9,y,'b');line(fp,8,4,6,8,'l');line(fp,6,8,8,10,'l');
const assets=[];
for(const [id,name,grid,semantic] of [['spinning_slash','回旋斩',slash,'broken rotating sweep with diagonal blade'],['heavy_thrust','重刺',thrust,'forward diagonal spear point with parallel motion strokes'],['stamp_upward_cut','踏前上斩',stamp,'vertical blade with upward motion and planted foot'],['fp','专注值',fp,'blue focus droplet']]){
  // One dark pixel outline protects the same silhouette on light and dark surfaces.
  const outlined=grid.map(r=>[...r]);for(let y=1;y<15;y++)for(let x=1;x<15;x++)if(grid[y][x]!=='.')for(const [dx,dy] of [[0,1],[0,-1],[1,0],[-1,0]])if(grid[y+dy][x+dx]==='.')outlined[y+dy][x+dx]='d';
  for(const n of [16,32]){const png=new PNG({width:n,height:n});for(let y=0;y<n;y++)for(let x=0;x<n;x++){const c=outlined[Math.floor(y*16/n)][Math.floor(x*16/n)];if(c!=='.')png.data.set([...colors[c],255],(y*n+x)*4)}const bytes=PNG.sync.write(png),file=`textures/${id}_${n}.png`;await fs.writeFile(path.join(out,file),bytes);assets.push({id:id==='fp'?'ui:fp':`eldencombat:${id}`,name,file,size:[n,n],sha256:hash(bytes),semantic,anchorPixels:[0,0],centerPixels:[n/2,n/2],state:'normal',suggestedResource:`eldencombat:textures/ui/${id}${n===32?'_32':''}.png`,scalePolicy:n===32?'Exact nearest 2x master, not extra invented detail':'Native GUI master'})}
}
const sourcePaths=['src/main/java/com/dsh/eldencombat/client/CombatHud.java','src/main/java/com/dsh/eldencombat/client/PresentationRoutes.java','src/main/java/com/dsh/eldencombat/client/grace/GraceScreen.java','src/main/java/com/dsh/eldencombat/server/skills/OrdinaryAshCatalog.java','src/main/resources/assets/eldencombat/routes/2d.json'];
const evidence=[];for(const file of sourcePaths)evidence.push({file,sha256:hash(await fs.readFile(path.join(root,file)))});
const reused=[];for(const dir of ['art/grace-pixel/textures/ui','art/grace-pixel/textures/map','art/grace-pixel/textures/fx','src/main/resources/assets/eldencombat/textures/item'])for(const file of await fs.readdir(path.join(root,dir)))if(file.endsWith('.png')){const f=`${dir}/${file}`;reused.push({file:f,sha256:hash(await fs.readFile(path.join(root,f))),policy:'reference_existing_unchanged'})}
const manifest={date:'2026-10-15',assets,reused,sourceEvidence:evidence,provenance:{method:'Original authored pixel grids',license:'CC0-1.0',source:'art/tooling/build-ui-pixel.mjs'},fp:{reuse:'CombatHud.drawBar',width:150,height:6,border:1,fill:'#3f7fd0',empty:'#2a2a2a',anchor:'x=guiWidth/2-75; fpY=guiHeight-92',iconCurrent:'x-18, fpY',iconRecommended:'x-18, fpY-5 (align 16px icon center to 6px bar center)',previewFractions:[0,.1,1],lowThreshold:'preview sample only; no new gameplay threshold'},selector:{width:330,height:210,rowWidth:150,rowHeight:16,rowPitch:18,iconProposal:[2,0,16,16],textProposal:{x:20,width:126},entryReuse:'art/grace-pixel/textures/ui/grace_ashes.png',states:'Use vanilla button hover/disabled/focus rendering; no additional PNG states'},runtimeIntegrated:false,inGameAccepted:false};
await fs.writeFile(path.join(out,'assets.json'),JSON.stringify(manifest,null,2)+'\n');await fs.writeFile(path.join(out,'preview-data.js'),'window.UI_ASSETS='+JSON.stringify(manifest)+';\n');
await fs.writeFile(path.join(out,'LICENSE.txt'),'New textures in this directory: CC0-1.0. Original authored pixel grids. Referenced existing resources retain their original provenance; no third-party artwork copied.\n');
console.log(`Generated ${assets.length} PNGs; referenced ${reused.length} existing PNGs unchanged.`);
