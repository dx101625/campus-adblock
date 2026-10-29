package com.campus.adblock.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import java.util.HashMap;
import java.util.Map;

/** Platform-only helpers for the backward-compatible glass strip and status reader. */
public final class UiKit {
    public final Context context;
    private final Map<String, Integer> ids = new HashMap<>();
    public UiKit(Context value) { context = value; }
    public int id(String type, String name) {
        String key = type + "/" + name;
        Integer value = ids.get(key);
        if (value == null) {
            value = context.getResources().getIdentifier(name, type, context.getPackageName());
            if (value == 0) throw new IllegalArgumentException("Missing resource " + key);
            ids.put(key, value);
        }
        return value;
    }
    public int color(String name) { return context.getColor(id("color", name)); }
    public String text(String name) { return context.getString(id("string", name)); }
    public String format(String name, Object... values) { return context.getString(id("string", name), values); }
    public int dim(String name) { return context.getResources().getDimensionPixelSize(id("dimen", name)); }
    public int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    public int dp(int value) { return dp((float) value); }
    public boolean night() { return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES; }
    public boolean highContrast() { return Settings.Secure.getInt(context.getContentResolver(), "high_text_contrast_enabled", 0) == 1; }
    public boolean animations() { return com.campus.adblock.Cfg.uiBoolean(context,"ui.motion",true) && Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f; }
    public GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(radius); return drawable;
    }
    public GradientDrawable outlined(int color, int radius, int stroke) {
        GradientDrawable drawable = rounded(color, radius); drawable.setStroke(dp(1), stroke); return drawable;
    }
}
