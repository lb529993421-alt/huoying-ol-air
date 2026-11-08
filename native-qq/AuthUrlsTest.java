package local.huoying.air.auth;
import java.net.URI;
public final class AuthUrlsTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static void rejected(Runnable action){try{action.run();throw new AssertionError("accepted invalid callback");}catch(IllegalArgumentException expected){}}
 public static void main(String[] args){
  String nonce="0123456789abcdef0123456789abcdef",jump="https://ssl.ptlogin2.qq.com/jump?u1=https%3A%2F%2Fconnect.qq.com&pt_3rd_aid=102045649&pt_browser=Chrome&pt_openlogin_data=OPAQUE%2BUNCHANGED";
  AuthUrls.Flow p=AuthUrls.prepare("wtloginmqq://ptlogin/qlogin?p="+AuthUrls.encode(jump)+"&schemacallback=googlechrome%3A%2F%2F",nonce);
  String target=AuthUrls.one(URI.create(p.launch),"p");check(target.contains("pt_openlogin_data=OPAQUE%2BUNCHANGED"));check(target.contains("pt_package=air.local.huoying.airgame"));check(!p.launch.contains("schemacallback"));check(!target.contains("Chrome"));
  String back=target+"&clientuin=12345678&clientkey=QA_ONLY&keyindex=19";String accepted=AuthUrls.returned(back,p);check(!accepted.contains(AuthUrls.STATE));check(accepted.contains("pt_openlogin_data=OPAQUE%2BUNCHANGED"));
  rejected(()->AuthUrls.returned(back.replace(nonce,"00000000000000000000000000000000"),p));rejected(()->AuthUrls.returned(back+"&clientkey=duplicate",p));rejected(()->AuthUrls.returned(back.replace("connect.qq.com","evil.example"),p));rejected(()->AuthUrls.returned(back.replace("ssl.ptlogin2.qq.com","evil.example"),p));rejected(()->AuthUrls.returned(back.replace("clientkey=QA_ONLY","clientkey="),p));
  AuthUrls.Flow q=AuthUrls.prepare("wtloginmqq://ptlogin/qlogin?qrcode="+AuthUrls.encode("http://txz.qq.com/p?k=QA_ONLY&f=1")+"&schemacallback=googlechrome%3A%2F%2F",nonce);check(q.launch.contains("huoyingairqq%3A%2F%2Flogin%2F"));check(AuthUrls.returned("huoyingairqq://login/"+nonce,q)==null);rejected(()->AuthUrls.returned("huoyingairqq://login/"+nonce+"?url=bad",q));
  System.out.println("PASS preserved OAuth fields, chooser and QR routes, nonce/app/target binding, duplicate and missing ticket rejection.");
 }
}
