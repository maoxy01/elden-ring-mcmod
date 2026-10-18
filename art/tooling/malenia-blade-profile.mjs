import assert from 'node:assert/strict';
import {line} from './equipment-pixels.mjs';
export const maleniaProfile={startY:8,endY:45,center:'0.5 + 4.2*t*t',halfWidth:'(1.55-0.2*t) * (1-smoothstep(clamp((t-0.7)/0.3,0,1)))',tipTaperStartsAt:.7,samples:144};
export function maleniaBlade(base){
 const ps=base.filter(p=>p.points.some(v=>v[1]<8)).map(p=>({...p,points:p.points.map(([x,y])=>[x,Math.min(8,y)])}));
 const smooth=q=>q*q*(3-2*q),center=t=>.5+4.2*t*t,width=t=>(1.55-.2*t)*(1-smooth(Math.max(0,Math.min(1,(t-.7)/.3)))),lanes=[-1,-.55,-.05,.6,1],colors=[[103,82,43],[139,115,58],[170,148,84],[209,192,131]];
 let previous=width(0);for(let i=0;i<=144;i++){const current=width(i/144);assert(current<=previous+1e-10&&current>=-1e-10);previous=current;}
 for(let i=0;i<144;i++)for(let j=0;j<4;j++){
   const t=i/144,tt=(i+1)/144,y=8+37*t,yy=8+37*tt;
   ps.push({bone:'root',color:colors[j],points:[[center(t)+width(t)*lanes[j],y],[center(t)+width(t)*lanes[j+1],y],[center(tt)+width(tt)*lanes[j+1],yy],[center(tt)+width(tt)*lanes[j],yy]]});
 }
 // Short engraving marks follow the same curve and stay inside the blade.
 for(let y=15;y<36;y+=7){const t=(y-8)/37,x=center(t)-width(t)*.1;ps.push(line([x,y],[x+.18,y+1.3],.25,[124,99,49]));}
 return ps;
}
