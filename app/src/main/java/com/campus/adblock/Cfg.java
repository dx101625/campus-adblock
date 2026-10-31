package com.campus.adblock;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * 模块配置：设置界面（本 App 进程）与 hook 端（目标 App 进程）共用同一份
 * {@code SharedPreferences} 文件名 {@link #GROUP}。
 *
 * <p>hook 端通过 libxposed 的 {@code getRemotePreferences(GROUP)} 读取模块 App 里
 * 写入的同名首选项文件，因此改配置只需在设置界面保存，然后让目标 App 重启生效，
 * 不需要重新打包模块。</p>
 *
 * <p>所有字段都是进程内的静态副本：每个进程启动时各读一次，读取失败就沿用默认值，
 * 保证即使设置界面从没打开过、或 remote prefs 不可用，模块行为也与历史版本一致。</p>
 */
public final class Cfg {

    public static final String GROUP = "campus_cfg";
    /** 目标 App 自己数据目录下的生效标记文件（root 读取用）。 */
    public static final String LOCAL_STATUS = "pg_status";

    /**
     * 目标 App 里「记住的二维码」用的首选项文件（沿用历史版本，扫一次就写这里）。
     */
    public static final String PREF_AUTO_QR = "pg_auto_qr";
    public static final String KEY_AUTO_QR = "qr";

    // ---- 胖乖生活 com.qiekj.user ----
    /** 去广告总开关（View / 弹窗 / 悬浮 / 摇一摇之外的静态广告位）。 */
    public static boolean qiekjAds = true;
    /** 开屏不再空等。 */
    public static boolean qiekjSplash = true;
    /** 打开 App 自动识别记住的设备码。 */
    public static boolean qiekjScan = true;
    /** 自动选机 + 自动按「立即使用」，停在订单预览。 */
    public static boolean qiekjAdvance = true;
    /** 机器：left1 / right2 / auto / none。 */
    public static String qiekjMachine = "right2";
    /** 档位：shortest / longest。 */
    public static String qiekjTier = "shortest";
    /** 支付方式：unionpay / alipay / wechat / none。 */
    public static String qiekjPay = "unionpay";
    /** 订单预览页是否自动勾选优惠券。 */
    public static boolean qiekjCoupon = false;
    /** 订单预览页是否自动使用积分。 */
    public static boolean qiekjPoints = false;
    /** 设置界面里手填的二维码，非空时优先于「扫过的码」。 */
    public static String qiekjQr = "";
    /** Effective per-session mode; ads-only never changes native device or payment choices. */
    public static boolean qiekjSelections = true;
    public static String automationMode = "preview";
    public static String profileId = "";
    public static boolean priceGuard = true;
    public static boolean pauseOnFailure = true;

    // ---- 智趣校园 com.klcxkj.zqxy ----
    public static boolean zqxyAds = true;
    public static boolean zqxyTab = true;

    // ---- 全局 ----
    /** 拦「摇一摇 / 扭一扭」开屏。 */
    public static boolean blockShake = true;
    /** 拦广告误触跳转（component / package / scheme）。 */
    public static boolean blockJump = true;
    public static String jumpMode = "standard";
    public static String jumpAllowed = "";
    /** 模块自身日志。 */
    public static boolean logEnabled = true;

    private static volatile boolean sLoaded;

    private Cfg() {
    }

    public static boolean loaded() {
        return sLoaded;
    }

    // UI-only accessors. Existing hook configuration loading and defaults stay unchanged.
    public static boolean uiBoolean(Context context, String key, boolean fallback) {
        return context.getSharedPreferences(GROUP, Context.MODE_PRIVATE).getBoolean(key, fallback);
    }

    public static String uiString(Context context, String key, String fallback) {
        return context.getSharedPreferences(GROUP, Context.MODE_PRIVATE).getString(key, fallback);
    }

    public static void uiPutBoolean(Context context, String key, boolean value) {
        context.getSharedPreferences(GROUP, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply();
    }

    public static void uiPutString(Context context, String key, String value) {
        context.getSharedPreferences(GROUP, Context.MODE_PRIVATE).edit().putString(key, value).apply();
    }

    /** 读一次配置。sp 为 null（模块未启用 / 老版本框架）时全部保持默认值。 */
    public static synchronized void load(SharedPreferences sp) {
        if (sp == null) {
            ModuleLog.w("CampusAdBlock", "remote prefs unavailable, using defaults");
            return;
        }
        try {
            qiekjAds = sp.getBoolean("q.ads", qiekjAds);
            qiekjSplash = sp.getBoolean("q.splash", qiekjSplash);
            qiekjScan = sp.getBoolean("q.scan", qiekjScan);
            qiekjAdvance = sp.getBoolean("q.advance", qiekjAdvance);
            qiekjMachine = sp.getString("q.machine", qiekjMachine);
            qiekjTier = sp.getString("q.tier", qiekjTier);
            qiekjPay = sp.getString("q.pay", qiekjPay);
            qiekjCoupon = sp.getBoolean("q.coupon", qiekjCoupon);
            qiekjPoints = sp.getBoolean("q.points", qiekjPoints);
            qiekjQr = sp.getString("q.qr", qiekjQr);

            zqxyAds = sp.getBoolean("z.ads", zqxyAds);
            zqxyTab = sp.getBoolean("z.tab", zqxyTab);

            blockShake = sp.getBoolean("g.shake", blockShake);
            blockJump = sp.getBoolean("g.jump", blockJump);
            jumpMode = sp.getString("g.jump.mode", "standard");
            jumpAllowed = sp.getString("g.jump.allowed", "");
            logEnabled = sp.getBoolean("g.log", logEnabled);
            String requested=sp.getString("q.mode","");
            automationMode=PreferenceRules.validMode(requested)?requested:PreferenceRules.mode(qiekjScan,qiekjAdvance);
            automationMode="ads".equals(automationMode) ? "ads" : "preview";
            qiekjSelections=!"ads".equals(automationMode);qiekjScan=qiekjSelections;qiekjAdvance="preview".equals(automationMode);
            priceGuard=false;pauseOnFailure=false;
            sLoaded = true;
        } catch (Throwable t) {
            ModuleLog.w("CampusAdBlock", "load config failed", t);
        }
    }

    /** The authenticated provider reads exactly the same file as the settings UI. */
    static synchronized void loadValues(android.os.Bundle values) {
        qiekjAds = values.getBoolean("q.ads", qiekjAds);
        qiekjSplash = values.getBoolean("q.splash", qiekjSplash);
        qiekjScan = values.getBoolean("q.scan", qiekjScan);
        qiekjAdvance = values.getBoolean("q.advance", qiekjAdvance);
        qiekjCoupon = values.getBoolean("q.coupon", qiekjCoupon);
        qiekjPoints = values.getBoolean("q.points", qiekjPoints);
        qiekjMachine = values.getString("q.machine", qiekjMachine);
        qiekjTier = values.getString("q.tier", qiekjTier);
        qiekjPay = values.getString("q.pay", qiekjPay);
        qiekjQr = values.getString("q.qr", qiekjQr);
        zqxyAds = values.getBoolean("z.ads", zqxyAds);
        zqxyTab = values.getBoolean("z.tab", zqxyTab);
        blockShake = values.getBoolean("g.shake", blockShake);
        blockJump = values.getBoolean("g.jump", blockJump);
        jumpMode = values.getString("g.jump.mode", "standard");
        jumpAllowed = values.getString("g.jump.allowed", "");
        logEnabled = values.getBoolean("g.log", logEnabled);
        automationMode = values.getString("runtime.mode", PreferenceRules.mode(qiekjScan, qiekjAdvance));
        qiekjSelections = values.getBoolean("runtime.selections", !"ads".equals(automationMode));
        profileId = values.getString("runtime.profile", "");
        priceGuard = false;
        pauseOnFailure = false;
        sLoaded = true;
    }

    /**
     * 目标 App 该用哪个二维码：设置界面填了的优先，否则用历史版本记住的那个。
     * 两者都为空时返回 null，调用方按「还没记住」处理。
     */
    public static String resolveQr(Context targetCtx) {
        if (qiekjQr != null && !qiekjQr.trim().isEmpty()) {
            return qiekjQr.trim();
        }
        if (targetCtx == null) {
            return null;
        }
        String remembered = targetCtx.getSharedPreferences(PREF_AUTO_QR, Context.MODE_PRIVATE)
                .getString(KEY_AUTO_QR, null);
        return remembered == null || remembered.isEmpty() ? null : remembered;
    }

    /**
     * hook 装好后给设置界面留个心跳：哪个 App、什么时候生效的。
     *
     * <p>走 {@link StatusProvider}（跨进程一次 {@code call}），而不是写 remote prefs ——
     * 后者的写入在部分框架实现里会被丢掉，界面就会一直显示「未生效」。
     * 调用方必须在**子线程**里调，别阻塞目标 App 的启动路径。</p>
     */
    public static boolean beat(Context ctx, String pkg, String appVer) {
        if (ctx == null || pkg == null || pkg.isEmpty()) {
            return false;
        }
        try {
            android.os.Bundle extras = new android.os.Bundle();
            extras.putString("ver", appVer == null ? "" : appVer);
            android.os.Bundle r = ctx.getContentResolver()
                    .call(StatusProvider.uri(), "beat", pkg, extras);
            if (r != null && r.getBoolean("ok", false)) {
                ModuleLog.i("CampusAdBlock", "heartbeat ok: " + pkg + " " + appVer);
                return true;
            }
            ModuleLog.w("CampusAdBlock", "heartbeat: provider 没有回应，改用广播");
        } catch (Throwable t) {
            ModuleLog.w("CampusAdBlock", "heartbeat: provider 不可用 -> " + t);
        }
        return broadcast(ctx, pkg, appVer);
    }


    /**
     * 把生效标记写进目标 App 自己的 prefs。设置界面在有 root 的机器上用 su 读这个文件，
     * 不依赖跨进程通道（这台机器没有 LSPosed 管理器，remote prefs 与 provider 都不可用）。
     */
    private static void markLocal(Context ctx, String pkg, String appVer) {
        try {
            ctx.getSharedPreferences(LOCAL_STATUS, Context.MODE_PRIVATE).edit()
                    .putString("pkg", pkg)
                    .putString("ver", appVer == null ? "" : appVer)
                    .putLong("ts", System.currentTimeMillis())
                    .commit();
        } catch (Throwable ignore) {
        }
    }

    /** 显式广播：按组件投递，不受包可见性限制。 */
    private static boolean broadcast(Context ctx, String pkg, String appVer) {
        try {
            Intent intent = new Intent(StatusReceiver.ACTION);
            intent.setClassName("com.campus.adblock", "com.campus.adblock.StatusReceiver");
            intent.putExtra("pkg", pkg);
            intent.putExtra("ver", appVer == null ? "" : appVer);
            ctx.sendBroadcast(intent);
            ModuleLog.i("CampusAdBlock", "heartbeat: broadcast sent for " + pkg + " " + appVer);
            markLocal(ctx, pkg, appVer);
            return true;
        } catch (Throwable t) {
            ModuleLog.w("CampusAdBlock", "heartbeat failed: " + t);
            return false;
        }
    }

    /** 旧入口：写 remote prefs，仅作 provider 不可用时的兜底。 */
    public static void beat(SharedPreferences sp, String pkg, String appVer) {
        if (sp == null) {
            return;
        }
        try {
            sp.edit()
                    .putString("st.pkg", pkg)
                    .putString("st.ver", appVer == null ? "" : appVer)
                    .putLong("st.ts", System.currentTimeMillis())
                    .apply();
        } catch (Throwable ignore) {
            // 心跳只是 UI 展示用，失败不影响功能
        }
    }
}
