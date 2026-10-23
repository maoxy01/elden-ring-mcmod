(async () => {
  const root = 'D:/codexout/mcmod/build/elden-source/blockbench-skills-20261021-arm70';
  const owned = new Set([globalThis.EldenSourceValidationProject]);
  if (!Project || !owned.has(Project.uuid)) throw Error('Select the Codex-owned validation project first');
  const existing = ModelProject.all.filter(p => !owned.has(p.uuid)).map(p => ({uuid:p.uuid,name:p.name,saved:p.saved}));
  const read = async path => {
    const file = await new Promise(resolve => Blockbench.read([path], {readtype:'text'}, files => resolve(files[0])));
    return JSON.parse(file.content);
  };
  const write = async (path, content) => new Promise(resolve => Blockbench.writeFile(path, {content}, resolve));
  const report = await read(root + '/conversion-report.json');
  const expected = await read(root + '/native-expected.json');
  const rows = [];
  const closeOwned = async () => {
    if (!Project || !owned.has(Project.uuid)) throw Error('Refusing to close an unowned project');
    Project.saved = true;
    await Project.close();
  };
  const openOwned = async asset => {
    newProject(Formats.free);
    if (existing.some(p => p.uuid === Project.uuid)) throw Error('New tab unexpectedly reused an existing project');
    const id = Project.uuid;
    owned.add(id);
    const json = await read(asset.path);
    if (Project?.uuid !== id || Cube.all.length || Group.all.length) throw Error('Owned empty project changed');
    Undo.initEdit({elements:[],outliner:true,animations:[],textures:[]});
    Codecs.project.parse(json, asset.path);
    Undo.finishEdit('Import motion into owned empty tab', {elements:Outliner.elements,outliner:true,animations:Animation.all,textures:Texture.all});
    await Promise.all(Texture.all.map(t => t.img.decode()));
    Project.save_path = asset.path;
    Project.saved = true;
    Canvas.updateAll();
    Modes.options.animate.select();
    return id;
  };
  try {
    await closeOwned();
    for (const asset of report.projects) {
      const id = await openOwned(asset);
      const ownGroups = new Map(Group.all.map(g => [g.name,g]));
      let count=0,midpoints=0,maxRotation=0,maxPosition=0,positionMismatch=null;
      for (const [clip, wanted] of Object.entries(expected).filter(([,v]) => v.skill === asset.skill)) {
        const animation = Animation.all.find(a => a.name === wanted.animation);
        if (!animation) throw Error('Missing native animation: '+clip);
        animation.select();
        for (const frame of wanted.frames) {
          if (Project?.uuid !== id) throw Error('Active tab changed during validation');
          Timeline.setTime(frame.time); Animator.preview();
          const actor=ownGroups.get('Minecraft六关节候选');
          const ae=new THREE.Matrix4().makeRotationFromEuler(actor.mesh.rotation).elements;
          const ar=[[ae[0],ae[4],ae[8]],[ae[1],ae[5],ae[9]],[ae[2],ae[6],ae[10]]];
          for(let r=0;r<3;r++)for(let c=0;c<3;c++)maxRotation=Math.max(maxRotation,Math.abs(ar[r][c]-frame.actorRotation[r][c]));
          for (const [name, matrix] of Object.entries(frame.rotations)) {
            const g = ownGroups.get('MC/'+name);
            const animator = animation.animators[g.uuid];
            if (!animator.quaternion_interpolation) throw Error('Quaternion interpolation lost');
            const e = new THREE.Matrix4().makeRotationFromEuler(g.mesh.rotation).elements;
            const actual = [[e[0],e[4],e[8]],[e[1],e[5],e[9]],[e[2],e[6],e[10]]];
            for(let r=0;r<3;r++) for(let c=0;c<3;c++) maxRotation=Math.max(maxRotation,Math.abs(actual[r][c]-matrix[r][c]));
            count++;
          }
          for(const [name,position] of Object.entries(frame.referencePositions)) {
            const actual = ownGroups.get('Source/'+name).mesh.position.toArray();
            for(let axis=0;axis<3;axis++) {
              const error=Math.abs(actual[axis]-position[axis]);
              if(error>maxPosition){maxPosition=error;positionMismatch={clip,time:frame.time,name,actual,expected:position};}
            }
          }
          if(frame.kind === 'quaternion_midpoint') midpoints++;
        }
      }
      if(maxRotation>2e-6 || maxPosition>.01501) throw Error(asset.skill+': native transform differs '+JSON.stringify({maxRotation,maxPosition,positionMismatch}));
      const compiled = Codecs.project.compile({raw:true});
      const binding=compiled.unhandled_root_fields.elden_source_motion;
      if(compiled.animations.length!==asset.animations || binding.speed!==1.3 || binding.waistGain!==.6 || binding.armAmplitudeControl!==.7 || !binding.actorHeadingSeparate) throw Error('Roundtrip lost source binding');
      await write(root+'/native-roundtrip/'+asset.skill+'.bbmodel',JSON.stringify(compiled));
      Animation.all[0].select();Timeline.setTime(Animation.all[0].length*.45);Animator.preview();
      const view=Preview.all.find(p=>p.id==='mcp_offscreen_source_skills_13');
      if(!view) throw Error('Private capture viewport missing');
      // New projects reset preview cameras, including private views.
      view.setProjectionMode(false);view.setLockedAngle(null);
      view.camera.position.set(45,35,-90);view.controls.target.set(-6,18,0);
      view.camera.fov=40;view.camera.updateProjectionMatrix();view.controls.update();
      const png=await new Promise(resolve=>view.screenshot({crop:false},resolve));
      await new Promise(resolve=>Blockbench.writeFile(root+'/previews/'+asset.skill+'.png',{content:png,savetype:'image'},resolve));
      rows.push({skill:asset.skill,clips:asset.animations,nativeRotationComparisons:count,nativeQuaternionMidpoints:midpoints,maxRotationComponentError:maxRotation,maxReferencePositionComponentError:maxPosition,compiled:true,captureCamera:view.camera.position.toArray()});
      await closeOwned();
    }
    // Reopen the native compiler's output, exercising a real file roundtrip.
    const asset={skill:'wave_of_gold',path:root+'/native-roundtrip/wave_of_gold.bbmodel'};
    await openOwned(asset);
    const animation=Animation.all[0];animation.select();
    const sample=expected.a820_040000.frames.find(f=>f.kind==='quaternion_midpoint');
    Timeline.setTime(sample.time);Animator.preview();
    const arm=Group.all.find(g=>g.name==='MC/rightArm');
    if(!animation.animators[arm.uuid].quaternion_interpolation)throw Error('Reopen lost quaternion interpolation');
    const matrix=new THREE.Matrix4().makeRotationFromEuler(arm.mesh.rotation).elements;
    const actual=[[matrix[0],matrix[4],matrix[8]],[matrix[1],matrix[5],matrix[9]],[matrix[2],matrix[6],matrix[10]]];
    for(let r=0;r<3;r++)for(let c=0;c<3;c++)if(Math.abs(actual[r][c]-sample.rotations.rightArm[r][c])>2e-6)throw Error('Reopen changed quaternion playback');
    const original=JSON.stringify(existing);
    const after=ModelProject.all.filter(p=>existing.some(s=>s.uuid===p.uuid)).map(p=>({uuid:p.uuid,name:p.name,saved:p.saved}));
    if(JSON.stringify(after)!==original)throw Error('Existing tab state changed');
    const result={passed:true,blockbench:Blockbench.version,speed:1.3,limbAmplitudeControl:.85,armAmplitudeControl:.7,waistGain:.6,waistEulerLimitsDegrees:[15,20,15],skills:rows,reopenedNativeRoundtrip:true,untouchedExistingProjects:existing};
    await write(root+'/blockbench-live.json',JSON.stringify(result,null,2));
    Preview.selected.loadAnglePreset({projection:'perspective',position:[60,40,-105],target:[-6,17,0]});
    Preview.selected.controls.update();
    Timeline.setTime(animation.length*.45);Animator.preview();
    return result;
  } catch(error) {
    // Only agent-created tabs can be discarded; user tabs never enter owned.
    if(Project && owned.has(Project.uuid)) await closeOwned();
    throw error;
  }
})()
