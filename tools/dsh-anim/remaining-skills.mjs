import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';

const here=path.dirname(fileURLToPath(import.meta.url));
const specs=path.join(here,'specs'),out=path.resolve(here,'../../src/main/resources/assets/eldencombat/animmodels/animations');
const parameters=JSON.parse(fs.readFileSync(path.resolve(here,'../../src/main/resources/data/eldencombat/dsh_skills/remembrance_parameters.json')));
const base={Root:[0,0,0],Torso:[-4,0,0],Chest:[-3,0,0],Head:[5,0,0],
  Thigh_R:[-4,0,8],Leg_R:[8,0,0],Knee_R:[8,0,0],Thigh_L:[4,0,-8],Leg_L:[8,0,0],Knee_L:[8,0,0],
  Shoulder_R:[0,0,15],Arm_R:[30,0,0],Hand_R:[0,0,0],
  Shoulder_L:[0,0,-15],Arm_L:[25,0,0],Hand_L:[0,0,0]};
const key=(tick,pose={})=>({t:tick/20,poses:Object.fromEntries(Object.entries({...base,...pose}).map(([joint,r])=>[joint,{r}]))});
const overhead={Torso:[8,0,0],Chest:[8,0,0],Shoulder_R:[-65,0,135],Arm_R:[70,0,0],
  Shoulder_L:[-65,0,-110],Arm_L:[65,0,0],Thigh_R:[-30,0,10],Thigh_L:[-20,0,-10]};
const slam={Torso:[-28,0,0],Chest:[-18,0,0],Shoulder_R:[65,0,70],Arm_R:[-15,0,0],
  Shoulder_L:[55,0,-65],Arm_L:[-10,0,0],Thigh_R:[-25,0,12],Thigh_L:[15,0,-12]};
const sweep=(yaw,side=1)=>({Root:[0,yaw*.2,0],Torso:[-8,yaw*.22,0],Chest:[-4,yaw*.12,0],
  Shoulder_R:[10,yaw,85],Arm_R:[25,0,0],Hand_R:[0,yaw*.2,0],
  Shoulder_L:[15,-yaw*.2,-30],Arm_L:[40,0,0],Thigh_R:[-15*side,0,12],Thigh_L:[15*side,0,-12]});
const bothUp={Torso:[4,0,0],Chest:[5,0,0],Shoulder_R:[-25,-20,140],Arm_R:[30,0,0],
  Shoulder_L:[-25,20,-140],Arm_L:[30,0,0]};
const ritual={Torso:[6,0,0],Chest:[8,0,0],Shoulder_R:[-20,0,155],Arm_R:[25,0,0],
  Shoulder_L:[15,0,-35],Arm_L:[35,0,0],Hand_R:[0,0,10]};
const definitions=[
  {id:'gold_breaker',keys:[[0],[4,{...overhead,Torso:[-18,0,0]}],[8,overhead],[14,overhead],[20,slam],[24,slam],[34]]},
  {id:'destined_death',keys:[[0],[4,sweep(-70)],[8,sweep(-100)],[12,sweep(95)],
    [16,{...sweep(-95),Root:[0,85,0]}],[20,{...sweep(95),Root:[0,165,0]}],
    [24,{...overhead,Root:[0,250,0]}],[28,{...slam,Root:[0,350,0]}],[32,slam],[42]]},
  {id:'takers_flames',keys:[[0],[5,overhead],[9,overhead],[11,slam],[13,slam],[23]]},
  {id:'waterfowl_dance',keys:[[0],[6,{...overhead,Thigh_R:[-60,0,8],Thigh_L:[-50,0,-8]}],
    [10,sweep(-100)],[13,sweep(90)],[16,sweep(-95,-1)],[19,sweep(95)],
    [22,sweep(-110,-1)],[25,sweep(100)],[28,sweep(-95)],[31,sweep(90,-1)],
    [34,sweep(-115)],[37,sweep(110)],[40,sweep(-100,-1)],[43,sweep(120)],[46,sweep(60)],[56]]},
  {id:'starcaller_cry',keys:[[0],[4,{...bothUp,Shoulder_R:[0,-25,90],Shoulder_L:[0,25,-90]}],
    [8,bothUp],[11,{...bothUp,Chest:[12,0,0]}],[14,bothUp],[24]]},
  {id:'starcaller_cry_followup',skill:'starcaller_cry',followup:true,keys:[[0,bothUp],[6,overhead],
    [12,{...slam,Shoulder_L:[65,0,-70],Arm_L:[-15,0,0]}],[14,slam],[24]]},
  {id:'bloodboon_ritual',keys:[[0],[3,{...ritual,Shoulder_R:[-10,0,100]}],[6,ritual],
    [8,{...ritual,Chest:[14,0,0]}],[10,ritual],[14]]},
  {id:'cursed_blood_slice',keys:[[0],[4,sweep(-65)],[8,sweep(-100)],[10,sweep(95)],
    [12,sweep(110)],[14,sweep(75)],[24]]},
  {id:'cursed_blood_slice_followup',skill:'cursed_blood_slice',followup:true,
    keys:[[0,sweep(70)],[4,sweep(95)],[6,sweep(-100)],[8,sweep(-65)],[18]]}
];
fs.mkdirSync(specs,{recursive:true});fs.mkdirSync(out,{recursive:true});
for(const definition of definitions) {
  const parameter=parameters.find(p=>p.id===(definition.skill??definition.id));
  const ticks=(definition.followup?parameter.followupWindupTicks:parameter.windupTicks)
    +(definition.followup?parameter.followupActiveTicks:parameter.activeTicks)+parameter.recoveryTicks;
  // Preserve all attack keys; only compress the return-to-stance segment.
  const contact=(definition.followup?parameter.followupWindupTicks:parameter.windupTicks)
    +(definition.followup?parameter.followupActiveTicks:parameter.activeTicks);
  const oldEnd=definition.keys.at(-1)[0];
  if(oldEnd<=contact)throw new Error(`${definition.id}: missing recovery segment`);
  const retimedKeys=definition.keys.map(([tick,pose])=>[
    tick<=contact?tick:contact+(tick-contact)*parameter.recoveryTicks/(oldEnd-contact),pose]);
  const spec={id:definition.id,duration:ticks/20,provenance:'Original prototype poses; EF biped binding only, no extracted game animation',
    note:'Root translation remains zero; attack keys unchanged, recovery only retimed to skill parameters.',keys:retimedKeys.map(([t,p])=>key(t,p))};
  const specPath=path.join(specs,definition.id+'.json');fs.writeFileSync(specPath,JSON.stringify(spec,null,2)+'\n');
  const result=spawnSync(process.execPath,[path.join(here,'posegen.mjs'),specPath,path.join(out,definition.id+'.json')],{encoding:'utf8'});
  if(result.status!==0)throw new Error(result.stderr||result.stdout);process.stdout.write(result.stdout);
}
