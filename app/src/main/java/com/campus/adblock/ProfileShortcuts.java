package com.campus.adblock;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.widget.Toast;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/** Android native shortcuts; profile options are resolved privately each time they are used. */
public final class ProfileShortcuts {
    private ProfileShortcuts(){}
    public static void launch(Context c,String id){c.startActivity(new Intent(c,DeviceLaunchActivity.class).putExtra("profile.id",id));}
    private static ShortcutInfo make(Context c,String id,String name){
        JSONObject profile=AutomationStore.find(c,id);String icon=profile==null?"other":profile.optString("icon","other");
        int resource=switch(icon){case "hair"->R.drawable.ic_device_hair;case "washer"->R.drawable.ic_device_washer;case "water"->R.drawable.ic_device_water;default->R.drawable.ic_device_other;};
        return new ShortcutInfo.Builder(c,"device."+id).setShortLabel(name).setLongLabel(name)
            .setActivity(new ComponentName(c,SettingsActivity.class)).setIcon(Icon.createWithResource(c,resource))
            .setIntent(new Intent(c,DeviceLaunchActivity.class).setAction(Intent.ACTION_VIEW).putExtra("profile.id",id)).build();
    }
    public static void publish(Context c){
        if(Build.VERSION.SDK_INT<25)return;
        try{
            ShortcutManager manager=c.getSystemService(ShortcutManager.class);if(manager==null)return;
            JSONArray profiles=AutomationStore.devices(c);ArrayList<ShortcutInfo> items=new ArrayList<>();ArrayList<String> alive=new ArrayList<>();
            int max=Math.min(4,manager.getMaxShortcutCountPerActivity());
            for(int i=0;i<profiles.length();i++){
                JSONObject p=profiles.getJSONObject(i);String id=p.getString("id");alive.add("device."+id);
                if(items.size()<Math.max(0,max-1))items.add(make(c,id,p.getString("name")));
            }
            alive.add("device._manual");if(items.size()<max)items.add(make(c,"_manual","仅打开胖乖首页"));
            ArrayList<String> stale=new ArrayList<>();for(ShortcutInfo p:manager.getPinnedShortcuts())if(p.getId().startsWith("device.")&&!alive.contains(p.getId()))stale.add(p.getId());
            if(!stale.isEmpty())manager.disableShortcuts(stale,"设备收藏已删除，可重新添加");
            manager.enableShortcuts(alive);manager.setDynamicShortcuts(items);
            ArrayList<ShortcutInfo> updated=new ArrayList<>();for(int i=0;i<profiles.length();i++){JSONObject p=profiles.getJSONObject(i);updated.add(make(c,p.getString("id"),p.getString("name")));}manager.updateShortcuts(updated);
        }catch(Throwable ignored){}
    }
    public static void pin(Activity a,String id){
        if(Build.VERSION.SDK_INT<26){Toast.makeText(a,"当前系统不支持应用内添加桌面入口",Toast.LENGTH_LONG).show();return;}
        try{
            JSONObject p=AutomationStore.find(a,id);if(p==null)throw new IllegalArgumentException("请先保存设备");
            ShortcutManager m=a.getSystemService(ShortcutManager.class);
            if(m==null||!m.isRequestPinShortcutSupported())throw new IllegalArgumentException("当前桌面不支持添加快捷方式");
            if(!m.requestPinShortcut(make(a,id,p.getString("name")),null))throw new IllegalArgumentException("桌面暂未接受请求");
        }catch(Exception e){Toast.makeText(a,e.getMessage(),Toast.LENGTH_LONG).show();}
    }
}
