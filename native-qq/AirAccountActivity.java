package local.huoying.air.auth;

import air.local.huoying.airgame.AppEntry;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;

public class AirAccountActivity extends AppEntry {
 private final Handler ui=new Handler(Looper.getMainLooper());
 private static final int BAR_DP=21;
 private SurfaceView observedSurface;
 private LinearLayout bar;private FrameLayout viewport,game;private int slot;private boolean active,closing,all;private String rendered="";private double applied=Double.NaN;
 private final Runnable refresh=new Runnable(){public void run(){
  if(closing)return;
  try{JSONObject root=Accounts.snapshot(AirAccountActivity.this),self=Accounts.find(root,slot);if(self==null||!self.optBoolean("open")){closeOwn();return;}
   double speed=self.optDouble("speed",1);if(speed!=applied&&QqExtension.hasContext()){QqExtension.command("speed_changed",String.valueOf(speed));applied=speed;}
   if(active){attachBar();String now=QqExtension.gameRunning()+root.toString();if(!now.equals(rendered)){rendered=now;render(root);}}
  }catch(Exception error){Diagnostics.record(AirAccountActivity.this,"refresh_failed "+error.getClass().getSimpleName());if(active)Toast.makeText(AirAccountActivity.this,"账号资料读取失败，请重启应用",Toast.LENGTH_LONG).show();}
  ui.postDelayed(this,1000);
 }};
 public void onCreate(Bundle state){
  Diagnostics.record(this,"activity_create_begin");super.onCreate(state);slot=Accounts.processSlot();Diagnostics.record(this,"activity_create_done slot="+slot);
  if(!OfflineLicense.isActivated(this)){startActivity(new Intent(this,AirProduct.Launcher.class));finish();return;}
  if(slot<0){finish();return;}getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);ui.post(refresh);acceptReturn(getIntent());
 }
 public void onResume(){super.onResume();fullscreen();Diagnostics.record(this,"activity_resume");active=true;if(slot>=0&&!closing){try{Accounts.selected(this,slot);}catch(Exception ignored){}ui.removeCallbacks(refresh);ui.post(refresh);ui.postDelayed(this::applyViewport,200);}}
 public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused&&!closing){fullscreen();ui.postDelayed(()->{applyViewport();QqExtension.command("host_resumed","");},200);}}
 private void fullscreen(){getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);WindowInsetsController controller=getWindow().getInsetsController();if(controller!=null){controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);controller.hide(WindowInsets.Type.systemBars());}}else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);}
 public void onPause(){Diagnostics.record(this,"activity_pause");active=false;super.onPause();}
 public void onDestroy(){ui.removeCallbacksAndMessages(null);super.onDestroy();}
 public void onActivityResult(int request,int result,Intent data){
  if(request==Diagnostics.SAVE_REQUEST){if(result==RESULT_OK&&data!=null)Diagnostics.save(this,data.getData());return;}
  super.onActivityResult(request,result,data);
 }
 public void onNewIntent(Intent intent){String callback=intent.getStringExtra("qqReturn");intent.removeExtra("qqReturn");intent.setData(null);super.onNewIntent(intent);if(callback!=null)QqExtension.acceptReturn(callback);}
 private void acceptReturn(Intent intent){String callback=intent.getStringExtra("qqReturn");intent.removeExtra("qqReturn");if(callback!=null)QqExtension.acceptReturn(callback);}
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private void attachBar(){
  if(bar!=null&&bar.getParent()!=null)return;
  ViewGroup content=findViewById(android.R.id.content);if(content==null||content.getChildCount()==0)return;
  game=new FrameLayout(this);while(content.getChildCount()>0){View child=content.getChildAt(0);content.removeViewAt(0);game.addView(child,new FrameLayout.LayoutParams(-1,-1));}
  viewport=new FrameLayout(this);viewport.setBackgroundColor(android.graphics.Color.BLACK);viewport.addView(game,new FrameLayout.LayoutParams(-1,-1));
  FrameLayout.LayoutParams gameLayout=new FrameLayout.LayoutParams(-1,-1);gameLayout.topMargin=dp(BAR_DP);content.addView(viewport,gameLayout);
  game.setPivotX(0);game.setPivotY(0);viewport.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->applyViewport());
  bar=new LinearLayout(this);bar.setContentDescription("账号工具栏");bar.setGravity(Gravity.CENTER_VERTICAL);bar.setBackgroundColor(0xff182333);bar.setPadding(dp(24),0,dp(24),0);
  bar.setOnApplyWindowInsetsListener((view,insets)->{applyToolbarInsets(insets);return insets;});content.addView(bar,new FrameLayout.LayoutParams(-1,dp(BAR_DP)));bar.requestApplyInsets();rendered="";
 }
 private void applyToolbarInsets(WindowInsets insets){
  int left=dp(24),right=dp(24);
  if(Build.VERSION.SDK_INT>=28){DisplayCutout cutout=insets.getDisplayCutout();if(cutout!=null){left=Math.max(left,cutout.getSafeInsetLeft());right=Math.max(right,cutout.getSafeInsetRight());}}
  if(Build.VERSION.SDK_INT>=31){RoundedCorner l=insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT),r=insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT);if(l!=null)left=Math.max(left,l.getRadius());if(r!=null)right=Math.max(right,r.getRadius());}
  bar.setPadding(left,0,right,0);
 }
 void applyViewport(){
  if(!active||!hasWindowFocus()||viewport==null||game==null)return;
  JSONObject self=Accounts.record(this,slot);if(self==null)return;
  DisplayLayout layout=QqExtension.gameRunning()?DisplayLayout.calculate(viewport.getWidth(),viewport.getHeight(),self.optString("displayMode","wide"),self.optInt("zoom",100)):DisplayLayout.login(viewport.getWidth(),viewport.getHeight());if(layout==null)return;
  ViewGroup.LayoutParams params=game.getLayoutParams();if(params.width!=layout.width||params.height!=layout.height){params.width=layout.width;params.height=layout.height;game.setLayoutParams(params);}
  SurfaceView surface=findSurface(game);if(surface!=null){if(surface!=observedSurface){observedSurface=surface;surface.getHolder().addCallback(new SurfaceHolder.Callback(){public void surfaceCreated(SurfaceHolder holder){Diagnostics.record(AirAccountActivity.this,"surface_created");ui.post(AirAccountActivity.this::applyViewport);}public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){Diagnostics.record(AirAccountActivity.this,"surface_changed "+width+"x"+height);ui.post(AirAccountActivity.this::applyViewport);}public void surfaceDestroyed(SurfaceHolder holder){Diagnostics.record(AirAccountActivity.this,"surface_destroyed");}});}ViewGroup.LayoutParams size=surface.getLayoutParams();if(size.width!=layout.width||size.height!=layout.height){size.width=layout.width;size.height=layout.height;surface.setLayoutParams(size);}android.graphics.Rect buffer=surface.getHolder().getSurfaceFrame();if(surface.getHolder().getSurface().isValid()&&(buffer.width()!=layout.width||buffer.height()!=layout.height)){prepareSurfaceResize(surface,layout);surface.getHolder().setFixedSize(layout.width,layout.height);}}
  game.setScaleX(layout.scale);game.setScaleY(layout.scale);game.setTranslationX(layout.left);game.setTranslationY(layout.top);
 }
 private SurfaceView findSurface(View root){if(root instanceof SurfaceView)return (SurfaceView)root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){SurfaceView surface=findSurface(group.getChildAt(i));if(surface!=null)return surface;}}return null;}
 private void prepareSurfaceResize(SurfaceView surface,DisplayLayout layout){
  // AIR treats a height reduction as keyboard panning instead of resizing the stage.
  // shortcut: AIR 51.5.1.1 field names; revalidate this adapter when upgrading the SDK.
  Class<?> type=surface.getClass();while(type!=null&&!type.getName().equals("com.adobe.air.AIRWindowSurfaceView"))type=type.getSuperclass();if(type==null)return;
  try{java.lang.reflect.Field height=type.getDeclaredField("mHt"),width=type.getDeclaredField("mWd");height.setAccessible(true);width.setAccessible(true);height.setInt(surface,layout.height);width.setInt(surface,layout.width);}catch(ReflectiveOperationException error){throw new IllegalStateException("AIR viewport resize contract changed",error);}
 }
 private Button button(String text,boolean selected,Runnable action){Button b=new Button(this);b.setText(text);b.setTextSize(10);b.setSingleLine(true);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setIncludeFontPadding(false);b.setPadding(dp(2),0,dp(2),0);android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setColor(selected?0xffffbb55:0xff2d4057);shape.setCornerRadius(dp(3));b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22000000),shape,null));b.setStateListAnimator(null);b.setTextColor(selected?0xff1f2430:0xfff0f5fa);if(action!=null)b.setOnClickListener(v->action.run());return b;}
 private void tabStyle(Button b){b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22000000),null,new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)));b.setStateListAnimator(null);b.setElevation(0);}
 private void render(JSONObject root)throws Exception{
  if(bar==null)return;bar.removeAllViews();LinearLayout tabs=new LinearLayout(this);HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.addView(tabs);bar.addView(scroll,new LinearLayout.LayoutParams(0,-1,1));
  JSONArray list=root.getJSONArray("slots");JSONObject self=Accounts.find(root,slot);double speed=self.optDouble("speed",1);boolean mixed=false;String mode=self.optString("displayMode","wide");int zoom=self.optInt("zoom",100);
  for(int i=0;i<list.length();i++){JSONObject p=list.getJSONObject(i);if(!p.optBoolean("open"))continue;int id=p.getInt("slot");boolean selected=id==slot;if(all&&p.optDouble("speed",1)!=speed)mixed=true;
   LinearLayout accountTab=new LinearLayout(this);android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();background.setColor(selected?0xffffbb55:0xff2d4057);background.setCornerRadius(dp(3));accountTab.setBackground(background);LinearLayout.LayoutParams tabLayout=new LinearLayout.LayoutParams(dp(104),-1);tabLayout.setMargins(dp(1),dp(1),dp(1),dp(1));tabs.addView(accountTab,tabLayout);
   String label=p.optBoolean("running")?p.optString("label","账号 "+(id+1)):"账号 "+(id+1)+" · 未启动";Button tab=button(label,selected,()->{if(id!=slot)Accounts.open(this,id);});tabStyle(tab);tab.setEllipsize(android.text.TextUtils.TruncateAt.END);tab.setContentDescription("切换账号 "+(id+1)+" "+label);accountTab.addView(tab,new LinearLayout.LayoutParams(0,-1,1));
   Button close=button("×",selected,()->close(id));tabStyle(close);close.setContentDescription("关闭账号 "+(id+1));accountTab.addView(close,new LinearLayout.LayoutParams(dp(18),-1));
  }
  Button add=button("＋",false,()->{int id=Accounts.add(this);if(id<0)Toast.makeText(this,"最多同时打开 "+Accounts.LIMIT+" 个账号，请先关闭一个",Toast.LENGTH_LONG).show();else Accounts.open(this,id);});add.setContentDescription("多开账号");tabs.addView(add,new LinearLayout.LayoutParams(dp(24),-1));
  Button together=button("全部",all,()->{all=!all;rendered="";ui.removeCallbacks(refresh);ui.post(refresh);});together.setSelected(all);together.setContentDescription(all?"全部账号，已选择":"全部账号，未选择");bar.addView(together,new LinearLayout.LayoutParams(dp(34),-1));
  final double menuSpeed=mixed?Double.NaN:speed;
  Button speedButton=button((mixed?"倍速":speedLabel(speed))+" ▾",false,null);
  speedButton.setContentDescription("选择倍速");speedButton.setOnClickListener(v->showSpeedMenu(v,menuSpeed));
  bar.addView(speedButton,new LinearLayout.LayoutParams(dp(45),-1));
  Button ratio=button(mode.equals("wide")?"宽屏":"原比例",false,()->{Accounts.display(this,slot,mode.equals("wide")?"fit":"wide",zoom);applyViewport();rendered="";ui.removeCallbacks(refresh);ui.post(refresh);});ratio.setContentDescription("切换游戏比例");bar.addView(ratio,new LinearLayout.LayoutParams(dp(37),-1));
  Button magnify=button(zoom+"%",false,()->{Accounts.display(this,slot,mode,zoom==100?125:100);applyViewport();rendered="";ui.removeCallbacks(refresh);ui.post(refresh);});magnify.setContentDescription("调整界面缩放");bar.addView(magnify,new LinearLayout.LayoutParams(dp(38),-1));
  if(!QqExtension.gameRunning()){Button reload=button("重载选区",false,()->QqExtension.command("selection_requested",""));bar.addView(reload,new LinearLayout.LayoutParams(dp(58),-1));}
  Button more=button("⋮",false,()->menu());more.setContentDescription("登录与选区菜单");bar.addView(more,new LinearLayout.LayoutParams(dp(36),-1));applyViewport();
 }
 private static String speedLabel(double speed){return speed==0.5?"0.5x":(int)speed+"x";}
 private void showSpeedMenu(View anchor,double current){
  PopupMenu menu=new PopupMenu(this,anchor);
  for(int i=0;i<Accounts.SPEED_OPTIONS.length;i++)menu.getMenu().add(0,i,i,speedLabel(Accounts.SPEED_OPTIONS[i]));
  menu.getMenu().setGroupCheckable(0,true,true);
  for(int i=0;i<Accounts.SPEED_OPTIONS.length;i++)if(Accounts.SPEED_OPTIONS[i]==current)menu.getMenu().getItem(i).setChecked(true);
  menu.setOnMenuItemClickListener(item->{Accounts.speed(this,slot,Accounts.SPEED_OPTIONS[item.getItemId()],all);rendered="";ui.removeCallbacks(refresh);ui.post(refresh);return true;});
  menu.show();
 }
 private void menu(){PopupMenu menu=new PopupMenu(this,bar.getChildAt(bar.getChildCount()-1));String[] items={"返回选区","账号密码登录","手机 QQ 登录","关闭当前账号","锁屏联网设置","分享诊断日志","保存诊断日志","重启当前账号"};for(int i=0;i<items.length;i++)menu.getMenu().add(0,i,i,items[i]);menu.setOnMenuItemClickListener(item->{if(item.getItemId()==0)QqExtension.command("selection_requested","");else if(item.getItemId()==1)QqExtension.command("login_requested","password");else if(item.getItemId()==2)QqExtension.command("login_requested","qq");else if(item.getItemId()==3)close(slot);else if(item.getItemId()==4)QqExtension.backgroundSettings(this);else if(item.getItemId()==5){SurfaceView surface=game==null?null:findSurface(game);Diagnostics.record(this,"share_viewport "+(viewport==null?"none":viewport.getWidth()+"x"+viewport.getHeight())+" surface="+(surface==null?"none":surface.getHolder().getSurfaceFrame()+" valid="+surface.getHolder().getSurface().isValid()));Diagnostics.share(this);}else if(item.getItemId()==6)Diagnostics.chooseSaveLocation(this);else restartAccount();return true;});menu.show();}
 private void restartAccount(){new android.app.AlertDialog.Builder(this).setTitle("重启当前账号？").setMessage("保留登录和授权资料，重新打开选区页面。").setNegativeButton("取消",null).setPositiveButton("重启",(dialog,which)->{Diagnostics.record(this,"manual_restart");Accounts.speed(this,slot,1,false);startActivity(new Intent(this,QqExtension.RestartActivity.class).putExtra("oldPid",android.os.Process.myPid()).putExtra("slot",slot));finish();}).show();}
 private void close(int id){Accounts.close(this,id);if(id!=slot){rendered="";return;}JSONObject root=Accounts.snapshot(this);JSONArray list=root.optJSONArray("slots");for(int i=0;i<list.length();i++){JSONObject p=list.optJSONObject(i);if(p.optBoolean("open")){Accounts.open(this,p.optInt("slot"));closeOwn();return;}}closeOwn();}
 private void closeOwn(){if(closing)return;closing=true;QqExtension.command("speed_changed","1");stopService(new Intent(this,Accounts.service(slot)));finish();new Handler(Looper.getMainLooper()).postDelayed(()->android.os.Process.killProcess(android.os.Process.myPid()),250);}
 public static class Service0 extends QqExtension.SessionService{}
 public static class Service1 extends QqExtension.SessionService{}
 public static class Service2 extends QqExtension.SessionService{}
 public static class Service3 extends QqExtension.SessionService{}
 public static class Service4 extends QqExtension.SessionService{}
 public static class Service5 extends QqExtension.SessionService{}
 public static class Service6 extends QqExtension.SessionService{}
 public static class Service7 extends QqExtension.SessionService{}
}
