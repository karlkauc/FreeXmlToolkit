package org.fxt.freexmltoolkit.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for XmlTemplate domain model.
 */
class XmlTemplateTest {

    private XmlTemplate template;

    @BeforeEach
    void setUp() {
        template = new XmlTemplate("Test Template", "<root>${name}</root>", "Basic");
    }

    // =========================================================================
    // Construction Tests
    // =========================================================================

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Default constructor sets defaults")
        void defaultConstructor() {
            XmlTemplate t = new XmlTemplate();
            assertNotNull(t.getId());
            assertNotNull(t.getCreated());
            assertNotNull(t.getLastModified());
            assertEquals("1.0", t.getVersion());
            assertTrue(t.isActive());
            assertFalse(t.isBuiltIn());
            assertTrue(t.isAutoIndent());
            assertEquals("  ", t.getIndentStyle());
            assertEquals(XmlTemplate.TemplateIndustry.GENERAL, t.getIndustry());
            assertEquals(XmlTemplate.TemplateComplexity.SIMPLE, t.getComplexity());
        }

        @Test
        @DisplayName("Parameterized constructor sets fields")
        void parameterizedConstructor() {
            assertEquals("Test Template", template.getName());
            assertEquals("<root>${name}</root>", template.getContent());
            assertEquals("Basic", template.getCategory());
            assertNotNull(template.getId());
        }

        @Test
        @DisplayName("Each template gets a unique ID")
        void uniqueIds() {
            XmlTemplate t1 = new XmlTemplate();
            XmlTemplate t2 = new XmlTemplate();
            assertNotEquals(t1.getId(), t2.getId());
        }
    }

    // =========================================================================
    // Template Processing Tests
    // =========================================================================

    @Nested
    @DisplayName("Template Processing")
    class ProcessingTests {

        @Test
        @DisplayName("Processes ${param} substitution")
        void dollarBraceSubstitution() {
            template.setAutoIndent(false);
            TemplateParameter param = TemplateParameter.stringParam("name", "World");
            template.addParameter(param);

            String result = template.processTemplate(Map.of("name", "Hello"));
            assertTrue(result.contains("Hello"));
            assertFalse(result.contains("${name}"));
        }

        @Test
        @DisplayName("Processes {{param}} substitution")
        void doubleBraceSubstitution() {
            template.setContent("<root>{{name}}</root>");
            template.setAutoIndent(false);
            TemplateParameter param = TemplateParameter.stringParam("name");
            template.addParameter(param);

            String result = template.processTemplate(Map.of("name", "Test"));
            assertTrue(result.contains("Test"));
            assertFalse(result.contains("{{name}}"));
        }

        @Test
        @DisplayName("Uses default value when parameter not provided")
        void defaultValueSubstitution() {
            template.setAutoIndent(false);
            TemplateParameter param = TemplateParameter.stringParam("name", "DefaultName");
            template.addParameter(param);

            String result = template.processTemplate(Map.of());
            assertTrue(result.contains("DefaultName"));
        }

        @Test
        @DisplayName("Returns empty string for null content")
        void nullContent() {
            template.setContent(null);
            assertEquals("", template.processTemplate(Map.of()));
        }

        @Test
        @DisplayName("Returns empty string for empty content")
        void emptyContent() {
            template.setContent("");
            assertEquals("", template.processTemplate(Map.of()));
        }

        @Test
        @DisplayName("Throws on missing required parameter")
        void missingRequiredParameter() {
            TemplateParameter param = TemplateParameter.requiredString("name");
            template.addParameter(param);

            assertThrows(IllegalArgumentException.class,
                    () -> template.processTemplate(Map.of()));
        }

        @Test
        @DisplayName("Conditional blocks included when parameter present")
        void conditionalBlockIncluded() {
            template.setContent("{{#if showExtra}}EXTRA{{/if}}");
            template.setAutoIndent(false);
            template.setConditionalBlocks(Map.of("showExtra", "true"));

            String result = template.processTemplate(Map.of("showExtra", "yes"));
            assertTrue(result.contains("EXTRA"));
        }

        @Test
        @DisplayName("Conditional blocks removed when parameter absent")
        void conditionalBlockRemoved() {
            template.setContent("{{#if showExtra}}EXTRA{{/if}}");
            template.setAutoIndent(false);
            template.setConditionalBlocks(Map.of("showExtra", "true"));

            String result = template.processTemplate(Map.of());
            assertFalse(result.contains("EXTRA"));
        }
    }

    // =========================================================================
    // Context Matching Tests
    // =========================================================================

    @Nested
    @DisplayName("Context Matching")
    class ContextMatchingTests {

        @Test
        @DisplayName("Applicable when no constraints set")
        void noConstraints() {
            assertTrue(template.isApplicableInContext(
                    XmlTemplate.TemplateContext.GENERAL, "any", Set.of()));
        }

        @Test
        @DisplayName("Not applicable when inactive")
        void inactiveTemplate() {
            template.setActive(false);
            assertFalse(template.isApplicableInContext(
                    XmlTemplate.TemplateContext.GENERAL, null, null));
        }

        @Test
        @DisplayName("Matches specific context")
        void contextMatch() {
            template.addContext(XmlTemplate.TemplateContext.ROOT_ELEMENT);

            assertTrue(template.isApplicableInContext(
                    XmlTemplate.TemplateContext.ROOT_ELEMENT, null, null));
            assertFalse(template.isApplicableInContext(
                    XmlTemplate.TemplateContext.CHILD_ELEMENT, null, null));
        }

        @Test
        @DisplayName("Matches target element")
        void elementMatch() {
            template.setTargetElements(Set.of("person", "employee"));

            assertTrue(template.isApplicableInContext(
                    XmlTemplate.TemplateContext.GENERAL, "person", Set.of()));
            assertFalse(template.isApplicableInContext(
                    XmlTemplate.TemplateContext.GENERAL, "unknown", Set.of()));
        }

        @Test
        @DisplayName("Regex element matches pattern")
        void regexElementMatch() {
            template.setTargetElements(Set.of("person.*"));

            assertTrue(template.isApplicableInContext(
                    XmlTemplate.TemplateContext.GENERAL, "personName", Set.of()));
            assertFalse(template.isApplicableInContext(
                    XmlTemplate.TemplateContext.GENERAL, "address", Set.of()));
        }

        @Test
        @DisplayName("Matches namespace")
        void namespaceMatch() {
            template.setXmlNamespaces(Set.of("http://example.com/ns"));

            assertTrue(template.isApplicableInContext(
                    XmlTemplate.TemplateContext.GENERAL, null,
                    Set.of("http://example.com/ns")));
            assertFalse(template.isApplicableInContext(
                    XmlTemplate.TemplateContext.GENERAL, null,
                    Set.of("http://other.com/ns")));
        }
    }

    // =========================================================================
    // Relevance Score Tests
    // =========================================================================

    @Nested
    @DisplayName("Relevance Score")
    class RelevanceScoreTests {

        @Test
        @DisplayName("Returns 0 for non-applicable template")
        void nonApplicable() {
            template.setActive(false);
            assertEquals(0.0, template.calculateRelevanceScore(
                    XmlTemplate.TemplateContext.GENERAL, null, Set.of()));
        }

        @Test
        @DisplayName("Higher score for context match")
        void contextBoost() {
            template.addContext(XmlTemplate.TemplateContext.ROOT_ELEMENT);

            double matchScore = template.calculateRelevanceScore(
                    XmlTemplate.TemplateContext.ROOT_ELEMENT, null, Set.of());
            assertTrue(matchScore > 1.0, "Score should be boosted for context match");
        }

        @Test
        @DisplayName("Higher score for more usage")
        void usageBoost() {
            double score1 = template.calculateRelevanceScore(
                    XmlTemplate.TemplateContext.GENERAL, null, Set.of());

            template.setUsageCount(100);
            double score2 = template.calculateRelevanceScore(
                    XmlTemplate.TemplateContext.GENERAL, null, Set.of());

            assertTrue(score2 > score1, "Score should increase with usage");
        }

        @Test
        @DisplayName("Higher score for higher rating")
        void ratingBoost() {
            double score1 = template.calculateRelevanceScore(
                    XmlTemplate.TemplateContext.GENERAL, null, Set.of());

            template.updateRating(5.0);
            double score2 = template.calculateRelevanceScore(
                    XmlTemplate.TemplateContext.GENERAL, null, Set.of());

            assertTrue(score2 > score1, "Score should increase with rating");
        }
    }

    // =========================================================================
    // Validation Tests
    // =========================================================================

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("Validates required parameters")
        void requiredParameterMissing() {
            template.setRequiredParameters(Set.of("name"));

            List<String> errors = template.validateParameters(Map.of());
            assertFalse(errors.isEmpty());
            assertTrue(errors.get(0).contains("name"));
        }

        @Test
        @DisplayName("No errors for valid parameters")
        void validParameters() {
            template.setRequiredParameters(Set.of("name"));

            List<String> errors = template.validateParameters(Map.of("name", "test"));
            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Validates with custom rules")
        void customValidationRule() {
            TemplateValidationRule rule = new TemplateValidationRule();
            rule.setRuleType(TemplateValidationRule.RuleType.CUSTOM);
            rule.setErrorMessage("Custom error");
            rule.setCustomValidator(params -> params.containsKey("required"));
            rule.setActive(true);

            template.setValidationRules(List.of(rule));

            List<String> errors = template.validateParameters(Map.of());
            assertFalse(errors.isEmpty());
            assertEquals("Custom error", errors.get(0));
        }
    }

    // =========================================================================
    // Parameter Management Tests
    // =========================================================================

    @Nested
    @DisplayName("Parameter Management")
    class ParameterTests {

        @Test
        @DisplayName("Add parameter with default value")
        void addParameterWithDefault() {
            TemplateParameter param = TemplateParameter.stringParam("key", "val");
            template.addParameter(param);

            assertEquals("val", template.getDefaultValue("key"));
            assertNotNull(template.getParameter("key"));
        }

        @Test
        @DisplayName("Add required parameter")
        void addRequiredParameter() {
            TemplateParameter param = TemplateParameter.requiredString("key");
            template.addParameter(param);

            assertTrue(template.getRequiredParameters().contains("key"));
        }

        @Test
        @DisplayName("Replace existing parameter with same name")
        void replaceParameter() {
            TemplateParameter param1 = TemplateParameter.stringParam("key", "val1");
            TemplateParameter param2 = TemplateParameter.stringParam("key", "val2");

            template.addParameter(param1);
            template.addParameter(param2);

            assertEquals(1, template.getParameters().size());
            assertEquals("val2", template.getDefaultValue("key"));
        }

        @Test
        @DisplayName("Remove parameter")
        void removeParameter() {
            TemplateParameter param = TemplateParameter.requiredString("key");
            template.addParameter(param);
            template.removeParameter("key");

            assertNull(template.getParameter("key"));
            assertNull(template.getDefaultValue("key"));
            assertFalse(template.getRequiredParameters().contains("key"));
        }
    }

    // =========================================================================
    // Copy Tests
    // =========================================================================

    @Nested
    @DisplayName("Copy")
    class CopyTests {

        @Test
        @DisplayName("Copy creates new ID")
        void copyNewId() {
            XmlTemplate copy = template.copy();
            assertNotEquals(template.getId(), copy.getId());
        }

        @Test
        @DisplayName("Copy appends (Copy) to name")
        void copyName() {
            XmlTemplate copy = template.copy();
            assertEquals("Test Template (Copy)", copy.getName());
        }

        @Test
        @DisplayName("Copy is never built-in")
        void copyNotBuiltIn() {
            template.setBuiltIn(true);
            XmlTemplate copy = template.copy();
            assertFalse(copy.isBuiltIn());
        }

        @Test
        @DisplayName("Copy preserves content")
        void copyContent() {
            XmlTemplate copy = template.copy();
            assertEquals(template.getContent(), copy.getContent());
            assertEquals(template.getCategory(), copy.getCategory());
        }

        @Test
        @DisplayName("Copy preserves collections independently")
        void copyCollections() {
            template.setXmlNamespaces(Set.of("ns1"));
            template.setTags(List.of("tag1"));

            XmlTemplate copy = template.copy();
            assertEquals(template.getXmlNamespaces(), copy.getXmlNamespaces());
            assertEquals(template.getTags(), copy.getTags());
        }
    }

    // =========================================================================
    // Preview Tests
    // =========================================================================

    @Nested
    @DisplayName("Preview")
    class PreviewTests {

        @Test
        @DisplayName("Preview truncates long content")
        void truncatesLongContent() {
            template.setContent("x".repeat(2000));
            template.setAutoIndent(false);

            String preview = template.getPreview(Map.of());
            assertTrue(preview.length() < 2000);
            assertTrue(preview.contains("(truncated)"));
        }

        @Test
        @DisplayName("Preview returns error on exception")
        void errorOnException() {
            TemplateParameter param = TemplateParameter.requiredString("missing");
            template.addParameter(param);

            String preview = template.getPreview(Map.of());
            assertTrue(preview.startsWith("Preview Error:"));
        }
    }

    // =========================================================================
    // Usage Tracking Tests
    // =========================================================================

    @Nested
    @DisplayName("Usage Tracking")
    class UsageTests {

        @Test
        @DisplayName("Record usage increments count")
        void recordUsage() {
            assertEquals(0, template.getUsageCount());
            template.recordUsage();
            assertEquals(1, template.getUsageCount());
            template.recordUsage();
            assertEquals(2, template.getUsageCount());
        }

        @Test
        @DisplayName("Record usage sets lastUsed")
        void recordUsageSetsLastUsed() {
            assertNull(template.getLastUsed());
            template.recordUsage();
            assertNotNull(template.getLastUsed());
        }

        @Test
        @DisplayName("Update rating within valid range")
        void validRating() {
            template.updateRating(4.5);
            assertEquals(4.5, template.getUserRating());
        }

        @Test
        @DisplayName("Update rating ignores out of range")
        void outOfRangeRating() {
            template.updateRating(3.0);
            template.updateRating(-1.0);
            assertEquals(3.0, template.getUserRating());
            template.updateRating(6.0);
            assertEquals(3.0, template.getUserRating());
        }
    }

    // =========================================================================
    // Summary and Utility Tests
    // =========================================================================

    @Nested
    @DisplayName("Summary and Utility")
    class SummaryTests {

        @Test
        @DisplayName("Summary includes name")
        void summaryIncludesName() {
            String summary = template.getSummary();
            assertTrue(summary.contains("Test Template"));
        }

        @Test
        @DisplayName("Summary includes description")
        void summaryIncludesDescription() {
            template.setDescription("A test");
            String summary = template.getSummary();
            assertTrue(summary.contains("A test"));
        }

        @Test
        @DisplayName("Summary includes parameter count")
        void summaryIncludesParams() {
            template.addParameter(TemplateParameter.stringParam("p1"));
            String summary = template.getSummary();
            assertTrue(summary.contains("1 parameters"));
        }

        @Test
        @DisplayName("Summary includes usage count")
        void summaryIncludesUsage() {
            template.setUsageCount(5);
            String summary = template.getSummary();
            assertTrue(summary.contains("Used 5 times"));
        }

        @Test
        @DisplayName("Equals and hashCode based on ID")
        void equalsAndHashCode() {
            XmlTemplate t1 = new XmlTemplate();
            XmlTemplate t2 = new XmlTemplate();
            assertNotEquals(t1, t2);

            t2.setId(t1.getId());
            assertEquals(t1, t2);
            assertEquals(t1.hashCode(), t2.hashCode());
        }

        @Test
        @DisplayName("toString includes key info")
        void toStringInfo() {
            String str = template.toString();
            assertTrue(str.contains("Test Template"));
            assertTrue(str.contains("Basic"));
        }
    }

    // =========================================================================
    // Setter Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("Setter Null Safety")
    class SetterNullSafety {

        @Test
        @DisplayName("Null collections default to empty")
        void nullCollections() {
            template.setParameters(null);
            assertNotNull(template.getParameters());
            assertTrue(template.getParameters().isEmpty());

            template.setDefaultValues(null);
            assertNotNull(template.getDefaultValues());

            template.setContexts(null);
            assertNotNull(template.getContexts());

            template.setXmlNamespaces(null);
            assertNotNull(template.getXmlNamespaces());

            template.setTargetElements(null);
            assertNotNull(template.getTargetElements());

            template.setTags(null);
            assertNotNull(template.getTags());

            template.setRelatedStandards(null);
            assertNotNull(template.getRelatedStandards());

            template.setConditionalBlocks(null);
            assertNotNull(template.getConditionalBlocks());
        }

        @Test
        @DisplayName("addContext to null contexts")
        void addContextNullSafe() {
            template.setContexts(null);
            template.addContext(XmlTemplate.TemplateContext.GENERAL);
            assertTrue(template.getContexts().contains(XmlTemplate.TemplateContext.GENERAL));
        }

        @Test
        @DisplayName("addRelatedStandard to null standards")
        void addRelatedStandardNullSafe() {
            template.setRelatedStandards(null);
            template.addRelatedStandard("ISO 20022");
            assertTrue(template.getRelatedStandards().contains("ISO 20022"));
        }
    }

    // =========================================================================
    // Enum Tests
    // =========================================================================

    @Nested
    @DisplayName("Enums")
    class EnumTests {

        @Test
        @DisplayName("TemplateContext has expected values")
        void templateContextValues() {
            XmlTemplate.TemplateContext[] values = XmlTemplate.TemplateContext.values();
            assertTrue(values.length >= 12);
            assertNotNull(XmlTemplate.TemplateContext.valueOf("ROOT_ELEMENT"));
            assertNotNull(XmlTemplate.TemplateContext.valueOf("GENERAL"));
        }

        @Test
        @DisplayName("TemplateIndustry has expected values")
        void templateIndustryValues() {
            assertNotNull(XmlTemplate.TemplateIndustry.valueOf("FINANCE"));
            assertNotNull(XmlTemplate.TemplateIndustry.valueOf("HEALTHCARE"));
            assertNotNull(XmlTemplate.TemplateIndustry.valueOf("AUTOMOTIVE"));
        }

        @Test
        @DisplayName("TemplateComplexity has expected values")
        void templateComplexityValues() {
            XmlTemplate.TemplateComplexity[] values = XmlTemplate.TemplateComplexity.values();
            assertEquals(4, values.length);
        }
    }
}
