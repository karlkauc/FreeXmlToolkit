/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2026.
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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end check that de-selecting documentation languages in the generator really removes
 * them from the generated HTML output (detail pages, type pages, attribute/enumeration docs,
 * search index and languages.json). Regression test for "deselected languages are still
 * generated".
 */
@DisplayName("XSD documentation - de-selected languages are excluded from the output")
class XsdDocumentationLanguageSelectionTest {

    private static final String XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" elementFormDefault="qualified">
              <xs:element name="root">
                <xs:annotation>
                  <xs:documentation>ROOT_DEFAULT_TEXT</xs:documentation>
                  <xs:documentation xml:lang="en">ROOT_ENGLISH_TEXT</xs:documentation>
                  <xs:documentation xml:lang="de">ROOT_GERMAN_TEXT</xs:documentation>
                </xs:annotation>
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="item" type="ItemType">
                      <xs:annotation>
                        <xs:documentation xml:lang="en">ITEM_ENGLISH_TEXT</xs:documentation>
                        <xs:documentation xml:lang="de">ITEM_GERMAN_TEXT</xs:documentation>
                      </xs:annotation>
                    </xs:element>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
              <xs:complexType name="ItemType">
                <xs:annotation>
                  <xs:documentation xml:lang="en">TYPE_ENGLISH_TEXT</xs:documentation>
                  <xs:documentation xml:lang="de">TYPE_GERMAN_TEXT</xs:documentation>
                </xs:annotation>
                <xs:sequence>
                  <xs:element name="code" type="CodeType"/>
                </xs:sequence>
                <xs:attribute name="id" type="xs:string">
                  <xs:annotation>
                    <xs:documentation xml:lang="en">ATTR_ENGLISH_TEXT</xs:documentation>
                    <xs:documentation xml:lang="de">ATTR_GERMAN_TEXT</xs:documentation>
                  </xs:annotation>
                </xs:attribute>
              </xs:complexType>
              <xs:simpleType name="CodeType">
                <xs:annotation>
                  <xs:documentation xml:lang="en">SIMPLE_ENGLISH_TEXT</xs:documentation>
                  <xs:documentation xml:lang="de">SIMPLE_GERMAN_TEXT</xs:documentation>
                </xs:annotation>
                <xs:restriction base="xs:string">
                  <xs:enumeration value="A">
                    <xs:annotation>
                      <xs:documentation xml:lang="en">ENUM_ENGLISH_TEXT</xs:documentation>
                      <xs:documentation xml:lang="de">ENUM_GERMAN_TEXT</xs:documentation>
                    </xs:annotation>
                  </xs:enumeration>
                </xs:restriction>
              </xs:simpleType>
            </xs:schema>
            """;

    @Test
    @DisplayName("Only the selected language (plus untagged 'default') appears in the generated HTML")
    void deselectedLanguagesAreNotGenerated(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("langs.xsd");
        Files.writeString(xsd, XSD, StandardCharsets.UTF_8);
        Path out = tmp.resolve("out");

        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.toString());
        service.setMethod(XsdDocumentationService.ImageOutputMethod.SVG);
        service.setIncludedLanguages(Set.of("en"));
        service.generateXsdDocumentation(out.toFile());

        List<Path> htmlFiles;
        try (Stream<Path> files = Files.walk(out)) {
            htmlFiles = files.filter(p -> p.toString().endsWith(".html")).toList();
        }
        assertFalse(htmlFiles.isEmpty(), "documentation must have been generated");

        String allHtml = concat(htmlFiles);
        // The selected language and the untagged fallback must be present.
        assertTrue(allHtml.contains("ROOT_ENGLISH_TEXT"), "selected 'en' documentation must be present");
        assertTrue(allHtml.contains("ROOT_DEFAULT_TEXT"), "untagged 'default' documentation must be kept");
        // Nothing German may survive anywhere in the HTML output.
        for (Path html : htmlFiles) {
            String content = Files.readString(html, StandardCharsets.UTF_8);
            for (String german : List.of("ROOT_GERMAN_TEXT", "ITEM_GERMAN_TEXT", "TYPE_GERMAN_TEXT",
                    "ATTR_GERMAN_TEXT", "SIMPLE_GERMAN_TEXT", "ENUM_GERMAN_TEXT")) {
                assertFalse(content.contains(german),
                        "de-selected 'de' documentation leaked: " + german + " in " + out.relativize(html));
            }
            assertFalse(content.contains("data-lang=\"de\""),
                    "no data-lang=\"de\" block may be rendered in " + out.relativize(html));
        }

        String searchIndex = Files.readString(out.resolve("search_index.json"), StandardCharsets.UTF_8);
        assertFalse(searchIndex.contains("GERMAN"), "search index must not contain de-selected languages");

        String languagesJson = Files.readString(out.resolve("languages.json"), StandardCharsets.UTF_8);
        assertTrue(languagesJson.contains("\"en\""), "languages.json must list the selected language");
        assertFalse(languagesJson.contains("\"de\""), "languages.json must not list de-selected languages");
    }

    @Test
    @DisplayName("Without a language selection every language is generated")
    void allLanguagesGeneratedWithoutSelection(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("langs.xsd");
        Files.writeString(xsd, XSD, StandardCharsets.UTF_8);
        Path out = tmp.resolve("out");

        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.toString());
        service.setMethod(XsdDocumentationService.ImageOutputMethod.SVG);
        service.generateXsdDocumentation(out.toFile());

        List<Path> htmlFiles;
        try (Stream<Path> files = Files.walk(out)) {
            htmlFiles = files.filter(p -> p.toString().endsWith(".html")).toList();
        }
        String allHtml = concat(htmlFiles);
        assertTrue(allHtml.contains("ROOT_ENGLISH_TEXT"));
        assertTrue(allHtml.contains("ROOT_GERMAN_TEXT"));
        assertTrue(allHtml.contains("TYPE_GERMAN_TEXT"));
        assertTrue(allHtml.contains("ATTR_GERMAN_TEXT"));
        assertTrue(allHtml.contains("SIMPLE_GERMAN_TEXT"));
        assertTrue(allHtml.contains("ENUM_GERMAN_TEXT"));
    }

    @Test
    @DisplayName("Type pages render the type's own documentation and the enumeration value docs")
    void typePagesRenderDocumentation(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("langs.xsd");
        Files.writeString(xsd, XSD, StandardCharsets.UTF_8);
        Path out = tmp.resolve("out");

        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.toString());
        service.setMethod(XsdDocumentationService.ImageOutputMethod.SVG);
        service.setIncludedLanguages(Set.of("en"));
        service.generateXsdDocumentation(out.toFile());

        String simpleTypePage = Files.readString(out.resolve("simpleTypes").resolve("CodeType.html"), StandardCharsets.UTF_8);
        assertTrue(simpleTypePage.contains("SIMPLE_ENGLISH_TEXT"), "simple type documentation must be rendered");
        assertTrue(simpleTypePage.contains("ENUM_ENGLISH_TEXT"), "enumeration value documentation must be rendered");
        assertFalse(simpleTypePage.contains("SIMPLE_GERMAN_TEXT"));
        assertFalse(simpleTypePage.contains("ENUM_GERMAN_TEXT"));

        String complexTypePage = Files.readString(out.resolve("complexTypes").resolve("ItemType.html"), StandardCharsets.UTF_8);
        assertTrue(complexTypePage.contains("TYPE_ENGLISH_TEXT"), "complex type documentation must be rendered");
        assertTrue(complexTypePage.contains("ATTR_ENGLISH_TEXT"), "attribute documentation must be rendered");
        assertFalse(complexTypePage.contains("TYPE_GERMAN_TEXT"));
        assertFalse(complexTypePage.contains("ATTR_GERMAN_TEXT"));
    }

    private static String concat(List<Path> files) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Path p : files) {
            sb.append(Files.readString(p, StandardCharsets.UTF_8)).append('\n');
        }
        return sb.toString();
    }
}
