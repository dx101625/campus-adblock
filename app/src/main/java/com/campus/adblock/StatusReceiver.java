package com.campus.adblock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * 目标 App 进程通过显式广播把「我已生效」告诉模块进程。
 *
 * <p>为什么不只用 ContentProvider：Android 11 起包可见性受限，目标 App 的
 * {@code ContentResolver.call} 会直接抛 {@code Unknown authority}，而显式广播
 * 是按组件投递的，不受可见性影响。</p>
 */
public class StatusReceiver extends BroadcastReceiver {

    static final String ACTION = "com.campus.adblock.STATUS";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String pkg = intent.getStringExtra("pkg");
        if (!RuntimeEvents.allowedPackage(pkg)) {
            return;
        }
        if (intent.hasExtra("event_kind")) {
            RuntimeEvents.record(context, pkg, intent.getStringExtra("event_kind"), intent.getStringExtra("event_detail"));
            return;
        }
        String ver = intent.getStringExtra("ver");
        SharedPreferences sp = context.getSharedPreferences(StatusProvider.GROUP, Context.MODE_PRIVATE);
        sp.edit()
                .putString("p." + pkg + ".ver", ver == null ? "" : ver)
                .putLong("p." + pkg + ".ts", System.currentTimeMillis())
                .commit();
        RuntimeEvents.record(context, pkg, "loaded", "模块已加载；具体功能结果请查看后续记录");
    }
}
