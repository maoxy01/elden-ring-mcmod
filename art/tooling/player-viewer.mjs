import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { createAsset } from './player-scene.mjs';

const pack = window.PLAYER_PACK, $ = id => document.getElementById(id);
const renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true });
renderer.setPixelRatio(Math.min(devicePixelRatio, 2)); renderer.setClearColor(0xe8eeed); renderer.outputColorSpace = THREE.SRGBColorSpace;
document.body.prepend(renderer.domElement);
const scene = new THREE.Scene();
scene.add(new THREE.HemisphereLight(0xffffff, 0xa1b5b3, 2));
const sun = new THREE.DirectionalLight(0xffffff, 2); sun.position.set(-3, 5, -4); scene.add(sun);
const floor = new THREE.Mesh(new THREE.PlaneGeometry(100, 100), new THREE.MeshLambertMaterial({ color: 0xdce4e2 }));
floor.rotation.x = -Math.PI / 2; floor.position.y = -0.008; scene.add(floor);
const grid = new THREE.GridHelper(12, 24, 0xb6c4c1, 0xcdd8d5); grid.position.y = -0.005; scene.add(grid);
let loaded = 0;
const loader = new THREE.TextureLoader();
function loadTexture(url) {
  const texture = loader.load(url, () => { if (++loaded === 2) window.reviewReady = true; });
  texture.magFilter = THREE.NearestFilter; texture.minFilter = THREE.NearestFilter; texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}
let skinTexture = loadTexture(pack.textures.skin), equipmentTexture = loadTexture(pack.textures.equipment);
const players = Object.fromEntries(pack.models.slice(0, 2).map(m => [m.id.split('_').at(-1), createAsset(m, skinTexture)]));
for (const a of Object.values(players)) scene.add(a.root);
const inventory = Object.fromEntries(pack.models.slice(2).map(m => [m.id.split(':')[1], createAsset(m, equipmentTexture)]));
const display = new THREE.Group(); scene.add(display);
const displayAssets = pack.models.slice(2).map((m, i) => {
  const a = createAsset(m, equipmentTexture); a.root.position.set((i - 1.5) * 1.15, 0.5, 0); display.add(a.root); return a;
});
const skeleton = new THREE.LineSegments(new THREE.BufferGeometry(), new THREE.LineBasicMaterial({ color: 0x16766a, depthTest: false }));
skeleton.renderOrder = 10; scene.add(skeleton);
const camera = new THREE.PerspectiveCamera(37, 1, 0.01, 100);
const controls = new OrbitControls(camera, renderer.domElement); controls.enableDamping = true; controls.minDistance = 2; controls.maxDistance = 16;
const ortho = new THREE.OrthographicCamera(-2, 2, 2, -2, 0.01, 100);
let time = 0;
const parameters = new URLSearchParams(location.search);
$('profile').value = parameters.get('profile') || 'light_sword'; $('variant').value = parameters.get('variant') || 'classic';
$('view').value = parameters.get('view') || 'orbit'; $('play').checked = parameters.get('play') !== '0';
function clips() {
  return $('profile').value === 'great_sword' ? pack.variantGreatClips[`player_${$('variant').value}`] : pack.profiles.light_sword;
}
function populate() {
  const old = $('animation').value; $('animation').replaceChildren();
  for (const c of clips()) $('animation').add(new Option(c.id.replaceAll('_', ' '), c.id));
  $('animation').value = clips().some(c => c.id === old) ? old : 'idle';
}
function frameCamera() {
  const equipment = $('view').value === 'equipment';
  const overhead = !equipment && (['heavy_windup', 'heavy_release', 'charged_release'].includes($('animation').value) ||
    $('profile').value === 'great_sword' && ['light_1', 'light_2'].includes($('animation').value));
  const target = new THREE.Vector3(0, equipment ? 1.2 : overhead ? 1.55 : 1.05, 0);
  const position = new THREE.Vector3(...(equipment ? [-3.5, 2.5, -6.7] : overhead ? [-4.4, 3.4, -6.2] : [-3.4, 2.5, -4.4]));
  const aspect = innerWidth / (innerHeight - document.querySelector('header').offsetHeight);
  camera.position.copy(position.sub(target).multiplyScalar(Math.max(1, (equipment ? 1.05 : 0.8) / aspect)).add(target));
  controls.target.copy(target); controls.update();
}
populate(); $('animation').value = parameters.get('animation') || 'idle'; time = Number(parameters.get('tick') || 0); frameCamera();
$('profile').addEventListener('change', () => { populate(); time = 0; frameCamera(); });
$('animation').addEventListener('change', () => { time = 0; frameCamera(); });
$('view').addEventListener('change', frameCamera);
window.addEventListener('resize', frameCamera);
$('timeline').addEventListener('input', () => { $('play').checked = false; time = Number($('timeline').value); });
$('skin').addEventListener('change', async () => {
  const file = $('skin').files[0]; if (!file) return;
  const url = URL.createObjectURL(file), texture = await loader.loadAsync(url); URL.revokeObjectURL(url);
  if (texture.image.width !== 64 || texture.image.height !== 64) { texture.dispose(); $('status').textContent = 'Skin must be 64 x 64'; return; }
  texture.magFilter = THREE.NearestFilter; texture.minFilter = THREE.NearestFilter; texture.colorSpace = THREE.SRGBColorSpace;
  for (const player of Object.values(players)) for (const mesh of player.meshes) { mesh.material.map = texture; mesh.material.needsUpdate = true; }
  skinTexture.dispose(); skinTexture = texture; window.customSkinLoaded = true;
});
function applyPose() {
  const clip = clips().find(c => c.id === $('animation').value) || clips()[0];
  const tick = clip.loop ? time % clip.lengthTicks : Math.min(time, clip.lengthTicks);
  const asset = players[$('variant').value], equipmentView = $('view').value === 'equipment';
  for (const [variant, player] of Object.entries(players)) player.root.visible = variant === $('variant').value && !equipmentView;
  display.visible = equipmentView;
  asset.pose(clip, tick, pack.visualOffsets.clips[clip.id]?.positionTracks);
  for (const a of Object.values(inventory)) { a.root.removeFromParent(); a.root.visible = false; }
  const sword = inventory[$('profile').value === 'great_sword' ? 'great_sword' : 'straight_sword'];
  const visible = pack.presentation.visibility[clip.id] || pack.presentation.visibility.default;
  const greatGrip = $('profile').value === 'great_sword' && visible.includes('mainhand') && !clip.id.startsWith('guard') && clip.id !== 'parry';
  if (visible.includes('mainhand')) { asset.bones.get('sword_socket').add(sword.root); sword.root.visible = true; }
  if (visible.includes('offhand') && !greatGrip) { asset.bones.get('shield_socket').add(inventory.shield.root); inventory.shield.root.visible = true; }
  if (visible.includes('drink')) { asset.bones.get('drink_socket').add(inventory.healing_flask.root); inventory.healing_flask.root.visible = true; }
  asset.root.updateMatrixWorld(true);
  const gripError = greatGrip ? asset.bones.get('hand_l').getWorldPosition(new THREE.Vector3()).distanceTo(sword.root.localToWorld(new THREE.Vector3(0, -3 / 16, 0))) : null;
  skeleton.visible = $('bones').checked && !equipmentView;
  if (skeleton.visible) {
    const points = [];
    for (const b of pack.models[$('variant').value === 'slim' ? 1 : 0].bones) if (b.parent) {
      points.push(...asset.bones.get(b.parent).getWorldPosition(new THREE.Vector3()), ...asset.bones.get(b.name).getWorldPosition(new THREE.Vector3()));
    }
    skeleton.geometry.setAttribute('position', new THREE.Float32BufferAttribute(points, 3)); skeleton.geometry.computeBoundingSphere();
  }
  $('timeline').max = clip.lengthTicks; $('timeline').value = tick;
  $('status').textContent = `${$('profile').value} / ${clip.id} / ${Math.floor(tick)} : ${clip.lengthTicks}`;
  $('views').style.display = $('view').value === 'sheet' ? 'flex' : 'none';
  window.reviewState = { animation: clip.id, tick, profile: $('profile').value, variant: $('variant').value, bodyMinY: asset.bounds().min.y,
    gripError, visibleEquipment: Object.entries(inventory).filter(([, a]) => a.root.visible).map(([id]) => id),
    cubeCount: asset.meshes.length, boneCount: asset.bones.size, equipmentView };
}
function drawOrtho(direction, x, width, height) {
  const target = new THREE.Vector3(0, 1.05, 0), halfHeight = 1.8;
  ortho.left = -halfHeight * width / height; ortho.right = -ortho.left; ortho.top = halfHeight; ortho.bottom = -halfHeight;
  ortho.position.copy(target).add(new THREE.Vector3(...direction)); ortho.lookAt(target); ortho.updateProjectionMatrix();
  renderer.setViewport(x, 0, width, height); renderer.setScissor(x, 0, width, height); renderer.render(scene, ortho);
}
let last = performance.now();
function render(now) {
  const dt = Math.min((now - last) / 1000, 0.1); last = now;
  if ($('play').checked) time += dt * 20;
  applyPose();
  if (renderer.domElement.width !== Math.round(innerWidth * renderer.getPixelRatio()) || renderer.domElement.height !== Math.round(innerHeight * renderer.getPixelRatio())) renderer.setSize(innerWidth, innerHeight);
  const width = innerWidth, height = innerHeight - document.querySelector('header').offsetHeight;
  renderer.setScissorTest(true); renderer.setViewport(0, 0, width, innerHeight); renderer.setScissor(0, 0, width, innerHeight); renderer.clear();
  if ($('view').value === 'sheet') {
    controls.enabled = false;
    [[0, 0, -8], [-8, 0, 0], [0, 0, 8]].forEach((d, i) => drawOrtho(d, Math.floor(width * i / 3), Math.floor(width * (i + 1) / 3) - Math.floor(width * i / 3), height));
  } else {
    controls.enabled = true; controls.update(); camera.aspect = width / height; camera.updateProjectionMatrix();
    renderer.setViewport(0, 0, width, height); renderer.setScissor(0, 0, width, height); renderer.render(scene, camera);
    const box = $('view').value === 'equipment' ? new THREE.Box3().setFromObject(display, true) : players[$('variant').value].bounds();
    if ($('view').value !== 'equipment') for (const a of Object.values(inventory)) if (a.root.visible) box.union(new THREE.Box3().setFromObject(a.root, true));
    const corners = [];
    for (const x of [box.min.x, box.max.x]) for (const y of [box.min.y, box.max.y]) for (const z of [box.min.z, box.max.z]) corners.push(new THREE.Vector3(x, y, z).project(camera));
    window.reviewState.framing = { minX: Math.min(...corners.map(c => c.x)), maxX: Math.max(...corners.map(c => c.x)),
      minY: Math.min(...corners.map(c => c.y)), maxY: Math.max(...corners.map(c => c.y)) };
  }
  requestAnimationFrame(render);
}
requestAnimationFrame(render);
