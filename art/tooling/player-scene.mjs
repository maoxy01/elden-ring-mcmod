import * as THREE from 'three';
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js';

export const sample = (keys, tick) => {
  if (tick <= keys[0].tick) return keys[0].value;
  for (let i = 1; i < keys.length; i++) if (tick <= keys[i].tick) {
    const a = keys[i - 1], b = keys[i], t = (tick - a.tick) / (b.tick - a.tick);
    return a.value.map((v, axis) => v + (b.value[axis] - v) * t);
  }
  return keys.at(-1).value;
};

export function createAsset(data, texture) {
  const root = new THREE.Group(), bones = new Map(), meshes = [];
  const vec = a => new THREE.Vector3(...a.map(n => n / data.unitsPerBlock));
  const radians = a => a.map(THREE.MathUtils.degToRad);
  for (const b of data.bones) {
    const node = new THREE.Group(), parent = data.bones.find(p => p.name === b.parent);
    node.name = b.name;
    node.position.copy(vec(b.pivot));
    if (parent) node.position.sub(vec(parent.pivot));
    node.rotation.set(...radians(b.rotation), 'XYZ');
    bones.set(b.name, node); (b.parent ? bones.get(b.parent) : root).add(node);
  }
  const material = new THREE.MeshLambertMaterial(texture ? { map: texture } : { color: 0x888888 });
  if(data.bakedMesh) {
    const baked=data.bakedMesh,geometry=new THREE.BufferGeometry();
    geometry.setAttribute('position',new THREE.Float32BufferAttribute(baked.positions,3));
    geometry.setAttribute('normal',new THREE.Float32BufferAttribute(baked.normals,3));
    geometry.setAttribute('uv',new THREE.Float32BufferAttribute(baked.uvs,2));
    geometry.setIndex(baked.indices);geometry.computeBoundingBox();geometry.computeBoundingSphere();
    const mesh=new THREE.Mesh(geometry,material);bones.get(baked.bone).add(mesh);meshes.push(mesh);
  }
  for (const c of data.bakedMesh?[]:data.cubes) {
    const geometry = new THREE.BoxGeometry(...vec(c.to).sub(vec(c.from)).toArray());
    const uv = geometry.attributes.uv;
    for (const [face, direction] of ['east', 'west', 'up', 'down', 'south', 'north'].entries()) {
      const rect = data.faceUVs?.[c.name]?.[direction] || data.skinFaces?.[c.name]?.[direction] || data.materials[c.material].uv;
      const insetU=Math.min(.5,(rect[2]-rect[0])/3),insetV=Math.min(.5,(rect[3]-rect[1])/3);
      for (let vertex = 0; vertex < 4; vertex++) {
        const i = face * 4 + vertex, u = uv.getX(i), v = uv.getY(i);
        uv.setXY(i, (rect[0] + insetU + u * (rect[2] - rect[0] - insetU*2)) / data.textureSize[0],
          1 - (rect[3] - insetV - v * (rect[3] - rect[1] - insetV*2)) / data.textureSize[1]);
      }
    }
    const owner = data.bones.find(b => b.name === c.bone), pivot = vec(c.pivot || c.from);
    const holder = new THREE.Group(); holder.position.copy(pivot).sub(vec(owner.pivot));
    holder.rotation.set(...radians(c.rotation || [0, 0, 0]), 'XYZ');
    const mesh = new THREE.Mesh(geometry, material); mesh.name = c.name;
    mesh.position.copy(vec(c.from).add(vec(c.to)).multiplyScalar(0.5).sub(pivot));
    holder.add(mesh); bones.get(c.bone).add(holder); meshes.push(mesh);
  }
  if(data.batchPreview&&!data.bakedMesh) {
    const merged=[];
    for(const node of bones.values()) {
      const owned=meshes.filter(mesh=>mesh.parent.parent===node);
      if(!owned.length)continue;
      const geometries=owned.map(mesh=> {
        mesh.parent.updateMatrix();mesh.updateMatrix();
        return mesh.geometry.clone().applyMatrix4(mesh.parent.matrix.clone().multiply(mesh.matrix));
      });
      const geometry=mergeGeometries(geometries,false);
      geometries.forEach(g=>g.dispose());
      for(const mesh of owned){node.remove(mesh.parent);mesh.geometry.dispose();}
      const mesh=new THREE.Mesh(geometry,material);node.add(mesh);merged.push(mesh);
    }
    meshes.splice(0,meshes.length,...merged);
  }
  function pose(clip, tick, positionTracks) {
    for (const b of data.bones) {
      const node = bones.get(b.name), parent = data.bones.find(p => p.name === b.parent);
      const delta = clip?.tracks[b.name] ? sample(clip.tracks[b.name], tick) : [0, 0, 0];
      node.rotation.set(...radians(b.rotation.map((n, i) => n + delta[i])), 'XYZ');
      node.position.copy(vec(b.pivot));
      if (parent) node.position.sub(vec(parent.pivot));
      if (positionTracks?.[b.name]) node.position.add(vec(sample(positionTracks[b.name], tick)));
    }
    root.updateMatrixWorld(true);
  }
  const bounds = () => meshes.reduce((box, mesh) => box.union(new THREE.Box3().setFromObject(mesh, true)), new THREE.Box3());
  return { root, bones, meshes, pose, bounds };
}

// Presentation-only two-bone IK. Hit sampling still uses the authored right arm.
export function gripLeftHand(asset, sword) {
  asset.root.updateMatrixWorld(true);
  const upper = asset.bones.get('upper_arm_l'), fore = asset.bones.get('forearm_l'), hand = asset.bones.get('hand_l');
  const shoulder = upper.getWorldPosition(new THREE.Vector3());
  const target = sword.localToWorld(new THREE.Vector3(0, -3 / 16, 0));
  const l1 = fore.position.length(), l2 = hand.position.length();
  const direction = target.clone().sub(shoulder), rawDistance = direction.length();
  const distance = THREE.MathUtils.clamp(rawDistance, Math.abs(l1 - l2) + 0.0001, l1 + l2 - 0.0001);
  direction.normalize();
  const pole = new THREE.Vector3(0, 0, -1).applyQuaternion(asset.bones.get('torso').getWorldQuaternion(new THREE.Quaternion()));
  pole.addScaledVector(direction, -pole.dot(direction)).normalize();
  const along = (l1 * l1 + distance * distance - l2 * l2) / (2 * distance);
  const elbow = shoulder.clone().addScaledVector(direction, along).addScaledVector(pole, Math.sqrt(Math.max(0, l1 * l1 - along * along)));
  const down = new THREE.Vector3(0, -1, 0);
  upper.quaternion.copy(upper.parent.getWorldQuaternion(new THREE.Quaternion()).invert())
    .multiply(new THREE.Quaternion().setFromUnitVectors(down, elbow.clone().sub(shoulder).normalize()));
  asset.root.updateMatrixWorld(true);
  const reachable = shoulder.clone().addScaledVector(direction, distance);
  fore.quaternion.copy(fore.parent.getWorldQuaternion(new THREE.Quaternion()).invert())
    .multiply(new THREE.Quaternion().setFromUnitVectors(down, reachable.sub(elbow).normalize()));
  asset.root.updateMatrixWorld(true);
  return hand.getWorldPosition(new THREE.Vector3()).distanceTo(target);
}
