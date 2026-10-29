package com.campus.adblock.ui;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class FrameworkConnectionsTest {
    private FrameworkConnections.Info info(String name) {
        return new FrameworkConnections.Info(name,"test",102,Arrays.asList("com.qiekj.user","com.klcxkj.zqxy"));
    }
    @Test public void newProcessHasNoAssumedActivation() {
        assertTrue(new FrameworkConnections().active().isEmpty());
    }
    @Test public void pendingBinderIsNotActiveBeforeSuccessfulReply() {
        FrameworkConnections state=new FrameworkConnections();Object source=new Object();long token=state.begin(source);
        assertTrue(state.active().isEmpty());state.connected(source,token,info("LSPosed"));assertEquals(1,state.active().size());
    }
    @Test public void binderDeathDuringQueryCannotResurrectActivation() {
        FrameworkConnections state=new FrameworkConnections();Object source=new Object();long token=state.begin(source);
        state.died(source);state.connected(source,token,info("LSPosed"));assertTrue(state.active().isEmpty());
    }
    @Test public void supersededReplyCannotReplaceCurrentFramework() {
        FrameworkConnections state=new FrameworkConnections();Object source=new Object();long old=state.begin(source),latest=state.begin(source);
        state.connected(source,latest,info("current"));state.connected(source,old,info("stale"));state.failed(source,old);
        assertEquals("current",state.active().get(0).name);
    }
    @Test public void anotherFrameworkSurvivesOneBinderDying() {
        FrameworkConnections state=new FrameworkConnections();Object a=new Object(),b=new Object();
        state.connected(a,state.begin(a),info("first"));state.connected(b,state.begin(b),info("second"));state.died(a);
        assertEquals("second",state.active().get(0).name);
    }
    @Test public void failedRecheckRemovesPreviouslyActiveConnection() {
        FrameworkConnections state=new FrameworkConnections();Object source=new Object();state.connected(source,state.begin(source),info("LSPosed"));
        state.failed(source,state.begin(source));assertTrue(state.active().isEmpty());
    }
    @Test public void scopeSnapshotCannotChangeBehindTheUi() {
        java.util.ArrayList<String> scope=new java.util.ArrayList<>();scope.add("com.qiekj.user");
        FrameworkConnections.Info snapshot=new FrameworkConnections.Info("LSPosed","test",102,scope);scope.clear();
        assertEquals(1,snapshot.scope.size());
        try { snapshot.scope.clear();fail("Scope must be immutable"); } catch (UnsupportedOperationException expected) { }
    }
}
