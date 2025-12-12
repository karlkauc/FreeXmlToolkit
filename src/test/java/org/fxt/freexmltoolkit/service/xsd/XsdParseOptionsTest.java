package org.fxt.freexmltoolkit.service.xsd;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link XsdParseOptions}.
 */
class XsdParseOptionsTest {

    @Test
    void defaultOptions_shouldHaveExpectedDefaults() {
        XsdParseOptions options = XsdParseOptions.defaults();

        assertEquals(XsdParseOptions.IncludeMode.PRESERVE_STRUCTURE, options.getIncludeMode());
        assertTrue(options.isResolveImports());
        assertTrue(options.isCacheEnabled());
        assertEquals(Duration.ofHours(24), options.getCacheExpiry());
        assertEquals(50, options.getMaxIncludeDepth());
        assertEquals(Duration.ofSeconds(30), options.getNetworkTimeout());
        assertNull(options.getProgressListener());
        assertNull(options.getWarningHandler());
    }

    @Test
    void forFlattening_shouldSetFlattenMode() {
        XsdParseOptions options = XsdParseOptions.forFlattening();

        assertEquals(XsdParseOptions.IncludeMode.FLATTEN, options.getIncludeMode());
    }

    @Test
    void forPreservingStructure_shouldSetPreserveMode() {
        XsdParseOptions options = XsdParseOptions.forPreservingStructure();

        assertEquals(XsdParseOptions.IncludeMode.PRESERVE_STRUCTURE, options.getIncludeMode());
    }

    @Test
    void builder_shouldAllowCustomConfiguration() {
        XsdParseOptions options = XsdParseOptions.builder()
                .includeMode(XsdParseOptions.IncludeMode.FLATTEN)
                .resolveImports(false)
                .cacheEnabled(false)
                .cacheExpiry(Duration.ofMinutes(30))
                .maxIncludeDepth(100)
                .networkTimeout(Duration.ofSeconds(60))
                .build();

        assertEquals(XsdParseOptions.IncludeMode.FLATTEN, options.getIncludeMode());
        assertFalse(options.isResolveImports());
        assertFalse(options.isCacheEnabled());
        assertEquals(Duration.ofMinutes(30), options.getCacheExpiry());
        assertEquals(100, options.getMaxIncludeDepth());
        assertEquals(Duration.ofSeconds(60), options.getNetworkTimeout());
    }

    @Test
    void builder_maxIncludeDepthLessThanOne_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () ->
                XsdParseOptions.builder().maxIncludeDepth(0).build()
        );
    }

    @Test
    void builder_nullIncludeMode_shouldThrow() {
        assertThrows(NullPointerException.class, () ->
                XsdParseOptions.builder().includeMode(null).build()
        );
    }

    @Test
    void builder_nullCacheExpiry_shouldThrow() {
        assertThrows(NullPointerException.class, () ->
                XsdParseOptions.builder().cacheExpiry(null).build()
        );
    }

    @Test
    void builder_nullNetworkTimeout_shouldThrow() {
        assertThrows(NullPointerException.class, () ->
                XsdParseOptions.builder().networkTimeout(null).build()
        );
    }

    @Test
    void progressListener_shouldReceiveProgressUpdates() {
        AtomicInteger progressCalls = new AtomicInteger(0);
        AtomicReference<String> lastMessage = new AtomicReference<>();

        XsdParseOptions options = XsdParseOptions.builder()
                .progressListener((message, current, total) -> {
                    progressCalls.incrementAndGet();
                    lastMessage.set(message);
                })
                .build();

        options.reportProgress("Processing", 1, 10);

        assertEquals(1, progressCalls.get());
        assertEquals("Processing", lastMessage.get());
    }

    @Test
    void progressListener_shouldNotFailWhenNull() {
        XsdParseOptions options = XsdParseOptions.defaults();

        // Should not throw
        assertDoesNotThrow(() -> options.reportProgress("test", 0, 1));
    }

    @Test
    void warningHandler_shouldReceiveWarnings() {
        AtomicReference<String> lastWarning = new AtomicReference<>();

        XsdParseOptions options = XsdParseOptions.builder()
                .warningHandler(lastWarning::set)
                .build();

        options.reportWarning("Test warning");

        assertEquals("Test warning", lastWarning.get());
    }

    @Test
    void warningHandler_shouldNotFailWhenNull() {
        XsdParseOptions options = XsdParseOptions.defaults();

        // Should not throw
        assertDoesNotThrow(() -> options.reportWarning("test"));
    }

    @Test
    void withIncludeMode_shouldCreateCopyWithDifferentMode() {
        XsdParseOptions original = XsdParseOptions.builder()
                .includeMode(XsdParseOptions.IncludeMode.PRESERVE_STRUCTURE)
                .maxIncludeDepth(75)
                .build();

        XsdParseOptions modified = original.withIncludeMode(XsdParseOptions.IncludeMode.FLATTEN);

        // Original unchanged
        assertEquals(XsdParseOptions.IncludeMode.PRESERVE_STRUCTURE, original.getIncludeMode());

        // Modified has new mode but same other settings
        assertEquals(XsdParseOptions.IncludeMode.FLATTEN, modified.getIncludeMode());
        assertEquals(75, modified.getMaxIncludeDepth());
    }

    @Test
    void equals_shouldCompareByValue() {
        XsdParseOptions options1 = XsdParseOptions.builder()
                .includeMode(XsdParseOptions.IncludeMode.FLATTEN)
                .maxIncludeDepth(50)
                .build();

        XsdParseOptions options2 = XsdParseOptions.builder()
                .includeMode(XsdParseOptions.IncludeMode.FLATTEN)
                .maxIncludeDepth(50)
                .build();

        assertEquals(options1, options2);
        assertEquals(options1.hashCode(), options2.hashCode());
    }

    @Test
    void equals_differentValues_shouldNotBeEqual() {
        XsdParseOptions options1 = XsdParseOptions.builder()
                .includeMode(XsdParseOptions.IncludeMode.FLATTEN)
                .build();

        XsdParseOptions options2 = XsdParseOptions.builder()
                .includeMode(XsdParseOptions.IncludeMode.PRESERVE_STRUCTURE)
                .build();

        assertNotEquals(options1, options2);
    }

    @Test
    void toString_shouldContainKeyValues() {
        XsdParseOptions options = XsdParseOptions.builder()
                .includeMode(XsdParseOptions.IncludeMode.FLATTEN)
                .build();

        String str = options.toString();

        assertTrue(str.contains("FLATTEN"));
        assertTrue(str.contains("includeMode"));
    }
}
