package local.huoying.air.auth;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.security.keystore.*;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;

public final class OfflineLicense {
 private static final String ALIAS="huoying.offline.device.v1";
 private static KeyStore keys()throws Exception{KeyStore keys=KeyStore.getInstance("AndroidKeyStore");keys.load(null);return keys;}
 private static AtomicFile file(Context context){return new AtomicFile(new File(context.getNoBackupFilesDir(),"offline-license-v1.txt"));}
 public static synchronized String requestCode()throws Exception{
  if(!keys().containsAlias(ALIAS)){
   KeyPairGenerator generator=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore");
   generator.initialize(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY).setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_SHA256).build());generator.generateKeyPair();
  }
  return "HK1-"+LicenseToken.fingerprint(keys().getCertificate(ALIAS).getPublicKey().getEncoded());
 }
 private static boolean valid(Context context,String token){
  try{
   KeyStore store=keys();if(!store.containsAlias(ALIAS))return false;PublicKey device=store.getCertificate(ALIAS).getPublicKey();
   byte[] issuer;try(InputStream in=context.getAssets().open("offline_authority.der")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[256];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>4096)return false;out.write(buffer,0,n);}issuer=out.toByteArray();}
   if(!LicenseToken.verify(token,LicenseToken.fingerprint(device.getEncoded()),context.getPackageName(),issuer))return false;
   byte[] challenge=new byte[32];new SecureRandom().nextBytes(challenge);Signature proof=Signature.getInstance("SHA256withECDSA");proof.initSign((PrivateKey)store.getKey(ALIAS,null));proof.update(challenge);byte[] signed=proof.sign();proof.initVerify(device);proof.update(challenge);return proof.verify(signed);
  }catch(Exception ignored){return false;}
 }
 public static boolean isActivated(Context context){
  try {
   Bundle metadata=context.getPackageManager().getApplicationInfo(context.getPackageName(),PackageManager.GET_META_DATA).metaData;
   if(metadata!=null&&!metadata.getBoolean("local.huoying.air.requiresActivation",true))return true;
  }catch(PackageManager.NameNotFoundException ignored){}
  try(InputStream in=file(context).openRead()){byte[] bytes=new byte[257];int n=in.read(bytes);return n>0&&n<=256&&in.read()==-1&&valid(context,new String(bytes,0,n,StandardCharsets.UTF_8));}catch(Exception ignored){return false;}
 }
 public static synchronized boolean activate(Context context,String input)throws Exception{
  if(input==null||input.length()>1024)return false;String token=input.replaceAll("\\s","");if(!valid(context,token))return false;
  AtomicFile target=file(context);FileOutputStream out=target.startWrite();try{out.write(token.getBytes(StandardCharsets.UTF_8));target.finishWrite(out);}catch(Exception error){target.failWrite(out);throw error;}return true;
 }
}
