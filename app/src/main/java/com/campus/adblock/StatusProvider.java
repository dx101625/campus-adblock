package com.campus.adblock;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * 心跳接收器：目标 App 里的 hook 装好后，跨进程调一次这里，把「谁、什么时候生效」
 * 写进模块自己的首选项文件 {@link #GROUP}，设置界面直接读同一份文件显示状态。
 *
 * <p>为什么不用 {@code getRemotePreferences}：libxposed 的 remote prefs 在部分框架实现里
 * 是只读的，写进去可能直接丢掉、也可能被模块进程的缓存挡住，导致界面永远显示「未生效」。
 * ContentProvider 的调用落在模块进程里，写入和界面读取是同一个进程、同一份
 * SharedPreferences，不存在缓存不同步的问题。</p>
 *
 * <p>provider 是 exported 的：目标 App 与模块签名不同，没法用 signature 权限保护。
 * 它能做的事只有「记录一次心跳」，最坏情况是别的应用伪造一个状态展示，不影响任何功能。</p>
 */
public class StatusProvider extends ContentProvider {

    public static final String AUTHORITY = "com.campus.adblock.status";
    public static final String GROUP = "campus_status";

    public static Uri uri() {
        return Uri.parse("content://" + AUTHORITY);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!RuntimeEvents.allowedPackage(arg)) { Bundle denied = new Bundle(); denied.putBoolean("ok", false); return denied; }
        if ("config".equals(method) || "session".equals(method) || "guard".equals(method) || "scan_result".equals(method)
                || "quote".equals(method) || "consume_grant".equals(method) || "jump".equals(method) || "health".equals(method)) {
            Context context = getContext(); Bundle out = new Bundle();
            String[] callers = context == null ? null : context.getPackageManager().getPackagesForUid(android.os.Binder.getCallingUid());
            boolean trusted = android.os.Binder.getCallingUid() == android.os.Process.myUid()
                    || (callers != null && java.util.Arrays.asList(callers).contains(arg));
            if (!trusted || context == null) { out.putBoolean("ok", false); return out; }
            if ("config".equals(method) || "session".equals(method)) return AutomationStore.snapshot(context,"session".equals(method));
            return RuntimeStore.handle(context,arg,method,extras==null?new Bundle():extras);
        }
        if ("event".equals(method) && extras != null && getContext() != null) {
            RuntimeEvents.record(getContext(), arg, extras.getString("kind"), extras.getString("detail"));
        }
        if ("beat".equals(method) && arg != null && !arg.isEmpty()) {
            Context ctx = getContext();
            if (ctx != null) {
                String ver = extras == null ? "" : extras.getString("ver", "");
                ctx.getSharedPreferences(GROUP, Context.MODE_PRIVATE)
                        .edit()
                        .putString("p." + arg + ".ver", ver)
                        .putLong("p." + arg + ".ts", System.currentTimeMillis())
                        .commit();
                RuntimeEvents.record(ctx, arg, "loaded", "模块已加载；具体功能结果请查看后续记录");
            }
        }
        Bundle out = new Bundle();
        out.putBoolean("ok", true);
        return out;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
