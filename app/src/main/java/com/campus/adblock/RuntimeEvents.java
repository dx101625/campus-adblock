package com.campus.adblock;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Small local event summaries. Never accepts QR text, order data or account data. */
public final class RuntimeEvents {
    public static final String GROUP = "campus_events";
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "campus-events"); t.setDaemon(true); return t;
    });
    private RuntimeEvents() {}

    public static void note(Context context, String kind, String detail) {
        if (context == null || !Cfg.logEnabled) return;
        Context app = context.getApplicationContext() == null ? context : context.getApplicationContext();
        String pkg = app.getPackageName();
        if (!allowedPackage(pkg)) return;
        final String message = clean(detail);
        WORKER.execute(() -> {
            try {
                Bundle extras = new Bundle(); extras.putString("kind", kind); extras.putString("detail", message);
                Bundle out = app.getContentResolver().call(StatusProvider.uri(), "event", pkg, extras);
                if (out != null && out.getBoolean("ok")) return;
            } catch (Throwable ignored) {}
            try {
                Intent i = new Intent(StatusReceiver.ACTION).setClassName("com.campus.adblock", "com.campus.adblock.StatusReceiver");
                i.putExtra("pkg", pkg).putExtra("event_kind", kind).putExtra("event_detail", message);
                app.sendBroadcast(i);
            } catch (Throwable ignored) {}
        });
    }

    public static boolean allowedPackage(String pkg) {
        return "com.qiekj.user".equals(pkg) || "com.klcxkj.zqxy".equals(pkg);
    }

    public static String clean(String text) {
        if (text == null) return "";
        String result = text.replaceAll("https?://\\S+", "[链接已隐藏]")
                .replaceAll("(?i)(NQT|IMEI|SN|token|deviceId|orderId)\\s*[=:]\\s*[^\\s,;]+", "$1=[已隐藏]")
                .replaceAll("[\\r\\n\\t]", " ");
        return result.substring(0, Math.min(240, result.length()));
    }

    public static synchronized void record(Context context, String pkg, String kind, String detail) {
        if (!allowedPackage(pkg) || kind == null || !kind.matches("[a-z_]{1,32}")
                || !context.getSharedPreferences(Cfg.GROUP, Context.MODE_PRIVATE).getBoolean("g.log", true)) return;
        try {
            android.content.SharedPreferences sp = context.getSharedPreferences(GROUP, Context.MODE_PRIVATE);
            JSONArray previous = new JSONArray(sp.getString("events", "[]"));
            String message = clean(detail); long now = System.currentTimeMillis();
            if (previous.length() > 0) {
                JSONObject last = previous.getJSONObject(previous.length() - 1);
                if (pkg.equals(last.optString("pkg")) && kind.equals(last.optString("kind"))
                        && message.equals(last.optString("detail")) && now - last.optLong("time") < 2000) return;
            }
            JSONArray next = new JSONArray();
            for (int i = Math.max(0, previous.length() - 29); i < previous.length(); i++) next.put(previous.get(i));
            next.put(new JSONObject().put("pkg", pkg).put("kind", kind).put("detail", message).put("time", now));
            sp.edit().putString("events", next.toString()).commit();
        } catch (Exception ignored) {}
    }
}
