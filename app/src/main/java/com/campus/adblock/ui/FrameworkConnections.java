package com.campus.adblock.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Process-local live connections. Historical load records are never accepted here. */
public final class FrameworkConnections {
    public static final class Info {
        public final String name, version;
        public final int api;
        public final List<String> scope;
        public Info(String name, String version, int api, List<String> scope) {
            if (name == null || name.trim().isEmpty() || api <= 0) throw new IllegalArgumentException("Invalid framework response");
            this.name = name.trim(); this.version = version == null ? "" : version.trim(); this.api = api;
            this.scope = Collections.unmodifiableList(new ArrayList<>(scope));
        }
    }
    private static final class Entry { long generation; Info info; }
    private final Map<Object, Entry> entries = new IdentityHashMap<>();
    private long generation;
    public synchronized long begin(Object source) {
        Entry entry = entries.computeIfAbsent(source, ignored -> new Entry());
        entry.generation = ++generation;
        return entry.generation;
    }
    public synchronized boolean connected(Object source, long token, Info info) {
        Entry entry = entries.get(source);
        if (entry == null || entry.generation != token) return false;
        entry.info = info;return true;
    }
    public synchronized boolean failed(Object source, long token) {
        Entry entry = entries.get(source);
        if (entry == null || entry.generation != token) return false;
        entries.remove(source);return true;
    }
    public synchronized void died(Object source) { entries.remove(source); }
    public synchronized List<Object> sources() { return new ArrayList<>(entries.keySet()); }
    public synchronized List<Info> active() {
        List<Info> active = new ArrayList<>();
        for (Entry entry : entries.values()) if (entry.info != null) active.add(entry.info);
        return Collections.unmodifiableList(active);
    }
}
