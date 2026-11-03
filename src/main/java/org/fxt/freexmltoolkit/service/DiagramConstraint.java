package org.fxt.freexmltoolkit.service;

import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.view.ConstraintRow;
import org.fxt.freexmltoolkit.domain.IdentityConstraint;
import org.fxt.freexmltoolkit.domain.XsdAssertion;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;

/**
 * One line an element box of the documentation diagram shows for an identity constraint or an
 * assertion. The documentation counterpart of the Graphic view's {@link ConstraintRow}: same
 * labels, same texts, same length limit.
 *
 * @param kind    what it is
 * @param text    the shortened line
 * @param tooltip the full details
 */
public record DiagramConstraint(ConstraintRow.Kind kind, String text, String tooltip) {

    /**
     * Collects the lines of an element: its identity constraints first, then the assertions of its
     * type, each in declaration order.
     *
     * @param element the element
     * @return its lines, empty if it has none
     */
    public static List<DiagramConstraint> of(XsdExtendedElement element) {
        List<DiagramConstraint> rows = new ArrayList<>();
        if (element == null) {
            return rows;
        }
        if (element.getIdentityConstraints() != null) {
            for (IdentityConstraint constraint : element.getIdentityConstraints()) {
                rows.add(identity(constraint));
            }
        }
        if (element.getAssertions() != null) {
            for (XsdAssertion assertion : element.getAssertions()) {
                if (assertion.getTest() != null && !assertion.getTest().isBlank()) {
                    rows.add(new DiagramConstraint(ConstraintRow.Kind.ASSERT,
                            ConstraintRow.shorten(ConstraintRow.normalize(assertion.getTest())),
                            "assert\n" + assertion.getTest().strip()));
                }
            }
        }
        return rows;
    }

    private static DiagramConstraint identity(IdentityConstraint constraint) {
        String name = constraint.getName() == null ? "" : constraint.getName();
        List<String> fields = constraint.getFields() == null ? List.of() : constraint.getFields();
        ConstraintRow.Kind kind;
        String keyword;
        switch (constraint.getType()) {
            case KEY -> {
                kind = ConstraintRow.Kind.KEY;
                keyword = "key";
            }
            case KEYREF -> {
                kind = ConstraintRow.Kind.KEYREF;
                keyword = "keyref";
            }
            default -> {
                kind = ConstraintRow.Kind.UNIQUE;
                keyword = "unique";
            }
        }
        String refer = constraint.getRefer();
        String text = kind == ConstraintRow.Kind.KEYREF && refer != null && !refer.isBlank()
                ? name + " → " + refer
                : (name + "  " + String.join(", ", fields)).strip();

        StringBuilder tooltip = new StringBuilder(keyword).append(' ').append(name);
        if (constraint.getSelector() != null && !constraint.getSelector().isBlank()) {
            tooltip.append("\nselector: ").append(constraint.getSelector());
        }
        if (!fields.isEmpty()) {
            tooltip.append("\nfields: ").append(String.join(", ", fields));
        }
        if (refer != null && !refer.isBlank()) {
            tooltip.append("\nrefer: ").append(refer);
        }
        return new DiagramConstraint(kind, ConstraintRow.shorten(text), tooltip.toString().strip());
    }
}
