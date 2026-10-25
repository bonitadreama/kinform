package org.kinform.app;
import org.json.*;
public final class CharacterBackupTest {
    static void check(boolean condition){if(!condition)throw new AssertionError();}
    static void reject(String raw)throws Exception{
        try{CharacterBackup.decode(raw);}catch(Exception expected){return;}throw new AssertionError("Accepted invalid backup");
    }
    public static void main(String[] args)throws Exception{
        CharacterBook book=new CharacterBook();CharacterBook.Character original=book.create(0);
        original.name="Greg 🪨";original.voice="voice_example";original.voiceLabel="Reluctantly Fond";
        original.personality="Sarcasm, affection, and \"quotes\".\nUnicode: 🖤";
        original.memories.add(new CharacterBook.Memory("old-id","A private memory 🖤",123456789L));
        original.memoryMode=CharacterBook.MemoryMode.OFF;original.initiate=true;
        original.quietStart="23:15";original.quietEnd="06:45";
        String raw=CharacterBackup.encode(original);CharacterBook.Character restored=CharacterBackup.decode(raw);
        check(book.characters.size()==1 && !restored.id.equals(original.id));
        check(restored.personality.equals(original.personality) && restored.name.equals(original.name));
        check(restored.voice.equals(original.voice) && restored.voiceLabel.equals(original.voiceLabel));
        check(restored.boundaries.equals(original.boundaries) && restored.body.equals(original.body));
        check(restored.initiate && restored.quietStart.equals("23:15") && restored.quietEnd.equals("06:45"));
        check(restored.memoryMode==CharacterBook.MemoryMode.OFF && restored.conversationMemories().isEmpty());
        check(restored.memories.size()==1 && restored.memories.get(0).text.equals(original.memories.get(0).text));
        check(restored.memories.get(0).createdAt==123456789L && !restored.memories.get(0).id.equals("old-id"));
        restored.memories.clear();check(original.memories.size()==1);
        reject(raw+" junk");reject("{}");reject(new JSONObject(raw).put("version",3).toString());
        reject(new JSONObject(raw).put("token","secret").toString());
        for(String field:new String[]{"name","personality","voice","memories"}){
            JSONObject bad=new JSONObject(raw);bad.getJSONObject("character").put(field,42);reject(bad.toString());
        }
        JSONObject bad=new JSONObject(raw);bad.getJSONObject("character").put("quietStart","25:00");reject(bad.toString());
        bad=new JSONObject(raw);bad.getJSONObject("character").getJSONArray("memories").getJSONObject(0).put("createdAt",-1);reject(bad.toString());
        bad=new JSONObject(raw);bad.getJSONObject("character").getJSONArray("memories").put(bad.getJSONObject("character").getJSONArray("memories").getJSONObject(0));reject(bad.toString());
        bad=new JSONObject(raw);bad.getJSONObject("character").put("voice","unrecognized");reject(bad.toString());
        JSONArray sessions=new JSONArray().put(new JSONObject().put("at",12345L).put("fragments",new JSONArray()
            .put(new JSONObject().put("role","user").put("text","Pancake Volcano 🖤"))
            .put(new JSONObject().put("role","character").put("text","Bring purple slippers!"))));
        String complete=CharacterBackup.encode(original,true,sessions);
        CharacterBackup.Restored copy=CharacterBackup.restore(complete);
        check(copy.historyEnabled && copy.sessions.toString().equals(sessions.toString()));
        check(copy.character.memoryMode==CharacterBook.MemoryMode.OFF);
        check(!copy.character.id.equals(original.id));
        copy.sessions.getJSONObject(0).getJSONArray("fragments").getJSONObject(0).put("text","changed");
        check(sessions.getJSONObject(0).getJSONArray("fragments").getJSONObject(0).getString("text").equals("Pancake Volcano 🖤"));
        check(!CharacterBackup.restore(complete).character.id.equals(copy.character.id));
        CharacterBackup.Restored disabled=CharacterBackup.restore(CharacterBackup.encode(original,false,sessions));
        check(!disabled.historyEnabled && disabled.sessions.length()==1);
        JSONObject legacy=new JSONObject(raw).put("version",1);legacy.remove("history");
        CharacterBackup.Restored old=CharacterBackup.restore(legacy.toString());
        check(!old.historyEnabled && old.sessions.length()==0 && old.character.memories.size()==1);
        JSONObject invalid=new JSONObject(complete);invalid.getJSONObject("history").put("enabled","true");reject(invalid.toString());
        invalid=new JSONObject(complete);invalid.getJSONObject("history").put("apiKey","secret");reject(invalid.toString());
        invalid=new JSONObject(complete);invalid.getJSONObject("history").getJSONArray("sessions").getJSONObject(0).getJSONArray("fragments").getJSONObject(0).put("role","system");reject(invalid.toString());
        invalid=new JSONObject(complete);invalid.getJSONObject("history").getJSONArray("sessions").getJSONObject(0).put("at",-1);reject(invalid.toString());
        invalid=new JSONObject(complete);invalid.getJSONObject("history").getJSONArray("sessions").getJSONObject(0).getJSONArray("fragments").getJSONObject(0).put("text",new String(new char[12001]));reject(invalid.toString());
        invalid=new JSONObject(complete);JSONArray overflow=invalid.getJSONObject("history").getJSONArray("sessions");for(int i=1;i<11;i++)overflow.put(sessions.getJSONObject(0));reject(invalid.toString());
        JSONObject missing=new JSONObject(complete);missing.remove("history");reject(missing.toString());
        check(!complete.contains("apiKey") && !complete.contains("token") && !complete.contains("certificate"));
        original.memoryMode=CharacterBook.MemoryMode.AUTO;
        check(CharacterBackup.restore(CharacterBackup.encode(original,true,sessions)).character.memoryMode==CharacterBook.MemoryMode.AUTO);
        original.memories.clear();for(int i=0;i<1000;i++)original.memories.add(new CharacterBook.Memory("id"+i,"x",i));
        check(CharacterBackup.decode(CharacterBackup.encode(original)).memories.size()==1000);
        original.memories.add(new CharacterBook.Memory("overflow"));
        try{CharacterBackup.encode(original);throw new AssertionError("Exported an unrestorable file");}catch(AssertionError e){throw e;}catch(Exception expected){}
        System.out.println("PASS: v1 compatibility, v2 history round-trip, independent IDs/data, disabled-history retention, strict history validation, fact/voice preservation and bounds");
    }
}
