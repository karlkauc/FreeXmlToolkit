/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("XmlValidationError")
class XmlValidationErrorTest {

    private XmlValidationError error;

    @BeforeEach
    void setUp() {
        error = new XmlValidationError(
                XmlValidationError.ErrorType.ERROR,
                10, 5,
                "Test error message",
                "Test suggestion"
        );
    }

    @Nested
    @DisplayName("ErrorType Enum")
    class ErrorTypeEnumTests {

        @Test
        @DisplayName("All four error types are defined")
        void allErrorTypesAreDefined() {
            assertEquals(4, XmlValidationError.ErrorType.values().length);
        }

        @ParameterizedTest
        @EnumSource(XmlValidationError.ErrorType.class)
        @DisplayName("All error types have display names")
        void allErrorTypesHaveDisplayNames(XmlValidationError.ErrorType type) {
            assertNotNull(type.getDisplayName());
            assertFalse(type.getDisplayName().isEmpty());
        }

        @ParameterizedTest
        @EnumSource(XmlValidationError.ErrorType.class)
        @DisplayName("All error types have CSS classes")
        void allErrorTypesHaveCssClasses(XmlValidationError.ErrorType type) {
            assertNotNull(type.getCssClass());
            assertFalse(type.getCssClass().isEmpty());
        }

        @ParameterizedTest
        @EnumSource(XmlValidationError.ErrorType.class)
        @DisplayName("All error types have severity levels")
        void allErrorTypesHaveSeverityLevels(XmlValidationError.ErrorType type) {
            assertTrue(type.getSeverity() >= 1 && type.getSeverity() <= 4);
        }

        @Test
        @DisplayName("FATAL has highest severity")
        void fatalHasHighestSeverity() {
            assertEquals(4, XmlValidationError.ErrorType.FATAL.getSeverity());
        }

        @Test
        @DisplayName("INFO has lowest severity")
        void infoHasLowestSeverity() {
            assertEquals(1, XmlValidationError.ErrorType.INFO.getSeverity());
        }

        @Test
        @DisplayName("Severities are properly ordered")
        void severitiesAreProperlyOrdered() {
            assertTrue(XmlValidationError.ErrorType.FATAL.getSeverity() >
                    XmlValidationError.ErrorType.ERROR.getSeverity());
            assertTrue(XmlValidationError.ErrorType.ERROR.getSeverity() >
                    XmlValidationError.ErrorType.WARNING.getSeverity());
            assertTrue(XmlValidationError.ErrorType.WARNING.getSeverity() >
                    XmlValidationError.ErrorType.INFO.getSeverity());
        }
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Constructor sets all basic fields")
        void constructorSetsAllBasicFields() {
            assertEquals(XmlValidationError.ErrorType.ERROR, error.getErrorType());
            assertEquals(10, error.getLineNumber());
            assertEquals(5, error.getColumnNumber());
            assertEquals("Test error message", error.getMessage());
            assertEquals("Test suggestion", error.getSuggestion());
        }

        @Test
        @DisplayName("Constructor sets timestamp")
        void constructorSetsTimestamp() {
            assertNotNull(error.getTimestamp());
        }

        @Test
        @DisplayName("Null message becomes empty string")
        void nullMessageBecomesEmptyString() {
            XmlValidationError err = new XmlValidationError(
                    XmlValidationError.ErrorType.ERROR, 1, 1, null, null
            );
            assertEquals("", err.getMessage());
            assertEquals("", err.getSuggestion());
        }
    }

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderTests {

        @Test
        @DisplayName("Builder creates error with all fields")
        void builderCreatesErrorWithAllFields() {
            XmlValidationError built = XmlValidationError.builder()
                    .type(XmlValidationError.ErrorType.FATAL)
                    .location(20, 15)
                    .message("Builder message")
                    .suggestion("Builder suggestion")
                    .element("root")
                    .attribute("id")
                    .xpath("/root/@id")
                    .sourceCode("<root id='test'>")
                    .errorCode("ERR001")
                    .schemaReference("schema.xsd#element-root")
                    .build();

            assertEquals(XmlValidationError.ErrorType.FATAL, built.getErrorType());
            assertEquals(20, built.getLineNumber());
            assertEquals(15, built.getColumnNumber());
            assertEquals("Builder message", built.getMessage());
            assertEquals("Builder suggestion", built.getSuggestion());
            assertEquals("root", built.getElementName());
            assertEquals("id", built.getAttributeName());
            assertEquals("/root/@id", built.getXpath());
            assertEquals("<root id='test'>", built.getSourceCode());
            assertEquals("ERR001", built.getErrorCode());
            assertEquals("schema.xsd#element-root", built.getSchemaReference());
        }

        @Test
        @DisplayName("Builder defaults to ERROR type")
        void builderDefaultsToErrorType() {
            XmlValidationError built = XmlValidationError.builder()
                    .message("Test")
                    .build();

            assertEquals(XmlValidationError.ErrorType.ERROR, built.getErrorType());
        }

        @Test
        @DisplayName("Builder returns itself for chaining")
        void builderReturnsSelfForChaining() {
            XmlValidationError.Builder builder = XmlValidationError.builder();
            assertSame(builder, builder.type(XmlValidationError.ErrorType.ERROR));
            assertSame(builder, builder.location(1, 1));
            assertSame(builder, builder.message("test"));
        }
    }

    @Nested
    @DisplayName("Static Factory Methods")
    class StaticFactoryMethodTests {

        @Test
        @DisplayName("fatal() with location creates FATAL error")
        void fatalWithLocationCreatesFatalError() {
            XmlValidationError err = XmlValidationError.fatal(10, 5, "Fatal error");

            assertEquals(XmlValidationError.ErrorType.FATAL, err.getErrorType());
            assertEquals(10, err.getLineNumber());
            assertEquals(5, err.getColumnNumber());
            assertEquals("Fatal error", err.getMessage());
        }

        @Test
        @DisplayName("error() with location creates ERROR")
        void errorWithLocationCreatesError() {
            XmlValidationError err = XmlValidationError.error(10, 5, "Error message");

            assertEquals(XmlValidationError.ErrorType.ERROR, err.getErrorType());
            assertEquals(10, err.getLineNumber());
        }

        @Test
        @DisplayName("warning() with location creates WARNING")
        void warningWithLocationCreatesWarning() {
            XmlValidationError err = XmlValidationError.warning(10, 5, "Warning message");

            assertEquals(XmlValidationError.ErrorType.WARNING, err.getErrorType());
        }

        @Test
        @DisplayName("info() with location creates INFO")
        void infoWithLocationCreatesInfo() {
            XmlValidationError err = XmlValidationError.info(10, 5, "Info message");

            assertEquals(XmlValidationError.ErrorType.INFO, err.getErrorType());
        }

        @Test
        @DisplayName("fatal() without location creates FATAL at line 0")
        void fatalWithoutLocationCreatesFatalAtLine0() {
            XmlValidationError err = XmlValidationError.fatal("Fatal error", "Fix it");

            assertEquals(XmlValidationError.ErrorType.FATAL, err.getErrorType());
            assertEquals(0, err.getLineNumber());
            assertEquals(0, err.getColumnNumber());
            assertEquals("Fix it", err.getSuggestion());
        }

        @Test
        @DisplayName("error() without location creates ERROR at line 0")
        void errorWithoutLocationCreatesErrorAtLine0() {
            XmlValidationError err = XmlValidationError.error("Error message", "Suggestion");

            assertEquals(XmlValidationError.ErrorType.ERROR, err.getErrorType());
            assertEquals(0, err.getLineNumber());
        }

        @Test
        @DisplayName("warning() without location creates WARNING at line 0")
        void warningWithoutLocationCreatesWarningAtLine0() {
            XmlValidationError err = XmlValidationError.warning("Warning message", "Suggestion");

            assertEquals(XmlValidationError.ErrorType.WARNING, err.getErrorType());
        }

        @Test
        @DisplayName("info() without suggestion creates INFO")
        void infoWithoutSuggestionCreatesInfo() {
            XmlValidationError err = XmlValidationError.info("Info message");

            assertEquals(XmlValidationError.ErrorType.INFO, err.getErrorType());
            assertEquals("", err.getSuggestion());
        }
    }

    @Nested
    @DisplayName("hasLocation")
    class HasLocationTests {

        @Test
        @DisplayName("Returns true when both line and column are set")
        void returnsTrueWhenBothLineAndColumnSet() {
            assertTrue(error.hasLocation());
        }

        @Test
        @DisplayName("Returns true when only line is set")
        void returnsTrueWhenOnlyLineSet() {
            XmlValidationError err = new XmlValidationError(
                    XmlValidationError.ErrorType.ERROR, 10, 0, "Test", ""
            );
            assertTrue(err.hasLocation());
        }

        @Test
        @DisplayName("Returns true when only column is set")
        void returnsTrueWhenOnlyColumnSet() {
            XmlValidationError err = new XmlValidationError(
                    XmlValidationError.ErrorType.ERROR, 0, 5, "Test", ""
            );
            assertTrue(err.hasLocation());
        }

        @Test
        @DisplayName("Returns false when neither line nor column is set")
        void returnsFalseWhenNoLocation() {
            XmlValidationError err = new XmlValidationError(
                    XmlValidationError.ErrorType.ERROR, 0, 0, "Test", ""
            );
            assertFalse(err.hasLocation());
        }
    }

    @Nested
    @DisplayName("getLocationString")
    class GetLocationStringTests {

        @Test
        @DisplayName("Returns line and column when both set")
        void returnsLineAndColumnWhenBothSet() {
            assertEquals("Line 10, Column 5", error.getLocationString());
        }

        @Test
        @DisplayName("Returns only line when column is 0")
        void returnsOnlyLineWhenColumn0() {
            XmlValidationError err = new XmlValidationError(
                    XmlValidationError.ErrorType.ERROR, 10, 0, "Test", ""
            );
            assertEquals("Line 10", err.getLocationString());
        }

        @Test
        @DisplayName("Returns only column when line is 0")
        void returnsOnlyColumnWhenLine0() {
            XmlValidationError err = new XmlValidationError(
                    XmlValidationError.ErrorType.ERROR, 0, 5, "Test", ""
            );
            assertEquals("Column 5", err.getLocationString());
        }

        @Test
        @DisplayName("Returns unknown location when neither set")
        void returnsUnknownWhenNeitherSet() {
            XmlValidationError err = new XmlValidationError(
                    XmlValidationError.ErrorType.ERROR, 0, 0, "Test", ""
            );
            assertEquals("Unknown location", err.getLocationString());
        }
    }

    @Nested
    @DisplayName("getContextString")
    class GetContextStringTests {

        @Test
        @DisplayName("Returns empty string when no context")
        void returnsEmptyWhenNoContext() {
            assertEquals("", error.getContextString());
        }

        @Test
        @DisplayName("Returns element context when element set")
        void returnsElementContextWhenElementSet() {
            error.setElementName("root");
            assertEquals("Element: root", error.getContextString());
        }

        @Test
        @DisplayName("Returns element and attribute context")
        void returnsElementAndAttributeContext() {
            error.setElementName("root");
            error.setAttributeName("id");
            assertEquals("Element: root, Attribute: id", error.getContextString());
        }

        @Test
        @DisplayName("Returns only attribute when no element")
        void returnsOnlyAttributeWhenNoElement() {
            error.setAttributeName("id");
            assertEquals("Attribute: id", error.getContextString());
        }

        @Test
        @DisplayName("Includes XPath when set")
        void includesXPathWhenSet() {
            error.setXpath("/root/@id");
            assertEquals("XPath: /root/@id", error.getContextString());
        }

        @Test
        @DisplayName("Combines all context information")
        void combinesAllContextInformation() {
            error.setElementName("root");
            error.setAttributeName("id");
            error.setXpath("/root/@id");

            String context = error.getContextString();
            assertTrue(context.contains("Element: root"));
            assertTrue(context.contains("Attribute: id"));
            assertTrue(context.contains("XPath: /root/@id"));
        }
    }

    @Nested
    @DisplayName("isCritical")
    class IsCriticalTests {

        @Test
        @DisplayName("FATAL is critical")
        void fatalIsCritical() {
            XmlValidationError err = XmlValidationError.fatal(1, 1, "Fatal");
            assertTrue(err.isCritical());
        }

        @Test
        @DisplayName("ERROR is critical")
        void errorIsCritical() {
            assertTrue(error.isCritical());
        }

        @Test
        @DisplayName("WARNING is not critical")
        void warningIsNotCritical() {
            XmlValidationError err = XmlValidationError.warning(1, 1, "Warning");
            assertFalse(err.isCritical());
        }

        @Test
        @DisplayName("INFO is not critical")
        void infoIsNotCritical() {
            XmlValidationError err = XmlValidationError.info(1, 1, "Info");
            assertFalse(err.isCritical());
        }
    }

    @Nested
    @DisplayName("getFormattedMessage")
    class GetFormattedMessageTests {

        @Test
        @DisplayName("Includes error type in brackets")
        void includesErrorTypeInBrackets() {
            assertTrue(error.getFormattedMessage().startsWith("[Error]"));
        }

        @Test
        @DisplayName("Includes location when available")
        void includesLocationWhenAvailable() {
            String formatted = error.getFormattedMessage();
            assertTrue(formatted.contains("Line 10, Column 5"));
        }

        @Test
        @DisplayName("Includes message")
        void includesMessage() {
            assertTrue(error.getFormattedMessage().contains("Test error message"));
        }

        @Test
        @DisplayName("Includes context when available")
        void includesContextWhenAvailable() {
            error.setElementName("root");
            String formatted = error.getFormattedMessage();
            assertTrue(formatted.contains("Element: root"));
        }
    }

    @Nested
    @DisplayName("getDetailedDescription")
    class GetDetailedDescriptionTests {

        @Test
        @DisplayName("Includes formatted message")
        void includesFormattedMessage() {
            String detailed = error.getDetailedDescription();
            assertTrue(detailed.contains("[Error]"));
        }

        @Test
        @DisplayName("Includes suggestion when available")
        void includesSuggestionWhenAvailable() {
            String detailed = error.getDetailedDescription();
            assertTrue(detailed.contains("Suggestion: Test suggestion"));
        }

        @Test
        @DisplayName("Includes schema reference when available")
        void includesSchemaReferenceWhenAvailable() {
            error.setSchemaReference("schema.xsd#element-root");
            String detailed = error.getDetailedDescription();
            assertTrue(detailed.contains("Schema Reference: schema.xsd#element-root"));
        }

        @Test
        @DisplayName("Includes source code when available")
        void includesSourceCodeWhenAvailable() {
            error.setSourceCode("<root>");
            String detailed = error.getDetailedDescription();
            assertTrue(detailed.contains("Source: <root>"));
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("Same error equals itself")
        void sameErrorEqualsItself() {
            assertEquals(error, error);
        }

        @Test
        @DisplayName("Equal errors with same properties")
        void equalErrorsWithSameProperties() {
            XmlValidationError other = new XmlValidationError(
                    XmlValidationError.ErrorType.ERROR,
                    10, 5, "Test error message", "Different suggestion"
            );
            assertEquals(error, other);
        }

        @Test
        @DisplayName("Different errors are not equal")
        void differentErrorsAreNotEqual() {
            XmlValidationError other = new XmlValidationError(
                    XmlValidationError.ErrorType.ERROR,
                    10, 5, "Different message", "Test suggestion"
            );
            assertNotEquals(error, other);
        }

        @Test
        @DisplayName("Not equal to null")
        void notEqualToNull() {
            assertNotEquals(null, error);
        }

        @Test
        @DisplayName("Equal errors have same hash code")
        void equalErrorsHaveSameHashCode() {
            XmlValidationError other = new XmlValidationError(
                    XmlValidationError.ErrorType.ERROR,
                    10, 5, "Test error message", "Different"
            );
            assertEquals(error.hashCode(), other.hashCode());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toString returns formatted message")
        void toStringReturnsFormattedMessage() {
            assertEquals(error.getFormattedMessage(), error.toString());
        }
    }

    @Nested
    @DisplayName("Setters")
    class SetterTests {

        @Test
        @DisplayName("Can set and get element name")
        void canSetAndGetElementName() {
            error.setElementName("testElement");
            assertEquals("testElement", error.getElementName());
        }

        @Test
        @DisplayName("Can set and get attribute name")
        void canSetAndGetAttributeName() {
            error.setAttributeName("testAttribute");
            assertEquals("testAttribute", error.getAttributeName());
        }

        @Test
        @DisplayName("Can set and get XPath")
        void canSetAndGetXPath() {
            error.setXpath("/root/element");
            assertEquals("/root/element", error.getXpath());
        }

        @Test
        @DisplayName("Can set and get source code")
        void canSetAndGetSourceCode() {
            error.setSourceCode("<element/>");
            assertEquals("<element/>", error.getSourceCode());
        }

        @Test
        @DisplayName("Can set and get error code")
        void canSetAndGetErrorCode() {
            error.setErrorCode("ERR001");
            assertEquals("ERR001", error.getErrorCode());
        }

        @Test
        @DisplayName("Can set and get schema reference")
        void canSetAndGetSchemaReference() {
            error.setSchemaReference("schema.xsd#type");
            assertEquals("schema.xsd#type", error.getSchemaReference());
        }
    }

    @Nested
    @DisplayName("getCssClass")
    class GetCssClassTests {

        @Test
        @DisplayName("Returns correct CSS class for error type")
        void returnsCorrectCssClass() {
            assertEquals("error", error.getCssClass());
        }

        @Test
        @DisplayName("FATAL returns fatal-error CSS class")
        void fatalReturnsFatalErrorCssClass() {
            XmlValidationError err = XmlValidationError.fatal(1, 1, "Fatal");
            assertEquals("fatal-error", err.getCssClass());
        }
    }

    @Nested
    @DisplayName("getSeverity")
    class GetSeverityTests {

        @Test
        @DisplayName("Returns error type severity")
        void returnsErrorTypeSeverity() {
            assertEquals(XmlValidationError.ErrorType.ERROR.getSeverity(), error.getSeverity());
        }
    }
}
