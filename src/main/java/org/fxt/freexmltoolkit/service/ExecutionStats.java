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

import java.time.LocalDateTime;
import java.util.Map;

/**
 * One recorded technical operation with its resource consumption — the unified
 * measurement collected by {@link ExecutionStatsService} when the developer
 * feature "execution statistics" is enabled.
 *
 * <p>Memory values are {@link Runtime}-based heap snapshots taken in a shared JVM and
 * therefore approximate: use them to compare runs, not as absolute costs. CPU time is
 * per-thread ({@code ThreadMXBean}); GC values are deltas of the JVM-wide collector
 * totals during the run.</p>
 *
 * @param id            monotonically increasing record id
 * @param type          the kind of operation
 * @param target        short human-readable target label (stylesheet/schema/query name)
 * @param startedAt     wall-clock start of the operation
 * @param wallMillis    wall-clock duration in milliseconds
 * @param cpuMillis     thread CPU time in milliseconds, or -1 when unsupported
 * @param heapBeforeBytes used heap before the run
 * @param heapDeltaBytes  used-heap delta (after - before); may be negative after a GC
 * @param gcCount       number of GC cycles observed during the run
 * @param gcTimeMillis  total GC time observed during the run
 * @param inputChars    input size in characters, or -1 when unknown
 * @param outputChars   output size in characters (bytes for binary outputs like PDF), or -1
 * @param phaseMillis   named sub-phase durations in ms (insertion-ordered, may be empty)
 * @param success       whether the operation succeeded
 * @param errorSummary  first line of the error on failure, empty string on success
 */
public record ExecutionStats(
        long id,
        OperationType type,
        String target,
        LocalDateTime startedAt,
        long wallMillis,
        long cpuMillis,
        long heapBeforeBytes,
        long heapDeltaBytes,
        long gcCount,
        long gcTimeMillis,
        long inputChars,
        long outputChars,
        Map<String, Long> phaseMillis,
        boolean success,
        String errorSummary) {

    /** The kinds of technical operations that record statistics. */
    public enum OperationType {
        XSLT, XQUERY, XPATH, JSONPATH, VALIDATION, XPROC, FOP_PDF
    }

    /**
     * Compact single-line label for status-bar display, e.g. {@code "XSLT · 42 ms"}.
     */
    public String shortLabel() {
        return type + " · " + formatMillis(wallMillis);
    }

    /**
     * First line of a (possibly multi-line) message, trimmed — used to compress an
     * {@code "ERROR: …"} result into the {@link #errorSummary} field.
     */
    public static String firstLine(String text) {
        if (text == null) {
            return "";
        }
        int newline = text.indexOf('\n');
        return (newline >= 0 ? text.substring(0, newline) : text).trim();
    }

    /**
     * Formats a millisecond duration for display: {@code "42 ms"}, {@code "1.2 s"},
     * or {@code MM:SS} for long runs.
     */
    public static String formatMillis(long millis) {
        if (millis < 1000) {
            return millis + " ms";
        }
        if (millis < 60_000) {
            return String.format(java.util.Locale.US, "%.1f s", millis / 1000.0);
        }
        return org.fxt.freexmltoolkit.util.FormattingUtils.formatElapsedTime(millis);
    }
}
