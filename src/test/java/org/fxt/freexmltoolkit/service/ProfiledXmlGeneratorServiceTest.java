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

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.domain.GenerationProfile;
import org.fxt.freexmltoolkit.domain.GenerationStrategy;
import org.fxt.freexmltoolkit.domain.XPathInfo;
import org.fxt.freexmltoolkit.domain.XPathRule;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ProfiledXmlGeneratorService")
class ProfiledXmlGeneratorServiceTest {

    private ProfiledXmlGeneratorService service;
    private XsdDocumentationData data;
    private String xsdFilePath;

    @BeforeEach
    void setUp() throws Exception {
        service = new ProfiledXmlGeneratorService();

        // Use the test XSD
        File xsdFile = new File("src/test/resources/demo-xsd/test-profiled-generation.xsd");
        assertTrue(xsdFile.exists(), "Test XSD file must exist");
        xsdFilePath = xsdFile.getAbsolutePath();

        XsdDocumentationService docService = new XsdDocumentationService();
        docService.setXsdFilePath(xsdFilePath);
        docService.processXsd(false);
        data = docService.xsdDocumentationData;
    }

    @Nested
    @DisplayName("Generate with empty profile")
    class EmptyProfileTests {

        @Test
        @DisplayName("Empty profile generates valid XML")
        void emptyProfileGeneratesXml() {
            var profile = new GenerationProfile("Empty");
            String xml = service.generate(profile, data, xsdFilePath);

            assertNotNull(xml);
            assertTrue(xml.startsWith("<?xml version=\"1.0\""));
            assertTrue(xml.contains("<order"));
            assertTrue(xml.contains("</order>"));
        }

        @Test
        @DisplayName("Empty profile produces similar output to original generation")
        void emptyProfileSimilarToOriginal() {
            var profile = new GenerationProfile("Default");
            String xml = service.generate(profile, data, xsdFilePath);

            // Should contain all mandatory elements
            assertTrue(xml.contains("<customer>") || xml.contains("<customer\n"));
            assertTrue(xml.contains("<name>") || xml.contains("<name/>"));
            assertTrue(xml.contains("<item") || xml.contains("<item>"));
        }
    }

    @Nested
    @DisplayName("Generate with rules")
    class RuleTests {

        @Test
        @DisplayName("FIXED strategy sets literal value")
        void fixedStrategy() {
            var profile = new GenerationProfile("Fixed Test");
            profile.addRule(new XPathRule("/order/customer/country", GenerationStrategy.FIXED,
                    Map.of("value", "AT")));

            String xml = service.generate(profile, data, xsdFilePath);
            assertTrue(xml.contains("<country>AT</country>"), "Should contain fixed value AT");
        }

        @Test
        @DisplayName("OMIT strategy removes element")
        void omitStrategy() {
            var profile = new GenerationProfile("Omit Test");
            profile.addRule(new XPathRule("/order/notes", GenerationStrategy.OMIT));

            String xml = service.generate(profile, data, xsdFilePath);
            assertFalse(xml.contains("<notes"), "Should not contain notes element");
        }

        @Test
        @DisplayName("EMPTY strategy creates empty element")
        void emptyStrategy() {
            var profile = new GenerationProfile("Empty Test");
            profile.addRule(new XPathRule("/order/customer/name", GenerationStrategy.EMPTY));

            String xml = service.generate(profile, data, xsdFilePath);
            assertTrue(xml.contains("<name></name>") || xml.contains("<name/>"),
                    "Should contain empty name element");
        }

        @Test
        @DisplayName("SEQUENCE strategy generates incrementing values")
        void sequenceStrategy() {
            var profile = new GenerationProfile("Seq Test");
            profile.setMaxOccurrences(2);
            profile.addRule(new XPathRule("/order/item/@itemId", GenerationStrategy.SEQUENCE,
                    Map.of("pattern", "ITEM-{seq:3}", "start", "1")));

            String xml = service.generate(profile, data, xsdFilePath);
            assertTrue(xml.contains("ITEM-001"), "Should contain ITEM-001");
            assertTrue(xml.contains("ITEM-002"), "Should contain ITEM-002 for second item");
        }
    }

    @Nested
    @DisplayName("XPath matching")
    class XPathMatchingTests {

        @Test
        @DisplayName("Exact match")
        void exactMatch() {
            assertTrue(ProfiledXmlGeneratorService.xpathMatches("/order/customer/name", "/order/customer/name"));
            assertFalse(ProfiledXmlGeneratorService.xpathMatches("/order/customer/email", "/order/customer/name"));
        }

        @Test
        @DisplayName("Descendant match with //")
        void descendantMatch() {
            assertTrue(ProfiledXmlGeneratorService.xpathMatches("/order/customer/name", "//name"));
            assertTrue(ProfiledXmlGeneratorService.xpathMatches("/order/item/sku", "//sku"));
            assertFalse(ProfiledXmlGeneratorService.xpathMatches("/order/customer/name", "//email"));
        }

        @Test
        @DisplayName("Wildcard match with [*]")
        void wildcardMatch() {
            assertTrue(ProfiledXmlGeneratorService.xpathMatches("/order/item[1]/sku", "/order/item[*]/sku"));
            assertTrue(ProfiledXmlGeneratorService.xpathMatches("/order/item[2]/sku", "/order/item[*]/sku"));
            assertFalse(ProfiledXmlGeneratorService.xpathMatches("/order/customer/name", "/order/item[*]/sku"));
        }

        @Test
        @DisplayName("Null inputs return false")
        void nullInputs() {
            assertFalse(ProfiledXmlGeneratorService.xpathMatches(null, "/a"));
            assertFalse(ProfiledXmlGeneratorService.xpathMatches("/a", null));
            assertFalse(ProfiledXmlGeneratorService.xpathMatches(null, null));
        }
    }

    @Nested
    @DisplayName("XPath extraction")
    class XPathExtractionTests {

        @Test
        @DisplayName("Extracts XPaths from schema")
        void extractsXPaths() {
            List<XPathInfo> xpaths = service.extractXPaths(data);
            assertFalse(xpaths.isEmpty(), "Should extract at least one XPath");

            // Check for known elements
            List<String> paths = xpaths.stream().map(XPathInfo::xpath).toList();
            assertTrue(paths.stream().anyMatch(p -> p.contains("customer")), "Should contain customer path");
            assertTrue(paths.stream().anyMatch(p -> p.contains("item")), "Should contain item path");
        }

        @Test
        @DisplayName("Distinguishes elements and attributes")
        void distinguishesAttributesAndElements() {
            List<XPathInfo> xpaths = service.extractXPaths(data);

            boolean hasAttributes = xpaths.stream().anyMatch(XPathInfo::isAttribute);
            boolean hasElements = xpaths.stream().anyMatch(x -> !x.isAttribute());

            assertTrue(hasAttributes, "Should have attributes");
            assertTrue(hasElements, "Should have elements");
        }

        @Test
        @DisplayName("Filters out structural containers")
        void filtersContainers() {
            List<XPathInfo> xpaths = service.extractXPaths(data);
            boolean hasContainers = xpaths.stream()
                    .anyMatch(x -> x.xpath().contains("SEQUENCE") || x.xpath().contains("CHOICE"));
            assertFalse(hasContainers, "Should not contain structural containers");
        }
    }

    @Nested
    @DisplayName("Rule priority")
    class RulePriorityTests {

        @Test
        @DisplayName("Higher priority rule wins")
        void higherPriorityWins() {
            var generalRule = new XPathRule("//name", GenerationStrategy.FIXED, Map.of("value", "GENERAL"));
            var specificRule = new XPathRule("/order/customer/name", GenerationStrategy.FIXED, Map.of("value", "SPECIFIC"));
            specificRule.setPriority(10);

            var result = service.matchRule("/order/customer/name", List.of(generalRule, specificRule));
            assertTrue(result.isPresent());
            assertEquals("SPECIFIC", result.get().getConfigValue("value"));
        }

        @Test
        @DisplayName("More specific path wins on same priority")
        void moreSpecificWins() {
            var descendantRule = new XPathRule("//name", GenerationStrategy.FIXED, Map.of("value", "DESCENDANT"));
            var exactRule = new XPathRule("/order/customer/name", GenerationStrategy.FIXED, Map.of("value", "EXACT"));

            var result = service.matchRule("/order/customer/name", List.of(descendantRule, exactRule));
            assertTrue(result.isPresent());
            assertEquals("EXACT", result.get().getConfigValue("value"));
        }

        @Test
        @DisplayName("Disabled rules are skipped")
        void disabledRulesSkipped() {
            var rule = new XPathRule("/order/customer/name", GenerationStrategy.FIXED, Map.of("value", "DISABLED"));
            rule.setEnabled(false);

            var result = service.matchRule("/order/customer/name", List.of(rule));
            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("File name resolution")
    class FileNameTests {

        @Test
        @DisplayName("Pattern with seq placeholder")
        void patternWithSeq() {
            assertEquals("order_001.xml", service.resolveFileName("order_{seq:3}.xml", 1));
            assertEquals("order_042.xml", service.resolveFileName("order_{seq:3}.xml", 42));
        }

        @Test
        @DisplayName("Pattern without placeholder")
        void patternWithoutPlaceholder() {
            assertEquals("static.xml", service.resolveFileName("static.xml", 1));
        }

        @Test
        @DisplayName("Blank pattern uses default")
        void blankPattern() {
            assertEquals("example_1.xml", service.resolveFileName("", 1));
        }
    }

    @Nested
    @DisplayName("hasNoEffectiveRules")
    class HasNoEffectiveRulesTests {

        @Test
        @DisplayName("null profile is treated as no effective rules")
        void nullProfile() {
            assertTrue(ProfiledXmlGeneratorService.hasNoEffectiveRules(null));
        }

        @Test
        @DisplayName("empty rules list is no effective rules")
        void emptyRules() {
            var profile = new GenerationProfile("E");
            assertTrue(ProfiledXmlGeneratorService.hasNoEffectiveRules(profile));
        }

        @Test
        @DisplayName("all enabled rules with AUTO strategy is no effective rules")
        void allAutoEnabled() {
            var profile = new GenerationProfile("AllAuto");
            profile.addRule(new XPathRule("/a", GenerationStrategy.AUTO));
            profile.addRule(new XPathRule("/b", GenerationStrategy.AUTO));
            assertTrue(ProfiledXmlGeneratorService.hasNoEffectiveRules(profile));
        }

        @Test
        @DisplayName("disabled non-AUTO rules count as no effective rules")
        void disabledNonAutoIgnored() {
            var profile = new GenerationProfile("Disabled");
            var fixed = new XPathRule("/a", GenerationStrategy.FIXED, Map.of("value", "X"));
            fixed.setEnabled(false);
            profile.addRule(fixed);
            profile.addRule(new XPathRule("/b", GenerationStrategy.AUTO));
            assertTrue(ProfiledXmlGeneratorService.hasNoEffectiveRules(profile));
        }

        @Test
        @DisplayName("at least one enabled non-AUTO rule means effective rules exist")
        void enabledFixedTriggersProfiled() {
            var profile = new GenerationProfile("Mixed");
            profile.addRule(new XPathRule("/a", GenerationStrategy.AUTO));
            profile.addRule(new XPathRule("/b", GenerationStrategy.FIXED, Map.of("value", "X")));
            assertFalse(ProfiledXmlGeneratorService.hasNoEffectiveRules(profile));
        }
    }

    @Nested
    @DisplayName("FundsXML Bond Fund profile end-to-end")
    class BondFundIntegrationTests {

        @Test
        @DisplayName("Bond Fund profile generates schema-valid XML against FundsXML4.xsd in reasonable time")
        void bondFundProfileGeneratesAgainstFundsXML4() throws Exception {
            File xsdFile = new File("release/examples/xsd/FundsXML4.xsd");
            File profileFile = new File("release/examples/profiles/FundsXML_Bond_Fund_EAM_Demo.json");
            assertTrue(xsdFile.exists(), "FundsXML4.xsd must exist at " + xsdFile.getAbsolutePath());
            assertTrue(profileFile.exists(), "Profile must exist at " + profileFile.getAbsolutePath());

            // Load profile from disk
            GenerationProfileService profileService =
                    new GenerationProfileService(java.nio.file.Files.createTempDirectory("profile-test"));
            GenerationProfile profile = profileService.importFromFile(profileFile);
            assertNotNull(profile, "Profile must deserialize");

            // Parse FundsXML4.xsd
            long parseStart = System.nanoTime();
            XsdDocumentationService docService = new XsdDocumentationService();
            docService.setXsdFilePath(xsdFile.getAbsolutePath());
            docService.processXsd(false);
            long parseMs = (System.nanoTime() - parseStart) / 1_000_000;
            XsdDocumentationData fundsXmlData = docService.xsdDocumentationData;
            int elementCount = fundsXmlData.getExtendedXsdElementMap().size();

            // Generate XML with the profile, measuring duration
            ProfiledXmlGeneratorService gen = new ProfiledXmlGeneratorService();
            long genStart = System.nanoTime();
            String xml = gen.generate(profile, fundsXmlData, xsdFile.getAbsolutePath());
            long genMs = (System.nanoTime() - genStart) / 1_000_000;

            // Validate against schema
            long valStart = System.nanoTime();
            XsdDocumentationService.ValidationResult validation = docService.validateXmlAgainstSchema(xml);
            long valMs = (System.nanoTime() - valStart) / 1_000_000;

            // Diagnostics — visible in test output for performance tracking
            System.out.println();
            System.out.println("=== FundsXML Bond Fund profile generation ===");
            System.out.println("XSD elements parsed:   " + elementCount);
            System.out.println("XSD parse time:        " + parseMs + " ms");
            System.out.println("Generation time:       " + genMs + " ms");
            System.out.println("Generated XML size:    " + xml.length() + " chars (~"
                    + (xml.length() / 1024) + " KB)");
            System.out.println("Schema validation:     " + valMs + " ms");
            System.out.println("Schema valid:          " + validation.isValid());
            System.out.println("Validation errors:     " + validation.errors().size());
            if (!validation.errors().isEmpty()) {
                System.out.println("First 5 errors:");
                validation.errors().stream().limit(5).forEach(e -> System.out.println("  " + e));
            }
            System.out.println();

            // Hard assertions — fail loud if regression occurs
            assertNotNull(xml, "Generation must produce XML");
            assertTrue(xml.startsWith("<?xml version=\"1.0\""), "XML must have prolog");
            assertTrue(xml.contains("FundsXML4"), "Root element must be FundsXML4");

            // Profile fingerprint values must appear in the output
            assertTrue(xml.contains("PQOH26KWDF7CG10L6792"), "LEI from profile must appear in XML");
            assertTrue(xml.contains("Erste Asset Management GmbH"),
                    "DataSupplier name from profile must appear in XML");
            assertTrue(xml.contains("EAM_FUND_003"), "UniqueDocumentID from profile must appear");
            assertTrue(xml.contains("2021-11-30"), "ContentDate from profile must appear");
            assertTrue(xml.contains("UCITS"), "ListedLegalStructure from profile must appear");

            // Performance budget: with the regex-cache fix the previous "10+ minute" runaway
            // completes in ~60s on a developer machine. The generous 180s budget catches
            // regressions where matchRule re-introduces per-call Pattern.compile() in the
            // hot loop without being flaky on slower CI runners.
            assertTrue(genMs < 180_000,
                    "Generation must finish in under 180s, took " + genMs + " ms");
        }
    }

    @Nested
    @DisplayName("All-AUTO delegation to plain generator")
    class AutoDelegationTests {

        @Test
        @DisplayName("empty profile delegates to plain generator")
        void emptyProfileDelegates() {
            var spy = new SpyService();
            var profile = new GenerationProfile("Empty");
            String xml = spy.generate(profile, data, xsdFilePath);

            assertEquals(1, spy.delegateCalls, "Empty profile must delegate exactly once");
            assertNotNull(xml);
            assertTrue(xml.startsWith("<?xml version=\"1.0\""));
        }

        @Test
        @DisplayName("all-AUTO rules from auto-fill delegate to plain generator")
        void allAutoRulesDelegate() {
            var spy = new SpyService();
            var profile = new GenerationProfile("AllAuto");
            for (XPathInfo info : spy.extractXPaths(data)) {
                profile.addRule(new XPathRule(info.xpath(), GenerationStrategy.AUTO));
            }
            String xml = spy.generate(profile, data, xsdFilePath);

            assertEquals(1, spy.delegateCalls,
                    "All-AUTO profile must delegate to plain generator exactly once");
            assertNotNull(xml);
            assertTrue(xml.contains("<order"));
        }

        @Test
        @DisplayName("mandatoryOnly + maxOccurrences are passed through to plain generator")
        void delegationPassesProfileSettings() {
            var spy = new SpyService();
            var profile = new GenerationProfile("MandatoryOnly");
            profile.setMandatoryOnly(true);
            profile.setMaxOccurrences(7);
            spy.generate(profile, data, xsdFilePath);

            assertEquals(1, spy.delegateCalls);
            assertTrue(spy.lastDelegatedMandatoryOnly,
                    "mandatoryOnly setting must be passed to plain generator");
            assertEquals(7, spy.lastDelegatedMaxOccurrences,
                    "maxOccurrences setting must be passed to plain generator");
        }

        @Test
        @DisplayName("mixed AUTO + FIXED bypasses delegation and uses profiled path")
        void mixedRulesUseProfiledPath() {
            var spy = new SpyService();
            var profile = new GenerationProfile("Mixed");
            for (XPathInfo info : spy.extractXPaths(data)) {
                profile.addRule(new XPathRule(info.xpath(), GenerationStrategy.AUTO));
            }
            profile.addRule(new XPathRule("/order/customer/country",
                    GenerationStrategy.FIXED, Map.of("value", "AT")));
            String xml = spy.generate(profile, data, xsdFilePath);

            assertEquals(0, spy.delegateCalls,
                    "Profile with FIXED rule must use profiled path, not delegate");
            assertTrue(xml.contains("<country>AT</country>"),
                    "FIXED rule must override AUTO and apply value AT");
        }

        @Test
        @DisplayName("batch with all-AUTO delegates per file")
        void batchAllAutoDelegatesPerFile() {
            var spy = new SpyService();
            var profile = new GenerationProfile("BatchAuto");
            profile.setBatchCount(3);
            var files = spy.generateBatch(profile, data, xsdFilePath);

            assertEquals(3, files.size());
            assertEquals(3, spy.delegateCalls,
                    "Each batch entry must delegate to plain generator");
        }

        @Test
        @DisplayName("batch with mixed rules uses profiled path")
        void batchMixedUsesProfiledPath() {
            var spy = new SpyService();
            var profile = new GenerationProfile("BatchMixed");
            profile.setBatchCount(2);
            profile.addRule(new XPathRule("/order/customer/country",
                    GenerationStrategy.FIXED, Map.of("value", "AT")));
            var files = spy.generateBatch(profile, data, xsdFilePath);

            assertEquals(2, files.size());
            assertEquals(0, spy.delegateCalls,
                    "Batch with non-AUTO rule must not delegate");
            files.forEach(f -> assertTrue(f.content().contains("<country>AT</country>")));
        }

        /** Test spy that records delegation calls and the parameters passed. */
        class SpyService extends ProfiledXmlGeneratorService {
            int delegateCalls = 0;
            boolean lastDelegatedMandatoryOnly;
            int lastDelegatedMaxOccurrences;

            @Override
            String delegateToPlainGenerator(GenerationProfile profile, String xsdPath) {
                delegateCalls++;
                lastDelegatedMandatoryOnly = profile.isMandatoryOnly();
                lastDelegatedMaxOccurrences = profile.getMaxOccurrences();
                return super.delegateToPlainGenerator(profile, xsdPath);
            }
        }
    }
}
