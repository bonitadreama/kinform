package org.kinform.app;

import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import okhttp3.OkHttpClient;
import java.util.concurrent.TimeUnit;

/** Personal BYOK only. No bundled credential, export, redirect, or arbitrary provider URL. */
final class ApiKeyStore {
    private static final String ALIAS="kinform.personal-key.v1";
    private static volatile String temporary;
    static boolean direct(Context c){return c.getSharedPreferences("kinform.mode",0).getBoolean("direct",true);}
    static void mode(Context c,boolean direct){c.getSharedPreferences("kinform.mode",0).edit().putBoolean("direct",direct).apply();}
    private static SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias(ALIAS)){
            KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();
        }
        return (SecretKey)ks.getKey(ALIAS,null);
    }
    static void save(Context c,String value,boolean remember) throws Exception {
        value=value.trim();
        if(!value.matches("sk-[A-Za-z0-9_-]{20,500}"))throw new Exception("Invalid key format");
        android.content.SharedPreferences.Editor e=c.getSharedPreferences("kinform.personal-key",0).edit().clear();
        if(remember){
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
            e.putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP));
            e.putString("ciphertext",Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP));
        }
        if(!e.commit())throw new Exception("Storage unavailable");
        temporary=remember?null:value;mode(c,true);
    }
    static String load(Context c) throws Exception {
        if(temporary!=null)return temporary;
        android.content.SharedPreferences p=c.getSharedPreferences("kinform.personal-key",0);
        if(!p.contains("ciphertext"))throw new Exception("No key");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(p.getString("iv",""),Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(p.getString("ciphertext",""),Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    static void forget(Context c) throws Exception {
        temporary=null;
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);ks.deleteEntry(ALIAS);
        if(!c.getSharedPreferences("kinform.personal-key",0).edit().clear().commit())throw new Exception("Storage unavailable");
    }
    static OkHttpClient client(){return new OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS)
        .readTimeout(0,TimeUnit.SECONDS).pingInterval(5,TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build();}
}
