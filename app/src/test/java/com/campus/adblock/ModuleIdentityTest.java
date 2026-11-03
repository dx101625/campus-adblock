package com.campus.adblock;

import org.junit.Test;
import static org.junit.Assert.*;

public class ModuleIdentityTest {
    @Test public void channelsUseInstalledGithubIdentityInsteadOfLegacyPackage() {
        assertEquals("io.github.dx101625.campusadblock", ModuleIdentity.ID);
        assertEquals(ModuleIdentity.ID + ".status", StatusProvider.AUTHORITY);
        assertEquals(ModuleIdentity.ID + ".STATUS", StatusReceiver.ACTION);
        assertNotEquals("com.campus.adblock.status", StatusProvider.AUTHORITY);
    }
}
