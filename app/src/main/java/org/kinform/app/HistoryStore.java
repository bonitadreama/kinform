package org.kinform.app;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;

final class HistoryStore {
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("kinform.history",0);}
    static boolean enabled(Context c,String id){return prefs(c).getBoolean(id+".enabled",false);}
    static void enable(Context c,String id,boolean enabled)throws Exception{
        if(!prefs(c).edit().putBoolean(id+".enabled",enabled).commit())throw new Exception();
    }
    static JSONArray load(Context c,String id)throws Exception{return new JSONArray(prefs(c).getString(id+".sessions","[]"));}
    static String context(Context c,String id)throws Exception{return enabled(c,id)?ConversationHistory.context(load(c,id)):"";}
    static synchronized void append(Context c,String id,JSONObject session)throws Exception{
        if(!enabled(c,id) || session.getJSONArray("fragments").length()==0)return;
        JSONArray sessions=load(c,id);sessions.put(session);
        if(!prefs(c).edit().putString(id+".sessions",ConversationHistory.bounded(sessions).toString()).commit())throw new Exception();
    }
    static void delete(Context c,String id)throws Exception{
        if(!prefs(c).edit().remove(id+".sessions").putBoolean(id+".enabled",false).commit())throw new Exception();
    }
    static void restoreNew(Context c,String id,boolean enabled,JSONArray sessions)throws Exception{
        SharedPreferences p=prefs(c);
        if(p.contains(id+".sessions") || p.contains(id+".enabled"))throw new Exception("History already exists");
        String raw=CharacterBackup.validateHistory(sessions).toString();
        if(!p.edit().putString(id+".sessions",raw).putBoolean(id+".enabled",enabled).commit())throw new Exception("Could not restore history");
    }
}
