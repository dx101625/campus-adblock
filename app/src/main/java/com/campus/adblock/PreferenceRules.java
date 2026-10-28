package com.campus.adblock;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Pure rules shared by runtime configuration and import validation. */
public final class PreferenceRules {
    private PreferenceRules() {}
    public static String mode(boolean scan, boolean advance) { return !scan ? "ads" : advance ? "preview" : "device"; }
    public static boolean validMode(String value) { return "preview".equals(value) || "device".equals(value) || "ads".equals(value); }
    public static String launchMode(String global, String profile, boolean shortcutOnly, boolean explicitEntry) {
        if (!explicitEntry && (shortcutOnly || "ads".equals(global))) return "ads";
        return validMode(profile) ? profile : global;
    }
    public static boolean changedQuote(long oldCents, double oldDuration, long newCents, double newDuration) {
        return oldCents != newCents || Math.abs(oldDuration - newDuration) > .000001;
    }
    public static boolean grantValid(long issued, long expires, long now) {
        return issued <= now && expires > now && expires - issued <= 300_000L && now - issued <= 300_000L;
    }
    /** Mutates only the matched one-use grant. Caller persists this under its synchronized store lock. */
    public static boolean consumeGrant(org.json.JSONObject grants,String source,String destination,long now) {
        String key=source+"/"+destination;org.json.JSONObject grant=grants.optJSONObject(key);
        boolean allowed=grant!=null&&grantValid(grant.optLong("issued"),grant.optLong("expires"),now)&&grant.optInt("remaining",0)!=0;
        if(allowed&&grant.optInt("remaining")==1)grants.remove(key);
        return allowed;
    }
    public static boolean verifiedQiekj(String version) { return "1.144.0".equals(version); }
    public static int paymentMethod(String key) {
        if ("unionpay".equals(key)) return 52;
        if ("alipay".equals(key)) return 13;
        if ("wechat".equals(key)) return 32;
        return -1;
    }
    public static boolean allowedPromotion(int type, boolean coupon, boolean points) {
        return (coupon && (type == 3 || type == 6 || type == 7)) || (points && type == 8);
    }
    public static boolean blockDestination(boolean enabled, boolean destinationMatches, boolean allowed,
                                           boolean strict, boolean advertisingCaller) {
        return enabled && destinationMatches && !allowed && (strict || advertisingCaller);
    }
    public static Set<String> packageSet(String raw) {
        Set<String> out = new HashSet<>();
        if (raw != null) for (String p : raw.split(",")) if (p.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+")) out.add(p);
        return out;
    }
}
