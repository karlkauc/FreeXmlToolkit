package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XPath2Service - XPath 2.0 evaluation using Saxon HE
 */
@DisplayName("XPath 2.0 Service Tests")
class XPath2ServiceTest {

    private XPath2Service service;
    private Document testDocument;
    private Node testElement;

    @BeforeEach
    void setUp() throws Exception {
        service = new XPath2Service();

        // Create a test XML document
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <root xmlns:ns="http://example.com/ns">
                    <person age="25">
                        <name>John Doe</name>
                        <ns:email>john@example.com</ns:email>
                    </person>
                    <person age="30">
                        <name>Jane Smith</name>
                        <ns:email>jane@example.com</ns:email>
                    </person>
                    <numbers>
                        <value>10</value>
                        <value>20</value>
                        <value>30</value>
                    </numbers>
                </root>
                """;

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        testDocument = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        testElement = testDocument.getDocumentElement();
    }

    @Nested
    @DisplayName("Expression Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should validate simple XPath 2.0 expression")
        void testValidSimpleExpression() {
            XPath2Service.ValidationResult result = service.validateExpression("1 + 1");
            assertTrue(result.valid());
            assertNull(result.errorMessage());
        }

        @Test
        @DisplayName("Should validate complex XPath 2.0 expression")
        void testValidComplexExpression() {
            XPath2Service.ValidationResult result = service.validateExpression("//person[@age > 20]/name");
            assertTrue(result.valid());
        }

        @Test
        @DisplayName("Should validate XPath 2.0 with functions")
        void testValidFunctionExpression() {
            XPath2Service.ValidationResult result = service.validateExpression("string-length('hello') > 3");
            assertTrue(result.valid());
        }

        @Test
        @DisplayName("Should validate XPath 2.0 with for-return")
        void testValidForReturnExpression() {
            XPath2Service.ValidationResult result = service.validateExpression("for $i in 1 to 5 return $i * 2");
            assertTrue(result.valid());
        }

        @Test
        @DisplayName("Should reject invalid XPath expression")
        void testInvalidExpression() {
            XPath2Service.ValidationResult result = service.validateExpression("//person[@age >");
            assertFalse(result.valid());
            assertNotNull(result.errorMessage());
        }

        @Test
        @DisplayName("Should reject empty expression")
        void testEmptyExpression() {
            XPath2Service.ValidationResult result = service.validateExpression("");
            assertFalse(result.valid());
            assertTrue(result.errorMessage().contains("empty"));
        }

        @Test
        @DisplayName("Should reject null expression")
        void testNullExpression() {
            XPath2Service.ValidationResult result = service.validateExpression(null);
            assertFalse(result.valid());
        }

        @Test
        @DisplayName("Should validate expression with namespace context")
        void testValidationWithNamespaces() {
            Map<String, String> namespaces = new HashMap<>();
            namespaces.put("ns", "http://example.com/ns");

            XPath2Service.ValidationResult result = service.validateExpression("//ns:email", namespaces);
            assertTrue(result.valid());
        }
    }

    @Nested
    @DisplayName("Expression Evaluation Tests")
    class EvaluationTests {

        @Test
        @DisplayName("Should evaluate simple arithmetic expression")
        void testEvaluateArithmetic() {
            XPath2Service.XPath2Result result = service.evaluate("2 + 3", testElement);
            assertTrue(result.success());
            assertEquals(1, result.values().size());
            assertEquals("5", result.values().get(0));
        }

        @Test
        @DisplayName("Should evaluate node selection")
        void testEvaluateNodeSelection() {
            XPath2Service.XPath2Result result = service.evaluate("//name", testElement);
            assertTrue(result.success());
            assertEquals(2, result.values().size());
            assertEquals("John Doe", result.values().get(0));
            assertEquals("Jane Smith", result.values().get(1));
        }

        @Test
        @DisplayName("Should evaluate with predicate")
        void testEvaluateWithPredicate() {
            XPath2Service.XPath2Result result = service.evaluate("//person[@age > 25]/name", testElement);
            assertTrue(result.success());
            assertEquals(1, result.values().size());
            assertEquals("Jane Smith", result.values().get(0));
        }

        @Test
        @DisplayName("Should evaluate string functions")
        void testEvaluateStringFunctions() {
            XPath2Service.XPath2Result result = service.evaluate("upper-case('hello')", testElement);
            assertTrue(result.success());
            assertEquals("HELLO", result.values().get(0));
        }

        @Test
        @DisplayName("Should evaluate aggregate functions")
        void testEvaluateAggregates() {
            XPath2Service.XPath2Result result = service.evaluate("sum(//value)", testElement);
            assertTrue(result.success());
            assertEquals("60", result.values().get(0));
        }

        @Test
        @DisplayName("Should evaluate count function")
        void testEvaluateCount() {
            XPath2Service.XPath2Result result = service.evaluate("count(//person)", testElement);
            assertTrue(result.success());
            assertEquals("2", result.values().get(0));
        }

        @Test
        @DisplayName("Should evaluate with namespace context")
        void testEvaluateWithNamespaces() {
            Map<String, String> namespaces = new HashMap<>();
            namespaces.put("ns", "http://example.com/ns");

            XPath2Service.XPath2Result result = service.evaluate("//ns:email", testElement, namespaces);
            assertTrue(result.success());
            assertEquals(2, result.values().size());
            assertEquals("john@example.com", result.values().get(0));
        }

        @Test
        @DisplayName("Should handle evaluation error gracefully")
        void testEvaluationError() {
            XPath2Service.XPath2Result result = service.evaluate("//undefined:element", testElement);
            assertFalse(result.success());
            assertNotNull(result.errorMessage());
        }

        @Test
        @DisplayName("Should evaluate XPath 2.0 for expression")
        void testEvaluateForExpression() {
            XPath2Service.XPath2Result result = service.evaluate("for $x in 1 to 3 return $x * $x", testElement);
            assertTrue(result.success());
            assertEquals(3, result.values().size());
            assertEquals("1", result.values().get(0));
            assertEquals("4", result.values().get(1));
            assertEquals("9", result.values().get(2));
        }
    }

    @Nested
    @DisplayName("Boolean Assertion Tests")
    class AssertionTests {

        @Test
        @DisplayName("Should evaluate boolean assertion - true")
        void testBooleanAssertionTrue() {
            XPath2Service.XPath2Result result = service.evaluateAssertion("count(//person) = 2", testElement, null);
            assertTrue(result.success());
            assertTrue(result.isBoolean());
            assertTrue(result.getBooleanValue());
        }

        @Test
        @DisplayName("Should evaluate boolean assertion - false")
        void testBooleanAssertionFalse() {
            XPath2Service.XPath2Result result = service.evaluateAssertion("count(//person) > 5", testElement, null);
            assertTrue(result.success());
            assertTrue(result.isBoolean());
            assertFalse(result.getBooleanValue());
        }

        @Test
        @DisplayName("Should evaluate comparison assertion")
        void testComparisonAssertion() {
            XPath2Service.XPath2Result result = service.evaluateAssertion("//person[1]/@age > 20", testElement, null);
            assertTrue(result.success());
        }

        @Test
        @DisplayName("Should evaluate complex assertion with functions")
        void testComplexAssertion() {
            String assertion = "every $p in //person satisfies $p/@age >= 18";
            XPath2Service.XPath2Result result = service.evaluateAssertion(assertion, testElement, null);
            assertTrue(result.success());
        }

        @Test
        @DisplayName("Should evaluate assertion with namespace")
        void testAssertionWithNamespace() {
            Map<String, String> namespaces = new HashMap<>();
            namespaces.put("ns", "http://example.com/ns");

            String assertion = "count(//ns:email) = 2";
            XPath2Service.XPath2Result result = service.evaluateAssertion(assertion, testElement, namespaces);
            assertTrue(result.success());
        }
    }

    @Nested
    @DisplayName("Document Evaluation Tests")
    class DocumentEvaluationTests {

        @Test
        @DisplayName("Should evaluate on document root")
        void testEvaluateOnDocument() {
            Map<String, String> namespaces = new HashMap<>();
            XPath2Service.XPath2Result result = service.evaluateOnDocument("count(/root/person)", testDocument, namespaces);
            assertTrue(result.success());
            assertEquals("2", result.values().get(0));
        }

        @Test
        @DisplayName("Should evaluate document-level assertion")
        void testDocumentAssertion() {
            Map<String, String> namespaces = new HashMap<>();
            XPath2Service.XPath2Result result = service.evaluateOnDocument("exists(/root)", testDocument, namespaces);
            assertTrue(result.success());
        }
    }

    @Nested
    @DisplayName("Namespace Extraction Tests")
    class NamespaceExtractionTests {

        @Test
        @DisplayName("Should extract default namespace")
        void testExtractDefaultNamespace() {
            Map<String, String> attributes = new HashMap<>();
            attributes.put("xmlns", "http://example.com/default");

            Map<String, String> namespaces = service.extractNamespaces(attributes);
            assertEquals(1, namespaces.size());
            assertEquals("http://example.com/default", namespaces.get(""));
        }

        @Test
        @DisplayName("Should extract prefixed namespaces")
        void testExtractPrefixedNamespaces() {
            Map<String, String> attributes = new HashMap<>();
            attributes.put("xmlns:foo", "http://example.com/foo");
            attributes.put("xmlns:bar", "http://example.com/bar");

            Map<String, String> namespaces = service.extractNamespaces(attributes);
            assertEquals(2, namespaces.size());
            assertEquals("http://example.com/foo", namespaces.get("foo"));
            assertEquals("http://example.com/bar", namespaces.get("bar"));
        }

        @Test
        @DisplayName("Should extract xpath-default-namespace")
        void testExtractXPathDefaultNamespace() {
            Map<String, String> attributes = new HashMap<>();
            attributes.put("xpath-default-namespace", "http://example.com/xsd11");

            Map<String, String> namespaces = service.extractNamespaces(attributes);
            assertEquals(1, namespaces.size());
            assertEquals("http://example.com/xsd11", namespaces.get(""));
        }

        @Test
        @DisplayName("Should extract mixed namespace declarations")
        void testExtractMixedNamespaces() {
            Map<String, String> attributes = new HashMap<>();
            attributes.put("xmlns", "http://example.com/default");
            attributes.put("xmlns:ns1", "http://example.com/ns1");
            attributes.put("xpath-default-namespace", "http://example.com/xpath");
            attributes.put("name", "test"); // non-namespace attribute

            Map<String, String> namespaces = service.extractNamespaces(attributes);
            // xpath-default-namespace overwrites xmlns for default namespace
            assertEquals(2, namespaces.size());
            assertEquals("http://example.com/xpath", namespaces.get(""));
            assertEquals("http://example.com/ns1", namespaces.get("ns1"));
        }

        @Test
        @DisplayName("Should handle null attributes map")
        void testExtractNamespacesFromNull() {
            Map<String, String> namespaces = service.extractNamespaces(null);
            assertNotNull(namespaces);
            assertTrue(namespaces.isEmpty());
        }

        @Test
        @DisplayName("Should handle empty attributes map")
        void testExtractNamespacesFromEmpty() {
            Map<String, String> namespaces = service.extractNamespaces(new HashMap<>());
            assertNotNull(namespaces);
            assertTrue(namespaces.isEmpty());
        }
    }

    @Nested
    @DisplayName("XPath 2.0 Specific Features")
    class XPath2FeaturesTests {

        @Test
        @DisplayName("Should support XPath 2.0 if-then-else")
        void testIfThenElse() {
            String expr = "if (5 > 3) then 'yes' else 'no'";
            XPath2Service.XPath2Result result = service.evaluate(expr, testElement);
            assertTrue(result.success());
            assertEquals("yes", result.values().get(0));
        }

        @Test
        @DisplayName("Should support XPath 2.0 quantified expressions")
        void testQuantifiedExpressions() {
            String expr = "some $age in //person/@age satisfies $age > 25";
            XPath2Service.XPath2Result result = service.evaluate(expr, testElement);
            assertTrue(result.success());
        }

        @Test
        @DisplayName("Should support XPath 2.0 sequence operations")
        void testSequenceOperations() {
            String expr = "(1, 2, 3, 4, 5)[. mod 2 = 0]";
            XPath2Service.XPath2Result result = service.evaluate(expr, testElement);
            assertTrue(result.success());
            assertEquals(2, result.values().size());
        }

        @Test
        @DisplayName("Should support XPath 2.0 date/time functions")
        void testDateTimeFunctions() {
            String expr = "current-date() instance of xs:date";
            XPath2Service.XPath2Result result = service.evaluate(expr, testElement);
            assertTrue(result.success());
        }
    }

    @Test
    @DisplayName("Should provide processor instance")
    void testGetProcessor() {
        assertNotNull(service.getProcessor());
    }
}
