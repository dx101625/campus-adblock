package com.campus.adblock;

/** Dedicated SDK advertisement activities observed in the installed app; excludes all business pages. */
public final class AdActivityRules {
    private AdActivityRules(){}
    public static boolean isSdkActivity(String name){
        return name!=null&&(name.startsWith("com.bytedance.sdk.openadsdk.core.component.reward.activity.")
                || name.startsWith("com.bytedance.sdk.openadsdk.activity.")
                || name.startsWith("com.bytedance.sdk.openadsdk.stub.activity."));
    }
}
