(function () {
    const base = 'D:/codexout/mcmod/art/equipment-2d/V0.2/holds/';
    const contexts = ['firstperson_righthand', 'firstperson_lefthand', 'thirdperson_righthand', 'thirdperson_lefthand'];
    const read = path => new Promise(resolve => Blockbench.read([path], {readtype: 'text'}, files => resolve(files[0].content)));
    const write = (path, content, image = false) => new Promise(resolve => Blockbench.writeFile(path, {content, ...(image ? {savetype: 'image'} : {})}, resolve));
    const json = obj => JSON.stringify(obj, null, 2) + '\n';
    const assert = (test, message) => { if (!test) throw new Error(message); };
    const nearly = (a, b, message) => {
        assert(a.length === b.length && a.every((v, i) => Math.abs(v - b[i]) < 0.00001), message);
    };
    function compareDisplay(expected, actual, label) {
        for (const [slot, spec] of Object.entries(expected)) {
            for (const field of ['rotation', 'translation', 'scale']) {
                const fallback = field === 'scale' ? [1, 1, 1] : [0, 0, 0];
                const a = spec[field] || fallback, b = actual[slot]?.[field] || fallback;
                if (field === 'rotation') {
                    assert(a.every((v, i) => Math.abs(((v - b[i] + 540) % 360) - 180) < 0.00001), label + ': ' + slot + '/' + field);
                } else nearly(a, b, label + ': ' + slot + '/' + field);
            }
        }
    }
    window.codexV02Audit = {
        owned: null, before: null, rows: [], next: 0, mapping: null,
        async init(owned, before) {
            assert(!before.tabs.some(p => p.uuid === owned), 'Cannot own a preexisting user project');
            assert(Project.uuid === owned, 'The separately created inspection tab must be active');
            this.owned = owned;
            this.before = before;
            this.mapping = JSON.parse(await read(base + 'mapping.json'));
            return {projects: this.mapping.items.length, owned};
        },
        async load(row) {
            const old = ModelProject.all.find(p => p.uuid === this.owned);
            assert(old && !this.before.tabs.some(p => p.uuid === old.uuid), 'Only owned inspection tabs may close');
            old.saved = true;
            await old.close();
            newProject(row.kind === 'held' ? Formats.java_block : Formats.free);
            this.owned = Project.uuid;
            assert(!this.before.tabs.some(p => p.uuid === this.owned), 'New inspection tab ownership');
            const path = base + row.project;
            const model = JSON.parse(await read(path));
            assert(!Cube.all.length && !Group.all.length, 'Parse only an empty owned inspection project');
            Undo.initEdit({elements: [], outliner: true, textures: []});
            Codecs.project.parse(model, path);
            Undo.finishEdit('Inspect separate matched V0.2 copy', {elements: Outliner.elements.slice(), outliner: true, textures: Texture.all.slice()});
            await Promise.all(Texture.all.map(t => t.img.decode()));
            Canvas.updateAll();
            return model;
        },
        async check(row) {
            const model = await this.load(row);
            const stem = row.project.split('/').pop().replace('.bbmodel', '');
            assert(Cube.all.length === model.elements.length, stem + ': cube count');
            assert(Texture.all.every(t => t.img.naturalWidth > 0 && t.img.naturalHeight > 0), stem + ': decoded textures');
            const roundtrip = Codecs.project.compile({raw: true});
            assert(roundtrip.elements.length === model.elements.length, stem + ': native roundtrip cube count');
            const sourceCubes = new Map(model.elements.map(c => [c.uuid, c]));
            for (const cube of roundtrip.elements) {
                const original = sourceCubes.get(cube.uuid);
                assert(original, stem + ': cube UUID');
                for (const field of ['from', 'to', 'origin']) nearly(original[field] || [0, 0, 0], cube[field] || [0, 0, 0], stem + ': geometry ' + field);
                for (const side of Object.keys(original.faces)) {
                    nearly(original.faces[side].uv, cube.faces[side].uv, stem + ': UV ' + side);
                }
            }
            const evidence = {id: row.id, state: row.state || null, kind: row.kind, project: row.project,
                cubes: Cube.all.length, exportedCubes: Cube.all.filter(c => c.export).length,
                texturesDecoded: true, geometryRoundtrip: true, screenshots: []};
            if (row.kind === 'held') {
                assert(Format.id === 'java_block', stem + ': Java item format');
                assert(Texture.all.length === 1 && Texture.all[0].javaTextureLink() === row.textureResource, stem + ': selected texture resource');
                compareDisplay(model.display, Project.display_settings, stem + ': import display');
                const exported = Codecs.java_block.compile({raw: true, prevent_dialog: true, groups: false, comment: false});
                compareDisplay(model.display, exported.display || {}, stem + ': export display');
                assert(exported.elements.length === row.sourceCubes, stem + ': Java element count');
                assert(exported.textures.layer0 === row.textureResource, stem + ': Java texture binding');
                for (const [i, element] of exported.elements.entries()) {
                    const cube = model.elements[i];
                    nearly(cube.from, element.from, stem + ': exported from');
                    nearly(cube.to, element.to, stem + ': exported to');
                    for (const [side, face] of Object.entries(element.faces)) {
                        assert(face.texture === '#layer0', stem + ': exported face texture');
                        const uv = cube.faces[side].uv.map((v, j) => v * 16 / (j % 2 ? model.resolution.height : model.resolution.width));
                        nearly(uv, face.uv, stem + ': exported face UV');
                    }
                }
                delete exported.hold_source;
                delete exported.hold_pairing;
                await write(base + 'exports/' + stem + '.json', json(exported));
                evidence.javaExport = 'exports/' + stem + '.json';
                evidence.displayContexts = Object.keys(model.display);
                evidence.displayUnchanged = true;
                evidence.textureResource = row.textureResource;
                Modes.options.display.select();
                for (const context of contexts) {
                    DisplayMode.load(context);
                    Canvas.updateAll();
                    await new Promise(resolve => setTimeout(resolve, 80));
                    const png = await new Promise(resolve => Preview.selected.screenshot({crop: false, width: 640, height: 480}, resolve));
                    const file = 'qa/screenshots/' + stem + '__' + context + '.png';
                    await write(base + file, png, true);
                    evidence.screenshots.push({context, file});
                }
            } else {
                assert(Format.id === 'free', stem + ': native worn format');
                const right = Group.all.find(g => g.name === 'arm_right');
                const left = Group.all.find(g => g.name === 'arm_right_l');
                assert(right && left, stem + ': both arm attachment roots');
                nearly(right.origin, row.armPivotRight, stem + ': right arm pivot');
                nearly(left.origin, row.armPivotLeft, stem + ': left arm pivot');
                assert(evidence.exportedCubes === (row.id === 'claw' ? 94 : 56), stem + ': worn export count');
                assert(Group.all.filter(g => g.name.startsWith('ref_')).every(g => g.export === false), stem + ': reference group not exported');
                evidence.armPivotsUnchanged = true;
            }
            await write(base + 'qa/native-roundtrip/' + stem + '.bbmodel', json(roundtrip));
            evidence.nativeRoundtrip = 'qa/native-roundtrip/' + stem + '.bbmodel';
            return evidence;
        },
        async batch(count) {
            const started = this.next;
            for (let i = 0; i < count && this.next < this.mapping.items.length; i++) {
                const row = this.mapping.items[this.next];
                const evidence = await this.check(row);
                this.rows.push(evidence);
                this.next++;
                await write(base + 'qa/native-validation.json', json({complete: this.next === this.mapping.items.length, checked: this.next, total: this.mapping.items.length, rows: this.rows}));
            }
            return {from: started, checked: this.next, total: this.mapping.items.length, last: this.rows.at(-1)?.project};
        },
        async finish() {
            assert(this.next === this.mapping.items.length, 'Not all projects checked');
            const owned = ModelProject.all.find(p => p.uuid === this.owned);
            assert(owned && !this.before.tabs.some(p => p.uuid === owned.uuid), 'Owned cleanup only');
            owned.saved = true;
            await owned.close();
            for (const before of this.before.tabs) {
                const current = ModelProject.all.find(p => p.uuid === before.uuid);
                assert(current && current.name === before.name && current.saved === before.saved, 'Preexisting user tab changed: ' + before.name);
            }
            ModelProject.all.find(p => p.uuid === this.before.active)?.select();
            const summary = {complete: true, checked: this.next, total: this.mapping.items.length,
                javaExports: this.rows.filter(r => r.javaExport).length,
                screenshots: this.rows.reduce((n, r) => n + r.screenshots.length, 0),
                preexistingTabsPreserved: true, restoredActive: Project.uuid,
                runtimeInstalled: false, humanVisualAccepted: false, rows: this.rows};
            await write(base + 'qa/native-validation.json', json(summary));
            return {checked: summary.checked, javaExports: summary.javaExports, screenshots: summary.screenshots, preexistingTabsPreserved: true, restoredActive: Project.uuid};
        }
    };
    return 'V0.2 native inspection helper loaded; only explicitly owned temporary tabs can be closed.';
})()
