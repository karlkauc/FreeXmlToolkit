package org.fxt.freexmltoolkit.controls.v2.view;

import java.util.List;
import java.util.Objects;

import org.fxt.freexmltoolkit.controls.shell.schema.SchemaConstraints;
import org.fxt.freexmltoolkit.controls.v2.model.XsdField;
import org.fxt.freexmltoolkit.controls.v2.model.XsdIdentityConstraint;
import org.fxt.freexmltoolkit.controls.v2.model.XsdKeyRef;

/**
 * One line a diagram card shows for an identity constraint or an assertion.
 *
 * @param kind    what it is
 * @param text    the shortened line, at most {@link #MAX_TEXT_LENGTH} characters
 * @param tooltip the full details
 * @since 2.0
 */
public record ConstraintRow(Kind kind, String text, String tooltip) {

    /** Longer lines would widen a card without limit, and an XPath can run to hundreds of characters. */
    static final int MAX_TEXT_LENGTH = 44;

    /** The kinds of constraint a card distinguishes. */
    public enum Kind {
        KEY("KEY"),
        UNIQUE("UNIQ"),
        KEYREF("REF"),
        ASSERT("ASSERT");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        /** @return the short label a card shows */
        public String label() {
            return label;
        }
    }

    /**
     * Creates the row of an identity constraint.
     *
     * @param info   the constraint
     * @param origin where it comes from if not the card's own declaration, or null
     * @return its row
     */
    static ConstraintRow identity(SchemaConstraints.ConstraintInfo info, String origin) {
        XsdIdentityConstraint constraint = (XsdIdentityConstraint) info.node();
        Kind kind = switch (info.kind()) {
            case "key" -> Kind.KEY;
            case "keyref" -> Kind.KEYREF;
            default -> Kind.UNIQUE;
        };
        String text = constraint instanceof XsdKeyRef keyRef && keyRef.getRefer() != null
                ? info.name() + " → " + keyRef.getRefer()
                : (info.name() + "  " + fieldsOf(constraint)).strip();
        String details = info.detail().replace(SchemaConstraints.DETAIL_SEPARATOR, "\n");
        return new ConstraintRow(kind, shorten(text), withOrigin(info.kind() + " " + info.name() + "\n" + details, origin));
    }

    /**
     * Creates the row of an assertion.
     *
     * @param test   its XPath
     * @param origin where it comes from if not the card's own declaration, or null
     * @return its row
     */
    static ConstraintRow assertion(String test, String origin) {
        String oneLine = normalize(test);
        return new ConstraintRow(Kind.ASSERT, shorten(oneLine), withOrigin("assert\n" + test.strip(), origin));
    }

    /** @return the XPath with its line breaks and indentation collapsed */
    static String normalize(String xpath) {
        return xpath.strip().replaceAll("\\s+", " ");
    }

    private static String fieldsOf(XsdIdentityConstraint constraint) {
        List<String> fields = constraint.getFields().stream()
                .map(XsdField::getXpath).filter(Objects::nonNull).toList();
        return String.join(", ", fields);
    }

    private static String shorten(String text) {
        return text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH - 1) + "…";
    }

    private static String withOrigin(String text, String origin) {
        String stripped = text.strip();
        return origin == null ? stripped : stripped + "\n" + origin;
    }
}
