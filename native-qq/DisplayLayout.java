package local.huoying.air.auth;

/** The same logical viewport as the earlier Android player, before physical scaling. */
public final class DisplayLayout {
 public final int width,height;
 public final float scale,left,top;
 private DisplayLayout(int w,int h,float s,float x,float y){width=w;height=h;scale=s;left=x;top=y;}
 public static DisplayLayout login(int width,int height){return width>0&&height>0?new DisplayLayout(width,height,1,0,0):null;}
 public static boolean valid(String mode,int zoom){return ("fit".equals(mode)||"wide".equals(mode))&&(zoom==100||zoom==125);}
 public static DisplayLayout calculate(int availableWidth,int availableHeight,String mode,int zoom){
  if(!valid(mode,zoom))throw new IllegalArgumentException("invalid display setting");
  if(availableWidth<=0||availableHeight<=0)return null;
  int baseWidth="wide".equals(mode)?Math.min(4096,Math.max(1280,Math.round(availableWidth*800f/availableHeight))):1280;
  double factor=Math.min(zoom/100.0,Math.min(baseWidth/1020.0,800/600.0));
  int w=(int)Math.round(baseWidth/factor),h=(int)Math.round(800/factor);
  float scale=Math.min(availableWidth/(float)w,availableHeight/(float)h);
  return new DisplayLayout(w,h,scale,Math.max(0,(availableWidth-w*scale)/2),Math.max(0,(availableHeight-h*scale)/2));
 }
}
