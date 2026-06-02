package org.fxt.freexmltoolkit.controls.shell.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.fxt.freexmltoolkit.controls.v2.model.XsdAssert;
import org.fxt.freexmltoolkit.controls.v2.model.XsdField;
import org.fxt.freexmltoolkit.controls.v2.model.XsdIdentityConstraint;
import org.fxt.freexmltoolkit.controls.v2.model.XsdKeyRef;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * UI-free collection of a node's identity constraints (key / keyref / unique) and XSD 1.1
 * assertions, for the inspector's read-only Constraints section. These have no V2 editing
 * commands yet, so they are surfaced for inspection only.
 */
public final class SchemaConstraints {

    private SchemaConstraints() {
    }

    /** A single constraint, flattened for display. */
    public record ConstraintInfo(String kind, String name, String detail) {
    }

    /** @return the identity constraints and assertions declared directly under the node. */
    public static List<ConstraintInfo> collect(XsdNode node) {
        List<ConstraintInfo> out = new ArrayList<>();
        if (node == null) {
            return out;
        }
        for (XsdNode child : node.getChildren()) {
            switch (child.getNodeType()) {
                case KEY -> out.add(identity("key", (XsdIdentityConstraint) child, null));
                case UNIQUE -> out.add(identity("unique", (XsdIdentityConstraint) child, null));
                case KEYREF -> out.add(identity("keyref", (XsdKeyRef) child, ((XsdKeyRef) child).getRefer()));
                case ASSERT -> {
                    String test = ((XsdAssert) child).getTest();
                    out.add(new ConstraintInfo("assert", "", test == null ? "" : test));
                }
                default -> {
                    // not a constraint
                }
            }
        }
        return out;
    }

    private static ConstraintInfo identity(String kind, XsdIdentityConstraint ic, String refer) {
        StringBuilder detail = new StringBuilder();
        if (ic.getSelector() != null && ic.getSelector().getXpath() != null) {
            detail.append("selector: ").append(ic.getSelector().getXpath());
        }
        List<String> fields = ic.getFields().stream()
                .map(XsdField::getXpath).filter(Objects::nonNull).toList();
        if (!fields.isEmpty()) {
            append(detail, "fields: " + String.join(", ", fields));
        }
        if (refer != null && !refer.isBlank()) {
            append(detail, "refer: " + refer);
        }
        return new ConstraintInfo(kind, ic.getName() == null ? "" : ic.getName(), detail.toString());
    }

    private static void append(StringBuilder sb, String part) {
        if (sb.length() > 0) {
            sb.append("  ·  ");
        }
        sb.append(part);
    }
}
