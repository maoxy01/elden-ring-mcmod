import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { deflateSync } from 'node:zlib';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { margit, spectralWeapons } from './margit.mjs';

const artDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const outDir = path.join(artDir, 'margit');
await fs.mkdir(outDir, { recursive: true });
const uuid = (label) => {
  const hash = createHash('sha256').update(label).digest('hex');
  return `${hash.slice(0, 8)}-${hash.slice(8, 12)}-4${hash.slice(13, 16)}-a${hash.slice(17, 20)}-${hash.slice(20, 32)}`;
};

const colors = {
  skin: [161, 154, 142], cloth: [103, 107, 69], cloth_dark: [58, 62, 45],
  horn: [197, 177, 143], horn_dark: [98, 90, 73], beard: [178, 176, 164],
  tail: [105, 96, 81], tail_tip: [139, 91, 70], wood: [101, 72, 46],
  gold: [233, 187, 67], face: [161, 154, 142]
};
const materials = {};
const pixels = Buffer.alloc(128 * 128 * 4);
for (let offset = 0; offset < pixels.length; offset += 4) {
  pixels[offset] = 31; pixels[offset + 1] = 34; pixels[offset + 2] = 33; pixels[offset + 3] = 255;
}
const put = (x, y, rgb) => {
  const offset = (y * 128 + x) * 4;
  for (let i = 0; i < 3; i++) pixels[offset + i] = Math.max(0, Math.min(255, rgb[i]));
  pixels[offset + 3] = 255;
};
for (const [index, [name, base]] of Object.entries(colors).entries()) {
  const tileX = (index % 4) * 32;
  const tileY = Math.floor(index / 4) * 32;
  materials[name] = { uv: [tileX, tileY, tileX + 32, tileY + 32], color: base, emissive: name === 'gold' };
  for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
    const px = Math.floor(x / 2), py = Math.floor(y / 2);
    const coarse = ((Math.floor(px / 3) * 17 + Math.floor(py / 4) * 13 + index * 7) % 7) - 3;
    let shade = coarse * 3;
    if (name === 'wood') shade = ((Math.floor(px / 3) * 7) % 5 - 2) * 5 + (py % 7 === 0 ? -4 : 0);
    if (name === 'gold') shade = (px < 2 || py < 2 || px > 13 || py > 13) ? 19 : coarse * 3;
    put(tileX + x, tileY + y, base.map(v => v + shade));
  }
}
const face = materials.face.uv;
const faceRect = (x, y, width, height, color) => {
  for (let dy = y * 2; dy < (y + height) * 2; dy++) for (let dx = x * 2; dx < (x + width) * 2; dx++) put(face[0] + dx, face[1] + dy, color);
};
faceRect(2, 5, 4, 1, [69, 65, 57]);
faceRect(10, 5, 4, 1, [69, 65, 57]);
faceRect(3, 6, 2, 1, [202, 194, 161]);
faceRect(11, 6, 2, 1, [202, 194, 161]);
faceRect(6, 9, 4, 2, [128, 120, 108]);
faceRect(5, 12, 6, 1, [77, 73, 65]);
faceRect(4, 13, 8, 3, [177, 174, 162]);

function crc32(buffer) {
  let crc = 0xffffffff;
  for (const byte of buffer) {
    crc ^= byte;
    for (let i = 0; i < 8; i++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
  }
  return (crc ^ 0xffffffff) >>> 0;
}
function pngChunk(type, data) {
  const tag = Buffer.from(type);
  const length = Buffer.alloc(4); length.writeUInt32BE(data.length);
  const checksum = Buffer.alloc(4); checksum.writeUInt32BE(crc32(Buffer.concat([tag, data])));
  return Buffer.concat([length, tag, data, checksum]);
}
const ihdr = Buffer.alloc(13);
ihdr.writeUInt32BE(128, 0); ihdr.writeUInt32BE(128, 4); ihdr[8] = 8; ihdr[9] = 6;
const scanlines = Buffer.alloc(128 * (128 * 4 + 1));
for (let y = 0; y < 128; y++) pixels.copy(scanlines, y * 513 + 1, y * 512, (y + 1) * 512);
const png = Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), pngChunk('IHDR', ihdr), pngChunk('IDAT', deflateSync(scanlines)), pngChunk('IEND', Buffer.alloc(0))]);
await fs.writeFile(path.join(outDir, 'margit.png'), png);

function validate(model) {
  const names = new Set(model.bones.map(b => b.name));
  assert.equal(names.size, model.bones.length, 'Duplicate bones');
  const roots = model.bones.filter(b => b.parent === null);
  assert.equal(roots.length, 1, 'Exactly one root is required');
  for (const b of model.bones) {
    assert(!b.parent || names.has(b.parent), `Missing parent for ${b.name}`);
    assert.equal(b.pivot.length, 3);
    const seen = new Set([b.name]);
    let parent = b.parent;
    while (parent) {
      assert(!seen.has(parent), `Bone cycle at ${b.name}`);
      seen.add(parent); parent = model.bones.find(item => item.name === parent).parent;
    }
  }
  for (const c of model.cubes) {
    assert(names.has(c.bone), `Missing owner for ${c.name}`);
    assert(materials[c.material], `Missing material for ${c.name}`);
    assert(c.from.every((v, i) => Number.isFinite(v) && v < c.to[i]), `Invalid bounds for ${c.name}`);
  }
  for (const animation of model.animations) {
    for (const [name, keys] of Object.entries(animation.tracks)) {
      assert(names.has(name), `Animation references missing bone ${name}`);
      assert(keys.every((k, i) => k.tick >= 0 && k.tick <= animation.lengthTicks && (!i || k.tick > keys[i - 1].tick)));
    }
    assert(animation.events.every(event => event.tick >= 0 && event.tick <= animation.lengthTicks));
  }
}

function blockbench(model) {
  const boneIDs = Object.fromEntries(model.bones.map(b => [b.name, uuid(`${model.id}/bone/${b.name}`)]));
  const cubeIDs = Object.fromEntries(model.cubes.map(c => [c.name, uuid(`${model.id}/cube/${c.name}`)]));
  const groups = model.bones.map(b => ({ name: b.name, origin: b.pivot, rotation: b.rotation, uuid: boneIDs[b.name], export: true, visibility: true, isOpen: true }));
  const children = (name) => [
    ...model.cubes.filter(c => c.bone === name).map(c => cubeIDs[c.name]),
    ...model.bones.filter(b => b.parent === name).map(b => ({ uuid: boneIDs[b.name], isOpen: true, children: children(b.name) }))
  ];
  const elements = model.cubes.map(c => ({
    name: c.name, type: 'cube', uuid: cubeIDs[c.name], from: c.from, to: c.to,
    origin: c.pivot || c.from, rotation: c.rotation || [0, 0, 0], inflate: 0,
    autouv: 0, color: 0, box_uv: false, export: true, visibility: true,
    faces: Object.fromEntries(['north', 'east', 'south', 'west', 'up', 'down'].map(direction => [direction, {
      uv: materials[model.id === 'eldencombat:margit' && c.name === 'head' && direction === 'north' ? 'face' : c.material].uv,
      texture: 0, rotation: 0
    }]))
  }));
  return {
    meta: { format_version: '5.0', model_format: 'free', box_uv: false },
    name: model.id.split(':')[1], model_identifier: model.id,
    resolution: { width: 128, height: 128 }, elements, groups,
    outliner: model.bones.filter(b => b.parent === null).map(b => ({ uuid: boneIDs[b.name], isOpen: true, children: children(b.name) })),
    textures: [{ name: 'margit.png', id: '0', uuid: uuid('eldencombat/margit/texture'),
      relative_path: 'margit.png', mode: 'bitmap', render_mode: 'default',
      particle: false, visible: true, saved: true, width: 128, height: 128,
      uv_width: 128, uv_height: 128, source: `data:image/png;base64,${png.toString('base64')}` }],
    animations: model.animations.map(a => ({
      uuid: uuid(`${model.id}/animation/${a.id}`), name: a.id, loop: a.loop ? 'loop' : 'once',
      override: false, length: a.lengthTicks / 20, snapping: 20,
      animators: Object.fromEntries(Object.entries(a.tracks).map(([name, keys]) => [boneIDs[name], {
        name, type: 'bone', keyframes: keys.map(k => ({
          uuid: uuid(`${a.id}/${name}/${k.tick}`), channel: 'rotation', time: k.tick / 20,
          color: -1, interpolation: 'linear', data_points: [{ x: String(k.value[0]), y: String(k.value[1]), z: String(k.value[2]) }]
        }))
      }]))
    }))
  };
}

const pack = [margit, ...spectralWeapons];
for (const model of pack) {
  validate(model);
  const name = model.id.split(':')[1];
  const runtime = { ...model, materials, rotationOrder: 'XYZ', animationRotation: 'additive_degrees' };
  delete runtime.animations;
  await fs.writeFile(path.join(outDir, `${name}.model.json`), JSON.stringify(runtime, null, 2) + '\n');
  await fs.writeFile(path.join(outDir, `${name}.bbmodel`), JSON.stringify(blockbench(model), null, 2) + '\n');
}
assert.deepEqual(margit.bones.filter(b => b.name.startsWith('tail_')).map(b => b.name), ['tail_01', 'tail_02', 'tail_03']);
assert.equal(margit.cubes.filter(c => c.bone.startsWith('tail_')).length, 3);
assert.equal(margit.bones.find(b => b.name === 'tail_02').parent, 'tail_01');
assert.equal(margit.bones.find(b => b.name === 'tail_03').parent, 'tail_02');
await fs.writeFile(path.join(outDir, 'margit.animations.json'), JSON.stringify({ schemaVersion: 1, ticksPerSecond: 20, rotationOrder: 'XYZ', rotationMode: 'additive_degrees', clips: margit.animations }, null, 2) + '\n');

const bundle = await build({ entryPoints: [path.join(artDir, 'tooling/viewer.mjs')], bundle: true, write: false, format: 'iife', minify: true, target: 'es2022' });
const script = bundle.outputFiles[0].text.replaceAll('</script', '<\\/script');
const template = await fs.readFile(path.join(artDir, 'tooling/viewer.html'), 'utf8');
const scene = JSON.stringify({ models: pack.map(m => ({ ...m, materials })), texture: `data:image/png;base64,${png.toString('base64')}` }).replaceAll('<', '\\u003c');
await fs.writeFile(path.join(outDir, 'preview.html'), template.replace('/* SCENE_DATA */', `window.MODEL_PACK=${scene};`).replace('/* VIEWER_BUNDLE */', script));

const sourceConcept = process.env.MARGIT_APPROVED_CONCEPT || process.argv[2];
if (sourceConcept) await fs.copyFile(sourceConcept, path.join(outDir, 'approved-concept-01C.png'));
await fs.copyFile(path.join(artDir, 'node_modules/three/LICENSE'), path.join(outDir, 'THREE-LICENSE.txt'));
const report = { status: 'passed', cubes: margit.cubes.length, bones: margit.bones.length, tailBones: 3, tailCubes: 3, textures: '128x128 RGBA', clips: margit.animations.map(a => a.id), runtimeIntegration: 'shared server/client skeleton; Epic Fight player combat' };
await fs.writeFile(path.join(outDir, 'validation.json'), JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report, null, 2));
