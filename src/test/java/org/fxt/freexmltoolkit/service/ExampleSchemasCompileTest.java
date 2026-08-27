package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shipped example schemas must be valid W3C XML Schemas (regression for schema2/schema3 src-resolve.4.1). */
class ExampleSchemasCompileTest {

    @ParameterizedTest
    @ValueSource(strings = {"schema2.xsd", "schema3.xsd", "context-sensitive-demo.xsd",
            "context-sensitive-demo2.xsd", "context-sensitive-demo3.xsd", "purchageOrder.xsd"})
    void exampleSchemaCompiles(String name) {
        File xsd = new File("release/examples/xsd", name);
        assertTrue(xsd.isFile(), xsd + " missing");
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        assertDoesNotThrow(() -> factory.newSchema(new StreamSource(xsd)), name + " must compile");
    }
}
