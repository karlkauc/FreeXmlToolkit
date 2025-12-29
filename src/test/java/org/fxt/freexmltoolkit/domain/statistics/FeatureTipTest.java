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

package org.fxt.freexmltoolkit.domain.statistics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FeatureTip")
class FeatureTipTest {

    @Nested
    @DisplayName("Constructors")
    class ConstructorTests {

        @Test
        @DisplayName("Default constructor creates empty tip")
        void defaultConstructorCreatesEmptyTip() {
            FeatureTip tip = new FeatureTip();

            assertNull(tip.getFeatureId());
            assertNull(tip.getTipMessage());
            assertNull(tip.getActionLink());
            assertEquals(0, tip.getPriority());
            assertNull(tip.getIconLiteral());
        }

        @Test
        @DisplayName("Constructor with 4 parameters sets values")
        void constructorWith4Parameters() {
            FeatureTip tip = new FeatureTip(
                    "xsd-documentation",
                    "Generate XSD documentation in HTML or PDF format",
                    "xsd",
                    1
            );

            assertEquals("xsd-documentation", tip.getFeatureId());
            assertEquals("Generate XSD documentation in HTML or PDF format", tip.getTipMessage());
            assertEquals("xsd", tip.getActionLink());
            assertEquals(1, tip.getPriority());
            assertNull(tip.getIconLiteral());
        }

        @Test
        @DisplayName("Constructor with 5 parameters sets all values")
        void constructorWith5Parameters() {
            FeatureTip tip = new FeatureTip(
                    "schematron-validation",
                    "Use Schematron for business rule validation",
                    "schematron",
                    2,
                    "bi-shield-check"
            );

            assertEquals("schematron-validation", tip.getFeatureId());
            assertEquals("Use Schematron for business rule validation", tip.getTipMessage());
            assertEquals("schematron", tip.getActionLink());
            assertEquals(2, tip.getPriority());
            assertEquals("bi-shield-check", tip.getIconLiteral());
        }
    }

    @Nested
    @DisplayName("Getters and Setters")
    class GettersSettersTests {

        @Test
        @DisplayName("Set and get feature ID")
        void setAndGetFeatureId() {
            FeatureTip tip = new FeatureTip();
            tip.setFeatureId("xml-signature");

            assertEquals("xml-signature", tip.getFeatureId());
        }

        @Test
        @DisplayName("Set and get tip message")
        void setAndGetTipMessage() {
            FeatureTip tip = new FeatureTip();
            tip.setTipMessage("Sign your XML documents digitally");

            assertEquals("Sign your XML documents digitally", tip.getTipMessage());
        }

        @Test
        @DisplayName("Set and get action link")
        void setAndGetActionLink() {
            FeatureTip tip = new FeatureTip();
            tip.setActionLink("signature");

            assertEquals("signature", tip.getActionLink());
        }

        @Test
        @DisplayName("Set and get priority")
        void setAndGetPriority() {
            FeatureTip tip = new FeatureTip();
            tip.setPriority(5);

            assertEquals(5, tip.getPriority());
        }

        @Test
        @DisplayName("Set and get icon literal")
        void setAndGetIconLiteral() {
            FeatureTip tip = new FeatureTip();
            tip.setIconLiteral("bi-file-earmark-code");

            assertEquals("bi-file-earmark-code", tip.getIconLiteral());
        }
    }

    @Nested
    @DisplayName("Priority")
    class PriorityTests {

        @Test
        @DisplayName("Default priority is 0")
        void defaultPriorityIsZero() {
            FeatureTip tip = new FeatureTip();
            assertEquals(0, tip.getPriority());
        }

        @Test
        @DisplayName("Can set negative priority")
        void canSetNegativePriority() {
            FeatureTip tip = new FeatureTip();
            tip.setPriority(-1);
            assertEquals(-1, tip.getPriority());
        }

        @Test
        @DisplayName("Can set high priority")
        void canSetHighPriority() {
            FeatureTip tip = new FeatureTip();
            tip.setPriority(100);
            assertEquals(100, tip.getPriority());
        }
    }

    @Nested
    @DisplayName("Real World Examples")
    class RealWorldTests {

        @Test
        @DisplayName("Create XSD documentation tip")
        void createXsdDocumentationTip() {
            FeatureTip tip = new FeatureTip(
                    "xsd-doc",
                    "Try generating documentation for your XSD schema",
                    "xsd",
                    1,
                    "bi-file-earmark-richtext"
            );

            assertEquals("xsd-doc", tip.getFeatureId());
            assertEquals("xsd", tip.getActionLink());
            assertEquals(1, tip.getPriority());
        }

        @Test
        @DisplayName("Create XSLT transformation tip")
        void createXsltTransformationTip() {
            FeatureTip tip = new FeatureTip(
                    "xslt-transform",
                    "Transform XML files using XSLT stylesheets",
                    "xslt",
                    2,
                    "bi-arrow-repeat"
            );

            assertEquals("xslt-transform", tip.getFeatureId());
            assertEquals("Transform XML files using XSLT stylesheets", tip.getTipMessage());
        }

        @Test
        @DisplayName("Create PDF generation tip")
        void createPdfGenerationTip() {
            FeatureTip tip = new FeatureTip(
                    "pdf-generation",
                    "Generate PDF documents from XML using XSL-FO",
                    "fop",
                    3,
                    "bi-file-earmark-pdf"
            );

            assertEquals("pdf-generation", tip.getFeatureId());
            assertEquals("fop", tip.getActionLink());
            assertEquals("bi-file-earmark-pdf", tip.getIconLiteral());
        }

        @Test
        @DisplayName("Tips can be sorted by priority")
        void tipsCanBeSortedByPriority() {
            FeatureTip lowPriority = new FeatureTip("low", "Low", "page", 1);
            FeatureTip highPriority = new FeatureTip("high", "High", "page", 10);
            FeatureTip mediumPriority = new FeatureTip("medium", "Medium", "page", 5);

            assertTrue(highPriority.getPriority() > mediumPriority.getPriority());
            assertTrue(mediumPriority.getPriority() > lowPriority.getPriority());
        }
    }
}
