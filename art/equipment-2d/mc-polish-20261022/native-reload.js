(() => {
 const root='D:/codexout/mcmod/art/equipment-2d/mc-polish-20261022/';
 const files=fs.readdirSync(root+'items').filter(n=>n.endsWith('.bbmodel'));
 window.mcReload={status:'running',items:[],error:null};
 const sheet=document.createElement('canvas');sheet.width=1120;sheet.height=800;
 const ctx=sheet.getContext('2d');ctx.fillStyle='#bac6c0';ctx.fillRect(0,0,1120,800);ctx.imageSmoothingEnabled=false;
 (async()=>{try{
  for(let i=0;i<files.length;i++){
   const model=JSON.parse(fs.readFileSync(root+'items/'+files[i],'utf8'));
   Codecs.project.load(model,{path:root+'items/'+files[i],name:files[i]});
   await new Promise(r=>setTimeout(r,250));
   if(Texture.all.some((t,j)=>t.canvas.width!==model.textures[j].width))throw Error(model.name+' reload dimensions');
   const t=Texture.all[0],x=i%7*160,y=Math.floor(i/7)*200;
   ctx.drawImage(t.canvas,x+16,y+8,128,128);ctx.fillStyle='#202b29';ctx.font='11px sans-serif';ctx.fillText(model.name,x+2,y+155);
   mcReload.items.push({id:model.name,textures:Texture.all.length,undo:Undo.history.length});
   Project.saved=true;Project.close();
  }
  Blockbench.writeFile(root+'after-sheet.png',{content:sheet.toDataURL(),savetype:'image'});mcReload.status='complete';
 }catch(e){mcReload.status='failed';mcReload.error=e.stack;}
 Blockbench.writeFile(root+'native-reload.json',{content:JSON.stringify(mcReload,null,2)});
 })();return 'Reloading 28 saved native projects';
})()
