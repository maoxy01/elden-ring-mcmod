'use strict';

// Shared by the desktop plugin and its Node/Java parity tests. No Minecraft dependency.
module.exports = function createPoseCore(fs, path, crypto) {
  const JOINTS = ['head', 'body', 'rightArm', 'leftArm', 'rightLeg', 'leftLeg'];
  const STAGES = ['IDLE', 'WINDUP', 'ACTIVE', 'FOLLOWUP_WINDOW', 'RECOVERY'];
  const POSES = 'src/main/resources/assets/eldencombat/skill_poses';
  const PARAMETERS = 'src/main/resources/data/eldencombat/dsh_skills/remembrance_parameters.json';
  const ROUTES = 'src/main/resources/assets/eldencombat/routes/2d.json';
  const clone = x => JSON.parse(JSON.stringify(x));
  const hash = x => crypto.createHash('sha256').update(x).digest('hex');
  const assert = (ok, message) => { if (!ok) throw new Error(message); };
  const read = file => JSON.parse(fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, ''));
  const numeric = x => (typeof x === 'number' || (typeof x === 'string' && x.trim() !== '')) && Number.isFinite(Number(x));
  const toEditor = r => [-r[0], -r[1], r[2]];
  const toRuntime = toEditor;
  function validate(pose) {
    assert(pose && pose.stages && typeof pose.stages === 'object' && !Array.isArray(pose.stages), '缺少 stages 对象');
    let count = 0;
    for (const [name, parts] of Object.entries(pose.stages)) {
      assert(/^(IDLE|WINDUP|ACTIVE|FOLLOWUP_WINDOW|RECOVERY)(#\d+)?$/.test(name), '不支持的阶段：' + name);
      assert(parts && typeof parts === 'object' && !Array.isArray(parts), name + ' 必须是对象');
      for (const [joint, keys] of Object.entries(parts)) {
        assert(JOINTS.includes(joint), '运行时不支持关节：' + joint);
        assert(Array.isArray(keys) && keys.length, name + '/' + joint + ' 没有关键帧');
        let previous = -1;
        for (const key of keys) {
          assert(Array.isArray(key) && key.length === 2 && typeof key[0] === 'number' && Number.isFinite(key[0]) && key[0] >= 0 && key[0] > previous, name + '/' + joint + ' 时间必须非负且严格递增');
          assert(Array.isArray(key[1]) && key[1].length === 3 && key[1].every(v => typeof v === 'number' && Number.isFinite(v)), '角度必须是三个有限数值');
          previous = key[0]; count++;
        }
      }
    }
    assert(count > 0, '动作不能没有关键帧');
    return pose;
  }
  function sample(keys, tick) {
    if (tick <= keys[0][0]) return [...keys[0][1]];
    if (tick >= keys.at(-1)[0]) return [...keys.at(-1)[1]];
    let i = 0;
    while (keys[i + 1][0] < tick) i++;
    const f = (tick - keys[i][0]) / (keys[i + 1][0] - keys[i][0]);
    return keys[i][1].map((v, axis) => v + (keys[i + 1][1][axis] - v) * f);
  }
  function resolve(pose, stage, index) {
    const stages = pose.stages;
    for (const key of [stage + '#' + index, stage]) {
      if (stages[key] && Object.keys(stages[key]).length) return {key, parts: stages[key], frozen: false};
    }
    // Java parse ignores empty stages, then forStage falls back to ACTIVE's last frame.
    if (stage === 'FOLLOWUP_WINDOW' || stage === 'RECOVERY') {
      const active = Object.keys(stages['ACTIVE#' + index] || {}).length ? 'ACTIVE#' + index : 'ACTIVE';
      if (stages[active]) return {key: active, parts: stages[active], frozen: true};
    }
    return {key: null, parts: {}, frozen: false};
  }
  function poseAt(pose, stage, index, tick) {
    const resolved = resolve(pose, stage, index);
    return Object.fromEntries(JOINTS.map(j => [j, resolved.parts[j] ? sample(resolved.parts[j], resolved.frozen ? Infinity : tick) : [0, 0, 0]]));
  }
  function flow(spec, followup) {
    let start = 0;
    const segments = [];
    function add(stage, index, ticks) {
      assert(Number.isInteger(ticks) && ticks > 0, '阶段时长必须是正整数：' + stage);
      segments.push({stage, index, start, ticks}); start += ticks;
    }
    add('WINDUP', 0, spec.windupTicks);
    let index = 1;
    for (;;) {
      add('ACTIVE', index, index > 1 && spec.followupActiveTicks > 0 ? spec.followupActiveTicks : spec.activeTicks);
      if (!(spec.followupWindowTicks > 0 && index < spec.maxStages)) break;
      if (!followup) { add('FOLLOWUP_WINDOW', index, spec.followupWindowTicks + 1); break; }
      // queueFollowup enters WINDUP with the previous ACTIVE index; enterStage increments on ACTIVE.
      add('WINDUP', index, spec.followupWindupTicks > 0 ? spec.followupWindupTicks : spec.windupTicks);
      index++;
    }
    add('RECOVERY', index, spec.recoveryTicks);
    return {segments, ticks: start};
  }
  function flowAt(timeline, tick) {
    const s = timeline.segments.find(s => tick < s.start + s.ticks) || timeline.segments.at(-1);
    return {...s, elapsed: Math.max(0, Math.min(s.ticks, tick - s.start))};
  }
  function duration(spec, stage) {
    const [base, suffix] = stage.split('#');
    const n = suffix === undefined ? 0 : +suffix;
    if (base === 'WINDUP') return n > 0 && spec.followupWindupTicks > 0 ? spec.followupWindupTicks : spec.windupTicks;
    if (base === 'ACTIVE') return n > 1 && spec.followupActiveTicks > 0 ? spec.followupActiveTicks : spec.activeTicks;
    if (base === 'FOLLOWUP_WINDOW') return spec.followupWindowTicks + 1;
    if (base === 'RECOVERY') return spec.recoveryTicks;
    return 1;
  }
  function contained(root, relative) {
    const absolute = path.resolve(root, relative), rel = path.relative(root, absolute);
    assert(rel && !rel.startsWith('..') && !path.isAbsolute(rel), '路径超出项目目录');
    return absolute;
  }
  function catalog(root) {
    const specs = read(path.join(root, PARAMETERS));
    return specs.map(spec => {
      assert(/^[a-z0-9_]+$/.test(spec.id), '非法战技 ID');
      const file = path.join(root, POSES, spec.id + '.json');
      return {id: spec.id, itemId: spec.itemId, file, spec};
    });
  }
  function load(root, id) {
    const item = catalog(root).find(s => s.id === id);
    assert(item, '找不到战技：' + id);
    const text = fs.readFileSync(item.file, 'utf8');
    return {version: 1, root: path.resolve(root), id, spec: item.spec, sourceHash: hash(text), original: validate(JSON.parse(text.replace(/^\uFEFF/, ''))), animations: {}, joints: {}, hand: 'right', followup: false};
  }
  function exportTracks(original, animations, jointIds) {
    const out = clone(original);
    const names = Object.fromEntries(Object.entries(jointIds).map(([n, id]) => [id, n]));
    for (const {stage, animation} of animations) {
      const parts = {};
      assert(animation, '阶段动画丢失：' + stage);
      for (const [id, animator] of Object.entries(animation.animators || {})) {
        const keyframes = animator.keyframes || Object.values(animator.channels || {}).flatMap(c => animator[c.name] || []);
        const list = Array.isArray(animator.keyframes) ? keyframes : ['rotation', 'position', 'scale'].flatMap(channel => animator[channel] || []);
        if (!list.length) continue;
        assert(names[id], '只能编辑六个原版关节，武器不可添加动画');
        assert(!animator.rotation_global && !animator.quaternion_interpolation, '不支持全局旋转或四元数插值');
        const keys = [];
        for (const k of list) {
          assert(k.channel === 'rotation', '运行时只支持旋转，不支持位移或缩放');
          assert(k.interpolation === 'linear', '运行时只支持 linear 线性插值');
          assert(k.data_points?.length === 1, '不支持前后双数据点或表达式关键帧');
          const point = k.data_points[0];
          assert(['x', 'y', 'z'].every(a => numeric(point[a])), '角度必须是常量数字，不能使用 Molang');
          assert(Number.isFinite(k.time) && k.time >= 0, '非法关键帧时间');
          keys.push([Math.round(k.time * 20 * 1e9) / 1e9, toRuntime(['x','y','z'].map(a => +point[a]))]);
        }
        keys.sort((a,b) => a[0]-b[0]);
        if (keys.length) parts[names[id]] = keys;
      }
      out.stages[stage] = parts;
    }
    return validate(out);
  }
  function save(binding, pose) {
    validate(pose);
    const file = catalog(binding.root).find(s => s.id === binding.id)?.file;
    assert(file, '源战技已移除');
    const before = fs.readFileSync(file);
    assert(hash(before) === binding.sourceHash, '源文件已被外部修改，保存已停止。请保留编辑工程，重新加载源动作后合并。');
    if (JSON.stringify(pose) === JSON.stringify(binding.original)) return {unchanged: true, file};
    const backup = file + '.' + Date.now() + '-' + crypto.randomBytes(4).toString('hex') + '.bak';
    fs.writeFileSync(backup, before, {flag: 'wx'});
    const next = JSON.stringify(pose, null, 2) + '\n';
    const temp = file + '.pose-' + crypto.randomBytes(5).toString('hex') + '.tmp';
    try {
      fs.writeFileSync(temp, next, {flag: 'wx'});
      assert(hash(fs.readFileSync(file)) === binding.sourceHash, '写入前检测到外部修改，保存已停止');
      fs.renameSync(temp, file);
    } finally { if (fs.existsSync(temp)) fs.unlinkSync(temp); }
    binding.sourceHash = hash(next); binding.original = clone(pose);
    return {file, backup, unchanged: false};
  }
  function weaponV02(root, itemId) {
    const base = 'art/equipment-2d/V0.2/holds/';
    const row = read(path.join(root, base, 'mapping.json')).items.find(r => r.id === itemId && r.kind === 'held');
    assert(row, 'V0.2没有配对的手持模型：' + itemId);
    const file = contained(root, base + row.project), bytes = fs.readFileSync(file), project = JSON.parse(bytes.toString('utf8').replace(/^\uFEFF/, ''));
    assert(project.meta?.model_format === 'java_block' && project.textures.length === 1, 'V0.2手持工程必须是单贴图Java物品模型');
    const texture = project.textures[0], resource = row.textureResource;
    assert(texture.source?.startsWith('data:image/png;base64,'), 'V0.2工程缺少嵌入贴图');
    assert(project.display?.thirdperson_righthand && project.display?.thirdperson_lefthand, 'V0.2工程缺少左右手握姿');
    const groups = new Map((project.groups || []).map(g => [g.uuid, g]));
    function checkGroups(nodes) {
      for (const node of nodes) if (typeof node !== 'string') {
        const group = {...groups.get(node.uuid), ...node};
        assert(!(group.rotation || []).some(v => v !== 0) && group.export !== false, 'V0.2手持组需要先烘焙旋转或可见性');
        checkGroups(node.children || []);
      }
    }
    checkGroups(project.outliner || []);
    const elements = project.elements.filter(e => e.export !== false).map(e => {
      assert((!e.type || e.type === 'cube') && !(e.rotation || []).some(v => v !== 0) && !e.inflate, 'V0.2手持几何需要先烘焙');
      const faces = Object.fromEntries(Object.entries(e.faces).filter(([,f]) => f.texture !== null && f.texture !== false).map(([side, face]) => {
        assert(face.texture === 0 || face.texture === texture.uuid, 'V0.2几何贴图绑定不一致');
        const uv = face.uv.map((v, axis) => v * 16 / (axis % 2 ? project.resolution.height : project.resolution.width));
        return [side, {...face, uv, texture: '#layer0'}];
      }));
      return {from: clone(e.from), to: clone(e.to), faces};
    });
    assert(elements.length, 'V0.2模型没有可见几何');
    return {itemId, state: row.state, display: clone(project.display),
      model: {textures: {layer0: resource}, elements}, textures: [{resource, data: texture.source}],
      source: {kind: 'v02', file: path.relative(root, file).replace(/\\/g, '/'), sha256: hash(bytes), runtimeParity: false}};
  }
  function weapon(root, itemId, source = 'runtime') {
    if (source === 'v02') return weaponV02(root, itemId);
    assert(source === 'runtime', '不支持的武器来源：' + source);
    const entry = read(path.join(root, ROUTES)).equipment.find(e => e.id === 'eldencombat:' + itemId);
    assert(entry?.status === 'ready' && entry.route === 'own_sprite', '武器不是可解析的 own_sprite 运行资源：' + itemId);
    const state = Object.hasOwn(entry.held || {}, 'standby') ? 'standby' : Object.keys(entry.held || {})[0];
    assert(state && entry.display && !(entry.blockers || []).length, '武器资源不完整：' + itemId);
    // Read the admitted source model used by generateEquipment2d. Never use a GUI thumbnail.
    const manifest = read(path.join(root, 'art/item-thickness/manifest.json'));
    const candidate = manifest.models.find(m => m.id === entry.id && m.state === state);
    assert(candidate, '缺少运行模型清单：' + itemId);
    const modelFile = contained(root, 'art/item-thickness/' + candidate.file);
    assert(hash(fs.readFileSync(modelFile)) === candidate.sha256, '武器模型校验失败，请先重新构建资源');
    const model = read(modelFile);
    assert(!model.loader && !model.overrides && (!model.elements || Array.isArray(model.elements)), '不支持自定义 loader 或动态 overrides');
    const textures = candidate.textures.map(t => {
      const file = contained(root, t.source), data = fs.readFileSync(file);
      assert(hash(data) === t.sha256, '武器贴图校验失败：' + file);
      return {resource: t.resource, data: 'data:image/png;base64,' + data.toString('base64')};
    });
    return {itemId, state, display: clone(entry.display), model, textures, source: {kind: 'runtime', runtimeParity: true}};
  }
  return {JOINTS, STAGES, POSES, PARAMETERS, ROUTES, clone, hash, assert, read, validate, sample, resolve, poseAt, flow, flowAt, duration, toEditor, toRuntime, catalog, load, exportTracks, save, weapon};
};
