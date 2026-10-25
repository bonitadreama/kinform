package org.kinform.app;

import android.content.Context;

final class SessionSettings {
    static int minutes(Context context){
        try{return SessionDuration.restored(context.getSharedPreferences("kinform.session",0).getInt("minutes",SessionDuration.DEFAULT_MINUTES));}
        catch(ClassCastException e){return SessionDuration.DEFAULT_MINUTES;}
    }
    static boolean save(Context context,int minutes){
        if(!SessionDuration.valid(minutes))throw new IllegalArgumentException("Invalid session duration");
        return context.getSharedPreferences("kinform.session",0).edit().putInt("minutes",minutes).commit();
    }
}
