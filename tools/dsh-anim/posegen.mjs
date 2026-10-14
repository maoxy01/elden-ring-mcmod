// dsh 战技动画生成器：把一个"世界轴姿态规格"（JSON）编译成 EF 玩家动画资源
// （assets/eldencombat/animmodels/animations/<id>.json，attributes 格式）。
//
// 为什么不用手工写矩阵：EF 的 MATRIX 格式是导出的绝对姿态矩阵，人手写不可维护；
// ATTRIBUTES 格式是"绑定姿态之上的局部增量"（loc/rot/sca），可以按关节姿态来写。
//
// 坐标约定（已由 EF 源码反推核实，见 fkcheck.mjs 头部说明）：
//   模型空间 前 = -Z，右 = +X，上 = +Y；工具槽 Tool_R 在 +X（右手）。
//   规格里的角度一律是"yaw=0 时的世界轴角度"，生成器再用关节的绑定世界旋转
//   W 做共轭转换：local_delta = Wᵀ · R_world · W，使作者只需考虑世界方向。
//   欧拉顺序：R = Rx · Ry · Rz（先绕 Z 外展/抬起，再绕 Y 水平横扫，最后绕 X 俯仰）。
//   这个顺序是刻意选的：手臂在绑定姿态沿 -Y 垂下，若先做 Y 旋转等于绕自身轴空转，
//   必须先把手臂外展（Z）抬离竖直轴，绕 Y 的横扫才真正产生水平位移。
//
// 用法: node posegen.mjs <spec.json> <out.json>
import { readFileSync, writeFileSync } from 'node:fs';

const ARMATURE = new URL('../../build/ef-ref/animmodels_entity_biped.json', import.meta.url);

// ---------- 线性代数 ----------
const I3 = [1, 0, 0, 0, 1, 0, 0, 0, 1];

function m3mul(a, b) {
  const o = new Array(9).fill(0);
  for (let r = 0; r < 3; r++) for (let c = 0; c < 3; c++) {
    let s = 0;
    for (let k = 0; k < 3; k++) s += a[r * 3 + k] * b[k * 3 + c];
    o[r * 3 + c] = s;
  }
  return o;
}
function m3T(a) {
  return [a[0], a[3], a[6], a[1], a[4], a[7], a[2], a[5], a[8]];
}
function m3v(a, v) {
  return [
    a[0] * v[0] + a[1] * v[1] + a[2] * v[2],
    a[3] * v[0] + a[4] * v[1] + a[5] * v[2],
    a[6] * v[0] + a[7] * v[1] + a[8] * v[2],
  ];
}
const d2r = (d) => (d * Math.PI) / 180;
function rx(t) { const c = Math.cos(t), s = Math.sin(t); return [1, 0, 0, 0, c, -s, 0, s, c]; }
function ry(t) { const c = Math.cos(t), s = Math.sin(t); return [c, 0, s, 0, 1, 0, -s, 0, c]; }
function rz(t) { const c = Math.cos(t), s = Math.sin(t); return [c, -s, 0, s, c, 0, 0, 0, 1]; }

// R = Rx · Ry · Rz
function eulerToM3(degX, degY, degZ) {
  return m3mul(rx(d2r(degX)), m3mul(ry(d2r(degY)), rz(d2r(degZ))));
}

function m3ToQuat(m) {
  const [m00, m01, m02, m10, m11, m12, m20, m21, m22] = m;
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
  const n = Math.hypot(...q) || 1;
  return q.map((v) => v / n);
}

// ---------- 骨架绑定旋转 ----------
const B2M = [1, 0, 0, 0, 0, 0, 1, 0, 0, -1, 0, 0, 0, 0, 0, 1];
function mul4(a, b) {
  const o = new Array(16).fill(0);
  for (let r = 0; r < 4; r++) for (let c = 0; c < 4; c++) {
    let s = 0;
    for (let k = 0; k < 4; k++) s += a[r * 4 + k] * b[k * 4 + c];
    o[r * 4 + c] = s;
  }
  return o;
}
// 取 4x4 行主序矩阵左上 3x3，按列归一化去掉缩放。
// 注意：必须是行主序展开（R9[r*3+c] = m4[r*4+c]），转置会让共轭方向反掉。
function rot3Of(m4) {
  const c = [m4[0], m4[1], m4[2], m4[4], m4[5], m4[6], m4[8], m4[9], m4[10]];
  const n = [0, 1, 2].map((k) => Math.hypot(c[k * 3], c[k * 3 + 1], c[k * 3 + 2]) || 1);
  for (let k = 0; k < 3; k++) for (let j = 0; j < 3; j++) c[k * 3 + j] /= n[k];
  return c;
}

function loadBindRotations() {
  const json = JSON.parse(readFileSync(ARMATURE, 'utf8'));
  const worlds = new Map();
  const parents = new Map();
  const order = [];
  const walk = (node, parent) => {
    let local = node.transform ? node.transform.slice() : [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
    if (!parent) local = mul4(B2M, local);
    const pw = parent ? worlds.get(parent) : [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
    worlds.set(node.name, mul4(pw, local));
    parents.set(node.name, parent);
    order.push(node.name);
    for (const c of node.children ?? []) walk(c, node.name);
  };
  for (const r of json.armature.hierarchy) walk(r, null);
  const rot = new Map();
  for (const [k, v] of worlds) rot.set(k, rot3Of(v));
  return { rot, parents, order };
}

const bind = loadBindRotations();

// ---------- 规格 -> 动画 JSON ----------
const spec = JSON.parse(readFileSync(process.argv[2], 'utf8'));
const outPath = process.argv[3];
const duration = spec.duration;

// 收集每个关节的关键帧
const perJoint = new Map();
const NEUTRAL = { r: [0, 0, 0], l: [0, 0, 0] };
for (const key of spec.keys) {
  for (const [joint, pose] of Object.entries(key.poses ?? {})) {
    if (!perJoint.has(joint)) perJoint.set(joint, []);
    perJoint.get(joint).push({ t: key.t, ...NEUTRAL, ...pose });
  }
}

for (const [joint, keys] of perJoint) {
  if (!bind.rot.has(joint)) throw new Error(`关节不在 biped 骨架里: ${joint}`);
  const W = bind.rot.get(joint);
  const Wt = m3T(W);
  keys.sort((a, b) => a.t - b.t);
  if (keys[0].t > 0) keys.unshift({ t: 0, ...NEUTRAL });
  if (keys[keys.length - 1].t < duration) keys.push({ t: duration, ...NEUTRAL });
  // 同时间点去重（后者覆盖前者）
  const dedup = new Map();
  for (const k of keys) dedup.set(k.t, k);
  perJoint.set(joint, [...dedup.values()].sort((a, b) => a.t - b.t));
}

const animation = [];
// 关节顺序：Root 必须在第一条（EF 用"数组首条"做根修正的判定）
const ordered = [...bind.order.filter((n) => perJoint.has(n))];
for (const name of ordered) {
  const W = bind.rot.get(name);
  const Wt = m3T(W);
  const keys = perJoint.get(name);
  const time = [];
  const transform = [];
  for (const k of keys) {
    const R = eulerToM3(k.r[0], k.r[1], k.r[2]);
    const Rl = m3mul(Wt, m3mul(R, W));
    // 自检：世界旋转必须满足 W · Rl == R · W（作者写的世界轴角度就是最终世界旋转）
    const lhs = m3mul(W, Rl);
    const rhs = m3mul(R, W);
    let err = 0;
    for (let i = 0; i < 9; i++) err = Math.max(err, Math.abs(lhs[i] - rhs[i]));
    if (err > 1.0e-4) {
      throw new Error(`共轭转换自检失败: joint=${name} t=${k.t} 最大误差=${err}（rot3Of 行主序/转置是否又写错？）`);
    }
    const q = m3ToQuat(Rl);
    const loc = m3v(Wt, k.l);
    time.push(round(k.t, 4));
    transform.push({
      loc: loc.map((v) => round(v, 6)),
      rot: [round(q[3], 6), round(-q[0], 6), round(-q[1], 6), round(-q[2], 6)],
      sca: [1.0, 1.0, 1.0],
    });
  }
  animation.push({ name, time, transform });
}

function round(v, d) {
  const f = 10 ** d;
  const r = Math.round(v * f) / f;
  return Object.is(r, -0) ? 0 : r;
}

const out = { format: 'attributes', animation };
writeFileSync(outPath, JSON.stringify(out, null, 1) + '\n');
console.log(`wrote ${outPath}: id=${spec.id ?? '?'} duration=${duration}s joints=${animation.length}`);
for (const a of animation) console.log(`  ${a.name.padEnd(12)} keys=${a.time.length} times=${a.time.join(',')}`);
