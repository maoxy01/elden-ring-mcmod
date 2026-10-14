// dsh 战技动画离线校验工具：把 EF biped 骨架 + 战技动画 JSON 做正向运动学，
// 渲染成 PNG 火柴人图，用于在不开游戏的情况下检查起手/挥砍/收招姿态是否合理。
//
// 依据（本机 EF 反编译源码 JsonAssetLoader.java / JointTransform.java / AnimationTransformEntry.java，已核实）：
//  - 骨架与动画的 16 元素数组都按"列主序装载 + transpose"处理（JsonAssetLoader:503-504、骨架 :503-508），
//    结果等价于直接按行主序读、平移落在第 4 列（下标 3/7/11）。
//  - 根关节额外左乘 BLENDER_TO_MINECRAFT_COORD（绕 X 轴 -90°），把 Blender Z-up 转成 MC Y-up
//    （JsonAssetLoader:506-508 骨架、:749-751 动画）。
//  - ATTRIBUTES 格式用 {"loc":[x,y,z],"rot":[w,x,y,z],"sca":[x,y,z]}，
//    轴分量装载时取负：quat = (-rot[1], -rot[2], -rot[3], rot[0])；不乘 invLocalTransform、不做根修正，
//    即数值是"绑定姿态之上的局部增量"，故 M = M_parent * bindLocal * trs(loc,q,sca)。
//  - MATRIX 格式会左乘 invLocalTransform（JsonAssetLoader:753），与 bindLocal 相消，
//    即数值是"父空间的绝对姿态"，故 M = M_parent * (root? B2M : I) * M_json。
//  - JointTransform.toMatrix = T * R * S；AnimationTransformEntry 按 PARENT -> JOINT_LOCAL ->
//    ANIMATION_TRANSFORM 顺序相乘。
//
// 用法：
//   node fkcheck.mjs <armature.json> <anim.json> <out.png> [--times a,b,c] [--cols n] [--scale pxPerBlock]
import { readFileSync, writeFileSync } from 'node:fs';
import { deflateSync } from 'node:zlib';

// ---------- 4x4 行主序矩阵（平移在第 4 列） ----------
const I4 = () => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];

function mul(a, b) {
  const o = new Array(16).fill(0);
  for (let r = 0; r < 4; r++) {
    for (let c = 0; c < 4; c++) {
      let s = 0;
      for (let k = 0; k < 4; k++) s += a[r * 4 + k] * b[k * 4 + c];
      o[r * 4 + c] = s;
    }
  }
  return o;
}

function trs(t, q, s) {
  const [x, y, z, w] = q;
  const xx = x * x, yy = y * y, zz = z * z;
  const xy = x * y, xz = x * z, yz = y * z, wx = w * x, wy = w * y, wz = w * z;
  const r = [
    1 - 2 * (yy + zz), 2 * (xy - wz), 2 * (xz + wy), 0,
    2 * (xy + wz), 1 - 2 * (xx + zz), 2 * (yz - wx), 0,
    2 * (xz - wy), 2 * (yz + wx), 1 - 2 * (xx + yy), 0,
    0, 0, 0, 1,
  ];
  // 列缩放
  for (let i = 0; i < 3; i++) { r[i] *= s[0]; r[4 + i] *= s[1]; r[8 + i] *= s[2]; }
  r[3] = t[0]; r[7] = t[1]; r[11] = t[2];
  return r;
}

function xform(m, v) {
  return [
    m[0] * v[0] + m[1] * v[1] + m[2] * v[2] + m[3],
    m[4] * v[0] + m[5] * v[1] + m[6] * v[2] + m[7],
    m[8] * v[0] + m[9] * v[1] + m[10] * v[2] + m[11],
  ];
}

function normalizeQuat(q) {
  const n = Math.hypot(q[0], q[1], q[2], q[3]) || 1;
  return q.map((v) => v / n);
}

function slerp(a, b, t) {
  let d = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
  let bb = b;
  if (d < 0) { bb = b.map((v) => -v); d = -d; }
  if (d > 0.9995) return normalizeQuat(a.map((v, i) => v + (bb[i] - v) * t));
  const th0 = Math.acos(Math.max(-1, Math.min(1, d)));
  const th = th0 * t;
  const s0 = Math.sin(th0 - th) / Math.sin(th0);
  const s1 = Math.sin(th) / Math.sin(th0);
  return normalizeQuat(a.map((v, i) => v * s0 + bb[i] * s1));
}

// ---------- 骨架 ----------
// 骨架 JSON 的 16 个数：按行主序直接读（等价于 EF 的"列主序装载 + transpose"），平移在下标 3/7/11。
function flat(a) {
  return new Array(16).fill(0).map((_, i) => a[i]);
}

// Blender(Z-up) -> Minecraft(Y-up)：绕 X 轴 -90°。
const B2M = [1, 0, 0, 0, 0, 0, 1, 0, 0, -1, 0, 0, 0, 0, 0, 1];

function loadArmature(path) {
  const json = JSON.parse(readFileSync(path, 'utf8'));
  const byName = new Map();
  const order = [];
  const walk = (node, parent) => {
    let local = node.transform ? flat(node.transform) : I4();
    if (!parent) local = mul(B2M, local); // 根关节做 Blender->Minecraft 修正
    byName.set(node.name, { name: node.name, parent, local, children: [] });
    order.push(node.name);
    if (parent) byName.get(parent).children.push(node.name);
    for (const c of node.children ?? []) walk(c, node.name);
  };
  for (const j of json.armature.hierarchy) walk(j, null);
  return { byName, order };
}

// ---------- 动画 ----------
function loadTrack(path) {
  const json = JSON.parse(readFileSync(path, 'utf8'));
  const isAttr = String(json.format ?? 'matrix').toLowerCase() === 'attributes';
  const tracks = new Map();
  let duration = 0;
  let first = true;
  for (const entry of json.animation) {
    const rootCorrection = first && !isAttr; // MATRIX 格式只对数组里第一条做根修正
    first = false;
    const times = entry.time;
    const keys = times.map((t, i) => {
      const raw = entry.transform[i];
      if (isAttr) {
        const q = normalizeQuat([-raw.rot[1], -raw.rot[2], -raw.rot[3], raw.rot[0]]);
        return { t, loc: raw.loc, q, sca: raw.sca, kind: 'delta' };
      }
      // MATRIX：数值是父空间绝对姿态；bindLocal 与之相消
      let m = flat(raw);
      if (rootCorrection) m = mul(B2M, m);
      return { t, mat: m, kind: 'absolute' };
    });
    keys.sort((a, b) => a.t - b.t);
    tracks.set(entry.name, keys);
    duration = Math.max(duration, keys.length ? keys[keys.length - 1].t : 0);
  }
  return { format: isAttr ? 'attributes' : 'matrix', tracks, duration };
}

function quatFromMatrix(m) {
  const m00 = m[0], m01 = m[1], m02 = m[2];
  const m10 = m[4], m11 = m[5], m12 = m[6];
  const m20 = m[8], m21 = m[9], m22 = m[10];
  const tr = m00 + m11 + m22;
  let q;
  if (tr > 0) {
    const s = Math.sqrt(tr + 1) * 2;
    q = [(m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s, 0.25 * s];
  } else if (m00 > m11 && m00 > m22) {
    const s = Math.sqrt(1 + m00 - m11 - m22) * 2;
    q = [0.25 * s, (m01 + m10) / s, (m02 + m20) / s, (m21 - m12) / s];
  } else if (m11 > m22) {
    const s = Math.sqrt(1 + m11 - m00 - m22) * 2;
    q = [(m01 + m10) / s, 0.25 * s, (m12 + m21) / s, (m02 - m20) / s];
  } else {
    const s = Math.sqrt(1 + m22 - m00 - m11) * 2;
    q = [(m02 + m20) / s, (m12 + m21) / s, 0.25 * s, (m10 - m01) / s];
  }
  return normalizeQuat(q);
}

// 在 MATRIX（绝对）与 ATTRIBUTES（增量）两种关键帧之间统一表示：都插值成 4x4。
function keyMatrix(k) {
  return k.kind === 'absolute' ? k.mat : trs(k.loc, k.q, k.sca);
}

function sampleMatrix(keys, time) {
  if (!keys || keys.length === 0) return null;
  if (time <= keys[0].t) return keyMatrix(keys[0]);
  const last = keys[keys.length - 1];
  if (time >= last.t) return keyMatrix(last);
  let i = 0;
  while (i < keys.length - 1 && keys[i + 1].t <= time) i++;
  const a = keys[i], b = keys[i + 1];
  const f = (time - a.t) / Math.max(1e-6, b.t - a.t);
  if (a.kind === 'delta' && b.kind === 'delta') {
    return trs(
      a.loc.map((v, k) => v + (b.loc[k] - v) * f),
      slerp(a.q, b.q, f),
      a.sca.map((v, k) => v + (b.sca[k] - v) * f),
    );
  }
  // 位置/缩放线性插值，旋转走 slerp，再重组（与 JointTransform.interpolate 同构）
  const ma = keyMatrix(a), mb = keyMatrix(b);
  const la = [ma[3], ma[7], ma[11]], lb = [mb[3], mb[7], mb[11]];
  const qa = quatFromMatrix(ma), qb = quatFromMatrix(mb);
  const sa = colLen(ma), sb = colLen(mb);
  return trs(
    la.map((v, k) => v + (lb[k] - v) * f),
    slerp(qa, qb, f),
    sa.map((v, k) => v + (sb[k] - v) * f),
  );
}

function colLen(m) {
  return [
    Math.hypot(m[0], m[4], m[8]),
    Math.hypot(m[1], m[5], m[9]),
    Math.hypot(m[2], m[6], m[10]),
  ];
}

function poseAt(arm, anim, time) {
  const absolute = anim.format === 'matrix';
  const world = new Map();
  const compute = (name) => {
    if (world.has(name)) return world.get(name);
    const j = arm.byName.get(name);
    const parentM = j.parent ? compute(j.parent) : I4();
    const animM = sampleMatrix(anim.tracks.get(name), time);
    const m = animM === null
      ? mul(parentM, j.local)                        // 该关节未出现在动画里 -> 保持绑定姿态
      : (absolute
        ? mul(parentM, animM)                        // MATRIX：父空间绝对姿态，bindLocal 相消
        : mul(mul(parentM, j.local), animM));        // ATTRIBUTES：绑定姿态之上的局部增量
    world.set(name, m);
    return m;
  };
  for (const n of arm.order) compute(n);
  return { world };
}

// ---------- PNG ----------
function crc32(buf) {
  let c, table = crc32.table;
  if (!table) {
    table = crc32.table = new Int32Array(256);
    for (let n = 0; n < 256; n++) {
      c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      table[n] = c;
    }
  }
  let crc = -1;
  for (let i = 0; i < buf.length; i++) crc = (crc >>> 8) ^ table[(crc ^ buf[i]) & 0xff];
  return (crc ^ -1) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function writePng(path, w, h, rgb) {
  const raw = Buffer.alloc((w * 3 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 3 + 1)] = 0;
    rgb.copy(raw, y * (w * 3 + 1) + 1, y * w * 3, (y + 1) * w * 3);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 2; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  writeFileSync(path, Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0)),
  ]));
}

class Canvas {
  constructor(w, h) { this.w = w; this.h = h; this.buf = Buffer.alloc(w * h * 3, 16); }
  px(x, y, r, g, b) {
    if (x < 0 || y < 0 || x >= this.w || y >= this.h) return;
    const i = (y * this.w + x) * 3;
    this.buf[i] = r; this.buf[i + 1] = g; this.buf[i + 2] = b;
  }
  line(x0, y0, x1, y1, r, g, b, thick = 1) {
    x0 = Math.round(x0); y0 = Math.round(y0); x1 = Math.round(x1); y1 = Math.round(y1);
    const dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
    const sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
    let err = dx - dy;
    for (;;) {
      for (let oy = 0; oy < thick; oy++) for (let ox = 0; ox < thick; ox++) this.px(x0 + ox, y0 + oy, r, g, b);
      if (x0 === x1 && y0 === y1) break;
      const e2 = 2 * err;
      if (e2 > -dy) { err -= dy; x0 += sx; }
      if (e2 < dx) { err += dx; y0 += sy; }
    }
  }
  text(x, y, s, r, g, b) {
    // 5x7 位图字体（仅需数字/字母/少量符号）
    for (const ch of s) {
      const glyph = FONT[ch] ?? FONT['?'];
      for (let row = 0; row < 7; row++) {
        for (let col = 0; col < 5; col++) {
          if (glyph[row] & (1 << (4 - col))) this.px(x + col, y + row, r, g, b);
        }
      }
      x += 6;
    }
  }
}

const FONT = (() => {
  const g = {};
  const def = (ch, rows) => (g[ch] = rows.map((r) => parseInt(r, 2)));
  def('0', ['01110', '10001', '10011', '10101', '11001', '10001', '01110']);
  def('1', ['00100', '01100', '00100', '00100', '00100', '00100', '01110']);
  def('2', ['01110', '10001', '00001', '00010', '00100', '01000', '11111']);
  def('3', ['11111', '00010', '00100', '00010', '00001', '10001', '01110']);
  def('4', ['00010', '00110', '01010', '10010', '11111', '00010', '00010']);
  def('5', ['11111', '10000', '11110', '00001', '00001', '10001', '01110']);
  def('6', ['00110', '01000', '10000', '11110', '10001', '10001', '01110']);
  def('7', ['11111', '00001', '00010', '00100', '01000', '01000', '01000']);
  def('8', ['01110', '10001', '10001', '01110', '10001', '10001', '01110']);
  def('9', ['01110', '10001', '10001', '01111', '00001', '00010', '01100']);
  def('.', ['00000', '00000', '00000', '00000', '00000', '01100', '01100']);
  def(':', ['00000', '01100', '01100', '00000', '01100', '01100', '00000']);
  def('s', ['00000', '00000', '01111', '10000', '01110', '00001', '11110']);
  def('?', ['01110', '10001', '00001', '00110', '00100', '00000', '00100']);
  return g;
})();

// ---------- 渲染 ----------
const ARM_PATH = process.argv[2];
const ANIM_PATH = process.argv[3];
const OUT = process.argv[4];
const argOf = (name, dflt) => {
  const i = process.argv.indexOf(name);
  return i >= 0 ? process.argv[i + 1] : dflt;
};

const arm = loadArmature(ARM_PATH);
const anim = loadTrack(ANIM_PATH);
const cols = Number(argOf('--cols', 6));
const pxPerBlock = Number(argOf('--scale', 90));
const explicit = argOf('--times', null);
const sampleCount = Number(argOf('--samples', 12));
const times = explicit
  ? explicit.split(',').map(Number)
  : Array.from({ length: sampleCount }, (_, i) => (anim.duration * i) / (sampleCount - 1));

const poseJson = argOf('--pose-json', null);
if (poseJson) {
  const keyTimes = [...new Set([...anim.tracks.values()].flatMap(keys => keys.map(key => key.t)))].sort((a, b) => a - b);
  writeFileSync(poseJson, JSON.stringify({
    order: arm.order,
    times: keyTimes,
    frames: keyTimes.map(time => Object.fromEntries(poseAt(arm, anim, time).world)),
  }));
}

const cellW = Math.round(2.6 * pxPerBlock);
const cellH = Math.round(3.2 * pxPerBlock);
const rows = Math.ceil(times.length / cols);
const canvas = new Canvas(cellW * cols, cellH * rows);

if (process.argv.includes('--trace')) {
  const f = (v) => v.map((x) => x.toFixed(3).padStart(7)).join(' ');
  console.log('时间   Tool_R 世界位置(x,y,z)       刃尖(x,y,z)  (前=-Z 右=+X 上=+Y)');
  for (const t of times) {
    const pose = poseAt(arm, anim, t);
    const m = pose.world.get('Tool_R');
    if (!m) continue;
    const base = xform(m, [0, 0, 0]);
    const tip = xform(m, [0, 1.6, 0]);
    console.log(`${t.toFixed(2)}   ${f(base)}   ${f(tip)}`);
  }
}

const COLOR = {
  bone: [90, 190, 255],
  tool: [255, 190, 60],
  root: [255, 80, 80],
};

const BONES = [
  ['Root', 'Torso'], ['Torso', 'Chest'], ['Chest', 'Head'],
  ['Chest', 'Shoulder_R'], ['Shoulder_R', 'Arm_R'], ['Arm_R', 'Elbow_R'],
  ['Elbow_R', 'Hand_R'], ['Hand_R', 'Tool_R'],
  ['Chest', 'Shoulder_L'], ['Shoulder_L', 'Arm_L'], ['Arm_L', 'Elbow_L'],
  ['Elbow_L', 'Hand_L'], ['Hand_L', 'Tool_L'],
  ['Root', 'Thigh_R'], ['Thigh_R', 'Knee_R'], ['Knee_R', 'Leg_R'],
  ['Root', 'Thigh_L'], ['Thigh_L', 'Knee_L'], ['Knee_L', 'Leg_L'],
];

// 世界坐标 -> 单元内屏幕坐标：3/4 视角（X 右，Y 上，Z 略向观察者）
const yaw = (Number(argOf('--yaw', 35)) * Math.PI) / 180;
function project(p, ox, oy) {
  const sx = p[0] * Math.cos(yaw) + p[2] * Math.sin(yaw);
  const sy = p[1];
  return [ox + cellW / 2 + sx * pxPerBlock, oy + cellH - 0.25 * pxPerBlock - sy * pxPerBlock];
}

times.forEach((time, idx) => {
  const col = idx % cols, rowI = Math.floor(idx / cols);
  const ox = col * cellW, oy = rowI * cellH;
  // 地面参考线
  canvas.line(ox + 6, oy + cellH - 0.25 * pxPerBlock, ox + cellW - 6, oy + cellH - 0.25 * pxPerBlock, 60, 60, 60, 1);
  const pose = poseAt(arm, anim, time);
  const P = (n) => project(xform(pose.world.get(n), [0, 0, 0]), ox, oy);
  for (const [a, b] of BONES) {
    if (!pose.world.has(a) || !pose.world.has(b)) continue;
    const pa = P(a), pb = P(b);
    canvas.line(pa[0], pa[1], pb[0], pb[1], COLOR.bone[0], COLOR.bone[1], COLOR.bone[2], 2);
  }
  // 武器插槽：沿 Tool_R 局部 +Y 画一段代表武器方向
  if (pose.world.has('Tool_R')) {
    const m = pose.world.get('Tool_R');
    const base = xform(m, [0, 0, 0]);
    const tip = xform(m, [0, 1.6, 0]);
    const pb0 = project(base, ox, oy), pb1 = project(tip, ox, oy);
    canvas.line(pb0[0], pb0[1], pb1[0], pb1[1], COLOR.tool[0], COLOR.tool[1], COLOR.tool[2], 2);
  }
  const rp = P('Root');
  canvas.line(rp[0] - 3, rp[1], rp[0] + 3, rp[1], COLOR.root[0], COLOR.root[1], COLOR.root[2], 1);
  canvas.line(rp[0], rp[1] - 3, rp[0], rp[1] + 3, COLOR.root[0], COLOR.root[1], COLOR.root[2], 1);
  if (pose.world.has('Root')) {
    // 世界轴指示：+X 红、+Y 绿、+Z 蓝（长 0.6 格），用于确认模型朝向
    const m = pose.world.get('Root');
    const o = xform(m, [0, 0, 0]);
    for (const [vec, col] of [[[0.6, 0, 0], [255, 60, 60]], [[0, 0.6, 0], [60, 255, 60]], [[0, 0, 0.6], [60, 120, 255]]]) {
      const a = project(o, ox, oy), b = project(xform(m, vec), ox, oy);
      canvas.line(a[0], a[1], b[0], b[1], col[0], col[1], col[2], 1);
    }
  }
  canvas.text(ox + 6, oy + 6, `${time.toFixed(2)}s`, 230, 230, 230);
});

writePng(OUT, canvas.w, canvas.h, canvas.buf);
console.log(`format=${anim.format} duration=${anim.duration.toFixed(3)}s joints_in_anim=${anim.tracks.size} armature_joints=${arm.order.length}`);
console.log(`samples=${times.map((t) => t.toFixed(2)).join(',')}`);
console.log(`wrote ${OUT} (${canvas.w}x${canvas.h})`);
