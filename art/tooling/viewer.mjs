import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';

const pack = window.MODEL_PACK;
const model = pack.models[0];
const renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true });
renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
renderer.setClearColor(0xe8eeeb);
renderer.outputColorSpace = THREE.SRGBColorSpace;
document.body.prepend(renderer.domElement);
const scene = new THREE.Scene();
scene.add(new THREE.HemisphereLight(0xffffff, 0x8a9b90, 2.1));
const sun = new THREE.DirectionalLight(0xffffff, 2.3);
sun.position.set(-3, 6, -4); scene.add(sun);
const floor = new THREE.Mesh(new THREE.PlaneGeometry(100, 100), new THREE.MeshLambertMaterial({ color: 0xdce4df }));
floor.rotation.x = -Math.PI / 2; floor.position.y = -0.02; scene.add(floor);
const grid = new THREE.GridHelper(12, 24, 0xb4c3b9, 0xcbd6ce); grid.position.y = -0.012; scene.add(grid);
const texture = new THREE.TextureLoader().load(pack.texture, () => { window.reviewReady = true; });
texture.magFilter = THREE.NearestFilter; texture.minFilter = THREE.NearestFilter;
texture.colorSpace = THREE.SRGBColorSpace;
const surface = new THREE.MeshLambertMaterial({ map: texture });
const spectralSurface = new THREE.MeshLambertMaterial({ map: texture, emissive: 0xb68521, emissiveIntensity: 0.55 });
const unit = value => value / model.unitsPerBlock;
const vector = value => new THREE.Vector3(...value.map(unit));
const radians = values => values.map(THREE.MathUtils.degToRad);

function createAsset(data) {
  const root = new THREE.Group();
  const bones = new Map();
  for (const spec of data.bones) {
    const node = new THREE.Group(); node.name = spec.name;
    const parentSpec = data.bones.find(b => b.name === spec.parent);
    node.position.copy(vector(spec.pivot));
    if (parentSpec) node.position.sub(vector(parentSpec.pivot));
    node.rotation.set(...radians(spec.rotation), 'XYZ');
    bones.set(spec.name, node);
    (spec.parent ? bones.get(spec.parent) : root).add(node);
  }
  for (const spec of data.cubes) {
    const bone = data.bones.find(b => b.name === spec.bone);
    const size = vector(spec.to).sub(vector(spec.from));
    const center = vector(spec.from).add(vector(spec.to)).multiplyScalar(0.5);
    const geometry = new THREE.BoxGeometry(size.x, size.y, size.z);
    const uvs = geometry.attributes.uv;
    // BoxGeometry orders +/-X, +/-Y, +/-Z; the head's -Z face is the face tile.
    for (let face = 0; face < 6; face++) {
      const tile = data.materials[data.id === 'eldencombat:margit' && spec.name === 'head' && face === 5 ? 'face' : spec.material].uv;
      for (let vertex = 0; vertex < 4; vertex++) {
        const index = face * 4 + vertex, u = uvs.getX(index), v = uvs.getY(index);
        uvs.setXY(index, (tile[0] + 0.5 + u * (tile[2] - tile[0] - 1)) / 128, 1 - (tile[3] - 0.5 - v * (tile[3] - tile[1] - 1)) / 128);
      }
    }
    const mesh = new THREE.Mesh(geometry, spec.material === 'gold' ? spectralSurface : surface);
    mesh.name = spec.name;
    const pivot = vector(spec.pivot || spec.from);
    const holder = new THREE.Group();
    holder.position.copy(pivot).sub(vector(bone.pivot));
    holder.rotation.set(...radians(spec.rotation || [0, 0, 0]), 'XYZ');
    mesh.position.copy(center).sub(pivot); holder.add(mesh); bones.get(spec.bone).add(holder);
  }
  return { root, bones };
}

const asset = createAsset(model); scene.add(asset.root);
const accessories = Object.fromEntries(pack.models.slice(1).map(spec => {
  const item = createAsset(spec).root;
  item.visible = false;
  asset.bones.get('spectral_socket').add(item);
  return [spec.id.split(':')[1], item];
}));
const skeleton = new THREE.Group(); scene.add(skeleton);
const lines = new THREE.BufferGeometry();
const lineMesh = new THREE.LineSegments(lines, new THREE.LineBasicMaterial({ color: 0x166664, depthTest: false }));
lineMesh.renderOrder = 10; skeleton.add(lineMesh); skeleton.visible = false;
const bonePoints = new THREE.Points(new THREE.BufferGeometry(), new THREE.PointsMaterial({ color: 0xa22b45, size: 0.065, depthTest: false }));
bonePoints.renderOrder = 11; skeleton.add(bonePoints);

const camera = new THREE.PerspectiveCamera(38, 1, 0.01, 100);
camera.position.set(-4.8, 3.2, -6.2);
const controls = new OrbitControls(camera, renderer.domElement);
controls.target.set(0, 1.7, 0); controls.enableDamping = true;
controls.minDistance = 3; controls.maxDistance = 12; controls.update();
const ortho = new THREE.OrthographicCamera(-2.5, 2.5, 4, -1, 0.01, 100);
const viewSelect = document.getElementById('view');
const animationSelect = document.getElementById('animation');
for (const clip of model.animations) animationSelect.add(new Option(clip.id.replaceAll('_', ' '), clip.id));
document.getElementById('stats').textContent = `${model.cubes.length} Cuboids / ${model.bones.length} Bones / Tail 3`;
let clipTime = 0;
animationSelect.addEventListener('change', () => { clipTime = 0; });
document.getElementById('bones').addEventListener('change', event => { skeleton.visible = event.target.checked; });
const parameters = new URLSearchParams(location.search);
viewSelect.value = parameters.get('view') || 'orbit';
animationSelect.value = parameters.get('animation') || 'rest';
document.getElementById('play').checked = parameters.get('play') !== '0';
if (parameters.has('tick')) clipTime = Number(parameters.get('tick'));
function frameSelectedAnimation() {
  const overhead = ['hammer_slam', 'staff_slam'].includes(animationSelect.value);
  camera.position.set(...(overhead ? [-6.5, 4.5, -8.5] : [-4.8, 3.2, -6.2]));
  controls.target.set(0, overhead ? 2.8 : 1.7, 0);
  controls.update();
}
animationSelect.addEventListener('change', frameSelectedAnimation);
frameSelectedAnimation();

function sample(keys, tick) {
  if (tick <= keys[0].tick) return keys[0].value;
  for (let i = 1; i < keys.length; i++) if (tick <= keys[i].tick) {
    const a = keys[i - 1], b = keys[i], t = (tick - a.tick) / (b.tick - a.tick);
    return a.value.map((v, axis) => v + (b.value[axis] - v) * t);
  }
  return keys.at(-1).value;
}
function applyPose(tick) {
  const clip = model.animations.find(a => a.id === animationSelect.value);
  const frame = clip ? (clip.loop ? tick % clip.lengthTicks : Math.min(tick, clip.lengthTicks)) : 0;
  for (const bone of model.bones) {
    const delta = clip?.tracks[bone.name] ? sample(clip.tracks[bone.name], frame) : [0, 0, 0];
    asset.bones.get(bone.name).rotation.set(...radians(bone.rotation.map((v, i) => v + delta[i])), 'XYZ');
  }
  for (const item of Object.values(accessories)) item.visible = false;
  if (clip) for (const event of clip.events) {
    if (event.tick > frame) continue;
    if (event.type === 'equip') accessories[event.item].visible = true;
    if (event.type === 'unequip') for (const item of Object.values(accessories)) item.visible = false;
  }
  asset.root.updateMatrixWorld(true);
  if (skeleton.visible) {
    const segments = [], points = [];
    for (const bone of model.bones) {
      const end = asset.bones.get(bone.name).getWorldPosition(new THREE.Vector3()); points.push(...end);
      if (bone.parent) segments.push(...asset.bones.get(bone.parent).getWorldPosition(new THREE.Vector3()), ...end);
    }
    lines.setAttribute('position', new THREE.Float32BufferAttribute(segments, 3));
    bonePoints.geometry.setAttribute('position', new THREE.Float32BufferAttribute(points, 3));
    lines.computeBoundingSphere(); bonePoints.geometry.computeBoundingSphere();
  }
  document.getElementById('status').textContent = clip ? `${clip.id} / tick ${Math.floor(frame)}` : '';
  window.reviewState = { animation: clip?.id || 'rest', tick: frame, tailBones: model.bones.filter(b => b.name.startsWith('tail_')).length, cubeCount: model.cubes.length, visibleWeapons: Object.keys(accessories).filter(name => accessories[name].visible) };
}
function drawOrthographic(view, x, y, width, height) {
  const center = new THREE.Vector3(0, 1.7, 0.25);
  const halfHeight = 2.2;
  const halfWidth = halfHeight * width / height;
  ortho.left = -halfWidth; ortho.right = halfWidth; ortho.top = halfHeight; ortho.bottom = -halfHeight;
  ortho.position.copy(center).add(view === 'front' ? new THREE.Vector3(0, 0, -8) : view === 'back' ? new THREE.Vector3(0, 0, 8) : new THREE.Vector3(-8, 0, 0));
  ortho.lookAt(center); ortho.updateProjectionMatrix();
  renderer.setViewport(x, y, width, height); renderer.setScissor(x, y, width, height); renderer.render(scene, ortho);
}
let last = performance.now();
function render(now) {
  const elapsed = Math.min((now - last) / 1000, 0.1); last = now;
  if (document.getElementById('play').checked) clipTime += elapsed * 20;
  applyPose(clipTime);
  const width = innerWidth, height = innerHeight;
  if (renderer.domElement.clientWidth !== width || renderer.domElement.clientHeight !== height || renderer.domElement.width !== Math.round(width * renderer.getPixelRatio())) renderer.setSize(width, height);
  const top = document.querySelector('header').offsetHeight;
  const availableHeight = height - top;
  renderer.setScissorTest(true); renderer.setViewport(0, 0, width, height); renderer.setScissor(0, 0, width, height); renderer.clear();
  document.getElementById('views').style.display = viewSelect.value === 'sheet' ? 'flex' : 'none';
  if (viewSelect.value === 'orbit') {
    controls.enabled = true; controls.update();
    camera.aspect = width / availableHeight; camera.updateProjectionMatrix();
    renderer.setViewport(0, 0, width, availableHeight); renderer.setScissor(0, 0, width, availableHeight); renderer.render(scene, camera);
  } else if (viewSelect.value === 'sheet') {
    controls.enabled = false;
    const colWidth = Math.floor(width / 3);
    ['front', 'side', 'back'].forEach((view, index) => drawOrthographic(view, index * colWidth, 0, index === 2 ? width - index * colWidth : colWidth, availableHeight));
  } else {
    controls.enabled = false; drawOrthographic(viewSelect.value, 0, 0, width, availableHeight);
  }
  requestAnimationFrame(render);
}
requestAnimationFrame(render);
