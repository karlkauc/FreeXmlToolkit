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

package org.fxt.freexmltoolkit.service.fundsxml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlExampleFilter.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("FundsXmlExampleFilter")
class FundsXmlExampleFilterTest {

    @TempDir
    Path root;

    private Path write(String relative, String content) throws IOException {
        Path p = root.resolve(relative);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
        return p;
    }

    private Category classify(String relative, String content) throws IOException {
        return FundsXmlExampleFilter.classify(root, write(relative, content));
    }

    @Test
    @DisplayName("FundsXML documents are examples, wherever they are")
    void fundsXmlDocuments() throws IOException {
        assertEquals(Category.EXAMPLE_XML, classify("FundsXML_Files/4.2.9/positions/Fund.xml",
                "<?xml version=\"1.0\"?>\n<!-- sample -->\n<FundsXML4 xmlns:xsi=\"x\"><ControlData/></FundsXML4>"));
        assertEquals(Category.EXAMPLE_XML, classify("tests/fixtures/invalid/xsd-invalid.xml", "<FundsXML4/>"));
        assertEquals(Category.EXAMPLE_XML, classify("Old/Fund.xml", "<FundsXML/>"));
    }

    @Test
    @DisplayName("other XML files are not examples")
    void otherXml() throws IOException {
        assertEquals(Category.NONE, classify("pom.xml", "<project/>"));
        assertEquals(Category.NONE, classify("config/log4j2.xml", "<Configuration/>"));
        assertEquals(Category.NONE, classify("broken.xml", "<FundsXML4"));
        assertEquals(Category.NONE, classify("empty.xml", ""));
    }

    @Test
    @DisplayName("a DOCTYPE is not resolved")
    void doctypeNotResolved() throws IOException {
        Path secret = write("secret.txt", "secret");
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE FundsXML4 [<!ENTITY x SYSTEM \""
                + secret.toUri() + "\">]><FundsXML4>&x;</FundsXML4>";
        // DTD support is off and reading stops at the root element: the entity is never expanded.
        assertEquals(Category.EXAMPLE_XML, classify("withDoctype.xml", xml));
    }

    @Test
    @DisplayName("program code, hidden and intermediate folders are skipped")
    void skippedFolders() throws IOException {
        String fundsXml = "<FundsXML4/>";
        assertEquals(Category.NONE, classify("XSD_Validation/java/pom.xml", "<project/>"));
        assertEquals(Category.NONE, classify("XQuery_Examples/invocation/pom.xml", "<project/>"));
        assertEquals(Category.NONE, classify("XML_Signature/dotnet/sample.xml", fundsXml));
        assertEquals(Category.NONE, classify("Export/.DAV/tpt_export.xslt", "<xsl:stylesheet/>"));
        assertEquals(Category.NONE, classify("Export/work/fundsxml/bank_01/a.xml", fundsXml));
        assertEquals(Category.NONE, classify("Export/enriched/a.xml", fundsXml));
        assertEquals(Category.NONE, classify(".github/workflows/ci.yml", "x"));
    }

    @Test
    @DisplayName("stylesheets, Schematron and queries are kept by extension")
    void byExtension() throws IOException {
        assertEquals(Category.XSLT, classify("XSLT_Transformations/csv.xslt", "<xsl:stylesheet/>"));
        assertEquals(Category.XSLT, classify("XSLT_DataQuality_Checks/report.xsl", "<xsl:stylesheet/>"));
        assertEquals(Category.SCHEMATRON, classify("Schematron/basic_checks.sch", "<schema/>"));
        assertEquals(Category.QUERY, classify("XQuery_Examples/top-holdings.xq", "1"));
        assertEquals(Category.QUERY, classify("queries/funds.xpath", "//Fund"));
        assertEquals(Category.QUERY, classify("queries/index.json", "[]"));
        assertEquals(Category.NONE, classify("Database_Integration/README.md", "# x"));
        assertEquals(Category.NONE, classify("tools/convert.py", "print()"));
    }

    @Test
    @DisplayName("isSkippedPath accepts both separators")
    void separators() {
        assertTrue(FundsXmlExampleFilter.isSkippedPath("XSD_Validation\\java\\pom.xml"));
        assertTrue(FundsXmlExampleFilter.isSkippedPath("pom.xml"));
        assertFalse(FundsXmlExampleFilter.isSkippedPath("FundsXML_Files/4.2.9/positions/Fund.xml"));
        assertFalse(FundsXmlExampleFilter.isSkippedPath("tests/fixtures/invalid/a.xml"));
    }
}
