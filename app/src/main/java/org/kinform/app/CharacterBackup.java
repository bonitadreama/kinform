package org.kinform.app;

import org.json.*;
import java.util.UUID;
import java.util.HashSet;

/** Portable character data only: no server connection or provider credentials. */
final class CharacterBackup {
    static final int MAX_BYTES=8*1024*1024;
    static final class Restored {
        final CharacterBook.Character character; final boolean historyEnabled; final JSONArray sessions;
        Restored(CharacterBook.Character c,boolean enabled,JSONArray sessions){this.character=c;this.historyEnabled=enabled;this.sessions=sessions;}
    }
    static String encode(CharacterBook.Character c) throws Exception {
        return encode(c,false,new JSONArray());
    }
    static String encode(CharacterBook.Character c,boolean historyEnabled,JSONArray sessions) throws Exception {
        JSONArray memories=new JSONArray();
        for(CharacterBook.Memory m:c.memories)memories.put(new JSONObject().put("id",m.id).put("text",m.text).put("createdAt",m.createdAt));
        JSONObject character=new JSONObject().put("name",c.name).put("body",c.body).put("personality",c.personality)
            .put("boundaries",c.boundaries).put("voice",c.voice).put("voiceLabel",c.voiceLabel)
            .put("memoryMode",c.memoryMode.name()).put("initiate",c.initiate)
            .put("quietStart",c.quietStart).put("quietEnd",c.quietEnd).put("memories",memories);
        String raw=new JSONObject().put("format","kinform.character").put("version",2).put("character",character)
            .put("history",new JSONObject().put("enabled",historyEnabled).put("sessions",sessions)).toString(2);
        decode(raw); // Do not export a file that this version cannot restore.
        return raw;
    }
    static CharacterBook.Character decode(String raw) throws Exception {
        return restore(raw).character;
    }
    static Restored restore(String raw) throws Exception {
        if(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES)throw new Exception("File too large");
        JSONTokener parser=new JSONTokener(raw);Object value=parser.nextValue();
        if(!(value instanceof JSONObject) || parser.nextClean()!=0)throw new Exception("Invalid JSON");
        JSONObject root=(JSONObject)value;
        Object version=root.get("version");
        if(!"kinform.character".equals(root.get("format")) || !(version instanceof Number) || (((Number)version).doubleValue()!=1 && ((Number)version).doubleValue()!=2))throw new Exception("Unsupported backup version");
        boolean modern=((Number)version).intValue()==2;
        if(modern)keys(root,"format","version","character","history");else keys(root,"format","version","character");
        boolean historyEnabled=false;JSONArray sessions=new JSONArray();
        if(modern){JSONObject h=root.getJSONObject("history");keys(h,"enabled","sessions");
            if(!(h.get("enabled") instanceof Boolean))throw new Exception("Invalid history preference");
            historyEnabled=h.getBoolean("enabled");sessions=validateHistory(h.getJSONArray("sessions"));
        }
        JSONObject c=root.getJSONObject("character");
        keys(c,"name","body","personality","boundaries","voice","voiceLabel","memoryMode","initiate","quietStart","quietEnd","memories");
        CharacterBook.Character result=new CharacterBook.Character(UUID.randomUUID().toString(),text(c,"name",80,true),text(c,"body",160,true),text(c,"personality",3000,true));
        result.boundaries=text(c,"boundaries",1500,false);result.voice=text(c,"voice",128,true);
        if(!CharacterBook.validVoice(result.voice))throw new Exception("Unsupported voice");
        result.voiceLabel=text(c,"voiceLabel",80,false);
        result.memoryMode=CharacterBook.MemoryMode.valueOf(text(c,"memoryMode",10,true));
        if(!(c.get("initiate") instanceof Boolean))throw new Exception("Invalid preference");
        result.initiate=c.getBoolean("initiate");
        result.quietStart=time(c,"quietStart");result.quietEnd=time(c,"quietEnd");
        JSONArray memories=c.getJSONArray("memories");if(memories.length()>1000)throw new Exception("Too many memories");
        HashSet<String> ids=new HashSet<>();
        for(int i=0;i<memories.length();i++){
            JSONObject m=memories.getJSONObject(i);keys(m,"id","text","createdAt");
            String id=text(m,"id",80,true);if(!ids.add(id))throw new Exception("Duplicate memory ID");
            Object date=m.get("createdAt");if(!(date instanceof Number))throw new Exception("Invalid memory date");
            long stamp=((Number)date).longValue();if(stamp<0 || stamp>253402300799999L || ((Number)date).doubleValue()!=stamp)throw new Exception("Invalid memory date");
            result.memories.add(new CharacterBook.Memory(UUID.randomUUID().toString(),text(m,"text",1000,true),stamp));
        }
        return new Restored(result,historyEnabled,sessions);
    }
    static JSONArray validateHistory(JSONArray sessions)throws Exception {
        if(sessions.length()>10 || sessions.toString().length()>200000)throw new Exception("History too large");
        for(int i=0;i<sessions.length();i++){
            JSONObject s=sessions.getJSONObject(i);keys(s,"at","fragments");
            Object at=s.get("at");if(!(at instanceof Number))throw new Exception("Invalid history date");
            long stamp=((Number)at).longValue();if(stamp<0 || stamp>253402300799999L || ((Number)at).doubleValue()!=stamp)throw new Exception("Invalid history date");
            JSONArray f=s.getJSONArray("fragments");if(f.length()>2000 || f.toString().length()>80000)throw new Exception("Session too large");
            for(int j=0;j<f.length();j++){
                JSONObject item=f.getJSONObject(j);keys(item,"role","text");
                String role=text(item,"role",12,true);if(!role.equals("user") && !role.equals("character"))throw new Exception("Invalid transcript role");
                text(item,"text",12000,false);
            }
        }
        return new JSONArray(sessions.toString());
    }
    private static void keys(JSONObject object,String... allowed) throws Exception {
        java.util.Set<String> set=new HashSet<>(java.util.Arrays.asList(allowed));
        java.util.Iterator<String> it=object.keys();while(it.hasNext())if(!set.remove(it.next()))throw new Exception("Unknown field");
        if(!set.isEmpty())throw new Exception("Missing field");
    }
    private static String text(JSONObject o,String key,int max,boolean required) throws Exception {
        Object v=o.get(key);if(!(v instanceof String))throw new Exception("Invalid text");String s=(String)v;
        if(s.length()>max || (required && s.trim().isEmpty()))throw new Exception("Invalid text length");return s;
    }
    private static String time(JSONObject o,String key) throws Exception {
        String s=text(o,key,5,true);if(!s.matches("([01][0-9]|2[0-3]):[0-5][0-9]"))throw new Exception("Invalid quiet hours");return s;
    }
}
