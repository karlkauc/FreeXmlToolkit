package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Welcome page's "Open an Example" files (UX plan 5.1) are complete and work together. */
class ExampleFilesTest {

    @Test
    void installsAllFilesAndKeepsUserChanges(@TempDir Path dir) throws Exception {
        Path document = ExampleFiles.install(dir);
        assertEquals(dir.resolve("order.xml"), document);
        for (String name : ExampleFiles.FILES) {
            assertTrue(Files.size(dir.resolve(name)) > 0, name + " is copied");
        }
        Files.writeString(document, "<order/>");
        ExampleFiles.install(dir);
        assertEquals("<order/>", Files.readString(document), "an edited example is not overwritten");
    }

    @Test
    void theOrderIsValidAgainstItsSchema(@TempDir Path dir) throws Exception {
        Path document = ExampleFiles.install(dir);
        var schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(dir.resolve("order.xsd").toFile());
        assertDoesNotThrow(() -> schema.newValidator().validate(new StreamSource(document.toFile())));
    }

    @Test
    void theStylesheetSumsTheOrder(@TempDir Path dir) throws Exception {
        Path document = ExampleFiles.install(dir);
        var transformer = new net.sf.saxon.TransformerFactoryImpl()
                .newTransformer(new StreamSource(dir.resolve("order-summary.xslt").toFile()));
        StringWriter out = new StringWriter();
        transformer.transform(new StreamSource(document.toFile()), new StreamResult(out));
        assertTrue(out.toString().contains("Order total: 125.30"), out.toString());
    }
}
