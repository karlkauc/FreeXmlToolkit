package org.fxt.freexmltoolkit.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.fxt.freexmltoolkit.domain.IdentityConstraint;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.junit.jupiter.api.Test;

/**
 * Correctness gate for processXsd optimizations: the canonical serialization of the
 * expanded-element map (on FundsXML_428.xsd) must hash to a fixed value. Any change to
 * the produced output changes the hash and fails the test.
 */
class ProcessXsdEquivalenceTest {

    private static final Path BIG_XSD = Path.of("src/test/resources/FundsXML_428.xsd");

    // Captured from the CURRENT (pre-optimization) code in Task 1 step 4. DO NOT edit after capture.
    private static final String EXPECTED_SHA256 = "9c43873e2920fb7fbe574dbefe8a0e75b014e5076d239e6fcb492670f3d7c178";

    @Test
    void expandedElementMapIsByteIdentical() throws Exception {
        XsdDocumentationService svc = new XsdDocumentationService();
        svc.setXsdFilePath(BIG_XSD.toAbsolutePath().toString());
        svc.processXsd(Boolean.FALSE);

        String canonical = canonicalize(svc.xsdDocumentationData.getExtendedXsdElementMap());
        String actual = sha256(canonical);
        System.out.println("PROCESSXSD_CANONICAL_SHA256=" + actual + " entries="
                + svc.xsdDocumentationData.getExtendedXsdElementMap().size());
        assertEquals(EXPECTED_SHA256, actual,
                "processXsd output changed — an optimization altered the expanded-element map");
    }

    /** Deterministic, order-independent serialization of the entry key fields. */
    static String canonicalize(Map<String, XsdExtendedElement> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, XsdExtendedElement> e : new TreeMap<>(map).entrySet()) {
            XsdExtendedElement x = e.getValue();
            List<String> children = x.getChildren() == null ? List.of() : x.getChildren();
            List<String> sortedChildren = new java.util.ArrayList<>(children);
            java.util.Collections.sort(sortedChildren);
            sb.append(e.getKey()).append('')
              .append(nz(x.getElementName())).append('')
              .append(nz(x.getElementType())).append('')
              .append(nz(x.getParentXpath())).append('')
              .append(x.getLevel()).append('')
              // NOTE: getDisplaySampleData() is intentionally EXCLUDED — sample values are
              // generated with ThreadLocalRandom/Math.random() (XsdSampleDataGenerator) and
              // are non-deterministic run-to-run. Including them made the golden hash
              // unstable. The remaining fields are XPath/structure metadata and reproducible.
              .append(restr(x.getRestrictionInfo())).append('')
              .append(String.valueOf(x.getDocumentations())).append('')
              .append(idc(x.getIdentityConstraints())).append('')
              .append(sortedChildren)
              .append('');
        }
        return sb.toString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Deterministic serialization of a RestrictionInfo (its facets map order is not guaranteed). */
    private static String restr(XsdExtendedElement.RestrictionInfo r) {
        if (r == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("base=").append(nz(r.base())).append("{");
        java.util.Map<String, java.util.List<String>> facets =
                r.facets() == null ? java.util.Map.of() : r.facets();
        for (String key : new java.util.TreeSet<>(facets.keySet())) {
            java.util.List<String> vals = new java.util.ArrayList<>(facets.get(key));
            java.util.Collections.sort(vals);
            sb.append(key).append('=').append(vals).append(';');
        }
        return sb.append('}').toString();
    }

    /**
     * Deterministic serialization of the identity-constraint list. Each constraint's fields
     * are emitted explicitly (type, name, selector, fields, refer, documentation). The
     * {@code fields} list order is XSD-significant and preserved; the outer list of
     * constraints is sorted by its serialized form so list ordering cannot affect the hash.
     */
    private static String idc(List<IdentityConstraint> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        java.util.List<String> parts = new java.util.ArrayList<>(list.size());
        for (IdentityConstraint c : list) {
            parts.add("{type=" + c.getType()
                    + ",name=" + nz(c.getName())
                    + ",selector=" + nz(c.getSelector())
                    + ",fields=" + c.getFields()
                    + ",refer=" + nz(c.getRefer())
                    + ",doc=" + nz(c.getDocumentation()) + "}");
        }
        java.util.Collections.sort(parts);
        return parts.toString();
    }

    private static String sha256(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : d) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
