package org.kinform.app;

import android.content.Context;
import org.json.*;
import java.util.UUID;

/** Untrusted drafts only. Never supplied as conversation context or saved facts. */
final class SuggestionStore {
    static JSONArray load(Context c,String id)throws Exception {
        return new JSONArray(c.getSharedPreferences("kinform.suggestions",0).getString(id,"[]"));
    }
    private static void write(Context c,String id,JSONArray items)throws Exception {
        if(!c.getSharedPreferences("kinform.suggestions",0).edit().putString(id,items.toString()).commit())throw new Exception("Draft storage failed");
    }
    static boolean offer(Context c,CharacterBook.Character owner,String text)throws Exception {
        return offerResult(c,owner,text)==MemoryTrace.Event.QUEUED;
    }
    static MemoryTrace.Event offerResult(Context c,CharacterBook.Character owner,String text)throws Exception {
        if(owner.memoryMode!=CharacterBook.MemoryMode.AUTO)return MemoryTrace.Event.OFF;
        String clean=text.trim();if(clean.isEmpty() || clean.length()>1000)return MemoryTrace.Event.INVALID;
        JSONArray items=load(c,owner.id);if(items.length()>=20)return MemoryTrace.Event.FULL;
        for(CharacterBook.Memory m:owner.memories)if(m.text.equalsIgnoreCase(clean))return MemoryTrace.Event.DUPLICATE;
        for(int i=0;i<items.length();i++)if(items.getJSONObject(i).getString("text").equalsIgnoreCase(clean))return MemoryTrace.Event.DUPLICATE;
        items.put(new JSONObject().put("id",UUID.randomUUID().toString()).put("text",clean));
        write(c,owner.id,items);return MemoryTrace.Event.QUEUED;
    }
    static JSONObject find(Context c,String owner,String id)throws Exception {
        JSONArray items=load(c,owner);
        for(int i=0;i<items.length();i++)if(items.getJSONObject(i).getString("id").equals(id))return items.getJSONObject(i);
        throw new Exception("Suggestion no longer available");
    }
    static void remove(Context c,String owner,String id)throws Exception {
        JSONArray items=load(c,owner),keep=new JSONArray();
        for(int i=0;i<items.length();i++)if(!items.getJSONObject(i).getString("id").equals(id))keep.put(items.getJSONObject(i));
        write(c,owner,keep);
    }
    static void delete(Context c,String owner)throws Exception {write(c,owner,new JSONArray());MemoryDiagnostics.delete(c,owner);}
}
