package org.fxt.freexmltoolkit.service.standards;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares a document's XML declaration with the chosen XML version. The parser itself always follows the
 * declaration (there is no way to read an XML 1.1 document as 1.0), so the choice is enforced here:
 * XML 1.0 selected and the document declares 1.1 → an error. XML 1.1 selected accepts both (an XML 1.0
 * document is a valid input of an XML 1.1 processor and is read by the 1.0 rules).
 */
public final class XmlVersionCheck {

    private static final Pattern DECLARATION = Pattern.compile(
            "\\A\\uFEFF?<\\?xml\\s+version\\s*=\\s*([\"'])([^\"']*)\\1");

    /** One finding (severity "error"), always on line 1. */
    public record Finding(String severity, String message) {
    }

    private XmlVersionCheck() {
    }

    /** The version a document declares: "1.0" without declaration. */
    public static String declaredVersion(String xml) {
        if (xml == null) {
            return "1.0";
        }
        Matcher m = DECLARATION.matcher(xml);
        return m.find() ? m.group(2).strip() : "1.0";
    }

    /** Findings of {@code xml} against {@code mode}; empty for {@link XmlVersionMode#AUTO} and matching documents. */
    public static List<Finding> check(String xml, XmlVersionMode mode) {
        if (mode == null || mode == XmlVersionMode.AUTO) {
            return List.of();
        }
        String declared = declaredVersion(xml);
        if (mode == XmlVersionMode.V1_0 && !"1.0".equals(declared)) {
            return List.of(new Finding("error", "The document declares XML version " + declared
                    + " but XML 1.0 is selected."));
        }
        if (mode == XmlVersionMode.V1_1 && !"1.0".equals(declared) && !"1.1".equals(declared)) {
            return List.of(new Finding("error", "The document declares XML version " + declared
                    + ", which is neither 1.0 nor 1.1."));
        }
        return List.of();
    }
}
