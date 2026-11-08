package local.huoying.air.auth;

import android.app.*;
import android.content.*;
import android.os.Process;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;

/** Small cross-process account catalogue. No cookies, passwords or OAuth tickets are stored here. */
public final class Accounts {
 public static final int LIMIT=8;
 static final double[] SPEED_OPTIONS={0.5,1,2,3,5};
 interface Change<T>{T run(JSONObject root)throws Exception;}
 private static synchronized <T>T access(Context context,boolean write,Change<T> change){
  try(RandomAccessFile guard=new RandomAccessFile(new File(context.getNoBackupFilesDir(),"accounts.lock"),"rw");FileLock lock=guard.getChannel().lock()){
   AtomicFile file=new AtomicFile(new File(context.getNoBackupFilesDir(),"accounts-v1.json"));JSONObject root;
   if(file.getBaseFile().exists()||new File(file.getBaseFile().getPath()+".bak").exists()){try(InputStream in=file.openRead()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[1024];int n;while((n=in.read(bytes))!=-1){if(out.size()+n>65536)throw new IOException("account catalogue too large");out.write(bytes,0,n);}root=new JSONObject(out.toString("UTF-8"));}}
   else root=new JSONObject().put("version",1).put("slots",new JSONArray()).put("selected",0);
   if(root.getInt("version")!=1||root.getJSONArray("slots").length()>LIMIT)throw new IOException("invalid account catalogue");
   T result=change.run(root);
   if(write){FileOutputStream out=file.startWrite();try{out.write(root.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}catch(Exception error){file.failWrite(out);throw error;}}
   return result;
  }catch(Exception error){throw new IllegalStateException("account catalogue unavailable",error);}
 }
 static JSONObject find(JSONObject root,int slot)throws Exception{if(slot<0||slot>=LIMIT)throw new IllegalArgumentException("invalid account slot");JSONArray list=root.getJSONArray("slots");for(int i=0;i<list.length();i++)if(list.getJSONObject(i).getInt("slot")==slot)return list.getJSONObject(i);return null;}
 public static int processSlot(){String name=Application.getProcessName();String suffix=name.substring(name.lastIndexOf(':')+1);return suffix.matches("account[0-7]")?Integer.parseInt(suffix.substring(7)):-1;}
 public static JSONObject snapshot(Context c){return access(c,false,r->{JSONObject copy=new JSONObject(r.toString());java.util.Set<String> names=new java.util.HashSet<>();java.util.List<ActivityManager.RunningAppProcessInfo> processes=((ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE)).getRunningAppProcesses();if(processes!=null)for(ActivityManager.RunningAppProcessInfo p:processes)if(p.uid==Process.myUid())names.add(p.processName);JSONArray list=copy.getJSONArray("slots");for(int i=0;i<list.length();i++){JSONObject p=list.getJSONObject(i);p.put("running",names.contains(c.getPackageName()+":account"+p.getInt("slot")));}return copy;});}
 public static JSONObject record(Context c,int slot){return access(c,false,r->{JSONObject p=find(r,slot);return p==null?null:new JSONObject(p.toString());});}
 private static boolean alive(Context c,int slot){java.util.List<ActivityManager.RunningAppProcessInfo> processes=((ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE)).getRunningAppProcesses();if(processes!=null)for(ActivityManager.RunningAppProcessInfo p:processes)if(p.uid==Process.myUid()&&p.processName.equals(c.getPackageName()+":account"+slot))return true;return false;}
 public static int add(Context c){return access(c,true,r->{
  for(int slot=0;slot<LIMIT;slot++){JSONObject p=find(r,slot);if(p!=null&&p.optBoolean("open")||alive(c,slot))continue;
   int generation=p==null?0:p.optInt("generation")+1;if(p==null){p=new JSONObject().put("slot",slot);r.getJSONArray("slots").put(p);}
   p.put("open",true).put("generation",generation).put("speed",1.0).put("displayMode","wide").put("zoom",100).put("label","账号 "+(slot+1)).put("pid",0).remove("nonce");r.put("selected",slot);return slot;
  }return -1;
 });}
 public static int defaultSlot(Context c){JSONObject root=snapshot(c);JSONArray list=root.optJSONArray("slots");int selected=root.optInt("selected",-1);for(int i=0;i<list.length();i++){JSONObject p=list.optJSONObject(i);if(p.optBoolean("open")&&p.optInt("slot")==selected)return selected;}for(int i=0;i<list.length();i++){JSONObject p=list.optJSONObject(i);if(p.optBoolean("open"))return p.optInt("slot");}return add(c);}
 public static void started(Context c,int slot){access(c,true,r->{JSONObject p=find(r,slot);if(p!=null)p.put("speed",1).put("label","账号 "+(slot+1)).put("pid",Process.myPid()).remove("zone");return null;});}
 public static void selected(Context c,int slot){access(c,true,r->{JSONObject p=find(r,slot);if(p==null||!p.optBoolean("open"))throw new IllegalStateException("account closed");r.put("selected",slot);return null;});}
 public static void close(Context c,int slot){access(c,true,r->{JSONObject p=find(r,slot);if(p!=null)p.put("open",false).put("speed",1.0).remove("nonce");return null;});}
 public static boolean speedAllowed(double value){for(double option:SPEED_OPTIONS)if(value==option)return true;return false;}
 public static void speed(Context c,int slot,double value,boolean all){if(!speedAllowed(value))throw new IllegalArgumentException("unsupported speed");access(c,true,r->{JSONArray list=r.getJSONArray("slots");for(int i=0;i<list.length();i++){JSONObject p=list.getJSONObject(i);if(p.optBoolean("open")&&(all||p.getInt("slot")==slot))p.put("speed",value);}return null;});}
 public static void display(Context c,int slot,String mode,int zoom){if(!DisplayLayout.valid(mode,zoom))throw new IllegalArgumentException("unsupported display");access(c,true,r->{JSONObject p=find(r,slot);if(p==null||!p.optBoolean("open"))throw new IllegalArgumentException("closed account");p.put("displayMode",mode).put("zoom",zoom);return null;});}
 public static void identity(Context c,int slot,String account,String zone,String name){if(!account.matches("[0-9]{5,12}")||!zone.matches("[0-9]{1,6}"))return;String clean=name==null?"":name.replaceAll("[\\p{Cntrl}]","").trim();String label=(clean.isEmpty()?account:clean.substring(0,Math.min(24,clean.length())))+" · "+zone;access(c,true,r->{JSONObject p=find(r,slot);if(p!=null)p.put("label",label).put("account",account).put("zone",zone).put("pid",Process.myPid());return null;});}
 public static void nonce(Context c,int slot,String value){if(!value.matches("[a-f0-9]{32}"))throw new IllegalArgumentException();access(c,true,r->{JSONObject p=find(r,slot);if(p!=null)p.put("nonce",value).put("nonceTime",System.currentTimeMillis());return null;});}
 public static int callbackSlot(Context c,String value){return access(c,false,r->{JSONArray list=r.getJSONArray("slots");for(int i=0;i<list.length();i++){JSONObject p=list.getJSONObject(i);long age=System.currentTimeMillis()-p.optLong("nonceTime");if(p.optBoolean("open")&&value.equals(p.optString("nonce"))&&age>=0&&age<=600000)return p.getInt("slot");}return -1;});}
 public static Intent intent(Context c,int slot){if(slot<0||slot>=LIMIT)throw new IllegalArgumentException("invalid account slot");return new Intent().setClassName(c,c.getPackageName()+".AccountEntries$Account"+slot).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);}
 public static void open(Activity activity,int slot){if(!OfflineLicense.isActivated(activity)){activity.startActivity(new Intent(activity,AirProduct.Launcher.class));return;}selected(activity,slot);activity.startActivity(intent(activity,slot));}
 public static Class<?> service(int slot){try{return Class.forName("local.huoying.air.auth.AirAccountActivity$Service"+slot);}catch(Exception error){throw new IllegalArgumentException("invalid account service",error);}}
}
