(() => {
 const root='D:/codexout/mcmod/art/vfx-blockbench/takers-flames-20261021/',contract=Project.unhandled_root_fields.takers_flames_art;
 const jets=[[3,5,13,-3],[8,5,22,-2],[13,6,26,0],[19,5,20,2],[25,4,15,3]];
 for(const d of contract.defs.filter(d=>d.layer==='flame_front')){
  const t=Texture.all.find(t=>t.uuid===d.texture),p=d.index/15,e=p<.27?.12+p/.27*.88:p<.64?1:Math.max(.045,(1-p)/.36);
  t.edit(canvas=>{
   const ctx=canvas.getContext('2d');ctx.clearRect(0,0,64,64);
   function rect(x,y,w,h,c){const l=Math.max(1,Math.round(x)),top=Math.max(1,Math.round(y)),r=Math.min(31,Math.round(x+w)),b=Math.min(31,Math.round(y+h));if(r<=l||b<=top)return;ctx.fillStyle=c;ctx.fillRect(l*2,top*2,(r-l)*2,(b-top)*2);}
   rect(2,26,28,3,'#4b2026');rect(4,26,24,3,p>.87?'#a52c24':'#e65920');rect(7,27,18,2,p>.87?'#944332':'#fa982f');
   for(let j=0;j<jets.length;j++){
    const [x,w,maxH,lean]=jets[j],h=Math.max(2,Math.round((maxH+(d.index%3-1)*(j%2?1:-1))*e));
    for(let lift=0;lift<h;lift+=3){const k=lift/h,width=Math.max(2,Math.round(w*(1-k*.55))),xx=x+Math.round(lean*k),yy=28-Math.min(h,lift+3);
     rect(xx,yy,width,Math.min(3,h-lift),'#4b2026');rect(xx,yy+1,width-1,Math.min(3,h-lift),'#a52c24');
     if(lift<h-3)rect(xx+1,yy+1,Math.max(1,width-2),Math.min(3,h-lift),p>.88?'#944332':'#fa982f');
     if(lift<h*.5&&width>=4)rect(xx+1,yy+2,Math.max(1,width-3),2,'#ffe493');
    }
   }
   for(const [x,y,w,h] of [[2,27,4,2],[9,26,4,2],[18,27,4,2],[26,26,4,3]]){rect(x,y,w,h,'#342f32');rect(x,y,w-1,1,'#514a47');}
  },{edit_name:'Broaden and lean magma jet silhouettes '+d.index});
  Blockbench.writeFile(root+'textures/flame_front/frame_'+String(d.index).padStart(2,'0')+'.png',{content:t.canvas.toDataURL('image/png'),savetype:'image'});
 }
 contract.visualRevision='Broad connected molten jets, five staggered vents, irregular crust lip';
 Codecs.project.write(Codecs.project.compile({bitmaps:true,history:true}),root+'takers-flames-eruption.bbmodel');
 Blockbench.writeFile(root+'native-authoring.json',{content:JSON.stringify({project:Project.uuid,version:Blockbench.version,elements:Outliner.elements.length,textures:Texture.all.length,history:Undo.history.length,contract},null,2)});
 return {refined:16,history:Undo.history.length,saved:true};
})()
