package com.campus.adblock;

/** Installed application identity, independent of the Java/Kotlin source namespace. */
public final class ModuleIdentity {
    public static final String ID = BuildConfig.APPLICATION_ID;
    public static final String STATUS_AUTHORITY = ID + ".status";
    public static final String STATUS_ACTION = ID + ".STATUS";
    public static final String STATUS_RECEIVER = "com.campus.adblock.StatusReceiver";
    private ModuleIdentity() {}
}
