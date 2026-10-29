package com.campus.adblock;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;

/** Installation evidence only; runtime execution remains in the separate local event list. */
final class HealthReporter {
    private static Context context;
    private static boolean localConfig;
    private static JSONArray steps=new JSONArray();
    private HealthReporter(){}
    static void begin(Context c){context=c;steps=new JSONArray();}
    static void configuration(boolean value){localConfig=value;}
    static synchronized void step(String name,boolean success){try{steps.put(new JSONObject().put("name",name).put("installed",success));}catch(Exception ignored){}}
    static void finish(){
        Context c=context;if(c==null)return;
        try{
            String process=Build.VERSION.SDK_INT>=28?android.app.Application.getProcessName():(String)Class.forName("android.app.ActivityThread").getDeclaredMethod("currentProcessName").invoke(null);
            if(process==null||!process.equals(c.getPackageName()))return;
            JSONObject report=new JSONObject().put("steps",steps).put("mode",Cfg.automationMode).put("localConfig",localConfig);
            Bundle b=new Bundle();b.putString("report",report.toString());RuntimeControl.call(c,"health",b);
        }catch(Throwable ignored){}
    }
}
