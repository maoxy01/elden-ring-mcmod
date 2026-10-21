'use strict';
const fs = require('node:fs');
const path = require('node:path');
const root = __dirname;
const core = require('./core.cjs').toString();
const plugin = fs.readFileSync(path.join(root, 'plugin.js'), 'utf8');
fs.mkdirSync(path.join(root, 'dist'), {recursive: true});
fs.writeFileSync(path.join(root, 'dist/eldencombat_pose.js'), '/* Elden Combat Pose Editor 1.0.0 - MIT - uses the Blockbench plugin API */\n(function(){\nconst createPoseCore = ' + core + ';\n' + plugin + '\n})();\n');
console.log('Built dist/eldencombat_pose.js');
