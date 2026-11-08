package local.huoying.air.auth;

public final class DisplayLayoutTest {
 public static void main(String[] args){
  for(int[] screen:new int[][]{{2670,1145},{2048,867},{1920,1025},{1280,667},{800,1200}}){
   for(String mode:new String[]{"fit","wide"}){
    DisplayLayout normal=DisplayLayout.calculate(screen[0],screen[1],mode,100),large=DisplayLayout.calculate(screen[0],screen[1],mode,125);
    for(DisplayLayout layout:new DisplayLayout[]{normal,large}){
     check(layout.width>=1020&&layout.height>=600);
     check(layout.left>=0&&layout.top>=0&&layout.left+layout.width*layout.scale<=screen[0]+.01&&layout.top+layout.height*layout.scale<=screen[1]+.01);
    }
    check(Math.abs(large.scale/normal.scale-1.25)<.005);
    if(mode.equals("wide")&&screen[0]/(float)screen[1]>=1.6)check(normal.left<1&&normal.top<1);
   }
  }
  check(DisplayLayout.calculate(0,800,"wide",100)==null);
  boolean refused=false;try{DisplayLayout.calculate(1920,1080,"fill",150);}catch(IllegalArgumentException error){refused=true;}check(refused);
  System.out.println("Viewport modes, UI scaling, minimum HUD size and physical bounds passed");
 }
 static void check(boolean value){if(!value)throw new AssertionError("display layout contract");}
}
