(() => {
 const root='D:/codexout/mcmod/art/vfx-blockbench/takers-flames-20261021/';let original;
 Blockbench.read([root+'takers-flames-eruption.bbmodel'],{readtype:'text'},f=>original=JSON.parse(f[0].content));
 if(Outliner.elements.length)throw Error('Expected empty hybrid project');
 const defs=[];Undo.initEdit({textures:[]});
 const blank=document.createElement('canvas');blank.width=32;blank.height=32;
 const palette=new Texture({name:'molten_and_basalt.png',width:32,height:32,uv_width:32,uv_height:32}).fromDataURL(blank.toDataURL('image/png')).add(false);
 for(let i=0;i<16;i++){const def=original.unhandled_root_fields.takers_flames_art.defs.find(d=>d.layer==='ground_rupture'&&d.index===i),source=original.textures.find(t=>t.uuid===def.texture).source,t=new Texture({name:'ground_'+String(i).padStart(2,'0')+'.png',width:64,height:64,uv_width:64,uv_height:64}).fromDataURL(source).add(false);defs.push({index:i,texture:t.uuid});}
 Undo.finishEdit('Add native molten palette and reuse ground rupture animation',{textures:Texture.all});
 Project.unhandled_root_fields.takers_hybrid_art={date:'2026-10-21',palette:palette.uuid,ground:defs,runtimeIntegrated:false,source:'Native Blockbench solid Cubes, UV, Texture.edit and animation; particle presets'};
 return {project:Project.uuid,textures:Texture.all.length};
})()
