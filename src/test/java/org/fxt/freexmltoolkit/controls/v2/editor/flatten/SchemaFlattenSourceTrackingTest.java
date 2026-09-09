package org.fxt.freexmltoolkit.controls.v2.editor.flatten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.fxt.freexmltoolkit.controls.shell.editor.SchemaActionRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the "track source files" flatten option: components pulled in from an
 * {@code xs:include} are marked with an {@code fxt:sourceFile} appinfo naming the file they came
 * from, so the origin survives writing the standalone schema to disk.
 */
class SchemaFlattenSourceTrackingTest {

    private static final String INCLUDED_XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" elementFormDefault="qualified">
              <xs:complexType name="PersonType">
                <xs:sequence>
                  <xs:element name="Name" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>
              <xs:simpleType name="EmailType">
                <xs:restriction base="xs:string"/>
              </xs:simpleType>
            </xs:schema>
            """;

    private static final String MAIN_XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" elementFormDefault="qualified">
              <xs:include schemaLocation="types.xsd"/>
              <xs:element name="Person" type="PersonType"/>
            </xs:schema>
            """;

    @Test
    void marksIncludedComponentsWithTheirSourceFile(@TempDir Path dir) throws IOException {
        String flattened = flatten(dir, INCLUDED_XSD, trackingOnly());

        assertTrue(flattened.contains("<fxt:sourceFile"),
                "included components must carry a source-file marker, was:\n" + flattened);
        assertTrue(flattened.contains(">types.xsd</fxt:sourceFile>"),
                "the marker must name the included file, was:\n" + flattened);
        assertEquals(2, countMarkers(flattened),
                "exactly the two components of types.xsd must be marked, was:\n" + flattened);
    }

    @Test
    void leavesTheSchemaAloneWhenTrackingIsOff(@TempDir Path dir) throws IOException {
        String flattened = flatten(dir, INCLUDED_XSD, FlattenOptions.NONE);

        assertFalse(flattened.contains("sourceFile"),
                "no marker may be written when the option is off, was:\n" + flattened);
    }

    @Test
    void markersSurviveAnnotationStripping(@TempDir Path dir) throws IOException {
        String flattened = flatten(dir, INCLUDED_XSD,
                new FlattenOptions(true, true, false, false, true, true));

        assertEquals(2, countMarkers(flattened),
                "the markers are added after the annotations are stripped, was:\n" + flattened);
        assertFalse(flattened.contains("<xs:documentation"),
                "documentation must still be stripped, was:\n" + flattened);
    }

    @Test
    void replacesAMarkerThatTheIncludedFileAlreadyCarried(@TempDir Path dir) throws IOException {
        String preMarked = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:fxt="http://freexmltoolkit.org/schema/flattening">
                  <xs:complexType name="PersonType">
                    <xs:annotation>
                      <xs:appinfo><fxt:sourceFile>legacy.xsd</fxt:sourceFile></xs:appinfo>
                    </xs:annotation>
                    <xs:sequence>
                      <xs:element name="Name" type="xs:string"/>
                    </xs:sequence>
                  </xs:complexType>
                </xs:schema>
                """;

        String flattened = flatten(dir, preMarked, trackingOnly());

        assertEquals(1, countMarkers(flattened),
                "the stale marker must be replaced, not duplicated, was:\n" + flattened);
        assertTrue(flattened.contains(">types.xsd</fxt:sourceFile>"),
                "the marker must name the file the component actually came from, was:\n" + flattened);
    }

    private static FlattenOptions trackingOnly() {
        return new FlattenOptions(false, false, false, false, true, true);
    }

    private static String flatten(Path dir, String includedXsd, FlattenOptions options) throws IOException {
        Files.writeString(dir.resolve("types.xsd"), includedXsd);
        Path main = dir.resolve("main.xsd");
        Files.writeString(main, MAIN_XSD);

        String flattened = SchemaActionRunner.flatten(Files.readString(main), dir, main, options);
        assertFalse(flattened.startsWith("ERROR:"), flattened);
        return flattened;
    }

    private static int countMarkers(String xsd) {
        return xsd.split("<fxt:sourceFile", -1).length - 1;
    }
}
