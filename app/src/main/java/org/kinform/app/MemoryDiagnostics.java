package org.kinform.app;
import android.content.Context;

/** One bounded local diagnostic per character, excluded from character exports. */
final class MemoryDiagnostics {
    static boolean save(Context c,String id,MemoryTrace trace){
        return c.getSharedPreferences("kinform.memory-diagnostics",0).edit().putString(id,trace.report()).commit();
    }
    static String read(Context c,String id){
        return c.getSharedPreferences("kinform.memory-diagnostics",0).getString(id,"No diagnostic yet. Make one Talk call with this version, then Stop and reopen this report.");
    }
    static void delete(Context c,String id){c.getSharedPreferences("kinform.memory-diagnostics",0).edit().remove(id).apply();}
}
