import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { deflateSync } from 'node:zlib';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { playerModel, equipment, commonClips, attackClips, presentation } from './player.mjs';
import { createAsset, gripLeftHand } from './player-scene.mjs';
import { effects, sounds } from './combat-effects.mjs';

const art = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..'), out = path.join(art, 'player');
await fs.mkdir(out, { recursive: true });
const writeJSON = (name, value) => fs.writeFile(path.join(out, name), JSON.stringify(value, null, 2) + '\n');
function png(size, pixel) {
  const raw = Buffer.alloc(size * (size * 4 + 1));
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    const i = y * (size * 4 + 1) + x * 4 + 1;
    const color = pixel(x, y); for (let c = 0; c < 4; c++) raw[i + c] = color[c] ?? 255;
  }
  const chunk = (name, data) => {
    const tag = Buffer.from(name), head = Buffer.alloc(4), tail = Buffer.alloc(4);
    head.writeUInt32BE(data.length); let crc = 0xffffffff;
    for (const b of Buffer.concat([tag, data])) { crc ^= b; for (let k = 0; k < 8; k++) crc = crc >>> 1 ^ ((crc & 1) ? 0xedb88320 : 0); }
    tail.writeUInt32BE((crc ^ 0xffffffff) >>> 0); return Buffer.concat([head, tag, data, tail]);
  };
  const header = Buffer.alloc(13); header.writeUInt32BE(size); header.writeUInt32BE(size, 4); header[8] = 8; header[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', header), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
const previewSkin = png(64, (x, y) => {
  let base = [194, 174, 155];
  if (y >= 16) base = [62, 102, 92];
  if (y >= 20 && y < 32 && x < 16 || y >= 52 && x < 32) base = [55, 60, 64];
  if (y >= 30 && y < 32 && x < 16 || y >= 62 && x < 32) base = [34, 38, 42];
  if (x >= 8 && x < 16 && y >= 8 && y < 16) {
    if (y < 10) base = [68, 69, 65];
    if (y === 11 && (x === 10 || x === 13)) base = [35, 45, 48];
    if (y === 14 && x >= 11 && x <= 13) base = [113, 85, 78];
  }
  const shade = ((Math.floor(x / 3) + Math.floor(y / 4)) % 3 - 1) * 4;
  return base.map(n => n + shade);
});
const palette = { steel: [193, 207, 209], steel_dark: [72, 91, 98], leather: [79, 66, 66], red: [142, 47, 60], amber: [225, 178, 62] };
const materials = Object.fromEntries(Object.entries(palette).map(([name, color], i) => [name, { uv: [(i % 4) * 16, Math.floor(i / 4) * 16, (i % 4 + 1) * 16, (Math.floor(i / 4) + 1) * 16], color }]));
const equipmentPNG = png(64, (x, y) => {
  const index = Math.floor(y / 16) * 4 + Math.floor(x / 16), base = Object.values(palette)[index] || [35, 40, 43];
  const shade = ((Math.floor(x / 3) * 3 + Math.floor(y / 4) * 5) % 5 - 2) * 4;
  return base.map(n => n + shade);
});
await fs.writeFile(path.join(out, 'player-preview.png'), previewSkin);
await fs.writeFile(path.join(out, 'equipment.png'), equipmentPNG);
await fs.mkdir(path.join(out, 'fx'), { recursive: true });
for (const [name, pixel] of Object.entries({
  spark: (x, y) => [255, 255, 255, (x >= 6 && x < 10 && y >= 3 && y < 13 || y >= 6 && y < 10 && x >= 3 && x < 13) ? 255 : 0],
  dust: (x, y) => [255, 255, 255, x >= 4 && x < 12 && y >= 4 && y < 12 ? 190 : 0],
  heal: (x, y) => [255, 255, 255, x >= 6 && x < 10 && y >= 4 && y < 12 ? 230 : 0]
})) await fs.writeFile(path.join(out, 'fx', `${name}.png`), png(16, pixel));
await writeJSON('combat.effects.json', effects);
await writeJSON('combat.sound-design.json', sounds);
for (const model of equipment) model.materials = materials;

const profiles = Object.fromEntries(['light_sword', 'great_sword'].map(profile => {
  const great = profile === 'great_sword';
  const attacks = attackClips(great);
  if (great) for (const c of attacks) for (const k of c.tracks.upper_arm_r) {
    k.value[2] = 45;
    if (c.id === 'light_2' && k.value[1] < 0) k.value[1] = 0;
  }
  const common = structuredClone(commonClips);
  if (great) for (const c of common) if (c.tracks.upper_arm_r && !['roll', 'drink', 'death'].includes(c.id)) for (const k of c.tracks.upper_arm_r) k.value[2] = 45;
  return [profile, [...common, ...attacks]];
}));
const visualOffsets = { schemaVersion: 1, units: 'model_units', affectsEntityMovement: false, clips: {} };
const classic = playerModel(false), slim = playerModel(true), poseSampler = createAsset(classic);
const variantGreatClips = {};
for (const model of [classic, slim]) {
  const player = createAsset(model), sword = createAsset(equipment.find(m => m.id.endsWith(':great_sword')));
  player.bones.get('sword_socket').add(sword.root);
  const clips = structuredClone(profiles.great_sword);
  for (const c of clips) {
    if (['roll', 'drink', 'death', 'parry'].includes(c.id) || c.id.startsWith('guard')) continue;
    const source = structuredClone(c);
    for (const name of ['upper_arm_l', 'forearm_l']) c.tracks[name] = [];
    for (let tick = 0; tick <= c.lengthTicks; tick++) {
      player.pose(source, tick); gripLeftHand(player, sword.root);
      for (const name of ['upper_arm_l', 'forearm_l']) {
        const euler = player.bones.get(name).rotation;
        const value = [euler.x, euler.y, euler.z].map(v => v * 180 / Math.PI);
        const previous = c.tracks[name].at(-1)?.value;
        if (previous) for (let i = 0; i < 3; i++) {
          while (value[i] - previous[i] > 180) value[i] -= 360;
          while (value[i] - previous[i] < -180) value[i] += 360;
        }
        c.tracks[name].push({ tick, value });
      }
    }
  }
  variantGreatClips[model.id.split(':')[1]] = clips;
}
for (const c of commonClips.filter(c => ['roll', 'death'].includes(c.id))) {
  const values = [];
  for (let tick = 0; tick <= c.lengthTicks; tick++) {
    poseSampler.pose(c, tick);
    const lift = -poseSampler.bounds().min.y * 16;
    values.push({ tick, value: [0, Math.round(lift * 10000) / 10000, 0] });
  }
  // Integer keys need a small clearance margin between rotation samples.
  const margins = values.map(() => 0);
  for (let i = 0; i < values.length - 1; i++) {
    let margin = 0;
    for (let step = 1; step < 20; step++) {
      const t = step / 20; poseSampler.pose(c, i + t);
      const offset = values[i].value[1] * (1 - t) + values[i + 1].value[1] * t;
      margin = Math.max(margin, -poseSampler.bounds().min.y * 16 - offset);
    }
    margins[i] = Math.max(margins[i], margin * 1.02 + 0.001);
    margins[i + 1] = Math.max(margins[i + 1], margin * 1.02 + 0.001);
  }
  for (let i = 0; i < values.length; i++) values[i].value[1] += margins[i];
  visualOffsets.clips[c.id] = { positionTracks: { root: values } };
}
await writeJSON('player.visual-offsets.json', visualOffsets);
await writeJSON('player.presentation.json', presentation);
for (const [variant, clips] of Object.entries(variantGreatClips)) await writeJSON(`${variant}.great_sword.animations.json`,
  { schemaVersion: 1, ticksPerSecond: 20, rotationOrder: 'XYZ', rotationMode: 'additive_degrees', clips });
await writeJSON('player.common.animations.json', { schemaVersion: 1, ticksPerSecond: 20, rotationOrder: 'XYZ', rotationMode: 'additive_degrees', clips: commonClips });
for (const [profile, clips] of Object.entries(profiles)) await writeJSON(`${profile === 'light_sword' ? 'straight_sword' : 'great_sword'}.animations.json`,
  { schemaVersion: 1, ticksPerSecond: 20, rotationOrder: 'XYZ', rotationMode: 'additive_degrees', clips });

const uuid = label => { const h = createHash('sha256').update(`player-assets/${label}`).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-a${h.slice(17, 20)}-${h.slice(20, 32)}`; };
function blockbench(model, clips = []) {
  const groupIDs = Object.fromEntries(model.bones.map(b => [b.name, uuid(`${model.id}/${b.name}`)]));
  const elementIDs = Object.fromEntries(model.cubes.map(c => [c.name, uuid(`${model.id}/cube/${c.name}`)]));
  const children = name => [...model.cubes.filter(c => c.bone === name).map(c => elementIDs[c.name]),
    ...model.bones.filter(b => b.parent === name).map(b => ({ uuid: groupIDs[b.name], isOpen: true, children: children(b.name) }))];
  return { meta: { format_version: '5.0', model_format: 'free', box_uv: false }, name: model.id.split(':')[1], model_identifier: model.id,
    resolution: { width: 64, height: 64 }, groups: model.bones.map(b => ({ name: b.name, uuid: groupIDs[b.name], origin: b.pivot, rotation: b.rotation, export: true, visibility: true })),
    elements: model.cubes.map(c => ({ name: c.name, type: 'cube', uuid: elementIDs[c.name], from: c.from, to: c.to, origin: c.pivot || c.from,
      rotation: c.rotation || [0, 0, 0], box_uv: false, autouv: 0, export: true, visibility: true,
      faces: Object.fromEntries(['north', 'east', 'south', 'west', 'up', 'down'].map(face => [face, { uv: model.skinFaces?.[c.name]?.[face] || model.materials[c.material].uv, texture: 0, rotation: 0 }])) })),
    outliner: model.bones.filter(b => b.parent === null).map(b => ({ uuid: groupIDs[b.name], isOpen: true, children: children(b.name) })),
    textures: [{ id: '0', uuid: uuid(`${model.id}/texture`), name: model.texture, relative_path: model.texture, mode: 'bitmap', render_mode: 'default',
      width: 64, height: 64, uv_width: 64, uv_height: 64, visible: true, saved: true,
      source: `data:image/png;base64,${(model.texture === 'equipment.png' ? equipmentPNG : previewSkin).toString('base64')}` }],
    animations: clips.map(c => ({ name: c.id, uuid: uuid(`${model.id}/${c.id}`), loop: c.loop ? 'loop' : 'once', length: c.lengthTicks / 20, snapping: 20,
      animators: Object.fromEntries([...new Set([...Object.keys(c.tracks), ...Object.keys(visualOffsets.clips[c.id]?.positionTracks || {})])].map(name => [groupIDs[name], {
        name, type: 'bone', keyframes: ['rotation', 'position'].flatMap(channel => {
          const frames = channel === 'rotation' ? c.tracks[name] : visualOffsets.clips[c.id]?.positionTracks[name];
          return (frames || []).map(k => ({ uuid: uuid(`${model.id}/${c.id}/${name}/${channel}/${k.tick}`), channel, time: k.tick / 20,
            interpolation: 'linear', data_points: [{ x: String(k.value[0]), y: String(k.value[1]), z: String(k.value[2]) }] }));
        })
      }])) })) };
}
const models = [classic, slim, ...equipment];
for (const model of models) {
  const name = model.id.split(':')[1], runtime = { ...model };
  delete runtime.skinFaces; delete runtime.overlayFaces; delete runtime.hitAxis;
  await writeJSON(`${name}.model.json`, runtime);
  await writeJSON(`${name}.bbmodel`, blockbench(model, name.startsWith('player') ? profiles.light_sword : []));
  if (model.skinFaces) {
    await writeJSON(`${name}.skin.json`, { schemaVersion: 1, textureSize: [64, 64], variant: name, faces: model.skinFaces,
      overlayFaces: model.overlayFaces, overlayInflateModelUnits: { head: 0.45, body: 0.225, limbs: 0.225 },
      internalJointFaces: ['arm_r_upper.down', 'arm_r_lower.up', 'arm_l_upper.down', 'arm_l_lower.up',
        'leg_r_upper.down', 'leg_r_lower.up', 'leg_l_upper.down', 'leg_l_lower.up'] });
    await writeJSON(`${name}.great_sword.bbmodel`, blockbench(model, variantGreatClips[name]));
  }
}
await writeJSON('equipment.hit-shapes.json', { schemaVersion: 1, unitsPerBlock: 16, space: 'equipment_root',
  weapons: Object.fromEntries(equipment.filter(m => m.hitAxis).map(m => [m.id.split(':')[1], m.hitAxis])) });

let checks = 0;
const check = (condition, message) => { assert(condition, message); checks++; };
for (const m of models) {
  const parents = new Set();
  for (const b of m.bones) { check(b.parent === null || parents.has(b.parent), `${m.id}: parent order`); check(!parents.has(b.name), 'duplicate bone'); parents.add(b.name); }
  for (const c of m.cubes) { check(parents.has(c.bone), 'cube bone'); check(c.from.every((v, i) => v < c.to[i]), 'positive volume'); }
}
for (const [profile, clips] of Object.entries(profiles)) {
  for (const c of clips) {
    check(!/execution|riposte|jump/.test(c.id), 'forbidden mechanic');
    check(c.events.every((e, i) => e.tick <= c.lengthTicks && (!i || e.tick >= c.events[i - 1].tick)), 'ordered events');
    for (const [name, frames] of Object.entries(c.tracks)) {
      check(classic.bones.some(b => b.name === name), 'unknown animated bone');
      check(frames.every((k, i) => k.value.length === 3 && k.value.every(Number.isFinite) && k.tick >= 0 && k.tick <= c.lengthTicks && (!i || k.tick > frames[i - 1].tick)), 'invalid keyframes');
    }
  }
  for (const id of ['light_1', 'light_2', 'heavy_release', 'charged_release']) {
    const c = clips.find(c => c.id === id), light = id.startsWith('light'), charged = id === 'charged_release';
    const expected = light ? (profile === 'light_sword' ? [22, 8, 12] : [30, 12, 17]) : charged ? [36, 16, 22] : [34, 15, 20];
    check(JSON.stringify([c.lengthTicks, c.events.find(e => e.type === 'hit_start').tick, c.events.find(e => e.type === 'hit_end').tick]) === JSON.stringify(expected), 'combat window mismatch');
  }
}
for (const c of commonClips.filter(c => ['roll', 'death'].includes(c.id))) for (let tick = 0; tick <= c.lengthTicks; tick += 0.05) {
  poseSampler.pose(c, tick, visualOffsets.clips[c.id].positionTracks);
  check(poseSampler.bounds().min.y >= -0.0001 && poseSampler.bounds().min.y < 0.125, 'floor clearance');
}
check(classic.bones.length === 17 && classic.cubes.length === 10, 'player complexity');
let maxGripError = 0;
for (const m of [classic, slim]) {
  const p = createAsset(m), sword = createAsset(equipment.find(m => m.id.endsWith(':great_sword')));
  p.bones.get('sword_socket').add(sword.root);
  for (const c of profiles.great_sword.filter(c => !['roll', 'drink', 'death', 'parry'].includes(c.id) && !c.id.startsWith('guard'))) {
    for (let tick = 0; tick <= c.lengthTicks; tick++) {
      p.pose(c, tick); const error = gripLeftHand(p, sword.root); maxGripError = Math.max(maxGripError, error);
      check(error < 0.05, `${m.id}/${c.id}/${tick}: hand misses grip ${error}`);
    }
  }
  for (const c of variantGreatClips[m.id.split(':')[1]].filter(c => !['roll', 'drink', 'death', 'parry'].includes(c.id) && !c.id.startsWith('guard'))) {
    for (let tick = 0; tick <= c.lengthTicks; tick += 0.5) {
      p.pose(c, tick); p.root.updateMatrixWorld(true);
      const error = p.bones.get('hand_l').getWorldPosition(p.root.position.clone()).distanceTo(sword.root.localToWorld(p.root.position.clone().set(0, -3 / 16, 0)));
      check(error < 0.05, 'baked grip misses between frames');
    }
  }
}
const bundle = await build({ entryPoints: [path.join(art, 'tooling/player-viewer.mjs')], bundle: true, write: false, format: 'iife', minify: true, target: 'es2022' });
const scene = JSON.stringify({ models, profiles, variantGreatClips, visualOffsets, presentation,
  textures: { skin: `data:image/png;base64,${previewSkin.toString('base64')}`, equipment: `data:image/png;base64,${equipmentPNG.toString('base64')}` } }).replaceAll('<', '\\u003c');
const template = await fs.readFile(path.join(art, 'tooling/player-viewer.html'), 'utf8');
await fs.writeFile(path.join(out, 'preview.html'), template.replace('/* SCENE_DATA */', `window.PLAYER_PACK=${scene};`).replace('/* VIEWER_BUNDLE */', bundle.outputFiles[0].text.replaceAll('</script', '<\\/script')));
await fs.copyFile(path.join(art, 'node_modules/three/LICENSE'), path.join(out, 'THREE-LICENSE.txt'));
const report = { status: 'passed', checks, playerBones: 17, playerCubes: 10, skinVariants: 2, equipment: equipment.map(m => m.id),
  clipsPerProfile: 16, maxGreatSwordGripErrorBlocks: maxGripError, javaIntegration: 'pending dsh', sounds: 'design specification only; no audio recordings included' };
await writeJSON('validation.json', report); console.log(JSON.stringify(report, null, 2));
