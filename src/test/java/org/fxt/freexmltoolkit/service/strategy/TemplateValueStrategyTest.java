package org.fxt.freexmltoolkit.service.strategy;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.Map;

import org.fxt.freexmltoolkit.service.GenerationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TemplateValueStrategy")
class TemplateValueStrategyTest {

    private final TemplateValueStrategy strategy = new TemplateValueStrategy();

    @Test
    @DisplayName("Sequence placeholder in template")
    void seqPlaceholder() {
        var ctx = new GenerationContext();
        ctx.setCurrentXPath("/order/id");
        String result = strategy.resolve(null, Map.of("pattern", "ORD-{seq:4}"), ctx);
        assertEquals("ORD-0001", result);
    }

    @Test
    @DisplayName("Date placeholder uses current year")
    void datePlaceholder() {
        var ctx = new GenerationContext();
        ctx.setCurrentXPath("/date");
        String result = strategy.resolve(null, Map.of("pattern", "DOC-{date:yyyy}"), ctx);
        assertEquals("DOC-" + LocalDate.now().getYear(), result);
    }

    @Test
    @DisplayName("Random placeholder generates N digits")
    void randomPlaceholder() {
        var ctx = new GenerationContext();
        ctx.setCurrentXPath("/rand");
        String result = strategy.resolve(null, Map.of("pattern", "R-{random:4}"), ctx);
        assertTrue(result.startsWith("R-"));
        String numberPart = result.substring(2);
        assertEquals(4, numberPart.length());
        assertDoesNotThrow(() -> Integer.parseInt(numberPart));
    }

    @Test
    @DisplayName("Ref placeholder copies from context")
    void refPlaceholder() {
        var ctx = new GenerationContext();
        ctx.setCurrentXPath("/ref");
        ctx.recordGeneratedValue("/order/@id", "ORD-0042");
        String result = strategy.resolve(null, Map.of("pattern", "REF-{ref:/order/@id}"), ctx);
        assertEquals("REF-ORD-0042", result);
    }

    @Test
    @DisplayName("Ref placeholder with missing reference returns empty")
    void refPlaceholderMissing() {
        var ctx = new GenerationContext();
        ctx.setCurrentXPath("/ref2");
        String result = strategy.resolve(null, Map.of("pattern", "REF-{ref:/missing}"), ctx);
        assertEquals("REF-", result);
    }

    @Test
    @DisplayName("File placeholder shows file index")
    void filePlaceholder() {
        var ctx = new GenerationContext();
        ctx.setCurrentXPath("/file");
        ctx.setFileIndex(4);
        String result = strategy.resolve(null, Map.of("pattern", "file_{file:3}"), ctx);
        assertEquals("file_005", result);
    }

    @Test
    @DisplayName("Combined placeholders")
    void combinedPlaceholders() {
        var ctx = new GenerationContext();
        ctx.setCurrentXPath("/combined");
        String result = strategy.resolve(null, Map.of("pattern", "ORD-{seq:3}-{date:yyyy}"), ctx);
        assertTrue(result.startsWith("ORD-001-"));
        assertTrue(result.endsWith(String.valueOf(LocalDate.now().getYear())));
    }

    @Test
    @DisplayName("Empty pattern returns empty string")
    void emptyPattern() {
        var ctx = new GenerationContext();
        assertEquals("", strategy.resolve(null, Map.of("pattern", ""), ctx));
    }

    @Test
    @DisplayName("Missing pattern key returns empty string")
    void missingPattern() {
        var ctx = new GenerationContext();
        assertEquals("", strategy.resolve(null, Map.of(), ctx));
    }

    @Test
    @DisplayName("Invalid date format falls back to ISO date")
    void invalidDateFormat() {
        var ctx = new GenerationContext();
        ctx.setCurrentXPath("/baddate");
        String result = strategy.resolve(null, Map.of("pattern", "{date:INVALID_FORMAT_XYZ}"), ctx);
        // Should fall back to ISO date format
        assertFalse(result.isEmpty());
    }
}
