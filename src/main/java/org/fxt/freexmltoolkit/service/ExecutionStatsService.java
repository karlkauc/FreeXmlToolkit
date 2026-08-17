/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2026.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.service;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Central collector for the optional developer feature "execution statistics".
 *
 * <p>Call sites wrap their work in a {@link StatsProbe}:</p>
 * <pre>{@code
 * var probe = ExecutionStatsService.getInstance().begin(OperationType.XSLT, "report.xslt");
 * String result = ...;                                    // the existing work, unchanged
 * long elapsedMs = probe.finish(input.length(), result.length(), success, errorSummary);
 * }</pre>
 *
 * <p>When the feature flag ({@link DeveloperPropertyKeys#EXECUTION_STATS_ENABLED}) is off,
 * {@code begin()} returns a no-op probe that only carries the {@code nanoTime} start —
 * no MXBean is touched and nothing is recorded, so the disabled path has effectively zero
 * overhead and callers can still use the returned wall-clock milliseconds for their
 * status lines.</p>
 *
 * <p>This class has no JavaFX dependencies. Listeners are notified on the thread that
 * calls {@code finish()} (usually a worker thread) — UI consumers must wrap their
 * handling in {@code Platform.runLater}.</p>
 */
public final class ExecutionStatsService {

    private static final Logger logger = LogManager.getLogger(ExecutionStatsService.class);

    /** Maximum number of runs kept in the history ring buffer. */
    static final int MAX_ENTRIES = 200;

    private static final ExecutionStatsService INSTANCE = new ExecutionStatsService();

    private final ArrayDeque<ExecutionStats> history = new ArrayDeque<>();
    private final List<Consumer<ExecutionStats>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong idCounter = new AtomicLong();

    private ExecutionStatsService() {
    }

    public static ExecutionStatsService getInstance() {
        return INSTANCE;
    }

    /** Whether the developer feature flag is currently enabled. */
    public boolean isEnabled() {
        try {
            return Boolean.parseBoolean(
                    PropertiesServiceImpl.getInstance().get(DeveloperPropertyKeys.EXECUTION_STATS_ENABLED));
        } catch (Throwable t) {
            return false; // properties unavailable (tests, early startup)
        }
    }

    /**
     * Starts measuring one operation. Must be called on the thread that will execute the
     * work; {@link StatsProbe#finish} must run on the same thread (CPU time is per-thread).
     */
    public StatsProbe begin(ExecutionStats.OperationType type, String target) {
        return new StatsProbe(type, target, isEnabled());
    }

    /** Returns the recorded runs, newest first. */
    public synchronized List<ExecutionStats> snapshot() {
        List<ExecutionStats> result = new ArrayList<>(history);
        java.util.Collections.reverse(result);
        return result;
    }

    /** Clears the recorded history. */
    public synchronized void clear() {
        history.clear();
    }

    public void addListener(Consumer<ExecutionStats> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<ExecutionStats> listener) {
        listeners.remove(listener);
    }

    /** Resets history, listeners and the id counter (test seam). */
    synchronized void resetForTests() {
        history.clear();
        listeners.clear();
        idCounter.set(0);
    }

    private void record(ExecutionStats stats) {
        synchronized (this) {
            if (history.size() >= MAX_ENTRIES) {
                history.removeFirst();
            }
            history.addLast(stats);
        }
        for (Consumer<ExecutionStats> listener : listeners) {
            try {
                listener.accept(stats);
            } catch (Exception e) {
                logger.warn("Execution stats listener failed: {}", e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // Probe
    // ------------------------------------------------------------------

    /**
     * Measurement token for one operation. Created by {@link #begin}; when the feature is
     * disabled it only tracks wall time and records nothing.
     */
    public final class StatsProbe {

        private final ExecutionStats.OperationType type;
        private final String target;
        private final boolean enabled;
        private final long startNanos;
        private final LocalDateTime startedAt;
        private final long cpuStartNanos;
        private final long heapBefore;
        private final long gcCountBefore;
        private final long gcTimeBefore;
        private final Map<String, Long> phases = new LinkedHashMap<>();
        private boolean finished;

        private StatsProbe(ExecutionStats.OperationType type, String target, boolean enabled) {
            this.type = type;
            this.target = target == null ? "" : target;
            this.enabled = enabled;
            this.startNanos = System.nanoTime();
            if (enabled) {
                this.startedAt = LocalDateTime.now();
                this.cpuStartNanos = currentThreadCpuNanos();
                this.heapBefore = usedHeap();
                long[] gc = gcTotals();
                this.gcCountBefore = gc[0];
                this.gcTimeBefore = gc[1];
            } else {
                this.startedAt = null;
                this.cpuStartNanos = -1;
                this.heapBefore = 0;
                this.gcCountBefore = 0;
                this.gcTimeBefore = 0;
            }
        }

        /** Records a named sub-phase duration; no-op when the feature is disabled. */
        public void phase(String name, long millis) {
            if (enabled && millis > 0) {
                phases.put(name, millis);
            }
        }

        /** Current wall-clock milliseconds since {@code begin()} without finishing. */
        public long elapsedMillis() {
            return (System.nanoTime() - startNanos) / 1_000_000;
        }

        /**
         * Finishes the measurement and returns the wall-clock milliseconds (always, even
         * when the feature is disabled). When enabled, the run is added to the history and
         * listeners are notified on the calling thread.
         *
         * @param inputChars   input size in characters, or -1 when unknown
         * @param outputChars  output size in characters (bytes for binary output), or -1
         * @param success      whether the operation succeeded
         * @param errorSummary short error description, ignored on success
         */
        public long finish(long inputChars, long outputChars, boolean success, String errorSummary) {
            long wallMillis = elapsedMillis();
            if (!enabled || finished) {
                return wallMillis;
            }
            finished = true;
            long cpuEnd = currentThreadCpuNanos();
            long cpuMillis = (cpuStartNanos >= 0 && cpuEnd >= 0) ? (cpuEnd - cpuStartNanos) / 1_000_000 : -1;
            long[] gc = gcTotals();
            ExecutionStats stats = new ExecutionStats(
                    idCounter.incrementAndGet(), type, target, startedAt,
                    wallMillis, cpuMillis,
                    heapBefore, usedHeap() - heapBefore,
                    gc[0] - gcCountBefore, gc[1] - gcTimeBefore,
                    inputChars, outputChars,
                    phases.isEmpty() ? Map.of() : new LinkedHashMap<>(phases),
                    success, success ? "" : (errorSummary == null ? "" : errorSummary));
            record(stats);
            return wallMillis;
        }
    }

    // ------------------------------------------------------------------
    // Measurement helpers
    // ------------------------------------------------------------------

    private static long currentThreadCpuNanos() {
        try {
            ThreadMXBean bean = ManagementFactory.getThreadMXBean();
            if (bean.isCurrentThreadCpuTimeSupported() && bean.isThreadCpuTimeEnabled()) {
                return bean.getCurrentThreadCpuTime();
            }
        } catch (Throwable ignored) {
            // unsupported on this JVM/platform
        }
        return -1;
    }

    private static long usedHeap() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    /** Returns {@code [totalGcCount, totalGcTimeMillis]} summed over all collectors. */
    private static long[] gcTotals() {
        long count = 0;
        long time = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long c = gc.getCollectionCount();
            long t = gc.getCollectionTime();
            if (c > 0) {
                count += c;
            }
            if (t > 0) {
                time += t;
            }
        }
        return new long[]{count, time};
    }

    // ------------------------------------------------------------------
    // Export
    // ------------------------------------------------------------------

    private static final DateTimeFormatter EXPORT_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** Renders the given runs as CSV (RFC-4180-style quoting, header row included). */
    public static String toCsv(List<ExecutionStats> stats) {
        StringBuilder sb = new StringBuilder(
                "id,startedAt,operation,target,wallMillis,cpuMillis,heapBeforeBytes,heapDeltaBytes,"
                        + "gcCount,gcTimeMillis,inputChars,outputChars,phases,success,error\n");
        for (ExecutionStats s : stats) {
            sb.append(s.id()).append(',')
                    .append(s.startedAt() != null ? EXPORT_TIME.format(s.startedAt()) : "").append(',')
                    .append(s.type()).append(',')
                    .append(csvField(s.target())).append(',')
                    .append(s.wallMillis()).append(',')
                    .append(s.cpuMillis()).append(',')
                    .append(s.heapBeforeBytes()).append(',')
                    .append(s.heapDeltaBytes()).append(',')
                    .append(s.gcCount()).append(',')
                    .append(s.gcTimeMillis()).append(',')
                    .append(s.inputChars()).append(',')
                    .append(s.outputChars()).append(',')
                    .append(csvField(formatPhases(s.phaseMillis()))).append(',')
                    .append(s.success()).append(',')
                    .append(csvField(s.errorSummary()))
                    .append('\n');
        }
        return sb.toString();
    }

    /** Renders the given runs as pretty-printed JSON. */
    public static String toJson(List<ExecutionStats> stats) {
        JsonArray array = new JsonArray();
        for (ExecutionStats s : stats) {
            JsonObject o = new JsonObject();
            o.addProperty("id", s.id());
            o.addProperty("startedAt", s.startedAt() != null ? EXPORT_TIME.format(s.startedAt()) : "");
            o.addProperty("operation", s.type().name());
            o.addProperty("target", s.target());
            o.addProperty("wallMillis", s.wallMillis());
            o.addProperty("cpuMillis", s.cpuMillis());
            o.addProperty("heapBeforeBytes", s.heapBeforeBytes());
            o.addProperty("heapDeltaBytes", s.heapDeltaBytes());
            o.addProperty("gcCount", s.gcCount());
            o.addProperty("gcTimeMillis", s.gcTimeMillis());
            o.addProperty("inputChars", s.inputChars());
            o.addProperty("outputChars", s.outputChars());
            JsonObject phases = new JsonObject();
            s.phaseMillis().forEach(phases::addProperty);
            o.add("phaseMillis", phases);
            o.addProperty("success", s.success());
            o.addProperty("error", s.errorSummary());
            array.add(o);
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(array);
    }

    private static String formatPhases(Map<String, Long> phases) {
        if (phases.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        phases.forEach((name, ms) -> {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(name).append('=').append(ms);
        });
        return sb.toString();
    }

    private static String csvField(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
