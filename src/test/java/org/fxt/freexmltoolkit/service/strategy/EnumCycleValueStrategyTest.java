package org.fxt.freexmltoolkit.service.strategy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.service.GenerationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EnumCycleValueStrategy")
class EnumCycleValueStrategyTest {

    private final EnumCycleValueStrategy strategy = new EnumCycleValueStrategy();

    private XsdExtendedElement createElementWithEnums(List<String> enums) {
        var element = new XsdExtendedElement();
        var facets = new LinkedHashMap<String, List<String>>();
        facets.put("enumeration", enums);
        element.setRestrictionInfo(new XsdExtendedElement.RestrictionInfo("xs:string", facets));
        return element;
    }

    @Test
    @DisplayName("Cycles through enumeration values")
    void cyclesThroughValues() {
        var element = createElementWithEnums(List.of("OPEN", "CLOSED", "PENDING"));
        var ctx = new GenerationContext();
        ctx.setCurrentXPath("/status");

        assertEquals("OPEN", strategy.resolve(element, Map.of(), ctx));
        assertEquals("CLOSED", strategy.resolve(element, Map.of(), ctx));
        assertEquals("PENDING", strategy.resolve(element, Map.of(), ctx));
        assertEquals("OPEN", strategy.resolve(element, Map.of(), ctx)); // wrap-around
    }

    @Test
    @DisplayName("Returns empty for element without enumerations")
    void emptyForNoEnums() {
        var element = new XsdExtendedElement();
        var ctx = new GenerationContext();
        ctx.setCurrentXPath("/noEnum");
        assertEquals("", strategy.resolve(element, Map.of(), ctx));
    }

    @Test
    @DisplayName("Returns empty for null element")
    void emptyForNull() {
        var ctx = new GenerationContext();
        assertEquals("", strategy.resolve(null, Map.of(), ctx));
    }

    @Test
    @DisplayName("Independent cycling per XPath")
    void independentPerXPath() {
        var statusElement = createElementWithEnums(List.of("A", "B"));
        var typeElement = createElementWithEnums(List.of("X", "Y", "Z"));
        var ctx = new GenerationContext();

        ctx.setCurrentXPath("/status");
        assertEquals("A", strategy.resolve(statusElement, Map.of(), ctx));

        ctx.setCurrentXPath("/type");
        assertEquals("X", strategy.resolve(typeElement, Map.of(), ctx));

        ctx.setCurrentXPath("/status");
        assertEquals("B", strategy.resolve(statusElement, Map.of(), ctx));

        ctx.setCurrentXPath("/type");
        assertEquals("Y", strategy.resolve(typeElement, Map.of(), ctx));
    }
}
