package org.kinform.app;
import org.json.*;
import java.util.*;

/** Bounded transcript excerpts, not invented turn boundaries or model-authored factual memories. */
final class ConversationHistory {
    private final LinkedHashSet<String> seen=new LinkedHashSet<>();
    private JSONArray fragments=new JSONArray();
    synchronized void accept(JSONObject event)throws Exception {
        String type=event.optString("type"),id=event.optString("event_id"),delta=event.optString("delta");
        if(!type.equals("session.input_transcript.delta") && !type.equals("session.output_transcript.delta"))return;
        if(id.isEmpty() || delta.isEmpty() || !seen.add(id))return;
        if(seen.size()>1024)seen.remove(seen.iterator().next());
        String role=type.equals("session.input_transcript.delta")?"user":"character";
        JSONObject last=fragments.optJSONObject(fragments.length()-1);
        if(last!=null && last.getString("role").equals(role))last.put("text",LiveProtocol.tail(last.getString("text")+delta,12000));
        else fragments.put(new JSONObject().put("role",role).put("text",LiveProtocol.tail(delta,12000)));
        while(fragments.length()>1 && fragments.toString().length()>16000)fragments.remove(0);
        while(fragments.toString().length()>16000){
            JSONObject only=fragments.getJSONObject(0);String text=only.getString("text");
            only.put("text",LiveProtocol.tail(text,text.length()/2));
        }
    }
    synchronized JSONObject snapshot()throws Exception{return new JSONObject().put("at",System.currentTimeMillis()).put("fragments",new JSONArray(fragments.toString()));}
    static JSONArray bounded(JSONArray sessions)throws Exception {
        while(sessions.length()>10 || (sessions.length()>1 && sessions.toString().length()>60000))sessions.remove(0);
        return sessions;
    }
    static String context(JSONArray sessions)throws Exception {
        return LiveProtocol.tail(display(sessions),10000);
    }
    static String display(JSONArray sessions)throws Exception {
        StringBuilder text=new StringBuilder();
        for(int i=0;i<sessions.length();i++){
            JSONObject s=sessions.getJSONObject(i);
            text.append("\nSession ").append(new Date(s.getLong("at")).toString()).append(":\n");
            JSONArray f=s.getJSONArray("fragments");
            for(int j=0;j<f.length();j++){JSONObject chunk=f.getJSONObject(j);text.append(chunk.getString("role")).append(": ").append(chunk.getString("text")).append('\n');}
        }
        return text.toString();
    }
}
