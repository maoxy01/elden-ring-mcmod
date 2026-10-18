// Author shapes on a four-pixel construction grid; small highlights stay subordinate.
export function createBlockPainters({sample,noise,clamp,smooth,frac}){
 const G=4,snap=v=>Math.round(v/G)*G;
 function rect(p,x,y,w,h,c,a=255){p.rect(snap(x),snap(y),Math.max(G,snap(w)),Math.max(G,snap(h)),c,a)}
 function poly(p,points,c,a=255){
  const ys=points.map(v=>v[1]),xs=points.map(v=>v[0]);
  for(let y=Math.floor(Math.min(...ys)/G)*G;y<Math.max(...ys);y+=G)for(let x=Math.floor(Math.min(...xs)/G)*G;x<Math.max(...xs);x+=G){
   const xx=x+G/2,yy=y+G/2;let inside=false;
   for(let i=0,j=points.length-1;i<points.length;j=i++){const u=points[i],v=points[j];if((u[1]>yy)!==(v[1]>yy)&&xx<(v[0]-u[0])*(yy-u[1])/(v[1]-u[1])+u[0])inside=!inside}
   if(inside)p.rect(x,y,G,G,c,a);
  }
 }
 function line(p,a,b,c,w=G,alpha=255){const n=Math.ceil(Math.max(Math.abs(b[0]-a[0]),Math.abs(b[1]-a[1]))/G);for(let i=0;i<=n;i++){const t=i/Math.max(n,1);rect(p,a[0]+(b[0]-a[0])*t-w/2,a[1]+(b[1]-a[1])*t-w/2,w,w,c,alpha)}}
 function trail(p,pts,w=G){for(let i=1;i<pts.length;i++){line(p,pts[i-1],pts[i],1,w+G,160);line(p,pts[i-1],pts[i],i>pts.length*.55?3:2,w)}}
 function crystal(p,x,y,size=8){
  x=snap(x);y=snap(y);const r=snap(size);
  poly(p,[[x-r,y],[x,y-r],[x+r,y],[x,y+r]],3);
  poly(p,[[x-r,y],[x,y-r],[x,y+r]],2);
  rect(p,x-G,y-G,G,G,5);
 }
 function bracket(p,cx,cy,rx,ry,c,a=255){
  const x=snap(cx-rx),y=snap(cy-ry),w=snap(rx*2),h=snap(ry*2);
  rect(p,x,y,12,G,c,a);rect(p,x,y,G,12,c,a);rect(p,x+w-12,y,12,G,c,a);rect(p,x+w-G,y,G,12,c,a);
  rect(p,x,y+h-G,12,G,c,a);rect(p,x,y+h-12,G,12,c,a);rect(p,x+w-12,y+h-G,12,G,c,a);rect(p,x+w-G,y+h-12,G,12,c,a);
 }
 function azur(p,t){
  const g=smooth(t/.22),x=76+156*g,y=108-24*g;
  poly(p,[[72,100],[x-8,y-12],[x+4,y-4],[x+4,y+12],[84,128],[72,116]],1);
  poly(p,[[76,100],[x-8,y-12],[x+4,y-4],[88,116]],3);
  poly(p,[[88,116],[x+4,y-4],[x+4,y+8],[88,124]],2);
  poly(p,[[76,104],[x-4,y-4],[x,y+2],[80,116]],5);
  // Large moving facets, not per-pixel texture noise.
  for(let k=0;k<5;k++){
   const u=frac(t*1.5+k*.2)*g,xx=84+140*u,yy=108-24*u,m=sample('s34041_a',u,t*.2);
   poly(p,[[xx,yy-12],[xx+12,yy-16],[xx+20,yy-12],[xx+8,yy-8]],m>.15?4:3);
   rect(p,xx+4,yy+8,12,4,2);
  }
  bracket(p,76,110,12*g,20*g,3);crystal(p,76,108,8);
  for(let k=0;k<4;k++){const u=frac(k*.27+t*.75);rect(p,84+u*144*g,88-u*24+(k%2)*52,8,4,2)}
 }
 function ruin(p,t){
  for(let k=0;k<12;k++){
   const v=clamp((t-.06-k*.016)/.7);if(v<=0||v>=1)continue;
   const point=u=>[72+152*u,108+Math.sin(u*Math.PI)*(-68+k*12)+u*(-24+k%3*12)];
   const pts=[];for(let j=9;j>=0;j--)pts.push(point(Math.max(0,v-j*.023)));trail(p,pts,4);crystal(p,...point(v),k%4===0?8:4);
   const m=sample('s34060_a',k*.13,t),[x,y]=point(Math.max(0,v-.08));if(m>.18)rect(p,x,y,8,4,4);
  }
  if(t<.4){bracket(p,72,108,8,16,2,160);crystal(p,72,108,4+smooth(t/.3)*4)}
 }
 function rain(p,t){
  const e=smooth(t/.2)*(1-smooth((t-.76)/.24));
  if(e>.04)for(let k=0;k<9;k++){
   const x=80+k*16,y=32+(k%3===0?-4:4),w=20,h=snap((12+noise(k,2)*16)*e);
   rect(p,x,y,w,Math.max(4,h),1);rect(p,x,y-4,16,8,2);
   const m=sample('s64040_a',k*.09+t*.05,.3),n=sample('s34030_a',k*.1,.5);
   if(m>.2)rect(p,x+4,y+4,12,8,3);if(n>.3)rect(p,x+8,y+12,8,4,4);
  }
  if(t>.15&&t<.91)for(let k=0;k<14;k++){
   const f=frac(t*1.8+k*.319),x=84+k*9,y=56+f*92;
   rect(p,x,y-16,4,16,1,160);rect(p,x,y-8,4,12,3);rect(p,x-4,y,8,4,5);
   if(f>.85){rect(p,x-4,152+(k%2)*4,12,4,3);rect(p,x,148+(k%2)*4,4,4,4)}
  }
 }
 function moon(p,t){
  const e=smooth(t/.2)*(1-smooth((t-.8)/.2)),r=snap(32*e),cx=snap(108+64*smooth((t-.2)/.6)),cy=76;
  if(r>=4){
   const points=[[-.5,-1],[.5,-1],[1,-.5],[1,.5],[.5,1],[-.5,1],[-1,.5],[-1,-.5]].map(([x,y])=>[cx+x*r,cy+y*r]);
   poly(p,points,1);
   poly(p,[[cx-r,cy-r/2],[cx-r/2,cy-r],[cx+r/2,cy-r],[cx+r*.4,cy-r*.5],[cx-r*.6,cy+r*.5],[cx-r,cy+r/2]],3);
   poly(p,[[cx-r*.6,cy+r*.5],[cx+r*.4,cy-r*.5],[cx+r,cy-r/2],[cx+r,cy+r/2],[cx+r/2,cy+r],[cx-r/2,cy+r]],2);
   poly(p,[[cx+r/2,cy-r],[cx+r,cy-r/2],[cx+r,cy+r/2],[cx+r/2,cy+r],[cx+r*.6,cy]],0);
   for(const [u,v,w,h] of [[-.5,-.5,12,8],[0,-.25,8,12],[-.5,.25,8,8],[.25,.4,8,4]]){
    const m=sample('s89011_a',u*.2+.5+t*.04,v*.25+.5);rect(p,cx+u*r,cy+v*r,w*e,h*e,m>.32?1:0);
    rect(p,cx+u*r,cy+v*r-4,Math.max(4,w*e-4),4,3);
   }
   for(let k=0;k<4;k++){const m=sample('s34070_a',k*.21,t*.1);if(m>.12)rect(p,cx-r-12-k%2*8,cy-r/2+k*12,8,4,3,160)}
  }
  if(t>.74&&r)for(let k=0;k<5;k++){const x=cx+(k-2)*16,y=cy+Math.sin(k*3)*r;rect(p,x, y+(t-.74)*60,8,4,k%2?3:2)}
 }
 function flame(p,t){
  const e=smooth(t/.2)*(1-smooth((t-.62)/.16)),cx=snap(104+68*clamp(t/.65)),cy=88,r=snap(24*e);
  if(r>=4){
   poly(p,[[cx-r/2,cy-r],[cx+r/2,cy-r],[cx+r,cy-r/2],[cx+r,cy+r/2],[cx+r/2,cy+r],[cx-r/2,cy+r],[cx-r,cy+r/2],[cx-r,cy-r/2]],1);
   poly(p,[[cx-r/2,cy-r],[cx+r/2,cy-r],[cx+r,cy-r/2],[cx+r/2,cy],[cx-r,cy-r/2]],3);
   poly(p,[[cx-r,cy-r/2],[cx-r/2,cy],[cx+r/2,cy],[cx+r/2,cy+r],[cx-r/2,cy+r],[cx-r,cy+r/2]],2);
   for(let k=0;k<5;k++){
    const x=cx-r+4+k*r*.4,h=(8+((k+Math.floor(t*14))%3)*4)*e,n=sample('s31041_a',k*.19,.5,Math.floor(t*50)%64);
    rect(p,x,cy-r-h,8,h,2);rect(p,x+4,cy-r-h+4,4,Math.max(4,h-4),n>.2?4:3);
   }
   rect(p,cx-4,cy-4,8,8,0);rect(p,cx-12,cy-8,12,4,4);rect(p,cx+4,cy+4,12,4,3);
   const turn=Math.floor(t*12)%3;
   rect(p,cx-r-4,cy-8-turn*4,8,16,3);rect(p,cx-r,cy-4-turn*4,4,8,4);
   rect(p,cx+r-4,cy-12+turn*4,8,16,2);rect(p,cx+r-4,cy-8+turn*4,4,8,4);
   rect(p,cx-12,cy+r-4,12,8,2);rect(p,cx-8,cy+r-4,8,4,3);
   rect(p,cx+4,cy-12,8,4,5);rect(p,cx-12,cy+8,8,4,4);
   if(sample('s34031_a',t*.5,.5)>.1)rect(p,cx-r,cy+4,8,12,4);
  }
  if(t>.61){
   const burst=smooth((t-.62)/.2),fade=1-smooth((t-.81)/.19);
   const rr=20+burst*40;poly(p,[[172-rr,144],[172-rr+12,136],[172+rr-16,136],[172+rr,148],[172+rr-12,156],[172-rr+12,156]],1,160);
   for(let k=0;k<9;k++){const x=136+k*10,y=144+k%2*4,h=snap((12+(k*7)%20)*fade);if(h<=0)continue;rect(p,x,y-h,8,h,2);rect(p,x+4,y-h+8,4,Math.max(4,h-8),4)}
   for(let k=0;k<4;k++)rect(p,144+k*20,124-burst*24+k%2*12,8,8,2);
  }
 }
 function elden(p,t){
  const v=clamp(t/.85),point=u=>[76+148*u,100-32*Math.sin(u*Math.PI)+12*u];
  const pts=[];for(let j=25;j>=0;j--)pts.push(point(Math.max(0,v-j*.02)));trail(p,pts,8);
  for(let k=0;k<5;k++){const u=Math.max(0,v-k*.09),[x,y]=point(u),m=sample('s42071_a',u,t+k*.1,k%64);rect(p,x,y-4,8,4,m>.2?4:3)}
  crystal(p,...point(v),12);
  for(let k=0;k<10;k++){
   const b=.08+k*.048;if(v<=b)continue;const f=clamp((v-b)/.4);if(f>=1)continue;
   const [sx,sy]=point(b),side=k%2?1:-1,child=u=>[sx+u*(48+k%3*8),sy+side*Math.sin(u*Math.PI*.8)*(24+k%4*8)+u*16];
   const linePts=[];for(let j=10;j>=0;j--)linePts.push(child(Math.max(0,f-j*.032)));trail(p,linePts,4);crystal(p,...child(f),4);
  }
 }
 function roar(p,t){
  const e=smooth(t/.18)*(1-smooth((t-.65)/.27));
  if(e>.05){
   const dx=snap((1-e)*-16),shift=ps=>ps.map(([x,y])=>[x+dx,y]);
   poly(p,shift([[64,112],[68,68],[80,52],[104,52],[116,64],[116,72],[148,72],[156,84],[148,96],[116,96],[100,116],[88,124]]),1);
   poly(p,shift([[68,76],[68,44],[60,36],[76,40],[84,52],[92,52],[92,28],[100,28],[100,48],[108,48],[108,40],[116,40],[116,64],[104,72]]),2);
   poly(p,shift([[76,60],[88,52],[104,56],[112,68],[100,80],[76,80]]),3);
   poly(p,shift([[104,76],[144,76],[152,84],[144,92],[112,92]]),3);
   const jaw=snap(smooth((t-.2)/.25)*20);
   rect(p,100+dx,92,40,12+jaw,0);
   poly(p,shift([[96,104+jaw],[132,104+jaw],[140,100+jaw],[148,100+jaw],[148,112+jaw],[132,120+jaw],[104,116+jaw]]),2);
   rect(p,100+dx,72,16,8,0);rect(p,108+dx,76,8,4,4);
   for(let k=0;k<5;k++)rect(p,112+dx+k*8,92,4,8,k%2?4:5);
   for(let k=0;k<6;k++){const x=72+(k%2)*12+dx,y=84+Math.floor(k/2)*8;rect(p,x,y,8,4,k%3===0?3:2)}
   if(sample('s00003_r',t,.3)>.1)rect(p,88+dx,64,8,4,2);
  }
  if(t>.25&&t<.94)for(let k=0;k<3;k++){
   const f=frac(clamp((t-.25)/.6)*1.5-k*.22);if(f<.06)continue;
   const x=132+f*84,r=12+f*40,c=4-k;
   rect(p,x,108-r,12,8,c);rect(p,x+8,116-r,8,12,c);rect(p,x+12,128-r,8,Math.max(4,r*2-40),c,200);
   rect(p,x,108+r,12,8,c);rect(p,x+8,96+r,8,12,c);
  }
  if(t>.4)for(let k=0;k<6;k++){const v=frac(t*.8+k*.17);rect(p,120+v*104,144+(k%2)*8-v*8,8,4,k%2?1:2)}
 }
 return {comet_azur:azur,stars_of_ruin:ruin,founding_rain:rain,ranni_dark_moon:moon,fell_god_flame:flame,elden_stars:elden,greyoll_roar:roar};
}
