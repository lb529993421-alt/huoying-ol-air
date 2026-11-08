package local.huoying.air.auth;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.webkit.WebView;
import org.json.JSONObject;

/** Providers initialize before AIR or WebView, in each account's own process. */
public class AccountBootstrap extends ContentProvider {
 public boolean onCreate(){int slot=Accounts.processSlot();if(slot>=0){Context context=getContext();Diagnostics.record(context,"bootstrap slot="+slot);Accounts.started(context,slot);JSONObject profile=Accounts.record(context,slot);int generation=profile==null?0:profile.optInt("generation");if(slot!=0||generation!=0)WebView.setDataDirectorySuffix("account-"+slot+"-"+generation);}return true;}
 public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){return null;}
 public String getType(Uri uri){return null;}
 public Uri insert(Uri uri,ContentValues values){return null;}
 public int delete(Uri uri,String selection,String[] args){return 0;}
 public int update(Uri uri,ContentValues values,String selection,String[] args){return 0;}
 public static final class Slot0 extends AccountBootstrap{}
 public static final class Slot1 extends AccountBootstrap{}
 public static final class Slot2 extends AccountBootstrap{}
 public static final class Slot3 extends AccountBootstrap{}
 public static final class Slot4 extends AccountBootstrap{}
 public static final class Slot5 extends AccountBootstrap{}
 public static final class Slot6 extends AccountBootstrap{}
 public static final class Slot7 extends AccountBootstrap{}
}
