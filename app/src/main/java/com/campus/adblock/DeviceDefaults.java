package com.campus.adblock;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Defaults and selection restoration. Never presses Next, creates an order, or starts equipment. */
final class DeviceDefaults {
    private static final String TAG = "CampusAdBlock";
    private static final String HAIR = "com.qiekj.user.ui.activity.scan.hair.HairGoodsSkuNewAct";
    private static final Map<Activity, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final class State {
        boolean ready;
        boolean pickedMachine;
        boolean busy;
        boolean userChanged;
        boolean unavailable;
        double preferredDuration = Double.NaN;
    }

    static void install(CampusAdBlockModule module, ClassLoader loader) throws Exception {
        Class<?> cls = Class.forName(HAIR, false, loader);
        // 自动营销横幅/弹窗的渲染回调：有就挂，名字变了就跳过，不能连累后面的选区逻辑。
        int marketing = 0;
        for (String callback : new String[]{"createObserver$lambda$32", "createObserver$lambda$33",
                "initData$lambda$24", "initData$lambda$25"}) {
            try {
                Method ad = AppReflect.named(cls, callback, 2);
                module.hook(ad).setId("pg:hair:marketing:" + callback).intercept(chain -> {
                    if (!Cfg.qiekjAds) return chain.proceed();
                    ModuleLog.i(TAG, "automatic hair marketing suppressed: " + callback);
                    return chain.proceed(new Object[]{chain.getArg(0), null});
                });
                marketing++;
            } catch (Throwable ignored) {
                // 这个版本没有该 lambda，忽略
            }
        }
        Method init = cls.getDeclaredMethod("initView", Bundle.class);
        module.hook(init).setId("pg:defaults:hairInit").intercept(chain -> {
            Object result = chain.proceed();
            Activity activity = (Activity) chain.getThisObject();
            state(activity).ready = true;
            apply(activity);
            return result;
        });
        Method price = cls.getDeclaredMethod("initPrice", boolean.class);
        module.hook(price).setId("pg:defaults:hairPrice").intercept(chain -> {
            Object result = chain.proceed();
            if (Boolean.TRUE.equals(chain.getArg(0))) apply((Activity) chain.getThisObject());
            return result;
        });
        // 列表建好后补一次：数据可能先到、Adapter 后建（1.144.0 的 initAdapter 是私有无参方法）。
        try {
            Method adapter = cls.getDeclaredMethod("initAdapter");
            module.hook(adapter).setId("pg:defaults:hairAdapter").intercept(chain -> {
                Object result = chain.proceed();
                apply((Activity) chain.getThisObject());
                return result;
            });
        } catch (Throwable ignored) {
        }
        for (Method choice : cls.getDeclaredMethods()) {
            final String name = choice.getName();
            if (!name.equals("initView$lambda$16") && !name.equals("initView$lambda$17")
                    && !name.equals("initAdapter$lambda$38") && !name.equals("initAdapter$lambda$39")) {
                continue;
            }
            module.hook(choice).setId("pg:defaults:userDevice:" + name).intercept(chain -> {
                Activity a = (Activity) chain.getArg(0);
                State s = state(a);
                if (s.busy) return chain.proceed();
                s.userChanged = true;
                s.busy = true;
                try {
                    Object result = chain.proceed();
                    try {
                        if (name.startsWith("initAdapter")) {
                            Object adapter = AppReflect.get(a, "mAdapter");
                            List<?> prices = (List<?>) AppReflect.call(adapter, "getData");
                            int index = ((Number) AppReflect.get(a, "timePosition")).intValue();
                            boolean pulse = Boolean.TRUE.equals(AppReflect.get(a, "isPulse"));
                            s.preferredDuration = number(prices.get(index), pulse ? "getRealTime" : "getTime");
                            ModuleLog.i(TAG, "defaults: remembered manual tier duration=" + s.preferredDuration);
                        }
                    } catch (Exception error) {
                        ModuleLog.w(TAG, "defaults: manual tier recording skipped", error);
                    }
                    return result;
                } finally {
                    s.busy = false;
                    if (!name.startsWith("initAdapter")) apply(a);
                }
            });
        }
        // initPrice 会被内联进这些版本匹配的调用方，必须去优化。
        for (Method m : cls.getDeclaredMethods()) {
            String name = m.getName();
            if (name.equals("initView") || name.equals("createObserver") || name.equals("initAdapter")
                    || name.equals("toOrder") || name.startsWith("createObserver$lambda$")
                    || name.startsWith("initAdapter$lambda$") || name.equals("initView$lambda$16")
                    || name.equals("initView$lambda$17") || name.equals("initPrice$default")) {
                module.deoptimize(m);
            }
        }
        ModuleLog.i(TAG, "defaults: hair hooks ready, marketing=" + marketing);
    }

    private static State state(Activity a) {
        State s = STATES.get(a);
        if (s == null) { s = new State(); STATES.put(a, s); }
        return s;
    }

    private static void apply(Activity activity) {
        if (!Cfg.qiekjSelections) return;
        State s = state(activity);
        if (!s.ready || s.busy || s.unavailable || activity.isFinishing()) return;
        s.busy = true;
        try {
            Object binding = AppReflect.call(activity, "getMBinding");
            if (!s.pickedMachine && !s.userChanged && !pickMachine(activity, s, binding)) {
                return;
            }
            Object adapter = AppReflect.get(activity, "mAdapter");
            if (adapter == null) return; // Shop information arrives asynchronously.
            List<?> prices = (List<?>) AppReflect.call(adapter, "getData");
            boolean pulse = Boolean.TRUE.equals(AppReflect.get(activity, "isPulse"));
            // 档位偏好：shortest = 最短时长（默认，最便宜）；longest = 最长时长
            final boolean longest = "longest".equals(Cfg.qiekjTier);
            int best = -1;
            double bestMetric = Double.POSITIVE_INFINITY;
            double cheapest = Double.POSITIVE_INFINITY;
            double chosen = Double.NaN;
            for (int i = 0; i < prices.size(); i++) {
                Object item = prices.get(i);
                if (!Boolean.TRUE.equals(AppReflect.call(item, "getStatus"))) continue;
                double duration = number(item, pulse ? "getRealTime" : "getTime");
                double price = number(item, "getPrice");
                if (s.userChanged && Double.isFinite(s.preferredDuration)
                        && Math.abs(duration - s.preferredDuration) > 0.000001) continue;
                if (!Double.isFinite(duration) || !Double.isFinite(price) || duration <= 0) continue;
                double metric = longest ? -duration : duration;
                if (metric < bestMetric || (metric == bestMetric && price < cheapest)) {
                    best = i; bestMetric = metric; cheapest = price; chosen = duration;
                }
            }
            if (best < 0) return;
            selectTier(activity, adapter, prices, best);
            s.preferredDuration = chosen;
            final int selected = best;
            final double chosenDuration = chosen;
            View list = (View) get(binding, "rvSelectTime");
            if (list != null) {
                list.post(() -> {
                    try { AppReflect.call(list, "scrollToPosition", selected); }
                    catch (Exception e) { ModuleLog.w(TAG, "defaults: list positioning skipped", e); }
                });
            }
            ModuleLog.i(TAG, "defaults: " + (s.userChanged ? "restored manual tier"
                            : (longest ? "longest available tier" : "lowest available tier"))
                    + " index=" + best + " duration=" + chosenDuration
                    + " price=" + cheapest + " actualIndex=" + AppReflect.get(activity, "timePosition")
                    + "; no submit action");
        } catch (Throwable error) {
            ModuleLog.w(TAG, "defaults: device selection failed", error);
        } finally {
            s.busy = false;
        }
    }

    /**
     * 按设置里的「默认机器」选一台。返回 true 表示可以继续选档位。
     * right2（默认）：只点右边 2 号机；left1：点左边 1 号机；
     * auto：有两台就选右 2，只有一台就选左 1；none：完全不选，交给用户。
     */
    private static boolean pickMachine(Activity activity, State s, Object binding) {
        String mode = Cfg.qiekjMachine == null ? "right2" : Cfg.qiekjMachine;
        if ("none".equals(mode)) {
            s.unavailable = true;
            ModuleLog.i(TAG, "defaults: automatic machine pick disabled by settings; waiting for a manual tap");
            return false;
        }
        List<?> skus = (List<?>) get(activity, "mSkus");
        boolean two = skus != null && skus.size() >= 2;
        if ("auto".equals(mode)) {
            if (two && clickMachine(activity, s, binding, "tvRight", "2", 1, true)) return true;
            return clickMachine(activity, s, binding, "tvLeft", "1", 0, false);
        }
        if ("left1".equals(mode)) {
            return clickMachine(activity, s, binding, "tvLeft", "1", 0, false);
        }
        if (!two) {
            s.unavailable = true;
            ModuleLog.w(TAG, "defaults: only one machine on this shop; nothing auto-selected");
            Toast.makeText(activity, "这台设备没有 2 号机，请手动选择", Toast.LENGTH_LONG).show();
            return false;
        }
        return clickMachine(activity, s, binding, "tvRight", "2", 1, false);
    }

    private static boolean clickMachine(Activity activity, State s, Object binding,
                                        String field, String label, int expected, boolean quiet) {
        TextView button = null;
        try {
            button = (TextView) get(binding, field);
        } catch (Throwable missing) {
            if (quiet) return false;
            s.unavailable = true;
            ModuleLog.w(TAG, "defaults: binding has no " + field + "; machine " + label + " unsupported here");
            Toast.makeText(activity, "这个版本没有可点的 " + label + " 号机按钮，请手动选择",
                    Toast.LENGTH_LONG).show();
            return false;
        }
        if (button == null || !button.getText().toString().contains(label)) {
            if (quiet) return false;
            s.unavailable = true;
            ModuleLog.w(TAG, "defaults: machine " + label + " not present; nothing auto-selected");
            Toast.makeText(activity, "没有找到 " + label + " 号机，请手动选择", Toast.LENGTH_LONG).show();
            return false;
        }
        if (!button.isEnabled() || button.getVisibility() != View.VISIBLE) {
            if (quiet) return false;
            s.unavailable = true;
            Toast.makeText(activity, label + "号机当前不可用，请手动确认设备", Toast.LENGTH_LONG).show();
            ModuleLog.w(TAG, "defaults: machine " + label + " unavailable; no automatic substitution");
            return false;
        }
        button.performClick();
        Object position = get(activity, "hairPosition");
        if (!(position instanceof Number) || ((Number) position).intValue() != expected) {
            throw new IllegalStateException("native machine " + label + " selection was rejected");
        }
        s.pickedMachine = true;
        ModuleLog.i(TAG, "defaults: selected machine " + label + " through native handler");
        return true;
    }

    /**
     * 直接改数据模型来选档位：1.144.0 起原生选择 lambda 的名字/index 会变，
     * 改 model.getData() 的 selected 再刷新价格比锁 lambda 稳。
     */
    private static void selectTier(Activity activity, Object adapter, List<?> prices, int index) {
        int current = -1;
        Object tp = get(activity, "timePosition");
        if (tp instanceof Number) current = ((Number) tp).intValue();
        if (current == index) return;
        try { AppReflect.call(prices.get(index), "setSelected", true); } catch (Throwable ignored) { }
        try {
            if (current >= 0 && current < prices.size()) {
                AppReflect.call(prices.get(current), "setSelected", false);
            }
        } catch (Throwable ignored) { }
        try { AppReflect.call(adapter, "notifyItemChanged", index); } catch (Throwable ignored) { }
        try {
            if (current >= 0) AppReflect.call(adapter, "notifyItemChanged", current);
        } catch (Throwable ignored) { }
        try {
            AppReflect.set(activity, "timePosition", index);
        } catch (Throwable error) {
            ModuleLog.w(TAG, "defaults: cannot write timePosition", error);
        }
        try {
            AppReflect.call(activity, "initPrice", Boolean.FALSE);
        } catch (Throwable error) {
            ModuleLog.w(TAG, "defaults: price refresh skipped", error);
        }
    }

    /**
     * 点「下一步」之前把机器与档位摆正（1.144.0 默认是左边 1 号 + 第一个档位）。
     * 只改选择，不提交、不建单。
     */
    static boolean fixBeforeOrder(Activity activity) {
        if (!Cfg.qiekjSelections || (activity!=null && state(activity).userChanged)) return true;
        try {
            if (activity == null) return false;
            Object binding = AppReflect.call(activity, "getMBinding");
            if (binding == null) return false;
            String mode = Cfg.qiekjMachine == null ? "right2" : Cfg.qiekjMachine;
            List<?> skus = (List<?>) get(activity, "mSkus");
            boolean two = skus != null && skus.size() >= 2;
            if (!"none".equals(mode)) {
                boolean wantRight = "right2".equals(mode) || ("auto".equals(mode) && two);
                int want = wantRight ? 1 : 0;
                Object cur = get(activity, "hairPosition");
                int hair = cur instanceof Number ? ((Number) cur).intValue() : -1;
                if (hair != want) {
                    Object button = get(binding, wantRight ? "tvRight" : "tvLeft");
                    if (!(button instanceof View) || !((View) button).isEnabled()
                            || ((View) button).getVisibility() != View.VISIBLE) {
                        return false;
                    }
                    ((View) button).performClick();
                    Object after = get(activity, "hairPosition");
                    hair = after instanceof Number ? ((Number) after).intValue() : -1;
                }
                if (hair != want) return false;
            }
            Object adapter = get(activity, "mAdapter");
            if (adapter == null) return false;
            List<?> prices = (List<?>) AppReflect.call(adapter, "getData");
            if (prices == null || prices.isEmpty()) return false;
            boolean pulse = Boolean.TRUE.equals(get(activity, "isPulse"));
            final boolean longest = "longest".equals(Cfg.qiekjTier);
            int best = -1;
            double bestMetric = Double.POSITIVE_INFINITY;
            double cheapest = Double.POSITIVE_INFINITY;
            for (int i = 0; i < prices.size(); i++) {
                Object item = prices.get(i);
                if (!Boolean.TRUE.equals(AppReflect.call(item, "getStatus"))) continue;
                double duration = number(item, pulse ? "getRealTime" : "getTime");
                double price = number(item, "getPrice");
                if (!Double.isFinite(duration) || !Double.isFinite(price) || duration <= 0) continue;
                double metric = longest ? -duration : duration;
                if (metric < bestMetric || (metric == bestMetric && price < cheapest)) {
                    best = i;
                    bestMetric = metric;
                    cheapest = price;
                }
            }
            if (best < 0) return false;
            selectTier(activity, adapter, prices, best);
            Object now = get(activity, "timePosition");
            int tier = now instanceof Number ? ((Number) now).intValue() : -1;
            if (tier != best) return false;
            ModuleLog.i(TAG, "defaults: before-order ready machine=" + get(activity, "hairPosition")
                    + " tier=" + tier + " price=" + cheapest + "; no submit action");
            return true;
        } catch (Throwable error) {
            ModuleLog.w(TAG, "defaults: before-order fix failed -> " + error);
            return false;
        }
    }

    private static Object get(Object target, String name) {
        try { return AppReflect.get(target, name); }
        catch (Exception e) { return null; }
    }

    private static double number(Object item, String getter) {
        try { return Double.parseDouble(String.valueOf(AppReflect.call(item, getter))); }
        catch (Exception ignored) { return Double.POSITIVE_INFINITY; }
    }
    static boolean checkAutomationQuote(Activity a) {
        if(!Cfg.priceGuard)return true;
        try {
            Object adapter=AppReflect.get(a,"mAdapter");List<?> list=(List<?>)AppReflect.call(adapter,"getData");
            int index=((Number)AppReflect.get(a,"timePosition")).intValue();Object item=list.get(index);
            boolean pulse=Boolean.TRUE.equals(AppReflect.get(a,"isPulse"));
            return RuntimeControl.quote(a,((Number)AppReflect.get(a,"hairPosition")).intValue(),number(item,pulse?"getRealTime":"getTime"),number(item,"getPrice"));
        } catch(Throwable ignored){return false;}
    }
}
