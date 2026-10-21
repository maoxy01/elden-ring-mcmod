'use strict';
const path = require('path'), crypto = require('crypto');
let fsModule, accessRoot;
function access(root) {
  if (accessRoot === root && fsModule) return;
  fsModule = requireNativeModule('fs', {scope:root, optional:false, message:'读取所选模组目录的动作和武器；仅点击保存动作时备份并写回 JSON。'});
  if (!fsModule) throw new Error('需要允许插件访问所选项目目录，才能读取或保存动作。');
  accessRoot=root;
}
const fs=new Proxy({}, {get(_target,key) {if(!fsModule) throw new Error('请先选择项目目录');return fsModule[key];}});
const Core = createPoseCore(fs, path, crypto);
const TITLE = 'Elden Combat 动作编辑器';
const LABELS = {wave_of_gold:'黄金波动',gold_breaker:'碎金',destined_death:'命定之死',takers_flames:'掠夺火焰',waterfowl_dance:'水鸟乱舞',starcaller_cry:'唤星',bloodboon_ritual:'授血仪式',cursed_blood_slice:'咒血斩击'};
let panel, host, format, actions = [], listeners = [], style;
const binding = () => Project?.unhandled_root_fields?.elden_pose;
const findGroup = id => Group.all.find(g => g.uuid === id);
const findAnimation = id => Animation.all.find(a => a.uuid === id);
function guard(fn) {
  return (...args) => Promise.resolve().then(() => fn(...args)).catch(error => {
    Blockbench.showMessageBox({title: TITLE, message: error.message});
    return {error: error.message};
  });
}
function message(text) { Blockbench.showQuickMessage(text, 3000); }
function listen(name, fn) { Blockbench.on(name, fn); listeners.push([name, fn]); }
function sceneSignature() {
  return JSON.stringify({
    groups: Group.all.map(g => [g.uuid, g.name, g.parent?.uuid || 'root', g.origin, g.rotation]),
    cubes: Cube.all.map(c => [c.uuid, c.parent?.uuid, c.from, c.to, c.origin, c.rotation, c.inflate, Object.fromEntries(Object.entries(c.faces).map(([n,f])=>[n,[f.uv,f.texture,f.rotation]]))])
  });
}
function exportPose(checkScene = true) {
  const b = binding(); Core.assert(b, '请先打开项目动作');
  if (checkScene) Core.assert(sceneSignature() === b.sceneSignature, '人物或武器几何已被修改。首版只保存动作，请撤销模型修改后再保存。');
  const allowed = new Set([...Object.values(b.animations), b.flowAnimation]);
  Core.assert(Animation.all.every(a => allowed.has(a.uuid)), '有未绑定阶段的动画，请使用面板的新增阶段入口');
  const stages = Object.entries(b.animations).filter(([stage,id])=>findAnimation(id) || Object.hasOwn(b.original.stages,stage)).map(([stage,id]) => {
    const a = findAnimation(id);
    Core.assert(a && (!a.blend_weight || +a.blend_weight === 1) && !a.anim_time_update && !a.start_delay && !a.loop_delay, '阶段动画丢失，或存在运行时不支持的混合/时间表达式');
    // The native getUndoCopy includes effect/custom channels too, so none can silently disappear.
    return {stage, animation:a.getUndoCopy()};
  });
  const flow = findAnimation(b.flowAnimation);
  Core.assert(flow && !Object.values(flow.animators).some(a => a.keyframes?.length), '完整流程是只读预览，请切换到具体阶段编辑');
  return Core.exportTracks(b.original, stages, b.joints);
}
function addStageAnimation(stage, parts, b) {
  const animators = {};
  for (const [joint, keys] of Object.entries(parts)) {
    animators[b.joints[joint]] = {name:joint,type:'bone',keyframes:keys.map(([tick,rotation]) => {
      const [x,y,z] = Core.toEditor(rotation);
      return {channel:'rotation', time:tick/20, interpolation:'linear', data_points:[{x,y,z}]};
    })};
  }
  const last = Math.max(0, ...Object.values(parts).flatMap(keys=>keys.map(k=>k[0])));
  const a = new Animation({name:stage, length:Math.max(Core.duration(b.spec,stage),last)/20, snapping:20, loop:'hold', override:true, animators}).add();
  b.animations[stage] = a.uuid;
  return a;
}
function playerRig(b) {
  const canvas = document.createElement('canvas'); canvas.width = canvas.height = 64;
  const c = canvas.getContext('2d');
  c.fillStyle='#ad795b'; c.fillRect(0,0,64,64);
  c.fillStyle='#285e69'; c.fillRect(16,16,24,16); c.fillRect(40,16,16,6); c.fillRect(32,48,16,6);
  c.fillStyle='#354066'; c.fillRect(0,16,16,16); c.fillRect(16,48,16,16);
  c.fillStyle='#3a2b25'; c.fillRect(0,0,32,8); c.fillRect(8,8,8,2);
  c.fillStyle='#e5e8e9'; c.fillRect(9,11,2,1); c.fillRect(13,11,2,1);
  const texture = new Texture({name:'中立玩家参考皮肤',width:64,height:64}).fromDataURL(canvas.toDataURL()).add();
  const pieces = {
    head:{origin:[0,24,0],from:[-4,24,-4],to:[4,32,4],uv:[0,0]},
    body:{origin:[0,24,0],from:[-4,12,-2],to:[4,24,2],uv:[16,16]},
    rightArm:{origin:[5,22,0],from:[4,12,-2],to:[8,24,2],uv:[40,16]},
    leftArm:{origin:[-5,22,0],from:[-8,12,-2],to:[-4,24,2],uv:[32,48]},
    rightLeg:{origin:[1.9,12,0],from:[-.1,0,-2],to:[3.9,12,2],uv:[0,16]},
    leftLeg:{origin:[-1.9,12,0],from:[-3.9,0,-2],to:[.1,12,2],uv:[16,48]}
  };
  for (const [joint, p] of Object.entries(pieces)) {
    const group = new Group({name:joint,origin:p.origin}).init();
    const cube = new Cube({name:joint+'_reference',from:p.from,to:p.to,box_uv:true,uv_offset:p.uv,locked:true}).addTo(group).init();
    cube.applyTexture(texture); b.joints[joint] = group.uuid;
  }
}
async function texturePixels(data) {
  const image = new Image(); image.src = data;
  await image.decode();
  const canvas = document.createElement('canvas'); canvas.width=image.width; canvas.height=image.height;
  const c=canvas.getContext('2d'); c.drawImage(image,0,0);
  return {width:image.width,height:image.height,pixels:c.getImageData(0,0,image.width,image.height).data};
}
async function weaponRig(b) {
  const weapon = Core.weapon(b.root,b.spec.itemId);
  b.weapon = weapon;
  const group = new Group({name:'weapon_readonly',origin:[0,0,0],locked:true}).addTo(findGroup(b.joints.rightArm)).init();
  b.weaponGroup = group.uuid;
  const textures = {};
  for (const t of weapon.textures) {
    const pixels = await texturePixels(t.data);
    const tex = new Texture({name:t.resource.split('/').at(-1)+'.png',width:pixels.width,height:pixels.height,uv_width:16,uv_height:16}).fromDataURL(t.data).add();
    textures[t.resource] = {tex,...pixels};
  }
  function resolveTexture(ref) {
    const seen=new Set();
    while(ref?.startsWith('#')) { Core.assert(!seen.has(ref),'贴图引用成环'); seen.add(ref); ref=weapon.model.textures[ref.slice(1)]; }
    Core.assert(textures[ref], '无法解析贴图：'+ref); return textures[ref];
  }
  if (weapon.model.elements) {
    for (const e of weapon.model.elements) {
      Core.assert(!e.rotation,'首版不支持带元素旋转的武器模型');
      const faces={};
      for(const [name,f] of Object.entries(e.faces)) faces[name]={...f,texture:resolveTexture(f.texture).tex.uuid};
      new Cube({name:'weapon_part',from:e.from,to:e.to,faces,box_uv:false,locked:true}).addTo(group).init();
    }
  } else {
    Core.assert(weapon.model.parent==='minecraft:item/generated' || weapon.model.parent==='item/generated', '不支持该武器父模型');
    Core.assert(Object.keys(weapon.model.textures).length===1, '不支持多图层生成模型');
    const {tex,width,height,pixels}=resolveTexture(weapon.model.textures.layer0);
    // Same alpha silhouette, pixel UVs and one-unit extrusion as ItemModelGenerator.
    for(let y=0;y<height;y++) for(let x=0;x<width;) {
      if(!pixels[(y*width+x)*4+3]) {x++;continue;}
      const start=x; while(x<width && pixels[(y*width+x)*4+3]) x++;
      const u=start*16/width,v=y*16/height,u2=x*16/width,v2=(y+1)*16/height;
      const faces={};
      for(const name of ['north','south','east','west','up','down']) {
        const uv=name==='south'?[u,v,u2,v2]:name==='north'?[u2,v,u,v2]:name==='west'?[u,v,u+16/width,v2]:name==='east'?[u2-16/width,v,u2,v2]:[u,v,u2,v2];
        faces[name]={uv,texture:tex.uuid};
      }
      new Cube({name:'weapon_pixels',from:[u,16-v2,7.5],to:[u2,16-v,8.5],faces,box_uv:false,locked:true}).addTo(group).init();
    }
  }
  const cubes=Cube.all.filter(c=>c.parent===group);
  Core.assert(cubes.length,'武器没有可见几何');
  b.depth={min:Math.min(...cubes.map(c=>c.from[2])),max:Math.max(...cubes.map(c=>c.to[2]))};
}
function weaponMatrix(b) {
  const left=b.hand==='left', sign=left?-1:1, d=b.weapon.display;
  const frame=d[left?'thirdperson_lefthand':'thirdperson_righthand'] || d.thirdperson_righthand || d[d.inheritance] || d.gui;
  Core.assert(frame,'缺少第三人称持握变换');
  const rad=Math.PI/180, M=THREE.Matrix4, m=new M().makeScale(-1,-1,1);
  const r=frame.rotation||[0,0,0],t=frame.translation||[0,0,0],s=frame.scale||[1,1,1];
  const q=new THREE.Quaternion().setFromEuler(new THREE.Euler(r[0]*rad,sign*r[1]*rad,sign*r[2]*rad,'XYZ'));
  m.multiply(new M().makeRotationX(-Math.PI/2)).multiply(new M().makeRotationY(Math.PI));
  m.multiply(new M().makeTranslation(sign,2,-10));
  m.multiply(new M().compose(new THREE.Vector3(sign*t[0],t[1],t[2]),q,new THREE.Vector3(...s)));
  m.multiply(new M().makeTranslation(-8,-8,-8));
  const center=(b.depth.min+b.depth.max)/2,span=b.depth.max-b.depth.min;
  Core.assert(span>0 && s[2]!==0,'武器厚度无效');
  // diamond_sword third-person display scale is .85 in MC 1.21.1 handheld.json.
  m.multiply(new M().makeTranslation(0,0,center)).multiply(new M().makeScale(1,1,.85/(span*Math.abs(s[2])))).multiply(new M().makeTranslation(0,0,-center));
  return m;
}
function draw() {
  const b=binding(); if(!b) return;
  const a=Animation.selected;
  if(a && host) host.querySelector('[data-stage]').value=a.uuid;
  if(a?.uuid===b.flowAnimation) {
    try {
      const state=Core.flowAt(Core.flow(b.spec,b.followup),Timeline.time*20);
      const pose=Core.poseAt(exportPose(false),state.stage,state.index,state.elapsed);
      for(const joint of Core.JOINTS) {
        const g=findGroup(b.joints[joint]);
        if(g) g.mesh.rotation.set(...Core.toEditor(pose[joint]).map(n=>n*Math.PI/180),'ZYX');
      }
      const text=host?.querySelector('[data-state]'); if(text) text.textContent=`${state.stage} #${state.index} · ${state.elapsed.toFixed(1)} tick`;
    } catch(error) {
      const text=host?.querySelector('[data-state]'); if(text) text.textContent=error.message;
    }
  } else {
    const text=host?.querySelector('[data-state]'); if(text && a) text.textContent=`${a.name} · ${(Timeline.time*20).toFixed(1)} tick`;
  }
  const weapon=findGroup(b.weaponGroup),arm=findGroup(b.joints[b.hand==='left'?'leftArm':'rightArm']);
  if(weapon?.mesh && arm?.mesh && b.weapon) {
    if(weapon.mesh.parent!==arm.mesh) arm.mesh.add(weapon.mesh);
    weapon.mesh.matrixAutoUpdate=false;
    weapon.mesh.matrix.copy(weaponMatrix(b));
  }
  Project.model_3d.scale.setScalar(.9375);
  Project.model_3d.updateMatrixWorld(true);
}
function refresh() {
  if(!host) return;
  const b=binding(); host.style.display=b?'':'none'; if(!b) return;
  host.querySelector('[data-title]').textContent=(LABELS[b.id]||b.id)+' / '+b.spec.itemId;
  host.querySelector('[data-root]').textContent=b.root;
  const select=host.querySelector('[data-stage]'),value=Animation.selected?.uuid;
  select.replaceChildren(...Object.entries(b.animations).filter(([,id])=>findAnimation(id)).map(([stage,id])=>new Option(stage,id)),new Option('完整流程（只读）',b.flowAnimation));
  select.value=value || Object.values(b.animations)[0];
  host.querySelector('[data-hand]').value=b.hand;
  host.querySelector('[data-followup]').value=b.followup?'yes':'no';
  const flow=findAnimation(b.flowAnimation); if(flow) flow.setLength(Core.flow(b.spec,b.followup).ticks/20);
  if(b.weaponError) host.querySelector('[data-warning]').textContent='武器不可用：'+b.weaponError;
  else host.querySelector('[data-warning]').textContent='中立站姿基线；不含位移、第一人称和特效。武器及持握参数只读。';
  draw();
}
async function openSkill(root,id) {
  access(root);
  const b=Core.load(root,id);
  newProject(format);
  Project.name=(LABELS[id]||id)+'-动作';
  Project.texture_width=Project.texture_height=64;
  Project.unhandled_root_fields.elden_pose=b;
  playerRig(b);
  try {await weaponRig(b);} catch(error) {b.weaponError=error.message; const g=findGroup(b.weaponGroup); if(g) g.remove(); delete b.weaponGroup; delete b.weapon;}
  for(const [stage,parts] of Object.entries(b.original.stages)) addStageAnimation(stage,parts,b);
  b.flowAnimation=new Animation({name:'完整流程（只读）',length:Core.flow(b.spec,false).ticks/20,snapping:20,loop:'hold',override:true}).add().uuid;
  await Promise.all(Texture.all.map(t=>t.img.decode()));
  b.sceneSignature=sceneSignature();
  localStorage.setItem('elden_pose_root',root);
  Canvas.updateAll(); Modes.options.animate.select();
  findAnimation(Object.values(b.animations)[0]).select();
  Animation.selected.getBoneAnimator(findGroup(b.joints.rightArm)).select();
  Timeline.setTime(0); Animator.preview();
  Preview.selected.loadAnglePreset({projection:'perspective',position:[65,45,-95],target:[0,20,0]});
  Preview.selected.controls.update();
  Project.saved=false;
  refresh();
  return {id,weaponError:b.weaponError||null,animations:Object.keys(b.animations).length};
}
function chooseSkill(changeRoot=false) {
  let root=localStorage.getItem('elden_pose_root');
  if(changeRoot || !root) root=Blockbench.pickDirectory({title:'选择包含 src 和 art 的 mcmod 项目目录',startpath:root});
  if(!root) return;
  access(root);
  const items=Core.catalog(root),options=Object.fromEntries(items.map(s=>[s.id,`${LABELS[s.id]||s.id} — ${s.itemId}`]));
  new Dialog({id:'elden_pose_choose',title:'打开项目动作',form:{skill:{label:'战技',type:'select',options}},onConfirm:guard(async result=>{await openSkill(root,result.skill);})}).show();
}
function addStage() {
  const b=binding(); Core.assert(b,'请先打开动作');
  new Dialog({id:'elden_pose_stage',title:'新增阶段或段号覆盖',form:{base:{label:'阶段',type:'select',options:Object.fromEntries(Core.STAGES.map(x=>[x,x])),value:'ACTIVE'},override:{label:'指定段号覆盖',type:'checkbox',value:true},index:{label:'运行时段号',type:'number',value:2,min:0,step:1}},onConfirm:guard(result=>{
    Core.assert(Number.isInteger(result.index) && result.index>=0,'段号必须是非负整数');
    const stage=result.base+(result.override?'#'+result.index:'');
    Core.assert(!b.animations[stage],'该阶段已存在');
    const parts=Core.clone(Core.resolve(exportPose(),result.base,result.index).parts);
    Undo.initEdit({animations:[]});
    const a=addStageAnimation(stage,parts,b);
    Undo.finishEdit('新增动作阶段',{animations:[a]});
    a.select(); refresh();
  })}).show();
}
function saveSource() {
  const b=binding(),pose=exportPose();
  access(b.root);
  const result=Core.save(b,pose);
  Project.saved=false; // The bbmodel's binding/hash changed too; save it separately with Ctrl+S.
  message(result.unchanged?'动作未改变，源文件未写入':'已保存动作；原文件备份：'+path.basename(result.backup));
  return result;
}
function saveProject() {
  Core.assert(binding(),'请先打开动作');
  Codecs.project.export();
}
function selectStage(id) {
  Timeline.pause(); Animation.all.forEach(a=>a.playing=false);
  const a=findAnimation(id);a?.select();if(a) a.playing=true;
  const b=binding();if(a && b && id!==b.flowAnimation) a.getBoneAnimator(findGroup(b.joints.rightArm)).select();
  Timeline.setTime(0); Animator.preview(); refresh();
}
function setupPanel() {
  panel=new Panel('elden_pose',{name:TITLE,icon:'accessibility_new',default_position:{slot:'right_bar',height:520,fixed_height:true},condition:()=>!!binding(),min_height:400,growable:false,resizable:true});
  host=document.createElement('div'); host.className='elden-pose';
  host.innerHTML=`<strong data-title></strong><small data-root></small>
    <div class="ep-row"><button data-open>打开其他战技</button><button data-rootchange>项目目录</button></div>
    <label>阶段<select data-stage></select></label>
    <div class="ep-row"><button data-addstage>新增阶段／段号覆盖</button></div>
    <label>完整流程<select data-followup><option value="no">不追击（等待窗口超时）</option><option value="yes">立即追击（最大段数）</option></select></label>
    <label>主手<select data-hand><option value="right">右手</option><option value="left">左手</option></select></label>
    <div class="ep-row"><button data-prev>−1 tick</button><button data-play>播放／暂停</button><button data-next>+1 tick</button></div>
    <div class="ep-row"><label>速度<select data-speed><option value="100">1×</option><option value="50">0.5×</option><option value="25">0.25×</option></select></label><label><input type="checkbox" data-loop>循环</label></div>
    <output data-state></output><p data-warning></p>
    <button class="ep-save" data-save>保存回项目动作 JSON</button><button data-project>另存编辑工程 .bbmodel</button>
    <small>在下方原生时间轴编辑旋转关键帧；X/Y 是编辑器坐标，导出时转换。Ctrl+Z 撤销，Ctrl+S 保存工程。保存源动作请使用上方按钮。</small>`;
  panel.node.append(host);
  const on=(selector,event,fn)=>host.querySelector(selector).addEventListener(event,guard(fn));
  on('[data-open]','click',()=>chooseSkill()); on('[data-rootchange]','click',()=>chooseSkill(true));
  on('[data-stage]','change',event=>selectStage(event.target.value));
  on('[data-addstage]','click',addStage);
  on('[data-followup]','change',event=>{binding().followup=event.target.value==='yes'; Project.saved=false; refresh(); Animator.preview();});
  on('[data-hand]','change',event=>{binding().hand=event.target.value; Project.saved=false; draw();});
  on('[data-prev]','click',()=>{Timeline.pause();Timeline.setTime(Math.max(0,Math.round(Timeline.time*20)-1)/20);Animator.preview();});
  on('[data-next]','click',()=>{Timeline.pause();Timeline.setTime(Math.min(Animation.selected.length,(Math.round(Timeline.time*20)+1)/20));Animator.preview();});
  on('[data-play]','click',()=>Timeline.playing?Timeline.pause():Timeline.start());
  on('[data-speed]','change',event=>{Timeline.playback_speed=+event.target.value;});
  on('[data-loop]','change',event=>{if(Animation.selected) Animation.selected.loop=event.target.checked?'loop':'hold';});
  on('[data-save]','click',saveSource); on('[data-project]','click',saveProject);
}
Plugin.register('eldencombat_pose',{
  title:TITLE,author:'Elden Combat tooling',description:'原版六关节战技动作和真实手持武器的离线编辑、预览及安全保存。',icon:'accessibility_new',version:'1.0.0',variant:'desktop',min_version:'5.2.2',tags:['Animation','Minecraft: Java Edition'],
  onload() {
    format=new ModelFormat('eldencombat_pose',{name:TITLE,icon:'accessibility_new',category:'minecraft',bone_rig:true,centered_grid:true,animation_mode:true,edit_mode:true,paint_mode:false,rotate_cubes:true,optional_box_uv:true,per_texture_uv_size:true,uv_rotation:true,euler_order:'ZYX',quaternion_interpolation:false,animation_loop_wrapping:false,show_on_start_screen:false});
    style=Blockbench.addCSS('#panel_elden_pose{overflow-y:auto}.elden-pose{padding:10px;display:flex;flex-direction:column;gap:8px}.elden-pose small{display:block;opacity:.7;overflow-wrap:anywhere;font-size:11px}.elden-pose label{display:flex;align-items:center;justify-content:space-between;gap:6px}.elden-pose select{min-width:0;max-width:230px}.elden-pose .ep-row{display:flex;gap:6px;justify-content:space-between}.elden-pose button{white-space:normal;min-height:28px}.elden-pose p{font-size:12px;opacity:.8}.elden-pose output{font-family:monospace}.elden-pose .ep-save{background:var(--color-accent);color:var(--color-accent_text)}');
    setupPanel();
    const open=new Action('elden_pose_open',{name:'Elden Combat：打开项目动作',icon:'accessibility_new',click:guard(()=>chooseSkill())});
    const save=new Action('elden_pose_save',{name:'Elden Combat：保存动作 JSON',icon:'save',condition:()=>!!binding(),click:guard(saveSource)});
    actions.push(open,save); MenuBar.addAction(open,'tools'); MenuBar.addAction(save,'tools');
    listen('display_animation_frame',draw);
    listen('select_project',()=>setTimeout(refresh,30));
    listen('select_mode',()=>setTimeout(()=>{refresh();draw();},30));
    listen('finished_edit',refresh); listen('undo',()=>setTimeout(refresh,0)); listen('redo',()=>setTimeout(refresh,0));
    // Testable entry points; no filesystem write happens until saveSource is explicitly invoked.
    globalThis.EldenPoseEditor={Core,openSkill,exportPose,saveSource,refresh,selectStage,weaponMatrix,sceneSignature,draw};
    refresh();
  },
  onunload() {
    listeners.forEach(([name,fn])=>Blockbench.removeListener(name,fn)); listeners=[];
    actions.forEach(a=>a.delete()); actions=[]; panel?.delete(); style?.delete(); format?.delete();
    delete globalThis.EldenPoseEditor;
  }
});
