package com.campus.adblock;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Defaults modify original preview/selection models. No payment or order-creation calls. */
final class OrderDefaults {
    private static final String TAG = "CampusAdBlock";
    private static final String ORDER = "com.qiekj.user.ui.activity.scan.OrderPayPreviewAct";
    private static final Map<Activity, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final class State {
        boolean userOffers;
        boolean userPayment;
        boolean clearing;
        boolean refreshing;
        String lastFiltered;
    }

    static void install(CampusAdBlockModule module, ClassLoader loader) throws Exception {
        Class<?> cls = Class.forName(ORDER, false, loader);
        Method init = cls.getDeclaredMethod("initView", Bundle.class);
        module.hook(init).setId("pg:defaults:orderInit").intercept(chain -> {
            if (!Cfg.qiekjSelections) return chain.proceed();
            state((Activity) chain.getThisObject());
            return chain.proceed();
        });
        Method preview = cls.getDeclaredMethod("orderPre", boolean.class, boolean.class, String.class, boolean.class);
        module.hook(preview).setId("pg:defaults:preview").intercept(chain -> {
            if (!Cfg.qiekjSelections) return chain.proceed();
            Activity a = (Activity) chain.getThisObject();
            if (state(a).userOffers) return chain.proceed();
            resetMarketingFields(a);
            String promotions = filterPromotionJson(String.valueOf(chain.getArg(2)));
            boolean auto = !state(a).refreshing && (Cfg.qiekjCoupon || Cfg.qiekjPoints) && "[]".equals(promotions);
            ModuleLog.i(TAG, "defaults: preview coupon=" + Cfg.qiekjCoupon + " points=" + Cfg.qiekjPoints + " autoSelect=" + auto + "; no order creation");
            return chain.proceed(new Object[]{chain.getArg(0), auto, promotions, chain.getArg(3)});
        });
        Method marketing = null;
        for (int arity : new int[]{4, 3}) {
            try {
                marketing = AppReflect.named(cls, "marketingDiscount", arity);
                break;
            } catch (Throwable ignored) {
            }
        }
        if (marketing != null) {
        module.hook(marketing).setId("pg:defaults:marketing").intercept(chain -> {
            if (!Cfg.qiekjSelections) return chain.proceed();
            Activity a = (Activity) chain.getThisObject();
            if (state(a).userOffers) return chain.proceed();
            clearOrderModels(a);
            resetMarketingFields(a);
            int size = chain.getArgs().size();
            Object[] args = new Object[size];
            for (int i = 0; i < size; i++) {
                args[i] = chain.getArg(i);
            }
            if (size > 1) args[1] = Boolean.FALSE;
            if (size > 2) args[2] = "0";
            Object result = chain.proceed(args);
            clearOrderModels(a);
            ModuleLog.i(TAG, "defaults: all marketing selections off; actual marketingType=" + AppReflect.get(a, "marketingType"));
            return result;
        });
        } else {
            ModuleLog.w(TAG, "defaults: marketingDiscount not found in this app version; rely on model clearing");
        }
        try {
            Method channels = cls.getDeclaredMethod("payChannels", List.class);
            module.hook(channels).setId("pg:defaults:unionpay").intercept(chain -> {
            if (!Cfg.qiekjSelections) return chain.proceed();
                Activity a = (Activity) chain.getThisObject();
                try {
                    if (!state(a).userPayment) selectPayment(a, (List<?>) chain.getArg(0));
                    if (!state(a).userOffers) resetMarketingFields(a);
                } catch (Throwable error) {
                    ModuleLog.w(TAG, "defaults: pay channel defaults skipped", error);
                }
                return chain.proceed();
            });
        } catch (Throwable missing) {
            ModuleLog.w(TAG, "defaults: payChannels hook skipped -> " + missing);
        }
        // Subsequent deliberate user selections retain the app's original behavior.
        for (Method method : cls.getDeclaredMethods()) {
            if (method.getName().equals("handlePromotionItemClick") || method.getName().equals("handleMarketingItemClick")) {
                module.hook(method).setId("pg:defaults:user:" + method.getName()).intercept(chain -> {
            if (!Cfg.qiekjSelections) return chain.proceed();
                    state((Activity) chain.getThisObject()).userOffers = true;
                    return chain.proceed();
                });
            }
            if (method.getName().equals("initData") || method.getName().startsWith("orderPre$")
                    || method.getName().startsWith("createObserver$") || method.getName().equals("selectChannel")) {
                module.deoptimize(method);
            }
        }
        Class<?> baseAdapter = Class.forName("com.chad.library.adapter.base.BaseQuickAdapter", false, loader);
        Method replaceData = baseAdapter.getDeclaredMethod("setNewInstance", List.class);
        module.hook(replaceData).setId("pg:defaults:promotionModels").intercept(chain -> {
            if (!Cfg.qiekjSelections) return chain.proceed();
            Object adapter = chain.getThisObject();
            List<Activity> activities;
            synchronized (STATES) { activities = new ArrayList<>(STATES.keySet()); }
            List<Activity> refresh = new ArrayList<>();
            for (Activity a : activities) {
                if (a == null || state(a).userOffers || a.isDestroyed()) continue;
                if (AppReflect.get(a, "promotionAdapter") == adapter) {
                    if (clearModels((List<?>) chain.getArg(0))) refresh.add(a);
                    resetMarketingFields(a);
                }
            }
            Object result = chain.proceed();
            for (Activity a : refresh) refreshFilteredPreview(a);
            return result;
        });
        Class<?> fragment = Class.forName("com.qiekj.user.ui.fragment.PayModeFragment", false, loader);
        Method payClick = AppReflect.named(fragment, "onItemClick", 3);
        module.hook(payClick).setId("pg:defaults:userPayChoice").intercept(chain -> {
            if (!Cfg.qiekjSelections) return chain.proceed();
            Object a = AppReflect.call(chain.getThisObject(), "getActivity");
            if (a instanceof Activity && ORDER.equals(a.getClass().getName())) state((Activity) a).userPayment = true;
            return chain.proceed();
        });
        Class<?> popups = Class.forName("com.qiekj.user.extensions.PopupExtKt", false, loader);
        Method retain = AppReflect.named(popups, "showRetainVipPopup", 5);
        module.hook(retain).setId("pg:defaults:giveUpVipOffer").intercept(chain -> {
            if (!Cfg.qiekjSelections) return chain.proceed();
            Object context = chain.getArg(0);
            if (context instanceof Activity && ORDER.equals(context.getClass().getName())
                    && !state((Activity) context).userOffers) {
                Object giveUp = chain.getArg(4);
                if (giveUp != null) AppReflect.call(giveUp, "invoke");
                ModuleLog.i(TAG, "defaults: native give-up-offer callback executed");
                return null;
            }
            return chain.proceed();
        });
    }

    private static State state(Activity a) {
        State s = STATES.get(a);
        if (s == null) { s = new State(); STATES.put(a, s); }
        return s;
    }

    private static void resetMarketingFields(Activity a) throws Exception {
        AppReflect.set(a, "marketingType", 0);
        AppReflect.set(a, "pgVipType", 0);
    }

    private static void clearOrderModels(Activity a) throws Exception {
        State s = state(a);
        if (s.clearing) return;
        s.clearing = true;
        try {
            resetMarketingFields(a);
            Object adapter = AppReflect.get(a, "promotionAdapter");
            if (adapter != null) {
                boolean changed = clearModels((List<?>) AppReflect.call(adapter, "getData"));
                if (changed) AppReflect.call(adapter, "notifyDataSetChanged");
            }
            Object vm = AppReflect.call(a, "getMViewModel");
            Object live = AppReflect.call(vm, "getCashierMarketingData");
            clearModels((List<?>) AppReflect.call(live, "getValue"));
        } finally { s.clearing = false; }
    }

    private static boolean clearModels(List<?> data) throws Exception {
        if (data == null) return false;
        boolean changed = false;
        for (Object item : data) {
            if (item == null) continue;
            String name = item.getClass().getName();
            if (name.equals("com.qiekj.user.entity.CashierMarketingBean")) {
                if (Boolean.TRUE.equals(AppReflect.call(item, "getChecked"))) changed = true;
                AppReflect.call(item, "setChecked", false);
            } else if (name.equals("com.qiekj.user.entity.scan.Promotions")) {
                int type = ((Number) AppReflect.call(item, "getPromotionType")).intValue();
                if (PreferenceRules.allowedPromotion(type, Cfg.qiekjCoupon, Cfg.qiekjPoints)) continue;
                if (Boolean.TRUE.equals(AppReflect.call(item, "isUsed"))) changed = true;
                AppReflect.call(item, "setUsed", false);
                Object options = AppReflect.call(item, "getOptions");
                if (options instanceof List) for (Object option : (List<?>) options) {
                    if (Boolean.TRUE.equals(AppReflect.call(option, "isUsed"))) changed = true;
                    AppReflect.call(option, "setUsed", false);
                }
            }
        }
        return changed;
    }

    private static void selectPayment(Activity a, List<?> channels) throws Exception {
        if (channels == null) return;
        int wanted = PreferenceRules.paymentMethod(Cfg.qiekjPay);
        if (wanted < 0) {
            RuntimeEvents.note(a, "selection", "支付方式遵循原App，不做预选");
            return;
        }
        Object union = null;
        for (Object item : channels) {
            if (((Number) AppReflect.call(item, "getMethod")).intValue() == wanted
                    && Boolean.TRUE.equals(AppReflect.call(item, "getCanChecked"))) {
                union = item;
                break;
            }
        }
        if (union == null) {
            ModuleLog.w(TAG, "defaults: requested payment unavailable: " + Cfg.qiekjPay + "; original choices retained");
            RuntimeEvents.note(a, "selection", "所选支付方式当前不可用，保留原App选项");
            return;
        }
        for (Object item : channels) AppReflect.call(item, "setChecked", item == union);
        ModuleLog.i(TAG, "defaults: payment=" + Cfg.qiekjPay + " method=" + wanted + " selected in native channel model; no payment action");
        String payment = switch (Cfg.qiekjPay) {
            case "unionpay" -> "银联支付";
            case "alipay" -> "支付宝";
            case "wechat" -> "微信支付";
            default -> "支付跟随App";
        };
        RuntimeEvents.note(a, "selection", "已应用订单偏好：" + payment + "；优惠券" + (Cfg.qiekjCoupon ? "允许自动选择" : "手动选择") + "，积分" + (Cfg.qiekjPoints ? "允许自动选择" : "手动选择"));
    }

    private static String filterPromotionJson(String raw) {
        try {
            org.json.JSONArray source = new org.json.JSONArray(raw), out = new org.json.JSONArray();
            for (int i=0;i<source.length();i++) {
                org.json.JSONObject p = source.getJSONObject(i);
                if (PreferenceRules.allowedPromotion(p.optInt("promotionType", -1), Cfg.qiekjCoupon, Cfg.qiekjPoints)) out.put(p);
            }
            return out.toString();
        } catch (Exception ignored) { return "[]"; }
    }

    /** Recompute native preview totals after removing a forbidden server-default promotion. */
    private static void refreshFilteredPreview(Activity a) {
        new Handler(Looper.getMainLooper()).post(() -> {
            State s = state(a);
            if (a.isFinishing() || a.isDestroyed() || s.userOffers || s.refreshing) return;
            try {
                Object adapter = AppReflect.get(a, "promotionAdapter");
                Object selected = AppReflect.call(a, "getSelPromotion", AppReflect.call(adapter, "getData"), -1);
                Class<?> gson = Class.forName("com.qiekj.user.util.GsonUtils", false, a.getClassLoader());
                Method stringify = AppReflect.named(gson, "convertVO2String", 1);
                String json = filterPromotionJson((String) stringify.invoke(null, selected));
                if (json.equals(s.lastFiltered)) return;
                s.lastFiltered = json; s.refreshing = true;
                try { AppReflect.call(a, "orderPre", false, false, json, false); }
                finally { s.refreshing = false; }
            } catch (Throwable error) { ModuleLog.w(TAG, "defaults: filtered preview refresh skipped", error); }
        });
    }
}
