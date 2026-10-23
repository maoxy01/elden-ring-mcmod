'use strict';
const pack=window.HUD_PACKAGE,canvas=document.querySelector('#preview'),display=canvas.getContext('2d'),scene=document.createElement('canvas'),ctx=scene.getContext('2d');
const images={},glyphs=new Map();
const state={width:480,height:270,health:18,maxHealth:20,armor:18,food:18,fp:60,maxFp:90,stamina:92,maxStamina:120,boss:68,showBoss:true,showMap:true,underwater:false,effects:false,selected:0,view:'full',nameOverride:'',bossName:'恶兆 玛尔基特'};
const clamp=(n,min,max)=>Math.max(min,Math.min(max,Number.isFinite(Number(n))?Number(n):min));
const color=(c)=>{ctx.fillStyle=c;};
function image(file,x,y,w,h){const im=images[file];if(!im)throw Error('Missing '+file);ctx.drawImage(im,Math.round(x),Math.round(y),w??im.width,h??im.height);}
const vanilla=(name,x,y,w,h)=>image('reference/vanilla/'+name+'.png',x,y,w,h);
function glyph(char,tint){
 const key=char+tint;if(glyphs.has(key))return glyphs.get(key);
 const g=document.createElement('canvas');let advance;
 if(char.charCodeAt(0)<128){g.width=8;g.height=8;const c=g.getContext('2d'),i=char.charCodeAt(0);c.drawImage(images['reference/vanilla/ascii.png'],i%16*8,Math.floor(i/16)*8,8,8,0,0,8,8);const p=c.getImageData(0,0,8,8);let last=-1;for(let y=0;y<8;y++)for(let x=0;x<8;x++)if(p.data[(y*8+x)*4+3])last=Math.max(last,x);advance=char===' '?4:Math.max(2,last+2);
 }else{const definition=pack.fontGlyphs[char];if(definition){g.width=definition.width;g.height=16;const c=g.getContext('2d');c.fillStyle='#fff';const digits=definition.width/4;for(let y=0;y<16;y++){const row=parseInt(definition.bits.slice(y*digits,(y+1)*digits),16);for(let x=0;x<definition.width;x++)if(row&(1<<(definition.width-1-x)))c.fillRect(x,y,1,1);}advance=definition.width/2+1;}else{g.width=20;g.height=20;const c=g.getContext('2d');c.font='20px SimSun, monospace';c.fillStyle='#fff';c.textBaseline='top';c.fillText(char,0,0);const p=c.getImageData(0,0,20,20);for(let i=0;i<p.data.length;i+=4)p.data[i+3]=p.data[i+3]>80?255:0;c.putImageData(p,0,0);advance=11;}}
 const c=g.getContext('2d');c.globalCompositeOperation='source-in';c.fillStyle=tint;c.fillRect(0,0,g.width,g.height);const result={canvas:g,advance};glyphs.set(key,result);return result;
}
function measure(text){return [...String(text)].reduce((n,c)=>n+glyph(c,'#fff').advance,0);}
function shorten(text,width){if(measure(text)<=width)return text;let s=String(text);while(s&&measure(s+'...')>width)s=s.slice(0,-1);return s+'...';}
function text(value,x,y,tint='#ffffff',align='left',maxWidth=Infinity){const s=shorten(String(value),maxWidth),w=measure(s);if(align==='center')x-=Math.floor(w/2);if(align==='right')x-=w;x=Math.round(x);y=Math.round(y);let at=x;for(const ch of s){const dark=glyph(ch,'#242625'),g=glyph(ch,tint),scale=ch.charCodeAt(0)<128?1:.5;ctx.drawImage(dark.canvas,at+1,y+1,dark.canvas.width*scale,dark.canvas.height*scale);ctx.drawImage(g.canvas,at,y,g.canvas.width*scale,g.canvas.height*scale);at+=g.advance;}return {x,y,width:w,height:10};}
function bar(id,x,y,value,max){const bg=images['textures/'+id+'_background.png'],fill=images['textures/'+id+'_fill.png'];image('textures/'+id+'_background.png',x,y);const n=Math.floor(fill.width*clamp(max>0?value/max:0,0,1));if(n>0)ctx.drawImage(fill,0,0,n,fill.height,x+1,y+1,n,fill.height);return {x,y,width:bg.width,height:bg.height};}
function calculate(){const W=state.width,H=state.height,cx=Math.floor(W/2),left=cx-91,right=cx+10,hotbarY=H-24,xpY=hotbarY-7,hpY=xpY-12,rows=Math.ceil(clamp(state.maxHealth,20,80)/20),armorY=hpY-rows*10-1,resourceY=armorY-9,airY=hpY-10,staminaY=airY,itemY=armorY-42,mapSize=W<380&&H<210?44:60;
 let bossY=20;if(state.showMap&&cx-91<8+mapSize+6)bossY=8+mapSize+24;
 return {W,H,cx,left,right,hotbarY,xpY,hpY,rows,armorY,airY,resourceY,staminaY,itemY,mapSize,bossY};}
function render(transparent=false){
 const L=calculate(),{W,H,cx,left,right,hotbarY,xpY,hpY,rows,armorY,airY,resourceY,staminaY,itemY,mapSize,bossY}=L;scene.width=W*2;scene.height=H*2;ctx.scale(2,2);ctx.imageSmoothingEnabled=false;
 if(!transparent){const bg=images['reference/scene.png'];ctx.drawImage(bg,0,0,W,H);}
 const boxes={};
 if(state.showMap){const x=8,y=8,pad=mapSize===60?4:3;image('textures/map_demo.png',x+pad,y+pad,mapSize-pad*2,mapSize-pad*2);image('textures/minimap_frame.png',x,y,mapSize,mapSize);image('textures/marker_player.png',x+Math.floor(mapSize/2)-3,y+Math.floor(mapSize/2)-3);image('textures/marker_enemy.png',x+Math.floor(mapSize*.7),y+Math.floor(mapSize*.28));image('reference/grace_discovered.png',x+Math.floor(mapSize*.2),y+Math.floor(mapSize*.58),8,8);text('N',x+mapSize/2,y+4,'#fff','center');text('128  64  -64',x,y+mapSize+4,'#fff','left',96);boxes.map={x,y,width:mapSize,height:mapSize+14};}
 if(state.showBoss){text(state.bossName,cx,bossY-13,'#fff','center',182);boxes.boss=bar('boss_bar',cx-91,bossY,state.boss,100);}
 if(state.effects){for(const [i,id] of ['poison','bleed'].entries()){const x=W-26-i*20;image('textures/status_frame.png',x,8);image('textures/status_'+id+'.png',x+1,9);}boxes.status={x:W-46,y:8,width:38,height:18};}
 color('#ffffff');ctx.fillRect(cx-3,Math.floor(H/2),7,1);ctx.fillRect(cx,Math.floor(H/2)-3,1,7);
 vanilla('hotbar',left,hotbarY,182,22);vanilla('hotbar_selection',left-1+state.selected*20,hotbarY-1,24,23);
 for(const [i,item] of pack.equipment.entries()){image(item.file,left+3+i*20,hotbarY+3,16,16);text(i+1,left+2+i*20,hotbarY+1,'#fff');}
 vanilla('xp_background',left,xpY,182,5);const xp=images['reference/vanilla/xp_fill.png'];ctx.drawImage(xp,0,0,Math.floor(xp.width*.42),xp.height,left,xpY,Math.floor(182*.42),5);text('12',cx,xpY-9,'#88ff23','center');
 for(let row=0;row<rows;row++)for(let i=0;i<10;i++){const x=left+i*8,y=hpY-row*10,index=row*20+i*2;vanilla('heart_container',x,y,9,9);if(state.health>index)vanilla(state.health-index>=2?'heart_full':'heart_half',x,y,9,9);}
 for(let i=0;i<10;i++){vanilla('armor_empty',left+i*8,armorY,9,9);if(state.armor>i*2)vanilla('armor_full',left+i*8,armorY,9,9);vanilla('food_empty',right+i*8,hpY,9,9);if(state.food>i*2)vanilla('food_full',right+i*8,hpY,9,9);}
 boxes.fp=bar('fp_bar',left,resourceY,state.fp,state.maxFp);
 boxes.sharedRight={x:right,y:staminaY,width:81,height:9};
 if(state.underwater){for(let i=0;i<10;i++)vanilla('air',right+i*8,airY,9,9);boxes.oxygen={...boxes.sharedRight};}
 else boxes.stamina=bar('stamina_bar',right,staminaY,state.stamina,state.maxStamina);
 if(state.fp<=state.maxFp*.15)image('textures/low_resource.png',left-10,resourceY,7,7);
 if(!state.underwater&&state.stamina===0)image('textures/low_resource.png',right+84,staminaY,7,7);
 boxes.item=text(state.nameOverride||pack.equipment[state.selected].name,cx,itemY,'#f8efae','center',182);
 boxes.hud={x:left-12,y:itemY-2,width:206,height:H-itemY};
 let crop={x:0,y:0,width:W,height:H};
 if(state.view==='hud')crop={x:left-14,y:Math.max(0,itemY-5),width:210,height:H-Math.max(0,itemY-5)};
 else if(state.view==='map')crop={x:4,y:4,width:106,height:mapSize+24};
 else if(state.view==='boss')crop={x:cx-96,y:Math.max(0,bossY-17),width:192,height:31};
 canvas.width=crop.width*2;canvas.height=crop.height*2;display.imageSmoothingEnabled=false;display.clearRect(0,0,canvas.width,canvas.height);display.drawImage(scene,crop.x*2,crop.y*2,crop.width*2,crop.height*2,0,0,canvas.width,canvas.height);canvas.style.aspectRatio=crop.width+'/'+crop.height;canvas.style.maxWidth=(crop.width/crop.height*65)+'vh';
 window.HUD_LAYOUT={...L,boxes,crop};document.querySelectorAll('[data-view]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.view===state.view)));
 for(const [id,key,max] of [['health','health','maxHealth'],['fp','fp','maxFp'],['stamina','stamina','maxStamina'],['boss','boss',null]]){document.querySelector('#'+id).value=state[key];document.querySelector('#'+id+'-value').value=Math.round(state[key])+'/'+(max?state[max]:100);}
 document.querySelector('#health').max=state.maxHealth;for(const id of ['showBoss','showMap','underwater','effects'])document.querySelector('#'+id).checked=state[id];document.querySelector('#equipment').value=state.selected;
 return L;
}
function update(values){Object.assign(state,values);state.selected=clamp(state.selected,0,8);for(const [key,max] of [['health','maxHealth'],['fp','maxFp'],['stamina','maxStamina']])state[key]=clamp(state[key],0,state[max]);state.boss=clamp(state.boss,0,100);render();}
async function start(){for(const [file,src] of Object.entries(pack.embeddedImages)){const img=new Image();img.src=src;await img.decode();images[file]=img;}
 const select=document.querySelector('#equipment');pack.equipment.forEach((item,i)=>{const o=document.createElement('option');o.value=i;o.textContent=item.name;select.append(o);});
 for(const id of ['health','fp','stamina','boss'])document.querySelector('#'+id).addEventListener('input',e=>update({[id]:Number(e.target.value)}));
 for(const id of ['showBoss','showMap','underwater','effects'])document.querySelector('#'+id).addEventListener('change',e=>update({[id]:e.target.checked}));
 select.addEventListener('change',e=>update({selected:Number(e.target.value)}));document.querySelectorAll('[data-view]').forEach(b=>b.addEventListener('click',()=>update({view:b.dataset.view})));
 document.querySelector('#viewport').addEventListener('change',e=>{const [width,height]=e.target.value.split(',').map(Number);update({width,height});});
 document.querySelector('#preset').addEventListener('change',e=>{const defaults={health:18,maxHealth:20,fp:60,stamina:92,boss:68,showBoss:true,showMap:true,underwater:false,effects:false};if(e.target.value==='low')Object.assign(defaults,{fp:8,stamina:0,effects:true});if(e.target.value==='explore')Object.assign(defaults,{showBoss:false,health:20});if(e.target.value==='underwater')Object.assign(defaults,{underwater:true});if(e.target.value==='multi')Object.assign(defaults,{maxHealth:60,health:53,underwater:true,effects:true});update(defaults);});
 document.querySelector('#export').addEventListener('click',()=>{render(true);const output=document.createElement('canvas');output.width=state.width*4;output.height=state.height*4;const c=output.getContext('2d');c.imageSmoothingEnabled=false;c.drawImage(scene,0,0,output.width,output.height);const a=document.createElement('a');a.download='eldencombat-hud-v02.png';a.href=output.toDataURL();a.click();render();});
 canvas.addEventListener('click',e=>{if(state.view!=='full'&&state.view!=='hud')return;const r=canvas.getBoundingClientRect(),crop=window.HUD_LAYOUT.crop,x=(e.clientX-r.left)*crop.width/r.width+crop.x,y=(e.clientY-r.top)*crop.height/r.height+crop.y,L=calculate();if(y>=L.hotbarY&&y<L.hotbarY+22&&x>=L.left&&x<L.left+180)update({selected:Math.floor((x-L.left)/20)});});
 window.HUD={state,update,render,exportDataURL:()=>{render(true);const data=scene.toDataURL();render();return data;}};render();window.HUD_READY=true;
}
start().catch(e=>{document.querySelector('footer').textContent='预览加载失败：'+e.message;window.HUD_ERROR=e.stack;});
