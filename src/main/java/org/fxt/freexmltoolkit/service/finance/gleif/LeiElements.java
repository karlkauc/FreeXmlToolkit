package org.fxt.freexmltoolkit.service.finance.gleif;

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.fxt.freexmltoolkit.service.finance.FinancialIdentifiers;
import org.fxt.freexmltoolkit.util.SecureXmlFactory;

/**
 * Finds LEIs with wrong check digits where an XML document says a value is an LEI: an element or attribute named
 * {@code LEI} (any case), ending in {@code LEI} ({@code IssuerLEI}), starting with {@code LEI} and an upper-case letter,
 * digit or underscore ({@code LEICode}, {@code LEI_Code}), or containing {@code LegalEntityIdentifier}.
 *
 * <p>{@link LeiScanner} finds LEIs in any text and therefore accepts only values with valid check digits - otherwise
 * every 20-character code ending in two digits would be reported. Inside such a named element the value is meant to be
 * an LEI, so a 20-character value with wrong check digits is an error. Values of another length are left to the schema.
 */
public final class LeiElements {

    private static final Pattern LEI_NAME = Pattern.compile(
            "(?i:lei)|.*LEI|LEI[A-Z0-9_].*|(?i:.*legalentityidentifier.*)");
    private static final Pattern CANDIDATE = Pattern.compile("[A-Za-z0-9]{20}");

    /** A value that should be an LEI but is none: 1-based line and column of its element. */
    public record Invalid(String value, int line, int column, String message) {
    }

    private static final class Frame {
        final boolean leiName;
        final int line;
        final int column;
        final StringBuilder text = new StringBuilder();
        boolean hasChildren;

        Frame(boolean leiName, int line, int column) {
            this.leiName = leiName;
            this.line = line;
            this.column = column;
        }
    }

    private LeiElements() {
    }

    /** @return whether an element or attribute name says its value is an LEI */
    public static boolean isLeiName(String localName) {
        return localName != null && LEI_NAME.matcher(localName).matches();
    }

    /** @return the invalid LEIs in LEI elements and attributes, in document order; empty for a text that is no XML */
    public static List<Invalid> scan(String xml) {
        List<Invalid> found = new ArrayList<>();
        if (xml == null || xml.isBlank() || xml.stripLeading().charAt(0) != '<') {
            return found;
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
                        int line = r.getLocation().getLineNumber();
                        int column = r.getLocation().getColumnNumber();
                        for (int i = 0; i < r.getAttributeCount(); i++) {
                            if (isLeiName(r.getAttributeLocalName(i))) {
                                check(r.getAttributeValue(i), line, column, found);
                            }
                        }
                        stack.push(new Frame(isLeiName(r.getLocalName()), line, column));
                    }
                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                        Frame top = stack.peek();
                        if (top != null && top.leiName && top.text.length() < 64) {
                            top.text.append(r.getText());
                        }
                    }
                    case XMLStreamConstants.END_ELEMENT -> {
                        Frame f = stack.pop();
                        if (f.leiName && !f.hasChildren) {
                            check(f.text.toString(), f.line, f.column, found);
                        }
                    }
                    default -> {
                    }
                }
            }
        } catch (XMLStreamException | RuntimeException e) {
            // not well-formed: what was found so far
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (XMLStreamException e) {
                    // nothing to release
                }
            }
        }
        found.sort(Comparator.comparingInt(Invalid::line).thenComparingInt(Invalid::column));
        return found;
    }

    private static void check(String raw, int line, int column, List<Invalid> found) {
        String value = raw == null ? "" : raw.strip();
        if (!CANDIDATE.matcher(value).matches()) {
            return;
        }
        String problem = FinancialIdentifiers.checkLei(value);
        if (problem != null) {
            found.add(new Invalid(value, line, column, problem));
        }
    }
}
