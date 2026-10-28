package com.campus.adblock;

import android.content.Context;
import android.os.Bundle;

/** Read the module's existing local preferences after the target obtains a Context. */
final class ConfigBridge {
    private ConfigBridge() {}
    static boolean load(Context context) {
        if (context == null) return false;
        try {
            Bundle result = context.getContentResolver().call(StatusProvider.uri(), "config", context.getPackageName(), null);
            if (result != null && result.getBoolean("ok", false) && result.getBundle("values") != null) {
                Cfg.loadValues(result.getBundle("values"));
                ModuleLog.i("CampusAdBlock", "local configuration synchronized: scan=" + Cfg.qiekjScan
                        + " advance=" + Cfg.qiekjAdvance + " pay=" + Cfg.qiekjPay + " jump=" + Cfg.jumpMode + " mode=" + Cfg.automationMode);
                return true;
            }
        } catch (Throwable error) { ModuleLog.w("CampusAdBlock", "local configuration bridge unavailable", error); }
        return false;
    }
    static String resume(Context context,boolean preserveSession) {
        Bundle result=RuntimeControl.call(context,"session",null);
        if(result!=null&&result.getBoolean("ok")&&result.getBundle("values")!=null){
            String token=result.getString("launch.token","");
            if(!preserveSession||!token.isEmpty())Cfg.loadValues(result.getBundle("values"));return token;
        }
        return "";
    }
}
