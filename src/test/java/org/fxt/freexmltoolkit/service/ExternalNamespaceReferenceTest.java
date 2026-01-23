package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that external namespace references (like ds:Signature from xmldsig) are properly handled
 * in sample XML generation for schemas without a targetNamespace.
 */
class ExternalNamespaceReferenceTest {

    @Test
    void testExternalNamespaceReferenceInNoTargetNamespaceSchema() throws Exception {
        // Get the test schema from resources
        URL schemaUrl = getClass().getClassLoader().getResource("schema/FundsXML4.xsd");
        assertNotNull(schemaUrl, "FundsXML4.xsd should be available in test resources");

        File schemaFile = new File(schemaUrl.toURI());
        assertTrue(schemaFile.exists(), "Schema file should exist");

        // Generate sample XML
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(schemaFile.getAbsolutePath());

        String sampleXml = service.generateSampleXml(false, 1); // Include optional elements

        assertNotNull(sampleXml, "Sample XML should not be null");
        assertFalse(sampleXml.isBlank(), "Sample XML should not be empty");

        // Check that ds:Signature namespace is declared
        assertTrue(sampleXml.contains("xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\""),
                "Sample XML should declare the ds namespace for xmldsig");

        // The root-level Signature from ds namespace should be prefixed
        // Check if the element after </CountrySpecificData> is properly prefixed
        int countrySpecificEnd = sampleXml.indexOf("</CountrySpecificData>");
        if (countrySpecificEnd > 0) {
            String afterCountrySpecific = sampleXml.substring(countrySpecificEnd,
                    Math.min(sampleXml.length(), countrySpecificEnd + 200));

            // The ds:Signature should appear here if included
            if (afterCountrySpecific.contains("<Signature")) {
                assertTrue(afterCountrySpecific.contains("<ds:Signature"),
                        "The Signature element from xmldsig namespace should be prefixed with 'ds:'");
            }
        }

        // Verify that ds:Signature element is actually present and has child elements
        assertTrue(sampleXml.contains("<ds:Signature"),
                "The ds:Signature element should be generated in the sample XML when mandatoryOnly=false");
        assertFalse(sampleXml.contains("<ds:Signature/>"),
                "The ds:Signature element should not be self-closing when its content is generated");
        assertTrue(sampleXml.contains("</ds:Signature>"),
                "The ds:Signature element should have a closing tag when content is generated");
        assertTrue(sampleXml.contains("<ds:SignedInfo"),
                "The ds:Signature element should contain required child elements like ds:SignedInfo");

        // Validate against schema - must be valid
        XsdDocumentationService.ValidationResult result = service.validateXmlAgainstSchema(sampleXml);
        assertTrue(result.isValid(),
                "Generated XML should validate against FundsXML4.xsd. Error: "
                        + result.message() + " Errors: " + result.errors());
    }

    @Test
    void testMandatoryOnlyExcludesOptionalSignature() throws Exception {
        // Get the test schema from resources
        URL schemaUrl = getClass().getClassLoader().getResource("schema/FundsXML4.xsd");
        assertNotNull(schemaUrl, "FundsXML4.xsd should be available in test resources");

        File schemaFile = new File(schemaUrl.toURI());

        // Generate sample XML with mandatoryOnly=true
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(schemaFile.getAbsolutePath());

        String sampleXml = service.generateSampleXml(true, 1); // Only mandatory elements

        assertNotNull(sampleXml, "Sample XML should not be null");
        assertFalse(sampleXml.isBlank(), "Sample XML should not be empty");

        // ds:Signature has minOccurs="0", so with mandatoryOnly=true, it should NOT appear
        assertFalse(sampleXml.contains("ds:Signature"),
                "With mandatoryOnly=true, optional ds:Signature should not be included");

        // Validate against schema (should pass since we're only including mandatory elements)
        XsdDocumentationService.ValidationResult result = service.validateXmlAgainstSchema(sampleXml);

        assertTrue(result.isValid(),
                "Generated XML with mandatory elements only should validate. Error: " + result.message());
    }
}
