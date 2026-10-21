(function(){
 const root='D:/codexout/mcmod/art/equipment-2d/blockbench-holds-20261021/grip-audit/candidates/';
 async function review(ids){
  const rows=[];
  for(const id of ids){
   const path=root+'projects/'+id+'.bbmodel';
   await new Promise(resolve=>Blockbench.read([path],{readtype:'text'},files=>{
    Codecs.project.load(JSON.parse(files[0].content),{path:files[0].path,no_file:true});resolve();}));
   for(let i=0;i<100&&Texture.all.some(t=>!t.img.complete||!t.img.naturalWidth);i++)await new Promise(r=>setTimeout(r,20));
   if(Texture.all.some(t=>!t.img.complete||!t.img.naturalWidth))throw Error('Texture not ready: '+id);
   Modes.options.display.select();
   const changes=Project.unhandled_root_fields.grip_candidate.changes;
   for(const [context,frame] of Object.entries(changes)){
    Undo.initEdit({display_slots:[context]});
    Project.display_settings[context].extend(frame);DisplayMode.load(context);Canvas.updateAll();
    Undo.finishEdit('Grip-contact correction: '+context);
   }
   const captures=[];
   for(const context of ['firstperson_righthand','firstperson_lefthand','thirdperson_righthand','thirdperson_lefthand']){
    DisplayMode.load(context);Canvas.updateAll();await new Promise(r=>setTimeout(r,100));
    captures.push(await LatestHold.capture('grip_candidate_'+id+'_'+context));
   }
   Blockbench.writeFile(path,{content:Codecs.project.compile({bitmaps:true,history:true})});
   const exported=Codecs.java_block.compile({raw:true});
   Blockbench.writeFile(root+'exports/'+id+'.json',{content:JSON.stringify(exported,null,2)});
   Project.saved=true;
   rows.push({id,changes:Object.keys(changes),captures,undoEntries:Undo.history.length});
  }
  const persisted=await new Promise(resolve=>Blockbench.read([root+'native-review.json'],{readtype:'text'},files=>resolve(files[0]?JSON.parse(files[0].content).items:[])));
  window.GripCandidateResults=persisted.filter(r=>!ids.includes(r.id)).concat(rows);
  Blockbench.writeFile(root+'native-review.json',{content:JSON.stringify({items:window.GripCandidateResults,runtimeAdopted:false,gameStarted:false,humanAccepted:false},null,2)});
  return rows.map(r=>({id:r.id,changedContexts:r.changes.length,captures:r.captures.length}));
 }
 window.GripCandidate={review};return {ready:true};
})()
