package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Sequence-position-aware child suggestions: only elements that may legally appear at the
 * caret are offered, given the siblings already present before and after it.
 */
class AllowedChildrenCalculatorTest {

    private XsdDocumentationData data;
    private Document dom;

    @BeforeEach
    void setUp() throws Exception {
        data = new XsdDocumentationData();
        dom = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    // /Address/SEQUENCE_1 : street, city, postalCode?, country
    private void sequenceSchema() {
        node("/Address", "Address", "complexType", "1", "1", "/Address/SEQUENCE_1");
        node("/Address/SEQUENCE_1", "SEQUENCE", "sequence", "1", "1",
                "/Address/SEQUENCE_1/street", "/Address/SEQUENCE_1/city",
                "/Address/SEQUENCE_1/postalCode", "/Address/SEQUENCE_1/country");
        node("/Address/SEQUENCE_1/street", "street", "element", "1", "1");
        node("/Address/SEQUENCE_1/city", "city", "element", "1", "1");
        node("/Address/SEQUENCE_1/postalCode", "postalCode", "element", "0", "1");
        node("/Address/SEQUENCE_1/country", "country", "element", "1", "1");
    }

    @Test
    void emptySequenceOffersOnlyUpToFirstRequiredParticle() {
        sequenceSchema();
        assertEquals(List.of("street"), names(compute(List.of(), List.of())));
    }

    @Test
    void afterRequiredSiblingOffersTheNextParticles() {
        sequenceSchema();
        assertEquals(List.of("city"), names(compute(List.of("street"), List.of())));
        // postalCode is optional, so country is reachable too
        assertEquals(List.of("postalCode", "country"), names(compute(List.of("street", "city"), List.of())));
    }

    @Test
    void repeatableParticleStaysOfferedUntilMaxOccurs() {
        node("/Inv", "Inv", "complexType", "1", "1", "/Inv/SEQUENCE_1");
        node("/Inv/SEQUENCE_1", "SEQUENCE", "sequence", "1", "1", "/Inv/SEQUENCE_1/line", "/Inv/SEQUENCE_1/total");
        node("/Inv/SEQUENCE_1/line", "line", "element", "1", "unbounded");
        node("/Inv/SEQUENCE_1/total", "total", "element", "1", "1");
        assertEquals(List.of("line", "total"), names(compute("/Inv", List.of("line", "line"), List.of())));
        assertEquals(List.of(), names(compute("/Inv", List.of("line", "total"), List.of())));
    }

    @Test
    void siblingsAfterCaretCapTheSuggestions() {
        sequenceSchema();
        // caret between city and country: only the optional postalCode fits (country is already there)
        assertEquals(List.of("postalCode"), names(compute(List.of("street", "city"), List.of("country"))));
        // caret between street and country: city is required next
        assertEquals(List.of("city"), names(compute(List.of("street"), List.of("country"))));
    }

    @Test
    void choiceOffersNothingOnceOneAlternativeIsPresent() {
        node("/P", "P", "complexType", "1", "1", "/P/CHOICE_1");
        node("/P/CHOICE_1", "CHOICE", "choice", "1", "1", "/P/CHOICE_1/a", "/P/CHOICE_1/b");
        node("/P/CHOICE_1/a", "a", "element", "1", "1");
        node("/P/CHOICE_1/b", "b", "element", "1", "1");
        assertEquals(List.of("a", "b"), names(compute("/P", List.of(), List.of())));
        assertEquals(List.of(), names(compute("/P", List.of("a"), List.of())));
    }

    @Test
    void nestedChoiceInsideSequenceActsAsOneParticle() {
        node("/P", "P", "complexType", "1", "1", "/P/SEQUENCE_1");
        node("/P/SEQUENCE_1", "SEQUENCE", "sequence", "1", "1", "/P/SEQUENCE_1/head", "/P/SEQUENCE_1/CHOICE_2", "/P/SEQUENCE_1/tail");
        node("/P/SEQUENCE_1/head", "head", "element", "1", "1");
        node("/P/SEQUENCE_1/CHOICE_2", "CHOICE", "choice", "1", "1", "/P/SEQUENCE_1/CHOICE_2/a", "/P/SEQUENCE_1/CHOICE_2/b");
        node("/P/SEQUENCE_1/CHOICE_2/a", "a", "element", "1", "1");
        node("/P/SEQUENCE_1/CHOICE_2/b", "b", "element", "1", "1");
        node("/P/SEQUENCE_1/tail", "tail", "element", "1", "1");
        assertEquals(List.of("a", "b"), names(compute("/P", List.of("head"), List.of())));
        assertEquals(List.of("tail"), names(compute("/P", List.of("head", "b"), List.of())));
    }

    @Test
    void allOffersEveryChildNotYetPresent() {
        node("/P", "P", "complexType", "1", "1", "/P/ALL_1");
        node("/P/ALL_1", "ALL", "all", "1", "1", "/P/ALL_1/x", "/P/ALL_1/y");
        node("/P/ALL_1/x", "x", "element", "1", "1");
        node("/P/ALL_1/y", "y", "element", "1", "1");
        assertEquals(List.of("x"), names(compute("/P", List.of("y"), List.of())));
    }

    @Test
    void unknownSiblingsAreIgnored() {
        sequenceSchema();
        assertEquals(List.of("street"), names(compute(List.of("bogus"), List.of())));
    }

    // -- helpers --------------------------------------------------------------------------

    private List<XsdExtendedElement> compute(List<String> before, List<String> after) {
        return compute("/Address", before, after);
    }

    private List<XsdExtendedElement> compute(String parent, List<String> before, List<String> after) {
        return new AllowedChildrenCalculator(data).compute(data.getExtendedXsdElementMap().get(parent), before, after);
    }

    private static List<String> names(List<XsdExtendedElement> els) {
        return els.stream().map(XsdExtendedElement::getElementName).toList();
    }

    private void node(String xpath, String name, String xsdTag, String min, String max, String... children) {
        XsdExtendedElement el = new XsdExtendedElement();
        el.setCurrentXpath(xpath);
        el.setElementName(name);
        Element e = dom.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:" + xsdTag);
        if (!"1".equals(min)) e.setAttribute("minOccurs", min);
        if (!"1".equals(max)) e.setAttribute("maxOccurs", max);
        el.setCurrentNode(e);
        for (String c : children) el.addChild(c);
        data.putExtendedXsdElement(xpath, el);
    }
}
