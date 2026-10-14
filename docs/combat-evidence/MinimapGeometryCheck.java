import com.dsh.eldencombat.client.grace.MinimapSize;
class MinimapGeometryCheck {
 public static void main(String[] args) {
  int[][] cases={{854,480,103},{1280,720,154},{1920,1080,231},{640,360,91}};
  for(int[] c:cases) {
   int oldHeight=Math.max(128,(int)Math.floor(0.30*c[1]));
   int oldWidth=(int)(oldHeight*(c[0]*1.0/c[1]));
   int side=MinimapSize.squareSide(oldHeight,c[0],c[1]);
   if(side!=c[2]) throw new AssertionError("Unexpected side: "+side);
   double target=Math.sqrt((double)oldWidth*oldHeight*2/7);
   if(Math.abs(side-target)>0.5) throw new AssertionError("Rounding exceeded half pixel");
   System.out.printf("%dx%d: old=%dx%d new=%dx%d area=%.6f target=%.6f%n",c[0],c[1],oldWidth,oldHeight,side,side,(double)side*side/(oldWidth*oldHeight),2.0/7);
  }
 }
}
