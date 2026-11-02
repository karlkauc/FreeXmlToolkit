package org.fxt.freexmltoolkit.controls.v2.editor.statistics;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A redefinition differs from its original on purpose. */
class XsdQualityCheckerRedefineTest {

    @Test
    void aRedefinedTypeIsNoInconsistentDefinition(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("base.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:complexType name="Address">
                    <xs:sequence><xs:element name="street" type="xs:string"/></xs:sequence>
                  </xs:complexType>
                </xs:schema>
                """);
        String xsd = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:redefine schemaLocation="base.xsd">
                    <xs:complexType name="Address"><xs:complexContent><xs:extension base="Address">
                      <xs:sequence><xs:element name="country" type="xs:string"/></xs:sequence>
                    </xs:extension></xs:complexContent></xs:complexType>
                  </xs:redefine>
                  <xs:element name="home" type="Address"/>
                </xs:schema>
                """;
        Path main = dir.resolve("main.xsd");
        Files.writeString(main, xsd);
        XsdSchema schema = new XsdNodeFactory().fromStringWithSchemaFile(xsd, main, dir);

        var result = new XsdQualityChecker(schema).check();

        assertFalse(result.issues().stream().anyMatch(issue -> issue.message().contains("Address")),
                result.issues().toString());
    }
}
