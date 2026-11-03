package com.campus.adblock;

import com.campus.adblock.ui.LocalData;
import org.junit.Test;
import static org.junit.Assert.*;

public class LocalDataTest {
    private String backup(String values, boolean devices) {
        return "{\"format\":\"campus-adblock-config\",\"schema\":1,\"includesDevices\":"+devices+",\"values\":"+values+"}";
    }
    private void rejected(String data) {
        try { LocalData.INSTANCE.parseBackup(data); fail("invalid backup accepted"); }
        catch (Exception expected) { }
    }
    @Test public void validatesQrAndRejectsNonDeviceLinks() {
        assertTrue(LocalData.INSTANCE.validQr("https://device.example/scan?NQT=fixture"));
        assertTrue(LocalData.INSTANCE.validQr("http://device.example/?a=1&SN=fixture"));
        assertFalse(LocalData.INSTANCE.validQr("intent://open?NQT=fixture"));
        assertFalse(LocalData.INSTANCE.validQr("https://device.example/?NQT="));
        assertFalse(LocalData.INSTANCE.validQr("https://device.example/?nqt=fixture"));
        assertFalse(LocalData.INSTANCE.validQr("https://device.example/?a=1"));
    }
    @Test public void validatesCompleteBackupBeforeAnyMutation() {
        assertEquals(2, LocalData.INSTANCE.parseBackup(backup("{\"q.ads\":false,\"q.pay\":\"wechat\"}",false)).getValues().size());
        rejected(backup("{\"q.ads\":\"false\"}",false));
        rejected(backup("{\"q.pay\":\"wallet\"}",false));
        rejected(backup("{\"q.ads\":true,\"shell\":\"anything\"}",false));
        rejected(backup("{\"q.qr\":\"https://device.example/?NQT=fixture\"}",false));
        rejected("{\"format\":\"other-app\",\"schema\":1,\"includesDevices\":false,\"values\":{\"q.ads\":true}}");
        rejected(backup("{}",false));
    }
    @Test public void devicesAreOptInAndIdsMustBeUnique() {
        String device="{\"id\":\"one\",\"name\":\"Dorm\",\"qr\":\"https://device.example/?NQT=fixture\"}";
        assertTrue(LocalData.INSTANCE.parseBackup(backup("{\"ui.devices\":["+device+"]}",true)).getIncludesDevices());
        rejected(backup("{\"ui.devices\":["+device+"]}",false));
        rejected(backup("{\"ui.devices\":["+device+","+device+"]}",true));
    }
    @Test public void diagnosticsRemoveDeviceAndCredentialText() {
        String cleaned=RuntimeEvents.clean("https://device.example/?NQT=private token=secret IMEI=123456789\n");
        assertFalse(cleaned.contains("private")); assertFalse(cleaned.contains("secret"));
        assertFalse(cleaned.contains("123456789")); assertFalse(cleaned.contains("\n"));
    }
    @Test public void unknownAppVersionsPauseAutomation() {
        assertTrue(PreferenceRules.verifiedQiekj("1.144.0"));
        assertTrue(PreferenceRules.verifiedQiekj("1.145.0"));
        assertFalse(PreferenceRules.verifiedQiekj("1.146.0"));
        assertFalse(PreferenceRules.verifiedQiekj(null));
    }
    @Test public void legacySchemaTwoBackupKeepsDeviceAndSelectedProfileAcrossPackageRename() {
        String data="{\"format\":\"campus-adblock-config\",\"schema\":2,\"includesDevices\":true,\"values\":{"
            +"\"q.mode\":\"preview\",\"q.profile\":\"one\",\"q.qr\":\"https://device.example/?NQT=fixture\","
            +"\"ui.devices\":[{\"id\":\"one\",\"name\":\"Fixture\",\"qr\":\"https://device.example/?NQT=fixture\"}]}}";
        var plan=LocalData.INSTANCE.parseBackup(data);
        assertTrue(plan.getIncludesDevices());
        assertEquals("one",plan.getValues().get("q.profile"));
        assertEquals("preview",plan.getValues().get("q.mode"));
        assertTrue(plan.getValues().get("ui.devices").toString().contains("Fixture"));
    }
    @Test public void backupWithoutDevicesDoesNotInventDeviceValuesDuringMigration() {
        var plan=LocalData.INSTANCE.parseBackup(backup("{\"q.mode\":\"ads\",\"q.pay\":\"wechat\"}",false));
        assertFalse(plan.getIncludesDevices());
        assertFalse(plan.getValues().containsKey("q.qr"));
        assertFalse(plan.getValues().containsKey("q.profile"));
        assertFalse(plan.getValues().containsKey("ui.devices"));
    }
}
