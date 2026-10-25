package org.kinform.app;
import android.content.Context;
import org.json.*;
import okhttp3.*;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Phone-local registry. Voice creation itself is performed by OpenAI. */
final class DirectVoices {
    static JSONObject request(Context c,JSONObject body)throws Exception {
        String key=ApiKeyStore.load(c);
        String scope=okio.ByteString.of(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))).hex();
        android.content.SharedPreferences p=c.getSharedPreferences("kinform.custom-voices",0);
        JSONArray voices=new JSONArray(p.getString(scope,"[]"));
        if(body==null)return new JSONObject().put("voices",voices);
        for(int i=0;i<voices.length();i++){
            JSONObject v=voices.getJSONObject(i);
            if(v.getString("name").equals(body.getString("name")) && v.getString("prompt").equals(body.getString("prompt")))return v;
        }
        if(voices.length()>=100)throw new Exception();
        JSONObject payload=new JSONObject(body.toString()).put("type","prompt").put("model","auto");
        OkHttpClient client=ApiKeyStore.client().newBuilder().callTimeout(90,TimeUnit.SECONDS).readTimeout(85,TimeUnit.SECONDS).build();
        try(Response response=client.newCall(new Request.Builder().url("https://api.openai.com/v1/audio/voices").header("Authorization","Bearer "+key)
            .post(RequestBody.create(payload.toString(),MediaType.get("application/json"))).build()).execute()){
            if(!response.isSuccessful())throw new Exception();
            java.io.InputStream input=response.body().byteStream();java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            byte[] chunk=new byte[4096];int n;while((n=input.read(chunk))!=-1){if(bytes.size()+n>512000)throw new Exception();bytes.write(chunk,0,n);}
            JSONObject answer=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
            String id=answer.getString("id");if(!id.matches("voice_[A-Za-z0-9_-]{1,122}"))throw new Exception();
            JSONObject v=new JSONObject(body.toString()).put("id",id);voices.put(v);
            if(!p.edit().putString(scope,voices.toString()).commit())throw new Exception();return v;
        }finally{client.connectionPool().evictAll();client.dispatcher().executorService().shutdown();}
    }
}
