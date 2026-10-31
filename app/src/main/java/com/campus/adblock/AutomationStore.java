package com.campus.adblock;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

/** Module-process configuration projection. QR values never leave the authenticated target channel. */
public final class AutomationStore {
    public static final String RUNTIME = "campus_runtime";
    private AutomationStore() {}
    public static String mode(SharedPreferences prefs) {
        String value = prefs.getString("q.mode", "");
        return PreferenceRules.validMode(value) ? value : PreferenceRules.mode(prefs.getBoolean("q.scan", true), prefs.getBoolean("q.advance", true));
    }
    public static void setMode(Context context, String mode) {
        if (!PreferenceRules.validMode(mode)) throw new IllegalArgumentException("模式无效");
        context.getSharedPreferences(Cfg.GROUP, 0).edit().putString("q.mode", mode)
            .putBoolean("q.scan", !"ads".equals(mode)).putBoolean("q.advance", "preview".equals(mode)).commit();
    }
    public static JSONArray devices(Context context) {
        try { return new JSONArray(context.getSharedPreferences(Cfg.GROUP, 0).getString("ui.devices", "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }
    public static JSONObject find(Context context, String id) {
        JSONArray all = devices(context);
        for (int i=0;i<all.length();i++) { JSONObject p=all.optJSONObject(i); if (p!=null && id.equals(p.optString("id"))) return p; }
        return null;
    }
    public static void prepareLaunch(Context context, String id) {
        if (!"_manual".equals(id) && find(context, id)==null) throw new IllegalArgumentException("设备收藏已不存在");
        context.getSharedPreferences(RUNTIME, 0).edit().putString("launch.id", id).putString("launch.token", UUID.randomUUID().toString())
            .putLong("launch.until", System.currentTimeMillis()+120_000L).putBoolean("launch.claimed", false).commit();
    }
    public static synchronized Bundle snapshot(Context context, boolean claim) {
        SharedPreferences p=context.getSharedPreferences(Cfg.GROUP,0), r=context.getSharedPreferences(RUNTIME,0);
        Bundle v=new Bundle();
        for (String key:new String[]{"q.ads","q.splash","z.ads","z.tab","g.shake","g.jump","g.log","q.price.guard","q.pause"}) v.putBoolean(key,p.getBoolean(key,true));
        for (String key:new String[]{"q.coupon","q.points"}) v.putBoolean(key,p.getBoolean(key,false));
        v.putString("q.machine",p.getString("q.machine","right2"));v.putString("q.tier",p.getString("q.tier","shortest"));v.putString("q.pay",p.getString("q.pay","unionpay"));
        v.putString("q.qr",p.getString("q.qr",""));v.putString("g.jump.mode",p.getString("g.jump.mode","standard"));v.putString("g.jump.allowed",p.getString("g.jump.allowed",""));
        long remaining=r.getLong("launch.until",0)-System.currentTimeMillis();
        boolean pending=remaining>0 && remaining<=120_000L && !r.getBoolean("launch.claimed",true);
        String id=pending?r.getString("launch.id",""):"", global=mode(p), mode=global;
        JSONObject profile=pending && !"_manual".equals(id)?find(context,id):null;
        if (!pending) {
            JSONObject selected=find(context,p.getString("q.profile",""));
            if(selected!=null&&v.getString("q.qr","").equals(selected.optString("qr")))profile=selected;
            JSONArray all=devices(context);
            for (int i=0;!p.contains("q.profile")&&profile==null&&i<all.length();i++) { JSONObject item=all.optJSONObject(i); if (item!=null && !v.getString("q.qr","").isEmpty() && v.getString("q.qr").equals(item.optString("qr"))) {profile=item;break;} }
        }
        if (profile!=null) {
            v.putString("q.qr",profile.optString("qr",v.getString("q.qr")));v.putString("runtime.profile",profile.optString("id"));
            JSONObject o=profile.optJSONObject("options");
            if (o!=null) {
                if (PreferenceRules.validMode(o.optString("mode"))) mode=o.optString("mode");
                for (String key:new String[]{"machine","tier","pay"}) if (!o.optString(key,"inherit").equals("inherit")) v.putString("q."+key,o.optString(key));
                for (String key:new String[]{"coupon","points"}) if (!o.optString(key,"inherit").equals("inherit")) v.putBoolean("q."+key,"yes".equals(o.optString(key)));
            }
        }
        mode="ads".equals(global) ? "ads" : "preview";
        if ("_manual".equals(id)) mode="ads";
        v.putBoolean("q.price.guard",false);v.putBoolean("q.pause",false);
        v.putString("runtime.mode",mode);v.putBoolean("runtime.selections",!"ads".equals(mode));
        v.putBoolean("q.scan",!"ads".equals(mode));v.putBoolean("q.advance","preview".equals(mode));
        Bundle out=new Bundle();out.putBoolean("ok",true);out.putBundle("values",v);
        if (pending && claim) {
            out.putString("launch.token",r.getString("launch.token",""));
            r.edit().putBoolean("launch.claimed",true).commit();
        }
        return out;
    }
}
