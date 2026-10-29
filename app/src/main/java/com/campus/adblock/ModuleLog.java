package com.campus.adblock;

import android.util.Log;

/** The existing g.log preference controls our logcat output. */
final class ModuleLog {
    private ModuleLog() {}
    static int i(String t, String m) { return Cfg.logEnabled ? Log.i(t,m) : 0; }
    static int w(String t, String m) { return Cfg.logEnabled ? Log.w(t,m) : 0; }
    static int w(String t, String m, Throwable e) { return Cfg.logEnabled ? Log.w(t,m,e) : 0; }
    static int e(String t, String m) { return Cfg.logEnabled ? Log.e(t,m) : 0; }
    static int e(String t, String m, Throwable e) { return Cfg.logEnabled ? Log.e(t,m,e) : 0; }
    static int d(String t, String m) { return Cfg.logEnabled ? Log.d(t,m) : 0; }
    static int v(String t, String m) { return Cfg.logEnabled ? Log.v(t,m) : 0; }
}
