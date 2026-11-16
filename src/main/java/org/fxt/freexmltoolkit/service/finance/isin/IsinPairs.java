package org.fxt.freexmltoolkit.service.finance.isin;

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.fxt.freexmltoolkit.service.finance.FinancialIdentifiers;
import org.fxt.freexmltoolkit.util.SecureXmlFactory;

/**
 * Finds what an XML document reports about an ISIN next to it, to compare with ESMA FIRDS:
 * <ul>
 *   <li>{@link Kind#CFI} - an element named like a CFI field ({@code ClssfctnTp}, {@code PdctClssfctn}, {@code CFI},
 *       {@code CFICode} …) with a six-letter value;</li>
 *   <li>{@link Kind#ISSUER_LEI} - a valid LEI inside an issuer element (name containing {@code Issr}, {@code Issuer} or
 *       {@code Emittent}, at most three levels up): MMF {@code IssrData/PtyId/LEI}, SFTR {@code Scty/Issr/Id} …</li>
 * </ul>
 * Each is paired with the one ISIN in its nearest enclosing elements ({@link Kind#levels} up); an ancestor with several
 * ISINs makes the pairing ambiguous and the value is skipped. Format-independent; a text that is no well-formed XML
 * gives what was paired before the error.
 */
public final class IsinPairs {

    /** What is paired with an ISIN. */
    public enum Kind {
        CFI(3), ISSUER_LEI(5);

        /** How many ancestors are searched for the ISIN. */
        final int levels;

        Kind(int levels) {
            this.levels = levels;
        }
    }

    private static final Pattern CFI_VALUE = Pattern.compile("[A-Z]{6}");
    private static final Pattern CFI_NAME = Pattern.compile("(?i)cfi|cficode|cfi_code|clssfctntp|pdctclssfctn|classificationtype");
    private static final Pattern ISSUER_NAME = Pattern.compile("(?i).*(issr|issuer|emittent).*");
    /** Levels above an LEI element that may name the issuer. */
    private static final int ISSUER_NAME_LEVELS = 3;

    /** A reported value and the ISIN it belongs to: 1-based line and column of the value's element. */
    public record Pair(Kind kind, String isin, String value, int line, int column) {
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
        final Kind kind;
        final String value;
        final int line;
        final int column;
        int levels;

        Pending(Kind kind, String value, int line, int column) {
            this.kind = kind;
            this.value = value;
            this.line = line;
            this.column = column;
        }
    }

    private IsinPairs() {
    }

    /**
     * @return whether a reported CFI code agrees with the one in FIRDS: compared position by position, an {@code X}
     *         on either side (not applicable / construct) matches anything
     */
    public static boolean cfiMatches(String reported, String firds) {
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

    /** @return the values paired with an ISIN, in document order of their elements */
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
            // not well-formed: what was paired so far
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (XMLStreamException e) {
                    // nothing to release
                }
            }
        }
        pairs.sort(Comparator.comparingInt(Pair::line).thenComparingInt(Pair::column));
        return pairs;
    }

    private static void close(Deque<Frame> stack, List<Pair> pairs) {
        Frame f = stack.pop();
        // values of descendants: this element is one of their ancestors, its subtree is complete now
        settle(f, pairs);
        if (!f.hasChildren) {
            String value = f.text.toString().strip().toUpperCase(Locale.ROOT);
            if (IsinScanner.isIsin(value)) {
                f.isins.add(value);
            } else if (CFI_NAME.matcher(f.name).matches() && CFI_VALUE.matcher(value).matches()) {
                f.pending.add(new Pending(Kind.CFI, value, f.line, f.column));
            } else if (value.length() == 20 && FinancialIdentifiers.checkLei(value) == null && insideIssuer(f, stack)) {
                f.pending.add(new Pending(Kind.ISSUER_LEI, value, f.line, f.column));
            }
        }
        Frame parent = stack.peek();
        if (parent != null) {
            parent.isins.addAll(f.isins);
            parent.pending.addAll(f.pending);
        }
    }

    /** @return whether the element or one of its nearest ancestors names the issuer */
    private static boolean insideIssuer(Frame f, Deque<Frame> ancestors) {
        if (ISSUER_NAME.matcher(f.name).matches()) {
            return true;
        }
        Iterator<Frame> up = ancestors.iterator();
        for (int i = 0; i < ISSUER_NAME_LEVELS && up.hasNext(); i++) {
            if (ISSUER_NAME.matcher(up.next().name).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the pending values of a closing frame: with exactly one ISIN in its subtree they pair with it; with
     * several the pairing is ambiguous; with none they move one level up (until their kind's limit).
     */
    private static void settle(Frame f, List<Pair> pairs) {
        List<Pending> still = new ArrayList<>();
        for (Pending p : f.pending) {
            p.levels++;
            if (f.isins.size() == 1) {
                pairs.add(new Pair(p.kind, f.isins.iterator().next(), p.value, p.line, p.column));
            } else if (f.isins.isEmpty() && p.levels < p.kind.levels) {
                still.add(p);
            }
        }
        f.pending.clear();
        f.pending.addAll(still);
    }
}
