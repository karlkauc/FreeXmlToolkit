package org.fxt.freexmltoolkit.controls.v2.xmleditor.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;

/**
 * Regression tests for {@link XsdSchemaAdapter#resolveElement(String)}: the schema element
 * (and thus the Properties-pane documentation) must be resolved by the FULL instance XPath.
 * The old fallback returned the first map entry with a matching local name, which showed the
 * documentation of a wrong same-named element (e.g. the many "Amount" declarations of FundsXML).
 */
class XsdSchemaAdapterResolveTest {

    private XsdSchemaAdapter adapter;

    @BeforeEach
    void setUp() {
        XsdDocumentationData data = new XsdDocumentationData();

        // /Root
        //   /Root/A
        //     /Root/A/CHOICE_5          (synthetic compositor segment, only in map keys)
        //       /Root/A/CHOICE_5/Amount ("Amount of A")
        //   /Root/B
        //     /Root/B/Amount            ("Amount of B")
        //     /Root/B/Unique            ("the only Unique")
        element(data, "/Root", "Root", null, "/Root/A", "/Root/B");
        element(data, "/Root/A", "A", null, "/Root/A/CHOICE_5");
        element(data, "/Root/A/CHOICE_5", "CHOICE", null, "/Root/A/CHOICE_5/Amount");
        element(data, "/Root/A/CHOICE_5/Amount", "Amount", "Amount of A");
        element(data, "/Root/B", "B", null, "/Root/B/Amount", "/Root/B/Unique");
        element(data, "/Root/B/Amount", "Amount", "Amount of B");
        element(data, "/Root/B/Unique", "Unique", "the only Unique");

        adapter = new XsdSchemaAdapter();
        adapter.setXsdDocumentationData(data);
    }

    private static void element(XsdDocumentationData data, String xpath, String name,
                                String documentation, String... children) {
        XsdExtendedElement el = new XsdExtendedElement();
        el.setCurrentXpath(xpath);
        el.setElementName(name);
        if (documentation != null) {
            el.addDocumentation(new XsdExtendedElement.DocumentationInfo("default", documentation));
        }
        for (String child : children) {
            el.addChild(child);
        }
        data.putExtendedXsdElement(xpath, el);
    }

    @Test
    void resolvesByExactFullPath() {
        XsdExtendedElement el = adapter.resolveElement("/Root/B/Amount");
        assertEquals("/Root/B/Amount", el.getCurrentXpath());
    }

    @Test
    void stripsPositionalPredicates() {
        XsdExtendedElement el = adapter.resolveElement("/Root/B[1]/Amount[3]");
        assertEquals("/Root/B/Amount", el.getCurrentXpath());
    }

    @Test
    void descendsThroughSyntheticCompositorSegments() {
        // The instance path has no CHOICE_5 step — the walk must still find A's Amount,
        // NOT fall back to the first same-named entry (B's Amount).
        XsdExtendedElement el = adapter.resolveElement("/Root/A/Amount");
        assertEquals("/Root/A/CHOICE_5/Amount", el.getCurrentXpath());
    }

    @Test
    void documentationComesFromTheCorrectSameNamedElement() {
        assertEquals("Amount of A", plain(adapter.getElementDocumentation("/Root/A/Amount").orElseThrow()));
        assertEquals("Amount of B", plain(adapter.getElementDocumentation("/Root/B/Amount").orElseThrow()));
    }

    @Test
    void neverGuessesAmongAmbiguousSameNamedElements() {
        // /Root/C does not exist and "Amount" is ambiguous — no guessing allowed.
        assertNull(adapter.resolveElement("/Root/C/Amount"));
        assertTrue(adapter.getElementDocumentation("/Root/C/Amount").isEmpty());
        assertTrue(adapter.getElementTypeInfo("/Root/C/Amount").isEmpty());
    }

    @Test
    void unambiguousNameStillResolvesAsLastResort() {
        // Wrong path, but "Unique" exists exactly once in the whole schema.
        XsdExtendedElement el = adapter.resolveElement("/Root/WrongPath/Unique");
        assertEquals("/Root/B/Unique", el.getCurrentXpath());
    }

    @Test
    void elementTypeInfoCarriesTheCorrectDocumentation() {
        var info = adapter.getElementTypeInfo("/Root/A/Amount[2]").orElseThrow();
        assertTrue(info.documentation().contains("Amount of A"),
                "expected A's documentation, got: " + info.documentation());
    }

    /** Strips the HTML the documentation renderer wraps around the raw text. */
    private static String plain(String html) {
        return html.replaceAll("<[^>]+>", "").trim();
    }
}
