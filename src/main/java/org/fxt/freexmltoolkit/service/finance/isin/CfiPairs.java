package org.fxt.freexmltoolkit.service.finance.isin;

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.fxt.freexmltoolkit.util.SecureXmlFactory;

/**
 * Finds the CFI codes an XML document reports next to an ISIN: an element named like a CFI field ({@code ClssfctnTp},
 * {@code PdctClssfctn}, {@code CFI}, {@code CFICode} …) with a six-letter value, paired with the one ISIN in its nearest
 * enclosing elements (at most {@link #MAX_LEVELS} up; an ancestor with several ISINs makes the pairing ambiguous and
 * is skipped). Format-independent: MMF ({@code AsstId}), SFTR ({@code Scty}), EMIR, FundsXML … A text that is no
 * well-formed XML gives no pairs.
 */
public final class CfiPairs {

    /** How many ancestors are searched for the ISIN. */
    static final int MAX_LEVELS = 3;
    private static final Pattern CFI_VALUE = Pattern.compile("[A-Z]{6}");
    private static final Pattern CFI_NAME = Pattern.compile("(?i)cfi|cficode|cfi_code|clssfctntp|pdctclssfctn|classificationtype");

    /** A reported CFI code and the ISIN it belongs to: 1-based line and column of the CFI element. */
    public record Pair(String isin, String cfi, int line, int column) {
    }

    private static final class Frame {
        final String name;
        final int line;
        final int column;
        final StringBuilder text = new StringBuilder();
        final Set<String> isins = new LinkedHashSet<>();
        final List<Pending> pending = new ArrayList<>();
        boolean hasChildren;

        Frame(String name, int line, int column) {
            this.name = name;
            this.line = line;
            this.column = column;
        }
    }

    private static final class Pending {
        final String cfi;
        final int line;
        final int column;
        int levels;

        Pending(String cfi, int line, int column) {
            this.cfi = cfi;
            this.line = line;
            this.column = column;
        }
    }

    private CfiPairs() {
    }

    /**
     * @return whether a reported CFI code agrees with the one in FIRDS: compared position by position, an {@code X}
     *         on either side (not applicable / construct) matches anything
     */
    public static boolean matches(String reported, String firds) {
        if (reported == null || firds == null || reported.length() != 6 || firds.length() != 6) {
            return true;
        }
        for (int i = 0; i < 6; i++) {
            char r = reported.charAt(i);
            char f = firds.charAt(i);
            if (r != 'X' && f != 'X' && r != f) {
                return false;
            }
        }
        return true;
    }

    /** @return the CFI codes paired with an ISIN, in document order of the CFI elements */
    public static List<Pair> scan(String xml) {
        List<Pair> pairs = new ArrayList<>();
        if (xml == null || xml.isBlank() || xml.stripLeading().charAt(0) != '<') {
            return pairs;
        }
        Deque<Frame> stack = new ArrayDeque<>();
        XMLStreamReader r = null;
        try {
            r = SecureXmlFactory.createSecureXMLInputFactory().createXMLStreamReader(new StringReader(xml));
            while (r.hasNext()) {
                switch (r.next()) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        if (!stack.isEmpty()) {
                            stack.peek().hasChildren = true;
                        }
                        stack.push(new Frame(r.getLocalName(), r.getLocation().getLineNumber(), r.getLocation().getColumnNumber()));
                    }
                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                        if (!stack.isEmpty() && stack.peek().text.length() < 64) {
                            stack.peek().text.append(r.getText());
                        }
                    }
                    case XMLStreamConstants.END_ELEMENT -> close(stack, pairs);
                    default -> {
                    }
                }
            }
        } catch (XMLStreamException | RuntimeException e) {
            return pairs; // not well-formed: what was paired so far
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (XMLStreamException e) {
                    // nothing to release
                }
            }
        }
        pairs.sort(java.util.Comparator.comparingInt(Pair::line).thenComparingInt(Pair::column));
        return pairs;
    }

    private static void close(Deque<Frame> stack, List<Pair> pairs) {
        Frame f = stack.pop();
        // CFI codes of descendants: this element is one of their ancestors, its subtree is complete now
        settle(f, pairs);
        if (!f.hasChildren) {
            String value = f.text.toString().strip().toUpperCase(Locale.ROOT);
            if (IsinScanner.isIsin(value)) {
                f.isins.add(value);
            } else if (CFI_NAME.matcher(f.name).matches() && CFI_VALUE.matcher(value).matches()) {
                f.pending.add(new Pending(value, f.line, f.column));
            }
        }
        Frame parent = stack.peek();
        if (parent != null) {
            parent.isins.addAll(f.isins);
            parent.pending.addAll(f.pending);
        }
    }

    /**
     * Resolves the pending CFI codes of a frame that is closing: with exactly one ISIN in its subtree they pair with it;
     * with several the pairing is ambiguous; with none they move one level up (until {@link #MAX_LEVELS}).
     */
    private static void settle(Frame f, List<Pair> pairs) {
        List<Pending> still = new ArrayList<>();
        for (Pending p : f.pending) {
            p.levels++;
            if (f.isins.size() == 1) {
                pairs.add(new Pair(f.isins.iterator().next(), p.cfi, p.line, p.column));
            } else if (f.isins.isEmpty() && p.levels < MAX_LEVELS) {
                still.add(p);
            }
        }
        f.pending.clear();
        f.pending.addAll(still);
    }
}
