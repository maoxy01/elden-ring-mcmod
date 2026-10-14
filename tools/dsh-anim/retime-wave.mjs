import fs from 'node:fs';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const path = new URL('./specs/wave_of_gold.json', import.meta.url);
const spec = JSON.parse(fs.readFileSync(path, 'utf8'));
const parameters = JSON.parse(fs.readFileSync(new URL('../../src/main/resources/data/eldencombat/dsh_skills/remembrance_parameters.json', import.meta.url)));
const wave = parameters.find(p => p.id === 'wave_of_gold');
const contact = (wave.windupTicks + wave.activeTicks) / 20;
const duration = contact + wave.recoveryTicks / 20;
const oldDuration = spec.duration;
if (oldDuration <= contact) throw new Error('Missing golden wave recovery segment');
for (const key of spec.keys) {
  if (key.t > contact) key.t = contact + (key.t - contact) * (duration - contact) / (oldDuration - contact);
}
spec.duration = duration;
spec.note = 'Original prototype golden wave. Windup/contact keys unchanged; recovery retimed to parameters. No source game assets.';
fs.writeFileSync(path, JSON.stringify(spec, null, 2) + '\n');
const result = spawnSync(process.execPath, [fileURLToPath(new URL('./posegen.mjs', import.meta.url)),
  fileURLToPath(path),
  fileURLToPath(new URL('../../src/main/resources/assets/eldencombat/animmodels/animations/wave_of_gold.json', import.meta.url))], {encoding: 'utf8'});
if (result.status !== 0) throw new Error(result.stderr || result.stdout);
process.stdout.write(result.stdout);
