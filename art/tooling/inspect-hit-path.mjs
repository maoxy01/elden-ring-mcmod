import { Matrix4, Vector3 } from 'three';
import { margit } from './margit.mjs';
const rad = d => d * Math.PI / 180;
function pose(clip, tick) {
  const world = new Map();
  for (const b of margit.bones) {
    const parent = margit.bones.find(x => x.name === b.parent);
    const frames = clip.tracks[b.name] || [{ tick: 0, value: [0, 0, 0] }];
    let lo = frames[0], hi = frames.at(-1);
    for (const f of frames) { if (f.tick <= tick) lo = f; if (f.tick >= tick) { hi = f; break; } }
    const t = hi.tick === lo.tick ? 0 : Math.min(1, Math.max(0, (tick - lo.tick) / (hi.tick - lo.tick)));
    const r = b.rotation.map((n,i) => rad(n + lo.value[i] + (hi.value[i]-lo.value[i])*t));
    const m = parent ? world.get(parent.name).clone() : new Matrix4();
    m.multiply(new Matrix4().makeTranslation(...b.pivot.map((n,i)=>(n-(parent?.pivot[i]||0))/16)));
    m.multiply(new Matrix4().makeRotationX(r[0])).multiply(new Matrix4().makeRotationY(r[1])).multiply(new Matrix4().makeRotationZ(r[2]));
    world.set(b.name,m);
  }
  return world;
}
for (const clip of margit.animations.filter(c=>c.events.some(e=>e.type==='hit_start'))) {
  const start = clip.events.find(e=>e.type==='hit_start').tick, end=clip.events.find(e=>e.type==='hit_end').tick;
  console.log(clip.id,start,end);
  for(let tick=start;tick<end;tick++) {
    const world=pose(clip,tick), staff=clip.id.startsWith('staff');
    const socket=staff?'staff_socket':'spectral_socket';
    const pts=staff ? [[0,-19/16,0],[0,26/16,0]] : clip.id==='dagger_slash'?[[0,4.5/16,0],[0,18/16,0]]:[[0,23/16,0],[0,32/16,0]];
    console.log(tick,pts.map(p=>new Vector3(...p).applyMatrix4(world.get(socket)).toArray().map(n=>+n.toFixed(2))));
  }
}
