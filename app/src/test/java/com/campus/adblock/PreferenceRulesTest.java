package com.campus.adblock;

import org.junit.Test;
import static org.junit.Assert.*;

public class PreferenceRulesTest {
    @Test public void nativePaymentChannelsAndNoOverride() {
        assertEquals(52, PreferenceRules.paymentMethod("unionpay"));
        assertEquals(13, PreferenceRules.paymentMethod("alipay"));
        assertEquals(32, PreferenceRules.paymentMethod("wechat"));
        assertEquals(-1, PreferenceRules.paymentMethod("none"));
        assertEquals(-1, PreferenceRules.paymentMethod("unknown"));
    }
    @Test public void couponAndPointsNeverAuthorizePaidMarketing() {
        for (int type : new int[]{3,6,7}) {
            assertFalse(PreferenceRules.allowedPromotion(type, false, true));
            assertTrue(PreferenceRules.allowedPromotion(type, true, false));
        }
        assertTrue(PreferenceRules.allowedPromotion(8, false, true));
        assertFalse(PreferenceRules.allowedPromotion(8, true, false));
        for (int type : new int[]{44,45,1000,3000,5000,6000,6001,7000,8000,9000,10000})
            assertFalse("marketing type " + type, PreferenceRules.allowedPromotion(type, true, true));
    }
    @Test public void strictModeRequiresDestinationAndRespectsUserExceptions() {
        assertFalse(PreferenceRules.blockDestination(false,true,false,true,true));
        assertFalse(PreferenceRules.blockDestination(true,false,false,true,true));
        assertFalse(PreferenceRules.blockDestination(true,true,true,true,true));
        assertFalse(PreferenceRules.blockDestination(true,true,false,false,false));
        assertTrue(PreferenceRules.blockDestination(true,true,false,false,true));
        assertTrue(PreferenceRules.blockDestination(true,true,false,true,false));
    }
    @Test public void malformedExceptionEntriesAreIgnored() {
        assertEquals(2, PreferenceRules.packageSet("com.sankuai.meituan,com.xingin.xhs").size());
        assertEquals(1, PreferenceRules.packageSet("com.xingin.xhs,../x,https://x,,").size());
    }
    @Test public void disabledLogNeverInvokesAndroidLog() {
        boolean previous = Cfg.logEnabled;
        try {
            Cfg.logEnabled = false;
            assertEquals(0, ModuleLog.i("test", "not emitted"));
            assertEquals(0, ModuleLog.w("test", "not emitted", new Exception()));
        } finally { Cfg.logEnabled = previous; }
    }
}
