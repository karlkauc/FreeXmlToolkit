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

    /** ASCII SOH (U+0001) — a field separator that can't occur in XSD/XPath text. */
    private static final char SEP = '';

    // Captured from the CURRENT (pre-optimization) code in Task 1 step 4. DO NOT edit after capture.
    // 2026-09-11: a restriction's facets replace its base type's facets of the same name instead of being appended.
    // Exactly two entries changed, both restr only: a pattern declared identically on a type and its base was listed
    // twice (FundsXML4 IndirectReporting country subdivision codes).
    // 2026-09-11: particles with the same name in one compositor get their own key (name[2]). Exactly two entries
    // were added: FundsXML4 UK declares UCITSExistingPerformanceFees and UKAssumedPortfolioReturn twice; before, the
    // second replaced the first, including its documentation.
    private static final String EXPECTED_SHA256 = "d161985a4d7d40f20b7b8b1409f3cb79a18531ebebb9221aeda2918021edfacd";

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
            sb.append(e.getKey()).append(SEP)
              .append(nz(x.getElementName())).append(SEP)
              .append(nz(x.getElementType())).append(SEP)
              .append(nz(x.getParentXpath())).append(SEP)
              .append(x.getLevel()).append(SEP)
              // NOTE: getDisplaySampleData() is intentionally EXCLUDED — sample values are
              // generated with ThreadLocalRandom/Math.random() (XsdSampleDataGenerator) and
              // are non-deterministic run-to-run. Including them made the golden hash
              // unstable. The remaining fields are XPath/structure metadata and reproducible.
              .append(restr(x.getRestrictionInfo())).append(SEP)
              // documentations is an ArrayList in DOM-traversal (document) order — stable across runs, no sort needed.
              .append(String.valueOf(x.getDocumentations())).append(SEP)
              .append(idc(x.getIdentityConstraints())).append(SEP)
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
