package com.campus.adblock;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.hardware.Sensor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedModule;

/**
 * 智趣校园（趣智校园，{@code com.klcxkj.zqxy}）去广告。
 *
 * <p>这一组规则来自对实际在手机上跑通的 {@code 智趣校园去广告_1.0.apk}
 * （{@code com.zqxy.adblock}，module.prop 里 version 1.1 / versionCode 2）的反编译比对：
 * 资源名、类名、延迟毫秒、传感器类型、屏蔽用的包名与 scheme 都与原版一致，没有自行猜测。
 * 相对原版只多了一层 {@link Cfg} 开关判断。
 */
public final class ZqxyHooks {

    /** 主界面，开屏被跳过后直接进这里。 */
    private static final String MAIN_ACT = "com.klcxkj.zqxy.ui.main.MainActivity";
    private static final String SPLASH_ACT = "com.klcxkj.zqxy.ui.splash.SplashActivity";
    private static final String TOPON_SPLASH_ACT = "com.klcxkj.zqxy.ui.splash.TopOnSplashActivity";
    /** TopOn 联盟自带的开屏容器，类名前缀固定。 */
    private static final String TOPON_SPLASH_PREFIX = "com.anythink.core.common.inner.ui.activity.";

    /** 底部这两个 id 的父容器要一起隐藏。 */
    private static final Set<String> TAB_IDS = new HashSet<>(Arrays.asList("main_scan", "main_shopping"));

    /** 精确命中的广告容器 id。 */
    private static final Set<String> AD_IDS = new HashSet<>(Arrays.asList(
            "privacy_adv_recommendation_tv",
            "home_banner_iv",
            "home_banner_view",
            "home_activity_rv",
            "home_meal_layout",
            "home_meal_rv",
            "home_hz_parent_layout",
            "home_hz_container_layout",
            "top_adv_layout"));

    /** 广告视图名里的特征片段（原版是 startsWith / contains 判断）。 */
    private static final String AD_PREFIX = "task_adv_";
    private static final String AD_PART_JUTUI = "ju_tui_";
    private static final String AD_PART_TIANMU = "tian_mu";
    private static final String AD_PART_MID = "_adv_";

    /** 误触会拉起的电商 App（原版 18 个）。 */
    private static final Set<String> AD_PKGS = new HashSet<>(Arrays.asList(
            "com.taobao.taobao", "com.taobao.idlefish", "com.taobao.litetaobao", "com.tmall.wireless",
            "com.jingdong.app.mall", "com.xunmeng.pinduoduo", "com.ss.android.ugc.aweme",
            "com.ss.android.ugc.aweme.lite", "com.ss.android.ugc.live", "com.smile.gifmaker",
            "com.kuaishou.nebula", "com.xingin.xhs", "com.zhihu.android", "com.sankuai.meituan",
            "com.dianping.v1", "com.taobao.trip", "cn.damai", "com.alibaba.wireless"));

    /** 误触 scheme（原版 16 个）。 */
    private static final Set<String> AD_SCHEMES = new HashSet<>(Arrays.asList(
            "taobao", "tbopen", "tmall", "tmopen", "jdmobile", "openapp.jdmobile", "pinduoduo",
            "snssdk1128", "kwai", "kwai-pro", "ksnebula", "xhsdiscover", "zhihu", "dianping",
            "imeituan", "damai"));

    /** 摇一摇广告用的传感器：加速度、陀螺仪、显著运动。 */
    private static final int[] SHAKE_SENSOR_TYPES = {Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE, Sensor.TYPE_SIGNIFICANT_MOTION};

    /** id → 资源名缓存，避免每次重扫都走 getResourceEntryName。 */
    private static final Map<Integer, String> NAME_CACHE = new ConcurrentHashMap<>();
    /** 已经打过日志的资源名，防止刷屏。 */
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    private static final String TAG = "CampusAdBlock";
    private static volatile XposedModule MODULE;
    private static volatile boolean installed;

    private ZqxyHooks() {
    }

    private static void log(int level, String message) {
        if (!Cfg.logEnabled) return;
        XposedModule module = MODULE;
        if (module != null) module.log(level, TAG, message);
    }

    /** 热重载后允许重新装 hook（与原版 onHotReloading 行为一致）。 */
    public static void reset() {
        installed = false;
        LOGGED.clear();
    }

    // ---------------------------------------------------------------- 安装

    public static void install(XposedModule module, ClassLoader cl) {
        MODULE = module;
        if (installed) {
            log(4, "zqxy: hooks already installed, skip");
            return;
        }
        installed = true;
        log(4, "zqxy: installHooks begin, cl=" + cl);

        step(module, "View.setVisibility", () -> hookSetVisibility(module));
        step(module, "Activity.onCreate", () -> hookLifecycle(module, "onCreate"));
        step(module, "Activity.onResume", () -> hookLifecycle(module, "onResume"));
        if (Cfg.blockShake) {
            step(module, "SensorManager.registerListener", () -> hookSensors(module));
        }
        if (Cfg.blockJump || Cfg.zqxyAds) {
            step(module, "Instrumentation.execStartActivity", () -> hookJumps(module));
        }
        if (Cfg.zqxyAds) {
            step(module, "TopOn ATSplashAd", () -> hookAtSplash(module, cl));
        }
        log(4, "zqxy: installHooks done");
    }

    private static void step(XposedModule module, String name, ThrowingRunnable body) {
        try {
            body.run();
            HealthReporter.step(name,true);
            log(4, "zqxy: [ok] " + name);
        } catch (Throwable error) {
            HealthReporter.step(name,false);
            log(5, "zqxy: [skip] " + name + " -> " + error);
        }
    }

    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    // ------------------------------------------------------- setVisibility

    private static void hookSetVisibility(XposedModule module) throws Exception {
        module.hook(View.class.getDeclaredMethod("setVisibility", int.class))
                .setId("campus:zqxy:setVisibility")
                .intercept(chain -> {
                    Object self = chain.getThisObject();
                    Object arg = chain.getArg(0);
                    if (!(self instanceof View) || !(arg instanceof Integer)) return chain.proceed();
                    if (((Integer) arg) == View.GONE) return chain.proceed();
                    View view = (View) self;
                    String name = nameOf(view);
                    if (Cfg.zqxyTab && name != null && TAB_IDS.contains(name)) {
                        try {
                            Object parent = view.getParent();
                            if (parent instanceof View) {
                                View holder = (View) parent;
                                if (holder.getVisibility() != View.GONE) {
                                    new Handler(Looper.getMainLooper()).post(() -> hide(holder));
                                }
                            }
                        } catch (Throwable ignored) {
                            // 父容器拿不到就算了，别影响 App 自己的布局
                        }
                    }
                    if (Cfg.zqxyAds && isAdView(view)) {
                        return chain.proceed(new Object[]{View.GONE});
                    }
                    return chain.proceed();
                });
    }

    // ----------------------------------------------------------- 生命周期

    private static void hookLifecycle(XposedModule module, String method) throws Exception {
        Method target = "onCreate".equals(method)
                ? Activity.class.getDeclaredMethod("onCreate", Bundle.class)
                : Activity.class.getDeclaredMethod("onResume");
        module.hook(target)
                .setId("campus:zqxy:" + method)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Object self = chain.getThisObject();
                    if (self instanceof Activity) {
                        Activity activity = (Activity) self;
                        CampusAdBlockModule.beatAsync(activity, activity.getPackageName());
                        skipSplash(module, activity);
                        scanLater(activity, 0L);
                        scanLater(activity, 400L);
                        scanLater(activity, 1000L);
                    }
                    return result;
                });
    }

    /** 开屏：SplashActivity 等 1 秒，TopOn/AnyThink 容器只等 60ms，然后直接进主页。 */
    private static void skipSplash(XposedModule module, Activity activity) {
        if (!Cfg.zqxyAds) return;
        String name = activity.getClass().getName();
        if (SPLASH_ACT.equals(name)) {
            finishSplash(module, activity, 1000L, false);
        } else if (TOPON_SPLASH_ACT.equals(name) || name.startsWith(TOPON_SPLASH_PREFIX)) {
            finishSplash(module, activity, 60L, true);
        }
    }

    private static void finishSplash(XposedModule module, Activity activity, long delay, boolean topOn) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (topOn) {
                    if (activity.isFinishing()) return;
                    boolean root = activity.isTaskRoot();
                    Intent intent = null;
                    if (root) {
                        intent = new Intent();
                        intent.setClassName(activity, MAIN_ACT);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    }
                    activity.finish();
                    log(4, "zqxy: topon splash activity finished, taskRoot=" + root);
                    if (intent != null) activity.getApplicationContext().startActivity(intent);
                } else {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    Intent intent = new Intent();
                    intent.setClassName(activity, MAIN_ACT);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    activity.startActivity(intent);
                    activity.finish();
                    log(4, "zqxy: splash skipped after 1000ms");
                }
            } catch (Throwable error) {
                log(5, "zqxy: skip splash failed: " + error);
            }
        }, delay);
    }

    // ------------------------------------------------------------- 扫树

    private static void scanLater(Activity activity, long delay) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                View root = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
                if (root != null) sweep(root);
            } catch (Throwable ignored) {
                // 页面正在销毁时 getWindow() 会抛，忽略
            }
        }, delay);
    }

    /** 递归整棵树：tab 父容器隐藏，广告容器隐藏。 */
    private static void sweep(View view) {
        if (view == null) return;
        try {
            String name = nameOf(view);
            if (Cfg.zqxyTab && name != null && TAB_IDS.contains(name)) {
                try {
                    Object parent = view.getParent();
                    if (parent instanceof View) {
                        View holder = (View) parent;
                        if (holder.getVisibility() != View.GONE) holder.setVisibility(View.GONE);
                    }
                } catch (Throwable ignored) {
                    // 同上
                }
            }
            if (Cfg.zqxyAds && view.getVisibility() != View.GONE && isAdView(view)) {
                view.setVisibility(View.GONE);
            }
        } catch (Throwable ignored) {
            // 单个节点出错不影响整棵树
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int count = group.getChildCount();
            for (int i = 0; i < count; i++) {
                try {
                    sweep(group.getChildAt(i));
                } catch (Throwable ignored) {
                    // 同上
                }
            }
        }
    }

    private static void hide(View view) {
        try {
            if (view.getVisibility() != View.GONE) view.setVisibility(View.GONE);
        } catch (Throwable ignored) {
            // 视图已 detach
        }
    }

    // ------------------------------------------------------- 视图名判定

    private static String nameOf(View view) {
        int id = view.getId();
        if (id == View.NO_ID) return null;
        String cached = NAME_CACHE.get(id);
        if (cached != null) return cached.isEmpty() ? null : cached;
        String name = null;
        try {
            name = view.getResources().getResourceEntryName(id);
        } catch (Throwable ignored) {
            // aapt 生成以外的 id
        }
        NAME_CACHE.put(id, name == null ? "" : name);
        return name;
    }

    private static boolean isAdView(View view) {
        String name = nameOf(view);
        if (name == null) return false;
        boolean hit = AD_IDS.contains(name)
                || name.startsWith(AD_PREFIX)
                || name.contains(AD_PART_JUTUI)
                || name.contains(AD_PART_TIANMU)
                || name.contains(AD_PART_MID);
        if (hit && LOGGED.add(name)) {
            log(4, "zqxy: ad view hidden: " + name);
        }
        return hit;
    }

    // ---------------------------------------------------------- 摇一摇

    private static void hookSensors(XposedModule module) throws Exception {
        int hooked = 0;
        for (Method method : Class.forName("android.hardware.SensorManager").getDeclaredMethods()) {
            if (!"registerListener".equals(method.getName())) continue;
            if (indexOf(method, Sensor.class) < 0) continue;
            final boolean isVoid = method.getReturnType() == void.class;
            module.hook(method)
                    .setId("campus:zqxy:registerListener:" + Arrays.toString(method.getParameterTypes()))
                    .intercept(chain -> {
                        for (Object arg : chain.getArgs()) {
                            if (!(arg instanceof Sensor)) continue;
                            int type = ((Sensor) arg).getType();
                            for (int blocked : SHAKE_SENSOR_TYPES) {
                                if (type != blocked) continue;
                                log(4, "zqxy: blocked sensor register, type=" + type);
                                return isVoid ? null : Boolean.TRUE;
                            }
                        }
                        return chain.proceed();
                    });
            hooked++;
        }
        if (hooked == 0) throw new IllegalStateException("no registerListener hook installed");
        log(4, "zqxy: sensor hooks: " + hooked);
    }

    // -------------------------------------------------------- 误触跳转

    private static void hookJumps(XposedModule module) throws Exception {
        JumpPolicy.install(module, "campus:zqxy:jumps");
    }

    private static int indexOf(Method method, Class<?> type) {
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            if (types[i] == type) return i;
        }
        return -1;
    }

    /** 命中就返回命中的说明文字，不命中返回 null。 */
    private static String blockedJump(Intent intent) {
        try {
            ComponentName component = intent.getComponent();
            if (component != null && component.getPackageName() != null
                    && AD_PKGS.contains(component.getPackageName())) {
                return "component:" + component.getPackageName();
            }
            String pkg = intent.getPackage();
            if (pkg != null && AD_PKGS.contains(pkg)) return "package:" + pkg;
            Uri data = intent.getData();
            String scheme = data == null ? null : data.getScheme();
            if (scheme != null && AD_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
                return "scheme:" + scheme + " -> " + data;
            }
        } catch (Throwable ignored) {
            // 某些 Intent 取字段会抛，按不命中处理
        }
        return null;
    }

    // ---------------------------------------------------------- 开屏 SDK

    private static void hookAtSplash(XposedModule module, ClassLoader cl) throws Exception {
        Class<?> cls = Class.forName("com.anythink.splashad.api.ATSplashAd", false, cl);
        int hooked = 0;
        for (Method method : cls.getDeclaredMethods()) {
            String name = method.getName();
            if ("isAdReady".equals(name) && method.getReturnType() == boolean.class) {
                module.hook(method)
                        .setId("campus:zqxy:atsplash:isAdReady")
                        .intercept(chain -> Boolean.FALSE);
                hooked++;
            } else if (name.startsWith("show")) {
                module.hook(method)
                        .setId("campus:zqxy:atsplash:" + name)
                        .intercept(chain -> {
                            log(4, "zqxy: blocked ATSplashAd." + name);
                            return null;
                        });
                hooked++;
            }
        }
        log(4, "zqxy: ATSplashAd hooks: " + hooked + "/" + cls.getDeclaredMethods().length);
        if (hooked == 0) throw new IllegalStateException("ATSplashAd methods not matched");
    }
}
