import {PNG,set,backShield} from './equipment-pixels.mjs';
const palette={A:[60,63,60],B:[113,117,115],C:[103,51,46],D:[143,123,81],E:[152,157,153]};
export function shieldPixels(n,back=false){
 const master=new PNG({width:16,height:16});
 const rows=['................','...AAAAAAAAAA...','...ABCCDDCCBA...','..ABECCDDCCEBA..','..ABCCCDDCCCBA..','..ADDDDDDDDDDA..','..ADDDDDDDDDDA..','..ABCCCDDCCCBA..','..ABCCCDDCCCBA..'];
 for(let y=0;y<rows.length;y++)for(let x=0;x<16;x++)if(palette[rows[y][x]])set(master,x,y,palette[rows[y][x]]);
 for(const [y,l,r] of [[9,2,13],[10,3,12],[11,3,12],[12,4,11],[13,5,10],[14,6,9]])for(let x=l;x<=r;x++){
   const key=x===l||x===r?'A':x===7||x===8?'D':x===l+1||x===r-1?'B':'C';set(master,x,y,palette[key]);
 }
 const source=back?backShield(master,[8.25,8.25]):master,result=new PNG({width:n,height:n});
 for(let y=0;y<n;y++)for(let x=0;x<n;x++){const sx=Math.floor(x*16/n),sy=Math.floor(y*16/n);source.data.copy(result.data,(y*n+x)*4,(sy*16+sx)*4,(sy*16+sx)*4+4)}
 return result;
}
