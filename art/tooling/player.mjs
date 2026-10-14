const scale = v => v.map(n => n * 0.9);
const bone = (name, parent, pivot, rotation = [0, 0, 0]) => ({ name, parent, pivot: scale(pivot), rotation });
const cube = (name, owner, from, to, material = 'skin') => ({ name, bone: owner, from: scale(from), to: scale(to), material });

function boxUV(x, y, w, h, d, start = 0, end = h) {
  const sideY = y + d;
  return {
    east: [x, sideY + start, x + d, sideY + end],
    north: [x + d, sideY + start, x + d + w, sideY + end],
    west: [x + d + w, sideY + start, x + 2 * d + w, sideY + end],
    south: [x + 2 * d + w, sideY + start, x + 2 * d + 2 * w, sideY + end],
    up: [x + d, y, x + d + w, y + d],
    down: [x + d + w, y, x + d + 2 * w, y + d]
  };
}

export function playerModel(slim = false) {
  const armWidth = slim ? 3 : 4;
  const armX = 4 + armWidth / 2;
  const bones = [bone('root', null, [0, 0, 0]), bone('pelvis', 'root', [0, 14, 0]),
    bone('torso', 'pelvis', [0, 14, 0]), bone('head', 'torso', [0, 24, 0])];
  const cubes = [cube('head', 'head', [-4, 24, -4], [4, 32, 4]), cube('body', 'torso', [-4, 12, -2], [4, 24, 2])];
  const skinFaces = { head: boxUV(0, 0, 8, 8, 8), body: boxUV(16, 16, 8, 12, 4) };
  const overlayFaces = { head: boxUV(32, 0, 8, 8, 8), body: boxUV(16, 32, 8, 12, 4) };
  for (const [side, sign] of [['r', -1], ['l', 1]]) {
    const x = sign * armX;
    bones.push(bone(`upper_arm_${side}`, 'torso', [x, 23, 0]),
      bone(`forearm_${side}`, `upper_arm_${side}`, [x, 18, 0]), bone(`hand_${side}`, `forearm_${side}`, [x, 12, 0]));
    const a = Math.min(sign * 4, sign * (4 + armWidth)), b = Math.max(sign * 4, sign * (4 + armWidth));
    cubes.push(cube(`arm_${side}_upper`, `upper_arm_${side}`, [a, 18, -2], [b, 24, 2]),
      cube(`arm_${side}_lower`, `forearm_${side}`, [a, 12, -2], [b, 18, 2]));
    const [u, v] = side === 'r' ? [40, 16] : [32, 48];
    const [ou, ov] = side === 'r' ? [40, 32] : [48, 48];
    for (const [part, start, end] of [['upper', 0, 6], ['lower', 6, 12]]) {
      skinFaces[`arm_${side}_${part}`] = boxUV(u, v, armWidth, 12, 4, start, end);
      overlayFaces[`arm_${side}_${part}`] = boxUV(ou, ov, armWidth, 12, 4, start, end);
    }
  }
  bones.push(bone('sword_socket', 'hand_r', [-armX, 13, 0], [-90, 0, 0]),
    bone('shield_socket', 'hand_l', [armX, 14, -2.5], [0, 0, 0]),
    bone('drink_socket', 'hand_r', [-armX, 13, -1], [0, 0, 0]));
  for (const [side, sign] of [['r', -1], ['l', 1]]) {
    bones.push(bone(`thigh_${side}`, 'pelvis', [sign * 2, 12, 0]), bone(`shin_${side}`, `thigh_${side}`, [sign * 2, 6, 0]));
    const a = Math.min(0, sign * 4), b = Math.max(0, sign * 4);
    cubes.push(cube(`leg_${side}_upper`, `thigh_${side}`, [a, 6, -2], [b, 12, 2]),
      cube(`leg_${side}_lower`, `shin_${side}`, [a, 0, -2], [b, 6, 2]));
    const [u, v] = side === 'r' ? [0, 16] : [16, 48];
    const [ou, ov] = side === 'r' ? [0, 32] : [0, 48];
    for (const [part, start, end] of [['upper', 0, 6], ['lower', 6, 12]]) {
      skinFaces[`leg_${side}_${part}`] = boxUV(u, v, 4, 12, 4, start, end);
      overlayFaces[`leg_${side}_${part}`] = boxUV(ou, ov, 4, 12, 4, start, end);
    }
  }
  return { schemaVersion: 1, id: `eldencombat:player_${slim ? 'slim' : 'classic'}`, unitsPerBlock: 16,
    textureSize: [64, 64], texture: 'player-preview.png', rotationOrder: 'XYZ', animationRotation: 'additive_degrees',
    bones, cubes, materials: { skin: { uv: [8, 8, 16, 16] } }, skinFaces, overlayFaces };
}

const itemCube = (name, from, to, material) => ({ name, bone: 'root', from, to, material });
const item = (id, cubes, hitAxis) => ({ schemaVersion: 1, id: `eldencombat:${id}`, unitsPerBlock: 16,
  textureSize: [64, 64], texture: 'equipment.png', rotationOrder: 'XYZ', animationRotation: 'additive_degrees',
  bones: [{ name: 'root', parent: null, pivot: [0, 0, 0], rotation: [0, 0, 0] }], cubes, hitAxis });
export const equipment = [
  item('straight_sword', [itemCube('grip', [-0.65, -3, -0.65], [0.65, 2, 0.65], 'leather'),
    itemCube('pommel', [-1, -4, -1], [1, -3, 1], 'steel_dark'), itemCube('crossguard', [-3.5, 2, -0.85], [3.5, 3, 0.85], 'steel_dark'),
    itemCube('blade', [-1, 3, -0.45], [1, 20, 0.45], 'steel'), itemCube('tip', [-0.6, 20, -0.35], [0.6, 22, 0.35], 'steel')],
  { from: [0, 3, 0], to: [0, 22, 0], radiusBlocks: 0.18 }),
  item('great_sword', [itemCube('grip', [-0.8, -5, -0.8], [0.8, 3, 0.8], 'leather'),
    itemCube('pommel', [-1.25, -6, -1.1], [1.25, -5, 1.1], 'steel_dark'), itemCube('crossguard', [-5, 3, -1.1], [5, 4.5, 1.1], 'steel_dark'),
    itemCube('blade', [-2.2, 4.5, -0.7], [2.2, 28, 0.7], 'steel'), itemCube('tip', [-1.25, 28, -0.55], [1.25, 31, 0.55], 'steel')],
  { from: [0, 4.5, 0], to: [0, 31, 0], radiusBlocks: 0.24 }),
  item('shield', [itemCube('panel', [-5, -6, -1.1], [5, 6, 0.3], 'red'),
    itemCube('rim_top', [-5.75, 6, -1.5], [5.75, 7, 0.5], 'steel_dark'), itemCube('rim_bottom', [-5.75, -7, -1.5], [5.75, -6, 0.5], 'steel_dark'),
    itemCube('rim_left', [-5.75, -6, -1.5], [-5, 6, 0.5], 'steel_dark'), itemCube('rim_right', [5, -6, -1.5], [5.75, 6, 0.5], 'steel_dark'),
    itemCube('boss', [-1.5, -1.5, -2.4], [1.5, 1.5, -1.1], 'steel'), itemCube('handle', [-0.6, -2, 0.3], [0.6, 2, 1.5], 'leather')]),
  item('healing_flask', [itemCube('bottle', [-1.6, -1.6, -1.1], [1.6, 1.6, 1.1], 'amber'),
    itemCube('neck', [-0.75, 1.6, -0.7], [0.75, 2.5, 0.7], 'amber'), itemCube('stopper', [-0.9, 2.5, -0.8], [0.9, 3.3, 0.8], 'leather')])
];

const keys = frames => frames.map(([tick, ...value]) => ({ tick, value }));
const track = (frames) => keys(frames);
const clip = (id, lengthTicks, tracks, events = [], loop = false) => ({ id, lengthTicks, loop, events: events.sort((a, b) => a.tick - b.tick), tracks });
const flat = v => track([[0, ...v]]);
const ready = { upper_arm_r: flat([30, 0, -10]), forearm_r: flat([55, 0, 0]), upper_arm_l: flat([22, 0, 8]), forearm_l: flat([30, 0, 0]), shield_socket: flat([-52, 0, 0]) };

export const commonClips = [
  clip('idle', 40, { ...ready, torso: track([[0, 0, 0, 0], [20, 1.5, 0, 0], [40, 0, 0, 0]]) }, [], true),
  ...[['walk', 24, 24], ['run', 16, 38]].map(([id, length, angle]) => clip(id, length, { ...ready,
    thigh_r: track([[0, angle, 0, 0], [length / 2, -angle, 0, 0], [length, angle, 0, 0]]),
    thigh_l: track([[0, -angle, 0, 0], [length / 2, angle, 0, 0], [length, -angle, 0, 0]]),
    shin_r: track([[0, -8, 0, 0], [length / 2, -25, 0, 0], [length, -8, 0, 0]]),
    shin_l: track([[0, -25, 0, 0], [length / 2, -8, 0, 0], [length, -25, 0, 0]]) },
    [{ tick: 0, type: 'footstep' }, { tick: length / 2, type: 'footstep' }], true)),
  clip('roll', 20, { pelvis: track([[0, 0, 0, 0], [3, 25, 0, 0], [8, 140, 0, 0], [13, 270, 0, 0], [18, 360, 0, 0], [20, 360, 0, 0]]),
    torso: track([[0, 0, 0, 0], [3, 25, 0, 0], [15, 25, 0, 0], [20, 0, 0, 0]]),
    thigh_r: track([[0, 0, 0, 0], [3, 75, 0, 0], [15, 75, 0, 0], [20, 0, 0, 0]]),
    thigh_l: track([[0, 0, 0, 0], [3, 75, 0, 0], [15, 75, 0, 0], [20, 0, 0, 0]]),
    shin_r: track([[0, 0, 0, 0], [3, -115, 0, 0], [15, -115, 0, 0], [20, 0, 0, 0]]),
    shin_l: track([[0, 0, 0, 0], [3, -115, 0, 0], [15, -115, 0, 0], [20, 0, 0, 0]]),
    upper_arm_r: track([[0, 30, 0, -10], [3, 65, 0, -12], [15, 65, 0, -12], [20, 30, 0, -10]]),
    forearm_r: flat([55, 0, 0]), upper_arm_l: flat([60, 0, 12]), forearm_l: flat([65, 0, 0]) }, [{ tick: 2, type: 'roll_whoosh' }]),
  clip('guard_idle', 40, { ...ready, upper_arm_l: flat([52, 0, 24]), forearm_l: flat([48, 0, 0]), shield_socket: flat([-100, 0, 0]) }, [], true),
  clip('guard_hit', 12, { ...ready, upper_arm_l: track([[0, 52, 0, 24], [2, 30, 0, 28], [12, 52, 0, 24]]),
    forearm_l: flat([48, 0, 0]), shield_socket: flat([-100, 0, 0]), torso: track([[0, 0, 0, 0], [2, -12, 0, 0], [12, 0, 0, 0]]) }, [{ tick: 0, type: 'guard_impact' }]),
  clip('guard_break', 24, { ...ready, torso: track([[0, 0, 0, 0], [4, -24, 0, 0], [17, -18, 0, 0], [24, 0, 0, 0]]),
    upper_arm_l: track([[0, 52, 0, 24], [4, -30, 0, 45], [17, -15, 0, 35], [24, 22, 0, 8]]) }, [{ tick: 0, type: 'guard_break' }]),
  clip('parry', 18, { ...ready, upper_arm_l: track([[0, 30, 0, 8], [3, 70, -25, 32], [7, 60, 35, 48], [18, 22, 0, 8]]),
    forearm_l: flat([30, 0, 0]), shield_socket: flat([-90, 0, 0]) }, [{ tick: 3, type: 'parry_whoosh' }]),
  clip('drink', 28, { ...ready, upper_arm_r: track([[0, 30, 0, -10], [8, 65, -12, 0], [18, 65, -12, 0], [28, 30, 0, -10]]),
    forearm_r: track([[0, 55, 0, 0], [8, 100, 0, 0], [18, 100, 0, 0], [28, 55, 0, 0]]),
    drink_socket: track([[0, 0, 0, 0], [8, 80, 0, 0], [18, 80, 0, 0], [28, 0, 0, 0]]),
    head: track([[0, 0, 0, 0], [8, -10, 0, 0], [18, -10, 0, 0], [28, 0, 0, 0]]) }, [{ tick: 18, type: 'drink_visual' }]),
  clip('hit_stagger', 16, { ...ready, torso: track([[0, 0, 0, 0], [3, -20, 0, 8], [11, -12, 0, 4], [16, 0, 0, 0]]) }, [{ tick: 0, type: 'hurt_visual' }]),
  clip('death', 40, { pelvis: track([[0, 0, 0, 0], [12, -40, 0, 8], [24, -90, 0, 0], [40, -90, 0, 0]]),
    upper_arm_r: flat([0, 0, -16]), upper_arm_l: flat([0, 0, 16]), head: track([[0, 0, 0, 0], [24, 15, 0, 0], [40, 15, 0, 0]]) })
];

export function attackClips(great = false) {
  const end = great ? 30 : 22, start = great ? 12 : 8, hitEnd = great ? 17 : 12;
  const attack = (id, total, a, b, alternate = false, charged = false) => {
    const lift = great || id !== 'light_1' && id !== 'light_2';
    const begin = lift ? [155, alternate ? -15 : 0, -16] : [55, -65 * (alternate ? -1 : 1), -30];
    const strike = lift ? [25, alternate ? 15 : 0, -16] : [50, 65 * (alternate ? -1 : 1), -10];
    const pre = id.startsWith('light') ? a - 2 : 0;
    return clip(id, total, { ...ready,
      torso: track([[0, 0, lift ? -12 : -25, 0], [a, lift ? 18 : 0, lift ? 12 : 25, 0], [total, 0, 0, 0]]),
      upper_arm_r: track([[0, ...begin], ...(pre > 0 ? [[pre, ...begin]] : []), [b - 1, ...strike], [total, 30, 0, -10]]),
      forearm_r: track([[0, lift ? -60 : 35, 0, 0], [b - 1, lift ? 18 : 30, 0, 0], [total, 55, 0, 0]]),
      ...(great ? { upper_arm_l: track([[0, 120, 5, 25], [b - 1, 60, 5, 25], [total, 30, 0, 8]]), forearm_l: flat([80, 0, 0]) } : {})
    }, [{ tick: a, type: 'hit_start' }, { tick: a, type: charged ? 'charged_whoosh' : 'sword_whoosh' }, { tick: b, type: 'hit_end' }]);
  };
  return [attack('light_1', end, start, hitEnd), attack('light_2', end, start, hitEnd, true),
    clip('heavy_windup', 30, { ...ready, upper_arm_r: track([[0, 30, 0, -10], [20, 155, 0, -16], [30, 155, 0, -16]]),
      forearm_r: track([[0, 55, 0, 0], [20, -60, 0, 0], [30, -60, 0, 0]]) }),
    attack('heavy_release', 34, 15, 20), attack('charged_release', 36, 16, 22, false, true)];
}

export const presentation = {
  schemaVersion: 1, unitsPerBlock: 16, playerHeightBlocks: 1.8, textureSource: 'live_player_skin',
  skinVariants: { default: 'player_classic', slim: 'player_slim' },
  sockets: { mainhand: 'sword_socket', offhand: 'shield_socket', drink: 'drink_socket' },
  actionBindings: { NONE: ['idle', 'walk', 'run'], LIGHT_ATTACK: ['light_1', 'light_2'], HEAVY_CHARGE: 'heavy_windup',
    HEAVY_ATTACK: 'heavy_release', CHARGED_HEAVY: 'charged_release', ROLL: 'roll', PARRY: 'parry', DRINK: 'drink',
    GUARD: 'guard_idle', GUARD_BROKEN: 'guard_break', STAGGER: 'hit_stagger', DEATH: 'death' },
  profiles: { light_sword: 'straight_sword.animations.json', great_sword: 'great_sword.animations.json' },
  variantAnimationOverrides: { great_sword: { default: 'player_classic.great_sword.animations.json', slim: 'player_slim.great_sword.animations.json' } },
  visibility: { default: ['mainhand', 'offhand'], roll: [], death: [], drink: ['drink'] },
  unsupportedMechanics: ['execution', 'riposte', 'jump_attack'],
  positionChannels: { file: 'player.visual-offsets.json', space: 'parent_model_units', affectsEntityMovement: false, axes: ['y'] },
  leftHanded: 'mirror geometry, animation and hit axis together; do not mirror the skin UV',
  twoHandGrip: { weapon: 'great_sword', leftHandTargetLocal: [0, -3, 0], implementation: 'use baked variant animation overrides; no Java IK needed',
    shieldHiddenFor: ['idle', 'walk', 'run', 'light_1', 'light_2', 'heavy_windup', 'heavy_release', 'charged_release', 'hit_stagger'] }
};
