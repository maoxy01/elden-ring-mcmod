import {createRemembrance,palette,arts,referenceSources} from './remembrance.mjs';
export {palette,arts,referenceSources};
export const weapons=createRemembrance({game:true,smoothGame:false});
for(const w of weapons)w.features=w.features.map(f=>f.replace('五层倒角','三层倒角'));
export const textureResolution=2048;
