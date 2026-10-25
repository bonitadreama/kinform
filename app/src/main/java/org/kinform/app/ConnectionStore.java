package org.kinform.app;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import okhttp3.OkHttpClient;
import okhttp3.HttpUrl;
import java.util.concurrent.TimeUnit;

/** The import contains a backend access token, NEVER a provider API key. */
final class ConnectionStore {
    private static final String ALIAS="kinform.connection.v1";
    static JSONObject validate(String raw) throws Exception {
        if(raw.length()>12000) throw new Exception("Connection file too large");
        JSONObject c=new JSONObject(raw);
        String url=c.getString("url"), token=c.getString("token");
        if(c.getInt("version")!=1 || !url.startsWith("wss://") || !token.matches("[A-Za-z0-9_-]{43}")) throw new Exception("Invalid connection file");
        HttpUrl parsed=HttpUrl.get(url.replaceFirst("wss://","https://"));
        if(!parsed.encodedPath().equals("/voice") || parsed.query()!=null || parsed.fragment()!=null || !parsed.username().isEmpty() || !parsed.password().isEmpty()) throw new Exception("Invalid backend address");
        certificate(c).checkValidity(); return c;
    }
    private static X509Certificate certificate(JSONObject c) throws Exception {
        return (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(c.getString("certificate").getBytes(StandardCharsets.US_ASCII)));
    }
    private static SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if(!ks.containsAlias(ALIAS)) {
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); generator.generateKey();
        }
        return (SecretKey)ks.getKey(ALIAS,null);
    }
    static void save(Context context,String raw) throws Exception {
        JSONObject c=validate(raw); Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
        String iv=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP);
        String encrypted=Base64.encodeToString(cipher.doFinal(c.toString().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        if(!context.getSharedPreferences("kinform.connection",0).edit().putString("iv",iv).putString("encrypted",encrypted).commit()) throw new Exception("Could not save connection");
    }
    static JSONObject load(Context context) throws Exception {
        android.content.SharedPreferences p=context.getSharedPreferences("kinform.connection",0);
        if(!p.contains("encrypted")) throw new Exception("Import a connection file first");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(p.getString("iv",""),Base64.NO_WRAP)));
        return validate(new String(cipher.doFinal(Base64.decode(p.getString("encrypted",""),Base64.NO_WRAP)),StandardCharsets.UTF_8));
    }
    static void forget(Context context) { context.getSharedPreferences("kinform.connection",0).edit().clear().apply(); }
    static OkHttpClient client(JSONObject c) throws Exception {
        KeyStore trusted=KeyStore.getInstance(KeyStore.getDefaultType()); trusted.load(null);
        trusted.setCertificateEntry("kinform-server",certificate(c));
        TrustManagerFactory factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); factory.init(trusted);
        X509TrustManager manager=(X509TrustManager)factory.getTrustManagers()[0];
        SSLContext ssl=SSLContext.getInstance("TLS"); ssl.init(null,new javax.net.ssl.TrustManager[]{manager},null);
        // Default hostname verification stays enabled; trust ONLY the imported server certificate.
        return new OkHttpClient.Builder().sslSocketFactory(ssl.getSocketFactory(),manager)
            .connectTimeout(15,TimeUnit.SECONDS).readTimeout(0,TimeUnit.SECONDS).pingInterval(5,TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build();
    }
}
