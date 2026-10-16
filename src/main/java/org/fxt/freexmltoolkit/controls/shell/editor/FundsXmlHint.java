package org.fxt.freexmltoolkit.controls.shell.editor;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * When to offer the FundsXML extension (UX plan 5.5): a document whose root element is a
 * FundsXML root ({@code FundsXML4}, …) is opened while the extension is off, and the hint was
 * never shown before. UI-free; the shell shows the {@link FundsXmlHintBar}.
 */
public final class FundsXmlHint {

    /** Property set once the hint was shown, so it appears only once. */
    public static final String SHOWN_KEY = "fundsxml.hint.shown";

    /** How much of a document is scanned for its root element. */
    private static final int SCAN_LIMIT = 64 * 1024;

    private FundsXmlHint() {
    }

    /** @return whether the hint should be shown for a document with this content */
    public static boolean shouldOffer(String content) {
        return !FundsXmlRunner.isEnabled() && !wasShown() && isFundsXml(content);
    }

    /** @return whether the document's root element is a FundsXML root (local name starts with {@code FundsXML}) */
    public static boolean isFundsXml(String content) {
        String root = rootElementName(content);
        if (root == null) {
            return false;
        }
        String local = root.substring(root.indexOf(':') + 1);
        return local.startsWith("FundsXML");
    }

    /** @return the qualified name of the root element, skipping the prolog, or {@code null} */
    static String rootElementName(String content) {
        if (content == null) {
            return null;
        }
        int end = Math.min(content.length(), SCAN_LIMIT);
        int i = 0;
        while (i < end) {
            int lt = content.indexOf('<', i);
            if (lt < 0 || lt + 1 >= end) {
                return null;
            }
            if (content.startsWith("<?", lt)) {
                i = skipPast(content, lt, "?>");
            } else if (content.startsWith("<!--", lt)) {
                i = skipPast(content, lt, "-->");
            } else if (content.startsWith("<!", lt)) {
                i = skipPast(content, lt, ">");
            } else {
                int start = lt + 1;
                int stop = start;
                while (stop < end && !Character.isWhitespace(content.charAt(stop))
                        && content.charAt(stop) != '>' && content.charAt(stop) != '/') {
                    stop++;
                }
                return stop > start ? content.substring(start, stop) : null;
            }
            if (i < 0) {
                return null;
            }
        }
        return null;
    }

    private static int skipPast(String content, int from, String terminator) {
        int at = content.indexOf(terminator, from);
        return at < 0 ? -1 : at + terminator.length();
    }

    /** @return whether the hint was shown before */
    public static boolean wasShown() {
        try {
            return Boolean.parseBoolean(ServiceRegistry.get(PropertiesService.class).get(SHOWN_KEY));
        } catch (Throwable t) {
            return true; // no settings: never nag
        }
    }

    /** Remembers that the hint was shown. */
    public static void markShown() {
        try {
            ServiceRegistry.get(PropertiesService.class).set(SHOWN_KEY, "true");
        } catch (Throwable ignored) {
            // properties service unavailable: the hint may show again next time
        }
    }
}
