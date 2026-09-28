package org.fxt.freexmltoolkit.controls.v2.editor.statistics;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker.*;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for XsdQualityChecker.
 *
 * @since 2.0
 */
class XsdQualityCheckerTest {

    private XsdSchema schema;

    @BeforeEach
    void setUp() {
        schema = new XsdSchema();
        schema.setTargetNamespace("http://example.com/test");
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("constructor should throw NullPointerException for null schema")
    void testConstructorNullSchema() {
        assertThrows(NullPointerException.class, () -> new XsdQualityChecker(null));
    }

    @Test
    @DisplayName("constructor should accept valid schema")
    void testConstructorValidSchema() {
        assertDoesNotThrow(() -> new XsdQualityChecker(schema));
    }

    // ========== Length/Enumeration Conflict Tests ==========

    @Nested
    @DisplayName("Length/Enumeration Conflict Detection")
    class LengthEnumerationConflictTests {

        @Test
        @DisplayName("should detect maxLength exceeded by enumeration values")
        void testDetectsMaxLengthExceeded() {
            // Setup: Create a SimpleType with maxLength=12 but enum with 26 chars
            XsdSimpleType simpleType = new XsdSimpleType("ListedType");
            XsdRestriction restriction = new XsdRestriction("xs:string");
            restriction.addFacet(new XsdFacet(XsdFacetType.MIN_LENGTH, "3"));
            restriction.addFacet(new XsdFacet(XsdFacetType.MAX_LENGTH, "12"));
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "AnnualReport")); // 12 chars - OK
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "KID / Simplified Prospectus")); // 26 chars - EXCEEDS
            simpleType.addChild(restriction);
            schema.addChild(simpleType);

            // Execute
            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            // Verify - filter specifically for maxLength issues
            List<QualityIssue> conflictIssues = result.getIssuesByCategory(IssueCategory.CONSTRAINT_CONFLICT);
            assertFalse(conflictIssues.isEmpty(), "Should detect at least one constraint conflict issue");

            // Find the maxLength issue
            QualityIssue maxLengthIssue = conflictIssues.stream()
                    .filter(i -> i.message().contains("maxLength"))
                    .findFirst()
                    .orElse(null);

            assertNotNull(maxLengthIssue, "Should detect maxLength conflict issue");
            assertEquals(IssueSeverity.ERROR, maxLengthIssue.severity(), "Should be ERROR severity");
            assertTrue(maxLengthIssue.message().contains("maxLength=12"), "Message should mention maxLength");
            assertTrue(maxLengthIssue.message().contains("ListedType"), "Message should mention type name");
            assertEquals(1, maxLengthIssue.affectedElements().size(), "Should have 1 affected element");
            // Check that the affected element mentions the violating value (KID / Simplified Prospectus)
            assertTrue(maxLengthIssue.affectedElements().get(0).contains("KID"),
                    "Affected element should contain the violating enum value. Actual: " +
                            maxLengthIssue.affectedElements().get(0));
        }

        @Test
        @DisplayName("should detect minLength violation by enumeration values")
        void testDetectsMinLengthViolation() {
            // Setup: Create a SimpleType with minLength=5 but enum with 2 chars
            XsdSimpleType simpleType = new XsdSimpleType("StatusType");
            XsdRestriction restriction = new XsdRestriction("xs:string");
            restriction.addFacet(new XsdFacet(XsdFacetType.MIN_LENGTH, "5"));
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "OK")); // 2 chars - TOO SHORT
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "ERROR")); // 5 chars - OK
            simpleType.addChild(restriction);
            schema.addChild(simpleType);

            // Execute
            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            // Verify
            List<QualityIssue> conflictIssues = result.getIssuesByCategory(IssueCategory.CONSTRAINT_CONFLICT);
            assertEquals(1, conflictIssues.size(), "Should detect exactly one conflict issue");

            QualityIssue issue = conflictIssues.get(0);
            assertEquals(IssueSeverity.ERROR, issue.severity(), "Should be ERROR severity");
            assertTrue(issue.message().contains("minLength=5"), "Message should mention minLength");
        }

        @Test
        @DisplayName("should detect exact length violation by enumeration values")
        void testDetectsExactLengthViolation() {
            // Setup: Create a SimpleType with length=4 but enum with wrong length
            XsdSimpleType simpleType = new XsdSimpleType("CodeType");
            XsdRestriction restriction = new XsdRestriction("xs:string");
            restriction.addFacet(new XsdFacet(XsdFacetType.LENGTH, "4"));
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "ABCD")); // 4 chars - OK
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "ABC")); // 3 chars - WRONG
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "ABCDE")); // 5 chars - WRONG
            simpleType.addChild(restriction);
            schema.addChild(simpleType);

            // Execute
            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            // Verify
            List<QualityIssue> conflictIssues = result.getIssuesByCategory(IssueCategory.CONSTRAINT_CONFLICT);
            assertEquals(1, conflictIssues.size(), "Should detect exactly one conflict issue");

            QualityIssue issue = conflictIssues.get(0);
            assertEquals(IssueSeverity.ERROR, issue.severity(), "Should be ERROR severity");
            assertTrue(issue.message().contains("length=4"), "Message should mention length");
            assertEquals(2, issue.affectedElements().size(), "Should have 2 affected elements");
        }

        @Test
        @DisplayName("should not report issue when enumerations match length constraints")
        void testNoIssueWhenConstraintsMatch() {
            // Setup: Create a SimpleType with matching constraints
            XsdSimpleType simpleType = new XsdSimpleType("StatusType");
            XsdRestriction restriction = new XsdRestriction("xs:string");
            restriction.addFacet(new XsdFacet(XsdFacetType.MIN_LENGTH, "4"));
            restriction.addFacet(new XsdFacet(XsdFacetType.MAX_LENGTH, "10"));
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "ACTIVE")); // 6 chars - OK
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "INACTIVE")); // 8 chars - OK
            simpleType.addChild(restriction);
            schema.addChild(simpleType);

            // Execute
            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            // Verify
            List<QualityIssue> conflictIssues = result.getIssuesByCategory(IssueCategory.CONSTRAINT_CONFLICT);
            assertTrue(conflictIssues.isEmpty(), "Should not report any constraint conflict issues");
        }

        @Test
        @DisplayName("should not report issue when only enumerations exist (no length constraints)")
        void testNoIssueWithOnlyEnumerations() {
            // Setup: Create a SimpleType with only enumerations
            XsdSimpleType simpleType = new XsdSimpleType("ColorType");
            XsdRestriction restriction = new XsdRestriction("xs:string");
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "RED"));
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "GREEN"));
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "BLUE"));
            simpleType.addChild(restriction);
            schema.addChild(simpleType);

            // Execute
            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            // Verify
            List<QualityIssue> conflictIssues = result.getIssuesByCategory(IssueCategory.CONSTRAINT_CONFLICT);
            assertTrue(conflictIssues.isEmpty(), "Should not report any constraint conflict issues");
        }

        @Test
        @DisplayName("should not report issue when only length constraints exist (no enumerations)")
        void testNoIssueWithOnlyLengthConstraints() {
            // Setup: Create a SimpleType with only length constraints
            XsdSimpleType simpleType = new XsdSimpleType("NameType");
            XsdRestriction restriction = new XsdRestriction("xs:string");
            restriction.addFacet(new XsdFacet(XsdFacetType.MIN_LENGTH, "1"));
            restriction.addFacet(new XsdFacet(XsdFacetType.MAX_LENGTH, "100"));
            simpleType.addChild(restriction);
            schema.addChild(simpleType);

            // Execute
            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            // Verify
            List<QualityIssue> conflictIssues = result.getIssuesByCategory(IssueCategory.CONSTRAINT_CONFLICT);
            assertTrue(conflictIssues.isEmpty(), "Should not report any constraint conflict issues");
        }

        @Test
        @DisplayName("should detect multiple violations in complex case from FundsXML report")
        void testFundsXMLListedTypeExample() {
            // This test replicates the exact case from SCHEMA_INCONSISTENCIES_REPORT.md
            XsdSimpleType simpleType = new XsdSimpleType("ListedType");
            XsdRestriction restriction = new XsdRestriction("xs:string");
            restriction.addFacet(new XsdFacet(XsdFacetType.MIN_LENGTH, "3"));
            restriction.addFacet(new XsdFacet(XsdFacetType.MAX_LENGTH, "12"));

            // All enumeration values from the report
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "AIFMD-AnnexIV")); // 13 chars
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "AnnualReport")); // 12 chars - OK
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "HalfYearReport")); // 14 chars
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "QuarterlyReport")); // 15 chars
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "MonthlyReport")); // 13 chars
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "AuditReport")); // 11 chars - OK
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "Factsheet")); // 9 chars - OK
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "Prospectus")); // 10 chars - OK
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "PRIIPS-KID")); // 10 chars - OK
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "KID / Simplified Prospectus")); // 26 chars
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "MarketingNotification")); // 21 chars
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "SFDR-PAIStatement")); // 17 chars
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "SFDR Website Disclosure")); // 23 chars
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "MarketingMaterial")); // 17 chars
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "ValuationReport")); // 15 chars

            simpleType.addChild(restriction);
            schema.addChild(simpleType);

            // Execute
            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            // Verify
            List<QualityIssue> conflictIssues = result.getIssuesByCategory(IssueCategory.CONSTRAINT_CONFLICT);
            assertEquals(1, conflictIssues.size(), "Should detect maxLength conflict");

            QualityIssue issue = conflictIssues.get(0);
            assertEquals(IssueSeverity.ERROR, issue.severity());
            assertTrue(issue.message().contains("maxLength=12"));

            // Should have 10 affected elements (those exceeding maxLength=12)
            // AIFMD-AnnexIV(13), HalfYearReport(14), QuarterlyReport(15), MonthlyReport(13),
            // KID/Simplified(26), MarketingNotification(21), SFDR-PAI(17), SFDR Website(23),
            // MarketingMaterial(17), ValuationReport(15)
            assertEquals(10, issue.affectedElements().size(),
                    "Should have 10 enum values exceeding maxLength=12");
        }

        @Test
        @DisplayName("should provide helpful suggestion with correct maxLength recommendation")
        void testSuggestionContainsCorrectMaxLength() {
            XsdSimpleType simpleType = new XsdSimpleType("TestType");
            XsdRestriction restriction = new XsdRestriction("xs:string");
            restriction.addFacet(new XsdFacet(XsdFacetType.MAX_LENGTH, "5"));
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "SHORT")); // 5 chars - OK
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "MEDIUMLONG")); // 10 chars - EXCEEDS
            simpleType.addChild(restriction);
            schema.addChild(simpleType);

            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            List<QualityIssue> conflictIssues = result.getIssuesByCategory(IssueCategory.CONSTRAINT_CONFLICT);
            assertEquals(1, conflictIssues.size());

            QualityIssue issue = conflictIssues.get(0);
            assertNotNull(issue.suggestion());
            assertTrue(issue.suggestion().contains("10"),
                    "Suggestion should recommend maxLength=10 (length of longest enum)");
        }

        @Test
        @DisplayName("should handle nested restriction in element with anonymous SimpleType")
        void testNestedRestrictionInElement() {
            // Create element with anonymous SimpleType containing restriction
            XsdElement element = new XsdElement("StatusCode");
            XsdSimpleType anonymousType = new XsdSimpleType();
            XsdRestriction restriction = new XsdRestriction("xs:string");
            restriction.addFacet(new XsdFacet(XsdFacetType.MAX_LENGTH, "3"));
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "ACTIVE")); // 6 chars - EXCEEDS
            anonymousType.addChild(restriction);
            element.addChild(anonymousType);
            schema.addChild(element);

            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            List<QualityIssue> conflictIssues = result.getIssuesByCategory(IssueCategory.CONSTRAINT_CONFLICT);
            assertEquals(1, conflictIssues.size());
        }
    }

    // ========== Quality Score Tests ==========

    @Nested
    @DisplayName("Quality Score Calculation")
    class QualityScoreTests {

        @Test
        @DisplayName("empty schema should have score of 100")
        void testEmptySchemaScore() {
            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            assertEquals(100, result.score(), "Empty schema should have perfect score");
        }

        @Test
        @DisplayName("constraint conflict issues should reduce score")
        void testConstraintConflictReducesScore() {
            // Add elements to establish baseline
            XsdElement element1 = new XsdElement("GoodElement");
            XsdElement element2 = new XsdElement("AnotherElement");
            schema.addChild(element1);
            schema.addChild(element2);

            // Add a SimpleType with constraint conflict
            XsdSimpleType simpleType = new XsdSimpleType("BadType");
            XsdRestriction restriction = new XsdRestriction("xs:string");
            restriction.addFacet(new XsdFacet(XsdFacetType.MAX_LENGTH, "5"));
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "TOOLONGVALUE")); // Exceeds maxLength
            simpleType.addChild(restriction);
            schema.addChild(simpleType);

            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            assertTrue(result.score() < 100, "Score should be reduced due to constraint conflict");
            List<QualityIssue> issues = result.getIssuesByCategory(IssueCategory.CONSTRAINT_CONFLICT);
            assertFalse(issues.isEmpty(), "Should have constraint conflict issues");
        }
    }

    @Nested
    @DisplayName("Passed Checks")
    class PassedChecksTests {

        @Test
        @DisplayName("passedChecks never drops below zero even when issues outnumber the checks")
        void testPassedChecksClampedAtZero() {
            // One named type = one naming check, but several error-level issues on it.
            XsdSimpleType simpleType = new XsdSimpleType("BadType");
            XsdRestriction restriction = new XsdRestriction("xs:string");
            restriction.addFacet(new XsdFacet(XsdFacetType.MAX_LENGTH, "3"));
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "TOOLONG-1"));
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "TOOLONG-2"));
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "TOOLONG-3"));
            simpleType.addChild(restriction);
            schema.addChild(simpleType);

            QualityResult result = new XsdQualityChecker(schema).check();

            assertTrue(result.passedChecks() >= 0, "passedChecks must not be negative: " + result.passedChecks());
            assertTrue(result.passedChecks() <= result.totalChecks(), "passedChecks must not exceed totalChecks");
            assertEquals(0, result.score());
        }
    }

    // ========== Issue Category Tests ==========

    @Nested
    @DisplayName("Issue Category Filtering")
    class IssueCategoryTests {

        @Test
        @DisplayName("getIssuesByCategory should return only matching issues")
        void testGetIssuesByCategory() {
            // Add naming inconsistency (creates NAMING_CONVENTION issue)
            XsdElement camelCase = new XsdElement("CamelCase");
            XsdElement snakeCase = new XsdElement("snake_case");
            schema.addChild(camelCase);
            schema.addChild(snakeCase);

            // Add constraint conflict (creates CONSTRAINT_CONFLICT issue)
            XsdSimpleType simpleType = new XsdSimpleType("TestType");
            XsdRestriction restriction = new XsdRestriction("xs:string");
            restriction.addFacet(new XsdFacet(XsdFacetType.MAX_LENGTH, "3"));
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "LONGVALUE"));
            simpleType.addChild(restriction);
            schema.addChild(simpleType);

            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            List<QualityIssue> namingIssues = result.getIssuesByCategory(IssueCategory.NAMING_CONVENTION);
            List<QualityIssue> conflictIssues = result.getIssuesByCategory(IssueCategory.CONSTRAINT_CONFLICT);

            assertFalse(conflictIssues.isEmpty(), "Should have constraint conflict issues");
            for (QualityIssue issue : conflictIssues) {
                assertEquals(IssueCategory.CONSTRAINT_CONFLICT, issue.category());
            }
        }
    }

    // ========== XPath Generation Tests ==========

    @Nested
    @DisplayName("XPath Generation for Issues")
    class XPathGenerationTests {

        @Test
        @DisplayName("constraint conflict issues should include XPath location")
        void testConstraintConflictIncludesXPath() {
            // Create a named SimpleType with constraint conflict
            XsdSimpleType simpleType = new XsdSimpleType("MyStatusType");
            XsdRestriction restriction = new XsdRestriction("xs:string");
            restriction.addFacet(new XsdFacet(XsdFacetType.MAX_LENGTH, "5"));
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "TOOLONGVALUE"));
            simpleType.addChild(restriction);
            schema.addChild(simpleType);

            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            List<QualityIssue> conflictIssues = result.getIssuesByCategory(IssueCategory.CONSTRAINT_CONFLICT);
            assertFalse(conflictIssues.isEmpty(), "Should have constraint conflict issue");

            QualityIssue issue = conflictIssues.get(0);
            assertNotNull(issue.xpath(), "XPath should not be null");
            assertTrue(issue.xpath().contains("xs:schema"), "XPath should contain schema root");
            assertTrue(issue.xpath().contains("xs:simpleType"), "XPath should contain simpleType");
            assertTrue(issue.xpath().contains("MyStatusType"), "XPath should contain type name");
            assertTrue(issue.xpath().contains("xs:restriction"), "XPath should contain restriction");
        }

        @Test
        @DisplayName("XPath should correctly represent nested structure")
        void testNestedXPathStructure() {
            // Create element with anonymous SimpleType containing restriction
            XsdElement element = new XsdElement("StatusCode");
            XsdSimpleType anonymousType = new XsdSimpleType();
            XsdRestriction restriction = new XsdRestriction("xs:string");
            restriction.addFacet(new XsdFacet(XsdFacetType.MAX_LENGTH, "2"));
            restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "TOOLONG"));
            anonymousType.addChild(restriction);
            element.addChild(anonymousType);
            schema.addChild(element);

            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            List<QualityIssue> conflictIssues = result.getIssuesByCategory(IssueCategory.CONSTRAINT_CONFLICT);
            assertFalse(conflictIssues.isEmpty());

            QualityIssue issue = conflictIssues.get(0);
            assertNotNull(issue.xpath());
            // XPath should show the path: /xs:schema/xs:element[@name='StatusCode']/xs:simpleType/xs:restriction
            assertTrue(issue.xpath().contains("StatusCode"),
                    "XPath should contain element name. Actual: " + issue.xpath());
        }
    }

    // ========== Inconsistent Definition Tests ==========

    @Nested
    @DisplayName("Inconsistent Definition Detection")
    class InconsistentDefinitionTests {

        @Test
        @DisplayName("should detect elements with same name but different types")
        void testSameNameDifferentType() {
            // Create two elements named "Status" but with different types
            XsdComplexType parent1 = new XsdComplexType("OrderType");
            XsdSequence seq1 = new XsdSequence();
            XsdElement elem1 = new XsdElement("Status");
            elem1.setType("xs:string");
            seq1.addChild(elem1);
            parent1.addChild(seq1);

            XsdComplexType parent2 = new XsdComplexType("InvoiceType");
            XsdSequence seq2 = new XsdSequence();
            XsdElement elem2 = new XsdElement("Status");
            elem2.setType("xs:int"); // Different type!
            seq2.addChild(elem2);
            parent2.addChild(seq2);

            schema.addChild(parent1);
            schema.addChild(parent2);

            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            List<QualityIssue> issues = result.getIssuesByCategory(IssueCategory.INCONSISTENT_DEFINITION);
            assertEquals(1, issues.size(), "Should detect inconsistent definition");
            assertTrue(issues.get(0).message().contains("Status"));
        }

        @Test
        @DisplayName("should not report issue when elements with same name have same structure")
        void testSameNameSameStructure() {
            // Create two elements named "Amount" with same type
            XsdComplexType parent1 = new XsdComplexType("OrderType");
            XsdSequence seq1 = new XsdSequence();
            XsdElement elem1 = new XsdElement("Amount");
            elem1.setType("xs:decimal");
            seq1.addChild(elem1);
            parent1.addChild(seq1);

            XsdComplexType parent2 = new XsdComplexType("InvoiceType");
            XsdSequence seq2 = new XsdSequence();
            XsdElement elem2 = new XsdElement("Amount");
            elem2.setType("xs:decimal"); // Same type
            seq2.addChild(elem2);
            parent2.addChild(seq2);

            schema.addChild(parent1);
            schema.addChild(parent2);

            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            List<QualityIssue> issues = result.getIssuesByCategory(IssueCategory.INCONSISTENT_DEFINITION);
            assertTrue(issues.isEmpty(), "Should not report issue for consistent definitions");
        }

        @Test
        @DisplayName("should detect SimpleTypes with same name but different restrictions")
        void testSameNameSimpleTypeDifferentRestrictions() {
            // Create two SimpleTypes named "StatusCodeType" with different patterns
            XsdSimpleType type1 = new XsdSimpleType("StatusCodeType");
            XsdRestriction restriction1 = new XsdRestriction("xs:string");
            restriction1.addFacet(new XsdFacet(XsdFacetType.PATTERN, "[A-Z]{3}"));
            type1.addChild(restriction1);

            // This simulates a second definition somewhere in included schema
            // In a real scenario these would be in different files
            XsdSimpleType type2 = new XsdSimpleType("StatusCodeType");
            XsdRestriction restriction2 = new XsdRestriction("xs:string");
            restriction2.addFacet(new XsdFacet(XsdFacetType.PATTERN, "[A-Z]{2}")); // Different pattern
            type2.addChild(restriction2);

            schema.addChild(type1);
            schema.addChild(type2);

            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            List<QualityIssue> issues = result.getIssuesByCategory(IssueCategory.INCONSISTENT_DEFINITION);
            assertEquals(1, issues.size(), "Should detect inconsistent SimpleType definitions");
            assertTrue(issues.get(0).message().contains("StatusCodeType"));
        }
    }

    // ========== Duplicate Definition Tests ==========

    @Nested
    @DisplayName("Duplicate Definition Detection")
    class DuplicateDefinitionTests {

        @Test
        @DisplayName("should detect SimpleTypes with different names but identical restrictions")
        void testDifferentNameSameRestrictions() {
            // Create two SimpleTypes with different names but identical content
            XsdSimpleType type1 = new XsdSimpleType("CustomerStatusType");
            XsdRestriction restriction1 = new XsdRestriction("xs:string");
            restriction1.addFacet(new XsdFacet(XsdFacetType.MAX_LENGTH, "50"));
            restriction1.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "ACTIVE"));
            restriction1.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "INACTIVE"));
            type1.addChild(restriction1);

            XsdSimpleType type2 = new XsdSimpleType("AccountStatusType");
            XsdRestriction restriction2 = new XsdRestriction("xs:string");
            restriction2.addFacet(new XsdFacet(XsdFacetType.MAX_LENGTH, "50"));
            restriction2.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "ACTIVE"));
            restriction2.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "INACTIVE"));
            type2.addChild(restriction2);

            schema.addChild(type1);
            schema.addChild(type2);

            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            List<QualityIssue> issues = result.getIssuesByCategory(IssueCategory.DUPLICATE_DEFINITION);
            assertEquals(1, issues.size(), "Should detect duplicate definitions");
            assertTrue(issues.get(0).message().contains("CustomerStatusType") ||
                    issues.get(0).message().contains("AccountStatusType"));
        }

        @Test
        @DisplayName("should not report duplicates for structurally different types")
        void testDifferentNameDifferentContent() {
            // Create two SimpleTypes that are genuinely different
            XsdSimpleType type1 = new XsdSimpleType("NameType");
            XsdRestriction restriction1 = new XsdRestriction("xs:string");
            restriction1.addFacet(new XsdFacet(XsdFacetType.MAX_LENGTH, "100"));
            type1.addChild(restriction1);

            XsdSimpleType type2 = new XsdSimpleType("CodeType");
            XsdRestriction restriction2 = new XsdRestriction("xs:string");
            restriction2.addFacet(new XsdFacet(XsdFacetType.MAX_LENGTH, "10"));
            restriction2.addFacet(new XsdFacet(XsdFacetType.PATTERN, "[A-Z0-9]+"));
            type2.addChild(restriction2);

            schema.addChild(type1);
            schema.addChild(type2);

            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            List<QualityIssue> issues = result.getIssuesByCategory(IssueCategory.DUPLICATE_DEFINITION);
            assertTrue(issues.isEmpty(), "Should not report duplicates for different structures");
        }

        @Test
        @DisplayName("should not report trivial definitions as duplicates")
        void testTrivialDefinitionsNotReportedAsDuplicates() {
            // Create two elements with just a type reference (trivial structure)
            XsdElement elem1 = new XsdElement("FirstName");
            elem1.setType("xs:string");

            XsdElement elem2 = new XsdElement("LastName");
            elem2.setType("xs:string");

            schema.addChild(elem1);
            schema.addChild(elem2);

            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            // Trivial definitions should not be flagged as duplicates
            List<QualityIssue> issues = result.getIssuesByCategory(IssueCategory.DUPLICATE_DEFINITION);
            assertTrue(issues.isEmpty(), "Should not flag trivial definitions as duplicates");
        }

        @Test
        @DisplayName("duplicate detection should have INFO severity")
        void testDuplicateIssueHasInfoSeverity() {
            XsdSimpleType type1 = new XsdSimpleType("Type1");
            XsdRestriction restriction1 = new XsdRestriction("xs:string");
            restriction1.addFacet(new XsdFacet(XsdFacetType.MAX_LENGTH, "25"));
            restriction1.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "VALUE1"));
            restriction1.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "VALUE2"));
            type1.addChild(restriction1);

            XsdSimpleType type2 = new XsdSimpleType("Type2");
            XsdRestriction restriction2 = new XsdRestriction("xs:string");
            restriction2.addFacet(new XsdFacet(XsdFacetType.MAX_LENGTH, "25"));
            restriction2.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "VALUE1"));
            restriction2.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "VALUE2"));
            type2.addChild(restriction2);

            schema.addChild(type1);
            schema.addChild(type2);

            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            List<QualityIssue> issues = result.getIssuesByCategory(IssueCategory.DUPLICATE_DEFINITION);
            assertFalse(issues.isEmpty());
            assertEquals(IssueSeverity.INFO, issues.get(0).severity(),
                    "Duplicate detection should be INFO severity (not ERROR)");
        }
    }

    // ========== Duplicate Element in Container Tests ==========

    @Nested
    @DisplayName("Duplicate Element in Container Detection")
    class DuplicateElementInContainerTests {

        @Test
        @DisplayName("should detect duplicate element names in sequence")
        void testDetectsDuplicateInSequence() {
            // This simulates the FundsXML4 bug where UCITSExistingPerformanceFees appears twice
            XsdComplexType complexType = new XsdComplexType("FundFeesType");
            XsdSequence sequence = new XsdSequence();

            XsdElement elem1 = new XsdElement("UCITSExistingPerformanceFees");
            elem1.setType("YesNoType");
            sequence.addChild(elem1);

            XsdElement elem2 = new XsdElement("UCITSExistingPerformanceFees"); // Duplicate!
            elem2.setType("YesNoType");
            sequence.addChild(elem2);

            XsdElement elem3 = new XsdElement("UCITSPerformanceFees");
            elem3.setType("xs:decimal");
            sequence.addChild(elem3);

            complexType.addChild(sequence);
            schema.addChild(complexType);

            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            List<QualityIssue> issues = result.getIssuesByCategory(IssueCategory.DUPLICATE_ELEMENT_IN_CONTAINER);
            assertEquals(1, issues.size(), "Should detect exactly one duplicate element issue");

            QualityIssue issue = issues.get(0);
            assertEquals(IssueSeverity.ERROR, issue.severity(), "Duplicate element in container should be ERROR");
            assertTrue(issue.message().contains("UCITSExistingPerformanceFees"),
                    "Message should contain the duplicate element name");
            assertTrue(issue.message().contains("sequence"),
                    "Message should mention the container type");
        }

        @Test
        @DisplayName("should detect duplicate element names in choice")
        void testDetectsDuplicateInChoice() {
            XsdComplexType complexType = new XsdComplexType("PaymentType");
            XsdChoice choice = new XsdChoice();

            XsdElement elem1 = new XsdElement("CreditCard");
            elem1.setType("xs:string");
            choice.addChild(elem1);

            XsdElement elem2 = new XsdElement("CreditCard"); // Duplicate!
            elem2.setType("xs:string");
            choice.addChild(elem2);

            complexType.addChild(choice);
            schema.addChild(complexType);

            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            List<QualityIssue> issues = result.getIssuesByCategory(IssueCategory.DUPLICATE_ELEMENT_IN_CONTAINER);
            assertEquals(1, issues.size(), "Should detect duplicate in choice");
            assertTrue(issues.get(0).message().contains("choice"));
        }

        @Test
        @DisplayName("should not report unique elements as duplicates")
        void testUniqueElementsNotReported() {
            XsdComplexType complexType = new XsdComplexType("PersonType");
            XsdSequence sequence = new XsdSequence();

            XsdElement firstName = new XsdElement("FirstName");
            firstName.setType("xs:string");
            sequence.addChild(firstName);

            XsdElement lastName = new XsdElement("LastName");
            lastName.setType("xs:string");
            sequence.addChild(lastName);

            XsdElement age = new XsdElement("Age");
            age.setType("xs:int");
            sequence.addChild(age);

            complexType.addChild(sequence);
            schema.addChild(complexType);

            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            List<QualityIssue> issues = result.getIssuesByCategory(IssueCategory.DUPLICATE_ELEMENT_IN_CONTAINER);
            assertTrue(issues.isEmpty(), "Should not report unique elements as duplicates");
        }

        @Test
        @DisplayName("should include parent context in error message")
        void testIncludesParentContext() {
            XsdComplexType complexType = new XsdComplexType("OrderType");
            XsdSequence sequence = new XsdSequence();

            XsdElement elem1 = new XsdElement("ItemCode");
            elem1.setType("xs:string");
            sequence.addChild(elem1);

            XsdElement elem2 = new XsdElement("ItemCode"); // Duplicate
            elem2.setType("xs:string");
            sequence.addChild(elem2);

            complexType.addChild(sequence);
            schema.addChild(complexType);

            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            List<QualityIssue> issues = result.getIssuesByCategory(IssueCategory.DUPLICATE_ELEMENT_IN_CONTAINER);
            assertEquals(1, issues.size());

            QualityIssue issue = issues.get(0);
            assertTrue(issue.message().contains("OrderType"),
                    "Error message should include the parent complexType name");
        }

        @Test
        @DisplayName("should detect multiple different duplicate groups")
        void testMultipleDuplicateGroups() {
            XsdComplexType complexType = new XsdComplexType("TestType");
            XsdSequence sequence = new XsdSequence();

            // First duplicate group
            sequence.addChild(new XsdElement("FieldA"));
            sequence.addChild(new XsdElement("FieldA")); // Duplicate

            // Second duplicate group
            sequence.addChild(new XsdElement("FieldB"));
            sequence.addChild(new XsdElement("FieldB")); // Duplicate
            sequence.addChild(new XsdElement("FieldB")); // Triple!

            complexType.addChild(sequence);
            schema.addChild(complexType);

            XsdQualityChecker checker = new XsdQualityChecker(schema);
            QualityResult result = checker.check();

            List<QualityIssue> issues = result.getIssuesByCategory(IssueCategory.DUPLICATE_ELEMENT_IN_CONTAINER);
            assertEquals(2, issues.size(), "Should detect two duplicate groups");
        }
    }

    // ========== Graph-based component checks ==========

    @Nested
    @DisplayName("Component Checks (reference graph)")
    class ComponentCheckTests {

        private QualityResult run(String body) throws Exception {
            XsdSchema parsed = new XsdNodeFactory().fromString(
                    "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:tns=\"urn:t\" targetNamespace=\"urn:t\">"
                            + body + "</xs:schema>");
            return new XsdQualityChecker(parsed).check();
        }

        private List<QualityIssue> of(QualityResult result, IssueCategory category) {
            return result.issues().stream().filter(i -> i.category() == category).toList();
        }

        @Test
        @DisplayName("unresolved local reference is an ERROR and lowers the score")
        void unresolvedReference() throws Exception {
            QualityResult result = run("""
                    <xs:element name="Root" type="tns:Missing"><xs:annotation><xs:documentation>d</xs:documentation></xs:annotation></xs:element>
                    <xs:element name="Fine" type="xs:string"><xs:annotation><xs:documentation>d</xs:documentation></xs:annotation></xs:element>
                    """);
            List<QualityIssue> issues = of(result, IssueCategory.UNRESOLVED_REFERENCE);
            assertEquals(1, issues.size());
            assertEquals(IssueSeverity.ERROR, issues.get(0).severity());
            assertTrue(issues.get(0).message().contains("'Missing'"));
            assertEquals(List.of("Missing"), issues.get(0).affectedElements());
            assertNotNull(issues.get(0).xpath());
            assertTrue(result.score() < 100, "an ERROR must affect the score");
        }

        @Test
        @DisplayName("derivation cycle is an ERROR, containment recursion only INFO")
        void cycles() throws Exception {
            QualityResult result = run("""
                    <xs:simpleType name="A"><xs:restriction base="tns:B"/></xs:simpleType>
                    <xs:simpleType name="B"><xs:restriction base="tns:A"/></xs:simpleType>
                    <xs:element name="Folder" type="tns:FolderType"/>
                    <xs:complexType name="FolderType"><xs:sequence><xs:element ref="tns:Folder" minOccurs="0"/></xs:sequence></xs:complexType>
                    """);
            List<QualityIssue> issues = of(result, IssueCategory.CIRCULAR_REFERENCE);
            assertEquals(2, issues.size(), issues.toString());
            QualityIssue derivation = issues.stream().filter(i -> i.severity() == IssueSeverity.ERROR).findFirst().orElseThrow();
            assertEquals("Type derivation cycle: A → B → A", derivation.message());
            QualityIssue recursion = issues.stream().filter(i -> i.severity() == IssueSeverity.INFO).findFirst().orElseThrow();
            assertEquals("Recursive content model: Folder → FolderType → Folder", recursion.message());
        }

        @Test
        @DisplayName("unused and unreachable components are INFO and do not change the score")
        void unusedComponents() throws Exception {
            QualityResult result = run("""
                    <xs:element name="Root" type="xs:string"><xs:annotation><xs:documentation>d</xs:documentation></xs:annotation></xs:element>
                    <xs:complexType name="Orphan"><xs:annotation><xs:documentation>d</xs:documentation></xs:annotation><xs:sequence><xs:element name="Item" type="tns:Leaf"/></xs:sequence></xs:complexType>
                    <xs:simpleType name="Leaf"><xs:annotation><xs:documentation>d</xs:documentation></xs:annotation><xs:restriction base="xs:string"/></xs:simpleType>
                    <xs:group name="G"><xs:annotation><xs:documentation>d</xs:documentation></xs:annotation><xs:sequence><xs:element name="Entry" type="xs:string"/></xs:sequence></xs:group>
                    """);
            List<QualityIssue> issues = of(result, IssueCategory.UNUSED_COMPONENT);
            assertEquals(3, issues.size(), issues.toString());
            assertTrue(issues.stream().allMatch(i -> i.severity() == IssueSeverity.INFO));
            assertTrue(issues.stream().anyMatch(i -> i.message().startsWith("Complex type 'Orphan' is never referenced")));
            assertTrue(issues.stream().anyMatch(i -> i.message().startsWith("Group 'G' is never referenced")));
            assertTrue(issues.stream().anyMatch(i -> i.message().startsWith("Simple type 'Leaf' is only referenced from")));
            assertEquals(100, result.score());
        }

        @Test
        @DisplayName("missing documentation on global components is a SUGGESTION")
        void missingDocumentation() throws Exception {
            QualityResult result = run("""
                    <xs:element name="Documented" type="xs:string"><xs:annotation><xs:documentation>d</xs:documentation></xs:annotation></xs:element>
                    <xs:element name="Bare" type="xs:string"/>
                    """);
            List<QualityIssue> issues = of(result, IssueCategory.MISSING_DOCUMENTATION);
            assertEquals(1, issues.size());
            assertEquals(IssueSeverity.SUGGESTION, issues.get(0).severity());
            assertEquals(List.of("Bare"), issues.get(0).affectedElements());
            assertEquals(100, result.score());
        }

        @Test
        @DisplayName("single-use named type via element type is an inline candidate; base-type use is not")
        void inlineCandidates() throws Exception {
            QualityResult result = run("""
                    <xs:element name="Root" type="tns:RootType"/>
                    <xs:complexType name="RootType"><xs:sequence>
                      <xs:element name="a" type="tns:OnceType"/>
                      <xs:element name="b" type="tns:Twice"/><xs:element name="c" type="tns:Twice"/>
                    </xs:sequence></xs:complexType>
                    <xs:simpleType name="OnceType"><xs:restriction base="tns:BaseOnce"/></xs:simpleType>
                    <xs:simpleType name="BaseOnce"><xs:restriction base="xs:string"/></xs:simpleType>
                    <xs:simpleType name="Twice"><xs:restriction base="xs:string"/></xs:simpleType>
                    """);
            List<QualityIssue> issues = of(result, IssueCategory.INLINE_CANDIDATE);
            List<String> names = issues.stream().map(i -> i.affectedElements().get(0)).sorted().toList();
            assertEquals(List.of("OnceType", "RootType"), names);
            assertTrue(issues.stream().allMatch(i -> i.severity() == IssueSeverity.SUGGESTION));
        }
    }

    @Nested
    @DisplayName("Score weighting")
    class ScoreWeightingTests {

        @Test
        @DisplayName("an inconsistent definition counts its declarations, not its description lines")
        void inconsistentDefinitionWeighsDeclarations() throws Exception {
            // 12 UpperCamelCase names checked; 'Value' is declared twice with different types.
            StringBuilder body = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                body.append("<xs:element name=\"Fine").append(i).append("\" type=\"xs:string\"/>");
            }
            body.append("<xs:complexType name=\"A\"><xs:sequence><xs:element name=\"Value\" type=\"xs:string\"/></xs:sequence></xs:complexType>");
            body.append("<xs:complexType name=\"B\"><xs:sequence><xs:element name=\"Value\" type=\"xs:int\"/></xs:sequence></xs:complexType>");
            XsdSchema parsed = new XsdNodeFactory().fromString(
                    "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">" + body + "</xs:schema>");
            QualityResult result = new XsdQualityChecker(parsed).check();

            QualityIssue issue = result.issues().stream()
                    .filter(i -> i.category() == IssueCategory.INCONSISTENT_DEFINITION).findFirst().orElseThrow();
            assertEquals(1, issue.affectedCount(), "one declaration deviates from the (equally frequent) baseline");
            assertTrue(issue.affectedElements().size() > 2, "the description keeps its variant/location lines");
            assertEquals(result.totalChecks() - 1, result.passedChecks());
            assertTrue(result.score() >= 90, "score " + result.score());
        }

        @Test
        @DisplayName("only declarations deviating from the most frequent variant count")
        void inconsistentDefinitionCountsDeviationsFromMajority() throws Exception {
            StringBuilder body = new StringBuilder();
            for (String type : List.of("A", "B", "C", "D")) {
                String valueType = type.equals("D") ? "xs:int" : "xs:string";
                body.append("<xs:complexType name=\"").append(type).append("\"><xs:sequence><xs:element name=\"Value\" type=\"")
                        .append(valueType).append("\"/></xs:sequence></xs:complexType>");
            }
            XsdSchema parsed = new XsdNodeFactory().fromString(
                    "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">" + body + "</xs:schema>");
            QualityIssue issue = new XsdQualityChecker(parsed).check().issues().stream()
                    .filter(i -> i.category() == IssueCategory.INCONSISTENT_DEFINITION).findFirst().orElseThrow();
            assertEquals(1, issue.affectedCount(), "three xs:string declarations are the baseline, one xs:int deviates");
        }

        @Test
        @DisplayName("a duplicate element in a container counts its occurrences")
        void duplicateElementWeighsOccurrences() throws Exception {
            XsdSchema parsed = new XsdNodeFactory().fromString("""
                    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                      <xs:complexType name="T"><xs:sequence>
                        <xs:element name="Dup" type="xs:string"/>
                        <xs:element name="Other" type="xs:string"/>
                        <xs:element name="Dup" type="xs:string"/>
                      </xs:sequence></xs:complexType>
                    </xs:schema>
                    """);
            QualityResult result = new XsdQualityChecker(parsed).check();
            QualityIssue issue = result.issues().stream()
                    .filter(i -> i.category() == IssueCategory.DUPLICATE_ELEMENT_IN_CONTAINER).findFirst().orElseThrow();
            assertEquals(2, issue.affectedCount());
            assertEquals(3, issue.affectedElements().size(), "headline + two locations");
        }

        @Test
        @DisplayName("the compatibility constructor derives affectedCount from the list size")
        void compatibilityConstructor() {
            QualityIssue issue = new QualityIssue(IssueCategory.BEST_PRACTICE, IssueSeverity.INFO, "m", null,
                    List.of("a", "b", "c"), null, null, null);
            assertEquals(3, issue.affectedCount());
        }
    }

    @Nested
    @DisplayName("Deep nesting")
    class DeepNestingTests {

        /** Root > L2 > L3 > ... as anonymous types, {@code levels} element levels in total. */
        private static XsdSchema nested(int levels) throws Exception {
            StringBuilder xsd = new StringBuilder("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">");
            for (int i = 1; i < levels; i++) {
                xsd.append("<xs:element name=\"Level").append(i).append("\"><xs:complexType><xs:sequence>");
            }
            xsd.append("<xs:element name=\"Level").append(levels).append("\" type=\"xs:string\"/>");
            for (int i = 1; i < levels; i++) {
                xsd.append("</xs:sequence></xs:complexType></xs:element>");
            }
            return new XsdNodeFactory().fromString(xsd.append("</xs:schema>").toString());
        }

        private static List<QualityIssue> nesting(QualityResult result) {
            return result.issues().stream()
                    .filter(i -> i.category() == IssueCategory.BEST_PRACTICE && i.message().contains("nested")).toList();
        }

        @Test
        @DisplayName("counts element levels, not compositor or type nodes; default limit is 5")
        void defaultLimit() throws Exception {
            assertEquals(5, XsdQualityChecker.DEFAULT_MAX_ELEMENT_NESTING);
            assertTrue(nesting(new XsdQualityChecker(nested(5)).check()).isEmpty(), "five levels are within the limit");

            List<QualityIssue> issues = nesting(new XsdQualityChecker(nested(7)).check());
            assertEquals(2, issues.size(), "levels 6 and 7");
            assertEquals("Element 'Level6' is nested 6 element levels deep (limit 5)", issues.get(0).message());
            assertEquals(IssueSeverity.WARNING, issues.get(0).severity());
            assertEquals(List.of("Level6"), issues.get(0).affectedElements());
            assertTrue(issues.get(0).xpath().endsWith("xs:element[@name='Level6']"), issues.get(0).xpath());
        }

        @Test
        @DisplayName("the limit is configurable and a type reference starts a new declaration")
        void configurableLimit() throws Exception {
            assertEquals(4, nesting(new XsdQualityChecker(nested(7)).setMaxElementNesting(3).check()).size());
            assertTrue(nesting(new XsdQualityChecker(nested(7)).setMaxElementNesting(7).check()).isEmpty());
            assertEquals(1, new XsdQualityChecker(nested(2)).setMaxElementNesting(0).getMaxElementNesting(), "raised to 1");

            XsdSchema viaTypes = new XsdNodeFactory().fromString("""
                    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                      <xs:element name="Root" type="A"/>
                      <xs:complexType name="A"><xs:sequence><xs:element name="InA" type="B"/></xs:sequence></xs:complexType>
                      <xs:complexType name="B"><xs:sequence><xs:element name="InB" type="xs:string"/></xs:sequence></xs:complexType>
                    </xs:schema>
                    """);
            assertTrue(nesting(new XsdQualityChecker(viaTypes).setMaxElementNesting(1).check()).isEmpty(),
                    "named types keep every element at level 1");
        }
    }
}
