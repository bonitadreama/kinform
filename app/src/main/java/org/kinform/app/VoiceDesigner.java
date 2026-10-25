package org.kinform.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.*;
import org.json.*;
import okhttp3.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit user-requested voice design, through the selected connection mode. */
final class VoiceDesigner {
    interface Choice { void use(String id, String name); }
    interface Result { void accept(JSONObject data) throws Exception; }
    private static final AtomicBoolean busy=new AtomicBoolean();
    static void library(Activity a, String name, Choice choice) {
        request(a,null,data->{
            JSONArray voices=data.getJSONArray("voices");
            String[] labels=new String[voices.length()+1];labels[0]="Design a new voice…";
            for(int i=0;i<voices.length();i++)labels[i+1]=voices.getJSONObject(i).getString("name");
            new AlertDialog.Builder(a).setTitle("Custom voices saved with this connection").setItems(labels,(dialog,index)->{
                if(index==0){design(a,name,choice);return;}
                try {
                    JSONObject v=voices.getJSONObject(index-1);
                    new AlertDialog.Builder(a).setTitle(v.getString("name")).setMessage(v.getString("prompt"))
                        .setPositiveButton("Use this voice",(d,w)->{try{choice.use(v.getString("id"),v.getString("name"));}catch(Exception e){error(a,"Could not select this voice.");}})
                        .setNegativeButton("Cancel",null).show();
                }catch(Exception e){error(a,"Could not read this voice.");}
            }).setNegativeButton("Cancel",null).show();
        });
    }
    private static void design(Activity a,String characterName,Choice choice) {
        LinearLayout layout=new LinearLayout(a);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(32,16,32,16);
        TextView help=new TextView(a);help.setText("Describe an original adult voice: pitch, texture, accent and pace. Avoid imitating a named person. Your description is sent to OpenAI and saved with the voice in your selected connection mode. Creation is free; listening uses normal paid Talk sessions. Up to 100 generated voices per OpenAI organization.");layout.addView(help);
        EditText name=new EditText(a);name.setHint("Voice name");name.setSingleLine(true);name.setText(characterName+" voice");name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});layout.addView(name);
        EditText prompt=new EditText(a);prompt.setHint("Describe how the voice sounds");prompt.setMinLines(4);prompt.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});
        if(characterName.equalsIgnoreCase("Greg"))prompt.setText("An older adult masculine voice with a low pitch, warm gravelly texture, clear American English, and an unhurried conversational pace. Dry, understated humor with a quietly affectionate tone. Clear and audible rather than whispered.");
        layout.addView(prompt);ScrollView scroll=new ScrollView(a);scroll.addView(layout);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Design a voice").setView(scroll).setPositiveButton("Create & use",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(name.getText().toString().trim().isEmpty()){name.setError("Give this voice a name");return;}
            if(prompt.getText().toString().trim().length()<20){prompt.setError("Describe the voice in at least 20 characters");return;}
            try {
                JSONObject body=new JSONObject().put("name",name.getText().toString().trim()).put("prompt",prompt.getText().toString().trim());
                request(a,body,result->{choice.use(result.getString("id"),result.getString("name"));dialog.dismiss();});
            }catch(Exception e){error(a,"Could not prepare the description.");}
        }));dialog.show();
    }
    private static void request(Activity a,JSONObject body,Result result) {
        if(!busy.compareAndSet(false,true)){error(a,"A voice request is already running. Please wait.");return;}
        if(ApiKeyStore.direct(a)){
            AlertDialog progress=new AlertDialog.Builder(a).setMessage(body==null?"Loading this phone's voice library…":"Creating your voice with OpenAI…").setCancelable(false).show();
            new Thread(()->{
                JSONObject data=null;try{data=DirectVoices.request(a,body);}catch(Exception ignored){}
                final JSONObject answer=data;
                a.runOnUiThread(()->{busy.set(false);if(a.isFinishing() || a.isDestroyed())return;progress.dismiss();
                    if(answer==null){error(a,body==null?"Could not open the library. Add your API key first.":"Could not confirm creation. Check your OpenAI voice dashboard and account access before retrying; a voice may already have been created.");return;}
                    try{result.accept(answer);}catch(Exception e){error(a,"Could not use this voice. Check the saved library.");}
                });
            },"kinform-direct-voices").start();return;
        }
        final JSONObject connection;final OkHttpClient client;
        try{connection=ConnectionStore.load(a);client=ConnectionStore.client(connection).newBuilder().callTimeout(90,TimeUnit.SECONDS).readTimeout(85,TimeUnit.SECONDS).build();}
        catch(Exception e){busy.set(false);error(a,"Import your private backend connection first.");return;}
        AlertDialog progress=new AlertDialog.Builder(a).setMessage(body==null?"Loading saved voices…":"Creating your voice… This can take a minute.").setCancelable(false).show();
        new Thread(()->{
            JSONObject data=null;String failure=null;
            try {
                HttpUrl url=HttpUrl.get(connection.getString("url").replaceFirst("wss://","https://")).newBuilder().encodedPath("/voices").build();
                Request.Builder builder=new Request.Builder().url(url).header("Authorization","Bearer "+connection.getString("token"));
                if(body!=null)builder.post(RequestBody.create(body.toString(),MediaType.get("application/json; charset=utf-8")));
                try(Response response=client.newCall(builder.build()).execute()) {
                    String raw=response.body().string();if(raw.length()>512000)throw new Exception();
                    if(!response.isSuccessful()) {
                        String code=new JSONObject(raw).optString("error");
                        if(code.equals("voice_access_denied"))failure="Your backend key needs access to create voices. Check its OpenAI project permissions.";
                        else if(code.contains("limit") || code.contains("busy"))failure="Voice creation is busy or a limit was reached. Your presets still work; try the saved library first.";
                        else if(code.equals("voice_creation_rejected"))failure="OpenAI could not create that voice. Check the description and account access.";
                        else failure="Could not confirm voice creation. Check your saved library and OpenAI voice dashboard before creating again.";
                    }else data=new JSONObject(raw);
                }
            }catch(Exception e){failure=body==null?"Could not load voices. Check that the updated backend is running.":"Connection lost or request timed out. Creation may have completed. Check the saved library and OpenAI voice dashboard before trying again.";}
            final JSONObject answer=data;final String message=failure;
            a.runOnUiThread(()->{busy.set(false);if(a.isFinishing() || a.isDestroyed())return;progress.dismiss();
                if(message!=null){error(a,message);return;}
                try{result.accept(answer);}catch(Exception e){error(a,"The voice may be saved on your backend. Reopen Custom voices to select it.");}
            });
            client.connectionPool().evictAll();client.dispatcher().executorService().shutdown();
        },"kinform-voice-design").start();
    }
    private static void error(Activity a,String text){if(!a.isFinishing() && !a.isDestroyed())new AlertDialog.Builder(a).setMessage(text).setPositiveButton("OK",null).show();}
}
