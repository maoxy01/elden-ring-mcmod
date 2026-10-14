import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {equipmentMaterialStyle} from './equipment-material-style.mjs';

const source=JSON.parse(await fs.readFile(new URL('../backups/flat-equipment-before-20261012/art/remembrance-game/sacred_relic_sword.model.json',import.meta.url)));
const style=equipmentMaterialStyle(source.materials,source.materials);
const pixels=material=>new Map(Array.from({length:8*20},(_,i)=>{
  const x=i%8,y=Math.floor(i/8);return [x+','+y,{x,y,front:material,back:material}];
}));
test('aged gold and the edge ramp stay below candy-yellow values',()=> {
  assert(style.materials.gold_rune.color[0]<source.materials.gold_rune.color[0]);
  assert(style.materials.gold_rune.color[1]<source.materials.gold_rune.color[1]);
  for(const value of Object.values(style.materials))assert(value.color.every(c=>Number.isInteger(c)&&c>=12&&c<=205));
});
test('front and back have directional tones and a dark side wall',()=> {
  const p=pixels('silver');style.paint(p,'eldencombat:straight_sword');
  assert.notEqual(p.get('0,5').front,p.get('0,5').back);
  for(const value of p.values())for(const key of ['front','back','side'])assert(style.materials[value[key]]);
  assert(p.get('7,5').side.endsWith('__shadow'));
});
test('black-blade guard uses grey metal instead of the universal gold palette',()=> {
  const p=pixels('gold');style.paint(p,'eldencombat:malikeths_black_blade');
  assert([...p.values()].every(v=>v.front.startsWith('ash_guard')));
});
test('cursed sword gets broad violet, olive and steel bands without random noise',()=> {
  const a=pixels('iridescent'),b=pixels('iridescent');
  style.paint(a,'eldencombat:morgotts_cursed_sword');style.paint(b,'eldencombat:morgotts_cursed_sword');
  assert.deepEqual(a,b);
  for(const name of ['oil_violet','oil_green','oil_steel'])assert([...a.values()].some(v=>v.front.startsWith(name)));
});
