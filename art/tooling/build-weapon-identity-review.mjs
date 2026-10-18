import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {PNG,shapes,line,project,stats,opaque} from './equipment-pixels.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),out=path.join(root,'art/equipment-2d/identity-review');
const read=async f=>JSON.parse(await fs.readFile(path.join(root,f),'utf8')),hash=b=>createHash('sha256').update(b).digest('hex');
await fs.mkdir(path.join(out,'sprites'),{recursive:true});await fs.mkdir(path.join(out,'before'),{recursive:true});
const data=await read('art/equipment-2d/full/equipment-42.json'),selected=['marikas_hammer','blasphemous_blade','morgotts_cursed_sword'];
const reasons={
 axe:'斧头与柄清楚，类别足够；不指认为法环某把具名斧。',claw:'平行三爪与腕箍已独立于拳套。',colossal_sword:'用户选定旧版；保留宽刃与装饰带。',colossal_weapon:'大体积锤头已有类别差异，暂无具名替换依据。',curved_greatsword:'保留宽弯刃，与短曲剑长度不同。',curved_sword:'短弯刃与护手可读。',dagger:'短刃与环护手清楚；提供方映射非实机证明。',fist:'拳套块面及腕箍与钩爪可分。',flail:'锤头与柄可分；链节在16像素弱，后续候选。',glintstone_staff:'长杆与蓝色石端可读。',great_hammer:'双面大锤头保留，类别映射非具名武器。',great_shield:'矩形长盾与中小盾区别明确。',great_spear:'窄头长杆与三叉蒙格矛不同。',great_sword:'用户选定旧版，保留。',greataxe:'双刃重斧与普通单刃斧可分。',halberd:'侧斧与顶刺可读；保持提供方状态。',hammer:'棱锤头可辨，维持类别Mace说明。',heavy_thrusting_sword:'重刺剑与直剑16图仍接近，后续看加宽刃根而非全图放大。',katana:'窄长单刃区别于大剑，普通类别不冒充义手刀。',sacred_seal:'掌中符印与长杖区分明确。',scythe:'横向镰头清楚。',shield:'红底金十字与灰边保留正反面。',small_shield:'圆盾与大中盾区别明确。',spear:'标准矛头、长杆保留。',straight_sword:'标准直剑类别清楚，保留原版引用。',thrusting_sword:'细刃与侧护手有区别。',twinblade:'两端刃保留；16图护手细节不足不影响类别。',whip:'静置弯曲可辨；不在本批扩展攻击形变。',short_bow:'短弓弯臂保留与拉弓状态一致。',long_bow:'加固弓臂与长度保留。',great_bow:'大弓状态已有；不因图标占槽小改变世界长度。',crossbow:'原版弩结构和状态可辨。',ballista:'横宽弩臂与长身保留；不以普通弩代替。',test_flask:'瓶颈瓶肩已强化，满瓶态保留。',
 sacred_relic_sword:'保留当前金色块面和分叉护手；外来缩图细节在16槽位不胜现有版。',hand_of_malenia:'保留金色窄长刀身与现有弯势；刃根细节16图弱，列后续观察。',starscourge_greatsword:'当前单刃黑金块面保留；双持由运行摆放，不挤入同一持用图。',malikeths_black_blade:'层叠黑刃与金护手已有区别；暗背景裂面偏弱，列后续观察。',mohgwyns_sacred_spear:'三叉主尖、金色叉根、深杆已独立可辨。',marikas_hammer:'金色钉状锤头偏金属；参考10414提炼灰石碎块与少量金裂。',blasphemous_blade:'当前肉红长刃接近普通红剑；参考10094强化贴附肢体与暗刃对比。',morgotts_cursed_sword:'当前宽刃偏短厚；参考10288强化细长曲刃与分段油彩。'};
const later=new Set(['flail','heavy_thrusting_sword','malikeths_black_blade','hand_of_malenia']);
const refs={sacred_relic_sword:'10090_神躯化剑',blasphemous_blade:'10094_亵渎圣剑',malikeths_black_blade:'10122_玛利喀斯的黑剑',starscourge_greatsword:'10125_碎星大剑',morgotts_cursed_sword:'10288_蒙葛特咒剑',hand_of_malenia:'10322_玛莲妮亚的义手刀',marikas_hammer:'10414_玛莉卡的石槌',mohgwyns_sacred_spear:'10641_蒙格温圣矛'};
const audit=[],candidates=[],protectedFiles=[];
for(const a of data.assets){
 const pngs=[];for(const v of [...a.variants,...a.gui]){const f=a.delivery==='provider_reference_only'?v.file:'art/equipment-2d/full/'+v.file;const b=await fs.readFile(path.join(root,f));pngs.push({file:f,sha256:hash(b),state:v.state});}
 const ref=refs[a.id]?`dist/法环武器图标/武器/${refs[a.id]}.png`:null;
 audit.push({id:a.id,name:a.name,category:a.category,decision:selected.includes(a.id)||later.has(a.id)?'需强化特色':'保留当前',referenceAvailability:ref?'存在可用参考':'普通类别沿用形制，无需具名替换',reason:reasons[a.id],reference:ref,delivery:a.delivery,runtimeIntegrated:false,inGameAccepted:false,currentPngs:pngs});
 if(['sacred_relic_sword','great_sword','colossal_sword'].includes(a.id)){for(const f of [a.source,...pngs.map(p=>p.file)])protectedFiles.push({file:f,sha256:hash(await fs.readFile(path.join(root,f)))});}
 if(!selected.includes(a.id))continue;
 const source=await read(a.source),base=shapes(source),referenceBytes=await fs.readFile(path.join(root,ref));
 let ps=base.map(p=>({...p,points:p.points.map(p=>[...p])}));
 if(a.id==='marikas_hammer'){
   ps=ps.filter(p=>!(p.points.every(v=>v[1]>21)&&p.color[0]>p.color[2]*1.3));
   ps=ps.map(p=>{const x=p.points.reduce((n,v)=>n+v[0],0)/4,y=p.points.reduce((n,v)=>n+v[1],0)/4;return {...p,color:y>15?x<-1?[91,92,83]:x>2?[130,129,113]:[109,110,98]:y>3?[89,86,70]:p.color}});
   ps.push(line([-5,21],[-1,20],.65,[203,169,66]),line([-1,20],[1,17],.65,[203,169,66]),line([-1,20],[4.5,22],.7,[219,184,77]));
 }
 if(a.id==='blasphemous_blade'){
   ps=ps.map(p=>({...p,color:p.points.every(v=>v[1]>6)?[Math.round(p.color[0]*.6),Math.round(p.color[1]*.6),Math.round(p.color[2]*.6)]:p.color}));
   for(let y=9,i=0;y<34;y+=5,i++){const s=i%2?1:-1;ps.push(line([0,y-2],[s*2.5,y],.9,[140,58,52]),line([s*2.5,y],[s*3.5,y+2],.8,[164,79,63]),line([s*3.5,y+2],[s*2,y+3],.7,[182,105,78]));}
 }
 if(a.id==='morgotts_cursed_sword'){
   ps=ps.map(p=>({...p,points:p.points.map(([x,y])=>{if(y<5)return [x,y];const t=Math.min(1,(y-5)/39);return [x*.72+3*Math.sin(t*Math.PI),y]}),color:p.points.every(v=>v[1]>6)?([ [88,67,89],[105,123,112],[136,116,133],[78,103,111] ][Math.floor(p.points[0][1]/8)%4]):p.color}));
 }
 const variants=[];
 for(const v of [...a.variants,...a.gui]){
   const before=await fs.readFile(path.join(root,'art/equipment-2d/full',v.file)),file=path.basename(v.file);await fs.writeFile(path.join(out,'before',file),before);
   const p=project(ps,v.size[0],45,base);assert(opaque(p.png,...v.anchorPixels),a.id+' grip');const st=stats(p.png);assert.equal(st.components8.length,1,a.id+' disconnected');assert(st.transparentMargin,a.id+' clipped');
   const bytes=PNG.sync.write(p.png);await fs.writeFile(path.join(out,'sprites',file),bytes);
   variants.push({...v,file:'sprites/'+file,url:'sprites/'+file,before:'before/'+file,beforeHash:hash(before),sha256:hash(bytes),...st,anchorPixels:v.anchorPixels});
 }
 candidates.push({...a,variants,gui:[],reference:ref,referenceHash:hash(referenceBytes),features:a.id==='marikas_hammer'?['灰石锤头','分裂金线','暗杆与石块对比']:a.id==='blasphemous_blade'?['暗色长刃','肉红缠绕肢体','贴附弯指轮廓']:['细长曲刃','分段油彩色带','原护手与柄连接'],thickness:{units:1,blocks:.0625,displayZIndependentOfXY:true},status:'candidate_pending_review',runtimeIntegrated:false});
}
for(const f of protectedFiles)assert.equal(hash(await fs.readFile(path.join(root,f.file))),f.sha256);
const result={date:'2026-10-16',scope:'42 audited, 3 authored candidates, no runtime edits',audit,candidates,protectedFiles,sourcePublication:'Original dist images are local reference only; no source image copied into candidate package',validation:{protectedUnchanged:true,connected:true,opaqueGrips:true,alpha:'0/255',browser:false,authorNameHiddenReview:false,humanBlindTest:false,inGameAccepted:false}};
await fs.writeFile(path.join(out,'manifest.json'),JSON.stringify(result,null,2)+'\n');await fs.writeFile(path.join(out,'preview-data.js'),'window.IDENTITY='+JSON.stringify(result)+';\n');
console.log('PASS 42 audit entries, 3 candidates / 9 PNGs; before snapshots, grips, alpha connectivity and protected files verified.');
