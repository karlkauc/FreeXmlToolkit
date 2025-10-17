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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for XSD choice element handling in sample data generation.
 * Tests that choice elements are handled correctly with random selection
 * instead of generating all options.
 */
class XsdDocumentationServiceChoiceTest {

    private XsdDocumentationService service;
    private String xsdPath;

    @BeforeEach
    void setUp() {
        service = new XsdDocumentationService();
        xsdPath = new File("src/test/resources/choiceTest.xsd").getAbsolutePath();
        service.setXsdFilePath(xsdPath);
    }

    /**
     * Test that verifies choice elements generate only ONE of the options (not all).
     * The XSD has a choice with 4 payment methods, but only one should be generated.
     */
    @Test
    void testChoiceElementGeneratesOnlyOneOption() throws Exception {
        // Generate sample XML
        String sampleXml = service.generateSampleXml(false, 2);

        System.out.println("Generated sample XML:");
        System.out.println(sampleXml);

        // Verify the XML is not empty
        assertNotNull(sampleXml, "Generated XML should not be null");
        assertFalse(sampleXml.isEmpty(), "Generated XML should not be empty");

        // Count occurrences of each payment method
        int creditCardCount = countOccurrences(sampleXml, "<CreditCard>");
        int bankTransferCount = countOccurrences(sampleXml, "<BankTransfer>");
        int cashCount = countOccurrences(sampleXml, "<Cash>");
        int chequeCount = countOccurrences(sampleXml, "<Cheque>");

        int totalPaymentMethods = creditCardCount + bankTransferCount + cashCount + chequeCount;

        // Assert that exactly one payment method was generated
        assertEquals(1, totalPaymentMethods,
                "Should generate exactly ONE payment method from the choice, not all of them. " +
                        "Found: CreditCard=" + creditCardCount + ", BankTransfer=" + bankTransferCount +
                        ", Cash=" + cashCount + ", Cheque=" + chequeCount);

        // Verify at least one was chosen
        assertTrue(creditCardCount == 1 || bankTransferCount == 1 || cashCount == 1 || chequeCount == 1,
                "Exactly one payment method should be present");
    }

    /**
     * Test that verifies multiple runs generate different choices (randomness).
     * This tests that the selection is truly random and not always picking the same element.
     */
    @Test
    void testChoiceElementRandomSelection() throws Exception {
        Set<String> generatedPaymentMethods = new HashSet<>();

        // Generate sample XML multiple times
        for (int i = 0; i < 20; i++) {
            // Create a new service instance for each iteration to get fresh random choices
            XsdDocumentationService newService = new XsdDocumentationService();
            newService.setXsdFilePath(xsdPath);
            String sampleXml = newService.generateSampleXml(false, 2);

            // Extract which payment method was chosen
            if (sampleXml.contains("<CreditCard>")) generatedPaymentMethods.add("CreditCard");
            if (sampleXml.contains("<BankTransfer>")) generatedPaymentMethods.add("BankTransfer");
            if (sampleXml.contains("<Cash>")) generatedPaymentMethods.add("Cash");
            if (sampleXml.contains("<Cheque>")) generatedPaymentMethods.add("Cheque");
        }

        // With 20 iterations, we should see at least 2 different payment methods chosen
        // This verifies randomness (it's statistically very unlikely to get the same choice 20 times)
        assertTrue(generatedPaymentMethods.size() >= 2,
                "Should generate different payment methods across multiple runs (randomness check). " +
                        "Found only: " + generatedPaymentMethods);
    }

    /**
     * Test that verifies the generated XML is valid and well-formed.
     */
    @Test
    void testGeneratedXmlIsWellFormed() throws Exception {
        String sampleXml = service.generateSampleXml(false, 2);

        // Basic well-formedness checks
        assertTrue(sampleXml.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"),
                "XML should have XML declaration");
        assertTrue(sampleXml.contains("<Order"), "Should have Order root element");
        assertTrue(sampleXml.contains("</Order>"), "Should have closing Order tag");
        assertTrue(sampleXml.contains("<OrderID>"), "Should have OrderID element");
        assertTrue(sampleXml.contains("<Amount>"), "Should have Amount element");

        // Verify exactly one payment method
        int paymentMethodCount = 0;
        if (sampleXml.contains("<CreditCard>")) paymentMethodCount++;
        if (sampleXml.contains("<BankTransfer>")) paymentMethodCount++;
        if (sampleXml.contains("<Cash>")) paymentMethodCount++;
        if (sampleXml.contains("<Cheque>")) paymentMethodCount++;

        assertEquals(1, paymentMethodCount, "Should have exactly one payment method");
    }

    /**
     * Test that mandatory-only mode still respects choice cardinality.
     */
    @Test
    void testChoiceElementInMandatoryOnlyMode() throws Exception {
        // Generate sample XML in mandatory-only mode
        String sampleXml = service.generateSampleXml(true, 2);

        System.out.println("Generated sample XML (mandatory only):");
        System.out.println(sampleXml);

        // Count occurrences of each payment method
        int creditCardCount = countOccurrences(sampleXml, "<CreditCard>");
        int bankTransferCount = countOccurrences(sampleXml, "<BankTransfer>");
        int cashCount = countOccurrences(sampleXml, "<Cash>");
        int chequeCount = countOccurrences(sampleXml, "<Cheque>");

        int totalPaymentMethods = creditCardCount + bankTransferCount + cashCount + chequeCount;

        // Assert that exactly one payment method was generated (choice has minOccurs="1")
        assertEquals(1, totalPaymentMethods,
                "In mandatory-only mode, should still generate exactly ONE payment method from the choice. " +
                        "Found: CreditCard=" + creditCardCount + ", BankTransfer=" + bankTransferCount +
                        ", Cash=" + cashCount + ", Cheque=" + chequeCount);
    }

    /**
     * Test that verifies choice elements with multiple occurrences work correctly.
     * The XSD has a choice with minOccurs="2" maxOccurs="3", so it should generate 2-3 options.
     */
    @Test
    void testChoiceElementWithMultipleOccurrences() throws Exception {
        // Use a different XSD with multiple choice occurrences
        String multipleChoiceXsdPath = new File("src/test/resources/choiceTestMultiple.xsd").getAbsolutePath();
        XsdDocumentationService multipleService = new XsdDocumentationService();
        multipleService.setXsdFilePath(multipleChoiceXsdPath);

        // Generate sample XML
        String sampleXml = multipleService.generateSampleXml(false, 3);

        System.out.println("Generated sample XML with multiple choice occurrences:");
        System.out.println(sampleXml);

        // Count occurrences of each notification method
        int emailCount = countOccurrences(sampleXml, "<Email>");
        int smsCount = countOccurrences(sampleXml, "<SMS>");
        int pushCount = countOccurrences(sampleXml, "<PushNotification>");
        int phoneCount = countOccurrences(sampleXml, "<PhoneCall>");

        int totalNotificationMethods = emailCount + smsCount + pushCount + phoneCount;

        // Assert that 2-3 notification methods were generated (based on minOccurs="2" maxOccurs="3")
        assertTrue(totalNotificationMethods >= 2 && totalNotificationMethods <= 3,
                "Should generate 2-3 notification methods from the choice (minOccurs=2, maxOccurs=3). " +
                        "Found total: " + totalNotificationMethods +
                        " (Email=" + emailCount + ", SMS=" + smsCount +
                        ", Push=" + pushCount + ", Phone=" + phoneCount + ")");
    }

    /**
     * Test that verifies mandatory-only mode respects minOccurs for multiple choice occurrences.
     */
    @Test
    void testChoiceElementMultipleOccurrencesInMandatoryMode() throws Exception {
        String multipleChoiceXsdPath = new File("src/test/resources/choiceTestMultiple.xsd").getAbsolutePath();
        XsdDocumentationService multipleService = new XsdDocumentationService();
        multipleService.setXsdFilePath(multipleChoiceXsdPath);

        // Generate sample XML in mandatory-only mode
        String sampleXml = multipleService.generateSampleXml(true, 3);

        System.out.println("Generated sample XML with multiple choice occurrences (mandatory only):");
        System.out.println(sampleXml);

        // Count occurrences of each notification method
        int emailCount = countOccurrences(sampleXml, "<Email>");
        int smsCount = countOccurrences(sampleXml, "<SMS>");
        int pushCount = countOccurrences(sampleXml, "<PushNotification>");
        int phoneCount = countOccurrences(sampleXml, "<PhoneCall>");

        int totalNotificationMethods = emailCount + smsCount + pushCount + phoneCount;

        // In mandatory mode, should generate exactly minOccurs (2) notification methods
        assertEquals(2, totalNotificationMethods,
                "In mandatory-only mode, should generate exactly minOccurs (2) notification methods. " +
                        "Found total: " + totalNotificationMethods +
                        " (Email=" + emailCount + ", SMS=" + smsCount +
                        ", Push=" + pushCount + ", Phone=" + phoneCount + ")");
    }

    /**
     * Helper method to count occurrences of a substring in a string.
     */
    private int countOccurrences(String text, String substring) {
        int count = 0;
        Pattern pattern = Pattern.compile(Pattern.quote(substring));
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
