package local.huoying.air.auth;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.*;
import android.widget.*;

public final class AirProduct {
 public static final class Launcher extends Activity {
  private void enter(){int slot=Accounts.defaultSlot(this);if(slot<0){Toast.makeText(this,"账号正在关闭，请稍后重试",Toast.LENGTH_LONG).show();return;}Accounts.open(this,slot);finish();}
  public void onCreate(Bundle state){
   super.onCreate(state);if(OfflineLicense.isActivated(this)){enter();return;}
   getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
   int pad=(int)(20*getResources().getDisplayMetrics().density);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(pad,pad,pad,pad);body.setBackgroundColor(0xff111827);ScrollView scroll=new ScrollView(this);scroll.addView(body);setContentView(scroll);
   text(body,"火影OL · 离线激活",24);text(body,"复制设备申请码给授权人，粘贴激活码后即可进入。",16);
   final String request;try{request=OfflineLicense.requestCode();}catch(Exception error){text(body,"本机密钥创建失败，请重新启动后重试。",16);return;}
   TextView code=text(body,request,14);code.setTextIsSelectable(true);code.setContentDescription("设备申请码");TextView status=text(body,"",14);status.setContentDescription("激活结果");
   Button copy=new Button(this);copy.setText("复制设备申请码");body.addView(copy);copy.setOnClickListener(v->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("设备申请码",request));status.setText("申请码已复制");});
   EditText input=new EditText(this);input.setTextColor(0xffffffff);input.setHintTextColor(0xff9ca3af);input.setHint("粘贴 HL1- 开头的激活码");input.setContentDescription("激活码");input.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_ACTION_DONE);input.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_DONE){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);return true;}return false;});input.setSingleLine(false);input.setMinLines(2);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1024)});body.addView(input);
   Button activate=new Button(this);activate.setText("激活并进入");body.addView(activate);activate.setOnClickListener(v->{try{if(OfflineLicense.activate(this,input.getText().toString()))enter();else status.setText("激活码无效或不属于本机");}catch(Exception error){status.setText("授权保存失败，请重试");}});
   text(body,"授权绑定本次安装。覆盖升级可保留授权；卸载、清除数据或换机后需重新申请。",14);
  }
  private TextView text(LinearLayout parent,String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(0xffffffff);t.setPadding(0,6,0,8);parent.addView(t);return t;}
 }
}
