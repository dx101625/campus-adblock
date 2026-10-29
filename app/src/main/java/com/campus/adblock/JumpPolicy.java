package com.campus.adblock;

import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import io.github.libxposed.api.XposedModule;

/** Cancels advertising launch requests before the ROM's cross-app confirmation. */
public final class JumpPolicy {
    public static final Map<String, String> DESTINATIONS = new LinkedHashMap<>();
    private static final Map<String, String> SCHEMES = new LinkedHashMap<>();
    static {
        add("com.taobao.taobao", "淘宝", "taobao", "tbopen");
        add("com.taobao.idlefish", "闲鱼", "fleamarket");
        add("com.taobao.litetaobao", "淘宝特价版", "taobaolite");
        add("com.tmall.wireless", "天猫", "tmall", "tmopen");
        add("com.jingdong.app.mall", "京东", "jdmobile", "openapp.jdmobile");
        add("com.xunmeng.pinduoduo", "拼多多", "pinduoduo");
        add("com.ss.android.ugc.aweme", "抖音", "snssdk1128");
        add("com.ss.android.ugc.aweme.lite", "抖音极速版", "snssdk2329");
        add("com.ss.android.ugc.live", "抖音火山版", "snssdk1112");
        add("com.smile.gifmaker", "快手", "kwai", "kwai-pro");
        add("com.kuaishou.nebula", "快手极速版", "ksnebula");
        add("com.xingin.xhs", "小红书", "xhsdiscover");
        add("com.zhihu.android", "知乎", "zhihu");
        add("com.sankuai.meituan", "美团", "imeituan", "meituanwaimai");
        add("com.dianping.v1", "大众点评", "dianping");
        add("com.taobao.trip", "飞猪", "fliggy");
        add("cn.damai", "大麦", "damai");
        add("com.alibaba.wireless", "1688", "ali1688");
    }
    private JumpPolicy() {}
    private static void add(String pkg, String label, String... schemes) {
        DESTINATIONS.put(pkg, label); for (String s : schemes) SCHEMES.put(s, pkg);
    }
    public static String destination(Intent intent) { return destination(intent, 0); }
    private static String destination(Intent intent, int depth) {
        if (intent == null || depth > 2) return null;
        if (intent.getComponent() != null && DESTINATIONS.containsKey(intent.getComponent().getPackageName())) return intent.getComponent().getPackageName();
        if (DESTINATIONS.containsKey(intent.getPackage())) return intent.getPackage();
        Uri data = intent.getData(); String scheme = data == null ? null : data.getScheme();
        String pkg = scheme == null ? null : SCHEMES.get(scheme.toLowerCase(Locale.ROOT));
        if (pkg == null && intent.getSelector() != null && intent.getSelector() != intent) return destination(intent.getSelector(), depth + 1);
        return pkg;
    }
    private static boolean advertisingCaller() {
        for (StackTraceElement f : Thread.currentThread().getStackTrace()) {
            String n = f.getClassName();
            for (String prefix : new String[]{"com.bytedance.", "com.byaztp.", "com.anythink.", "com.qq.e.ads.",
                    "com.kwad.", "com.qiekeji.pgad.", "com.jutui.ads.", "com.yd.saas.", "com.sigmob.", "com.miui.zeus.mimo."})
                if (n.startsWith(prefix)) return true;
        }
        return false;
    }
    private static boolean block(Intent intent, boolean advertising, android.content.Context context) {
        android.content.ComponentName component=intent==null?null:intent.getComponent();
        if(context!=null&&component!=null&&context.getPackageName().equals(component.getPackageName())
                && AdActivityRules.isSdkActivity(component.getClassName())
                && (("com.qiekj.user".equals(context.getPackageName())&&Cfg.qiekjAds)||("com.klcxkj.zqxy".equals(context.getPackageName())&&Cfg.zqxyAds))){
            ModuleLog.i("CampusAdBlock","blocked SDK ad activity before launch: "+component.getClassName());
            RuntimeEvents.note(context,"ad_blocked","已阻止全屏广告页面启动");return true;
        }
        String pkg = destination(intent);
        if (!PreferenceRules.blockDestination(Cfg.blockJump, pkg != null,
                PreferenceRules.packageSet(Cfg.jumpAllowed).contains(pkg), "strict".equals(Cfg.jumpMode), advertising)) return false;
        if (context != null && RuntimeControl.consumeGrant(context,pkg)) {
            RuntimeEvents.note(context,"jump_allowed","已按临时允许放行到"+DESTINATIONS.get(pkg)); return false;
        }
        ModuleLog.i("CampusAdBlock", "blocked ad jump: " + pkg + " mode=" + Cfg.jumpMode);
        RuntimeControl.jumpBlocked(context,pkg);
        return true;
    }
    public static void install(XposedModule module, String prefix) throws Exception {
        int count = 0;
        for (Method method : Instrumentation.class.getDeclaredMethods()) {
            if (!method.getName().equals("execStartActivity") && !method.getName().equals("execStartActivities")) continue;
            Class<?>[] types = method.getParameterTypes(); int at = -1;
            for (int i=0;i<types.length;i++) if (types[i] == Intent.class || types[i] == Intent[].class) { at=i; break; }
            if (at < 0) continue;
            final int index = at;
            module.hook(method).setId(prefix + ":" + method.getName() + ":" + types.length).intercept(chain -> {
                Object arg = chain.getArg(index); boolean ad = advertisingCaller();
                android.content.Context context = null;
                for (Object item : chain.getArgs()) if (item instanceof android.content.Context) { context = (android.content.Context) item; break; }
                if (context == null) context = CampusAdBlockModule.currentActivity();
                if (arg instanceof Intent && block((Intent) arg, ad, context)) return null;
                if (arg instanceof Intent[]) {
                    List<Intent> kept = new ArrayList<>();
                    for (Intent item : (Intent[]) arg) if (!block(item, ad, context)) kept.add(item);
                    if (kept.isEmpty()) return null;
                    if (kept.size() != ((Intent[]) arg).length) {
                        Object[] args = chain.getArgs().toArray(); args[index] = kept.toArray(new Intent[0]); return chain.proceed(args);
                    }
                }
                return chain.proceed();
            });
            count++;
        }
        if (count == 0) throw new IllegalStateException("no activity launch methods matched");
        ModuleLog.i("CampusAdBlock", "jump policy ready, hooks=" + count + " mode=" + Cfg.jumpMode);
    }
}
