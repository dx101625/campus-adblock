package com.campus.adblock;

import com.campus.adblock.ui.LocalData;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class HabitRulesTest {
    private String backup(String values,int schema){return "{\"format\":\"campus-adblock-config\",\"schema\":"+schema+",\"includesDevices\":true,\"values\":"+values+"}";}
    private String device(String extra){return "{\"id\":\"fixture-1\",\"name\":\"Dorm\",\"qr\":\"https://device.example/?NQT=fixture\""+extra+"}";}
    private void reject(String text){try{LocalData.INSTANCE.parseBackup(text);fail("invalid habit backup accepted");}catch(Exception expected){}}
    @Test public void legacyFlagsBecomeDistinctLevels(){
        assertEquals("ads",PreferenceRules.mode(false,false));assertEquals("ads",PreferenceRules.mode(false,true));
        assertEquals("device",PreferenceRules.mode(true,false));assertEquals("preview",PreferenceRules.mode(true,true));
        assertFalse(PreferenceRules.validMode("pay"));assertFalse(PreferenceRules.validMode(null));
    }
    @Test public void adsOnlyOrdinaryLaunchCannotBeOverriddenByADeviceProfile(){
        assertEquals("ads",PreferenceRules.launchMode("ads","preview",false,false));
        assertEquals("ads",PreferenceRules.launchMode("preview","preview",true,false));
        assertEquals("preview",PreferenceRules.launchMode("ads","preview",true,true));
        assertEquals("ads",PreferenceRules.launchMode("preview","ads",false,true));
        assertEquals("device",PreferenceRules.launchMode("preview","device",false,false));
    }
    @Test public void legacyDeviceImportsWithInheritedOptions() throws Exception {
        String normalized=(String)LocalData.INSTANCE.parseBackup(backup("{\"ui.devices\":["+device("")+"]}",1)).getValues().get("ui.devices");
        JSONObject p=new org.json.JSONArray(normalized).getJSONObject(0);
        assertEquals("inherit",p.getJSONObject("options").getString("mode"));assertEquals("",p.getString("group"));
    }
    @Test public void independentProfilesRejectUnknownOrInvalidOverrides() throws Exception {
        String d=device(",\"group\":\"Dormitory\",\"icon\":\"hair\",\"options\":{\"mode\":\"ads\",\"pay\":\"wechat\",\"coupon\":\"no\"}");
        assertTrue(LocalData.INSTANCE.parseBackup(backup("{\"ui.devices\":["+d+"]}",2)).getIncludesDevices());
        reject(backup("{\"ui.devices\":["+device(",\"options\":{\"mode\":\"pay\"}")+"]}",2));
        reject(backup("{\"ui.devices\":["+device(",\"options\":{\"shell\":\"command\"}")+"]}",2));
        reject(backup("{\"ui.devices\":["+device(",\"options\":[]")+"]}",2));
        reject(backup("{\"ui.devices\":["+device(",\"group\":7")+"]}",2));
        reject(backup("{\"ui.devices\":["+device(",\"icon\":\"unknown\"")+"]}",2));
    }
    @Test public void newSettingsAreValidatedTogetherBeforeRestore(){
        assertEquals(5,LocalData.INSTANCE.parseBackup(backup("{\"q.mode\":\"ads\",\"q.launch\":\"shortcut\",\"ui.theme\":\"dark\",\"ui.glass\":\"clear\",\"q.pause\":true}",2)).getValues().size());
        reject(backup("{\"q.mode\":\"preview\",\"ui.theme\":\"purple\"}",2));
        reject(backup("{\"q.pause\":\"true\"}",2));reject(backup("{\"q.mode\":\"ads\"}",3));
    }
    @Test public void quoteGuardComparesSamePlanPriceAndDuration(){
        assertFalse(PreferenceRules.changedQuote(40,1,40,1.00000001));
        assertTrue(PreferenceRules.changedQuote(40,1,80,1));
        assertTrue(PreferenceRules.changedQuote(40,1,40,2));
        assertTrue(PreferenceRules.changedQuote(40,1,30,1));
    }
    @Test public void temporaryGrantsExpireAndRejectClockAnomalies(){
        assertTrue(PreferenceRules.grantValid(1000,301000,1000));assertTrue(PreferenceRules.grantValid(1000,301000,300999));
        assertFalse(PreferenceRules.grantValid(1000,301000,301000));assertFalse(PreferenceRules.grantValid(1000,301000,999));
        assertFalse(PreferenceRules.grantValid(1000,601000,2000));assertFalse(PreferenceRules.grantValid(1000,301000,400000));
    }
    @Test public void grantsAreSourceScopedAndOnlyOneUseIsRemoved() throws Exception {
        JSONObject grants=new JSONObject().put("q/meituan",new JSONObject().put("issued",1000).put("expires",301000).put("remaining",1));
        assertFalse(PreferenceRules.consumeGrant(grants,"z","meituan",2000));assertEquals(1,grants.length());
        assertTrue(PreferenceRules.consumeGrant(grants,"q","meituan",2000));assertEquals(0,grants.length());
        assertFalse(PreferenceRules.consumeGrant(grants,"q","meituan",2001));
        grants.put("q/meituan",new JSONObject().put("issued",1000).put("expires",301000).put("remaining",-1));
        assertTrue(PreferenceRules.consumeGrant(grants,"q","meituan",2000));assertTrue(PreferenceRules.consumeGrant(grants,"q","meituan",2001));
        assertFalse(PreferenceRules.consumeGrant(grants,"q","meituan",301000));
    }
    @Test public void sdkAdvertisementActivitiesNeverMatchBusinessScannerOrPaymentPages(){
        assertTrue(AdActivityRules.isSdkActivity("com.bytedance.sdk.openadsdk.core.component.reward.activity.TTFullScreenVideoActivity"));
        assertTrue(AdActivityRules.isSdkActivity("com.bytedance.sdk.openadsdk.activity.TTRewardVideoActivity"));
        assertFalse(AdActivityRules.isSdkActivity("com.qiekj.user.MainActivity"));
        assertFalse(AdActivityRules.isSdkActivity("com.qiekj.user.ui.activity.scan.HomeScanCodeAct"));
        assertFalse(AdActivityRules.isSdkActivity("com.qiekj.user.ui.activity.scan.OrderPayPreviewAct"));
        assertFalse(AdActivityRules.isSdkActivity("com.alipay.sdk.app.PayTask"));assertFalse(AdActivityRules.isSdkActivity(null));
    }
}
