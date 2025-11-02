/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
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

import org.fxt.freexmltoolkit.domain.IdentityConstraint;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Identity Constraints (xs:key, xs:keyref, xs:unique) visualization in SVG diagrams.
 */
public class XsdSvgIdentityConstraintsTest {

    private static final String TEST_XSD_WITH_IDENTITY_CONSTRAINTS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="http://example.com/test"
                       elementFormDefault="qualified">
            
                <xs:element name="Library">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="Book" maxOccurs="unbounded">
                                <xs:complexType>
                                    <xs:sequence>
                                        <xs:element name="ISBN" type="xs:string"/>
                                        <xs:element name="Title" type="xs:string"/>
                                        <xs:element name="Author" type="xs:string"/>
                                    </xs:sequence>
                                </xs:complexType>
                            </xs:element>
                            <xs:element name="Member" maxOccurs="unbounded">
                                <xs:complexType>
                                    <xs:sequence>
                                        <xs:element name="MemberID" type="xs:string"/>
                                        <xs:element name="Name" type="xs:string"/>
                                        <xs:element name="Email" type="xs:string"/>
                                    </xs:sequence>
                                </xs:complexType>
                            </xs:element>
                            <xs:element name="Loan" maxOccurs="unbounded">
                                <xs:complexType>
                                    <xs:sequence>
                                        <xs:element name="BookISBN" type="xs:string"/>
                                        <xs:element name="MemberID" type="xs:string"/>
                                        <xs:element name="LoanDate" type="xs:date"/>
                                    </xs:sequence>
                                </xs:complexType>
                            </xs:element>
                        </xs:sequence>
                    </xs:complexType>
            
                    <!-- Key constraint on Book/ISBN -->
                    <xs:key name="BookKey">
                        <xs:selector xpath="Book"/>
                        <xs:field xpath="ISBN"/>
                    </xs:key>
            
                    <!-- Unique constraint on Member/Email -->
                    <xs:unique name="UniqueEmail">
                        <xs:selector xpath="Member"/>
                        <xs:field xpath="Email"/>
                    </xs:unique>
            
                    <!-- Keyref from Loan/BookISBN to Book/ISBN -->
                    <xs:keyref name="LoanBookRef" refer="BookKey">
                        <xs:selector xpath="Loan"/>
                        <xs:field xpath="BookISBN"/>
                    </xs:keyref>
                </xs:element>
            </xs:schema>
            """;

    @Test
    void testKeyConstraintIsRecognized(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test-identity-constraints.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_IDENTITY_CONSTRAINTS);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        XsdDocumentationData data = service.xsdDocumentationData;

        // Assert: Verify Library element has identity constraints
        var libraryElement = data.getExtendedXsdElementMap().values().stream()
                .filter(elem -> "Library".equals(elem.getElementName()))
                .findFirst()
                .orElse(null);

        assertNotNull(libraryElement, "Library element should exist");
        assertNotNull(libraryElement.getIdentityConstraints(), "Identity constraints list should not be null");
        assertFalse(libraryElement.getIdentityConstraints().isEmpty(),
                "Library should have at least one identity constraint");

        // Verify we have Key, Unique, and KeyRef
        boolean hasKey = libraryElement.getIdentityConstraints().stream()
                .anyMatch(c -> c.getType() == IdentityConstraint.Type.KEY);
        boolean hasUnique = libraryElement.getIdentityConstraints().stream()
                .anyMatch(c -> c.getType() == IdentityConstraint.Type.UNIQUE);
        boolean hasKeyRef = libraryElement.getIdentityConstraints().stream()
                .anyMatch(c -> c.getType() == IdentityConstraint.Type.KEYREF);

        assertTrue(hasKey, "Library should have a KEY constraint");
        assertTrue(hasUnique, "Library should have a UNIQUE constraint");
        assertTrue(hasKeyRef, "Library should have a KEYREF constraint");
    }

    @Test
    void testKeyConstraintDetails(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test-key.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_IDENTITY_CONSTRAINTS);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        XsdDocumentationData data = service.xsdDocumentationData;

        // Assert: Verify Key constraint details
        var libraryElement = data.getExtendedXsdElementMap().values().stream()
                .filter(elem -> "Library".equals(elem.getElementName()))
                .findFirst()
                .orElse(null);

        assertNotNull(libraryElement, "Library element should exist");

        var keyConstraint = libraryElement.getIdentityConstraints().stream()
                .filter(c -> c.getType() == IdentityConstraint.Type.KEY)
                .findFirst()
                .orElse(null);

        assertNotNull(keyConstraint, "Key constraint should exist");
        assertEquals("BookKey", keyConstraint.getName(), "Key name should be 'BookKey'");
        assertEquals("Book", keyConstraint.getSelector(), "Selector should be 'Book'");
        assertNotNull(keyConstraint.getFields(), "Fields list should not be null");
        assertTrue(keyConstraint.getFields().contains("ISBN"), "Fields should contain 'ISBN'");
    }

    @Test
    void testKeyRefConstraintDetails(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test-keyref.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_IDENTITY_CONSTRAINTS);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        XsdDocumentationData data = service.xsdDocumentationData;

        // Assert: Verify KeyRef constraint details
        var libraryElement = data.getExtendedXsdElementMap().values().stream()
                .filter(elem -> "Library".equals(elem.getElementName()))
                .findFirst()
                .orElse(null);

        assertNotNull(libraryElement, "Library element should exist");

        var keyRefConstraint = libraryElement.getIdentityConstraints().stream()
                .filter(c -> c.getType() == IdentityConstraint.Type.KEYREF)
                .findFirst()
                .orElse(null);

        assertNotNull(keyRefConstraint, "KeyRef constraint should exist");
        assertEquals("LoanBookRef", keyRefConstraint.getName(), "KeyRef name should be 'LoanBookRef'");
        assertEquals("BookKey", keyRefConstraint.getRefer(), "Refer should be 'BookKey'");
        assertEquals("Loan", keyRefConstraint.getSelector(), "Selector should be 'Loan'");
    }

    @Test
    void testUniqueConstraintDetails(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test-unique.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_IDENTITY_CONSTRAINTS);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        XsdDocumentationData data = service.xsdDocumentationData;

        // Assert: Verify Unique constraint details
        var libraryElement = data.getExtendedXsdElementMap().values().stream()
                .filter(elem -> "Library".equals(elem.getElementName()))
                .findFirst()
                .orElse(null);

        assertNotNull(libraryElement, "Library element should exist");

        var uniqueConstraint = libraryElement.getIdentityConstraints().stream()
                .filter(c -> c.getType() == IdentityConstraint.Type.UNIQUE)
                .findFirst()
                .orElse(null);

        assertNotNull(uniqueConstraint, "Unique constraint should exist");
        assertEquals("UniqueEmail", uniqueConstraint.getName(), "Unique name should be 'UniqueEmail'");
        assertEquals("Member", uniqueConstraint.getSelector(), "Selector should be 'Member'");
        assertTrue(uniqueConstraint.getFields().contains("Email"), "Fields should contain 'Email'");
    }
}
