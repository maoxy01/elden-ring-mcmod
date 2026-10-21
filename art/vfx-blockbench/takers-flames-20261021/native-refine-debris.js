(() => {
 const root='D:/codexout/mcmod/art/vfx-blockbench/takers-flames-20261021/',contract=Project.unhandled_root_fields.takers_flames_art,blocks=[[4,18,3,3,-1],[9,25,4,3,-1],[14,15,3,2,-1],[19,23,4,3,1],[24,16,3,4,1],[27,21,3,2,1]];
 for(const d of contract.defs.filter(d=>d.layer==='eruption_debris')){
  const t=Texture.all.find(t=>t.uuid===d.texture),p=d.index/15;
  t.edit(canvas=>{
   const ctx=canvas.getContext('2d');ctx.clearRect(0,0,64,64);
   const rect=(x,y,w,h,c)=>{const l=Math.max(1,Math.round(x)),top=Math.max(1,Math.round(y)),r=Math.min(31,Math.round(x+w)),b=Math.min(31,Math.round(y+h));if(r<=l||b<=top)return;ctx.fillStyle=c;ctx.fillRect(l*2,top*2,(r-l)*2,(b-top)*2);};
   for(let j=0;j<blocks.length;j++){
    const [base,h,w,tall,side]=blocks[j],q=Math.max(0,Math.min(1,(p-.04-j*.018)/.79));if(q<=0||q>=1)continue;
    const x=base+side*2*q-w/2,y=27-h*4*q*(1-q)+6*q;
    rect(x,y,w,tall,'#342f32');rect(x,y,w-1,1,'#817465');rect(x,y+1,1,tall-1,'#514a47');if(q<.62)rect(x+1,y+tall-1,w-1,1,'#e65920');
   }
   if(d.index>10&&d.index<15){rect(6,27,3,1,'#5c5450');rect(24,28,3,1,'#5c5450');}
  },{edit_name:'Separate six rising and falling crust chunks '+d.index});
  Blockbench.writeFile(root+'textures/eruption_debris/frame_'+String(d.index).padStart(2,'0')+'.png',{content:t.canvas.toDataURL('image/png'),savetype:'image'});
 }
 contract.debrisRevision='Six independently spaced coarse crust fragments with visible ascent and descent';
 Codecs.project.write(Codecs.project.compile({bitmaps:true,history:true}),root+'takers-flames-eruption.bbmodel');
 Blockbench.writeFile(root+'native-authoring.json',{content:JSON.stringify({project:Project.uuid,version:Blockbench.version,elements:Outliner.elements.length,textures:Texture.all.length,history:Undo.history.length,contract},null,2)});
 return {refined:16,history:Undo.history.length};
})()
