// Authored pixel albedo, informed by the original-game weapon catalogue.
const tones={
  steel:[113,117,115],steel_dark:[60,63,60],silver_edge:[152,157,153],engraving:[48,51,48],
  leather:[57,43,37],leather_wrap:[80,58,49],red:[103,51,46],wood:[86,65,45],amber:[143,123,81],
  bone:[149,145,127],ivory:[165,158,135],stone:[98,96,88],crystal:[65,99,113],
  relic_metal:[129,116,80],relic_edge:[164,151,109],gold:[141,123,82],gold_light:[175,157,111],
  gold_rune:[157,135,88],black_steel:[39,39,38],black_edge:[70,71,67],
  blood:[72,27,29],flesh:[106,61,50],scarlet:[119,43,43],bronze:[91,77,55],silver:[150,153,147],
  curse:[105,81,110],iridescent:[101,114,112]
};
const luminance=c=>c[0]*.2126+c[1]*.7152+c[2]*.0722;
const distance=(a,b)=>a.reduce((n,c,i)=>n+(c-b[i])**2,0);
const scale=(color,factor)=>color.map(c=>Math.round(Math.max(12,Math.min(205,c*factor))));

export function equipmentMaterialStyle(palette,sourceMaterials) {
  const materials={},families={},original=Object.entries(sourceMaterials);
  for(const [name,value] of Object.entries(palette)) {
    const family=sourceMaterials[name]?name:original.reduce((best,[key,m])=>distance(m.color,value.color)<best.delta?
      {key,delta:distance(m.color,value.color)}:best,{key:original[0][0],delta:Infinity}).key;
    families[name]=family;
    const variation=Math.max(.8,Math.min(1.15,luminance(value.color)/Math.max(1,luminance(sourceMaterials[family].color))));
    const color=scale(tones[family]||value.color,variation);
    materials[name]={color};materials[name+'__shadow']={color:scale(color,.74)};materials[name+'__edge']={color:scale(color,1.22)};
  }
  for(const [name,color] of Object.entries({ash_guard:[128,123,108],oil_violet:[103,83,111],oil_green:[96,110,98],oil_steel:[105,113,116]})) {
    materials[name]={color};materials[name+'__shadow']={color:scale(color,.74)};materials[name+'__edge']={color:scale(color,1.22)};
  }
  function paint(pixels,id) {
    const rows=new Map();
    for(const p of pixels.values()) {
      const range=rows.get(p.y)||[Infinity,-Infinity];range[0]=Math.min(range[0],p.x);range[1]=Math.max(range[1],p.x+1);rows.set(p.y,range);
    }
    const bottom=Math.min(...rows.keys()),height=Math.max(...rows.keys())+1-bottom;
    for(const p of pixels.values()) {
      const [left,right]=rows.get(p.y),width=right-left,u=(p.x+.5-left)/width,v=(p.y+.5-bottom)/height;
      for(const face of ['front','back']) {
        let name=p[face],family=families[name];
        if(id.endsWith('malikeths_black_blade')&&['gold','gold_light','gold_rune','bronze'].includes(family))name='ash_guard';
        if(id.endsWith('morgotts_cursed_sword')&&['iridescent','curse','silver'].includes(family))
          name=v<.38?'oil_violet':v<.7?'oil_green':'oil_steel';
        const side=face==='front'?u:1-u;
        const edge=side>.86||side>.43&&side<.58;
        // Wear stays at the perimeter, in sparse two-pixel clusters rather than noise.
        const worn=width>3&&(side<.14||side>.86)&&((p.y-bottom)%13)<2;
        p[face]=name+(side<.29?'__shadow':edge&&!worn?'__edge':'');
      }
      p.side=p.front.endsWith('__shadow')?p.front:p.front.replace(/__edge$/,'')+'__shadow';
    }
  }
  return {materials,paint};
}
