package com.campus.adblock.ui;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Official framework-to-module service connection; never writes an activation preference. */
public final class FrameworkActivation {
    public enum Status { CHECKING, ACTIVE, UNAVAILABLE, UNSUPPORTED }
    public static final class Snapshot {
        public final Status status;
        public final List<FrameworkConnections.Info> frameworks;
        private Snapshot(Status status, List<FrameworkConnections.Info> frameworks) { this.status=status; this.frameworks=frameworks; }
        public static Snapshot checking() { return new Snapshot(Status.CHECKING, Collections.emptyList()); }
    }
    public interface Listener { void changed(Snapshot snapshot); }
    private static final FrameworkActivation INSTANCE = new FrameworkActivation();
    public static FrameworkActivation getInstance() { return INSTANCE; }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> { Thread t=new Thread(r,"campus-framework-state");t.setDaemon(true);return t; });
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private final FrameworkConnections connections = new FrameworkConnections();
    private boolean registered, checking = true;
    private Api26 bridge;
    private final Runnable finishChecking = () -> { checking=false;publish(); };
    private FrameworkActivation() { }
    public void listen(Listener listener) {
        listeners.add(listener);
        if (!registered) {
            registered=true;
            if (Build.VERSION.SDK_INT >= 26) { bridge=new Api26(this);bridge.start(); }
        }
        refresh();
    }
    public void remove(Listener listener) { listeners.remove(listener); }
    public void refresh() {
        if (Build.VERSION.SDK_INT < 26) { publish();return; }
        if (!registered) return;
        checking=connections.active().isEmpty();publish();
        main.removeCallbacks(finishChecking);main.postDelayed(finishChecking,1800);
        if (bridge != null) bridge.refresh();
    }
    private void publish() {
        List<FrameworkConnections.Info> active=connections.active();
        Status status=Build.VERSION.SDK_INT<26 ? Status.UNSUPPORTED : !active.isEmpty() ? Status.ACTIVE : checking ? Status.CHECKING : Status.UNAVAILABLE;
        Snapshot snapshot=new Snapshot(status,active);
        for (Listener listener:listeners) listener.changed(snapshot);
    }
    /** No service-library class is loaded by the API24-25 settings process. */
    @android.annotation.TargetApi(26)
    private static final class Api26 implements io.github.libxposed.service.XposedServiceHelper.OnServiceListener {
        private final FrameworkActivation owner;
        private final Set<io.github.libxposed.service.XposedService> services = new CopyOnWriteArraySet<>();
        Api26(FrameworkActivation owner) { this.owner=owner; }
        void start() { io.github.libxposed.service.XposedServiceHelper.registerListener(this); }
        @Override public void onServiceBind(io.github.libxposed.service.XposedService service) { services.add(service);probe(service); }
        @Override public void onServiceDied(io.github.libxposed.service.XposedService service) {
            services.remove(service);
            owner.connections.died(service);
            owner.main.post(() -> { owner.checking=false;owner.publish(); });
        }
        void refresh() {
            for (io.github.libxposed.service.XposedService service:services) probe(service);
        }
        private void probe(io.github.libxposed.service.XposedService service) {
            long token=owner.connections.begin(service);
            owner.worker.execute(() -> {
                boolean accepted;
                try {
                    FrameworkConnections.Info info=new FrameworkConnections.Info(service.getFrameworkName(),service.getFrameworkVersion(),service.getApiVersion(),service.getScope());
                    accepted=owner.connections.connected(service,token,info);
                } catch (Throwable unavailable) { accepted=owner.connections.failed(service,token); }
                if (accepted) owner.main.post(() -> { owner.checking=false;owner.publish(); });
            });
        }
    }
}
