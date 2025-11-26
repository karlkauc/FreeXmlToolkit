package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XPathExpressionAnalyzer.
 * Tests context detection for XPath and XQuery expressions.
 */
@DisplayName("XPathExpressionAnalyzer Tests")
class XPathExpressionAnalyzerTest {

    @Nested
    @DisplayName("Path Context Detection")
    class PathContextTests {

        @Test
        @DisplayName("Empty expression should return PATH_START")
        void emptyExpression_shouldReturnPathStart() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("", 0, false);

            assertEquals(XPathContextType.PATH_START, context.getContextType());
            assertEquals("", context.getCurrentToken());
        }

        @Test
        @DisplayName("Single slash should return AFTER_SLASH")
        void singleSlash_shouldReturnAfterSlash() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("/", 1, false);

            assertEquals(XPathContextType.AFTER_SLASH, context.getContextType());
        }

        @Test
        @DisplayName("Double slash should return AFTER_DOUBLE_SLASH")
        void doubleSlash_shouldReturnAfterDoubleSlash() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("//", 2, false);

            assertEquals(XPathContextType.AFTER_DOUBLE_SLASH, context.getContextType());
        }

        @Test
        @DisplayName("Expression after slash should detect context")
        void expressionAfterSlash_shouldDetectContext() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("/root/", 6, false);

            assertEquals(XPathContextType.AFTER_SLASH, context.getContextType());
        }

        @Test
        @DisplayName("Partial token after slash should track token")
        void partialToken_shouldTrackToken() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("/root/ch", 8, false);

            assertEquals("ch", context.getCurrentToken());
        }
    }

    @Nested
    @DisplayName("Attribute Context Detection")
    class AttributeContextTests {

        @Test
        @DisplayName("At symbol should return AFTER_AT")
        void atSymbol_shouldReturnAfterAt() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("//element/@", 11, false);

            assertEquals(XPathContextType.AFTER_AT, context.getContextType());
        }

        @Test
        @DisplayName("Attribute with prefix should track token")
        void attributeWithPrefix_shouldTrackToken() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("//element/@id", 13, false);

            assertEquals("id", context.getCurrentToken());
            assertEquals(XPathContextType.AFTER_AT, context.getContextType());
        }
    }

    @Nested
    @DisplayName("Predicate Context Detection")
    class PredicateContextTests {

        @Test
        @DisplayName("Open bracket should return IN_PREDICATE")
        void openBracket_shouldReturnInPredicate() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("//element[", 10, false);

            assertEquals(XPathContextType.IN_PREDICATE, context.getContextType());
            assertEquals(1, context.getPredicateDepth());
        }

        @Test
        @DisplayName("Nested predicates should track depth")
        void nestedPredicates_shouldTrackDepth() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("//element[child[", 16, false);

            assertEquals(XPathContextType.IN_PREDICATE, context.getContextType());
            assertEquals(2, context.getPredicateDepth());
        }

        @Test
        @DisplayName("Closed predicate should exit predicate context")
        void closedPredicate_shouldExitContext() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("//element[1]/", 13, false);

            assertEquals(XPathContextType.AFTER_SLASH, context.getContextType());
            assertEquals(0, context.getPredicateDepth());
        }
    }

    @Nested
    @DisplayName("Axis Context Detection")
    class AxisContextTests {

        @Test
        @DisplayName("After double colon should return AFTER_AXIS")
        void afterDoubleColon_shouldReturnAfterAxis() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("child::", 7, false);

            assertEquals(XPathContextType.AFTER_AXIS, context.getContextType());
        }

        @Test
        @DisplayName("Ancestor axis should be recognized")
        void ancestorAxis_shouldBeRecognized() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("ancestor::", 10, false);

            assertEquals(XPathContextType.AFTER_AXIS, context.getContextType());
        }
    }

    @Nested
    @DisplayName("Function Context Detection")
    class FunctionContextTests {

        @Test
        @DisplayName("Open parenthesis after function name should return IN_FUNCTION_ARGS")
        void openParen_shouldReturnInFunctionArgs() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("contains(", 9, false);

            assertEquals(XPathContextType.IN_FUNCTION_ARGS, context.getContextType());
            assertEquals(1, context.getFunctionDepth());
        }

        @Test
        @DisplayName("Nested function calls should track depth")
        void nestedFunctions_shouldTrackDepth() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("concat(substring(", 17, false);

            assertEquals(XPathContextType.IN_FUNCTION_ARGS, context.getContextType());
            assertEquals(2, context.getFunctionDepth());
        }
    }

    @Nested
    @DisplayName("String Literal Context Detection")
    class StringLiteralContextTests {

        @Test
        @DisplayName("Inside single quotes should return IN_STRING_LITERAL")
        void insideSingleQuotes_shouldReturnInStringLiteral() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("contains(text, 'hello", 21, false);

            assertEquals(XPathContextType.IN_STRING_LITERAL, context.getContextType());
        }

        @Test
        @DisplayName("Inside double quotes should return IN_STRING_LITERAL")
        void insideDoubleQuotes_shouldReturnInStringLiteral() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("contains(text, \"hello", 21, false);

            assertEquals(XPathContextType.IN_STRING_LITERAL, context.getContextType());
        }

        @Test
        @DisplayName("After closed string should exit string context")
        void afterClosedString_shouldExitContext() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("contains(text, 'hello')", 23, false);

            assertNotEquals(XPathContextType.IN_STRING_LITERAL, context.getContextType());
        }
    }

    @Nested
    @DisplayName("Operator Context Detection")
    class OperatorContextTests {

        @Test
        @DisplayName("After 'and' should return AFTER_OPERATOR")
        void afterAnd_shouldReturnAfterOperator() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("@id = '1' and ", 14, false);

            assertEquals(XPathContextType.AFTER_OPERATOR, context.getContextType());
        }

        @Test
        @DisplayName("After 'or' should return AFTER_OPERATOR")
        void afterOr_shouldReturnAfterOperator() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("@id = '1' or ", 13, false);

            assertEquals(XPathContextType.AFTER_OPERATOR, context.getContextType());
        }
    }

    @Nested
    @DisplayName("XQuery FLWOR Context Detection")
    class XQueryFlworContextTests {

        @Test
        @DisplayName("After 'for' keyword should return AFTER_FOR")
        void afterFor_shouldReturnAfterFor() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("for ", 4, true);

            assertEquals(XPathContextType.AFTER_FOR, context.getContextType());
        }

        @Test
        @DisplayName("After 'let' keyword should return AFTER_LET")
        void afterLet_shouldReturnAfterLet() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("let ", 4, true);

            assertEquals(XPathContextType.AFTER_LET, context.getContextType());
        }

        @Test
        @DisplayName("After 'where' keyword should return AFTER_WHERE")
        void afterWhere_shouldReturnAfterWhere() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("for $x in //item where ", 23, true);

            assertEquals(XPathContextType.AFTER_WHERE, context.getContextType());
        }

        @Test
        @DisplayName("After 'return' keyword should return AFTER_RETURN")
        void afterReturn_shouldReturnAfterReturn() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("for $x in //item return ", 24, true);

            assertEquals(XPathContextType.AFTER_RETURN, context.getContextType());
        }

        @Test
        @DisplayName("After 'in' keyword should return AFTER_BINDING")
        void afterIn_shouldReturnAfterBinding() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("for $x in ", 10, true);

            assertEquals(XPathContextType.AFTER_BINDING, context.getContextType());
        }

        @Test
        @DisplayName("After dollar sign should return AFTER_DOLLAR")
        void afterDollar_shouldReturnAfterDollar() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("for $", 5, true);

            assertEquals(XPathContextType.AFTER_DOLLAR, context.getContextType());
        }

        @Test
        @DisplayName("XQuery keywords should not trigger in XPath mode")
        void xqueryKeywords_shouldNotTriggerInXPathMode() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("for ", 4, false);

            // In XPath mode, 'for' is just a partial token, not a keyword
            assertNotEquals(XPathContextType.AFTER_FOR, context.getContextType());
        }
    }

    @Nested
    @DisplayName("Token Position Tracking")
    class TokenPositionTests {

        @Test
        @DisplayName("Token start position should be correct")
        void tokenStartPosition_shouldBeCorrect() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("/root/child", 11, false);

            assertEquals(6, context.getTokenStartPosition());
            assertEquals("child", context.getCurrentToken());
        }

        @Test
        @DisplayName("Token length should be tracked")
        void tokenLength_shouldBeTracked() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("/root/child", 11, false);

            assertEquals(5, context.getCurrentTokenLength());
        }

        @Test
        @DisplayName("Caret in middle of token should track partial token")
        void caretInMiddle_shouldTrackPartialToken() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("/root/children", 9, false);

            assertEquals("chi", context.getCurrentToken());
            assertEquals(3, context.getCurrentTokenLength());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Null expression should return empty context")
        void nullExpression_shouldReturnEmptyContext() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze(null, 0, false);

            assertNotNull(context);
            assertEquals(XPathContextType.UNKNOWN, context.getContextType());
        }

        @Test
        @DisplayName("Caret beyond expression length should handle gracefully")
        void caretBeyondLength_shouldHandleGracefully() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("/root", 100, false);

            assertNotNull(context);
        }

        @Test
        @DisplayName("Negative caret position should handle gracefully")
        void negativeCaretPosition_shouldHandleGracefully() {
            XPathEditorContext context = XPathExpressionAnalyzer.analyze("/root", -1, false);

            assertNotNull(context);
        }

        @Test
        @DisplayName("Complex expression should parse correctly")
        void complexExpression_shouldParseCorrectly() {
            String expr = "//book[contains(@isbn, 'X')]/title";
            XPathEditorContext context = XPathExpressionAnalyzer.analyze(expr, expr.length(), false);

            assertNotNull(context);
            assertEquals("title", context.getCurrentToken());
        }
    }

    @Nested
    @DisplayName("XPath vs XQuery Mode")
    class XPathVsXQueryModeTests {

        @ParameterizedTest
        @MethodSource("provideKeywordExpressions")
        @DisplayName("XQuery keywords should be context-sensitive to mode")
        void xqueryKeywords_shouldBeContextSensitive(String expression, int caretPos,
                                                       XPathContextType expectedXPath,
                                                       XPathContextType expectedXQuery) {
            XPathEditorContext xpathContext = XPathExpressionAnalyzer.analyze(expression, caretPos, false);
            XPathEditorContext xqueryContext = XPathExpressionAnalyzer.analyze(expression, caretPos, true);

            assertEquals(expectedXPath, xpathContext.getContextType(),
                    "XPath mode context mismatch for: " + expression);
            assertEquals(expectedXQuery, xqueryContext.getContextType(),
                    "XQuery mode context mismatch for: " + expression);
        }

        static Stream<Arguments> provideKeywordExpressions() {
            return Stream.of(
                    Arguments.of("for ", 4, XPathContextType.PATH_START, XPathContextType.AFTER_FOR),
                    Arguments.of("let ", 4, XPathContextType.PATH_START, XPathContextType.AFTER_LET)
            );
        }
    }
}
