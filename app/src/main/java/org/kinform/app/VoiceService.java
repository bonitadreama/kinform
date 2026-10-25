package org.kinform.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.media.*;
import android.media.audiofx.*;
import android.content.pm.ServiceInfo;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import okhttp3.*;
import okio.ByteString;

/** Explicit user-started foreground session. No wake word, auto-reconnect or fallback to phone mic. */
public final class VoiceService extends Service {
    public static volatile String status="Not listening";
    public static volatile boolean active=false;
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean stopping=false,ready=false,capturing=false;
    private volatile boolean memoryAudioHeld;
    private AudioManager manager;
    private AudioFocusRequest focus;
    private AudioDeviceInfo selected;
    private volatile AudioRecord recorder;
    private volatile AudioTrack player;
    private AcousticEchoCanceler aec;
    private NoiseSuppressor ns;
    private OkHttpClient client;
    private WebSocket socket;
    private final LinkedBlockingQueue<byte[]> output=new LinkedBlockingQueue<>(100);
    private final AtomicInteger queued=new AtomicInteger();
    private Thread inputThread,outputThread;
    private int oldMode;
    private String character;
    private AudioManager.OnCommunicationDeviceChangedListener routeListener;
    private PowerManager.WakeLock wakeLock;
    private boolean sessionStarted=false;
    private boolean direct,keepHistory;
    private String characterId,memoryDelegation;
    private LiveProtocol protocol;
    private MemoryTrace memoryTrace;
    private final ConversationHistory history=new ConversationHistory();
    private final CountDownLatch finalized=new CountDownLatch(1);

    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null || "stop".equals(intent.getAction())) {stopVoice("Not listening",intent!=null); return START_NOT_STICKY;}
        if(sessionStarted) return START_NOT_STICKY;
        sessionStarted=true; active=true; stopping=false;
        character=intent.getStringExtra("character");
        try {
            NotificationManager notifications=getSystemService(NotificationManager.class);
            notifications.createNotificationChannel(new NotificationChannel("voice","Active voice session",NotificationManager.IMPORTANCE_LOW));
            Intent stop=new Intent(this,VoiceService.class).setAction("stop");
            PendingIntent action=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notification=new Notification.Builder(this,"voice").setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Kinform voice session").setContentText("Bluetooth microphone session · tap Stop to end")
                .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Stop",action).build()).build();
            startForeground(11,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE|ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            wakeLock=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Kinform:voice");
            wakeLock.acquire(330000);
            manager=getSystemService(AudioManager.class); oldMode=manager.getMode();
            focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes()).setOnAudioFocusChangeListener(change->{if(change<0)stopVoice("Paused: another app needs audio");},main).build();
            if(manager.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) throw new Exception();
            manager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            int deviceId=intent.getIntExtra("deviceId",-1);
            for(AudioDeviceInfo d:manager.getAvailableCommunicationDevices())if(d.getId()==deviceId && bluetooth(d))selected=d;
            if(selected==null || !manager.setCommunicationDevice(selected)) {stopVoice("Bluetooth call audio unavailable. Reconnect your Bluetooth device and enable call audio.");return START_NOT_STICKY;}
            routeListener=d->{if(ready && (d==null || d.getId()!=selected.getId()))stopVoice("Stopped: Bluetooth audio route changed");};
            manager.addOnCommunicationDeviceChangedListener(getMainExecutor(),routeListener);
            status="Connecting Bluetooth call audio…";
            awaitRoute(SystemClock.elapsedRealtime()+15000);
            main.postDelayed(()->stopVoice("Session ended: five-minute test limit"),300000);
        } catch(Exception e) {stopVoice("Could not start audio. Check microphone and Nearby devices permissions.");}
        return START_NOT_STICKY;
    }
    static boolean bluetooth(AudioDeviceInfo d) {return d!=null && (d.getType()==AudioDeviceInfo.TYPE_BLUETOOTH_SCO || d.getType()==AudioDeviceInfo.TYPE_BLE_HEADSET);}
    private AudioAttributes attributes(){return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();}
    private void awaitRoute(long deadline) {
        if(stopping)return;
        AudioDeviceInfo d=manager.getCommunicationDevice();
        if(d!=null && d.getId()==selected.getId()) {connect(); return;}
        if(SystemClock.elapsedRealtime()>deadline){stopVoice("Bluetooth call route did not connect");return;}
        main.postDelayed(()->awaitRoute(deadline),100);
    }
    private void connect() {
        direct=ApiKeyStore.direct(this);
        if(direct){connectDirect();return;}
        try {
            JSONObject owner=new JSONObject(character);characterId=owner.getString("id");
            memoryTrace=new MemoryTrace("AUTO".equals(owner.getString("memoryMode")),false,false);persistMemoryTrace();
            JSONObject connection=ConnectionStore.load(this);
            client=ConnectionStore.client(connection);
            status="Connecting to your private backend…";
            socket=client.newWebSocket(new Request.Builder().url(connection.getString("url"))
                .header("Authorization","Bearer "+connection.getString("token")).build(),new WebSocketListener(){
                @Override public void onOpen(WebSocket ws,Response response) {
                    if(stopping){ws.close(1000,"Stopped");return;}
                    try{ws.send(new JSONObject().put("type","start").put("memoryProtocol",1).put("character",new JSONObject(character)).toString());}
                    catch(Exception e){stopVoice("Invalid character setup");}
                }
                @Override public void onMessage(WebSocket ws,String message) {
                    if(stopping)return;
                    try{
                        JSONObject event=new JSONObject(message); String type=event.getString("type");
                        if(type.equals("ready")) main.post(()->startAudio());
                        else if(type.equals("memory_save"))main.post(()->saveVoiceMemory(event));
                        else if(type.equals("error"))stopVoice("Backend: "+friendly(event.optString("code")));
                        else if(type.equals("ended"))stopVoice("Session ended");
                    }catch(Exception e){stopVoice("Invalid backend response");}
                }
                @Override public void onMessage(WebSocket ws,ByteString data) {
                    if(stopping || !ready)return;
                    byte[] bytes=data.toByteArray();
                    if(bytes.length%2!=0 || bytes.length>96000 || queued.addAndGet(bytes.length)>48000){stopVoice("Audio arrived too quickly; stopped to avoid delayed replies");return;}
                    if(!output.offer(bytes))stopVoice("Playback queue full");
                }
                @Override public void onFailure(WebSocket ws,Throwable failure,Response response){stopVoice(response!=null && response.code()==401?"Connection revoked or invalid. Import a new connection file.":"Connection failed. Check the backend, Wi-Fi, and connection file.");}
                @Override public void onClosing(WebSocket ws,int code,String reason){ws.close(code,null);stopVoice("Session ended");}
                @Override public void onClosed(WebSocket ws,int code,String reason){stopVoice("Session ended");}
            });
        }catch(Exception e){stopVoice("Import a valid backend connection file first");}
    }
    private void connectDirect() {
        try {
            JSONObject c=new JSONObject(character);characterId=c.getString("id");
            boolean memory=!"OFF".equals(c.getString("memoryMode"));
            keepHistory=memory && HistoryStore.enabled(this,characterId);
            memoryTrace=new MemoryTrace("AUTO".equals(c.getString("memoryMode")),keepHistory,true);persistMemoryTrace();
            protocol=new LiveProtocol(memory,characterId,"AUTO".equals(c.getString("memoryMode")));
            final JSONObject start=LiveProtocol.start(c,keepHistory?HistoryStore.context(this,characterId):"");
            client=ApiKeyStore.client();status="Connecting directly to OpenAI…";
            socket=client.newWebSocket(new Request.Builder().url("wss://api.openai.com/v1/live/sessions")
                .header("Authorization","Bearer "+ApiKeyStore.load(this)).build(),new WebSocketListener(){
                @Override public void onOpen(WebSocket ws,Response response){
                    if(stopping){ws.cancel();return;}
                    if(!ws.send(start.toString()))stopVoice("Could not start the voice session");
                }
                @Override public void onMessage(WebSocket ws,String raw){
                    try{
                        if(raw.length()>262144)throw new Exception();
                        JSONObject event=new JSONObject(raw);String type=event.getString("type");
                        if(type.equals("session.closed")){finalized.countDown();ws.close(1000,"Ended");if(!stopping)stopVoice("Session ended");return;}
                        if(stopping)return;
                        // Serialize commands, privacy changes and writes on the service's main thread.
                        main.post(()->{
                            if(stopping)return;
                            try {
                                if(type.equals("session.started")){memoryTrace.event(MemoryTrace.Event.SESSION_READY);startAudio();}
                                else if(type.equals("session.output_audio.delta"))enqueueAudio(android.util.Base64.decode(event.getString("delta"),android.util.Base64.DEFAULT));
                                else if(type.equals("session.input_transcript.delta") || type.equals("session.output_transcript.delta")){
                                    boolean input=type.equals("session.input_transcript.delta");
                                    boolean hadOutput=protocol.hasOutputForDiagnostics();
                                    boolean hadProposal=protocol.hasReadyVoiceProposal();
                                    if(input)protocol.transcript(event,SystemClock.elapsedRealtime());else {
                                        if(protocol.acceptsOutput(event)){
                                            JSONObject fallback=protocol.voiceReplyBoundary(SystemClock.elapsedRealtime());
                                            if(fallback!=null){
                                                memoryTrace.event(MemoryTrace.Event.VOICE_REPLY_BOUNDARY);
                                                // This response did not delegate; never reuse an earlier task ID.
                                                memoryDelegation=null;
                                                if(fallback.has("save")){
                                                    memoryTrace.event(MemoryTrace.Event.VOICE_SAVE_REQUESTED);
                                                    saveVoiceMemory(fallback.getJSONObject("save"));
                                                }else {stopVoice("Memory confirmation expired. Nothing saved; please try again.");return;}
                                                if(stopping)return;
                                            }
                                        }
                                        protocol.outputTranscript(event,SystemClock.elapsedRealtime());
                                        if(protocol.takePrematureSaveWarning()){
                                            memoryTrace.event(MemoryTrace.Event.PREMATURE_SAVE_CLAIM);
                                            status="Memory not saved yet · waiting for confirmation";
                                            boolean sent=socket.send(new JSONObject().put("type","session.instructions.append")
                                                .put("event_id",java.util.UUID.randomUUID().toString()).put("delegation_id",JSONObject.NULL)
                                                .put("content","Your preceding Saved claim had no application storage confirmation. If still awaiting the user's confirmation, briefly correct it with 'Not saved yet' and wait. Do not repeat the fact or ask another question. Your speech cannot approve a memory. A later explicit application storage confirmation supersedes this notice; only then may you acknowledge success.").toString());
                                            memoryTrace.event(sent?MemoryTrace.Event.SAVE_CORRECTION_SENT:MemoryTrace.Event.SAVE_CORRECTION_FAILED);
                                            if(!sent){stopVoice("Voice connection interrupted; check Memories for save status");return;}
                                        }
                                    }
                                    boolean hold=protocol.awaitingVoiceReplyBoundary();
                                    if(hold && !memoryAudioHeld){
                                        memoryAudioHeld=true;
                                        byte[] pending;while((pending=output.poll())!=null)queued.addAndGet(-pending.length);
                                        if(player!=null){player.pause();player.flush();}
                                        memoryTrace.event(MemoryTrace.Event.VOICE_AUDIO_HELD);
                                    }else if(!hold && memoryAudioHeld){memoryAudioHeld=false;if(player!=null && ready)player.play();}
                                    if(input && !hadProposal && protocol.hasReadyVoiceProposal())memoryTrace.event(MemoryTrace.Event.READBACK_CAPTURED_BEFORE_REPLY);
                                    if(protocol.hasReadbackMarkerForDiagnostics())memoryTrace.event(MemoryTrace.Event.READBACK_MARKER);
                                    if(input && hadOutput && !protocol.hasOutputForDiagnostics())memoryTrace.event(MemoryTrace.Event.INPUT_CLEARED_OUTPUT);
                                    memoryTrace.transcript(input,!event.optString("event_id").isEmpty(),protocol.hasSuggestionMarkerForDiagnostics());
                                    if(keepHistory)history.accept(event);
                                }else if(type.equals("session.delegation.created")){
                                    memoryTrace.event(MemoryTrace.Event.DELEGATED);
                                    if(!protocol.hasOutputForDiagnostics())memoryTrace.event(MemoryTrace.Event.EMPTY_OUTPUT_AT_DELEGATION);
                                    boolean marker=protocol.hasSuggestionMarkerForDiagnostics();
                                    String suggested=protocol.takeSuggestion();
                                    memoryTrace.event(suggested==null?MemoryTrace.Event.NO_DRAFT:MemoryTrace.Event.DRAFT_PARSED);
                                    if(marker && suggested==null)memoryTrace.event(MemoryTrace.Event.MARKER_NOT_PARSEABLE);
                                    memoryDelegation=event.getJSONObject("delegation").getString("id");
                                    CharacterBook.Character current=new BookStore(VoiceService.this).load().find(characterId);
                                    if(current==null)memoryTrace.event(MemoryTrace.Event.OWNER_MISSING);
                                    else if(current.memoryMode!=CharacterBook.MemoryMode.AUTO)memoryTrace.event(MemoryTrace.Event.OFF);
                                    if(suggested!=null && current!=null && current.memoryMode==CharacterBook.MemoryMode.AUTO){
                                        protocol.suggestionQueued();
                                        MemoryTrace.Event outcome;
                                        try{outcome=SuggestionStore.offerResult(VoiceService.this,current,suggested);}
                                        catch(Exception e){memoryTrace.event(MemoryTrace.Event.STORAGE_ERROR);throw e;}
                                        memoryTrace.event(outcome);
                                        boolean queued=outcome==MemoryTrace.Event.QUEUED;
                                        commentary(queued?"The proposal is queued for phone review only, NOT a saved memory. Tell the user they can Stop and open Memories to approve, edit or reject it. Do not repeat the proposal or delegate again.":"No new suggestion was queued (duplicate or queue full). Nothing was remembered. Continue the conversation normally.");
                                        return;
                                    }
                                    memoryTrace.event(protocol.hasReadyVoiceProposal()?MemoryTrace.Event.VOICE_PENDING:MemoryTrace.Event.VOICE_NOT_READY);
                                    JSONObject result=protocol.delegate(SystemClock.elapsedRealtime());
                                    if(result.has("save")){memoryTrace.event(MemoryTrace.Event.VOICE_SAVE_REQUESTED);saveVoiceMemory(result.getJSONObject("save"));}
                                    else commentary(result.getString("say"));
                                }else if(type.equals("error"))stopVoice("OpenAI rejected the session or request. Check key permissions, billing and voice access; try a preset voice.");
                            }catch(Exception e){memoryTrace.event(MemoryTrace.Event.PROTOCOL_ERROR);stopVoice("Could not process the voice session safely");}
                        });
                    }catch(Exception e){stopVoice("Invalid voice service response");}
                }
                @Override public void onFailure(WebSocket ws,Throwable t,Response r){
                    finalized.countDown();stopVoice(r!=null && (r.code()==401 || r.code()==403)?"OpenAI access denied. Check your personal API key and project permissions.":r!=null && r.code()==429?"OpenAI quota or rate limit reached. Check your API account billing.":"Direct connection failed. Check internet access and your OpenAI account.");
                }
                @Override public void onClosing(WebSocket ws,int code,String reason){ws.close(code,null);finalized.countDown();stopVoice("Voice connection ended");}
                @Override public void onClosed(WebSocket ws,int code,String reason){finalized.countDown();stopVoice("Voice connection ended");}
            });
            main.postDelayed(()->{if(!ready && !stopping)stopVoice("OpenAI did not start the voice session in time");},20000);
        }catch(Exception e){stopVoice("Could not prepare phone-only voice. Check your saved key and character history.");}
    }
    private void enqueueAudio(byte[] bytes){
        if(stopping || !ready || memoryAudioHeld)return;
        if(bytes.length%2!=0 || bytes.length>96000 || queued.addAndGet(bytes.length)>48000 || !output.offer(bytes))
            stopVoice("Audio arrived too quickly; stopped to avoid delayed replies");
    }
    private void commentary(String text)throws Exception{
        if(text!=null){
            boolean sent=socket.send(new JSONObject().put("type","session.commentary.append").put("event_id",java.util.UUID.randomUUID().toString()).put("delegation_id",memoryDelegation==null?JSONObject.NULL:memoryDelegation).put("content",text).toString());
            if(memoryTrace!=null)memoryTrace.event(sent?MemoryTrace.Event.COMMENTARY_SENT:MemoryTrace.Event.COMMENTARY_FAILED);
            if(!sent)stopVoice("Voice connection interrupted");
        }
    }
    private void persistMemoryTrace(){
        if(memoryTrace==null || characterId==null)return;
        try{if(!MemoryDiagnostics.save(this,characterId,memoryTrace))status+=" · Diagnostic could not be saved";}
        catch(Exception ignored){status+=" · Diagnostic could not be saved";}
    }
    private void saveVoiceMemory(JSONObject event) {
        if(stopping || !ready)return;
        String result="failed",id=event.optString("id");
        try {
            if(!id.matches("[0-9a-fA-F-]{36}"))throw new IllegalArgumentException();
            String characterId=new JSONObject(character).getString("id");
            if(!characterId.equals(event.getString("characterId")))throw new IllegalArgumentException();
            String fact=event.getString("text");
            if(fact.trim().isEmpty() || fact.length()>1000)throw new IllegalArgumentException();
            BookStore store=new BookStore(this);CharacterBook book=store.load();
            CharacterBook.Character owner=book.find(characterId);
            if(owner==null)result="missing";
            else if(owner.memoryMode==CharacterBook.MemoryMode.OFF)result="off";
            else {
                boolean exists=false;for(CharacterBook.Memory memory:owner.memories)if(memory.id.equals(id)){
                    if(!memory.text.equals(fact))throw new IllegalArgumentException();exists=true;
                }
                if(exists)result="saved";
                else if(owner.memories.size()>=1000)result="full";
                else {owner.memories.add(new CharacterBook.Memory(id,fact,System.currentTimeMillis()));store.save(book);result="saved";}
            }
        } catch(Exception ignored) {result="failed";}
        status=result.equals("saved")?"Listening · memory saved on this phone":"Listening · memory was not saved";
        if(memoryTrace!=null)memoryTrace.event(result.equals("saved")?MemoryTrace.Event.VOICE_SAVED:MemoryTrace.Event.VOICE_SAVE_FAILED);
        if(direct && !result.equals("saved")){
            protocol.acknowledge(id,result);
            stopVoice("Memory was not saved on this phone. Check Memories before trying again.");return;
        }
        if(direct && memoryAudioHeld){memoryAudioHeld=false;if(player!=null && ready)player.play();}
        try {if(direct)commentary(protocol.acknowledge(id,result));else socket.send(new JSONObject().put("type","memory_result").put("id",id).put("status",result).toString());}catch(Exception ignored){}
    }
    private static String friendly(String code) {
        switch(code){
            case "daily_limit":return "Daily voice allowance reached";
            case "busy":return "Another session is already running";
            case "hourly_limit":return "Too many starts; try again later";
            case "provider_auth_failed":return "Check the API key on the backend";
            case "provider_quota":return "Provider quota or billing needs attention";
            case "provider_rejected_session":return "Provider did not accept the voice session";
            default:return "Voice connection stopped. Check the backend setup";
        }
    }
    // AudioRecord permission revocation is handled by the catch(Exception) below,
    // which includes SecurityException and stops/cleans up the voice session.
    @android.annotation.SuppressLint("MissingPermission")
    private void startAudio() {
        if(stopping || ready)return;
        try {
            if(manager.getCommunicationDevice()==null || manager.getCommunicationDevice().getId()!=selected.getId())throw new Exception();
            int inMin=AudioRecord.getMinBufferSize(24000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            recorder=new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(Math.max(inMin,4800)).build();
            AudioDeviceInfo input=null;
            for(AudioDeviceInfo d:manager.getDevices(AudioManager.GET_DEVICES_INPUTS))if(bluetooth(d) && d.getType()==selected.getType() && (d.getAddress().isEmpty() || selected.getAddress().isEmpty() || d.getAddress().equals(selected.getAddress()))) {input=d;break;}
            if(input==null || !recorder.setPreferredDevice(input))throw new Exception();
            if(AcousticEchoCanceler.isAvailable()){aec=AcousticEchoCanceler.create(recorder.getAudioSessionId());if(aec!=null)aec.setEnabled(true);}
            if(NoiseSuppressor.isAvailable()){ns=NoiseSuppressor.create(recorder.getAudioSessionId());if(ns!=null)ns.setEnabled(true);}
            int outMin=AudioTrack.getMinBufferSize(24000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
            player=new AudioTrack.Builder().setAudioAttributes(attributes())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(outMin,4800)).build();
            if(!player.setPreferredDevice(selected))throw new Exception();
            player.play(); recorder.startRecording(); ready=true; capturing=true;
            status="Listening through "+selected.getProductName()+" · say hello";
            inputThread=new Thread(()->captureLoop(),"Kinform microphone");
            outputThread=new Thread(()->playbackLoop(),"Kinform speaker");
            inputThread.start(); outputThread.start();
        }catch(Exception e){stopVoice("Could not open the Bluetooth microphone and speaker together");}
    }
    private boolean correctRoute(AudioDeviceInfo d) {
        return bluetooth(d) && d.getType()==selected.getType() && (d.getAddress().isEmpty() || selected.getAddress().isEmpty() || d.getAddress().equals(selected.getAddress()));
    }
    private void captureLoop() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        byte[] bytes=new byte[960]; // 20ms PCM16 at 24kHz
        try {
            while(capturing){
                int n=recorder.read(bytes,0,bytes.length,AudioRecord.READ_BLOCKING);
                if(!capturing)break;
                if(n<=0 || n%2!=0 || !correctRoute(recorder.getRoutedDevice())){stopVoice("Stopped: selected Bluetooth microphone is unavailable");break;}
                if(socket.queueSize()>(direct?72000:48000)){stopVoice("Upload is too slow; session stopped");break;}
                boolean sent=direct?socket.send(new JSONObject().put("type","session.input_audio.append").put("audio",android.util.Base64.encodeToString(bytes,0,n,android.util.Base64.NO_WRAP)).toString()):socket.send(ByteString.of(bytes,0,n));
                if(!sent){stopVoice("Voice connection interrupted");break;}
            }
        }catch(Exception e){if(!stopping)stopVoice("Microphone stopped unexpectedly");}
    }
    private void playbackLoop() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        try {
            while(capturing){
                byte[] bytes=output.poll(1,TimeUnit.SECONDS);if(bytes==null)continue;
                queued.addAndGet(-bytes.length);
                int offset=0;
                while(offset<bytes.length && capturing){
                    if(memoryAudioHeld)break;
                    AudioDeviceInfo communication=manager.getCommunicationDevice();
                    if(communication==null || communication.getId()!=selected.getId()){stopVoice("Stopped: Bluetooth speaker disconnected");return;}
                    int n=player.write(bytes,offset,Math.min(960,bytes.length-offset),AudioTrack.WRITE_BLOCKING);
                    if(n<=0){stopVoice("Speaker playback failed");return;} offset+=n;
                    if(!correctRoute(player.getRoutedDevice())){stopVoice("Stopped: speaker audio route changed");return;}
                }
            }
        }catch(Exception e){if(!stopping)stopVoice("Speaker stopped unexpectedly");}
    }
    private void stopVoice(String reason) {
        stopVoice(reason,false);
    }
    private void stopVoice(String reason,boolean finalizeDrafts) {
        if(Looper.myLooper()!=Looper.getMainLooper()){main.post(()->stopVoice(reason,finalizeDrafts));return;}
        if(stopping)return;stopping=true; capturing=false;ready=false;status=reason;
        // Only explicit Stop finalizes late transcript drafts. Errors/route loss do not.
        if(finalizeDrafts && direct && protocol!=null && memoryTrace!=null){
            memoryTrace.event(MemoryTrace.Event.STOP_CHECK);
            memoryTrace.event(protocol.stopDraftDiagnostic());
            try{
                String draft;
                while((draft=protocol.takeFinalSuggestion())!=null){
                    memoryTrace.event(MemoryTrace.Event.STOP_DRAFT_CAPTURE);
                    CharacterBook.Character owner=new BookStore(this).load().find(characterId);
                    if(owner==null){memoryTrace.event(MemoryTrace.Event.OWNER_MISSING);break;}
                    MemoryTrace.Event outcome=SuggestionStore.offerResult(this,owner,draft);
                    memoryTrace.event(outcome);
                }
            }catch(Exception e){memoryTrace.event(MemoryTrace.Event.STORAGE_ERROR);status+=" · Suggestion draft could not be stored";}
        }
        if(memoryTrace!=null){if(!finalizeDrafts)memoryTrace.event(MemoryTrace.Event.AUTOMATIC_STOP);memoryTrace.event(MemoryTrace.Event.STOPPED);persistMemoryTrace();}
        main.removeCallbacksAndMessages(null);
        if(socket!=null){
            if(direct)socket.send("{\"type\":\"session.close\"}");
            else {socket.send("{\"type\":\"stop\"}");socket.close(1000,"Stopped");}
        }
        if(keepHistory)try{
            CharacterBook.Character owner=new BookStore(this).load().find(characterId);
            if(owner!=null && owner.memoryMode!=CharacterBook.MemoryMode.OFF)HistoryStore.append(this,characterId,history.snapshot());
        }catch(Exception e){status=reason+" · Recent conversation could not be saved";}
        if(recorder!=null)try{recorder.stop();}catch(Exception ignored){}
        if(player!=null)try{player.pause();player.flush();}catch(Exception ignored){}
        if(inputThread!=null)inputThread.interrupt();if(outputThread!=null)outputThread.interrupt();
        // Workers may be exiting a native read/write: join off the UI thread before releasing audio objects.
        new Thread(()->{
            try{if(inputThread!=null)inputThread.join(1500);if(outputThread!=null)outputThread.join(1500);}catch(InterruptedException ignored){}
            if(aec!=null)aec.release();if(ns!=null)ns.release();
            if(recorder!=null)recorder.release();if(player!=null)player.release();
            if(direct && socket!=null)try{if(!finalized.await(15,TimeUnit.SECONDS)){socket.cancel();status=reason+" · Provider close was not confirmed; check API usage";}}catch(InterruptedException e){socket.cancel();}
            if(client!=null){client.connectionPool().evictAll();client.dispatcher().executorService().shutdown();}
            main.post(()->{active=false;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();});
        },"Kinform audio cleanup").start();
        output.clear();
        if(manager!=null)try{if(routeListener!=null)manager.removeOnCommunicationDeviceChangedListener(routeListener);manager.clearCommunicationDevice();manager.setMode(oldMode);if(focus!=null)manager.abandonAudioFocusRequest(focus);}catch(SecurityException ignored){}
        if(wakeLock!=null && wakeLock.isHeld())wakeLock.release();
    }
    @Override public void onDestroy(){stopVoice("Not listening");super.onDestroy();}
    @Override public void onTaskRemoved(Intent rootIntent){stopVoice("Stopped when Kinform was dismissed");}
}
