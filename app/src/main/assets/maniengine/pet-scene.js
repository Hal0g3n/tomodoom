// The pet's scene, shared by index.html and tools/maniengine/thumbnails.html so the item thumbnails
// show exactly what the pet wears. Add the camera to `scene` first: override child paths count it.
import { buildModel, OVERRIDES } from './pet-model.js';

export function buildPetScene(THREE, scene, renderer) {
  buildModel(THREE, scene, renderer);
  scene.traverse(o => { if (o.isDirectionalLight) o.shadow.mapSize.set(1024, 1024); });  // phone-sized

  // Editor overrides (the export's live-edit table): top-level objects by unique name, else child index.
  for (const o of OVERRIDES) {
    if (String(o.path).includes('/')) { console.warn('skipping nested override', o.name); continue; }
    const matches = scene.children.filter(c => c.name === o.name);
    const target = matches.length === 1 ? matches[0] : scene.children[+o.path];
    if (!target || target.name !== o.name) { console.warn('unmatched override', o.name); continue; }
    if (o.p) { target.position.fromArray(o.p); target.rotation.fromArray(o.r); target.scale.fromArray(o.s); }
    if (o.v !== undefined) target.visible = o.v;
  }

  // Drop the hidden room; group what's left into rug + rabbit (pivot at its feet) + head (pivot at neck).
  scene.children.filter(o => o.isMesh && !o.visible).forEach(o => scene.remove(o));
  const isRug = o => /rug/i.test(o.name);
  const isHead = o => /head|ear|eye|muzzle|nose|mouth|whisker|smile/i.test(o.name);
  const meshes = scene.children.filter(o => o.isMesh && !isRug(o));
  const box = (list) => list.reduce((b, o) => b.expandByObject(o), new THREE.Box3());
  const named = (n) => scene.getObjectByName(n);

  const rabbitBox = box(meshes), headBox = box([named('Rabbit cream flocked head')]);
  const rabbit = new THREE.Group(), head = new THREE.Group();
  rabbit.position.set(0, rabbitBox.min.y, rabbitBox.getCenter(new THREE.Vector3()).z);
  head.position.set(0, headBox.min.y + 0.1, rabbit.position.z);
  scene.add(rabbit, head);
  for (const m of meshes) (isHead(m) ? head : rabbit).attach(m);
  rabbit.attach(head);

  // --- Add-ons for the AccessoryCatalog item IDs, built once in world space, then toggled ---
  const mat = (color, extra) => new THREE.MeshStandardMaterial({ color, roughness: .7, ...extra });
  const mesh = (geo, m, x, y, z) => { const o = new THREE.Mesh(geo, m); o.position.set(x, y, z); o.castShadow = true; return o; };
  const top = headBox.max.y, hz = headBox.getCenter(new THREE.Vector3()).z;
  const eyes = ['Left glossy dark eye', 'Right glossy dark eye'].map(n => named(n).getWorldPosition(new THREE.Vector3()));
  const body = box([named('Rabbit flocked body')]);
  function addOn(parent, ...parts) { const g = new THREE.Group(); g.add(...parts); g.visible = false; parent.attach(g); return g; }

  const capMat = mat(0x6f8fb8), beanieMat = mat(0xd891a9, { roughness: 1 }), gold = mat(0xe3c06b, { metalness: .6, roughness: .3 });
  const ADDONS = {
    hat_cap: addOn(head,
      mesh(new THREE.SphereGeometry(.3, 32, 16, 0, Math.PI * 2, 0, Math.PI / 2), capMat, 0, top - .1, hz),
      mesh(new THREE.CylinderGeometry(.3, .3, .02, 32, 1, false, -Math.PI / 2, Math.PI), capMat, 0, top - .1, hz + .12)),
    hat_beanie: addOn(head,
      mesh(new THREE.SphereGeometry(.34, 32, 16, 0, Math.PI * 2, 0, Math.PI / 2), beanieMat, 0, top - .14, hz),
      mesh(new THREE.SphereGeometry(.08, 16, 12), mat(0xfffcf3, { roughness: 1 }), 0, top + .2, hz)),
    hat_crown: addOn(head,
      mesh(new THREE.CylinderGeometry(.2, .2, .14, 5, 1, true), gold, 0, top + .02, hz),
      ...[0, 1, 2, 3, 4].map(i => mesh(new THREE.ConeGeometry(.05, .12, 8), gold,
        Math.sin(i * 1.2566) * .2, top + .15, hz + Math.cos(i * 1.2566) * .2))),
    acc_glasses: addOn(head,
      ...eyes.map(e => mesh(new THREE.TorusGeometry(.1, .012, 8, 32), mat(0x383a3d), e.x, e.y, e.z + .07)),
      mesh(new THREE.CylinderGeometry(.01, .01, Math.abs(eyes[1].x - eyes[0].x) - .2, 8).rotateZ(Math.PI / 2),
        mat(0x383a3d), 0, eyes[0].y, eyes[0].z + .08)),
    acc_scarf: addOn(rabbit,
      mesh(new THREE.TorusGeometry(.36, .07, 12, 48).rotateX(Math.PI / 2), mat(0xc7604f, { roughness: 1 }), 0, headBox.min.y + .08, hz)),
    acc_backpack: addOn(rabbit,
      mesh(new THREE.BoxGeometry(.5, .55, .22), mat(0x8ca17a, { roughness: .9 }), 0, (body.min.y + body.max.y) / 2, body.min.z - .02)),
  };

  return { rabbit, head, rabbitBox, ADDONS };
}
