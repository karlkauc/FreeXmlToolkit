package org.fxt.freexmltoolkit.service.telemetry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Anonymous, message-free fingerprint of a {@link Throwable}.
 *
 * <ul>
 *   <li>{@link #errorCode()} — fully-qualified class name of the root-most exception of the
 *       cause chain (e.g. {@code java.io.IOException}).</li>
 *   <li>{@link #detail()} — the exception class chain (outer first, {@code " <- "}-joined)
 *       followed by up to {@value #MAX_FRAMES} frames as {@code class#method:line}.
 *       {@code org.fxt} frames are preferred: runs of foreign frames are collapsed to
 *       {@code "… (n frames)"} (the top two frames are always kept). If the trace contains no
 *       {@code org.fxt} frame at all, the first frames are kept verbatim.</li>
 *   <li>{@link #hash()} — first 16 hex chars of SHA-256 over the class chain plus the kept
 *       frames <em>without</em> line numbers and collapse counts, so the hash is stable
 *       across unrelated code edits that only move lines.</li>
 * </ul>
 *
 * <p><b>Privacy:</b> {@link Throwable#getMessage()} / {@code getLocalizedMessage()} are never
 * read — messages routinely contain file paths, element names or document content.
 *
 * @param errorCode root-most exception class (≤ 200 chars)
 * @param detail    sanitized stack signature (≤ max chars)
 * @param hash      16 hex chars
 */
public record ErrorSignature(String errorCode, String detail, String hash) {

    /** Max frames listed in {@link #detail()}. */
    public static final int MAX_FRAMES = 20;
    /** Max length of {@link #detail()} for events (API limit). */
    public static final int MAX_DETAIL_CHARS = 4000;
    /** Max cause-chain depth examined. */
    private static final int MAX_CHAIN = 10;
    /** Top-of-stack frames always kept, even when not {@code org.fxt}. */
    private static final int ALWAYS_KEEP_TOP = 2;
    private static final String OWN_PACKAGE = "org.fxt.";

    /** Signature with the event limits ({@value #MAX_FRAMES} frames, {@value #MAX_DETAIL_CHARS} chars). */
    public static ErrorSignature of(Throwable t) {
        return of(t, MAX_FRAMES, MAX_DETAIL_CHARS);
    }

    /**
     * Builds the signature of {@code t}.
     *
     * @param t         the throwable (must not be null)
     * @param maxFrames max frames listed
     * @param maxChars  max length of the detail text
     */
    public static ErrorSignature of(Throwable t, int maxFrames, int maxChars) {
        List<Throwable> chain = causeChain(t);
        Throwable root = chain.getLast();

        StringBuilder chainText = new StringBuilder();
        for (int i = 0; i < chain.size(); i++) {
            if (i > 0) {
                chainText.append(" <- ");
            }
            chainText.append(className(chain.get(i)));
        }

        // Frames of the root-most exception that actually carries a stack trace.
        StackTraceElement[] frames = new StackTraceElement[0];
        for (int i = chain.size() - 1; i >= 0; i--) {
            StackTraceElement[] st = chain.get(i).getStackTrace();
            if (st != null && st.length > 0) {
                frames = st;
                break;
            }
        }

        List<String> lines = new ArrayList<>();      // with line numbers (detail)
        List<String> hashLines = new ArrayList<>();  // without line numbers (hash)
        boolean hasOwn = false;
        for (StackTraceElement f : frames) {
            if (isOwn(f)) {
                hasOwn = true;
                break;
            }
        }

        int kept = 0;
        int collapsed = 0;
        int i = 0;
        for (; i < frames.length && kept < maxFrames; i++) {
            StackTraceElement f = frames[i];
            boolean keep = !hasOwn || i < ALWAYS_KEEP_TOP || isOwn(f);
            if (keep) {
                if (collapsed > 0) {
                    lines.add("… (" + collapsed + " frames)");
                    hashLines.add("…");
                    collapsed = 0;
                }
                lines.add(frameText(f, true));
                hashLines.add(frameText(f, false));
                kept++;
            } else {
                collapsed++;
            }
        }
        int remaining = collapsed + (frames.length - i);
        if (remaining > 0) {
            lines.add("… (" + remaining + " frames)");
        }

        StringBuilder detail = new StringBuilder(chainText);
        for (String line : lines) {
            detail.append('\n').append("at ").append(line);
        }
        String detailText = detail.length() > maxChars ? detail.substring(0, Math.max(0, maxChars - 1)) + "…" : detail.toString();

        String hashInput = chainText + "\n" + String.join("\n", hashLines);
        String code = TelemetrySanitizer.errorCode(className(root));
        return new ErrorSignature(code != null ? code : "unknown", detailText, TelemetrySanitizer.hash16(hashInput));
    }

    /** Cause chain, outer first; cycle-safe and depth-limited. */
    static List<Throwable> causeChain(Throwable t) {
        List<Throwable> chain = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable cur = t;
        while (cur != null && chain.size() < MAX_CHAIN && seen.add(cur)) {
            chain.add(cur);
            cur = cur.getCause();
        }
        return chain;
    }

    private static boolean isOwn(StackTraceElement f) {
        return f.getClassName() != null && f.getClassName().startsWith(OWN_PACKAGE);
    }

    private static String className(Throwable t) {
        return normalizeClass(t.getClass().getName());
    }

    private static String frameText(StackTraceElement f, boolean withLine) {
        StringBuilder sb = new StringBuilder(normalizeClass(f.getClassName()))
                .append('#').append(normalizeMethod(f.getMethodName()));
        if (withLine && f.getLineNumber() >= 0) {
            sb.append(':').append(f.getLineNumber());
        }
        return sb.toString();
    }

    /** Removes volatile synthetic suffixes: {@code $$Lambda/0x…}, {@code $Proxy12}, hidden-class ids. */
    static String normalizeClass(String cls) {
        if (cls == null) {
            return "?";
        }
        String c = cls.replaceAll("\\$\\$Lambda.*$", "\\$\\$Lambda");
        c = c.replaceAll("\\$Proxy\\d+", "\\$Proxy");
        c = c.replaceAll("/0x[0-9a-fA-F]+", "");
        return c;
    }

    /** {@code lambda$foo$3} → {@code lambda$foo} (the counter shifts whenever lambdas are added). */
    static String normalizeMethod(String method) {
        if (method == null) {
            return "?";
        }
        return method.replaceAll("^(lambda\\$.*?)\\$\\d+$", "$1");
    }
}
