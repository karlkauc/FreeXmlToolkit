package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for TemplateEngine - template processing and suggestion engine.
 * Note: This tests the singleton instance and its core functionality.
 */
class TemplateEngineTest {

    private TemplateEngine templateEngine;

    @BeforeEach
    void setUp() throws Exception {
        // Reset singleton instance using reflection for clean test state
        java.lang.reflect.Field instanceField = TemplateEngine.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        templateEngine = TemplateEngine.getInstance();
    }

    @Test
    @DisplayName("Should get singleton instance")
    void testGetInstance() {
        TemplateEngine instance1 = TemplateEngine.getInstance();
        TemplateEngine instance2 = TemplateEngine.getInstance();

        assertSame(instance1, instance2, "Should return same singleton instance");
    }

    @Test
    @DisplayName("Should return error for non-existent template")
    void testProcessNonExistentTemplate() {
        // Arrange
        String templateId = "non-existent-template-id";
        Map<String, String> parameters = new HashMap<>();

        // Act
        TemplateEngine.TemplateProcessingResult result =
            templateEngine.processTemplate(templateId, parameters);

        // Assert
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().get(0).contains("not found"));
    }

    @Test
    @DisplayName("Should validate template with missing required parameters")
    void testValidateTemplateMissingParameters() {
        // Arrange
        String templateId = "test-template";
        Map<String, String> emptyParameters = new HashMap<>();

        // Act
        TemplateEngine.TemplateValidationResult result =
            templateEngine.validateTemplate(templateId, emptyParameters);

        // Assert
        assertNotNull(result);
        // Since template doesn't exist, it should return invalid
        assertFalse(result.valid());
        assertFalse(result.errors().isEmpty());
    }

    @Test
    @DisplayName("Should get empty template suggestions for null context")
    void testGetTemplateSuggestions() {
        // Arrange
        TemplateEngine.TemplateContext context = new TemplateEngine.TemplateContext();

        // Act
        List<TemplateEngine.TemplateSuggestion> suggestions =
            templateEngine.getTemplateSuggestions(context);

        // Assert
        assertNotNull(suggestions);
        // Should return empty list for empty context
        assertTrue(suggestions.isEmpty());
    }

    @Test
    @DisplayName("Should create template context")
    void testTemplateContext() {
        // Arrange & Act
        TemplateEngine.TemplateContext context = new TemplateEngine.TemplateContext();

        context.setCurrentElement("element");
        context.setCursorPosition(10);
        context.setXmlContent("<root></root>");

        Set<String> namespaces = new HashSet<>();
        namespaces.add("http://example.com/ns");
        context.setAvailableNamespaces(namespaces);

        Map<String, String> variables = new HashMap<>();
        variables.put("var1", "value1");
        context.setContextVariables(variables);

        // Assert
        assertEquals("element", context.getCurrentElement());
        assertEquals(10, context.getCursorPosition());
        assertEquals("<root></root>", context.getXmlContent());
        assertEquals(namespaces, context.getAvailableNamespaces());
        assertEquals(variables, context.getContextVariables());
    }

    @Test
    @DisplayName("Should get contextual suggestions for XML content")
    void testGetContextualSuggestions() {
        // Arrange
        String xmlContent = """
            <root>
                <child>
                    |
                </child>
            </root>
            """;
        int cursorPosition = xmlContent.indexOf("|");
        Set<String> namespaces = new HashSet<>();
        namespaces.add("http://www.w3.org/2001/XMLSchema");

        // Act
        List<TemplateEngine.TemplateSuggestion> suggestions =
            templateEngine.getContextualSuggestions(xmlContent, cursorPosition, namespaces);

        // Assert
        assertNotNull(suggestions);
        // Should return suggestions based on context
        // Empty for now as no templates are loaded
    }

    @Test
    @DisplayName("Should get parameter suggestions for template")
    void testGetParameterSuggestions() {
        // Arrange
        String templateId = "test-template";
        TemplateEngine.TemplateContext context = new TemplateEngine.TemplateContext();
        context.setCurrentElement("element");

        // Act
        Map<String, List<String>> suggestions =
            templateEngine.getParameterSuggestions(templateId, context);

        // Assert
        assertNotNull(suggestions);
        // Should return empty map for non-existent template
        assertTrue(suggestions.isEmpty());
    }

    @Test
    @DisplayName("Should get template preview for non-existent template")
    void testGetTemplatePreview() {
        // Arrange
        String templateId = "non-existent";
        Map<String, String> parameters = new HashMap<>();

        // Act
        String preview = templateEngine.getTemplatePreview(templateId, parameters);

        // Assert
        assertNotNull(preview);
        assertEquals("Template not found", preview);
    }

    @Test
    @DisplayName("Should clear processing cache")
    void testClearCache() {
        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> templateEngine.clearCache());
    }

    @Test
    @DisplayName("Should get cache statistics")
    void testGetCacheStats() {
        // Act
        Map<String, Object> stats = templateEngine.getCacheStats();

        // Assert
        assertNotNull(stats);
        assertTrue(stats.containsKey("cacheSize"));
        assertTrue(stats.containsKey("cacheHitRate"));
    }

    @Test
    @DisplayName("Should get performance statistics")
    void testGetPerformanceStats() {
        // Act
        Map<String, TemplateEngine.ProcessingStats> stats =
            templateEngine.getPerformanceStats();

        // Assert
        assertNotNull(stats);
        // Should be empty initially
        assertTrue(stats.isEmpty());
    }

    @Test
    @DisplayName("Should get slowest templates")
    void testGetSlowestTemplates() {
        // Act
        List<TemplateEngine.TemplatePerformanceInfo> slowest =
            templateEngine.getSlowestTemplates(10);

        // Assert
        assertNotNull(slowest);
        // Should be empty initially
        assertTrue(slowest.isEmpty());
    }

    @Test
    @DisplayName("Should process batch templates")
    void testProcessBatchTemplates() {
        // Arrange
        List<TemplateEngine.TemplateBatchItem> batchItems = new ArrayList<>();
        batchItems.add(new TemplateEngine.TemplateBatchItem("template1", new HashMap<>()));
        batchItems.add(new TemplateEngine.TemplateBatchItem("template2", new HashMap<>()));

        // Act
        List<TemplateEngine.TemplateProcessingResult> results =
            templateEngine.processTemplates(batchItems);

        // Assert
        assertNotNull(results);
        assertEquals(2, results.size());
        // All should fail since templates don't exist
        results.forEach(result -> assertFalse(result.isSuccess()));
    }

    @Test
    @DisplayName("Should test ProcessingStats recording")
    void testProcessingStatsRecording() {
        // Arrange
        TemplateEngine.ProcessingStats stats = new TemplateEngine.ProcessingStats();

        // Act
        stats.recordProcessing(100, true);
        stats.recordProcessing(200, true);
        stats.recordProcessing(150, false);

        // Assert
        assertEquals(3, stats.getTotalProcessings());
        assertEquals(2, stats.getSuccessfulProcessings());
        assertEquals(450, stats.getTotalProcessingTime());
        assertEquals(150.0, stats.getAverageProcessingTime(), 0.001);
        assertEquals(0.666, stats.getSuccessRate(), 0.01);
        assertEquals(100, stats.getMinProcessingTime());
        assertEquals(200, stats.getMaxProcessingTime());
        assertNotNull(stats.getFirstProcessing());
        assertNotNull(stats.getLastProcessing());
    }

    @Test
    @DisplayName("Should handle empty ProcessingStats")
    void testEmptyProcessingStats() {
        // Arrange
        TemplateEngine.ProcessingStats stats = new TemplateEngine.ProcessingStats();

        // Assert
        assertEquals(0, stats.getTotalProcessings());
        assertEquals(0, stats.getSuccessfulProcessings());
        assertEquals(0, stats.getTotalProcessingTime());
        assertEquals(0.0, stats.getAverageProcessingTime());
        assertEquals(0.0, stats.getSuccessRate());
        assertEquals(0, stats.getMinProcessingTime());
        assertEquals(0, stats.getMaxProcessingTime());
        assertNull(stats.getFirstProcessing());
        assertNull(stats.getLastProcessing());
    }

    @Test
    @DisplayName("Should create TemplateProcessingResult with success")
    void testTemplateProcessingResultSuccess() {
        // Arrange
        String content = "<test>content</test>";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("param1", "value1");

        // Act
        TemplateEngine.TemplateProcessingResult result =
            TemplateEngine.TemplateProcessingResult.success(content, null, parameters);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(content, result.getContent());
        assertEquals(parameters, result.getParameters());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    @DisplayName("Should create TemplateProcessingResult with error")
    void testTemplateProcessingResultError() {
        // Arrange
        String errorMessage = "Template processing failed";

        // Act
        TemplateEngine.TemplateProcessingResult result =
            TemplateEngine.TemplateProcessingResult.error(errorMessage);

        // Assert
        assertFalse(result.isSuccess());
        assertNull(result.getContent());
        assertEquals(1, result.getErrors().size());
        assertEquals(errorMessage, result.getErrors().get(0));
    }

    @Test
    @DisplayName("Should create TemplateProcessingResult with validation errors")
    void testTemplateProcessingResultValidationError() {
        // Arrange
        List<String> errors = Arrays.asList("Error 1", "Error 2", "Error 3");

        // Act
        TemplateEngine.TemplateProcessingResult result =
            TemplateEngine.TemplateProcessingResult.validationError(errors);

        // Assert
        assertFalse(result.isSuccess());
        assertNull(result.getContent());
        assertEquals(errors, result.getErrors());
    }

    @Test
    @DisplayName("Should set processing time on result")
    void testSetProcessingTime() {
        // Arrange
        TemplateEngine.TemplateProcessingResult result =
            TemplateEngine.TemplateProcessingResult.success("content", null, new HashMap<>());

        // Act
        result.setProcessingTimeMs(150);

        // Assert
        assertEquals(150, result.getProcessingTimeMs());
    }

    @Test
    @DisplayName("Should create TemplateValidationResult")
    void testTemplateValidationResult() {
        // Arrange
        List<String> errors = Arrays.asList("Missing parameter");
        List<String> warnings = Arrays.asList("Optional parameter not provided");

        // Act
        TemplateEngine.TemplateValidationResult result =
            new TemplateEngine.TemplateValidationResult(false, errors, warnings);

        // Assert
        assertFalse(result.valid());
        assertEquals(errors, result.errors());
        assertEquals(warnings, result.warnings());
    }

    @Test
    @DisplayName("Should create TemplateValidationResult without warnings")
    void testTemplateValidationResultWithoutWarnings() {
        // Arrange
        List<String> errors = Arrays.asList("Error");

        // Act
        TemplateEngine.TemplateValidationResult result =
            new TemplateEngine.TemplateValidationResult(false, errors);

        // Assert
        assertFalse(result.valid());
        assertEquals(errors, result.errors());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    @DisplayName("Should create valid TemplateValidationResult")
    void testValidTemplateValidationResult() {
        // Act
        TemplateEngine.TemplateValidationResult result =
            new TemplateEngine.TemplateValidationResult(true, new ArrayList<>(), new ArrayList<>());

        // Assert
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    @DisplayName("Should create TemplateBatchItem")
    void testTemplateBatchItem() {
        // Arrange
        String templateId = "test-id";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("key", "value");

        // Act
        TemplateEngine.TemplateBatchItem item =
            new TemplateEngine.TemplateBatchItem(templateId, parameters);

        // Assert
        assertEquals(templateId, item.templateId());
        assertEquals(parameters, item.parameters());
    }

    @Test
    @DisplayName("Should create TemplatePerformanceInfo")
    void testTemplatePerformanceInfo() {
        // Arrange
        String templateId = "perf-template";
        TemplateEngine.ProcessingStats stats = new TemplateEngine.ProcessingStats();
        stats.recordProcessing(100, true);
        stats.recordProcessing(200, true);

        // Act
        TemplateEngine.TemplatePerformanceInfo perfInfo =
            new TemplateEngine.TemplatePerformanceInfo(templateId, stats);

        // Assert
        assertEquals(templateId, perfInfo.templateId());
        assertEquals(stats, perfInfo.stats());
        assertEquals(150.0, perfInfo.getAverageProcessingTime(), 0.001);
        assertEquals(1.0, perfInfo.getSuccessRate(), 0.001);
        assertEquals(2, perfInfo.getTotalProcessings());
    }
}
