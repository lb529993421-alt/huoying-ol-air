package local.huoying.air.auth;

import android.app.Activity;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.widget.Toast;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Bounded diagnostic files only; account catalogues, cookies and keys are never shared. */
public final class Diagnostics {
 private static final int LIMIT=512*1024;
 private static final long DAY_MS=24L*60*60*1000;
 static final int SAVE_REQUEST=5201;
 private static final String REPORT_NAME="huoying-diagnostics-[0-9]+\\.zip";
 private static long lastCleanup;
 static synchronized void record(Context context,String event){
  long now=System.currentTimeMillis();
  if(now<lastCleanup||now-lastCleanup>=DAY_MS){
   lastCleanup=now;
   try{removeExpired(new File(context.getApplicationInfo().dataDir),shareDirectory(context),now);}catch(IOException ignored){}
  }
  try{File target=new File(context.getFilesDir(),"air-native-"+android.os.Process.myPid()+".log");if(target.length()>LIMIT){File previous=new File(target.getPath()+".previous");if(previous.exists()&&!previous.delete())return;if(!target.renameTo(previous))return;}
   try(FileOutputStream stream=new FileOutputStream(target,true)){stream.write((now+" "+clean(event)+"\n").getBytes(StandardCharsets.UTF_8));}
  }catch(IOException ignored){}
 }
 static void removeExpired(File data,File reports,long now)throws IOException{
  List<File> logs=new ArrayList<>();
  collect(data.getCanonicalFile(),5,logs,new int[]{0});
  File root=reports.getCanonicalFile();
  File[] shared=root.listFiles();
  if(shared!=null)for(File report:shared){
   if(report.getName().matches(REPORT_NAME)&&report.isFile()&&report.equals(report.getCanonicalFile()))logs.add(report);
  }
  long cutoff=now-7*DAY_MS;
  for(File log:logs){long modified=log.lastModified();if(modified>0&&modified<cutoff)log.delete();}
 }
 static String clean(String text){return text.replaceAll("https?://[^\\s\"<>]+","[URL]").replaceAll("(?i)(password|access_token|skey|cookie|openid)[=:][^\\s,\"}]+","$1=[redacted]");}
 private static void collect(File dir,int depth,List<File> found,int[] visited)throws IOException{
  if(depth<0||++visited[0]>150)return;File[] entries=dir.listFiles();if(entries==null)return;
  for(File entry:entries){if(!entry.getCanonicalPath().equals(entry.getAbsolutePath()))continue;
   String name=entry.getName();if(entry.isFile()&&name.matches("air-(diagnostics-[0-9]+-[0-9]+\\.jsonl|native-[0-9]+\\.log(\\.previous)?)"))found.add(entry);
   else if(entry.isDirectory()&&!name.contains("webview")&&!name.equals("cache")&&!name.equals("code_cache")&&!name.equals("no_backup")&&!name.equals("diagnostic-share"))collect(entry,depth-1,found,visited);
  }
 }
 private static void entry(ZipOutputStream zip,String name,String text)throws IOException{zip.putNextEntry(new ZipEntry(name));zip.write(text.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
 private static void writeReport(Context context,OutputStream destination)throws Exception{
  try(ZipOutputStream zip=new ZipOutputStream(destination)){
   List<File> logs=new ArrayList<>();
   collect(new File(context.getApplicationInfo().dataDir).getCanonicalFile(),5,logs,new int[]{0});
   logs.sort((a,b)->Long.compare(b.lastModified(),a.lastModified()));
   String version=context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName;
   entry(zip,"device.txt","app="+version+"\nandroid="+Build.VERSION.RELEASE+" sdk="+Build.VERSION.SDK_INT+"\nmodel="+Build.MANUFACTURER+" "+Build.MODEL+"\n");
   for(int i=0;i<Math.min(24,logs.size());i++){
    File file=logs.get(i);byte[] tail;
    try(RandomAccessFile input=new RandomAccessFile(file,"r")){
     long size=input.length();input.seek(Math.max(0,size-LIMIT));tail=new byte[(int)Math.min(size,LIMIT)];input.readFully(tail);
    }catch(FileNotFoundException|EOFException rotated){continue;}
    entry(zip,"logs/"+i+"-"+file.getName(),clean(new String(tail,StandardCharsets.UTF_8)));
   }
  }
 }
 static void chooseSaveLocation(Activity activity){
  Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
    .setType("application/zip").putExtra(Intent.EXTRA_TITLE,"huoying-diagnostics-"+System.currentTimeMillis()+".zip");
  try{activity.startActivityForResult(intent,SAVE_REQUEST);}
  catch(ActivityNotFoundException error){Toast.makeText(activity,"系统文件选择器不可用，请用分享诊断日志",Toast.LENGTH_LONG).show();}
 }
 static void save(Activity activity,Uri destination){
  if(destination==null||!"content".equals(destination.getScheme()))return;
  record(activity,"save_requested");
  new Thread(()->{
   String message;
   try(OutputStream output=activity.getContentResolver().openOutputStream(destination,"w")){
    if(output==null)throw new IOException("document unavailable");
    writeReport(activity,output);message="诊断日志已保存";
   }catch(Exception error){record(activity,"save_failed "+error.getClass().getSimpleName());message="保存诊断日志失败，请重试";}
   final String result=message;
   activity.runOnUiThread(()->Toast.makeText(activity,result,Toast.LENGTH_LONG).show());
  },"huoying-save-diagnostics").start();
 }
 private static File shareDirectory(Context context){return new File(context.getFilesDir(),"diagnostic-share");}
 static File reportFile(File directory,String name)throws IOException{
  if(name==null||!name.matches(REPORT_NAME))throw new FileNotFoundException();
  File root=directory.getCanonicalFile(),report=new File(root,name);
  if(!report.equals(report.getCanonicalFile())||!report.isFile())throw new FileNotFoundException();
  return report;
 }
 static void share(Activity activity){
  record(activity,"share_requested");Toast.makeText(activity,"正在整理诊断日志…",Toast.LENGTH_SHORT).show();
  new Thread(()->{
   try{File dir=shareDirectory(activity);if(!dir.isDirectory()&&!dir.mkdirs()&&!dir.isDirectory())throw new IOException("directory");
    File report=File.createTempFile("huoying-diagnostics-",".zip",dir);
    writeReport(activity,new FileOutputStream(report));
    Uri uri=new Uri.Builder().scheme("content").authority(activity.getPackageName()+".diagnostics").appendPath(report.getName()).build();
    activity.runOnUiThread(()->{if(activity.isFinishing()||activity.isDestroyed())return;Intent send=new Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);send.setClipData(ClipData.newRawUri("诊断日志",uri));try{activity.startActivity(Intent.createChooser(send,"发送火影诊断日志"));}catch(ActivityNotFoundException error){Toast.makeText(activity,"未找到接收文件的应用，请安装 QQ 或文件管理器",Toast.LENGTH_LONG).show();}});
   }catch(Exception error){record(activity,"share_failed "+error.getClass().getSimpleName());activity.runOnUiThread(()->Toast.makeText(activity,"日志整理失败，请重试",Toast.LENGTH_LONG).show());}
  },"huoying-diagnostics").start();
 }
 public static final class Provider extends ContentProvider {
  public boolean onCreate(){return true;}
  private File file(Uri uri)throws FileNotFoundException{
   if(!"content".equals(uri.getScheme())||!(getContext().getPackageName()+".diagnostics").equals(uri.getAuthority())||uri.getPathSegments().size()!=1)throw new FileNotFoundException();
   try{return reportFile(shareDirectory(getContext()),uri.getLastPathSegment());}catch(IOException error){throw new FileNotFoundException();}
  }
  public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException();return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
  public String getType(Uri uri){return "application/zip";}
  public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){try{File file=file(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(columns);Object[] values=new Object[columns.length];for(int i=0;i<columns.length;i++)values[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i])?file.getName():OpenableColumns.SIZE.equals(columns[i])?file.length():null;cursor.addRow(values);return cursor;}catch(FileNotFoundException error){return null;}}
  public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
  public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
  public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
 }
}
