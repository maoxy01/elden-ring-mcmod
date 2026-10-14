import { equipment } from './player.mjs';
import { refineWeapon } from './refine-weapons.mjs';

const box = (name, from, to, material = 'steel', bone = 'root', rotation = [0,0,0], pivot = from) =>
  ({ name, bone, from, to, material, rotation, pivot });
const root = () => [{ name:'root',parent:null,pivot:[0,0,0],rotation:[0,0,0] }];
const grip = (long = false) => [box('grip',[-.8,long?-6:-3,-.8],[.8,2,.8],'leather'),box('pommel',[-1,-4-(long?3:0),-1],[1,-3-(long?3:0),1],'amber')];
const blade = (length, width, curve = false) => [
  ...grip(length>28),box('guard',[-3.5,2,-1],[3.5,3,1],'steel_dark'),
  ...Array.from({length:curve?4:1},(_,i)=> {
    const n=curve?4:1, y=3+(length-3)*i/n, x=curve?i*.9:0;
    return box(`blade_${i}`,[-width/2+x,y,-.5],[width/2+x,3+(length-3)*(i+1)/n,.5]);
  }),box('tip',[(curve?2.7:0)-width/4,length,-.4],[(curve?2.7:0)+width/4,length+2,.4])
];
const pole = (length=40) => [box('shaft',[-.8,-12,-.8],[.8,length,.8],'wood'),box('binding',[-1,0,-1],[1,4,1],'leather')];
const axe = (length, big=false) => [...grip(big),box('haft',[-.75,2,-.75],[.75,length,.75],'wood'),
  box('head',[-2,length-6,-1.4],[big?7:5,length+2,1.4],'steel_dark'),
  box('edge',[big?7:5,length-8,-.8],[big?9:6.5,length+3,.8]),box('back_spike',[-4,length-3,-.7],[-2,length,.7])];
const hammer = (length,big=false) => [...grip(big),box('haft',[-.9,2,-.9],[.9,length,.9],'wood'),
  box('head',[-(big?6:4),length-4,-(big?4:2.6)],[big?6:4,length+3,big?4:2.6],'steel_dark'),
  box('cap_left',[-(big?7:5),length-3,-2.8],[-(big?6:4),length+2,2.8],'amber'),
  box('cap_right',[big?6:4,length-3,-2.8],[big?7:5,length+2,2.8],'amber')];
const spear = (length=40,big=false) => [...pole(length),box('spearhead',[-(big?2.2:1.3),length,-.6],[big?2.2:1.3,length+9,.6]),
  box('tip',[-.7,length+9,-.4],[.7,length+12,.4]),box('collar',[-1.5,length-1,-1.2],[1.5,length+1,1.2],'amber')];
const limb = (name,a,b,bone) => {
  const length=Math.hypot(b[0]-a[0],b[1]-a[1]);
  return box(name,[a[0]-.85,a[1],-.8],[a[0]+.85,a[1]+length,.8],'wood',bone,
    [0,0,-Math.atan2(b[0]-a[0],b[1]-a[1])*180/Math.PI],[a[0],a[1],0]);
};
const bow = (large=false) => {
  const length=large?28:18, width=large?9:6;
  return [box('grip',[-1,-3,-1],[1,3,1],'leather'),
    limb('upper_inner',[0,3],[-2,length*.65],'bow_upper'),
    limb('upper_tip',[-2,length*.65],[-width,length],'bow_upper'),
    limb('lower_inner',[0,-3],[-2,-length*.65],'bow_lower'),
    limb('lower_tip',[-2,-length*.65],[-width,-length],'bow_lower'),
    box('string',[-width-.2,-length,-.2],[-width+.2,length,.2],'bone','string'),
    box('reinforcement',[-2,-2,-1.2],[0,2,1.2],'amber')];
};
const bowBones = () => [...root(),{name:'bow_upper',parent:'root',pivot:[0,3,0],rotation:[0,0,0]},
  {name:'bow_lower',parent:'root',pivot:[0,-3,0],rotation:[0,0,0]},
  {name:'string',parent:'root',pivot:[0,0,0],rotation:[0,0,0]}];
const crossbow = (large=false) => {
  const s=large?1.6:1;
  return [box('stock',[-1.5,-7,-1.5],[1.5,15*s,1.5],'wood'),box('grip',[-1.1,-2,1.5],[1.1,3,5],'leather'),
    box('left_limb',[-12*s,10*s,-1],[-1.5,13*s,1],'steel_dark'),box('right_limb',[1.5,10*s,-1],[12*s,13*s,1],'steel_dark'),
    box('bowstring',[-12*s,7*s-.25,-.25],[12*s,7*s+.25,.25],'bone','string'),box('rail',[-.5,0,-1.9],[.5,15*s,-1.5],'steel'),
    box('trigger',[-.4,-1,2],[.4,0,4],'amber')];
};
const knuckle = (claw=false) => [box('brace',[-3,-3,-2],[3,3,2],'leather'),box('knuckles',[-4,2,-2.2],[4,5,2.2],'steel_dark'),
  ...[-2.5,0,2.5].map((x,i)=>box(`spike_${i}`,[x-.5,5,-1.4],[x+.5,claw?16:7,-.4],claw?'steel':'amber'))];
const shield = (size=1,round=false) => {
  const cubes=[box('panel',[-5*size,-6*size,-1.2],[5*size,6*size,.2],'red'),
    box('boss',[-1.5,-1.5,-2.5],[1.5,1.5,-1.2],'amber'),box('handle',[-.6,-2,.2],[.6,2,2],'leather')];
  if(round) {
    cubes[0]=box('panel',[-3.5,-5,-1.2],[3.5,5,.2],'wood');
    cubes.push(box('panel_wide',[-5,-3.5,-1.2],[5,3.5,.2],'wood'));
    for(let i=0;i<8;i++)cubes.push(box(`rim_${i}`,[-2.5,4.7,-1.5],[2.5,5.6,.4],'steel_dark','root',[0,0,i*45],[0,0,0]));
  } else for(const [name,from,to] of [['top',[-5*size,6*size,-1.5],[5*size,7*size,.4]],
    ['bottom',[-5*size,-7*size,-1.5],[5*size,-6*size,.4]],['left',[-6*size,-7*size,-1.5],[-5*size,7*size,.4]],
    ['right',[5*size,-7*size,-1.5],[6*size,7*size,.4]]])cubes.push(box(`rim_${name}`,from,to,'steel_dark'));
  return cubes;
};
const entries=[];
function add(id,name,group,preset,art,cubes,bones=root(),extra={}) {
  entries.push({id,name,group,proposedPreset:preset,proposedArt:art,
    model:{schemaVersion:1,id:`eldencombat:${id}`,unitsPerBlock:16,textureSize:[128,128],texture:'weapons.png',
      rotationOrder:'XYZ',animationRotation:'additive_degrees',bones,cubes},...extra});
}
add('dagger','匕首','近战','epicfight:dagger','quickstep',blade(12,1.8));
for(const [id,name] of [['straight_sword','直剑'],['great_sword','大剑']])
  add(id,name,'近战',id==='great_sword'?'epicfight:greatsword':'epicfight:sword',id==='great_sword'?'heavy_slash':'square_off',structuredClone(equipment.find(e=>e.id.endsWith(':'+id)).cubes));
add('colossal_sword','特大剑','近战','epicfight:greatsword','grounded_slam',blade(40,7.4));
add('thrusting_sword','刺剑','近战','custom:spear_1h','impaling_thrust',[
  ...blade(26,.9),box('basket_left',[-3,0,-2],[-2.2,4,2],'amber'),box('basket_right',[2.2,0,-2],[3,4,2],'amber')]);
add('heavy_thrusting_sword','重刺剑','近战','custom:spear_1h','piercing_lunge',blade(33,2.6));
add('curved_sword','曲剑','近战','epicfight:sword','spinning_slash',blade(23,2.2,true));
add('curved_greatsword','大曲剑','近战','epicfight:greatsword','wide_sweep',blade(34,4.8,true));
add('katana','刀','近战','epicfight:uchigatana','unsheathe',[
  ...grip(true),box('guard',[-2,2,-2],[2,3,2],'amber'),box('blade_lower',[-.8,3,-.45],[.8,22,.45]),
  box('blade_upper',[0,22,-.45],[1.6,32,.45]),box('tip',[.5,32,-.35],[1.4,34,.35])]);
add('twinblade','双头剑','近战','custom:twinblade','double_slash',[
  box('grip',[-.8,-4,-.8],[.8,4,.8],'leather'),box('guard_top',[-3,4,-1],[3,5,1],'amber'),
  box('guard_bottom',[-3,-5,-1],[3,-4,1],'amber'),box('blade_top',[-1.2,5,-.5],[1.2,26,.5]),box('blade_bottom',[-1.2,-26,-.5],[1.2,-5,.5])]);
add('axe','斧','近战','epicfight:axe','war_cry',axe(19));
add('greataxe','大斧','近战','custom:heavy_axe','heavy_slash',axe(30,true));
add('hammer','槌','近战','custom:blunt_1h','endure',hammer(18));
add('great_hammer','大槌','近战','custom:blunt_2h','grounded_slam',hammer(30,true));
add('colossal_weapon','特大武器','近战','custom:blunt_2h','grounded_slam',[
  ...pole(26),box('stone_head',[-8,22,-5],[8,36,5],'stone'),box('band_1',[-8.3,24,-5.3],[8.3,26,5.3],'steel_dark'),
  box('band_2',[-8.3,32,-5.3],[8.3,34,5.3],'steel_dark')]);
add('flail','连枷','近战','custom:flail','spinning_chain',[
  ...grip(),box('haft',[-.7,2,-.7],[.7,15,.7],'wood'),
  box('link_1',[-.35,15,-.35],[.35,18,.35],'steel_dark','chain_01'),
  box('link_2',[-.35,18,-.35],[.35,21,.35],'steel_dark','chain_02'),
  box('ball',[-2.5,21,-2.5],[2.5,26,2.5],'steel_dark','chain_03'),
  box('spike',[-3.8,23,-.6],[3.8,24,.6],'steel','chain_03')],
  [...root(),...['01','02','03'].map((n,i)=>({name:'chain_'+n,parent:i?'chain_0'+i:'root',pivot:[0,15+i*3,0],rotation:[0,0,0]}))]);
add('spear','矛','近战','epicfight:spear','impaling_thrust',spear());
add('great_spear','大矛','近战','epicfight:spear','piercing_lunge',spear(44,true));
add('halberd','戟','近战','custom:halberd','wide_sweep',[
  ...spear(36),box('axe_head',[1.3,31,-.8],[7,40,.8]),box('axe_edge',[7,29,-.5],[8.5,41,.5]),
  box('hook',[-5,33,-.8],[-1.3,35,.8],'steel_dark')]);
add('scythe','镰刀','近战','custom:scythe','spinning_slash',[
  ...pole(36),box('head_1',[-10,34,-.6],[1,37,.6],'steel_dark'),box('head_2',[-15,31,-.5],[-9,35,.5]),
  box('tip',[-18,27,-.4],[-14,32,.4])]);
add('whip','软鞭','近战','custom:whip','whip_crack',[
  ...grip(),box('grip_long',[-.6,2,-.6],[.6,8,.6],'leather'),
  box('lash_1',[-.4,8,-.4],[.4,20,.4],'leather','lash_01'),
  box('lash_2',[-.35,20,-.35],[.35,32,.35],'leather','lash_02'),
  box('lash_3',[-.3,32,-.3],[.3,44,.3],'leather','lash_03')],
  [...root(),...['01','02','03'].map((n,i)=>({name:'lash_'+n,parent:i?'lash_0'+i:'root',pivot:[0,8+i*12,0],rotation:[0,0,i?30:0]}))]);
add('fist','拳套','近战','epicfight:fist','rapid_fists',knuckle());
add('claw','钩爪','近战','custom:claw','quickstep',knuckle(true));
add('short_bow','短弓','远程','epicfight:bow','barrage',bow(),bowBones());
add('long_bow','长弓','远程','epicfight:bow','mighty_shot',bow(true),bowBones());
add('great_bow','大弓','远程','epicfight:bow','mighty_shot',bow(true).map(c=>({...c,from:c.from.map(n=>n*1.25),to:c.to.map(n=>n*1.25),pivot:(c.pivot||c.from).map(n=>n*1.25)})),
  bowBones().map(b=>({...b,pivot:b.pivot.map(n=>n*1.25)})));
add('crossbow','弩','远程','epicfight:crossbow','power_bolt',crossbow(),[...root(),{name:'string',parent:'root',pivot:[0,7,0],rotation:[0,0,0]}]);
add('ballista','弩炮','远程','custom:crossbow_heavy','power_bolt',crossbow(true),[...root(),{name:'string',parent:'root',pivot:[0,11.2,0],rotation:[0,0,0]}]);
add('glintstone_staff','辉石杖','施法','custom:catalyst','glintstone_bolt',[
  ...pole(32),box('crown',[-3,31,-2],[3,34,2],'amber'),box('crystal',[-1.8,34,-1.8],[1.8,40,1.8],'crystal'),
  box('crystal_tip',[-1,40,-1],[1,42,1],'crystal')]);
add('sacred_seal','圣印记','施法','custom:catalyst','healing_prayer',[
  box('handle',[-.6,-4,-.6],[.6,0,.6],'leather'),...Array.from({length:8},(_,i)=>box(`ring_${i}`,[-2.3,0,-.5],[2.3,1,.5],'amber','root',[0,0,i*45],[0,3,0])),
  box('sigil',[-.5,1,-.8],[.5,5,.8],'amber')]);
add('small_shield','小盾','防具','epicfight:shield','parry',shield(.7,true));
add('shield','中盾','防具','epicfight:shield','parry',structuredClone(equipment.find(e=>e.id.endsWith(':shield')).cubes));
add('great_shield','大盾','防具','epicfight:shield','shield_bash',shield(1.45));

export const weapons=entries.map(refineWeapon);
export const arts=[
  ['square_off','准备架式','stance',14,22,1.5,'直剑：架式后衔接横斩，不位移。'],
  ['heavy_slash','重斩','melee',18,28,1.8,'大剑 / 大斧：地面重击，增加削韧。'],
  ['grounded_slam','踏步砸击','melee',24,36,2.1,'重武器：短步落槌，不跳跃，命中后产生近距离震波。'],
  ['quickstep','迅步','movement',10,12,0,'匕首 / 钩爪：按输入方向短闪，检测地形，不造成伤害。'],
  ['impaling_thrust','贯穿','melee',14,20,1.6,'刺剑 / 矛：前方直刺，附带部分防御穿透。'],
  ['piercing_lunge','突刺','melee',18,26,1.8,'重刺剑 / 大矛：受地形限制的短距离冲刺后刺击。'],
  ['spinning_slash','回旋斩','melee',18,24,1.25,'曲剑 / 镰刀：地面两段横扫，可选择第二段。'],
  ['wide_sweep','横扫','melee',20,28,1.5,'大曲剑 / 戟：宽角地面横扫，不用圆形全向伤害。'],
  ['unsheathe','居合','stance',16,20,1.7,'刀：纳刀架式到拔刀斩，无处决和空中下砸分支。'],
  ['double_slash','连续斩','melee',20,26,1.0,'双头剑：三段连斩，各段各自记录命中。'],
  ['war_cry','战吼','buff',16,22,0,'斧：短暂增强攻击，不能重复叠加。'],
  ['endure','忍耐','buff',18,14,0,'槌：短时间减伤与抗打断，不是无敌。'],
  ['spinning_chain','旋转连枷','melee',20,30,1.3,'连枷：三段链节驱动球头扫掠，最多一次主击结算。'],
  ['whip_crack','鞭打','melee',16,22,1.5,'软鞭：三段鞭体展开，使用多段胶囊，不隔墙命中。'],
  ['rapid_fists','连续拳','melee',18,20,.65,'拳套：连续短距离打击。'],
  ['barrage','连续射击','projectile',18,30,1,'短弓：三箭，逐箭扣弹药，不能空发。'],
  ['mighty_shot','强力射击','projectile',22,30,1.8,'长弓 / 大弓：蓄力箭，消耗一支箭。'],
  ['power_bolt','重弩射击','projectile',22,32,1.8,'弩 / 弩炮：增强下一次已装填射击，不能凭空装填。'],
  ['glintstone_bolt','辉石弹','projectile',14,20,1,'辉石杖：定向魔法弹，需要真实投射物与施法资源。'],
  ['healing_prayer','恢复祷告','support',24,36,0,'圣印记：引导后治疗，受击打断，范围限定自身 / 同队。'],
  ['parry','弹反','guard',0,16,0,'小盾 / 中盾：精确前方时窗，不触发处决。'],
  ['shield_bash','盾击','melee',16,24,1.2,'大盾：地面盾击，消耗精力，没有跳跃分支。']
].map(([id,name,kind,fp,totalTicks,damageMultiplier,description])=>({id,name,kind,fp,totalTicks,damageMultiplier,description,
  status:'design-only; not implemented',source:'fan prototype; not an exact Elden Ring timing table'}));
