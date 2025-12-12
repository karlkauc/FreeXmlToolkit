package org.fxt.freexmltoolkit.service.xsd;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link XsdParseException}.
 */
class XsdParseExceptionTest {

    @Test
    void simpleConstructor_shouldSetMessage() {
        XsdParseException ex = new XsdParseException("Test message");

        assertEquals("Test message", ex.getMessage());
        assertNull(ex.getCause());
        assertTrue(ex.getSourceFile().isEmpty());
        assertTrue(ex.getSchemaLocation().isEmpty());
        assertEquals(-1, ex.getLineNumber());
        assertEquals(-1, ex.getColumnNumber());
        assertEquals(XsdParseException.ErrorType.PARSE_ERROR, ex.getErrorType());
    }

    @Test
    void constructorWithCause_shouldSetMessageAndCause() {
        Exception cause = new RuntimeException("Cause");
        XsdParseException ex = new XsdParseException("Test message", cause);

        assertEquals("Test message", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    void fullConstructor_shouldSetAllFields() {
        Path path = Path.of("/test/schema.xsd");
        Exception cause = new RuntimeException("Cause");

        XsdParseException ex = new XsdParseException(
                "Test message",
                cause,
                path,
                "included.xsd",
                10, 5,
                XsdParseException.ErrorType.CIRCULAR_REFERENCE
        );

        assertEquals("Test message", ex.getMessage());
        assertEquals(cause, ex.getCause());
        assertTrue(ex.getSourceFile().isPresent());
        assertEquals(path, ex.getSourceFile().get());
        assertTrue(ex.getSchemaLocation().isPresent());
        assertEquals("included.xsd", ex.getSchemaLocation().get());
        assertEquals(10, ex.getLineNumber());
        assertEquals(5, ex.getColumnNumber());
        assertEquals(XsdParseException.ErrorType.CIRCULAR_REFERENCE, ex.getErrorType());
        assertTrue(ex.hasLocation());
    }

    @Test
    void fileNotFound_shouldCreateCorrectException() {
        Path path = Path.of("/test/missing.xsd");

        XsdParseException ex = XsdParseException.fileNotFound(path);

        assertTrue(ex.getMessage().contains("not found"));
        assertTrue(ex.getMessage().contains(path.toString()));
        assertEquals(XsdParseException.ErrorType.FILE_NOT_FOUND, ex.getErrorType());
        assertTrue(ex.getSourceFile().isPresent());
        assertEquals(path, ex.getSourceFile().get());
    }

    @Test
    void circularReference_shouldCreateCorrectException() {
        Path path = Path.of("/test/schema.xsd");

        XsdParseException ex = XsdParseException.circularReference(path, "types.xsd");

        assertTrue(ex.getMessage().contains("Circular reference"));
        assertTrue(ex.getMessage().contains("types.xsd"));
        assertEquals(XsdParseException.ErrorType.CIRCULAR_REFERENCE, ex.getErrorType());
        assertTrue(ex.getSourceFile().isPresent());
        assertTrue(ex.getSchemaLocation().isPresent());
        assertEquals("types.xsd", ex.getSchemaLocation().get());
    }

    @Test
    void unresolvedReference_shouldCreateCorrectException() {
        Path path = Path.of("/test/schema.xsd");

        XsdParseException ex = XsdParseException.unresolvedReference(path, "external.xsd");

        assertTrue(ex.getMessage().contains("Cannot resolve"));
        assertTrue(ex.getMessage().contains("external.xsd"));
        assertEquals(XsdParseException.ErrorType.UNRESOLVED_REFERENCE, ex.getErrorType());
    }

    @Test
    void maxDepthExceeded_shouldCreateCorrectException() {
        XsdParseException ex = XsdParseException.maxDepthExceeded(60, 50);

        assertTrue(ex.getMessage().contains("Maximum include depth exceeded"));
        assertTrue(ex.getMessage().contains("60"));
        assertTrue(ex.getMessage().contains("50"));
        assertEquals(XsdParseException.ErrorType.MAX_DEPTH_EXCEEDED, ex.getErrorType());
    }

    @Test
    void malformedXml_shouldCreateCorrectException() {
        Path path = Path.of("/test/bad.xsd");
        Exception cause = new RuntimeException("XML error");

        XsdParseException ex = XsdParseException.malformedXml(path, cause, 15, 8);

        assertTrue(ex.getMessage().contains("Malformed XML"));
        assertTrue(ex.getMessage().contains("line 15"));
        assertTrue(ex.getMessage().contains("column 8"));
        assertEquals(XsdParseException.ErrorType.MALFORMED_XML, ex.getErrorType());
        assertEquals(15, ex.getLineNumber());
        assertEquals(8, ex.getColumnNumber());
        assertTrue(ex.hasLocation());
        assertEquals(cause, ex.getCause());
    }

    @Test
    void networkError_shouldCreateCorrectException() {
        Exception cause = new RuntimeException("Connection failed");

        XsdParseException ex = XsdParseException.networkError("http://example.com/schema.xsd", cause);

        assertTrue(ex.getMessage().contains("Failed to fetch remote schema"));
        assertTrue(ex.getMessage().contains("http://example.com/schema.xsd"));
        assertEquals(XsdParseException.ErrorType.NETWORK_ERROR, ex.getErrorType());
        assertEquals(cause, ex.getCause());
    }

    @Test
    void hasLocation_shouldReturnFalseWhenNoLocation() {
        XsdParseException ex = new XsdParseException("Test");

        assertFalse(ex.hasLocation());
    }

    @Test
    void hasLocation_shouldReturnFalseWhenPartialLocation() {
        XsdParseException ex = new XsdParseException(
                "Test", null, null, null, 10, -1,
                XsdParseException.ErrorType.PARSE_ERROR
        );

        assertFalse(ex.hasLocation());
    }

    @Test
    void toString_shouldContainAllRelevantInfo() {
        Path path = Path.of("/test/schema.xsd");
        XsdParseException ex = new XsdParseException(
                "Test error",
                null,
                path,
                null,
                5, 10,
                XsdParseException.ErrorType.INVALID_SCHEMA
        );

        String str = ex.toString();

        assertTrue(str.contains("INVALID_SCHEMA"));
        assertTrue(str.contains("Test error"));
        assertTrue(str.contains(path.toString()));
        assertTrue(str.contains("line: 5"));
        assertTrue(str.contains("column: 10"));
    }

    @Test
    void toString_withoutSourceFile_shouldNotContainFileInfo() {
        XsdParseException ex = new XsdParseException("Simple error");

        String str = ex.toString();

        assertTrue(str.contains("Simple error"));
        assertFalse(str.contains("file:"));
    }
}
