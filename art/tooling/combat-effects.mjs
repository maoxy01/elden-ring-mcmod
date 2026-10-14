export const effects = {
  schemaVersion: 1, status: 'design_and_particle_textures_only', worldUnits: 'blocks',
  budget: { maxAlivePerEncounter: 160, reducedParticlesScale: 0.35, trailsMaxPoints: 12, trailLifetimeTicks: 3 },
  events: {
    sword_whoosh: { source: 'clip_event', particle: 'none', sound: 'sword_swing' },
    charged_whoosh: { source: 'clip_event', particle: 'none', sound: 'great_sword_swing' },
    roll_whoosh: { source: 'clip_event', sound: 'roll', particle: 'dust', count: 6, color: '#9ca7a2', size: 0.06, lifeTicks: [6, 10], anchor: 'feet' },
    parry_whoosh: { source: 'clip_event', particle: 'none', sound: 'parry_swing' },
    hit_confirmed: { source: 'server_result', sound: 'blade_hit', particle: 'spark', count: 5, color: '#d6dddc', size: 0.035, lifeTicks: [3, 6], anchor: 'contact_point' },
    guard_impact: { source: 'server_result', sound: 'shield_block', particle: 'spark', count: 7, color: '#ead4a0', size: 0.035, lifeTicks: [4, 7], anchor: 'shield_contact' },
    guard_break: { source: 'server_result', sound: 'guard_break', particle: 'dust', count: 10, color: '#b7c2c1', size: 0.055, lifeTicks: [5, 9], anchor: 'shield_contact' },
    parry_success: { source: 'server_result', sound: 'parry_success', particle: 'spark', count: 12, color: '#f7e9a1', size: 0.045, lifeTicks: [4, 8], anchor: 'contact_point' },
    drink_visual: { source: 'clip_event_after_server_consumption', sound: 'flask_drink', particle: 'heal', count: 8, color: '#b8d9a5', size: 0.04, lifeTicks: [10, 16], anchor: 'torso', verticalSpeed: 0.025 },
    hurt_visual: { source: 'server_result', particle: 'none', sound: 'player_hurt' },
    footstep: { source: 'clip_event_when_grounded_and_moving', particle: 'none', sound: 'ground_material_step' },
    staff_slam: { source: 'margit.staff_slam.impact@33', sound: 'staff_impact', particle: 'dust', count: 18, color: 'sample_ground', size: 0.09, lifeTicks: [7, 13], anchor: 'staff_contact_raycast', radius: 0.8 },
    hammer_slam: { source: 'margit.hammer_slam.impact@42', sound: 'hammer_impact', particle: 'spark_and_dust', count: 30, color: '#edc666', size: 0.06, lifeTicks: [6, 12], anchor: 'hammer_contact_raycast', radius: 1.2 },
    spectral_equip: { source: 'margit.equip', sound: 'spectral_form', particle: 'spark', count: 8, color: '#e9bb43', size: 0.035, lifeTicks: [5, 8], anchor: 'spectral_socket' },
    spectral_trail: { source: 'margit_active_hit_window', particle: 'spark', color: '#e9bb43', size: 0.025, lifeTicks: [2, 3], anchor: 'sample_weapon_axis', maxPoints: 12 },
    boss_death: { source: 'margit.death', sound: 'boss_death', particle: 'dust', color: '#a9b9b3', size: 0.065, lifeTicks: [10, 16],
      bursts: [{ tick: 40, count: 12 }, { tick: 44, count: 12 }, { tick: 48, count: 12 }], opacity: { fromTick: 40, toTick: 50 }, cleanupTick: 52 }
  },
  restrictions: { damageFromParticles: false, dynamicLights: false, blood: false, fullscreenFlash: false, cameraShakeDefault: false,
    keepSpectralWeaponFullBright: true, goldWeaponOpacity: 1 },
  cancellation: ['clear trails and pending events on sequence change, death, unload, world exit'],
  audioDelivery: 'No OGG files or sounds.json included. Use verified existing game sounds temporarily.'
};

export const sounds = {
  schemaVersion: 1, status: 'design_only_no_audio_files', outputFormat: { container: 'ogg', codec: 'vorbis', channels: 1, sampleRate: 44100 },
  sourcePolicy: 'Create original sound recordings/synthesis; do not extract Elden Ring audio.',
  specs: [
    ['sword_swing', 0.18, 0.45, 1.15, 'short dry air swish, light metal tail'],
    ['great_sword_swing', 0.32, 0.65, 0.85, 'heavier low air swish, no explosion'],
    ['blade_hit', 0.16, 0.6, 1, 'brief blunt contact plus metal click'],
    ['shield_block', 0.22, 0.65, 1, 'steel knock with short wooden body'],
    ['guard_break', 0.35, 0.8, 0.9, 'low metal rattle and sharp release'],
    ['parry_swing', 0.14, 0.4, 1.2, 'short shield air movement'],
    ['parry_success', 0.4, 0.85, 1, 'clear two-tone steel ring; no execution cue'],
    ['roll', 0.3, 0.4, 1, 'cloth brush and ground scrape'],
    ['flask_drink', 0.45, 0.45, 1, 'small bottle gulp and glass tap'],
    ['player_hurt', 0.15, 0.45, 1, 'brief impact; no voiced imitation'],
    ['staff_impact', 0.5, 0.85, 0.9, 'wood crack and earth thud'],
    ['hammer_impact', 0.7, 1, 0.85, 'heavy metal thud with short bright decay'],
    ['spectral_form', 0.35, 0.5, 1.05, 'soft rising metallic shimmer'],
    ['boss_death', 1.5, 0.85, 0.85, 'low cloth collapse and fading granular dust']
  ].map(([id, durationSeconds, volume, pitch, direction]) => ({ id: `eldencombat:${id}`, durationSeconds, volume, pitch, direction, variants: 2 })),
  playback: { maxDistanceBlocks: 24, footstepsDistanceBlocks: 12, variancePitch: 0.04, deduplicationKey: 'entityId/actionSequence/eventType/eventTick',
    confirmationAuthority: 'server', noDoublePlaybackWithVanilla: true }
};
