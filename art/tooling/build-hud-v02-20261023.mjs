import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {PNG} from './equipment-pixels.mjs';
const root='D:/codexout/mcmod',out=path.join(root,'art/ui-pixel/hud-v02-20261023');
const protectionFile=path.join(out,'protection.json');
if(fs.existsSync(protectionFile))for(const item of JSON.parse(fs.readFileSync(protectionFile))){const actual=createHash('sha256').update(fs.readFileSync(path.join(root,item.file))).digest('hex');if(actual!==item.sha256)throw Error('Protected source changed; review before rebuilding: '+item.file);}
fs.mkdirSync(path.join(out,'textures'),{recursive:true});
fs.mkdirSync(path.join(out,'reference'),{recursive:true});
fs.mkdirSync(path.join(out,'qa'),{recursive:true});
const rgb=h=>[0,2,4].map(i=>parseInt(h.slice(i,i+2),16));
const pixel=(p,x,y,c)=>{if(x>=0&&y>=0&&x<p.width&&y<p.height)p.data.set([...rgb(c),255],(y*p.width+x)*4);};
const rect=(p,x,y,w,h,c)=>{for(let yy=y;yy<y+h;yy++)for(let xx=x;xx<x+w;xx++)pixel(p,xx,yy,c);};
const assets=[];
function save(name,p,kind='original'){const file='textures/'+name+'.png',bytes=PNG.sync.write(p);fs.writeFileSync(path.join(out,file),bytes);assets.push({id:name,file,width:p.width,height:p.height,sha256:createHash('sha256').update(bytes).digest('hex'),kind});}
function bar(name,w,h,color,empty){const p=new PNG({width:w,height:h});rect(p,0,0,w,h,'080b0d');rect(p,1,1,w-2,h-2,empty);for(let x=9;x<w-1;x+=8)rect(p,x,1,1,h-2,'11171b');save(name+'_background',p);const fill=new PNG({width:w-2,height:h-2});rect(fill,0,0,fill.width,fill.height,color[1]);rect(fill,0,0,fill.width,1,color[0]);rect(fill,0,fill.height-1,fill.width,1,color[2]);for(let x=8;x<fill.width;x+=8)rect(fill,x,0,1,fill.height,'11171b');save(name+'_fill',fill);}
bar('fp_bar',81,7,['6caaf3','3f7fd0','285296'],'172539');
bar('stamina_bar',81,7,['ffe38a','dbb849','927327'],'302b19');
bar('boss_bar',182,6,['f25a53','d32d2c','8d1d24'],'292a2c');
function icon(name,rows,palette){const p=new PNG({width:rows[0].length,height:rows.length});rows.forEach((r,y)=>[...r].forEach((c,x)=>{if(palette[c])pixel(p,x,y,palette[c]);}));save(name,p);}
icon('focus_small',['...d....','..dld...','.dlbbd..','dlbbbbd.','dlbbbbd.','.dbbbd..','..ddd...','........'],{d:'13202e',l:'a5d9ff',b:'4389d9'});
icon('stamina_small',['....dd..','...dgd..','..dggd..','.dggddd.','..dggd..','..dgd...','.dgd....','..d.....'],{d:'3b3218',g:'edd269'});
icon('low_resource',['...d...','..dwd..','..dwd..','.dwwwd.','.dwdwd.','dwwwwwd','ddddddd'],{d:'1d1e19',w:'f0ce68'});
icon('marker_player',['..ddd..','.dwwwd.','dwwwwwd','dwwwwwd','dwwwwwd','.dwwwd.','..ddd..'],{d:'101416',w:'ffffff'});
icon('marker_enemy',['ddd','drd','ddd'],{d:'48181d',r:'eb5146'});
const map=new PNG({width:60,height:60});rect(map,0,0,60,60,'111416');rect(map,1,1,58,58,'b9c0b7');rect(map,2,2,56,56,'404b47');rect(map,3,3,54,54,'111416');for(let y=4;y<56;y++)for(let x=4;x<56;x++)map.data[(y*60+x)*4+3]=0;save('minimap_frame',map);
const status=new PNG({width:18,height:18});rect(status,0,0,18,18,'111416');rect(status,1,1,16,16,'6c7674');rect(status,2,2,14,14,'29312f');save('status_frame',status);
icon('status_poison',['................','......dddd......','.....dggggd.....','.....dggggd.....','......dggd......','.....dggggd.....','....dgwwgggd....','...dgwwgggggd...','...dgwwgggggd...','...dggggggggd...','...dggggggggd...','....dggggggd....','.....dddddd.....','................','................','................'],{d:'182a20',g:'73b95e',w:'c8e493'});
icon('status_bleed',['................','.......dd.......','......drrd......','.....drrrdd.....','.....drrrrd.....','....drrlrrrd....','....drllrrrd....','...drrllrrrrd...','...drrllrrrrd...','...drrrrrrrrd...','....drrrrrrd....','.....drrrrd.....','......dddd......','................','................','................'],{d:'371b22',r:'ca474c',l:'f29485'});
const demo=new PNG({width:52,height:52});
for(let y=0;y<52;y++)for(let x=0;x<52;x++){
 let c=['637a42','6e8648','5d773e'][Math.floor(x/8)%3];if(y>35&&x<18)c='536d39';if(x>36&&y<15)c='91938a';
 const river=24+Math.floor(Math.sin(y/9)*4);if(Math.abs(x-river)<4)c=Math.abs(x-river)>2?'b6b793':'437bac';
 if((y>13&&y<17&&x<21)||(x>12&&x<16&&y>13&&y<39))c='b3a37b';pixel(demo,x,y,c);
}
for(const [x,y] of [[5,6],[10,27],[35,34],[41,24],[32,6]]){rect(demo,x,y,5,5,'304f32');rect(demo,x+1,y,3,3,'3c613b');}
for(const [x,y] of [[4,19],[17,29]]){rect(demo,x,y,7,6,'514638');rect(demo,x+1,y+1,5,3,'8a7758');rect(demo,x+3,y+4,1,2,'282c27');}
save('map_demo',demo,'preview_only_original');
const source='C:/下载/方案三：原版 MC 风格@2x.png';if(!fs.existsSync(path.join(out,'reference/user-layout.png')))fs.copyFileSync(source,path.join(out,'reference/user-layout.png'));
const scenery='docs/combat-evidence/vfx-semantics-correction-20261017/vfxfixed-01-gold_breaker-primary1-clean.png';fs.copyFileSync(path.join(root,scenery),path.join(out,'reference/scene.png'));
const reuse=[['art/ui-pixel/textures/fp_16.png','reference/fp_16.png'],['art/grace-pixel/textures/map/grace_ancient_discovered.png','reference/grace_discovered.png']];for(const [from,to] of reuse)fs.copyFileSync(path.join(root,from),path.join(out,to));
const selection=JSON.parse(fs.readFileSync(path.join(root,'art/equipment-2d/V0.2/manifest.json')));
const hotbarIds=['marikas_hammer','malikeths_black_blade','hand_of_malenia','mohgwyns_sacred_spear','great_shield','morgotts_cursed_sword','glintstone_staff','great_bow','test_flask'];
const equipment=[];fs.mkdirSync(path.join(out,'reference/items'),{recursive:true});for(const id of hotbarIds){const item=selection.items.find(i=>i.id===id),sprite=item.textures.find(t=>t.state==='gui_16');const file='reference/items/'+id+'.png';fs.copyFileSync(path.join(root,'art/equipment-2d/V0.2',sprite.file),path.join(out,file));equipment.push({id,name:item.name,file});}
const protectedFiles=[];
function protectTree(relative){for(const f of fs.readdirSync(path.join(root,relative),{withFileTypes:true})){const name=relative+'/'+f.name;if(f.isDirectory())protectTree(name);else protectedFiles.push({file:name,sha256:createHash('sha256').update(fs.readFileSync(path.join(root,name))).digest('hex')});}}
protectTree('art/equipment-2d/V0.2');protectTree('src/main/resources/assets/eldencombat');for(const f of ['src/main/java/com/dsh/eldencombat/client/CombatHud.java','src/main/java/com/dsh/eldencombat/client/CombatHudLayout.java'])protectedFiles.push({file:f,sha256:createHash('sha256').update(fs.readFileSync(path.join(root,f))).digest('hex')});
const manifest={date:'2026-10-23',status:'art_candidate',runtimeIntegrated:false,assets,equipment,background:{file:'reference/scene.png',source:scenery},reference:'reference/user-layout.png',logicalUnits:'GUI-scaled pixels',layout:{hotbar:{width:182,height:22,bottom:2},experience:{width:182,height:5,bottom:25},stats:{rowPitch:10,iconWidth:9,iconPitch:8,columns:2,columnWidth:81},resourceBars:{width:81,height:7,left:'FP',right:'stamina',labelHeight:9,labelGap:2},itemName:{height:10,gap:6,maxWidth:182},map:{left:8,top:8,size:60,coordinatesGap:4},boss:{top:20,width:182,height:6,nameY:8},status:{right:8,top:8,size:18,pitch:20}},reused:[{name:'vanilla HUD icons and font',source:'local Minecraft 1.21.1 client.jar',usage:'private preview; runtime uses minecraft namespace'},{name:'project FP icon and grace marker',source:'art/ui-pixel and art/grace-pixel',usage:'existing local assets'},{name:'Raised',url:'https://github.com/yurisuika/Raised',license:'LGPL-3.0',usage:'reviewed optional HUD-layer positioning mod; not copied or installed'},{name:'FTB Chunks',url:'https://github.com/FTBTeam/FTB-Chunks',license:'All Rights Reserved',usage:'reviewed only, not an open-source reuse dependency'}]};
manifest.layout.resourceBars={width:81,height:7,left:'FP',right:'stamina_or_oxygen',labelsVisible:false,valuesVisible:false,fpArmorOffsetY:-9,fpArmorGap:2,staminaHealthOffsetY:-10};
manifest.layout.oxygen={width:81,height:9,y:'healthY-10',visibleWhen:'underwater',replaces:'stamina',staminaWarningVisibleUnderwater:false};
manifest.layout.itemName={height:10,armorOffsetY:-42,maxWidth:182};
manifest.layout.boss.nameY=7;
const embeddedImages={};const previewFiles=[...assets.map(a=>a.file),...equipment.map(e=>e.file),'reference/scene.png','reference/fp_16.png','reference/grace_discovered.png'];
const vanillaDir=path.join(out,'reference/vanilla');if(fs.existsSync(vanillaDir))for(const f of fs.readdirSync(vanillaDir))if(f.endsWith('.png'))previewFiles.push('reference/vanilla/'+f);
for(const file of previewFiles)embeddedImages[file]='data:image/png;base64,'+fs.readFileSync(path.join(out,file)).toString('base64');
const fontGlyphs={},characters=new Set([...equipment.map(e=>e.name).join('')+'恶兆玛尔基特专注耐力超长武器名称测试与被诅咒的剑']);
if(fs.existsSync(path.join(vanillaDir,'unifont.hex')))for(const line of fs.readFileSync(path.join(vanillaDir,'unifont.hex'),'utf8').split('\n')){const [code,bits]=line.trim().split(':');if(!bits)continue;const char=String.fromCodePoint(parseInt(code,16));if(characters.has(char))fontGlyphs[char]={width:bits.length/4,height:16,bits};}
manifest.fontPreview={source:'local Minecraft GNU Unifont bitmap',renderScale:2,cjkGlyphs:Object.keys(fontGlyphs).length,runtimeFont:'Minecraft mc.font'};
fs.writeFileSync(path.join(out,'manifest.json'),JSON.stringify(manifest,null,2));fs.writeFileSync(path.join(out,'data.js'),'window.HUD_PACKAGE='+JSON.stringify({...manifest,embeddedImages,fontGlyphs})+';');
fs.writeFileSync(path.join(out,'protection.json'),JSON.stringify(protectedFiles,null,2));
console.log(JSON.stringify({assets:assets.length,equipment:equipment.length,protectedFiles:protectedFiles.length,out}));
