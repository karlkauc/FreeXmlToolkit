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

package org.fxt.freexmltoolkit.controls.v2.editor.services;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression tests for re-loading a schema into the same {@link MutableXmlSchemaProvider}.
 * <p>
 * The provider used to reuse one {@code XsdDocumentationService}, whose
 * {@code processXsd} mutates its single {@code XsdDocumentationData} in place — a
 * re-load (e.g. the schema reference in the document changed) modified the very element
 * map an FX-thread reader (tooltip lookup via {@code findBestMatchingElement}) was
 * iterating, throwing {@link java.util.ConcurrentModificationException}. Each load must
 * build a fresh data object and publish it atomically.
 */
class MutableXmlSchemaProviderReloadTest {

    @TempDir
    Path tempDir;

    private File writeXsd(String name, String rootElement) throws IOException {
        File file = tempDir.resolve(name).toFile();
        Files.writeString(file.toPath(), """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="%s">
                    <xs:complexType>
                      <xs:sequence>
                        <xs:element name="child" type="xs:string" minOccurs="0"/>
                      </xs:sequence>
                    </xs:complexType>
                  </xs:element>
                </xs:schema>
                """.formatted(rootElement));
        return file;
    }

    @Test
    @DisplayName("Re-loading publishes a fresh data object and leaves the old snapshot untouched")
    void reloadDoesNotMutateThePublishedSnapshot() throws Exception {
        MutableXmlSchemaProvider provider = new MutableXmlSchemaProvider();

        assertTrue(provider.loadSchema(writeXsd("a.xsd", "alpha")));
        XsdDocumentationData first = provider.getXsdDocumentationData();
        assertNotNull(first);
        Set<String> firstKeysSnapshot = Set.copyOf(first.getExtendedXsdElementMap().keySet());
        assertFalse(firstKeysSnapshot.isEmpty());

        assertTrue(provider.loadSchema(writeXsd("b.xsd", "beta")));
        XsdDocumentationData second = provider.getXsdDocumentationData();

        assertNotSame(first, second,
                "each load must publish a fresh XsdDocumentationData (in-place reuse caused"
                        + " ConcurrentModificationException in FX-thread readers)");
        assertEquals(firstKeysSnapshot, first.getExtendedXsdElementMap().keySet(),
                "the previously published snapshot must not be mutated by a re-load");
        assertTrue(second.getExtendedXsdElementMap().keySet().stream().anyMatch(k -> k.contains("beta")),
                "the new snapshot must reflect the re-loaded schema");

        // Old-snapshot lookups (what a tooltip in flight would do) must still work.
        assertNotNull(provider.findBestMatchingElement("/beta/child"));
    }
}
