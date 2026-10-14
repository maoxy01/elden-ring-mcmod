// 打印 EF biped 骨架的关节树与绑定局部变换（只读参考文件，不写入模组资源）。
// 用法: node biped-info.mjs <biped.json>
import { readFileSync } from 'node:fs';

const path = process.argv[2] ?? 'build/ef-ref/animmodels_entity_biped.json';
const root = JSON.parse(readFileSync(path, 'utf8'));

console.log('top-level keys:', Object.keys(root).join(', '));

// 找出含 joints 的键
function findJoints(node, keyPath = '') {
  if (node === null || typeof node !== 'object') return null;
  if (Array.isArray(node)) {
    for (let i = 0; i < node.length; i++) {
      const r = findJoints(node[i], `${keyPath}[${i}]`);
      if (r) return r;
    }
    return null;
  }
  if (Array.isArray(node.joints)) return { keyPath: `${keyPath}.joints`, joints: node.joints };
  for (const k of Object.keys(node)) {
    const r = findJoints(node[k], `${keyPath}.${k}`);
    if (r) return r;
  }
  return null;
}

const found = findJoints(root);
if (!found) {
  console.log('no "joints" array found; dumping shallow structure');
  console.log(JSON.stringify(root, (k, v) => (Array.isArray(v) ? `[array ${v.length}]` : v), 2).slice(0, 3000));
  process.exit(0);
}

console.log('joints at', found.keyPath, 'count =', found.joints.length);
console.log(JSON.stringify(found.joints[0], null, 2).slice(0, 1500));

function walk(list, depth) {
  for (const j of list) {
    const pad = '  '.repeat(depth);
    const t = j.transform ?? j.local_transform ?? j.localTransform ?? j.origin_transform;
    console.log(`${pad}${j.name}${j.id !== undefined ? ` #${j.id}` : ''}  tf=${JSON.stringify(t)}`);
    const kids = j.children ?? j.subJoints ?? j.sub_joints;
    if (Array.isArray(kids)) walk(kids, depth + 1);
  }
}
console.log('---- tree ----');
walk(found.joints, 0);
