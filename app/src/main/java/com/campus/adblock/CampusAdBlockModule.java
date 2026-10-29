package com.campus.adblock;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewStub;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * 胖乖生活去广告，libxposed API 102。
 * 291 版首页的 banner_view 是原生业务功能栏，不能作为广告隐藏。
 * 首页采用 App 自身无商城广告分支的布局，保留业务入口和通知。
 * H5 仅在积分首页清理明确营销类名，不按文字、面积或兄弟关系删除内容。
 * SDK 开屏、明确广告资源位、广告来源摇动和外跳分别在对应入口拦截。
 * 不修改目标 APK 或账户；不提交订单、不付款、不启动设备。
 */
public class CampusAdBlockModule extends XposedModule {

    private static final String TAG = "CampusAdBlock";
    /** 每个目标进程只报一次心跳，避免每次 resume 都跨进程调用。 */
    private static final java.util.concurrent.atomic.AtomicBoolean sBeat =
            new java.util.concurrent.atomic.AtomicBoolean();
    /** 公开版版本号，只用于启动日志（`module.prop` 里还有一份，发版时一起改）。 */
    static final String BUILD_NAME = "1.3.0";
    static final String TARGET_PACKAGE = "com.qiekj.user";
    /** 智趣校园，第二套规则（见 {@link ZqxyHooks}）。 */
    static final String TARGET_ZQXY = "com.klcxkj.zqxy";

    /** 热启动开屏广告 Activity（GroMore 自渲染热开屏），精确匹配立即 finish。 */
    private static final String CLS_HOT_SPLASH = "com.qiekj.user.ad.gromore.HotSplashAct";
    /** 广告 Activity finish 后若处于任务栈底，兜底跳转的主页。 */
    private static final String CLS_MAIN = "com.qiekj.user.MainActivity";
    /** 提交订单页：微博优惠默认不勾选 + 默认银联支付的行为修正目标。 */
    private static final String CLS_ORDER_PREVIEW = "com.qiekj.user.ui.activity.scan.OrderPayPreviewAct";

    /** 纯广告 Activity 前缀：onCreate 后延迟 finish。 */
    private static final String[] AD_ACT_PREFIXES = {
            "com.byazt.mw.Stub_",
            "com.bytedance.sdk.openadsdk.stub.activity.",
            "com.jutui.ads.core.ui.",
            "com.yd.saas.s2s.sdk.ad.",
            "com.yd.saas.activity.Ptg",
            "com.yd.saas.blk.base.activity.",
            "com.yd.saas.h5.",
            "com.yd.saas.base.inner.",
            "com.qiekeji.pgad.activity.",
            "com.qq.e.ads.",
            "com.kwad.sdk.api.proxy.app.FeedDownloadActivity",
            "com.anythink.core.common.inner.ui.activity.",
            "com.miui.zeus.mimo.sdk.ad.",
            "com.octopus.ad.",
            "com.meishu.sdk.activity.",
            "com.ubix.ssp.open.comm.AdActivity",
            "com.sigmob.sdk.base.common.",
            "com.wangmai.insightvision.openadsdk.",
            "com.leyou.fusionsdk.activity.",
            "cj.mobile.fw.activity.",
            "com.fl.saas.adx.base.activity.",
    };

    private void moduleLog(int level, String tag, String message) {
        if (Cfg.logEnabled) super.log(level, tag, message);
    }
    private void moduleLog(int level, String tag, String message, Throwable error) {
        if (Cfg.logEnabled) super.log(level, tag, message, error);
    }

    private volatile boolean mHooked;

    /**
     * 当前前台 Activity。首页那个匿名 WebView 的 Context 往往是
     * {@code MutableContextWrapper}／Application 而不是 Activity 本身，
     * 只靠 {@code wv.getContext() instanceof Activity} 判断会直接把整条 H5 处理链掐断
     * （v1.3/v1.4 的坑），所以额外记录一个"由 onResume 观测到的当前 Activity"作为兜底。
     */
    private static volatile Activity sCurAct;
    public static Activity currentActivity() { return sCurAct; }

    // ---------------------------------------------------------------- 生命周期

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        sSelf = this;
    }

    private volatile boolean mConfigured;

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        final String pkg = param.getPackageName();
        if (!TARGET_PACKAGE.equals(pkg) && !TARGET_ZQXY.equals(pkg)) return;
        try { Cfg.load(getRemotePreferences(Cfg.GROUP)); }
        catch (Throwable ignored) { }
        final ClassLoader loader = param.getClassLoader();
        Context context = null;
        try {
            Class<?> thread = Class.forName("android.app.ActivityThread");
            context = (Context) thread.getDeclaredMethod("currentApplication").invoke(null);
        } catch (Throwable ignored) { }
        if (context != null && pkg.equals(context.getPackageName())) {
            configureTarget(pkg, loader, context);
            return;
        }
        try {
            Method attach = android.app.Application.class.getDeclaredMethod("attach", Context.class);
            hook(attach).setId("campus:configuration:attach").intercept(chain -> {
                Object result = chain.proceed();
                Context base = (Context) chain.getArg(0);
                if (base != null && pkg.equals(base.getPackageName())) configureTarget(pkg, loader, base);
                return result;
            });
        } catch (Throwable error) {
            moduleLog(Log.WARN, TAG, "configuration attach hook unavailable; using framework configuration", error);
            configureTarget(pkg, loader, null);
        }
    }

    private synchronized void configureTarget(String pkg, ClassLoader loader, Context context) {
        if (mConfigured) return;
        mConfigured = true;
        HealthReporter.begin(context);
        boolean local = ConfigBridge.load(context);
        HealthReporter.configuration(local);
        if (!local) RuntimeEvents.note(context, "configuration", "设置同步不可用，当前使用框架配置或默认值");
        moduleLog(Log.INFO, TAG, "v" + BUILD_NAME + " package ready, process=" + pkg + " localConfig=" + local);
        try {
            if (TARGET_ZQXY.equals(pkg)) ZqxyHooks.install(this, loader);
            else installHooks(loader);
        } catch (Throwable error) { moduleLog(Log.ERROR, TAG, "installHooks failed", error); }
        finally { HealthReporter.finish(); }
    }

    /** 目标 App 的 targetSdk，只用于设置界面的「已生效」提示。 */
    private static String versionOf(XposedModuleInterface.PackageReadyParam param) {
        try {
            android.content.pm.ApplicationInfo ai = param.getApplicationInfo();
            return ai == null ? "" : ("targetSdk " + ai.targetSdkVersion);
        } catch (Throwable t) {
            return "";
        }
    }

    @Override
    public boolean onHotReloading(XposedModuleInterface.HotReloadingParam param) {
        mHooked = false;
        mConfigured = false;
        ZqxyHooks.reset();
        moduleLog(Log.INFO, TAG, "hot reloading, hooks will be re-installed");
        return true;
    }

    // ---------------------------------------------------------------- 安装 hook

    private void installHooks(ClassLoader cl) {
        if (mHooked) {
            moduleLog(Log.INFO, TAG, "hooks already installed, skip");
            return;
        }
        mHooked = true;
        moduleLog(Log.INFO, TAG, "installHooks begin, cl=" + cl);

        step("Instrumentation act lifecycle", this::hookInstrumentationLifecycle);
        if (Cfg.qiekjAds) {
            step("View.setVisibility", this::hookViewVisibility);
            step("Activity.onCreate", this::hookActivityCreate);
            step("Activity.onResume", this::hookActivityResume);
            // v1.7 新增：Activity 生命周期改由 Instrumentation 层驱动。
            // 直接 hook Activity.onResume/onCreate 只在子类不 override 或调用了 super 时才触发；
            // 而 Instrumentation.callActivityOnResume/callActivityOnCreate 是 framework 主动调用的，
            // 无论子类怎么写都必经此地。这是 v1.3~v1.6 首页扫树/注入一直没跑的疑似主因。
            // v1.7 新增：View 上屏即扫树兜底。任何 View 附着到窗口都会走 onAttachedToWindow，
            // 不依赖 Activity 生命周期，H5 容器 inflate 出来的那一刻就能被扫到。
            step("View.onAttachedToWindow", this::hookViewAttach);
            step("ViewStub.inflate", this::hookViewStubInflate);
            step("Dialog.show", this::hookDialogShow);
            step("PopupWindow.show*", this::hookPopupWindow);
            // v1.6 新增：WebView 层 hook。v1.3~v1.5 只靠 onResume 后整树扫描找 WebView，
            // 而首页 H5 容器往往是 onResume 之后才 inflate 出来的，扫描那几趟树里根本没有
            // WebView 实例 → handleWebView 一次都没跑过 → h5 inject/JS 全部不执行，
            // 粉色错误页自然清不掉。这里改为在 WebView 创建/加载 URL 时就接住它。
            step("Native home 291", () -> hookNativeHome(cl));
            step("Passive interstitials", () -> hookPassiveInterstitials(cl));
            step("WebView.<init>", this::hookWebViewCreate);
            step("WebView.loadUrl", this::hookWebViewLoad);
            step("WebViewClient.onPageFinished", this::hookWebViewClient);
            step("SDK ATSplashAd", () -> hookAnyThinkSplash(cl));
            step("SDK TTAdNative", () -> hookTTAdNative(cl));
            step("SDK SplashAD", () -> hookGdtSplash(cl));
        } else {
            moduleLog(Log.INFO, TAG, "去广告已关闭（设置界面）");
        }
        if (Cfg.blockShake) {
            step("SensorManager.registerListener", this::hookSensorManager);
        }
        if (Cfg.blockJump || Cfg.qiekjAds) {
            step("Instrumentation.execStartActivity", this::hookInstrumentation);
        }
        // 选机/选档/支付方式是「下单流程」而不是去广告，独立于广告开关存在。
        step("Device default selections", () -> DeviceDefaults.install(this, cl));
        step("Order default selections", () -> OrderDefaults.install(this, cl));
        if (Cfg.qiekjSplash) {
            // v3.4 新增：开屏对话框不再等 6 秒（广告被拦后它的关闭回调永远等不到）。
            step("Splash dialog no-wait", () -> hookSplashDialog(cl));
        }
        {
            // v3.5 新增：记住一次二维码，之后打开 App 自动识别、直达设备页。
            step("Auto scan", () -> hookAutoScan(cl));
        }
        {
            step("Auto advance", () -> hookAutoAdvance(cl));
        }

        moduleLog(Log.INFO, TAG, "installHooks done");
    }

    private interface Step {
        void run() throws Throwable;
    }

    private void step(String name, Step s) {
        try {
            s.run();
            HealthReporter.step(name,true);
            moduleLog(Log.INFO, TAG, "[ok] " + name);
        } catch (Throwable t) {
            HealthReporter.step(name,false);
            moduleLog(Log.WARN, TAG, "[skip] " + name + " -> " + t);
        }
    }

    // ------------------------------------------------------------ 1. 广告 View 隐藏

    private void hookViewVisibility() throws Throwable {
        Method m = View.class.getDeclaredMethod("setVisibility", int.class);
        hook(m).setId("pg:setVisibility").intercept(chain -> {
            Object self = chain.getThisObject();
            if (!(self instanceof View)) {
                return chain.proceed();
            }
            Object visArg = chain.getArg(0);
            if (!(visArg instanceof Integer)) {
                return chain.proceed();
            }
            int vis = (Integer) visArg;
            View v = (View) self;
            if (sHomeServiceViews.contains(v)) {
                return chain.proceed(new Object[]{View.VISIBLE});
            }
            if (vis == View.GONE) {
                return chain.proceed();
            }
            // 已被判定为广告的 WebView：SDK 之后想再显示回来，一律压回 GONE
            if (v instanceof WebView && sAdWv.containsKey(v)) {
                return chain.proceed(new Object[]{View.GONE});
            }
            String n = entryNameOf(v);
            if (n != null && PARENT_HIDE.contains(n)) {
                scheduleHideParent(v);
            }
            if (isAdView(v)) {
                return chain.proceed(new Object[]{View.GONE});
            }
            return chain.proceed();
        });
    }

    /** 资源 id -> 资源条目名 的缓存，空串代表“查不到”。 */
    private static final ConcurrentHashMap<Integer, String> sIdNameCache = new ConcurrentHashMap<>();

    private static String entryNameOf(View v) {
        int id = v.getId();
        if (id == View.NO_ID) {
            return null;
        }
        String cached = sIdNameCache.get(id);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        String name = null;
        try {
            name = v.getResources().getResourceEntryName(id);
        } catch (Throwable ignore) {
            // 系统/三方库 id 在本包资源表里查不到，属正常情况
        }
        sIdNameCache.put(id, name == null ? "" : name);
        return name;
    }

    /** 只打一次日志，避免 setVisibility 高频刷屏。 */
    private static final Set<String> sLoggedNames = ConcurrentHashMap.newKeySet();

    private static boolean isAdView(View v) {
        String n = entryNameOf(v);
        if (n == null) {
            return false;
        }
        boolean ad = isAdName(n);
        if (ad && sLoggedNames.add(n)) {
            ModuleLog.i(TAG, "ad view hidden: " + n);
        }
        return ad;
    }

    // ---- 资源名规则（全部小写比较）----

    private static final Set<String> EXCLUDE = new HashSet<>(Arrays.asList(
            "privacy_adv_recommendation_tv",
            "tvPromotionName".toLowerCase(Locale.ROOT)
    ));

    /** 业务广告位 / 营销位精确名。 */
    private static final Set<String> EXACT = new HashSet<>(Arrays.asList(
            // 首页 v3（banner_head=顶部原生 Banner 轮播槽，如"1.9元抢5斤"梨子横幅）
            "banner_head", "local_life_ad", "fl_ad_container",
            "include_rebate_guide", "rl_turntable_ad", "rl_float_ad", "vs_newcomer_float",
            "cl_upglide_tips", "lavUp", "tv_upglide_give", "lottie_up", "iv_up_top",
            // 首页旧版 / v1 / v2
            "bannerAd", "bannerFence", "banner_carousel",
            "clAdFloat", "ivFloatImg", "floatCancel",
            "cv_browse_container", "tv_browse_content", "tv_browse_integral",
            "cl_penguin_welfare", "tv_penguin_welfare", "welfareAssistantView", "welfare_assistant",
            "summer_cool",
            "fl_native_flow", "school_wall_hot",
            // 首页运营横幅（粉色“页面太火爆啦”）
            "root_banner", "vp_banner", "iv_banner",
            // 本地生活营销榜单
            "view_food_ranking", "view_play_ranking", "banner_food_ranking",
            "banner_play_ranking", "cl_food", "cl_play",
            // 提交订单页营销块
            "cashier_adx_task", "iv_package_ad",
            "pocket_buy", "cl_pocket_buy", "rv_pocket_goods", "tv_pocket_title",
            // 其它
            "fcvPromotionContainer"
    ));

    /** 广告 SDK 布局/业务前缀。 */
    private static final String[] PREFIX = {
            "ad_", "anythink_", "sdm_myoffer_", "ksad_", "hiad_", "mimo_",
            "junion_", "qiming_", "tianmu_", "vlion_", "yd_saas_", "fanti_ad_",
            "fw_splash", "fw_banner", "fw_native", "fw_feed",
            "qy_layout_splash", "qy_feed", "qy_im_ad", "jad_",
            "ttt_listitem_ad", "ttt_activity_ad",
            "ly_gdt", "ly_view_gdt", "ly_sup_splash", "ly_hailiang",
            "sig_ad_", "oct_native", "oct_layout_banner",
            "ms_ad_", "ms_banner", "ms_draw_ad", "ms_layout_native_splash", "ms_splash_ad", "ms_gdt",
            "yf_splash", "yf_feed",
            "saas_banner", "saas_native", "saas_splash", "saas_feed",
            "adx_banner", "adx_interstitial", "adx_splash",
            "banner_native_item", "inter_native_item",
            "item_ad_", "item_mix_native", "item_native", "item_banner",
            "feed_ad_", "view_hot_splash", "view_native_inter", "view_splash",
            "bh_layout_", "bh_activity_ad", "fl_adx_", "jpush_banner",
            "activity_sdk_splash", "custom_ad_bg_layout", "exo_ad_overlay",
            "layout_float_image_ad", "layout_fake_series_ad",
            "popup_interstitial_ad", "popup_banner",
            "channel_banner", "channel_native",
    };

    /** 中缀/关键词。 */
    private static final String[] CONTAINS = {
            "_ad_", "_adv", "advert", "adview", "gromore", "pangle",
            "float_ad", "ad_float",
            "splash_ad", "ad_splash", "native_ad", "ad_native",
            "banner_ad", "ad_banner", "food_ranking", "play_ranking",
            "hot_splash", "hotsplash",
    };

    private static final String[] SUFFIX = {"_ad", "_adv", "_adview", "_advert"};

    /** 命中后连带隐藏直接父容器（父容器本身无广告 ID 的情况）。 */
    private static final Set<String> PARENT_HIDE = new HashSet<>(Arrays.asList(
            "tvMarketing", "go_make_money", "banner_food_ranking", "banner_play_ranking"
    ));

    /** 营销弹窗特征 ID：扫描命中后把整个弹窗根隐藏。 */
    private static final Set<String> POPUP_MARKERS = new HashSet<>(Arrays.asList(
            "go_make_money", "tv_waive", "tv_award", "btn_xianyu_reward_coupon_popup"
    ));

    private static boolean isAdName(String raw) {
        String n = raw.toLowerCase(Locale.ROOT);
        if (EXCLUDE.contains(n)) {
            return false;
        }
        if (EXACT.contains(raw) || EXACT.contains(n)) {
            return true;
        }
        for (String p : PREFIX) {
            if (n.startsWith(p)) {
                return true;
            }
        }
        for (String s : SUFFIX) {
            if (n.endsWith(s)) {
                return true;
            }
        }
        for (String c : CONTAINS) {
            if (n.contains(c)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------- 2. 整树兜底扫描

    private void hookActivityResume() throws Throwable {
        Method m = Activity.class.getDeclaredMethod("onResume");
        hook(m).setId("pg:onResume").intercept(chain -> {
            Object ret = chain.proceed();
            Object self = chain.getThisObject();
            if (self instanceof Activity) {
                onActResumed((Activity) self);
            }
            return ret;
        });
    }

    /** 首次进前台时跨进程报一次心跳，给设置界面显示「已生效」。子线程做，不阻塞启动。 */
    /** 模块实例：remote prefs 兜底要用，onModuleLoaded 时赋值。 */
    private static volatile CampusAdBlockModule sSelf;

    static SharedPreferences remotePrefsStatic() {
        CampusAdBlockModule self = sSelf;
        if (self == null) {
            return null;
        }
        try {
            return self.getRemotePreferences(Cfg.GROUP);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 供其它受管应用（智趣校园）复用的心跳上报：线程里做，失败不重试堆积。 */
    static void beatAsync(android.content.Context ctx, String pkg) {
        if (ctx == null || pkg == null || sBeat.get()) return;
        try {
            final android.content.Context app =
                    ctx.getApplicationContext() == null ? ctx : ctx.getApplicationContext();
            String ver = "";
            try {
                ver = app.getPackageManager().getPackageInfo(pkg, 0).versionName;
            } catch (Throwable ignore) {
            }
            final String fv = "v" + ver;
            final SharedPreferences remote = remotePrefsStatic();
            Thread t = new Thread(() -> {
                if (Cfg.beat(app, pkg, fv)) {
                    sBeat.set(true);
                } else if (remote != null) {
                    Cfg.beat(remote, pkg, fv);
                }
            }, "campus-beat");
            t.setDaemon(true);
            t.start();
        } catch (Throwable ignore) {
        }
    }

    private void beatOnce(Activity a) {
        if (sBeat.get()) {
            return;
        }
        try {
            final android.content.Context app = a.getApplicationContext();
            final String pkg = a.getPackageName();
            String ver = "";
            try {
                ver = app.getPackageManager().getPackageInfo(pkg, 0).versionName;
            } catch (Throwable ignore) {
            }
            final String fv = "v" + ver;
            final SharedPreferences remote = remotePrefsOrNull();
            Thread t = new Thread(() -> {
                if (Cfg.beat(app, pkg, fv)) {
                    sBeat.set(true);
                } else if (remote != null) {
                    Cfg.beat(remote, pkg, fv);
                }
            }, "campus-beat");
            t.setDaemon(true);
            t.start();
        } catch (Throwable ignore) {
        }
    }

    /** 拿 remote prefs，失败返回 null（老框架 / provider 不可用时的兜底路径用）。 */
    private SharedPreferences remotePrefsOrNull() {
        try {
            return getRemotePreferences(Cfg.GROUP);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Activity 进入前台后的统一处理：由 Activity.onResume 与 Instrumentation 两条路共同驱动。 */
    private void onActResumed(Activity a) {
        try {
            beatOnce(a);
            final String cn = a.getClass().getName();
            sCurAct = a;
            logAct("resumed", a);
            // v3.5：首页第一次进前台时自动扫码；扫码页进前台时把码直接交给它的 ViewModel。
            maybeAutoScan(a);

            // v3.6：自动扫码后，设备页选好 2 号机+最低档再替用户按一次「立即使用」，停在订单预览页。
            advanceDevicePage(a);
            noteOrderPreview(a);
            if (!Cfg.qiekjAds) return;
            // 首页 H5 容器是 onResume 之后才 inflate 的，扫树必须铺开一段时间，
            // 只在 0/400/1000ms 扫三趟会正好错过（v1.6 之前的问题）。
            scanLater(a, 0L);
            scanLater(a, 400L);
            scanLater(a, 1000L);
            scanLater(a, 2000L);
            scanLater(a, 3500L);
            scanLater(a, 5000L);
            scanLater(a, 7000L);
            scanLater(a, 9000L);

            if (!isAdAct(cn)) {
                // 视图树诊断 dump：不再只认 MainActivity（首页真实类名可能不是它），
                // 任何非广告 Activity 都 dump 一次，前 3 个各出一份，供定位漏网广告位。
                dumpLater(a, 2500L);
            }
        } catch (Throwable ignore) {
        }
    }

    private static final Set<String> sLoggedActs = ConcurrentHashMap.newKeySet();

    private void logAct(String what, Activity a) {
        try {
            String cn = a.getClass().getName();
            if (sLoggedActs.size() < 60 && sLoggedActs.add(what + "#" + cn)) {
                moduleLog(Log.INFO, TAG, "act " + what + ": " + cn);
            }
        } catch (Throwable ignore) {
        }
    }

    /** 是否纯广告 Activity（开屏/插屏/激励）。 */
    private static boolean isAdAct(String cn) {
        if (cn == null) {
            return false;
        }
        if (AdActivityRules.isSdkActivity(cn)) return true;
        if (CLS_HOT_SPLASH.equals(cn)) {
            return true;
        }
        for (String p : AD_ACT_PREFIXES) {
            if (cn.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    /** 是否首页（决定能否做过激动作：reload / 整块隐藏 WebView）。 */
    private static boolean isHomeAct(Activity a) {
        if (a == null) {
            return false;
        }
        String cn = a.getClass().getName();
        if (isAdAct(cn)) {
            return false;
        }
        return CLS_MAIN.equals(cn) || cn.endsWith("MainActivity") || cn.endsWith("HomeActivity");
    }

    private void scanLater(Activity a, long delay) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (a.isFinishing() || a.isDestroyed()) {
                    return;
                }
                View root = a.getWindow() == null ? null : a.getWindow().getDecorView();
                if (root != null) {
                    scanTree(root);
                }
            } catch (Throwable ignore) {
            }
        }, delay);
    }

    private void scanTree(View v) {
        scanTree(v, null);
    }

    private void scanTree(View v, View popupRoot) {
        if (v == null) {
            return;
        }
        try {
            String n = entryNameOf(v);
            if (n != null) {
                if (PARENT_HIDE.contains(n)) {
                    hideParentNow(v);
                }
                if (popupRoot != null && POPUP_MARKERS.contains(n) && v.getVisibility() != View.GONE) {
                    hideQuietly(popupRoot);
                }
            }
            if (v.getVisibility() != View.GONE && isAdView(v)) {
                v.setVisibility(View.GONE);
            }
            if (v instanceof WebView) {
                handleWebView(v);
            }
        } catch (Throwable ignore) {
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            int n = g.getChildCount();
            for (int i = 0; i < n; i++) {
                try {
                    scanTree(g.getChildAt(i), popupRoot);
                } catch (Throwable ignore) {
                }
            }
        }
    }

    // ------------------------------------------------------- 2.5 WebView 广告与诊断 dump

    private static final Set<String> sLoggedUrls = ConcurrentHashMap.newKeySet();

    // ------------------------------------------------ 2.45 WebView 层 hook（v1.6 新增）

    /**
     * WebView 事件日志去重集合。首页 H5 只加载一次，日志量很小；上限只是防异常循环。
     */
    private static final Set<String> sLoggedWv = ConcurrentHashMap.newKeySet();

    private static String ctxName(View v) {
        try {
            Context c = v.getContext();
            return c == null ? "null" : c.getClass().getName();
        } catch (Throwable ignore) {
            return "?";
        }
    }

    private static String actName(View v) {
        try {
            Activity a = actOf(v);
            return a == null ? "null" : a.getClass().getName();
        } catch (Throwable ignore) {
            return "?";
        }
    }

    private void noteWv(String kind, String detail, Object self) {
        try {
            if (!(self instanceof WebView)) {
                return;
            }
            WebView wv = (WebView) self;
            String key = kind + "#" + detail;
            if (sLoggedWv.size() < 80 && sLoggedWv.add(key)) {
                ModuleLog.i(TAG, "wv " + kind + ": " + detail
                        + " act=" + actName(wv) + " ctx=" + ctxName(wv)
                        + " size=" + wv.getWidth() + "x" + wv.getHeight());
            }
        } catch (Throwable ignore) {
        }
    }

    private static String head(String s, int n) {
        if (s == null) {
            return "null";
        }
        String t = s.replace('\n', ' ').replace('\r', ' ');
        return t.length() <= n ? t : t.substring(0, n) + "…";
    }

    /** hook WebView 构造器：只做诊断，回答"首页到底有没有 WebView、它是拿什么 Context 建的"。 */
    private void hookWebViewCreate() throws Throwable {
        final Class<?>[][] sigs = new Class<?>[][]{
                new Class<?>[]{Context.class},
                new Class<?>[]{Context.class, android.util.AttributeSet.class},
                // 布局 XML 带 style / defStyleAttr 时 framework 走的是三参构造器，
                // 只 hook 前两个签名会整批漏掉（v1.6 的坑），这里补上三参和四参。
                new Class<?>[]{Context.class, android.util.AttributeSet.class, int.class},
                new Class<?>[]{Context.class, android.util.AttributeSet.class, int.class, int.class},
        };
        int n = 0;
        for (final Class<?>[] sig : sigs) {
            try {
                final java.lang.reflect.Constructor<?> c =
                        WebView.class.getDeclaredConstructor(sig);
                hook(c).setId("pg:wvInit" + n).intercept(chain -> {
                    Object ret = chain.proceed();
                    noteWv("new", Arrays.toString(sig), chain.getThisObject());
                    return ret;
                });
                n++;
            } catch (Throwable ignore) {
            }
        }
        if (n == 0) {
            throw new NoSuchMethodException("no WebView constructor hooked");
        }
    }

    /** hook WebView 的加载入口：一有加载动作就把这个 WebView 接管，按节奏注入 JS。 */
    private void hookWebViewLoad() throws Throwable {
        Method m1 = WebView.class.getDeclaredMethod("loadUrl", String.class);
        hook(m1).setId("pg:wvLoadUrl").intercept(chain -> {
            Object self = chain.getThisObject();
            String url = String.valueOf(chain.getArg(0));
            if (self instanceof WebView && blockAdWv((WebView) self, url)) {
                return null;
            }
            Object ret = chain.proceed();
            if (self instanceof WebView) {
                noteWv("loadUrl", url, self);
                scheduleWvInject((WebView) self);
            }
            return ret;
        });
        Method m2 = WebView.class.getDeclaredMethod("loadUrl", String.class, Map.class);
        hook(m2).setId("pg:wvLoadUrlMap").intercept(chain -> {
            Object self = chain.getThisObject();
            String url = String.valueOf(chain.getArg(0));
            if (self instanceof WebView && blockAdWv((WebView) self, url)) {
                return null;
            }
            Object ret = chain.proceed();
            if (self instanceof WebView) {
                noteWv("loadUrl2", url, self);
                scheduleWvInject((WebView) self);
            }
            return ret;
        });
        // loadDataWithBaseURL：AgentWeb/自研容器加载"错误页 HTML"走的就是这条路，
        // 命中就说明粉色页是本地/服务端下发的错误页 HTML，而不是真页面加载失败。
        try {
            Method m3 = WebView.class.getDeclaredMethod("loadDataWithBaseURL",
                    String.class, String.class, String.class, String.class, String.class);
            hook(m3).setId("pg:wvLoadData").intercept(chain -> {
                Object self = chain.getThisObject();
                Object base = chain.getArg(0);
                Object data = chain.getArg(1);
                Object ret = chain.proceed();
                if (self instanceof WebView) {
                    String h = data == null ? "null" : String.valueOf(data);
                    noteWv("loadData", "base=" + base + " len=" + h.length()
                            + " head=" + head(h, 80), self);
                    scheduleWvInject((WebView) self);
                }
                return ret;
            });
        } catch (Throwable ignore) {
        }
    }

    /** 已判定为广告的 WebView：加载被拦截，后续任何 setVisibility(VISIBLE) 都会被压回 GONE。 */
    private static final Map<WebView, Boolean> sAdWv =
            Collections.synchronizedMap(new WeakHashMap<WebView, Boolean>());

    /** 广告加载拦截日志去重。 */
    private static final Set<String> sLoggedBlocked = ConcurrentHashMap.newKeySet();

    /** 仅隐藏广告 WebView 本身，不再摘除宿主容器或强显其他业务页。 */
    private static boolean blockAdWv(final WebView wv, String url) {
        if (url == null || !isAdUrl(url)) {
            return false;
        }
        sAdWv.put(wv, Boolean.TRUE);
        if (sLoggedBlocked.size() < 60 && sLoggedBlocked.add(url)) {
            ModuleLog.i(TAG, "wv loadUrl BLOCKED: " + head(url, 150));
        }
        new Handler(Looper.getMainLooper()).post(() -> hideQuietly(wv));
        return true;
    }

    /** hook WebViewClient 生命周期：页面加载完成再补一遍注入，兜住 loadUrl 之后的重定向。 */
    private void hookWebViewClient() throws Throwable {
        Method m = WebViewClient.class.getDeclaredMethod("onPageFinished",
                WebView.class, String.class);
        hook(m).setId("pg:onPageFinished").intercept(chain -> {
            Object self = chain.getThisObject();
            Object wvObj = chain.getArg(0);
            String url = String.valueOf(chain.getArg(1));
            Object ret = chain.proceed();
            if (wvObj instanceof WebView) {
                noteWv("onPageFinished", url, wvObj);
                scheduleWvInject((WebView) wvObj);
            }
            return ret;
        });
    }

    /** 每次执行前校验真实业务 URL，避免延迟任务误改导航后的页面。 */
    private boolean shouldTouch(WebView wv) {
        try {
            Uri uri = Uri.parse(wv.getUrl());
            return "https".equals(uri.getScheme())
                    && "h5user.qiekj.com".equals(uri.getHost())
                    && "/integral/homeForApp".equals(uri.getPath());
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * WebView 一被创建/加载就排注入：先 0ms 打一次，再 600ms×16（前 10 秒）持续盯。
     *
     * <p>H5 的粉色错误页可能 3~10 秒才渲染出来，集中在一次注入里判断会正好错过。
     */
    private void scheduleWvInject(final WebView wv) {
        try {
            if (!shouldTouch(wv)) {
                return;
            }
            seenSince(wv);
            String url = null;
            try {
                url = wv.getUrl();
            } catch (Throwable ignore) {
            }
            final String u = url;
            if (sLoggedWv.size() < 80 && sLoggedWv.add("inject#" + u)) {
                ModuleLog.i(TAG, "h5 inject: act=" + actName(wv) + " ctx=" + ctxName(wv)
                        + " url=" + u);
            }
            if (!sJsScheduled.add(wv)) {
                runJs(wv);
                return;
            }
            for (int i = 1; i <= 16; i++) {
                final long d = 600L * i;
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        runJs(wv);
                    }
                }, d);
            }
            runJs(wv);
        } catch (Throwable ignore) {
        }
    }

    /**
     * WebView 处理：记录 URL 供诊断；命中广告域名直接隐藏；
     * 首页上无 ID 且占屏过半的匿名 WebView（H5 广告槽加载失败页）也隐藏。
     */
    private void handleWebView(View v) {
        try {
            WebView wv = (WebView) v;
            String url = wv.getUrl();
            if (url != null && sLoggedUrls.add(url)) {
                ModuleLog.i(TAG, "webview url: " + url + " vis=" + v.getVisibility()
                        + " size=" + v.getWidth() + "x" + v.getHeight()
                        + " id=" + entryNameOf(v));
            }
            boolean ad = url != null && isAdUrl(url);
            if (ad) {
                sAdWv.put(wv, Boolean.TRUE);
            }
            if (ad && v.getVisibility() != View.GONE) {
                ModuleLog.i(TAG, "webview hidden: ad url=" + head(url, 120));
                hideQuietly(v);
                return;
            }
            injectH5Hide(v);
        } catch (Throwable ignore) {
        }
    }

    /** 精确限定积分首页及已确认的营销类名；保留价值卡、新客卡和业务功能。 */
    private static final String HIDE_JS =
            "(function(){try{"
          + "if(location.hostname!=='h5user.qiekj.com'||location.pathname!=='/integral/homeForApp')return 'SKIP';"
          + "var sel='[class*=HomeForApp_marke]';"
          + "function clean(){var es=document.querySelectorAll(sel);for(var i=0;i<es.length;i++)es[i].style.setProperty('display','none','important');}"
          + "clean();if(!window.__pgAdObserver){window.__pgAdObserver=new MutationObserver(clean);"
          + "window.__pgAdObserver.observe(document.documentElement,{childList:true,subtree:true});}"
          + "return 'OK|explicit-marketing-only';}catch(e){return 'JSERR|'+e;}})();";

    private static final Set<WebView> sJsScheduled =
            Collections.newSetFromMap(new WeakHashMap<WebView, Boolean>());
    private static final Set<WebView> sReloaded =
            Collections.newSetFromMap(new WeakHashMap<WebView, Boolean>());
    private static final Map<WebView, Long> sReloadAt =
            Collections.synchronizedMap(new WeakHashMap<WebView, Long>());
    private static final Map<WebView, Long> sWvSeen =
            Collections.synchronizedMap(new WeakHashMap<WebView, Long>());
    private static final Set<String> sLoggedInject = ConcurrentHashMap.newKeySet();

    private static long seenSince(WebView wv) {
        Long t = sWvSeen.get(wv);
        if (t == null) {
            t = System.currentTimeMillis();
            sWvSeen.put(wv, t);
        }
        return t;
    }

    /**
     * 解包 {@link ContextWrapper} 找到真正承载这个 View 的 Activity；拿不到就退回
     * {@link #sCurAct}（onResume 观测到的当前 Activity）。
     *
     * <p>首页那个匿名 WebView 很可能是用 MutableContextWrapper 或 Application Context 创建的，
     * v1.3/v1.4 在这里直接 {@code ctx instanceof Activity} 判 false 就 return，
     * 于是整条 H5 处理链一次都没跑过——这是"粉色错误页永远不消失"的直接原因。
     */
    private static Activity actOf(View v) {
        try {
            Context c = v.getContext();
            int guard = 0;
            while (c != null && guard++ < 12) {
                if (c instanceof Activity) {
                    return (Activity) c;
                }
                if (c instanceof ContextWrapper) {
                    c = ((ContextWrapper) c).getBaseContext();
                } else {
                    break;
                }
            }
        } catch (Throwable ignore) {
        }
        return sCurAct;
    }

    private void injectH5Hide(View v) {
        try {
            if (v.getVisibility() != View.VISIBLE || !shouldTouch((WebView) v)) {
                return;
            }
            Activity act = actOf(v);
            if (act == null) {
                return;
            }
            String cn = act.getClass().getName();
            if (isAdAct(cn)) {
                return;
            }
            final boolean home = isHomeAct(act);
            final WebView wv = (WebView) v;
            seenSince(wv);
            String url = null;
            try {
                url = wv.getUrl();
            } catch (Throwable ignore) {
            }
            if (sLoggedInject.size() < 40) {
                String key = cn + "#" + url + "#"
                        + (v.getContext() == null ? "null" : v.getContext().getClass().getName());
                if (sLoggedInject.add(key)) {
                    ModuleLog.i(TAG, "h5 inject: act=" + cn + " home=" + home + " ctx="
                            + (v.getContext() == null ? "null" : v.getContext().getClass().getName())
                            + " url=" + url);
                }
            }
            // H5 的粉色错误页往往在 3~10 秒才被服务端/前端渲染出来，
            // 只在固定几个时间点注入会正好错过，改成前 12 秒持续盯（40 次 × 300ms）。
            if (sJsScheduled.add(wv)) {
                final int times = home ? 40 : 8;
                final long step = home ? 300L : 500L;
                for (int i = 1; i <= times; i++) {
                    final long d = step * i;
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            runJs(wv);
                        }
                    }, d);
                }
            }
            runJs(wv);
        } catch (Throwable ignore) {
        }
    }

    /** JS 结果去重日志，便于用户开 LSPosed 日志时直接看到 H5 结构判定。 */
    private static final Set<String> sLoggedJs = ConcurrentHashMap.newKeySet();

    private void runJs(final WebView wv) {
        try {
            if (!shouldTouch(wv)) return;
            final String js = HIDE_JS;
            wv.evaluateJavascript(js, new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    try {
                        if (value == null) {
                            return;
                        }
                        String url = wv.getUrl();
                        if (sLoggedJs.size() < 120 && sLoggedJs.add(value + " @ " + url)) {
                            ModuleLog.i(TAG, "h5 js: " + value + " url=" + url);
                        }
                    } catch (Throwable ignore) {
                    }
                }
            });
        } catch (Throwable ignore) {
        }
    }

    private static boolean isAdUrl(String url) {
        Uri uri;
        try {
            uri = Uri.parse(url);
        } catch (Throwable ignored) {
            return false;
        }
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) return false;
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        if (host.equals("qiekj.com") || host.endsWith(".qiekj.com")
                || host.equals("youzan.com") || host.endsWith(".youzan.com")) return false;
        String u = (host + (uri.getPath() == null ? "" : uri.getPath())).toLowerCase(Locale.ROOT);
        String[] marks = {
                "gromore", "pangle", "pglstatp", "ad-pattern", "solid_aggregation",
                "shoppingmall", ".csj.", "/csj/", "csj.cn", "sigmob", "octopus", "meishu",
                "anythink", "topon", "junion", "yd_saas", "ydsaas", "saas.ad", "ksad",
                "hiad", "mimo.ad", "/ad/", ".ads.", "/ads/", "/obj/ad/", "adserver", "pgad",
                "adx", "splash.ad", "qiekeji.com/ad", "quanzhi",
        };
        for (String m : marks) {
            if (u.contains(m)) {
                return true;
            }
        }
        return false;
    }

    private static final java.util.concurrent.atomic.AtomicInteger sDumpCount =
            new java.util.concurrent.atomic.AtomicInteger(0);

    private void dumpLater(Activity a, long delay) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (a.isFinishing() || a.isDestroyed()) {
                    return;
                }
                if (sDumpCount.get() >= 3) {
                    return;
                }
                sDumpCount.incrementAndGet();
                View root = a.getWindow() == null ? null : a.getWindow().getDecorView();
                if (root != null) {
                    ModuleLog.i(TAG, "TREE-BEGIN act=" + a.getClass().getName()
                            + " root=" + root.getWidth() + "x" + root.getHeight());
                    dumpBig(root, root, root.getWidth(), 0, new int[1]);
                    ModuleLog.i(TAG, "TREE-END");
                }
            } catch (Throwable ignore) {
            }
        }, delay);
    }

    /**
     * 只 dump "整行级"节点（宽≥45%屏宽、高≥8px），上限 36 条、深度 26。
     *
     * <p>全树 dump 动辄上千行，日志页翻不动；真正要回答的问题只有一个——
     * 首页内容区那一层的直接子节点到底是什么控件（WebView / FrameLayout / ImageView）。
     * 按宽度砍掉所有小控件后，一张截图就能看完。
     */
    private void dumpBig(View v, View root, int sw, int depth, int[] count) {
        if (v == null || count[0] >= 36 || depth > 26) {
            return;
        }
        try {
            if (v != root) {
                int w = v.getWidth();
                int h = v.getHeight();
                if (sw > 0 && (w < sw * 0.45 || h < 8)) {
                    return;
                }
            }
            if (v.getVisibility() != View.GONE) {
                count[0]++;
                StringBuilder sb = new StringBuilder("TREE d").append(depth)
                        .append(' ').append(v.getClass().getName())
                        .append(" id=").append(entryNameOf(v))
                        .append(' ').append(v.getWidth()).append('x').append(v.getHeight())
                        .append(" vis=").append(v.getVisibility());
                if (v instanceof WebView) {
                    sb.append(" url=").append(((WebView) v).getUrl());
                }
                ModuleLog.i(TAG, sb.toString());
            }
        } catch (Throwable ignore) {
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            int n = g.getChildCount();
            for (int i = 0; i < n; i++) {
                if (count[0] >= 36) {
                    return;
                }
                try {
                    dumpBig(g.getChildAt(i), root, sw, depth + 1, count);
                } catch (Throwable ignore) {
                }
            }
        }
    }

    /** 在 setVisibility hook 里延迟处理父容器，避免 hook 内重入。 */
    private void scheduleHideParent(View v) {
        try {
            final ViewParent p = v.getParent();
            if (!(p instanceof View)) {
                return;
            }
            final View parent = (View) p;
            if (parent.getVisibility() == View.GONE) {
                return;
            }
            new Handler(Looper.getMainLooper()).post(() -> hideQuietly(parent));
        } catch (Throwable ignore) {
        }
    }

    private void hideParentNow(View v) {
        try {
            ViewParent p = v.getParent();
            if (p instanceof View) {
                hideQuietly((View) p);
            }
        } catch (Throwable ignore) {
        }
    }

    private static void hideQuietly(View v) {
        try {
            if (v != null && v.getVisibility() != View.GONE) {
                v.setVisibility(View.GONE);
            }
        } catch (Throwable ignore) {
        }
    }

    // ------------------------------------------------------- 3. ViewStub / 弹窗

    private void hookViewStubInflate() throws Throwable {
        Method m = ViewStub.class.getDeclaredMethod("inflate");
        hook(m).setId("pg:viewStubInflate").intercept(chain -> {
            Object ret = chain.proceed();
            try {
                if (ret instanceof View) {
                    View inflated = (View) ret;
                    View stub = (View) chain.getThisObject();
                    String stubName = stub == null ? null : entryNameOf(stub);
                    String retName = entryNameOf(inflated);
                    boolean ad = (stubName != null && isAdName(stubName))
                            || (retName != null && isAdName(retName));
                    if (ad) {
                        inflated.setVisibility(View.GONE);
                        ModuleLog.i(TAG, "viewstub inflated ad hidden: stub=" + stubName + " root=" + retName);
                    }
                }
            } catch (Throwable ignore) {
            }
            return ret;
        });
    }

    private void hookDialogShow() throws Throwable {
        Method m = android.app.Dialog.class.getDeclaredMethod("show");
        hook(m).setId("pg:dialogShow").intercept(chain -> {
            Object ret = chain.proceed();
            try {
                final Object self = chain.getThisObject();
                if (self instanceof android.app.Dialog) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            android.app.Dialog d = (android.app.Dialog) self;
                            if (d.isShowing() && d.getWindow() != null) {
                                View decor = d.getWindow().getDecorView();
                                scanTree(decor, decor);
                            }
                        } catch (Throwable ignore) {
                        }
                    }, 250L);
                }
            } catch (Throwable ignore) {
            }
            return ret;
        });
    }

    private void hookPopupWindow() throws Throwable {
        int count = 0;
        for (Method m : PopupWindow.class.getDeclaredMethods()) {
            String name = m.getName();
            if (!("showAsDropDown".equals(name) || "showAtLocation".equals(name) || "showAsDropDown".equals(name))) {
                continue;
            }
            hook(m).setId("pg:popup:" + Arrays.toString(m.getParameterTypes())).intercept(chain -> {
                Object ret = chain.proceed();
                try {
                    final Object self = chain.getThisObject();
                    if (self instanceof PopupWindow) {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            try {
                                PopupWindow pw = (PopupWindow) self;
                                if (pw.isShowing()) {
                                    View cv = pw.getContentView();
                                    if (cv != null) {
                                        scanTree(cv, cv);
                                    }
                                }
                            } catch (Throwable ignore) {
                            }
                        }, 250L);
                    }
                } catch (Throwable ignore) {
                }
                return ret;
            });
            count++;
        }
        if (count == 0) {
            throw new IllegalStateException("no PopupWindow show hook installed");
        }
        moduleLog(Log.INFO, TAG, "popup hooks: " + count);
    }

    // ------------------------------------------------------- 4. 开屏/插屏 Activity finish

    private void hookActivityCreate() throws Throwable {
        Method m = Activity.class.getDeclaredMethod("onCreate", Bundle.class);
        hook(m).setId("pg:onCreate").intercept(chain -> {
            Object ret = chain.proceed();
            Object self = chain.getThisObject();
            if (self instanceof Activity) {
                onActCreated((Activity) self);
            }
            return ret;
        });
    }

    /** Activity 创建后的统一处理：由 Activity.onCreate 与 Instrumentation 两条路共同驱动。 */
    private void onActCreated(Activity a) {
        if (!Cfg.qiekjAds) return;
        try {
            final String cn = a.getClass().getName();
            logAct("created", a);
            if (CLS_HOT_SPLASH.equals(cn)) {
                finishSoon(a, 60L);
                return;
            }
            for (String prefix : AD_ACT_PREFIXES) {
                if (cn.startsWith(prefix)) {
                    finishSoon(a, 120L);
                    return;
                }
            }
        } catch (Throwable ignore) {
        }
    }

    /**
     * 在 Instrumentation 层接管 Activity 生命周期。
     *
     * <p>{@code callActivityOnCreate(Activity, Bundle)} / {@code callActivityOnResume(Activity)}
     * 是 framework（ActivityThread）主动调用的，与子类是否 override、是否调用 super 无关，
     * 比 hook {@code Activity.onResume} 稳得多。页面的扫树/注入/订单修正全部由此驱动。
     */
    private void hookInstrumentationLifecycle() throws Throwable {
        int n = 0;
        try {
            Method m1 = Instrumentation.class.getDeclaredMethod(
                    "callActivityOnCreate", Activity.class, Bundle.class);
            hook(m1).setId("pg:instrCreate").intercept(chain -> {
                Object actArg = chain.getArg(0);
                Object ret = chain.proceed();
                if (actArg instanceof Activity) {
                    try {
                        onActCreated((Activity) actArg);
                    } catch (Throwable ignore) {
                    }
                }
                return ret;
            });
            n++;
        } catch (Throwable ignore) {
        }
        try {
            Method m2 = Instrumentation.class.getDeclaredMethod(
                    "callActivityOnResume", Activity.class);
            hook(m2).setId("pg:instrResume").intercept(chain -> {
                Object actArg = chain.getArg(0);
                Object ret = chain.proceed();
                if (actArg instanceof Activity) {
                    try {
                        onActResumed((Activity) actArg);
                    } catch (Throwable ignore) {
                    }
                }
                return ret;
            });
            n++;
        } catch (Throwable ignore) {
        }
        if (n == 0) {
            throw new NoSuchMethodException("Instrumentation lifecycle methods not found");
        }
    }

    private static volatile long sLastAttachScanAt;

    /**
     * View 上屏兜底：任何 View 附着窗口都会走 {@code onAttachedToWindow}，
     * 这里节流（400ms 一次）扫一遍当前 Activity 的视图树。
     *
     * <p>之所以要这条：首页 H5 容器是 onResume 之后才 inflate 的，靠生命周期时间点扫树
     * 很容易错过；冤枉的是扫树本身没什么开销（几百个节点），真正贵的是重复注入。
     */
    private void hookViewAttach() throws Throwable {
        Method m = View.class.getDeclaredMethod("onAttachedToWindow");
        hook(m).setId("pg:onAttach").intercept(chain -> {
            Object ret = chain.proceed();
            Object self = chain.getThisObject();
            if (self instanceof View) {
                onViewAttached((View) self);
            }
            return ret;
        });
    }

    private void onViewAttached(View v) {
        try {
            long now = System.currentTimeMillis();
            if (now - sLastAttachScanAt < 400L) {
                return;
            }
            sLastAttachScanAt = now;
            Activity a = actOf(v);
            if (a == null) {
                a = sCurAct;
            }
            if (a == null || a.isFinishing()) {
                return;
            }
            final Activity act = a;
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try {
                        View root = act.getWindow() == null ? null : act.getWindow().getDecorView();
                        if (root == null) {
                            return;
                        }
                        scanTree(root);
                    } catch (Throwable ignore) {
                    }
                }
            });
        } catch (Throwable ignore) {
        }
    }

    private void finishSoon(Activity a, long delay) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (a.isFinishing()) {
                    return;
                }
                boolean taskRoot = a.isTaskRoot();
                Intent toMain = null;
                if (taskRoot) {
                    // 该 Activity 位于任务栈底，finish 后会退回桌面，需要接手跳主页
                    toMain = new Intent();
                    toMain.setClassName(a, CLS_MAIN);
                    toMain.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                }
                a.finish();
                moduleLog(Log.INFO, TAG, "ad activity finished: " + a.getClass().getName() + " taskRoot=" + taskRoot);
                if (toMain != null) {
                    a.getApplicationContext().startActivity(toMain);
                }
            } catch (Throwable t) {
                moduleLog(Log.WARN, TAG, "finish ad activity failed: " + t);
            }
        }, delay);
    }

    // ------------------------------------------------------- 5. 摇一摇

    private void hookSensorManager() throws Throwable {
        Class<?> sm = Class.forName("android.hardware.SensorManager");
        int count = 0;
        for (Method m : sm.getDeclaredMethods()) {
            if (!"registerListener".equals(m.getName())) {
                continue;
            }
            Class<?>[] ps = m.getParameterTypes();
            boolean hasSensor = false;
            for (Class<?> p : ps) {
                if (p == Sensor.class) {
                    hasSensor = true;
                    break;
                }
            }
            if (!hasSensor) {
                continue;
            }
            final boolean retVoid = m.getReturnType() == void.class;
            hook(m).setId("pg:registerListener:" + Arrays.toString(ps)).intercept(chain -> {
                if (!isAdCaller()) return chain.proceed();
                for (Object arg : chain.getArgs()) {
                    if (arg instanceof Sensor) {
                        int type = ((Sensor) arg).getType();
                        if (type == Sensor.TYPE_ACCELEROMETER
                                || type == Sensor.TYPE_GYROSCOPE
                                || type == Sensor.TYPE_SIGNIFICANT_MOTION
                                || type == Sensor.TYPE_LINEAR_ACCELERATION) {
                            moduleLog(Log.INFO, TAG, "blocked sensor register, type=" + type);
                            return retVoid ? null : Boolean.FALSE;
                        }
                    }
                }
                return chain.proceed();
            });
            count++;
        }
        if (count == 0) {
            throw new IllegalStateException("no registerListener hook installed");
        }
        moduleLog(Log.INFO, TAG, "sensor hooks: " + count);
    }

    // ------------------------------------------------------- 6. 误触跳转拦截

    private static final Set<String> AD_PKGS = new HashSet<>(Arrays.asList(
            "com.taobao.taobao",
            "com.taobao.idlefish",
            "com.taobao.litetaobao",
            "com.tmall.wireless",
            "com.jingdong.app.mall",
            "com.xunmeng.pinduoduo",
            "com.ss.android.ugc.aweme",
            "com.ss.android.ugc.aweme.lite",
            "com.ss.android.ugc.live",
            "com.smile.gifmaker",
            "com.kuaishou.nebula",
            "com.xingin.xhs",
            "com.zhihu.android",
            "com.sankuai.meituan",
            "com.dianping.v1",
            "com.taobao.trip",
            "cn.damai",
            "com.alibaba.wireless"
    ));

    private static final Set<String> AD_SCHEMES = new HashSet<>(Arrays.asList(
            "taobao", "tbopen", "tmall", "tmopen",
            "jdmobile", "openapp.jdmobile", "pinduoduo",
            "snssdk1128", "kwai", "kwai-pro", "ksnebula",
            "xhsdiscover", "zhihu", "dianping", "imeituan", "damai"
    ));

    private void hookInstrumentation() throws Throwable {
        JumpPolicy.install(this, "campus:qiekj:jumps");
    }

    private static String adJumpReason(Intent it) {
        if (it == null) {
            return null;
        }
        try {
            ComponentName cn = it.getComponent();
            if (cn != null && cn.getPackageName() != null && AD_PKGS.contains(cn.getPackageName())) {
                return "component:" + cn.getPackageName();
            }
            String pkg = it.getPackage();
            if (pkg != null && AD_PKGS.contains(pkg)) {
                return "package:" + pkg;
            }
            Uri data = it.getData();
            if (data != null) {
                String scheme = data.getScheme();
                if (scheme != null && AD_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
                    return "scheme:" + scheme + " -> " + data;
                }
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    // ------------------------------------------------------- 7. 提交订单页行为修正

    /**
     * 提交订单页（OrderPayPreviewAct）：
     * <ul>
     *   <li>「阅读微博优惠0.5元」服务端默认勾选 → 模拟点击取消勾选；</li>
     *   <li>支付方式默认选中「银联支付」→ 未选中时模拟点击选中。</li>
     * </ul>
     * 行定位不依赖容器 ID：整树找 tvPromotionName / tvPayName 两个标记 TextView，
     * 其直接父级即行根。勾选态：优惠行看 viewArrow（蓝色对勾）是否 VISIBLE；
     * 支付行看 viewSelector 背景中心像素是否不透明（对勾实心 / 圆环空心）。
     */
    private static final Map<Activity, int[]> sOrderState =
            Collections.synchronizedMap(new IdentityHashMap<Activity, int[]>());

    private void orderFixLater(Activity a, long delay, int pass) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (a.isFinishing() || a.isDestroyed()) {
                    return;
                }
                orderFixPass(a, pass);
            } catch (Throwable t) {
                ModuleLog.w(TAG, "orderFix pass" + pass + " failed: " + t);
            }
        }, delay);
    }

    private void orderFixPass(Activity a, int pass) {
        if (!Cfg.qiekjSelections) return;
        View root = a.getWindow() == null ? null : a.getWindow().getDecorView();
        if (root == null) {
            return;
        }
        int[] st = sOrderState.get(a);
        if (st == null) {
            st = new int[4]; // [0]=weibo 点击次数 [1]=union 点击次数 [2]=weibo 完成 [3]=union 完成
            sOrderState.put(a, st);
        }
        int idTitle = resId(a, "tvPromotionName");
        int idArrow = resId(a, "viewArrow");
        int idSelector = resId(a, "viewSelector");
        int idPayName = resId(a, "tvPayName");
        if (idTitle == 0 || idPayName == 0) {
            return;
        }

        // 7.1 微博优惠取消勾选
        boolean weiboChecked = false;
        if (st[2] == 0 && st[0] < 3) {
            TextView title = findFirstById(root, idTitle);
            if (title != null && title.getText() != null
                    && title.getText().toString().contains("微博")) {
                View row = (View) title.getParent();
                View arrow = idArrow == 0 ? null : row.findViewById(idArrow);
                weiboChecked = arrow != null && arrow.getVisibility() == View.VISIBLE;
                if (weiboChecked) {
                    View target;
                    if (st[0] == 0) {
                        target = row;
                    } else if (st[0] == 1) {
                        target = arrow;
                    } else {
                        target = idSelector == 0 ? row : row.findViewById(idSelector);
                    }
                    if (target != null) {
                        target.performClick();
                        st[0]++;
                        ModuleLog.i(TAG, "order: unchecked weibo promo, pass=" + pass + " attempt=" + st[0]);
                    }
                } else {
                    st[2] = 1;
                }
            }
        }

        // 7.2 默认银联支付（等微博优惠处理完再动支付行，避免同帧状态竞争）
        if (st[3] == 0 && st[2] == 1 && st[1] < 2) {
            TextView payName = findFirstById(root, idPayName);
            while (payName != null) {
                CharSequence txt = payName.getText();
                if (txt != null && txt.toString().contains("银联")) {
                    View row = (View) payName.getParent();
                    View sel = idSelector == 0 ? null : row.findViewById(idSelector);
                    boolean selected = sel != null && isCenterOpaque(sel.getBackground());
                    if (!selected) {
                        View target = st[1] == 0 ? row : sel;
                        if (target != null) {
                            target.performClick();
                            st[1]++;
                            ModuleLog.i(TAG, "order: selected unionpay, pass=" + pass + " attempt=" + st[1]);
                        }
                    } else {
                        st[3] = 1;
                        ModuleLog.i(TAG, "order: unionpay already selected, pass=" + pass);
                    }
                    break;
                }
                payName = findNextById(payName, root, idPayName);
            }
        }
    }

    private static final ConcurrentHashMap<String, Integer> sResIdCache = new ConcurrentHashMap<>();

    private static int resId(Activity a, String name) {
        String key = a.getPackageName() + "/" + name;
        Integer cached = sResIdCache.get(key);
        if (cached != null) {
            return cached;
        }
        int id = 0;
        try {
            id = a.getResources().getIdentifier(name, "id", a.getPackageName());
        } catch (Throwable ignore) {
        }
        sResIdCache.put(key, id);
        return id;
    }

    private static TextView findFirstById(View root, int id) {
        return (TextView) findFirstByIdInternal(root, id);
    }

    private static View findFirstByIdInternal(View v, int id) {
        if (v == null) {
            return null;
        }
        if (v.getId() == id) {
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = ( ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View hit = findFirstByIdInternal(g.getChildAt(i), id);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    /** 深度优先顺序里 found 之后的下一个同 ID View。 */
    private static TextView findNextById(View found, View root, int id) {
        boolean[] seen = {false};
        return (TextView) findNextInternal(root, id, found, seen);
    }

    private static View findNextInternal(View v, int id, View found, boolean[] seen) {
        if (v == null) {
            return null;
        }
        if (seen[0]) {
            if (v.getId() == id) {
                return v;
            }
        } else if (v == found) {
            seen[0] = true;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View hit = findNextInternal(g.getChildAt(i), id, found, seen);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    /** 背景 drawable 中心像素是否不透明：实心对勾=选中，空心圆环=未选中。 */
    private static boolean isCenterOpaque(Drawable d) {
        if (d == null) {
            return false;
        }
        Bitmap bm = null;
        try {
            bm = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            d.setBounds(0, 0, 3, 3);
            d.draw(c);
            return Color.alpha(bm.getPixel(1, 1)) > 8;
        } catch (Throwable t) {
            return false;
        } finally {
            if (bm != null) {
                bm.recycle();
            }
        }
    }

    /** Native home in App 1.143.1: banner_view is the SERVICE menu, not an ad.
     * requestMallAd's own empty-ad branch hides llScrollingView and sets ctl flags=0.
     * Apply that layout state without requesting the ecommerce ad or touching services.
     */
    private void hookNativeHome(ClassLoader cl) throws Throwable {
        Class<?> cls = Class.forName("com.qiekj.user.ui.fragment.HomeFragmentV3", false, cl);
        Method accessor = cls.getDeclaredMethod("access$getMBinding", cls);
        accessor.setAccessible(true);
        // Validate the expected layout before installing version-specific hooks.
        Class<?> binding = accessor.getReturnType();
        for (String name : new String[]{"appBar", "ctl", "llScrollingView", "bannerView",
                "clColumn", "tvLocationInfo", "ivUpTop", "tvTop", "refresh", "toolbar"}) {
            binding.getField(name);
        }
        // Deoptimize callers so AOT inlining cannot bypass the narrow app hooks.
        for (Method method : cls.getDeclaredMethods()) {
            if ("initData".equals(method.getName())
                    || method.getName().startsWith("loadAd$")
                    || method.getName().startsWith("initListener$lambda$9")) {
                deoptimize(method);
            }
        }
        Method load = cls.getDeclaredMethod("loadAd");
        hook(load).setId("pg:home:noMallLoader").intercept(chain -> {
            cleanNativeHome(chain.getThisObject(), accessor);
            ModuleLog.i(TAG, "v2.8 native home: mall loader suppressed; services retained");
            return null;
        });
        Method request = cls.getDeclaredMethod("requestMallAd");
        hook(request).setId("pg:home:noMallAd").intercept(chain -> {
            cleanNativeHome(chain.getThisObject(), accessor);
            ModuleLog.i(TAG, "v2.8 native home: mall ad request suppressed; service menu preserved");
            return null;
        });
        Method init = cls.getDeclaredMethod("initView", Bundle.class);
        hook(init).setId("pg:home:init").intercept(chain -> {
            Object result = chain.proceed();
            cleanNativeHome(chain.getThisObject(), accessor);
            Object bindingObject = accessor.invoke(null, chain.getThisObject());
            View toolbar = bindingView(bindingObject, "toolbar");
            toolbar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> positionNativeServices(bindingObject));
            toolbar.post(() -> positionNativeServices(bindingObject));
            // The app otherwise initializes the location label only after its ad/dialog chain.
            // Reuse its own permission checks and cached-POI logic so location remains usable.
            Method location = cls.getDeclaredMethod("checkAndShowLocation");
            location.setAccessible(true);
            location.invoke(chain.getThisObject());
            return result;
        });
        // With a zero scroll range the original offset callback always enters mall mode.
        Method mallEnter = cls.getDeclaredMethod("onMallEnter");
        hook(mallEnter).setId("pg:home:noMallMode").intercept(chain -> null);
    }

    private static final Set<View> sHomeServiceViews =
            Collections.newSetFromMap(new WeakHashMap<View, Boolean>());

    private static View bindingView(Object binding, String name) throws Exception {
        return (View) binding.getClass().getField(name).get(binding);
    }

    private static void cleanNativeHome(Object fragment, Method accessor) {
        try {
            Object binding = accessor.invoke(null, fragment);
            java.lang.reflect.Field topFlag = fragment.getClass().getDeclaredField("topFlag");
            topFlag.setAccessible(true);
            topFlag.setBoolean(fragment, true);
            View ctl = bindingView(binding, "ctl");
            ViewGroup.LayoutParams params = ctl.getLayoutParams();
            params.getClass().getMethod("setScrollFlags", int.class).invoke(params, 0);
            ctl.setLayoutParams(params);
            View appBar = bindingView(binding, "appBar");
            appBar.getClass().getMethod("setExpanded", boolean.class, boolean.class)
                    .invoke(appBar, true, false);
            hideQuietly(bindingView(binding, "llScrollingView"));
            hideQuietly(bindingView(binding, "ivUpTop"));
            hideQuietly(bindingView(binding, "tvTop"));
            for (String name : new String[]{"bannerView", "clColumn", "tvLocationInfo"}) {
                sHomeServiceViews.add(bindingView(binding, name));
            }
            bindingView(binding, "bannerView").setVisibility(View.VISIBLE);
            bindingView(binding, "clColumn").setVisibility(View.VISIBLE);
            bindingView(binding, "tvLocationInfo").setVisibility(View.VISIBLE);
            positionNativeServices(binding);
            // Disable the bottom-edge gesture that opens the advertising mall.
            View refresh = bindingView(binding, "refresh");
            refresh.getClass().getMethod("setEnableLoadMore", boolean.class).invoke(refresh, false);
        } catch (Throwable e) {
            ModuleLog.w(TAG, "v2.8 native home layout skipped", e);
        }
    }

    private static void positionNativeServices(Object binding) {
        try {
            View location = bindingView(binding, "tvLocationInfo");
            location.setVisibility(View.VISIBLE);
            View column = bindingView(binding, "clColumn");
            View toolbar = bindingView(binding, "toolbar");
            View content = (View) column.getParent();
            content.setVisibility(View.VISIBLE);
            int inset = toolbar.getHeight();
            if (inset > 0 && content.getPaddingTop() != inset) {
                content.setPadding(content.getPaddingLeft(), inset, content.getPaddingRight(), content.getPaddingBottom());
            }
            ViewGroup.LayoutParams params = column.getLayoutParams();
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
                if (margins.topMargin != 0) {
                    margins.topMargin = 0;
                    column.setLayoutParams(margins);
                }
            }
        } catch (Throwable e) {
            ModuleLog.w(TAG, "v2.8 native service position skipped", e);
        }
    }

    /** Automatic interstitials have an app-owned entry distinct from reward video.
     * Blocking before SDK load prevents the mediation chain from trying another SDK
     * when an already-created ad Activity is closed.
     */
    private void hookPassiveInterstitials(ClassLoader cl) throws Throwable {
        Class<?> cls = Class.forName("com.qiekj.user.ad.AdExtKt", false, cl);
        int count = 0;
        for (Method method : cls.getDeclaredMethods()) {
            String name = method.getName();
            if (("loadTopOnDialog".equals(name) || "loadTopOnDialog$default".equals(name)
                    || "loadTopOnSlot".equals(name) || "skuDialog".equals(name))
                    && method.getReturnType() == void.class) {
                hook(method).setId("pg:passive:" + name).intercept(chain -> {
                    Object activity = chain.getArg(0);
                    if (!(activity instanceof Activity)) return chain.proceed();
                    String page = activity.getClass().getName();
                    boolean scanPage = "com.qiekj.user.ui.activity.scan.hair.HairGoodsSkuNewAct".equals(page)
                            || "com.qiekj.user.ui.activity.scan.OrderPayPreviewAct".equals(page);
                    boolean deviceDialog = "com.qiekj.user.ui.activity.home.MyDeviceAct".equals(page)
                            && name.startsWith("loadTopOnDialog");
                    boolean homeAd = CLS_MAIN.equals(page) && name.startsWith("loadTopOn");
                    if (!scanPage && !deviceDialog && !homeAd) {
                        return chain.proceed();
                    }
                    if ("loadTopOnSlot".equals(name) && chain.getArg(1) instanceof View) {
                        View slot = (View) chain.getArg(1);
                        slot.setVisibility(View.GONE);
                        ViewGroup.LayoutParams params = slot.getLayoutParams();
                        if (params != null) { params.height = 0; slot.setLayoutParams(params); }
                    }
                    ModuleLog.i(TAG, "automatic ad suppressed at app entry: " + name + " page=" + page);
                    return null;
                });
                count++;
            }
        }
        Class<?> devices = Class.forName("com.qiekj.user.ui.activity.home.MyDeviceAct", false, cl);
        for (Method method : devices.getDeclaredMethods()) {
            if (method.getName().startsWith("initData")) deoptimize(method);
        }
        for (String page : new String[]{CLS_MAIN, "com.qiekj.user.ui.activity.scan.hair.HairGoodsSkuNewAct",
                "com.qiekj.user.ui.activity.scan.OrderPayPreviewAct"}) {
            for (Method method : Class.forName(page, false, cl).getDeclaredMethods()) {
                if (method.getName().startsWith("initData")) deoptimize(method);
            }
        }
        if (count == 0) throw new NoSuchMethodException("AdExtKt.loadTopOnDialog");
    }

    private static boolean isAdCaller() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String n = frame.getClassName();
            if (n.startsWith("com.bytedance.") || n.startsWith("com.byaztp.")
                    || n.startsWith("com.anythink.") || n.startsWith("com.qq.e.ads.")
                    || n.startsWith("com.kwad.") || n.startsWith("com.qiekeji.pgad.")
                    || n.startsWith("com.jutui.ads.") || n.startsWith("com.yd.saas.")
                    || n.startsWith("com.sigmob.") || n.startsWith("com.miui.zeus.mimo.")) return true;
        }
        return false;
    }

    // ------------------------------------------------------- 8. 广告 SDK 可选增强

    private void hookAnyThinkSplash(ClassLoader cl) throws Throwable {
        Class<?> cls = Class.forName("com.anythink.splashad.api.ATSplashAd", false, cl);
        int count = 0;
        for (Method m : cls.getDeclaredMethods()) {
            String name = m.getName();
            if ("isAdReady".equals(name) && m.getReturnType() == boolean.class) {
                hook(m).setId("pg:atsplash:isAdReady").intercept(chain -> false);
                count++;
            } else if (name.startsWith("show")) {
                hook(m).setId("pg:atsplash:" + name).intercept(chain -> {
                    moduleLog(Log.INFO, TAG, "blocked ATSplashAd." + name);
                    return null;
                });
                count++;
            }
        }
        moduleLog(Log.INFO, TAG, "ATSplashAd hooks: " + count);
        if (count == 0) {
            throw new IllegalStateException("ATSplashAd methods not matched");
        }
    }

    private void hookTTAdNative(ClassLoader cl) throws Throwable {
        Class<?> cls = Class.forName("com.bytedance.sdk.openadsdk.api.TTAdNative", false, cl);
        int count = 0;
        for (Method m : cls.getDeclaredMethods()) {
            if ("loadSplashAd".equals(m.getName())) {
                hook(m).setId("pg:tt:loadSplashAd").intercept(chain -> {
                    moduleLog(Log.INFO, TAG, "blocked TTAdNative.loadSplashAd");
                    return null;
                });
                count++;
            }
        }
        moduleLog(Log.INFO, TAG, "TTAdNative hooks: " + count);
        if (count == 0) {
            throw new IllegalStateException("TTAdNative.loadSplashAd not matched");
        }
    }

    private void hookGdtSplash(ClassLoader cl) throws Throwable {
        Class<?> cls = Class.forName("com.qq.e.ads.splash.SplashAD", false, cl);
        int count = 0;
        for (Method m : cls.getDeclaredMethods()) {
            String name = m.getName();
            if (name.startsWith("show") || name.startsWith("fetchAndShow")) {
                hook(m).setId("pg:gdt:" + name).intercept(chain -> {
                    moduleLog(Log.INFO, TAG, "blocked SplashAD." + name);
                    return null;
                });
                count++;
            }
        }
        moduleLog(Log.INFO, TAG, "SplashAD hooks: " + count);
        if (count == 0) {
            throw new IllegalStateException("SplashAD show methods not matched");
        }
    }

    // --------------------------------------- 9. 开屏对话框不再空等（v3.4 新增）

    /** 291 版开屏流程：SplashActivity → MainActivity(showSplash=true) → 本对话框。 */
    private static final String CLS_SPLASH_DIALOG = "com.qiekj.user.ui.activity.SplashDialogFragment";
    /** 开屏广告的两个应用层封装：只有 showAd() 被调用后才会回调 onAdClosed()。 */
    private static final String CLS_PG_SPLASH_AD = "com.qiekeji.pgad.manager.gromore.SplashAd";
    private static final String CLS_PG_TAKU_SPLASH_AD = "com.qiekeji.pgad.manager.taku.TakuSplashAd";

    /**
     * 用户反馈"进软件要等好久"的根因：
     * <ul>
     *   <li>{@code SplashDialogFragment.initView()} 里先 {@code getSplashAd()} 拉开屏配置，
     *       再 {@code postDelayed(safeDismiss, 6000)} 兜底，即 {@code maxTimeout = 6000}；</li>
     *   <li>{@code loadAd(AdBean)} 按 {@code linkType} 分派到
     *       {@code loadGroMoreSplash()} / {@code loadTakuSplash()}；</li>
     *   <li>对话框只有三条关闭路径：广告 {@code onAdClosed()}、{@code onAdFailed()}、
     *       以及那个 6000ms 超时。模块把 SDK 的 {@code show()} 拦掉后，前两条永不回调，
     *       所以用户在品牌图或空白上要白等满 6 秒才进首页。</li>
     * </ul>
     * 这里改成：不再请求开屏广告、不再挂 6 秒定时器，对话框一初始化就关闭。
     * {@code onDismiss()} 仍会发出 splashDismissEvent，下游初始化逻辑不变。
     */
    private void hookSplashDialog(ClassLoader cl) throws Throwable {
        Class<?> cls = Class.forName(CLS_SPLASH_DIALOG, false, cl);
        Set<String> targets = new HashSet<>(Arrays.asList(
                "initView", "loadAd", "loadGroMoreSplash", "loadTakuSplash"));
        int count = 0;
        for (Method m : cls.getDeclaredMethods()) {
            if (!targets.contains(m.getName()) || m.getReturnType() != void.class) {
                continue;
            }
            final String name = m.getName();
            hook(m).setId("pg:splash:" + name).intercept(chain -> {
                Object frag = chain.getThisObject();
                moduleLog(Log.INFO, TAG, "splash dialog skip " + name + ", dismiss now");
                dismissSplash(frag, 0L);
                dismissSplash(frag, 200L);
                return null;
            });
            count++;
        }
        moduleLog(Log.INFO, TAG, "SplashDialogFragment hooks: " + count);
        if (count == 0) {
            throw new IllegalStateException("SplashDialogFragment methods not matched");
        }
        int sdk = hookPgSplashShow(cl, CLS_PG_SPLASH_AD, "pg:splash:pg");
        sdk += hookPgSplashShow(cl, CLS_PG_TAKU_SPLASH_AD, "pg:splash:taku");
        moduleLog(Log.INFO, TAG, "pg splash show hooks: " + sdk);
    }

    private int hookPgSplashShow(ClassLoader cl, String className, String id) {
        try {
            Class<?> cls = Class.forName(className, false, cl);
            final Field listener = cls.getDeclaredField("mListener");
            listener.setAccessible(true);
            int count = 0;
            for (Method m : cls.getDeclaredMethods()) {
                if (!"showAd".equals(m.getName()) || m.getReturnType() != void.class) {
                    continue;
                }
                hook(m).setId(id + ":showAd").intercept(chain -> {
                    moduleLog(Log.INFO, TAG, "blocked " + className + ".showAd, fire onAdClosed");
                    postOnAdClosed(chain.getThisObject(), listener);
                    return null;
                });
                count++;
            }
            return count;
        } catch (Throwable t) {
            moduleLog(Log.WARN, TAG, "pg splash show hook skip " + className + " -> " + t);
            return 0;
        }
    }

    /** 广告封装里 showAd() 被拦掉时，替它回调一次 onAdClosed()，等价于"广告已关闭"。 */
    private void postOnAdClosed(final Object self, final Field listener) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Object l = listener.get(self);
                if (l == null) {
                    return;
                }
                l.getClass().getMethod("onAdClosed").invoke(l);
            } catch (Throwable t) {
                moduleLog(Log.WARN, TAG, "fire onAdClosed failed -> " + t);
            }
        });
    }

    /** 关闭开屏对话框；dismissAllowingStateLoss 是 Fragment 的公开方法，不依赖 Activity 状态。 */
    private void dismissSplash(final Object fragment, long delay) {
        if (fragment == null) {
            return;
        }
        Runnable r = () -> {
            try {
                Method m = fragment.getClass().getMethod("dismissAllowingStateLoss");
                m.invoke(fragment);
                moduleLog(Log.INFO, TAG, "splash dialog dismissed");
            } catch (Throwable t) {
                moduleLog(Log.WARN, TAG, "splash dismiss failed -> " + t);
            }
        };
        Handler h = new Handler(Looper.getMainLooper());
        if (delay > 0L) {
            h.postDelayed(r, delay);
        } else {
            h.post(r);
        }
    }

    // ------------------------------------------------ 10. 自动扫码（v3.5 新增）

    /**
     * 扫一扫页。291 版的识别结果只有两个入口：
     * 相机走 onResult(HmsScan[])，相册选图走 scanCodeResult(HmsScan)，后者是真实业务分发点
     * （解析 URL 里的 NQT/IMEI/SN → ScanCodeVm.scanV2 → ScanAdxGate → 设备页）。
     */
    private static final String CLS_SCAN_ACT = "com.qiekj.user.ui.activity.scan.HomeScanCodeAct";
    /**
     * 记住的二维码原文。写在目标 App 自己的 SharedPreferences 里：模块与目标不同 uid，
     * 只能读自己包的 prefs，写目标包的 prefs 才不受 SELinux 限制。
     */
    private static final String PREF_AUTO_QR = "pg_auto_qr";
    private static final String KEY_AUTO_QR = "qr";
    /** 设备码键的顺序与 App 内 ComUtils.URLRequest 的判定顺序一致：NQT → IMEI → SN。 */
    private static final Pattern QR_DEVICE = Pattern.compile("[?&](NQT|IMEI|SN)=([^&#]+)");
    /** 直接调 API 的 ViewModel（不经过扫码页，避免出现相机取景框）。 */
    private static final String CLS_SCAN_VM = "com.qiekj.user.viewmodel.ScanCodeVm";
    /** 设备页入口：scanV2 成功后的下一步是 LockingAct.actionStart(ctx, deviceId, false)。 */
    private static final String CLS_LOCKING_ACT = "com.qiekj.user.ui.activity.scan.LockingAct";
    private static final AtomicBoolean sAutoScanOpened = new AtomicBoolean(false);
    /** 扫一扫页是不是我们自动拉起来的：只有自动拉起的这次才把记住的码喂进去，手动扫码不受影响。 */


    private void hookAutoScan(ClassLoader cl) throws Throwable {
        Class<?> cls = Class.forName(CLS_SCAN_ACT, false, cl);
        int count = 0;
        for (Method m : cls.getDeclaredMethods()) {
            String name = m.getName();
            if (!"onResult".equals(name) && !"scanCodeResult".equals(name)) {
                continue;
            }
            hook(m).setId("pg:scan:" + name).intercept(chain -> {
                Object ret = chain.proceed();
                rememberQr(chain.getThisObject(), chain.getArgs().isEmpty() ? null : chain.getArgs().get(0));
                return ret;
            });
            count++;
        }
        moduleLog(Log.INFO, TAG, "HomeScanCodeAct hooks: " + count);
        if (count == 0) {
            throw new IllegalStateException("HomeScanCodeAct scan entry not matched");
        }
    }

    /** 记下任何一次成功识别（相机或相册）的二维码原文，供下次开 App 直接复用。 */
    private void rememberQr(Object scanAct, Object arg) {
        try {
            Object scan = arg instanceof Object[] ? ((Object[]) arg)[0] : arg;
            if (scan == null || !(scanAct instanceof Activity)) {
                return;
            }
            Object value = call(scan, "getOriginalValue");
            if (!(value instanceof String) || ((String) value).isEmpty()) {
                return;
            }
            String qr = (String) value;
            ((Activity) scanAct).getSharedPreferences(PREF_AUTO_QR, Context.MODE_PRIVATE)
                    .edit().putString(KEY_AUTO_QR, qr).apply();
            moduleLog(Log.INFO, TAG, "auto scan remembered: [设备码已隐藏]");
        } catch (Throwable ignore) {
        }
    }

    /** 首页第一次进前台：把记住的二维码自动送进扫一扫（每个进程只做一次）。 */
    private void maybeAutoScan(Activity a) {
        try {
            if (isHomeAct(a)) refreshHomeSession(a);
            if (!Cfg.qiekjScan || !isHomeAct(a) || !sAutoScanOpened.compareAndSet(false, true)) {
                return;
            }
            final Context ctx = a.getApplicationContext();
            String targetVersion = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
            if (!PreferenceRules.verifiedQiekj(targetVersion)) {
                RuntimeEvents.note(a, "compatibility", "当前App版本尚未验证，自动直达已暂停；去广告规则仍按匹配结果执行");
                return;
            }
            // 设置界面里手填的码优先，其次才是以前扫过的那个（Cfg.resolveQr 里已合并）
            final String qr = Cfg.resolveQr(ctx);
            if (qr == null || qr.isEmpty()) {
                moduleLog(Log.INFO, TAG, "auto scan: 还没记住二维码，手动扫一次就会记住");
                return;
            }
            if (!QR_DEVICE.matcher(qr).find()) {
                moduleLog(Log.WARN, TAG, "auto scan: 二维码不是设备码");
                return;
            }
            if (!RuntimeControl.canScan(ctx,qr)) {
                RuntimeEvents.note(a,"auto_paused","该设备连续识别失败，已暂停自动直达；可在模块中恢复");
                android.widget.Toast.makeText(a,"自动直达已暂停，请在模块中恢复",android.widget.Toast.LENGTH_LONG).show();return;
            }
            final Matcher mt = QR_DEVICE.matcher(qr);
            mt.find();
            final String key = mt.group(1);
            final String value = mt.group(2);
            new Handler(Looper.getMainLooper()).postDelayed(() -> autoScanDirect(a, key, value), 150L);
        } catch (Throwable ignore) {
        }
    }

    /**
     * 扫一扫页进前台：不走相机，直接把记住的设备码交给它的 ViewModel。
     * scanV2 的结果由该页自己的 observer 处理（ScanAdxGate → 设备页），与手动扫码完全同一条链路。
     */
    /**
     * v3.7：不打开扫一扫页，直接 new 一个 ScanCodeVm 调 scanV2，观察它的 LiveData，
     * 拿到设备 id 后调 LockingAct.actionStart 进设备页。这样冷启动全程不出现相机取景框。
     * 任何一步不对（拿不到 VM / 超时 / 服务端没给 id / 该码要跳支付宝）都退回旧的扫码页路径。
     */
    private void autoScanDirect(final Activity home, final String key, final String value) {
        sDirectInFlight = true;
        final Object[] live = new Object[1];
        final Object[] proxy = new Object[1];
        final AtomicBoolean done = new AtomicBoolean(false);
        try {
            RuntimeEvents.note(home, "scan_started", "正在识别默认设备码");
            final ClassLoader cl = home.getClassLoader();
            final Object vm = Class.forName(CLS_SCAN_VM, true, cl).getConstructor().newInstance();
            live[0] = call(vm, "getScanV2Bean");
            if (live[0] == null) {
                throw new IllegalStateException("ScanV2Bean missing");
            }
            final Class<?> observerCls = Class.forName("androidx.lifecycle.Observer", true, cl);
            proxy[0] = Proxy.newProxyInstance(cl, new Class<?>[]{observerCls}, (p0, m0, args0) -> {
                if ("onChanged".equals(m0.getName()) && args0 != null && args0.length == 1) {
                    onScanBean(home, args0[0], done, live, proxy);
                }
                if (m0.getName().equals("hashCode")) return System.identityHashCode(p0);
                if (m0.getName().equals("equals")) return p0 == args0[0];
                if (m0.getName().equals("toString")) return "CampusScanObserver";
                return null;
            });
            call(live[0], "observeForever", proxy[0]);
            call(vm, "scanV2", key, value);
            moduleLog(Log.INFO, TAG, "auto scan: 直连 scanV2 " + key + "=[已隐藏]（不开相机）");
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (done.compareAndSet(false, true)) {
                    call(live[0], "removeObserver", proxy[0]);
                    moduleLog(Log.WARN, TAG, "auto scan: 直连超时，停留当前页");
                    stopAutoScan(home);
                }
            }, 8000L);
        } catch (Throwable t) {
            if (done.compareAndSet(false, true)) {
                call(live[0], "removeObserver", proxy[0]);
                moduleLog(Log.WARN, TAG, "auto scan: 直连失败 -> " + t);
                stopAutoScan(home);
            }
        }
    }

    /** scanV2 的回包：拿设备 id 直接进设备页；异常情形交回扫码页。 */
    private void onScanBean(Activity home, Object bean, AtomicBoolean done, Object[] live, Object[] proxy) {
        if (bean == null || !done.compareAndSet(false, true)) {
            return;
        }
        call(live[0], "removeObserver", proxy[0]);
        sDirectInFlight = false;
        String id = null;
        Object raw = call(bean, "getId");
        if (raw instanceof String) {
            id = (String) raw;
        }
        if (id == null || id.isEmpty()) {
            moduleLog(Log.WARN, TAG, "auto scan: 服务端没给设备 id，停留当前页");
            stopAutoScan(home);
            return;
        }
        if (Boolean.TRUE.equals(call(bean, "getJumpToAlipayFlag2"))) {
            moduleLog(Log.WARN, TAG, "auto scan: 这个码要跳支付宝，需手动处理，停留当前页");
            stopAutoScan(home);
            return;
        }
        try {
            if (!startLockingAct(home, id)) {
                throw new IllegalStateException("LockingAct.actionStart not matched");
            }
            sAutoAdvance.set(Cfg.qiekjAdvance);
            sAdvanceTries.set(0);
            RuntimeControl.scanResult(home,true);
            RuntimeEvents.note(home, "scan_success", "已直接进入设备页，未打开相机");
            moduleLog(Log.INFO, TAG, "auto scan: 直达设备页（没经过扫码页、没开相机）");
        } catch (Throwable t) {
            moduleLog(Log.WARN, TAG, "auto scan: 进设备页失败 -> " + t);
            stopAutoScan(home);
        }
    }

    /**
     * 进设备页：LockingAct 的 Companion 与 actionStart 在混淆后名字可能变了，
     * 所以按「字段类型是 Companion」「方法签名是 (Context, String, boolean)」来找，不写死名字。
     */
    private static boolean startLockingAct(Activity home, String id) throws Throwable {
        Class<?> cls = Class.forName(CLS_LOCKING_ACT, true, home.getClassLoader());
        Object companion = null;
        for (Field f : cls.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            if (f.getType().getName().endsWith("Companion")) {
                f.setAccessible(true);
                companion = f.get(null);
                if (companion != null) {
                    break;
                }
            }
        }
        if (companion == null) {
            return false;
        }
        for (Method m : companion.getClass().getDeclaredMethods()) {
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length == 3
                    && Context.class.isAssignableFrom(pt[0])
                    && pt[1] == String.class
                    && (pt[2] == boolean.class || pt[2] == Boolean.class)) {
                m.setAccessible(true);
                m.invoke(companion, home, id, Boolean.FALSE);
                return true;
            }
        }
        return false;
    }

    /** Automatic failures stay on the current page; only a deliberate manual scan opens a camera. */
    private void stopAutoScan(Activity home) {
        sDirectInFlight = false;
        sAutoAdvance.set(false); sAdvanceTries.set(0);
        if (home != null) RuntimeControl.scanResult(home,false);
        RuntimeEvents.note(home, "scan_failed", "自动识别未完成，已停留当前页；请检查网络和设备码，或手动扫码");
        if (home != null && !home.isFinishing() && !home.isDestroyed())
            android.widget.Toast.makeText(home, "自动识别未完成，请检查网络或设备码", android.widget.Toast.LENGTH_LONG).show();
    }

    /** 沿类继承链找方法并调用，不依赖编译期类型（目标类是 App 私有的）。 */
    private static Object call(Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                    return m.invoke(target, args);
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    /** 沿类继承链写字段（deviceParams 是 Kotlin 私有的普通字段）。 */
    private static void setField(Object target, String name, Object value) {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable ignore) {
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // 11. 自动推进到订单预览（v3.6）
    // ------------------------------------------------------------------
    private static final String CLS_HAIR_SKU = "com.qiekj.user.ui.activity.scan.hair.HairGoodsSkuNewAct";
    /** 只有「本次是自动扫码进来的」才替用户按「立即使用」；手动扫码 / 手动进设备页永不代按。 */
    private static final AtomicBoolean sAutoAdvance = new AtomicBoolean(false);
    private static final AtomicInteger sAdvanceTries = new AtomicInteger(0);

    private void hookAutoAdvance(ClassLoader cl) throws Exception {
        Class<?> cls = Class.forName(CLS_HAIR_SKU, false, cl);
        int n = 0;
        for (Method m : cls.getDeclaredMethods()) {
            String name = m.getName();
            if (!name.equals("initView") && !name.equals("initPrice")
                    && !name.equals("initAdapter") && !name.equals("onResume")) {
                continue;
            }
            hook(m).setId("pg:advance:" + name).intercept(chain -> {
                Object result = chain.proceed();
                advanceDevicePage((Activity) chain.getThisObject());
                return result;
            });
            n++;
        }
        // 1.144.0 起 initView$lambda$15 里的设备校验会改选中项，直接在 toOrder 入口再兜一次。
        try {
            Method order = cls.getDeclaredMethod("toOrder");
            hook(order).setId("pg:advance:toOrder").intercept(chain -> {
                try {
                    DeviceDefaults.fixBeforeOrder((Activity) chain.getThisObject());
                } catch (Throwable ignore) {
                }
                return chain.proceed();
            });
            n++;
        } catch (Throwable missing) {
            moduleLog(Log.WARN, TAG, "toOrder hook skipped -> " + missing);
        }
        if (n == 0) {
            throw new IllegalStateException("no HairGoodsSkuNewAct entry to watch");
        }
        moduleLog(Log.INFO, TAG, "HairGoodsSkuNewAct advance hooks: " + n);
    }

    /** 设备页每次刷新都来看一眼：只有 2 号机 + 最低档都落定后才按。 */
    private void advanceDevicePage(Activity a) {
        if (!Cfg.qiekjAdvance) return;
        try {
            if (a == null || !CLS_HAIR_SKU.equals(a.getClass().getName())) {
                return;
            }
            if (!sAutoAdvance.get() || a.isFinishing()) {
                return;
            }
            if (sAdvanceTries.get() >= 8) {
                return;
            }
            new Handler(Looper.getMainLooper()).postDelayed(() -> tryPressSubmit(a), 700L);
        } catch (Throwable ignore) {
        }
    }

    private void tryPressSubmit(Activity a) {
        if (!Cfg.qiekjAdvance || !sAutoAdvance.get() || a == null || a.isFinishing()) {
            return;
        }
        if (!CLS_HAIR_SKU.equals(a.getClass().getName())) {
            return;
        }
        try {
            if (Boolean.TRUE.equals(AppReflect.get(a, "submitOrder"))) {
                return; // 已经在提交，别重复点
            }
            // 1.144.0 默认选左边 1 号 + 第一档，先按设置把机器/档位改对再按。
            if (!DeviceDefaults.fixBeforeOrder(a)) {
                retryAdvance(a); // 机器或档位还没落定，等下一次刷新
                return;
            }
            int tier = ((Number) AppReflect.get(a, "timePosition")).intValue();
            Object binding = AppReflect.call(a, "getMBinding");
            if (binding == null) {
                retryAdvance(a);
                return;
            }
            if (tier < 0) {
                retryAdvance(a);
                return;
            }
            View submit = (View) AppReflect.get(binding, "tvSubmit");
            if (submit == null || !submit.isEnabled() || !submit.isShown()) {
                retryAdvance(a);
                return;
            }
            if (!DeviceDefaults.checkAutomationQuote(a)) {
                sAutoAdvance.set(false);
                RuntimeEvents.note(a,"price_changed","报价或档位需要核对，已停留设备页；请手动核对，若有新报价可在模块中确认");
                android.widget.Toast.makeText(a,"报价需核对，已停留设备页",android.widget.Toast.LENGTH_LONG).show();return;
            }
            sAutoAdvance.set(false);
            moduleLog(Log.INFO, TAG, "auto advance: 机器/档位已按设置落定，替用户点一次「下一步」（不点去支付）");
            sPreviewUntil = android.os.SystemClock.elapsedRealtime()+15_000L;
            submit.performClick();
        } catch (Throwable t) {
            moduleLog(Log.WARN, TAG, "auto advance skipped -> " + t);
            sAutoAdvance.set(false);
        }
    }

    private void retryAdvance(Activity a) {
        if (sAdvanceTries.incrementAndGet() >= 8) {
            sAutoAdvance.set(false);
            moduleLog(Log.WARN, TAG, "auto advance: 设备页没等到就绪，留给用户手动点");
            return;
        }
        advanceDevicePage(a);
    }

    private void noteOrderPreview(Activity a) {
        try {
            if (a == null || !CLS_ORDER_PREVIEW.equals(a.getClass().getName())) {
                return;
            }
            if (sAutoAdvance.getAndSet(false) || (sPreviewUntil>android.os.SystemClock.elapsedRealtime())) {
                sPreviewUntil=0;
                RuntimeEvents.note(a, "preview_ready", "已到订单预览，等待手动付款");
                moduleLog(Log.INFO, TAG, "auto advance: 已到订单预览页，后面只等你点「去支付」");
            }
        } catch (Throwable ignore) {
        }
    }

    private static volatile boolean sDirectInFlight;
    private static volatile long sPreviewUntil;
    private static String sLaunchToken = "";
    private static boolean sExplicitSession;
    private static final Map<Activity,Long> sHomeSync = Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private void refreshHomeSession(Activity a) {
        long now=android.os.SystemClock.elapsedRealtime();Long previous=sHomeSync.get(a);
        if(sDirectInFlight || (previous!=null && now-previous<700))return;
        sHomeSync.put(a,now);
        String token=ConfigBridge.resume(a,sExplicitSession);
        if(!token.isEmpty()&&!token.equals(sLaunchToken)){
            sLaunchToken=token;sExplicitSession=true;sAutoScanOpened.set(false);sAutoAdvance.set(false);sAdvanceTries.set(0);
            RuntimeEvents.note(a,"profile_launch","已应用本次设备档案；最终付款由用户确认");
        }
        moduleLog(Log.INFO,TAG,"home mode="+Cfg.automationMode+" selections="+Cfg.qiekjSelections+" scan="+Cfg.qiekjScan+" advance="+Cfg.qiekjAdvance);
    }
}

