package local.huoying.air.auth;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;

/** AIR port of the existing QqAuthUrls contract; opaque OAuth fields retain their bytes. */
public final class AuthUrls {
 public static final String STATE="air_huoying_state", APP="air.local.huoying.airgame";
 public static final class Flow {
  public final String launch,nonce,u1,client;
  Flow(String launch,String nonce,String u1,String client){this.launch=launch;this.nonce=nonce;this.u1=u1;this.client=client;}
 }
 static void require(boolean b){if(!b)throw new IllegalArgumentException("Invalid QQ authorization envelope");}
 static String decode(String v){try{return URLDecoder.decode(v,"UTF-8");}catch(Exception e){throw new IllegalArgumentException("Invalid URI encoding");}}
 static String encode(String v){try{return URLEncoder.encode(v,"UTF-8");}catch(Exception e){throw new IllegalArgumentException("Invalid URI encoding");}}
 static List<String> fields(URI u){return Arrays.asList((u.getRawQuery()==null?"":u.getRawQuery()).split("&"));}
 static String key(String part){return decode(part.split("=",2)[0]);}
 static String one(URI u,String name){String result=null;int count=0;for(String field:fields(u))if(key(field).equals(name)){count++;String[] p=field.split("=",2);result=decode(p.length==2?p[1]:"");}require(count==1);return result;}
 static boolean clean(URI u){return u.getRawUserInfo()==null&&u.getRawFragment()==null;}
 static boolean jump(URI u){return "https".equals(u.getScheme())&&"ssl.ptlogin2.qq.com".equals(u.getHost())&&"/jump".equals(u.getPath())&&(u.getPort()==-1||u.getPort()==443)&&clean(u);}
 static boolean connect(URI u){return "https".equals(u.getScheme())&&"connect.qq.com".equals(u.getHost())&&(u.getPath().isEmpty()||u.getPath().equals("/"))&&(u.getPort()==-1||u.getPort()==443)&&clean(u);}
 public static Flow prepare(String raw,String nonce){
  require(raw.length()<=65536&&nonce.matches("[a-f0-9]{32}"));URI outer=URI.create(raw);
  require("wtloginmqq".equals(outer.getScheme())&&"ptlogin".equals(outer.getHost())&&"/qlogin".equals(outer.getPath())&&outer.getPort()==-1&&clean(outer));
  boolean chooser=false;for(String f:fields(outer))if(key(f).equals("p"))chooser=true;
  Set<String> drop=new HashSet<>(Arrays.asList("schemacallback","encode_jump_url"));String parameter,u1=null,client=null;
  if(chooser){
   for(String f:fields(outer))require(!key(f).equals("qrcode"));URI target=URI.create(one(outer,"p"));require(jump(target));client=one(target,"pt_3rd_aid");require(client.equals("102045649"));
   for(String f:fields(target))require(!key(f).equals(STATE));u1=one(target,"u1");URI next=URI.create(u1);
   require(connect(next)||("https".equals(next.getScheme())&&"graph.qq.com".equals(next.getHost())&&Arrays.asList("/oauth/login_jump","/oauth2.0/login_jump").contains(next.getPath())&&(next.getPort()==-1||next.getPort()==443)&&clean(next)));
   List<String> query=new ArrayList<>();for(String f:fields(target))if(!Arrays.asList("pt_browser","pt_ua","pt_package","pt_appname").contains(key(f)))query.add(f);
   String adjusted=target.toASCIIString().split("\\?",2)[0]+"?"+String.join("&",query)+"&pt_browser=other&pt_package="+APP+"&pt_appname=HuoyingAIR&"+STATE+"="+nonce;
   parameter="p="+encode(adjusted);drop.add("p");
  }else{
   URI qr=URI.create(one(outer,"qrcode"));require(Arrays.asList("http","https").contains(qr.getScheme())&&"txz.qq.com".equals(qr.getHost())&&"/p".equals(qr.getPath())&&qr.getPort()==-1&&clean(qr));require(!one(qr,"k").trim().isEmpty());
   parameter="schemacallback="+encode("huoyingairqq://login/"+nonce);
  }
  List<String> kept=new ArrayList<>();for(String f:fields(outer))if(!drop.contains(key(f)))kept.add(f);kept.add(parameter);
  String launch="wtloginmqq://ptlogin/qlogin?"+String.join("&",kept);require(launch.length()<=65536);return new Flow(launch,nonce,u1,client);
 }
 public static String callbackNonce(String raw){
  try{require(raw.length()<=65536);URI u=URI.create(raw);String n;
   if(jump(u))n=one(u,STATE);else{require(raw.length()<=256&&"huoyingairqq".equals(u.getScheme())&&"login".equals(u.getHost())&&u.getPort()==-1&&clean(u)&&u.getRawQuery()==null);n=u.getPath().substring(1);}
   return n.matches("[a-f0-9]{32}")?n:null;
  }catch(Exception e){return null;}
 }
 public static String returned(String raw,Flow flow){
  require(flow.nonce.equals(callbackNonce(raw)));
  if(flow.u1==null){require(raw.startsWith("huoyingairqq://"));return null;}
  URI u=URI.create(raw);require(jump(u));require(flow.client.equals(one(u,"pt_3rd_aid")));String actual=one(u,"u1");URI a=URI.create(actual),b=URI.create(flow.u1);
  require(actual.equals(flow.u1)||(connect(a)&&connect(b)&&Objects.equals(a.getRawQuery(),b.getRawQuery())));
  require(one(u,"clientuin").matches("[0-9]{4,20}"));require(one(u,"clientkey").length()>=1&&one(u,"clientkey").length()<=4096);require(one(u,"keyindex").matches("[0-9]{1,3}"));
  List<String> query=new ArrayList<>();for(String f:fields(u))if(!key(f).equals(STATE))query.add(f);return u.toASCIIString().split("\\?",2)[0]+"?"+String.join("&",query);
 }
}
