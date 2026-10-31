package com.campus.adblock;

import android.content.SharedPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

/** Empty-settings contract without clearing an installed phone's user data. */
public class FirstUseDefaultsTest {
    private final Map<Field,Object> original = new LinkedHashMap<>();
    @Before public void preserveProcessState() throws Exception {
        for (Field f : Cfg.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers())) {
                f.setAccessible(true); original.put(f,f.get(null));
            }
        }
    }
    @After public void restoreProcessState() throws Exception {
        for (Map.Entry<Field,Object> e : original.entrySet()) e.getKey().set(null,e.getValue());
    }
    @Test public void emptySettingsUseDocumentedDefaultsWithoutInventingADeviceCode() {
        MemoryPreferences p = new MemoryPreferences();
        assertEquals("preview",AutomationStore.mode(p));
        Cfg.load(p);
        assertTrue(Cfg.qiekjAds); assertTrue(Cfg.qiekjSplash);
        assertTrue(Cfg.qiekjScan); assertTrue(Cfg.qiekjAdvance);
        assertEquals("",Cfg.qiekjQr); assertEquals("right2",Cfg.qiekjMachine);
        assertEquals("shortest",Cfg.qiekjTier); assertEquals("unionpay",Cfg.qiekjPay);
        assertFalse(Cfg.qiekjCoupon); assertFalse(Cfg.qiekjPoints);
        assertTrue(Cfg.zqxyAds); assertTrue(Cfg.zqxyTab);
        assertTrue(Cfg.blockShake); assertTrue(Cfg.blockJump);
        assertEquals("standard",Cfg.jumpMode); assertEquals("",Cfg.jumpAllowed);
        assertFalse(Cfg.priceGuard); assertFalse(Cfg.pauseOnFailure);
        assertTrue("Reading defaults must not persist a device or change settings",p.getAll().isEmpty());
    }
    @Test public void firstExplicitAdsOnlyChoiceDisablesAllAutomaticSelections() {
        MemoryPreferences p = new MemoryPreferences(); p.values.put("q.mode","ads");
        Cfg.load(p);
        assertEquals("ads",Cfg.automationMode);
        assertFalse(Cfg.qiekjScan); assertFalse(Cfg.qiekjSelections); assertFalse(Cfg.qiekjAdvance);
        assertTrue(Cfg.qiekjAds); assertTrue(Cfg.qiekjSplash);
    }
    @Test public void legacyLaunchRestrictionCannotOverrideEnabledSwitch() {
        MemoryPreferences p = new MemoryPreferences();
        p.values.put("q.mode","preview"); p.values.put("q.launch","shortcut");
        p.values.put("q.price.guard",true); p.values.put("q.pause",true);
        Cfg.load(p);
        assertEquals("preview",Cfg.automationMode);
        assertTrue(Cfg.qiekjScan); assertTrue(Cfg.qiekjSelections); assertTrue(Cfg.qiekjAdvance);
        assertFalse(Cfg.priceGuard); assertFalse(Cfg.pauseOnFailure);
    }
    @Test public void legacyDeviceModeMigratesToEnabledAutomation() {
        MemoryPreferences p = new MemoryPreferences(); p.values.put("q.mode","device");
        Cfg.load(p);
        assertEquals("preview",Cfg.automationMode); assertTrue(Cfg.qiekjAdvance);
    }
    private static class MemoryPreferences implements SharedPreferences {
        final Map<String,Object> values = new HashMap<>();
        public Map<String,?> getAll(){return new HashMap<>(values);}
        public boolean contains(String key){return values.containsKey(key);}
        public String getString(String key,String fallback){return values.containsKey(key)?(String)values.get(key):fallback;}
        @SuppressWarnings("unchecked") public Set<String> getStringSet(String key,Set<String> fallback){return values.containsKey(key)?(Set<String>)values.get(key):fallback;}
        public boolean getBoolean(String key,boolean fallback){return values.containsKey(key)?(Boolean)values.get(key):fallback;}
        public int getInt(String key,int fallback){return values.containsKey(key)?(Integer)values.get(key):fallback;}
        public long getLong(String key,long fallback){return values.containsKey(key)?(Long)values.get(key):fallback;}
        public float getFloat(String key,float fallback){return values.containsKey(key)?(Float)values.get(key):fallback;}
        public Editor edit(){throw new AssertionError("Default reads must not edit user settings");}
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener){}
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener){}
    }
}
