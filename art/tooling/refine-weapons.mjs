const cube = (name,from,to,material='steel',bone='root',rotation=[0,0,0],pivot=from) =>
  ({name,bone,from,to,material,rotation,pivot});

// Detail stays on the existing rigid bones; ornaments do not add joints.
export function refineWeapon(entry) {
  const model=entry.model, original=[...model.cubes], detail=[];
  let serial=0;
  const add=(label,from,to,material='steel',bone='root',rotation=[0,0,0],pivot=from)=>
    detail.push(cube(`detail_${label}_${serial++}`,from,to,material,bone,rotation,pivot));
  const line=(label,a,b,width,material='amber',bone='root')=> {
    const length=Math.hypot(b[0]-a[0],b[1]-a[1]);
    add(label,[a[0]-width/2,a[1],a[2]-.1],[a[0]+width/2,a[1]+length,a[2]+.1],material,bone,
      [0,0,-Math.atan2(b[0]-a[0],b[1]-a[1])*180/Math.PI],a);
  };
  const rivet=(x,y,z,bone='root')=>add('rivet',[x-.18,y-.18,z-.12],[x+.18,y+.18,z+.12],'silver_edge',bone);

  for(const c of original.filter(c=>/^(grip|grip_long|handle)$/.test(c.name))) {
    if(entry.group==='防具')continue;
    const width=c.to[0]-c.from[0],depth=c.to[2]-c.from[2];
    for(let y=c.from[1]+.4;y<c.to[1]-.2;y+=.85)
      add('wrapped_grip',[c.from[0]-.08,y,c.from[2]-.08],[c.to[0]+.08,y+.22,c.to[2]+.08],
        'leather_wrap',c.bone,[0,0,7],[0,y,0]);
    for(const y of [c.from[1],c.to[1]-.35])
      add('grip_ferrule',[-width/2-.16,y,-depth/2-.16],[width/2+.16,y+.35,depth/2+.16],'amber',c.bone);
  }

  const swords=['dagger','straight_sword','great_sword','colossal_sword','thrusting_sword','heavy_thrusting_sword',
    'curved_sword','curved_greatsword','katana','twinblade'];
  if(swords.includes(entry.id)) {
    const blades=original.filter(c=>/^blade/.test(c.name));
    model.cubes=model.cubes.filter(c=>!/^blade|^tip$/.test(c.name));
    for(const c of blades) {
      const width=c.to[0]-c.from[0],height=c.to[1]-c.from[1],center=(c.to[0]+c.from[0])/2;
      const pieces=entry.id==='colossal_sword'?8:5;
      for(let i=0;i<pieces;i++) {
        const y=c.from[1]+height*i/pieces,next=c.from[1]+height*(i+1)/pieces;
        const half=width/2*(1-.2*i/pieces),edge=Math.min(.26,width*.18),thick=(c.to[2]-c.from[2])/2;
        add('blade_core',[center-half+edge,y,-thick],[center+half-edge,next,thick],'steel',c.bone);
        for(const sign of [-1,1]) {
          const x=center+sign*(half-edge/2);
          add('blade_bevel',[x-edge/2,y,-thick*.6],[x+edge/2,next,thick*.6],'silver_edge',c.bone);
          if(width>1.6)add('fuller',[center-.15,y,sign>0?thick: -thick-.07],
            [center+.15,next,sign>0?thick+.07:-thick],'steel_dark',c.bone);
        }
      }
      if(entry.id==='colossal_sword')for(let y=c.from[1]+2;y<c.to[1]-2;y+=3.5)
        for(const z of [-.74,.74])line('blade_rune',[center-.5,y,z],[center+.5,y+1,z],.12,'amber');
    }
    for(const c of original.filter(c=>c.name==='tip')) {
      const cx=(c.from[0]+c.to[0])/2,half=(c.to[0]-c.from[0])/2,height=c.to[1]-c.from[1];
      for(let i=0;i<5;i++) {
        const w=Math.max(.07,half*(1-i/5));
        add('blade_tip',[cx-w,c.from[1]+height*i/5,-.25],[cx+w,c.from[1]+height*(i+1)/5,.25],'silver_edge');
      }
    }
    const guard=original.find(c=>/guard|crossguard/.test(c.name));
    if(guard && entry.id!=='katana') {
      const y=(guard.from[1]+guard.to[1])/2,w=Math.max(Math.abs(guard.from[0]),Math.abs(guard.to[0]));
      for(const sign of [-1,1]) {
        add('quillon',[sign*w-.4,y-.5,-.8],[sign*w+.4,y+1.5,.8],'amber','root',[0,0,sign*20],[sign*w,y,0]);
        rivet(sign*w*.65,y,-1);rivet(sign*w*.65,y,1);
      }
      add('ricasso',[-.6,y+.6,-.6],[.6,y+2,.6],'steel_dark');
    }
    if(entry.id==='katana') {
      for(let i=0;i<4;i++)add('tsuba',[-1.8,2.7,-1.8],[1.8,3.05,1.8],'steel_dark','root',[0,i*45,0],[0,2.7,0]);
      for(const x of [-1.2,1.2])add('tsuba_inlay',[x-.2,3.05,-1.2],[x+.2,3.12,1.2],'amber');
      add('habaki',[-.95,3.1,-.6],[.95,4.3,.6],'amber');
    }
    const pommel=original.find(c=>c.name==='pommel');
    if(pommel) {
      const y=(pommel.from[1]+pommel.to[1])/2;
      for(let i=0;i<4;i++)add('pommel_facet',[-.7,y-.7,-.8],[.7,y+.7,.8],'amber','root',[0,0,i*45],[0,y,0]);
      add('pommel_gem',[-.35,y-.35,-.92],[.35,y+.35,-.8],entry.id==='katana'?'red':'crystal');
    }
    if(entry.id==='thrusting_sword')for(const sign of [-1,1])
      line('basket_arc',[sign*2.5,0,-2],[sign*3.2,3,-2],.25,'amber');
  }

  for(const c of original.filter(c=>/^(shaft|haft|stock)$/.test(c.name))) {
    const top=c.to[1],bottom=c.from[1],w=(c.to[0]-c.from[0])/2;
    for(const y of [bottom+.5,(bottom+top)/2,top-1.5])
      add('shaft_ferrule',[-w-.22,y,c.from[2]-.2],[w+.22,y+.8,c.to[2]+.2],'steel_dark');
    if(c.name!=='stock')for(let y=bottom+1;y<Math.min(top,7);y+=1)
      add('shaft_binding',[-w-.08,y,c.from[2]-.09],[w+.08,y+.22,c.to[2]+.09],'leather_wrap');
  }

  if(['spear','great_spear','halberd'].includes(entry.id)) {
    const head=original.find(c=>c.name==='spearhead'),cx=(head.from[0]+head.to[0])/2;
    model.cubes=model.cubes.filter(c=>!['spearhead','tip'].includes(c.name));
    const height=12,base=head.from[1],wide=(head.to[0]-head.from[0])/2;
    for(let i=0;i<9;i++) {
      const w=wide*(i<3?.55+i*.15:Math.max(.08,1-(i-2)/7));
      add('spearhead',[-w,base+i*height/9,-.32],[w,base+(i+1)*height/9,.32],'silver_edge');
      add('spear_spine',[cx-.15,base+i*height/9,-.5],[cx+.15,base+(i+1)*height/9,.5],'steel');
    }
    for(const sign of [-1,1])line('socket_engraving',[sign*.7,base-1,-1.24],[0,base+1,-1.24],.13,'amber');
  }

  if(['axe','greataxe','halberd','scythe'].includes(entry.id)) {
    for(const c of original.filter(c=>/^(head|axe_head|head_1)$/.test(c.name))) {
      for(const z of [c.from[2]-.14,c.to[2]+.14]) {
        line('head_inlay',[c.from[0]+.4,c.from[1]+.6,z],[c.to[0]-.5,c.to[1]-.5,z],.16,'amber');
        for(const x of [c.from[0]+.6,c.to[0]-.6])rivet(x,(c.from[1]+c.to[1])/2,z);
      }
    }
    for(const c of original.filter(c=>/edge|^head_2$/.test(c.name))) {
      const x=c.to[0]-.22;
      add('cutting_edge',[x,c.from[1],c.from[2]*.5],[c.to[0]+.12,c.to[1],c.to[2]*.5],'silver_edge');
    }
  }

  if(['hammer','great_hammer','colossal_weapon','flail'].includes(entry.id)) {
    const head=original.find(c=>/^(head|stone_head|ball)$/.test(c.name));
    const cy=(head.from[1]+head.to[1])/2,bone=head.bone;
    for(const z of [head.from[2]-.15,head.to[2]+.15]) {
      for(const x of [head.from[0]+.5,head.to[0]-.5])for(const y of [head.from[1]+.6,head.to[1]-.6])rivet(x,y,z,bone);
      if(entry.id!=='flail')for(const sign of [-1,1])
        line('head_crest',[0,cy,z],[sign*1.5,cy+1.5,z],.3,'amber',bone);
    }
    if(entry.id==='flail') {
      for(const c of original.filter(c=>/^link_/.test(c.name)))for(const z of [-.45,.45])
        add('chain_loop',[-.65,c.from[1],z-.13],[.65,c.to[1],z+.13],'silver_edge',c.bone);
      for(const [x,z] of [[-3.4,0],[3.4,0],[0,-3.4],[0,3.4]])
        add('ball_spike',[x-.35,cy-.4,z-.35],[x+.35,cy+.4,z+.35],'silver_edge',bone);
    }
    if(entry.id==='colossal_weapon')for(let i=0;i<4;i++)
      line('stone_crack',[-5+i*2,24+i,-5.05],[-4+i*2,29+i,-5.05],.2,'engraving');
  }

  if(entry.id==='whip')for(const c of original.filter(c=>/^lash_/.test(c.name))) {
    for(let y=c.from[1]+1;y<c.to[1];y+=2)
      add('whip_braid',[c.from[0]-.06,y,c.from[2]-.06],[c.to[0]+.06,y+.18,c.to[2]+.06],'leather_wrap',c.bone);
    if(c.name==='lash_3')add('whip_tip',[-.38,c.to[1]-.8,-.38],[.38,c.to[1]+1,.38],'steel_dark',c.bone);
  }

  if(['fist','claw'].includes(entry.id)) {
    model.cubes=model.cubes.filter(c=>c.name!=='knuckles');
    for(let i=0;i<4;i++) {
      const x=-3+i*2;
      add('knuckle_plate',[x-.85,2,-2.35],[x+.85,5,2.35],'steel_dark');
      add('knuckle_ridge',[x-.55,4,-2.6],[x+.55,5.6,-1.9],'silver_edge');
      rivet(x,3,-2.65);
    }
    for(const y of [-2,0])add('wrist_strap',[-3.2,y,-2.15],[3.2,y+.5,2.15],'leather_wrap');
    add('wrist_buckle',[-1,-2.3,-2.5],[1,-1.2,-2.15],'amber');
    if(entry.id==='claw')for(const c of original.filter(c=>/^spike_/.test(c.name))) {
      const x=(c.from[0]+c.to[0])/2;
      add('claw_hook',[x-.3,14,-1.8],[x+.3,17,-.9],'silver_edge','root',[25,0,0],[x,14,-1]);
    }
  }

  if(entry.id.endsWith('bow')&&entry.id!=='crossbow') {
    for(const c of original.filter(c=>/^(upper|lower)/.test(c.name))) {
      for(const t of [.15,.85]) {
        const y=c.from[1]+(c.to[1]-c.from[1])*t;
        add('limb_binding',[c.from[0]-.12,y,-.92],[c.to[0]+.12,y+.45,.92],'amber',c.bone,c.rotation,c.pivot);
      }
    }
    add('arrow_rest',[-2,-.6,-1.6],[1,.1,-.8],'steel_dark');
    add('bow_sight',[.7,1,-1.4],[1.3,3,-.8],'amber');
  }
  if(['crossbow','ballista'].includes(entry.id)) {
    const stock=original.find(c=>c.name==='stock');
    add('stock_inlay',[-.45,-6,-1.62],[.45,stock.to[1]-1,-1.5],'amber');
    for(const c of original.filter(c=>c.name.endsWith('_limb'))) {
      for(const x of [c.from[0]+.6,c.to[0]-.6])rivet(x,(c.from[1]+c.to[1])/2,-1.2);
      add('limb_edge',c.from.map((n,i)=>i===2?n-.12:n),[c.to[0],c.from[1]+.3,c.to[2]],'silver_edge');
    }
    add('sight_front',[-.2,stock.to[1]-.7,-2.7],[.2,stock.to[1],-1.7],'steel_dark');
    for(const x of [-.8,.8])add('sight_rear',[x-.15,0,-2.4],[x+.15,.5,-1.6],'steel_dark');
    add('trigger_guard_left',[-1.5,-2,2],[-1.1,1,4.7],'steel_dark');
    add('trigger_guard_right',[1.1,-2,2],[1.5,1,4.7],'steel_dark');
    add('trigger_guard_bridge',[-1.5,-2,4.3],[1.5,-1.6,4.7],'steel_dark');
  }

  if(entry.id==='glintstone_staff') {
    for(const sign of [-1,1]) {
      line('crown_prong',[sign*2,32,-2.1],[sign*3.2,38,-2.1],.6,'amber');
      add('crystal_facet',[sign*1.5-.4,35,-2.1],[sign*1.5+.4,39,-1.2],'crystal','root',[0,0,sign*12],[sign*1.5,35,-1.2]);
    }
    for(let y=10;y<30;y+=4)line('staff_rune',[-.35,y,-.84],[.35,y+1,-.84],.13,'amber');
    add('crystal_core',[-.65,35,-1.95],[.65,40,-1.8],'silver_edge');
  }
  if(entry.id==='sacred_seal') {
    for(let i=0;i<8;i++) {
      const angle=i*Math.PI/4,x=Math.sin(angle)*3,y=3+Math.cos(angle)*3;
      add('seal_gem',[x-.3,y-.3,-.76],[x+.3,y+.3,-.53],i%2?'ivory':'crystal');
    }
    line('seal_sigil_a',[-1.2,2,-.92],[1.2,4,-.92],.22,'ivory');
    line('seal_sigil_b',[1.2,2,-.92],[-1.2,4,-.92],.22,'ivory');
  }

  if(entry.group==='防具') {
    const panel=original.find(c=>c.name==='panel'),sx=Math.max(Math.abs(panel.from[0]),Math.abs(panel.to[0])),sy=panel.to[1];
    const z=panel.from[2]-.18;
    for(const sign of [-1,1])for(let y=-sy+.6;y<sy;y+=2)
      rivet(sign*(sx-.45),y,z-.13);
    for(const y of [-sy+.5,sy-.5])for(let x=-sx+1;x<sx;x+=2)rivet(x,y,z-.13);
    if(entry.id==='small_shield')for(let i=0;i<8;i++) {
      const a=i*Math.PI/4;
      line('shield_sun',[Math.sin(a)*1.8,Math.cos(a)*1.8,z-.2],[Math.sin(a)*3,Math.cos(a)*3,z-.2],.25,'amber');
    }
    else {
      line('shield_tree',[0,-sy*.65,z],[0,sy*.64,z],.4,'amber');
      for(let i=0;i<3;i++)for(const sign of [-1,1])
        line('shield_branch',[0,-sy*.25+i*sy*.27,z],[sign*sx*(.6-i*.1),sy*.05+i*sy*.24,z],.25,'amber');
      for(const sign of [-1,1])line('shield_root',[0,-sy*.4,z],[sign*sx*.5,-sy*.65,z],.25,'amber');
      for(const x of [-sx+1,sx-1])line('shield_inner_border',[x,-sy+.8,z],[x,sy-.8,z],.18,'amber');
    }
    for(const sign of [-1,1])add('shield_armstrap',[sign*2-.35,-2,.4],[sign*2+.35,2,1.3],'leather_wrap');
  }
  model.cubes.push(...detail);
  const handle=original.find(c=>c.name==='handle');
  const shift=entry.group==='防具'?[0,0,(handle.from[2]+handle.to[2])/2]:
    ['crossbow','ballista'].includes(entry.id)?[0,.5,3.25]:entry.id==='sacred_seal'?[0,-2,0]:[0,0,0];
  for(const c of model.cubes) {
    c.pivot=(c.pivot||c.from).map((v,i)=>v-shift[i]);
    c.from=c.from.map((v,i)=>v-shift[i]);c.to=c.to.map((v,i)=>v-shift[i]);
  }
  for(const bone of model.bones)if(bone.name!=='root')bone.pivot=bone.pivot.map((v,i)=>v-shift[i]);
  model.detailLevel='refined cuboid prototype; tapered blades, fitted hardware, wraps and inlays';
  return entry;
}
