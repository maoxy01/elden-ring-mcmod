(() => {
 const root='D:/codexout/mcmod/art/vfx-blockbench/takers-flames-20261021/';
 if(Project.name!=='Takers Flames - Ground Eruption Candidate'||Outliner.elements.length)throw Error('Expected empty dedicated candidate');
 const layers=['flame_front','ground_rupture','eruption_debris'],defs=[],fs=require('fs');
 const canvas=document.createElement('canvas');canvas.width=64;canvas.height=64;
 Undo.initEdit({textures:[]});
 for(const layer of [...layers,'before'])for(let i=0;i<16;i++){
  const name=layer+'_'+String(i).padStart(2,'0');
  const src=layer==='before'?'data:image/png;base64,'+fs.readFileSync(root+'before/flame_front/frame_'+String(i).padStart(2,'0')+'.png').toString('base64'):canvas.toDataURL('image/png');
  const t=new Texture({name:name+'.png',width:64,height:64,uv_width:64,uv_height:64}).fromDataURL(src).add(false);
  defs.push({layer,index:i,texture:t.uuid,name});
 }
 Undo.finishEdit('Import original flame reference and add native candidate canvases',{textures:Texture.all});
 Project.texture_width=64;Project.texture_height=64;
 Project.unhandled_root_fields.takers_flames_art={date:'2026-10-21',defs,source:'Blockbench native Texture.edit / Cube UV / Animation',candidate:true,runtimeIntegrated:false};
 return {project:Project.uuid,textures:Texture.all.length,history:Undo.history.length,readyForNativePainting:true};
})()
