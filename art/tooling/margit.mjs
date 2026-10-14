// Model units are sixteenths of a Minecraft block; forward is -Z.
const bones = [];
const cubes = [];
const bone = (name, parent, pivot, rotation = [0, 0, 0]) => {
  bones.push({ name, parent, pivot, rotation });
};
const cube = (name, owner, from, to, material, rotation = [0, 0, 0], pivot) => {
  cubes.push({ name, bone: owner, from, to, material, rotation, pivot: pivot || from });
};

bone('root', null, [0, 0, 0]);
bone('pelvis', 'root', [0, 24, 0]);
bone('torso', 'pelvis', [0, 26, 0], [-8, 0, 0]);
bone('head', 'torso', [0, 40, -1], [8, 0, 0]);
bone('upper_arm_r', 'torso', [-13, 37, 0], [0, 0, -5]);
bone('forearm_r', 'upper_arm_r', [-13, 27, 0], [-10, 0, 0]);
bone('hand_r', 'forearm_r', [-13, 19, 0]);
bone('staff_socket', 'hand_r', [-13, 20, -2]);
bone('upper_arm_l', 'torso', [13, 37, 0], [0, 0, 5]);
bone('forearm_l', 'upper_arm_l', [13, 27, 0], [-8, 0, 0]);
bone('hand_l', 'forearm_l', [13, 19, 0]);
bone('spectral_socket', 'hand_l', [13, 20, -2]);
bone('thigh_r', 'pelvis', [-6, 24, 0]);
bone('shin_r', 'thigh_r', [-6, 12, 0]);
bone('thigh_l', 'pelvis', [6, 24, 0]);
bone('shin_l', 'thigh_l', [6, 12, 0]);
bone('tail_01', 'pelvis', [0, 23, 6], [-8, 35, 0]);
bone('tail_02', 'tail_01', [0, 23, 15], [-30, 0, 0]);
bone('tail_03', 'tail_02', [0, 23, 23], [-30, 0, 0]);

cube('body', 'torso', [-10, 24, -6], [10, 39, 7], 'cloth_dark');
cube('head', 'head', [-5, 39, -7], [5, 48, 3], 'skin');
cube('beard', 'head', [-3, 35.5, -7.8], [3, 41, -6.7], 'beard');
cube('nose', 'head', [-1, 41.5, -8], [1, 44, -7], 'skin');

for (const [side, sign] of [['r', -1], ['l', 1]]) {
  const x = sign * 13;
  cube(`arm_${side}`, `upper_arm_${side}`, [x - 3.8, 27, -3.8], [x + 3.8, 38, 3.8], 'cloth');
  cube(`forearm_${side}`, `forearm_${side}`, [x - 3.2, 19, -3.2], [x + 3.2, 27, 3.2], 'skin');
  cube(`hand_${side}`, `hand_${side}`, [x - 3.3, 16, -3.4], [x + 3.3, 20, 2.4], 'skin');
  const legX = sign * 6;
  cube(`thigh_${side}`, `thigh_${side}`, [legX - 4, 12, -4], [legX + 4, 24, 4], 'skin');
  cube(`shin_${side}`, `shin_${side}`, [legX - 3.5, 3, -3.4], [legX + 3.5, 12, 3.4], 'skin');
  cube(`foot_${side}`, `shin_${side}`, [legX - 4, 0, -7], [legX + 4, 3, 4], 'skin');
}

// Six broad cloak pieces carry painted wear rather than modeled folds.
cube('cloak_front_r', 'torso', [-11, 17, -7.8], [-0.7, 36, -6.4], 'cloth');
cube('cloak_front_l', 'torso', [0.7, 19, -7.8], [11, 36, -6.4], 'cloth');
cube('cloak_back', 'torso', [-12, 15, 7.2], [12, 39, 9], 'cloth');
cube('cloak_side_r', 'torso', [-12, 20, -6], [-10.5, 36, 7], 'cloth_dark');
cube('cloak_side_l', 'torso', [10.5, 18, -6], [12, 36, 7], 'cloth_dark');
cube('cloak_front_hem', 'torso', [-4, 21, -7.4], [4, 30, -6.2], 'cloth_dark');
cube('collar_front', 'torso', [-13, 36, -8.5], [13, 41, -6.2], 'cloth');
cube('collar_back', 'torso', [-13, 36, 6.5], [13, 42, 9.5], 'cloth');

for (const sign of [-1, 1]) {
  const side = sign === -1 ? 'r' : 'l';
  const x = sign * 3;
  cube(`horn_top_${side}_base`, 'head', [x - 1.25, 47, -2], [x + 1.25, 51, 0.5], 'horn_dark', [0, 0, -sign * 18], [x, 47, -0.75]);
  cube(`horn_top_${side}_tip`, 'head', [sign * 4.3 - 1, 50, -1.8], [sign * 4.3 + 1, 53, 0.2], 'horn', [0, 0, sign * 28], [sign * 4.3, 50, -0.8]);
  cube(`horn_side_${side}_base`, 'head', [sign * 5.2 - 1.8, 44.5, -3], [sign * 5.2 + 1.8, 47, -0.5], 'horn_dark');
  cube(`horn_side_${side}_tip`, 'head', [sign * 7 - 1.1, 46, -2.8], [sign * 7 + 1.1, 49, -0.8], 'horn', [0, 0, -sign * 20], [sign * 7, 46, -1.8]);
}

cube('tail_root', 'tail_01', [-3, 20, 6], [3, 26, 15], 'tail');
cube('tail_middle', 'tail_02', [-2.5, 20.5, 15], [2.5, 25.5, 23], 'tail');
cube('tail_tip', 'tail_03', [-1.8, 21.2, 23], [1.8, 24.8, 30], 'tail_tip');
cube('staff_shaft', 'staff_socket', [-13.85, 1, -2.85], [-12.15, 46, -1.15], 'wood');
cube('staff_top', 'staff_socket', [-13.85, 45, -2.85], [-12.15, 51, -1.15], 'wood', [0, 0, -12], [-13, 45, -2]);
cube('staff_branch', 'staff_socket', [-17.5, 43, -2.75], [-13, 45, -1.25], 'wood');

const keys = (...frames) => frames.map(([tick, value]) => ({ tick, value }));
const clip = (id, lengthTicks, loop, tracks, events = []) => ({ id, lengthTicks, loop, tracks, events });
const animations = [
  clip('idle', 60, true, {
    torso: keys([0, [0, 0, 0]], [30, [2, 0, 0]], [60, [0, 0, 0]]),
    head: keys([0, [0, 0, 0]], [30, [-2, 0, 0]], [60, [0, 0, 0]]),
    tail_01: keys([0, [0, -4, 0]], [30, [0, 4, 0]], [60, [0, -4, 0]]),
    tail_02: keys([0, [-3, 0, 0]], [30, [3, 0, 0]], [60, [-3, 0, 0]])
  }),
  clip('walk', 32, true, {
    thigh_r: keys([0, [20, 0, 0]], [16, [-20, 0, 0]], [32, [20, 0, 0]]),
    thigh_l: keys([0, [-20, 0, 0]], [16, [20, 0, 0]], [32, [-20, 0, 0]]),
    shin_r: keys([0, [0, 0, 0]], [8, [25, 0, 0]], [16, [0, 0, 0]], [32, [0, 0, 0]]),
    shin_l: keys([0, [0, 0, 0]], [16, [0, 0, 0]], [24, [25, 0, 0]], [32, [0, 0, 0]]),
    upper_arm_l: keys([0, [12, 0, 0]], [16, [-12, 0, 0]], [32, [12, 0, 0]]),
    upper_arm_r: keys([0, [-8, 0, 0]], [16, [8, 0, 0]], [32, [-8, 0, 0]]),
    tail_01: keys([0, [0, -6, 0]], [16, [0, 6, 0]], [32, [0, -6, 0]])
  }, [{ tick: 0, type: 'footstep', side: 'right' }, { tick: 16, type: 'footstep', side: 'left' }]),
  clip('staff_sweep', 44, false, {
    torso: keys([0, [0, 0, 0]], [16, [25, -50, 0]], [20, [25, -50, 0]], [26, [25, 40, 0]], [44, [0, 0, 0]]),
    upper_arm_r: keys([0, [0, 0, 0]], [16, [45, 0, 25]], [20, [45, 0, 25]], [26, [45, 0, 25]], [44, [0, 0, 0]]),
    forearm_r: keys([0, [0, 0, 0]], [16, [10, 0, 0]], [26, [10, 0, 0]], [44, [0, 0, 0]]),
    staff_socket: keys([0, [0, 0, 0]], [16, [180, 0, 0]], [26, [180, 0, 0]], [44, [0, 0, 0]]),
    tail_01: keys([0, [0, 0, 0]], [16, [0, 10, 0]], [26, [0, -10, 0]], [44, [0, 0, 0]])
  }, [{ tick: 20, type: 'hit_start', attack: 'staff_sweep' }, { tick: 27, type: 'hit_end', attack: 'staff_sweep' }]),
  clip('staff_slam', 54, false, {
    torso: keys([0, [0, 0, 0]], [20, [0, 0, 0]], [28, [0, 0, 0]], [33, [25, 0, 0]], [54, [0, 0, 0]]),
    upper_arm_r: keys([0, [0, 0, 0]], [20, [155, 0, 25]], [28, [155, 0, 25]], [33, [60, 0, 25]], [54, [0, 0, 0]]),
    forearm_r: keys([0, [0, 0, 0]], [20, [10, 0, 0]], [28, [10, 0, 0]], [33, [10, 0, 0]], [54, [0, 0, 0]]),
    staff_socket: keys([0, [0, 0, 0]], [20, [180, 0, 0]], [36, [180, 0, 0]], [54, [0, 0, 0]])
  }, [{ tick: 30, type: 'hit_start', attack: 'staff_slam' }, { tick: 35, type: 'hit_end', attack: 'staff_slam' }, { tick: 33, type: 'impact' }]),
  clip('dagger_slash', 30, false, {
    torso: keys([0, [0, 0, 0]], [8, [20, 25, 0]], [15, [20, -25, 0]], [30, [0, 0, 0]]),
    upper_arm_l: keys([0, [0, 0, 0]], [8, [55, 0, -35]], [15, [55, 0, -35]], [30, [0, 0, 0]]),
    forearm_l: keys([0, [0, 0, 0]], [8, [8, 0, 0]], [15, [8, 0, 0]], [30, [0, 0, 0]]),
    spectral_socket: keys([0, [0, 0, 0]], [3, [180, 0, 0]], [26, [180, 0, 0]], [30, [0, 0, 0]])
  }, [{ tick: 3, type: 'equip', item: 'spectral_dagger', socket: 'spectral_socket' }, { tick: 11, type: 'hit_start', attack: 'dagger_slash' }, { tick: 17, type: 'hit_end', attack: 'dagger_slash' }, { tick: 26, type: 'unequip', socket: 'spectral_socket' }]),
  clip('hammer_slam', 64, false, {
    torso: keys([0, [0, 0, 0]], [20, [0, 0, 0]], [36, [0, 0, 0]], [42, [25, 0, 0]], [64, [0, 0, 0]]),
    upper_arm_l: keys([0, [0, 0, 0]], [20, [155, 0, -35]], [36, [155, 0, -35]], [42, [65, 0, -35]], [64, [0, 0, 0]]),
    forearm_l: keys([0, [0, 0, 0]], [20, [8, 0, 0]], [36, [8, 0, 0]], [42, [8, 0, 0]], [64, [0, 0, 0]]),
    spectral_socket: keys([0, [0, 0, 0]], [20, [180, 0, 0]], [60, [180, 0, 0]], [64, [0, 0, 0]]),
    tail_01: keys([0, [0, 0, 0]], [36, [-8, 5, 0]], [42, [8, -5, 0]], [64, [0, 0, 0]])
  }, [{ tick: 4, type: 'equip', item: 'spectral_hammer', socket: 'spectral_socket' }, { tick: 39, type: 'hit_start', attack: 'hammer_slam' }, { tick: 45, type: 'hit_end', attack: 'hammer_slam' }, { tick: 42, type: 'impact' }, { tick: 60, type: 'unequip', socket: 'spectral_socket' }]),
  clip('stagger', 70, false, {
    torso: keys([0, [0, 0, 0]], [8, [-32, 0, 0]], [54, [-32, 0, 0]], [70, [0, 0, 0]]),
    head: keys([0, [0, 0, 0]], [8, [18, 0, 0]], [54, [18, 0, 0]], [70, [0, 0, 0]]),
    upper_arm_l: keys([0, [0, 0, 0]], [8, [0, 0, 18]], [54, [0, 0, 18]], [70, [0, 0, 0]])
  }),
  clip('death', 50, false, {
    root: keys([0, [0, 0, 0]], [18, [0, 0, 20]], [34, [0, 0, 90]], [50, [0, 0, 90]]),
    torso: keys([0, [0, 0, 0]], [18, [-15, 0, 0]], [34, [-15, 0, 0]], [50, [-15, 0, 0]])
  })
];

// Retime keys and combat events together; the server samples this same timeline.
const combatLengths = { staff_sweep: 34, staff_slam: 42, dagger_slash: 22, hammer_slam: 50, walk: 26 };
for (const animation of animations) {
  const length = combatLengths[animation.id];
  if (!length) continue;
  const factor = length / animation.lengthTicks;
  for (const track of Object.values(animation.tracks)) for (const frame of track) frame.tick = Math.round(frame.tick * factor);
  for (const event of animation.events) event.tick = Math.round(event.tick * factor);
  animation.lengthTicks = length;
}

export const margit = {
  schemaVersion: 1,
  id: 'eldencombat:margit',
  unitsPerBlock: 16,
  forward: '-Z',
  texture: 'margit.png',
  textureSize: [128, 128],
  bones,
  cubes,
  animations
};

export const spectralWeapons = [
  {
    schemaVersion: 1, id: 'eldencombat:spectral_hammer', unitsPerBlock: 16, forward: '-Z',
    texture: 'margit.png', textureSize: [128, 128],
    bones: [{ name: 'root', parent: null, pivot: [0, 0, 0], rotation: [0, 0, 0] }],
    cubes: [
      { name: 'handle', bone: 'root', from: [-0.8, -3, -0.8], to: [0.8, 25, 0.8], material: 'gold' },
      { name: 'head', bone: 'root', from: [-8, 23, -5], to: [8, 32, 5], material: 'gold' }
    ], animations: []
  },
  {
    schemaVersion: 1, id: 'eldencombat:spectral_dagger', unitsPerBlock: 16, forward: '-Z',
    texture: 'margit.png', textureSize: [128, 128],
    bones: [{ name: 'root', parent: null, pivot: [0, 0, 0], rotation: [0, 0, 0] }],
    cubes: [
      { name: 'handle', bone: 'root', from: [-0.8, -3, -0.8], to: [0.8, 4, 0.8], material: 'wood' },
      { name: 'guard', bone: 'root', from: [-3, 3, -1], to: [3, 4.5, 1], material: 'gold' },
      { name: 'blade', bone: 'root', from: [-1.3, 4.5, -0.5], to: [1.3, 15, 0.5], material: 'gold' },
      { name: 'tip', bone: 'root', from: [-0.65, 15, -0.5], to: [0.65, 18, 0.5], material: 'gold' }
    ], animations: []
  }
];
