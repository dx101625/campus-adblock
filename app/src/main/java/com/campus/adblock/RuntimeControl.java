package com.campus.adblock;

import android.content.Context;
import android.os.Bundle;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Bounded, authenticated local control. Does not launch activities or perform payments. */
public final class RuntimeControl {
    private RuntimeControl() {}
    public static String deviceKey(String qr) {
        if (qr==null || qr.isEmpty()) return "";
        try { byte[] b=MessageDigest.getInstance("SHA-256").digest(qr.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(java.util.Locale.ROOT,"%02x",x&255));return s.toString(); }
        catch(Exception e){return "";}
    }
    public static Bundle call(Context c,String method,Bundle extras) {
        try { return c.getContentResolver().call(StatusProvider.uri(),method,c.getPackageName(),extras); }
        catch(Throwable ignored){return null;}
    }
    public static boolean canScan(Context c,String qr) {
        if (!Cfg.pauseOnFailure) return true;
        Bundle b=new Bundle();b.putString("key",deviceKey(qr));Bundle out=call(c,"guard",b);
        return out==null || !out.getBoolean("paused",false);
    }
    public static void scanResult(Context c,boolean success) {
        Bundle b=new Bundle();b.putString("key",deviceKey(Cfg.resolveQr(c)));b.putBoolean("success",success);call(c,"scan_result",b);
    }
    public static boolean quote(Context c,int machine,double duration,double price) {
        if (!Cfg.priceGuard) return true;
        if (!Double.isFinite(duration) || !Double.isFinite(price) || duration<=0 || price<0 || price>1_000_000) return false;
        Bundle b=new Bundle();b.putString("key",deviceKey(Cfg.resolveQr(c)));b.putString("tier",Cfg.qiekjTier);b.putInt("machine",machine);
        b.putDouble("duration",duration);b.putLong("cents",Math.round(price*100));
        Bundle out=call(c,"quote",b);
        return out!=null && out.getBoolean("allowed",false);
    }
    public static boolean consumeGrant(Context c,String dest) {
        Bundle b=new Bundle();b.putString("destination",dest);Bundle out=call(c,"consume_grant",b);return out!=null && out.getBoolean("allowed",false);
    }
    public static void jumpBlocked(Context c,String dest) {
        if(c==null)return;Bundle b=new Bundle();b.putString("destination",dest);call(c,"jump",b);
    }
}
