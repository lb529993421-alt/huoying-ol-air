package local.huoying.air.auth;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;

/** Same HK1/HL1 wire format as the existing owner-only offline signer. */
public final class LicenseToken {
 public static String hex(byte[] value){StringBuilder out=new StringBuilder();for(byte b:value)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
 public static String fingerprint(byte[] key)throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(key));}
 public static byte[] payload(String product,String fingerprint){return ("huoying-offline-v1\n"+product+"\n"+fingerprint).getBytes(StandardCharsets.UTF_8);}
 public static boolean verify(String token,String fingerprint,String product,byte[] issuer){
  try{
   if(!fingerprint.matches("[0-9a-f]{64}")||token.length()<85||token.length()>256)return false;
   String[] parts=token.split("-",-1);if(parts.length!=3||!"HL1".equals(parts[0])||!fingerprint.equals(parts[1])||!parts[2].matches("[0-9a-f]{16,144}")||parts[2].length()%2!=0)return false;
   byte[] bytes=new byte[parts[2].length()/2];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)Integer.parseInt(parts[2].substring(i*2,i*2+2),16);
   PublicKey key=KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(issuer));
   Signature signature=Signature.getInstance("SHA256withECDSA");signature.initVerify(key);signature.update(payload(product,fingerprint));return signature.verify(bytes);
  }catch(Exception ignored){return false;}
 }
}
