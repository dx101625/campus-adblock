package com.campus.adblock;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;

/** Private module-side counters, short-lived grants and observed quotes; excluded from backups. */
public final class RuntimeStore {
    private RuntimeStore() {}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(AutomationStore.RUNTIME,0);}
    public static JSONObject json(Context c,String key){try{return new JSONObject(prefs(c).getString(key,"{}"));}catch(Exception e){return new JSONObject();}}
    public static JSONArray array(Context c,String key){try{return new JSONArray(prefs(c).getString(key,"[]"));}catch(Exception e){return new JSONArray();}}
    private static void put(Context c,String key,Object value){prefs(c).edit().putString(key,value.toString()).commit();}
    private static boolean key(String s){return s!=null && s.matches("[0-9a-f]{64}");}
    public static synchronized Bundle handle(Context c,String pkg,String method,Bundle b) {
        Bundle out=new Bundle();out.putBoolean("ok",true);
        try {
            if ("guard".equals(method)) {
                String k=b.getString("key","");out.putBoolean("paused",key(k)&&json(c,"failures").optInt(k,0)>=3);
            } else if ("scan_result".equals(method)) {
                String k=b.getString("key","");if(!key(k))return out;
                JSONObject failures=json(c,"failures");
                if(b.getBoolean("success")) failures.remove(k);
                else if(c.getSharedPreferences(Cfg.GROUP,0).getBoolean("q.pause",true)){
                    if(!failures.has(k)&&failures.length()>=30) failures.remove(failures.keys().next());
                    failures.put(k,Math.min(3,failures.optInt(k,0)+1));
                }
                put(c,"failures",failures);
            } else if ("quote".equals(method)) {
                String hash=b.getString("key","");long cents=b.getLong("cents",-1);double duration=b.getDouble("duration",Double.NaN);int machine=b.getInt("machine",-1);String tier=b.getString("tier","");
                if(!key(hash)||cents<0||cents>100_000_000||!Double.isFinite(duration)||duration<=0||machine<0||machine>1||!("shortest".equals(tier)||"longest".equals(tier))) {out.putBoolean("allowed",false);return out;}
                String k=hash+":"+machine+":"+tier;JSONObject quotes=json(c,"quotes"),old=quotes.optJSONObject(k);
                JSONObject now=new JSONObject().put("key",k).put("cents",cents).put("duration",duration).put("at",System.currentTimeMillis());
                if(old==null) {if(quotes.length()>=60)quotes.remove(quotes.keys().next());quotes.put(k,now);put(c,"quotes",quotes);out.putBoolean("allowed",true);}
                else if(PreferenceRules.changedQuote(old.optLong("cents"),old.optDouble("duration"),cents,duration)){
                    now.put("oldCents",old.optLong("cents")).put("oldDuration",old.optDouble("duration"));put(c,"quote.pending",now);out.putBoolean("allowed",false);
                } else {out.putBoolean("allowed",true);}
            } else if ("consume_grant".equals(method)) {
                String dest=b.getString("destination","");JSONObject all=json(c,"grants");
                boolean allowed=PreferenceRules.consumeGrant(all,pkg,dest,System.currentTimeMillis());
                if(allowed)put(c,"grants",all);out.putBoolean("allowed",allowed);
            } else if ("jump".equals(method)) {
                if(!c.getSharedPreferences(Cfg.GROUP,0).getBoolean("g.log",true))return out;
                String dest=b.getString("destination","");if(!JumpPolicy.DESTINATIONS.containsKey(dest))return out;
                JSONArray prior=array(c,"jumps"),next=new JSONArray();for(int i=Math.max(0,prior.length()-19);i<prior.length();i++)next.put(prior.get(i));
                next.put(new JSONObject().put("source",pkg).put("destination",dest).put("at",System.currentTimeMillis()));put(c,"jumps",next);
                RuntimeEvents.record(c,pkg,"jump_blocked","已拦截跳转到"+JumpPolicy.DESTINATIONS.get(dest));
            } else if ("health".equals(method)) {
                String report=b.getString("report","");if(report.length()>8192)return out;
                JSONObject health=new JSONObject(report);JSONArray steps=health.getJSONArray("steps");if(steps.length()>60)return out;
                for(int i=0;i<steps.length();i++) if(steps.getJSONObject(i).getString("name").length()>96)return out;
                health.put("at",System.currentTimeMillis());put(c,"health."+pkg,health);
            }
        }catch(Exception ignored){out.putBoolean("ok",false);}
        return out;
    }
    public static synchronized void resume(Context c){put(c,"failures",new JSONObject());}
    public static synchronized void acceptQuote(Context c){
        JSONObject pending=json(c,"quote.pending");String k=pending.optString("key");if(k.isEmpty())return;
        try{JSONObject quotes=json(c,"quotes");pending.remove("oldCents");pending.remove("oldDuration");quotes.put(k,pending);put(c,"quotes",quotes);prefs(c).edit().remove("quote.pending").commit();}catch(Exception ignored){}
    }
    public static synchronized void grant(Context c,String source,String destination,boolean once){
        if(!RuntimeEvents.allowedPackage(source)||!JumpPolicy.DESTINATIONS.containsKey(destination))throw new IllegalArgumentException("应用无效");
        try{long now=System.currentTimeMillis();JSONObject all=json(c,"grants");all.put(source+"/"+destination,new JSONObject().put("source",source).put("destination",destination).put("issued",now).put("expires",now+300_000).put("remaining",once?1:-1));put(c,"grants",all);}catch(Exception e){throw new IllegalArgumentException("未能保存临时允许");}
    }
    public static synchronized void revokeGrants(Context c){put(c,"grants",new JSONObject());}
}
