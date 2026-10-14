const path = require('node:path');
const fs = require('node:fs/promises');
const assert = require('node:assert/strict');
const { createRequire } = require('node:module');
const { pathToFileURL } = require('node:url');
const dependencies = process.argv[2] ? createRequire(path.join(process.argv[2], 'package.json')) : require;
const { chromium } = dependencies('playwright');
const { PNG } = dependencies('pngjs');
const game=process.argv.includes('--game-remembrance');
const remembrance=game||process.argv.includes('--remembrance'),count=remembrance?8:33,size=remembrance?2048:512;
const out = path.resolve(__dirname, game?'../remembrance-game':remembrance?'../remembrance':'../weapons');

function ink(bytes) {
  const png = PNG.sync.read(bytes);
  let count = 0;
  for (let i = 0; i < png.data.length; i += 4)
    if (png.data[i] < 205 && png.data[i + 1] < 215 && png.data[i + 2] < 220) count++;
  return count;
}
function difference(a, b) {
  const first = PNG.sync.read(a), second = PNG.sync.read(b);
  assert.equal(first.data.length, second.data.length);
  let count = 0;
  for (let i = 0; i < first.data.length; i += 4)
    if (Math.abs(first.data[i]-second.data[i])+Math.abs(first.data[i+1]-second.data[i+1])+Math.abs(first.data[i+2]-second.data[i+2])>30) count++;
  return count;
}
function framed(bytes, id) {
  const png=PNG.sync.read(bytes);
  for(let y=0;y<png.height;y++)for(let x=0;x<png.width;x++) {
    if(x>=5&&x<png.width-5&&y>=5&&y<png.height-5)continue;
    const i=(y*png.width+x)*4;
    assert(!(png.data[i]<205&&png.data[i+1]<215&&png.data[i+2]<220),`${id}: model reaches canvas edge`);
  }
}

(async () => {
  const {validateStructure}=await import('./weapon-structure.mjs');
  const {connectedComponents}=await import('./weapon-connectivity.mjs');
  const {validateBakedMesh}=await import('./weapon-mesh-validation.mjs');
  const structuralResults={};
  const previews = path.join(out, 'previews');
  await fs.mkdir(previews, { recursive: true });
  const catalog = JSON.parse(await fs.readFile(path.join(out, 'catalog.json')));
  assert.equal(catalog.weapons.length, count);
  const atlas = PNG.sync.read(await fs.readFile(path.join(out, 'weapons.png')));
  assert.equal(atlas.width, size); assert.equal(atlas.height, size);
  const skillDesign=JSON.parse(await fs.readFile(path.join(out,'weapon-arts.design.json')));
  assert.equal(skillDesign.executions,false);assert.equal(skillDesign.skillJumpAttacksAllowed,true);
  assert.equal(skillDesign.specialJumpAttacks,true);
  let smoothModels=0;
  for (const w of catalog.weapons) {
    const model = JSON.parse(await fs.readFile(path.join(out, w.model)));
    const bb = JSON.parse(await fs.readFile(path.join(out, w.blockbench)));
    if(remembrance) {
      if(!game)assert(w.cubes >= w.previousCubes*4,`${w.id}: insufficient refinement`);
      else {
        assert(w.cubes<=1200&&w.geometryReduction>=.8,`${w.id}: game geometry budget`);
        assert.equal(connectedComponents(model).length,1,`${w.id}: floating geometry`);
        const detached=structuredClone(model);detached.cubes[0].from[0]+=500;detached.cubes[0].to[0]+=500;
        detached.cubes[0].pivot[0]+=500;
        assert(connectedComponents(detached).length>1,'Connectivity negative test must detect a detached part');
        const mesh=JSON.parse(await fs.readFile(path.join(out,w.mesh)));
        validateBakedMesh(mesh);assert.equal(mesh.triangleCount,w.triangles);
        assert.throws(()=>validateBakedMesh({...mesh,indices:[...mesh.indices,mesh.vertexCount+1,0,1]}));
      }
      assert.equal(model.cubes.length,w.cubes);
      assert(!w.defaultArt.endsWith('_grounded'));
      assert(skillDesign.arts.some(a=>'eldencombat:'+a.id===w.defaultArt));
      assert(model.structuralChecks?.length);
      structuralResults[w.id]=validateStructure(model);
      if(['malikeths_black_blade','blasphemous_blade','starscourge_greatsword','morgotts_cursed_sword'].some(id=>w.id.endsWith(':'+id))) {
        assert.throws(()=>validateStructure({...model,cubes:model.cubes.filter(c=>!c.name.startsWith('structural_tang_')&&!c.name.startsWith('guard_socket_'))}),
          /open joint/,'Joint regression test must fail when connector geometry is removed');
      }
    }
    const smooth=game&&!!model.smoothSurfaces?.length;
    if(smooth)smoothModels++;
    assert.equal(bb.elements.length, model.cubes.length+(smooth?1:0));
    const exported = new Map(bb.elements.map(c => [c.name, c]));
    for (const cube of model.cubes) {
      const element = exported.get(cube.name); assert(element);
      if(smooth){assert.equal(element.visibility,false);assert.equal(element.export,false);}
      assert.deepEqual(element.from, cube.from); assert.deepEqual(element.to, cube.to);
      for (const face of ['north', 'east', 'south', 'west', 'up', 'down']) {
        const uv = model.faceUVs?.[cube.name]?.[face] || model.materials[cube.material].uv;
        assert(uv.length === 4 && uv.every(Number.isFinite));
        assert(uv[0] >= 0 && uv[1] >= 0 && uv[2] <= size && uv[3] <= size);
        assert(uv[0] < uv[2] && uv[1] < uv[3]);
        assert.deepEqual(element.faces[face].uv, uv, `${w.id}/${cube.name}/${face}: UV mismatch`);
      }
    }
    const elements = new Set(bb.elements.map(c => c.uuid)), groups = new Set(bb.groups.map(g => g.uuid)), seen = new Set();
    const walk = entries => entries.forEach(entry => {
      if (typeof entry === 'string') { assert(elements.has(entry)); assert(!seen.has(entry)); seen.add(entry); }
      else { assert(groups.has(entry.uuid)); walk(entry.children); }
    });
    walk(bb.outliner); assert.equal(seen.size, bb.elements.length);
    if(smooth) {
      const mesh=JSON.parse(await fs.readFile(path.join(out,w.mesh))),element=exported.get('continuous_game_mesh');
      assert.equal(element.type,'mesh');assert.equal(element.visibility,true);assert.equal(element.export,true);
      assert.equal(Object.keys(element.vertices).length,mesh.vertexCount);
      assert.equal(Object.keys(element.faces).length,mesh.triangleCount);
      for(let i=0;i<mesh.vertexCount;i++)assert.deepEqual(element.vertices[`v${i}`],mesh.positions.slice(i*3,i*3+3).map(p=>p*16));
      for(let i=0;i<mesh.triangleCount;i++) {
        const face=element.faces[`f${i}`],ids=mesh.indices.slice(i*3,i*3+3);
        assert.deepEqual(face.vertices,ids.map(j=>`v${j}`));
        for(const j of ids)assert.deepEqual(face.uv[`v${j}`],[mesh.uvs[j*2]*size,(1-mesh.uvs[j*2+1])*size]);
      }
    }
    for (const animation of bb.animations) for (const id of Object.keys(animation.animators)) assert(groups.has(id));
    for (const bone of model.bones) {
      const ancestors = new Set([bone.name]); let parent = bone.parent;
      while (parent) {
        assert(!ancestors.has(parent), `${w.id}: bone cycle`); ancestors.add(parent);
        const node = model.bones.find(b => b.name === parent); assert(node); parent = node.parent;
      }
    }
    const embedded = PNG.sync.read(Buffer.from(bb.textures[0].source.split(',')[1], 'base64'));
    assert.equal(embedded.width, size);
    assert.equal(embedded.height, size); assert.deepEqual(embedded.data, atlas.data);
  }
  const errors = [], samples = {};
  const browser = await chromium.launch({ channel: 'msedge', headless: true,
    args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--allow-file-access-from-files'] });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1 });
    page.on('pageerror', e => errors.push(e.message));
    await page.goto(pathToFileURL(path.join(out, 'preview.html')).href);
    await page.waitForFunction(() => window.WEAPONS_QA?.textureLoaded);
    await page.waitForTimeout(150);
    assert.equal(await page.locator('.label').count(), count);
    assert(ink(await page.locator('canvas').screenshot()) > 25000, 'Blank gallery');
    await page.screenshot({ path: path.join(previews, 'weapon-sheet.png'), fullPage: true });
    for(const w of catalog.weapons) {
      await page.selectOption('#weapon',w.id.split(':')[1]);await page.waitForTimeout(40);
      const frame=await page.locator('canvas').screenshot();
      assert(ink(frame)>500,`${w.id}: missing geometry`);framed(frame,w.id);
    }
    for (const id of remembrance?catalog.weapons.map(w=>w.id.split(':')[1]):['katana', 'halberd', 'sacred_seal', 'flail', 'long_bow']) {
      await page.selectOption('#weapon', id);
      await page.waitForTimeout(100);
      const frame = await page.locator('canvas').screenshot();
      samples[id] = ink(frame); assert(samples[id] > 1200, `${id}: blank model`);
      await page.screenshot({ path: path.join(previews, `${id}.png`), fullPage: true });
    }
    let movingPixels=null;
    await page.selectOption('#weapon',remembrance?'sacred_relic_sword':'flail');
    if(!remembrance) {
      await page.locator('#mechanical').check();
      const a = await page.locator('canvas').screenshot(); await page.waitForTimeout(410);
      movingPixels = difference(a, await page.locator('canvas').screenshot());
      assert(movingPixels > 800, 'Mechanical motion not visible');
      await page.locator('#mechanical').uncheck();
    }
    const before = await page.locator('canvas').screenshot();
    const box = await page.locator('canvas').boundingBox();
    await page.mouse.move(box.x+box.width/2, box.y+box.height/2); await page.mouse.down();
    await page.mouse.move(box.x+box.width/2+170, box.y+box.height/2+70, { steps: 12 }); await page.mouse.up();
    await page.waitForTimeout(150);
    const rotatedPixels = difference(before, await page.locator('canvas').screenshot());
    assert(rotatedPixels > 800, 'Orbit interaction did not change image');
    await page.locator('#bounds').check(); await page.waitForTimeout(80);
    await page.screenshot({ path: path.join(previews, remembrance?'sacred-relic-bounds.png':'flail-bounds.png'), fullPage: true });
    await page.locator('#bounds').uncheck();
    if(remembrance) {
      for(const [id,part] of [['sacred_relic_sword','grip'],['sacred_relic_sword','blade'],
        ['malikeths_black_blade','grip'],['blasphemous_blade','grip'],['blasphemous_blade','blade'],
        ['starscourge_greatsword','grip'],['starscourge_greatsword','blade'],['morgotts_cursed_sword','grip'],['hand_of_malenia','grip']]) {
        await page.selectOption('#weapon',id);await page.selectOption('#focus',part);await page.waitForTimeout(100);
        assert(ink(await page.locator('canvas').screenshot())>2000,`${id}/${part}: empty close-up`);
        assert(await page.locator('#labels').isHidden(),'Close-up labels overlap geometry');
        await page.screenshot({path:path.join(previews,`${id}-${part}-closeup.png`),fullPage:true});
      }
    }
    await page.setViewportSize({ width: 390, height: 844 }); await page.selectOption('#weapon', 'all');
    await page.waitForTimeout(120);
    assert.equal(await page.evaluate(() => window.WEAPONS_QA.columns), 2);
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'Mobile horizontal overflow');
    assert(await page.locator('header').evaluate(h => Array.from(h.children).every(e => {
      const r = e.getBoundingClientRect(), p = h.getBoundingClientRect();
      return r.left >= 0 && r.right <= innerWidth && r.bottom <= p.bottom;
    })), 'Mobile toolbar overflow');
    await page.screenshot({ path: path.join(previews, 'mobile-sheet.png'), fullPage: true });
    for(const w of catalog.weapons) {
      await page.selectOption('#weapon',w.id.split(':')[1]);await page.waitForTimeout(40);
      const frame=await page.locator('canvas').screenshot();
      assert(ink(frame)>300,`${w.id}: mobile missing geometry`);framed(frame,'mobile/'+w.id);
    }
    await page.selectOption('#weapon', remembrance?'sacred_relic_sword':'great_shield'); await page.waitForTimeout(100);
    assert(ink(await page.locator('canvas').screenshot()) > 1500, 'Mobile model missing');
    await page.screenshot({ path: path.join(previews, remembrance?'mobile-sacred-relic.png':'mobile-shield.png'), fullPage: true });
    assert.deepEqual(errors, []);
    await page.setViewportSize({width:1440,height:1000});await page.selectOption('#weapon','detail');
    await page.waitForTimeout(100);
    assert.equal(await page.locator('.label').count(),remembrance?4:6);
    await page.screenshot({path:path.join(previews,'detail-sheet.png'),fullPage:true});
    const report = { status: 'passed', models: count, blockbench: game?(smoothModels?'visible continuous mesh matches runtime positions, indices and UVs; hidden cuboid fallback and all references verified':'visible cuboids and face UVs match runtime mesh; all references verified'):'all cube and animator references resolved',
      png: `${size}x${size} RGBA decoded`, desktop: `1440x900, all ${count} initial views framed`, mobile: `390x844, all ${count} framed, no horizontal overflow`,
      samples, movingPixels, rotatedPixels, errors, gameplayVerified: false };
    report.executionsAllowed=false;report.skillJumpAttacksAllowed=true;
    if(remembrance) {
      report.structuralChecks=structuralResults;
      if(!game)report.minimumGeometryMultiplier=Math.min(...catalog.weapons.map(w=>w.geometryMultiplier));
      else {
        report.profile='game';report.minimumGeometryReduction=Math.min(...catalog.weapons.map(w=>w.geometryReduction));
        report.maximumCubes=Math.max(...catalog.weapons.map(w=>w.cubes));
        report.connectedComponentsPerModel=1;report.bakedMeshValidation='indices, normals, UVs and nondegenerate triangles passed';
      }
      report.closeupViews='grip and blade rendered; captions do not cover geometry';
    }
    await fs.writeFile(path.join(out, 'preview-validation.json'), JSON.stringify(report, null, 2)+'\n');
    console.log(JSON.stringify(report, null, 2));
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
