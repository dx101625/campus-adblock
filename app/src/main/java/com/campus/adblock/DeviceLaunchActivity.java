package com.campus.adblock;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Launcher entry carries only a local profile ID, never arbitrary QR text or an external intent. */
public final class DeviceLaunchActivity extends Activity {
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        try {
            String id=getIntent().getStringExtra("profile.id");
            if(id==null||id.length()>64)throw new IllegalArgumentException("设备入口无效");
            Intent target=getPackageManager().getLaunchIntentForPackage("com.qiekj.user");
            if(target==null)throw new IllegalArgumentException("请先安装胖乖生活");
            AutomationStore.prepareLaunch(this,id);
            target.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(target);
        }catch(Exception error){Toast.makeText(this,error.getMessage()==null?"暂时无法打开设备":error.getMessage(),Toast.LENGTH_LONG).show();}
        finish();
    }
}
