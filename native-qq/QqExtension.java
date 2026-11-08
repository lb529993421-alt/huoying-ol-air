package local.huoying.air.auth;

import com.adobe.fre.*;
import android.app.*;
import android.content.*;
import android.content.pm.verify.domain.*;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.http.SslError;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.widget.Toast;
import androidx.webkit.*;
import org.json.JSONObject;
import java.security.SecureRandom;
import java.util.*;

/** QQ-only Android handoff for AIR's existing StageWebView; leaves its client callbacks intact. */
public final class QqExtension implements FREExtension {
 private static QqContext current;
 private static String pendingReturn;
 public static boolean hasContext(){return current!=null&&!current.disposed;}
 public static boolean gameRunning(){return hasContext()&&current.keepSession;}
 public static void command(String code,String value){if(hasContext())current.event(code,value);}
 public static void acceptReturn(String raw){if(hasContext())current.returned(raw);else pendingReturn=raw;}
 public static void backgroundSettings(Activity activity){
  PowerManager power=(PowerManager)activity.getSystemService(Context.POWER_SERVICE);
  boolean exempt=power.isIgnoringBatteryOptimizations(activity.getPackageName());
  Intent details=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+activity.getPackageName()));
  try{activity.startActivity(exempt?details:new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,details.getData()));if(exempt)Toast.makeText(activity,"系统省电豁免已开启；小米手机请再将应用省电策略设为无限制，并允许后台联网",Toast.LENGTH_LONG).show();}
  catch(ActivityNotFoundException error){try{activity.startActivity(details);}catch(ActivityNotFoundException missing){Toast.makeText(activity,"请在系统应用信息中允许火影OL后台联网，并将省电策略设为无限制",Toast.LENGTH_LONG).show();}}
 }
 public void initialize(){}
 public FREContext createContext(String type){current=new QqContext();return current;}
 public void dispose(){}

 public static final class QqContext extends FREContext {
  final Handler ui=new Handler(Looper.getMainLooper());
  Activity activity;WebView view;WebViewClient original;ScriptHandler script;AuthUrls.Flow flow;
  String source,pending,loginMode,gameUserAgent;long deadline;boolean waitingSettings,disposed,keepSession;int launched,delivered;
  android.window.OnBackInvokedCallback back;AlertDialog selectionDialog;
  int controlReceived,controlReplied;
  final Application.ActivityLifecycleCallbacks lifecycle=new Application.ActivityLifecycleCallbacks(){
   public void onActivityResumed(Activity a){if(a==activity){if(keepSession)startSession();if(waitingSettings){waitingSettings=false;if(linksAllowed())launch();else notice("请在默认打开设置中允许 ssl.ptlogin2.qq.com，再重新登录");}}}
   public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
  };
  public Map<String,FREFunction> getFunctions(){
   Map<String,FREFunction> map=new HashMap<>();
   map.put("diagnostic",(c,args)->{try{Diagnostics.record(getActivity(),args[0].getAsString());}catch(Exception ignored){}return null;});
   map.put("loginNotice",(c,args)->{try{String message=args[0].getAsString();ui.post(()->notice(message));}catch(Exception ignored){}return null;});
   map.put("licenseActive",(c,args)->{try{return FREObject.newObject(OfflineLicense.isActivated(getActivity()));}catch(Exception e){return null;}});
   map.put("identity",(c,args)->{try{Accounts.identity(getActivity(),Accounts.processSlot(),args[0].getAsString(),args[1].getAsString(),args[2].getAsString());}catch(Exception ignored){}return null;});
   map.put("install",(c,args)->{try{source=args[0].getAsString();activity=getActivity();if(!OfflineLicense.isActivated(activity))return FREObject.newObject(false);ui.post(()->install(0));return FREObject.newObject(true);}catch(Exception e){event("qq_native_error");return null;}});
   map.put("open",(c,args)->{try{String raw=args[0].getAsString();ui.post(()->intercept(raw));return FREObject.newObject(true);}catch(Exception e){event("qq_request_rejected");return null;}});
   map.put("mode",(c,args)->{try{String mode=args[0].getAsString();if(!Arrays.asList("password","qq").contains(mode))return null;ui.post(()->openLoginMode(mode));return FREObject.newObject(true);}catch(Exception e){event("qq_native_error");return null;}});
   map.put("gameUserAgent",(c,args)->{try{return FREObject.newObject(gameUserAgent);}catch(Exception e){return null;}});
   map.put("gameStarted",(c,args)->{ui.post(()->{keepSession=true;enableBack();startSession();if(activity instanceof AirAccountActivity)((AirAccountActivity)activity).applyViewport();});try{return FREObject.newObject(true);}catch(Exception e){return null;}});
   map.put("returnToSelection",(c,args)->{ui.post(this::selectAgain);return null;});
   map.put("controlReply",(c,args)->{try{String reply=args[0].getAsString();if(++controlReplied<=200)android.util.Log.i("HuoyingAIR","control reply "+controlReplied+" bytes="+reply.length());if(reply.length()<2000000)ui.post(()->gameScript("window.airControlResult=JSON.parse("+JSONObject.quote(reply)+");void(0)"));else event("control_reply_too_large");}catch(Exception e){event("control_reply_failed");}return null;});
   map.put("gameScript",(c,args)->{try{String script=args[0].getAsString();if(script.length()<2000000)ui.post(()->gameScript(script));}catch(Exception e){event("game_notify_failed");}return null;});
   map.put("status",(c,args)->{try{return FREObject.newObject(new JSONObject().put("installed",view!=null).put("waitingSettings",waitingSettings).put("launched",launched).put("delivered",delivered).put("active",flow!=null).toString());}catch(Exception e){return null;}});
   return map;
  }
  void event(String code){event(code,"");}
  void event(String code,String value){if(!disposed)try{dispatchStatusEventAsync(code,value);}catch(Exception ignored){}}
  void notice(String text){if(activity!=null)Toast.makeText(activity,text,Toast.LENGTH_LONG).show();}
  void gameScript(String script){if(disposed||view==null)return;Uri page=Uri.parse(view.getUrl()==null?"":view.getUrl());if("https".equals(page.getScheme())&&"game.huoying.qq.com".equals(page.getHost()))view.evaluateJavascript(script,null);}
  void enableBack(){if(Build.VERSION.SDK_INT>=33&&back==null){back=this::selectAgain;activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,back);}}
  void selectAgain(){
   if(disposed||activity==null||activity.isFinishing()||selectionDialog!=null)return;
   selectionDialog=new AlertDialog.Builder(activity).setTitle("返回选区？").setMessage("将结束当前游戏并保留 QQ 登录状态。").setNegativeButton("取消",null).setPositiveButton("返回选区",(dialog,which)->{
    CookieManager.getInstance().flush();int slot=Accounts.processSlot();Accounts.speed(activity,slot,1,false);activity.startActivity(new Intent(activity,RestartActivity.class).putExtra("oldPid",android.os.Process.myPid()).putExtra("slot",slot));activity.finish();
   }).create();selectionDialog.setOnDismissListener(dialog->selectionDialog=null);selectionDialog.show();
  }
  void startSession(){
   if(disposed||activity==null||!keepSession||!OfflineLicense.isActivated(activity))return;
   ActivityManager.RunningAppProcessInfo process=new ActivityManager.RunningAppProcessInfo();ActivityManager.getMyMemoryState(process);
   if(process.importance>ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)return;
   try{
    Intent service=new Intent(activity,Accounts.service(Accounts.processSlot()));if(Build.VERSION.SDK_INT>=26)activity.startForegroundService(service);else activity.startService(service);
    if(Build.VERSION.SDK_INT>=33&&activity.checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED){
     android.content.SharedPreferences preferences=activity.getSharedPreferences("air-runtime",Context.MODE_PRIVATE);
     if(!preferences.getBoolean("notificationAsked",false)){preferences.edit().putBoolean("notificationAsked",true).apply();activity.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},4331);}
    }
   }catch(Exception error){event("background_start_failed");notice("后台保持连接未启动，请先保持游戏在前台");}
  }
  WebView find(View root){if(root instanceof WebView)return (WebView)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){WebView v=find(g.getChildAt(i));if(v!=null)return v;}}return null;}
  void install(int attempt){
   if(disposed)return;
   if(view!=null){event("qq_ready");return;}
   WebView candidate=find(activity.getWindow().getDecorView());
   if(candidate==null){if(attempt<100)ui.postDelayed(()->install(attempt+1),100);else event("qq_native_error");return;}
   try{
    // The official desktop OAuth callback has no mobile s_url parameter.
    gameUserAgent=candidate.getSettings().getUserAgentString();
    candidate.getSettings().setUserAgentString(gameUserAgent.replaceAll("\\(Linux;[^)]*Android[^)]*\\)","(X11; Linux x86_64)").replace(" Version/4.0","").replace(" Mobile","")+" HuoyingAIR/0.2");
    candidate.getSettings().setDomStorageEnabled(true);
    candidate.getSettings().setUseWideViewPort(true);candidate.getSettings().setLoadWithOverviewMode(true);candidate.getSettings().setBuiltInZoomControls(true);candidate.getSettings().setDisplayZoomControls(false);
    CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(candidate,true);
    if(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)){
     try{script=WebViewCompat.addDocumentStartJavaScript(candidate,source,new HashSet<>(Arrays.asList("https://huoying.qq.com","https://graph.qq.com","https://xui.ptlogin2.qq.com","https://ssl.ptlogin2.qq.com")));}
     catch(RuntimeException error){Diagnostics.record(activity,"login_script_unavailable "+error.getClass().getSimpleName());}
    }
    original=candidate.getWebViewClient();view=candidate;view.setWebViewClient(new Client(this,original));
    Diagnostics.record(activity,script==null?"login_web_fallback":"login_document_start_ready");
    activity.getApplication().registerActivityLifecycleCallbacks(lifecycle);event("qq_ready");if(pendingReturn!=null){String raw=pendingReturn;pendingReturn=null;returned(raw);}
   }catch(RuntimeException error){Diagnostics.record(activity,"login_webview_setup_failed "+error.getClass().getSimpleName());event("qq_native_error");}
  }
  void openLoginMode(String mode){
   if(disposed||view==null){event("qq_native_error");return;}
   flow=null;pending=null;waitingSettings=false;loginMode=mode;
   if("https://huoying.qq.com/server/website/".equals(view.getUrl()))applyMode(0);
   else view.loadUrl("https://huoying.qq.com/server/website/");
  }
  boolean linksAllowed(){
   if(Build.VERSION.SDK_INT<35)return false;
   try{DomainVerificationManager m=activity.getSystemService(DomainVerificationManager.class);DomainVerificationUserState s=m.getDomainVerificationUserState(activity.getPackageName());Integer state=s==null?null:s.getHostToStateMap().get("ssl.ptlogin2.qq.com");return s!=null&&s.isLinkHandlingAllowed()&&state!=null&&(state==DomainVerificationUserState.DOMAIN_STATE_SELECTED||state==DomainVerificationUserState.DOMAIN_STATE_VERIFIED);}catch(Exception e){return false;}
  }
  void applyMode(int attempt){
   final String mode=loginMode;if(mode==null||disposed||view==null)return;
   if(script==null){
    loginMode=null;
    view.evaluateJavascript("(function(){if(location.origin==='https://huoying.qq.com'){var link=document.getElementById('dologin');if(link)link.click();}})()",null);
    notice("请在官网登录窗口选择账号密码或 QQ 扫码");
    return;
   }
   view.evaluateJavascript("(()=>{if(location.href!=='https://huoying.qq.com/server/website/'||document.readyState==='loading'||!window.airQqReturn)return false;window.airQqReturn.mode("+JSONObject.quote(mode)+");return true;})()",value->{
    if(!mode.equals(loginMode))return;
    if("true".equals(value))loginMode=null;
    else if(attempt<40)ui.postDelayed(()->applyMode(attempt+1),250);
    else{loginMode=null;notice("官网登录窗口未自动打开，请点击网页上的登录按钮");}
   });
  }
  boolean intercept(String raw){
   if(raw!=null&&(raw.startsWith("huoyingair-control:")||raw.startsWith("huoyingair-session:"))){
    if(disposed||view==null)return true;
    // Cancel local commands before WebView can replace the page with an unknown-scheme error.
    Uri page=Uri.parse(view.getUrl()==null?"":view.getUrl());
    boolean session=raw.startsWith("huoyingair-session:");
    if(raw.length()<(session?131072:8192)&&"https".equals(page.getScheme())&&"game.huoying.qq.com".equals(page.getHost())){if(!session&&++controlReceived<=200)android.util.Log.i("HuoyingAIR","control received "+controlReceived);try{dispatchStatusEventAsync(session?"game_session":"game_control",raw);}catch(Exception error){android.util.Log.w("HuoyingAIR","Bridge context unavailable: "+error.getClass().getSimpleName());}}
    return true;
   }
   if(raw==null||!raw.startsWith("wtloginmqq:"))return false;
   try{
    Uri parent=Uri.parse(view.getUrl());if(!"https".equals(parent.getScheme())||!Arrays.asList("huoying.qq.com","graph.qq.com","xui.ptlogin2.qq.com","ssl.ptlogin2.qq.com").contains(parent.getHost()))throw new IllegalArgumentException();
    if(flow!=null&&SystemClock.elapsedRealtime()<deadline)return true;
    byte[] bytes=new byte[16];new SecureRandom().nextBytes(bytes);StringBuilder key=new StringBuilder();for(byte b:bytes)key.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
    flow=AuthUrls.prepare(raw,key.toString());Accounts.nonce(activity,Accounts.processSlot(),flow.nonce);deadline=SystemClock.elapsedRealtime()+600000;pending=raw;
    if(flow.u1!=null&&(script==null||!linksAllowed())){
     if(Build.VERSION.SDK_INT<35||script==null){openLoginMode("password");notice("当前系统不支持 QQ 选账号回跳，已切换到网页登录");return true;}
     new AlertDialog.Builder(activity).setTitle("允许 QQ 登录返回火影OL")
      .setMessage("请在默认打开设置中允许受支持的链接，并选中 ssl.ptlogin2.qq.com。返回后会继续打开 QQ 选账号。")
      .setPositiveButton("去设置",(d,w)->{waitingSettings=true;try{activity.startActivity(new Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,Uri.parse("package:"+activity.getPackageName())));}catch(Exception e){waitingSettings=false;flow=null;notice("请在应用信息的默认打开设置中允许登录链接");}})
      .setNeutralButton("网页登录",(d,w)->openLoginMode("password"))
      .setNegativeButton("取消",(d,w)->{flow=null;pending=null;}).setOnCancelListener(d->{flow=null;pending=null;}).show();event("qq_link_setup");
    }else launch();
   }catch(Exception e){flow=null;pending=null;event("qq_request_rejected");notice("未能发起 QQ 授权，可在菜单选择账号密码登录");}
   return true;
  }
  void launch(){
   if(flow==null||disposed||SystemClock.elapsedRealtime()>deadline)return;
   try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(flow.launch)).setPackage("com.tencent.mobileqq").addCategory(Intent.CATEGORY_BROWSABLE));pending=null;launched++;event("qq_launched");}
   catch(Exception e){flow=null;pending=null;notice("未找到手机 QQ，请先安装 QQ 或使用网页登录");event("qq_launch_failed");}
  }
  void returned(String raw){
   if(flow==null||SystemClock.elapsedRealtime()>deadline){flow=null;pending=null;event("qq_callback_expired");notice("登录会话已过期，请在火影OL 中重新发起 QQ 登录");return;}
   try{
    String target=AuthUrls.returned(raw,flow);flow=null;pending=null;waitingSettings=false;delivered++;
    if(target!=null)deliver(target,0);else event("qq_returned");
   }catch(Exception e){flow=null;pending=null;waitingSettings=false;event("qq_callback_rejected");notice("QQ 返回信息校验失败，请重新登录或选择账号密码登录");}
  }
  void deliver(String target,int attempt){
   if(disposed||view==null)return;
   view.evaluateJavascript("window.airQqReturn?.accepted===true || location.hostname==='game.huoying.qq.com'",value->{
    if("true".equals(value)){event("qq_returned");return;}
    if(attempt>=30){event("qq_return_not_accepted");notice("登录页尚未接到 QQ 返回，请重新发起登录");return;}
    view.evaluateJavascript("window.airQqReturn?.accept("+JSONObject.quote(target)+")",null);ui.postDelayed(()->deliver(target,attempt+1),250);
   });
  }
  public void dispose(){disposed=true;flow=null;pending=null;keepSession=false;ui.post(()->{if(activity!=null){if(selectionDialog!=null)selectionDialog.dismiss();if(Build.VERSION.SDK_INT>=33&&back!=null)activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(back);activity.stopService(new Intent(activity,Accounts.service(Accounts.processSlot())));activity.getApplication().unregisterActivityLifecycleCallbacks(lifecycle);}if(script!=null)script.remove();if(view!=null&&original!=null)view.setWebViewClient(original);view=null;activity=null;});if(current==this)current=null;}
 }

 /** User-visible session ownership, following the existing Android client's GameSessionService. */
 public static class SessionService extends Service {
  static volatile boolean running;
  private static final String STOP="local.huoying.air.STOP_SESSION",CHANNEL="air-game-session";
  private PowerManager.WakeLock wake;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private ConnectivityManager network;private boolean screenRegistered,networkRegistered;
  private final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){state(intent.getAction());if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction()))handler.postDelayed(()->state("screen_off_5s"),5000);}};
  private final ConnectivityManager.NetworkCallback connection=new ConnectivityManager.NetworkCallback(){public void onAvailable(Network value){handler.post(()->state("network_available"));}public void onLost(Network value){handler.post(()->state("network_lost"));}public void onBlockedStatusChanged(Network value,boolean blocked){handler.post(()->state("network_blocked_"+blocked));}};
  private final Runnable renew=new Runnable(){public void run(){if(wake!=null){wake.acquire(600000);handler.postDelayed(this,540000);}}};
  public IBinder onBind(Intent intent){return null;}
  public void onCreate(){super.onCreate();if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"游戏后台连接",NotificationManager.IMPORTANCE_LOW));
   IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);filter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
   if(Build.VERSION.SDK_INT>=33)registerReceiver(screen,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(screen,filter);screenRegistered=true;
   network=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);try{network.registerDefaultNetworkCallback(connection);networkRegistered=true;}catch(SecurityException error){android.util.Log.w("HuoyingAIR","Network diagnostics permission unavailable");}
  }
  private void state(String cause){
   PowerManager power=(PowerManager)getSystemService(Context.POWER_SERVICE);Network active=network==null?null:network.getActiveNetwork();NetworkCapabilities caps=active==null?null:network.getNetworkCapabilities(active);
   String transport=caps==null?"none":caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?"wifi":caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)?"cellular":"other";
   String detail=cause+" at="+System.currentTimeMillis()+" screen="+power.isInteractive()+" idle="+power.isDeviceIdleMode()+" exempt="+power.isIgnoringBatteryOptimizations(getPackageName())+" cpu="+(wake!=null&&wake.isHeld())+" net="+transport;
   android.util.Log.i("HuoyingAIR",detail);if(current!=null)current.event("background_state",detail);
  }
  public int onStartCommand(Intent intent,int flags,int startId){
   if(intent!=null&&STOP.equals(intent.getAction())){if(current!=null)current.keepSession=false;stopSelf();return START_NOT_STICKY;}
   if(current==null||!current.keepSession||!OfflineLicense.isActivated(this)){stopSelf();return START_NOT_STICKY;}
   Intent launch=Accounts.intent(this,Accounts.processSlot());
   PendingIntent open=PendingIntent.getActivity(this,Accounts.processSlot(),launch.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,getClass()).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   Notification.Builder notification=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
   boolean exempt=((PowerManager)getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName());
   notification.setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("火影OL · 账号 "+(Accounts.processSlot()+1)+" 后台连接").setContentText(exempt?"点击返回游戏；停止后将不再保持后台连接":"锁屏联网尚未豁免：请点游戏 ⋮ → 锁屏联网设置").setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"停止后台连接",stop).build());
   if(Build.VERSION.SDK_INT>=34)startForeground(731+Accounts.processSlot(),notification.build(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(731+Accounts.processSlot(),notification.build());
   if(wake==null){wake=((PowerManager)getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"HuoyingAIR:Session");wake.setReferenceCounted(false);renew.run();}
   if(!running&&!exempt)current.notice("锁屏联网需允许不受电量限制，请点 ⋮ → 锁屏联网设置");running=true;state("session_started");current.event("background_started");return START_NOT_STICKY;
  }
  public void onTaskRemoved(Intent intent){if(current!=null)current.keepSession=false;stopSelf();new Handler(Looper.getMainLooper()).postDelayed(()->android.os.Process.killProcess(android.os.Process.myPid()),250);}
  public void onDestroy(){running=false;if(screenRegistered)unregisterReceiver(screen);screenRegistered=false;if(networkRegistered)network.unregisterNetworkCallback(connection);networkRegistered=false;handler.removeCallbacksAndMessages(null);if(wake!=null&&wake.isHeld())wake.release();wake=null;if(Build.VERSION.SDK_INT>=24)stopForeground(STOP_FOREGROUND_REMOVE);else stopForeground(true);if(current!=null)current.event("background_stopped");super.onDestroy();}
 }

 public static class ReturnActivity extends Activity {
  void accept(Intent intent){
   Uri u=intent==null?null:intent.getData();if(intent!=null)intent.setData(null);
   if(u!=null&&AuthUrls.callbackNonce(u.toString())!=null){
    String nonce=AuthUrls.callbackNonce(u.toString());int slot=Accounts.callbackSlot(this,nonce);
    if(OfflineLicense.isActivated(this)&&slot>=0)startActivity(Accounts.intent(this,slot).putExtra("qqReturn",u.toString()));
    else{startActivity(new Intent(this,AirProduct.Launcher.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));Toast.makeText(this,"登录会话已过期，请重新发起 QQ 登录",Toast.LENGTH_LONG).show();}
   }
   finish();
  }
  protected void onCreate(Bundle state){super.onCreate(state);accept(getIntent());}
  protected void onNewIntent(Intent intent){super.onNewIntent(intent);accept(intent);}
 }

 /** A fresh VM is required: unloading the movie leaves the game's static models and sockets alive. */
 public static final class RestartActivity extends Activity {
  boolean launched;
  protected void onCreate(Bundle state){super.onCreate(state);android.widget.TextView text=new android.widget.TextView(this);text.setText("正在返回选区…");text.setTextSize(22);text.setTextColor(0xffffffff);text.setGravity(Gravity.CENTER);text.setBackgroundColor(0xff111827);setContentView(text);}
  protected void onResume(){super.onResume();if(launched)return;launched=true;
   int oldPid=getIntent().getIntExtra("oldPid",-1),slot=getIntent().getIntExtra("slot",-1);boolean owned=false;
   List<ActivityManager.RunningAppProcessInfo> processes=((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getRunningAppProcesses();
   if(processes!=null)for(ActivityManager.RunningAppProcessInfo p:processes)if(p.pid==oldPid&&p.uid==android.os.Process.myUid()&&(getPackageName()+":account"+slot).equals(p.processName))owned=true;
   if(!owned||slot<0||slot>=Accounts.LIMIT||!OfflineLicense.isActivated(this)){finish();return;}
   android.os.Process.killProcess(oldPid);
   awaitExit(oldPid,slot,0);
  }
  void awaitExit(int oldPid,int slot,int attempt){
   List<ActivityManager.RunningAppProcessInfo> processes=((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getRunningAppProcesses();boolean alive=false;
   if(processes!=null)for(ActivityManager.RunningAppProcessInfo p:processes)if(p.pid==oldPid&&p.uid==android.os.Process.myUid())alive=true;
   if(alive){if(attempt<50)new Handler(Looper.getMainLooper()).postDelayed(()->awaitExit(oldPid,slot,attempt+1),100);else finish();return;}
   startActivity(Accounts.intent(this,slot));finish();
  }
 }

 /** Delegate every AIR callback so game and AS3 navigation behavior remain intact. */
 static final class Client extends WebViewClient {
  final QqContext c;final WebViewClient d;Client(QqContext c,WebViewClient d){this.c=c;this.d=d;}
  public boolean shouldOverrideUrlLoading(WebView v,String u){return c.intercept(u)||d.shouldOverrideUrlLoading(v,u);}
  public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return c.intercept(r.getUrl().toString())||d.shouldOverrideUrlLoading(v,r);}
  public void onPageStarted(WebView v,String u,Bitmap b){d.onPageStarted(v,u,b);}
  public void onPageFinished(WebView v,String u){d.onPageFinished(v,u);if("https://huoying.qq.com/server/website/".equals(u))c.applyMode(0);}
  public void onPageCommitVisible(WebView v,String u){d.onPageCommitVisible(v,u);}
  public void onLoadResource(WebView v,String u){d.onLoadResource(v,u);}
  public WebResourceResponse shouldInterceptRequest(WebView v,String u){return d.shouldInterceptRequest(v,u);}
  public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return d.shouldInterceptRequest(v,r);}
  public void onReceivedError(WebView v,int code,String desc,String url){d.onReceivedError(v,code,desc,url);}
  public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())Diagnostics.record(v.getContext(),"login_page_error "+e.getErrorCode());d.onReceivedError(v,r,e);}
  public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse e){d.onReceivedHttpError(v,r,e);}
  public void onReceivedSslError(WebView v,SslErrorHandler h,SslError e){d.onReceivedSslError(v,h,e);}
  public void onReceivedClientCertRequest(WebView v,ClientCertRequest r){d.onReceivedClientCertRequest(v,r);}
  public void onReceivedHttpAuthRequest(WebView v,HttpAuthHandler h,String host,String realm){d.onReceivedHttpAuthRequest(v,h,host,realm);}
  public void onFormResubmission(WebView v,Message no,Message yes){d.onFormResubmission(v,no,yes);}
  public void doUpdateVisitedHistory(WebView v,String u,boolean reload){d.doUpdateVisitedHistory(v,u,reload);}
  public boolean shouldOverrideKeyEvent(WebView v,KeyEvent e){return d.shouldOverrideKeyEvent(v,e);}
  public void onUnhandledKeyEvent(WebView v,KeyEvent e){d.onUnhandledKeyEvent(v,e);}
  public void onScaleChanged(WebView v,float old,float now){d.onScaleChanged(v,old,now);}
  public void onReceivedLoginRequest(WebView v,String realm,String account,String args){d.onReceivedLoginRequest(v,realm,account,args);}
  public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail e){return d.onRenderProcessGone(v,e);}
  public void onSafeBrowsingHit(WebView v,WebResourceRequest r,int t,SafeBrowsingResponse response){d.onSafeBrowsingHit(v,r,t,response);}
 }
}
