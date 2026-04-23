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
    @DisplayName("Seeded generator produces reproducible CHOICE selections")
    class SeededGenerationTests {

        @Test
        @DisplayName("Two services with the same seed pick the same CHOICE option")
        void sameSeedSameChoice() throws Exception {
            File xsdFile = new File("src/test/resources/choiceTest.xsd");
            assertTrue(xsdFile.exists());
            String path = xsdFile.getAbsolutePath();
            XsdDocumentationService docService = new XsdDocumentationService();
            docService.setXsdFilePath(path);
            docService.processXsd(false);
            XsdDocumentationData localData = docService.xsdDocumentationData;

            // With all-AUTO rules the profiled generator delegates to plain, so force
            // a non-AUTO rule to keep the profiled CHOICE path active.
            var profile = new GenerationProfile("SeededChoice");
            profile.addRule(new XPathRule("/Order/OrderID", GenerationStrategy.FIXED,
                    Map.of("value", "ORDER-1")));

            var a = new ProfiledXmlGeneratorService(42L);
            var b = new ProfiledXmlGeneratorService(42L);
            String xmlA = a.generate(profile, localData, path);
            String xmlB = b.generate(profile, localData, path);

            // Extract which CHOICE option each one picked — must match.
            String optA = extractChoiceOption(xmlA);
            String optB = extractChoiceOption(xmlB);
            assertEquals(optA, optB,
                    "Same seed must produce the same CHOICE pick; got " + optA + " vs " + optB);
        }

        private String extractChoiceOption(String xml) {
            for (String opt : new String[]{"CreditCard", "BankTransfer", "Cash", "Cheque"}) {
                if (xml.contains("<" + opt + ">")) return opt;
            }
            return "<none>";
        }
    }

    @Nested
    @DisplayName("Per-rule maxOccurrences override")
    class MaxOccurrencesOverrideTests {

        @Test
        @DisplayName("Rule config maxOccurrences overrides profile global for matched xpath")
        void overrideAppliedToMatchedElement() {
            var profile = new GenerationProfile("ItemOverride");
            profile.setMaxOccurrences(1);
            profile.addRule(new XPathRule("/order/item", GenerationStrategy.AUTO,
                    Map.of("maxOccurrences", "3")));

            String xml = service.generate(profile, data, xsdFilePath);
            long itemCount = xml.lines().filter(l -> l.contains("<item")).count();
            assertEquals(3, itemCount,
                    "Rule override should force three <item> instances; XML was:\n" + xml);
        }

        @Test
        @DisplayName("Override only affects matched xpath, other elements keep profile default")
        void overrideIsScoped() {
            var profile = new GenerationProfile("ScopedOverride");
            profile.setMaxOccurrences(1);
            profile.addRule(new XPathRule("/order/item", GenerationStrategy.AUTO,
                    Map.of("maxOccurrences", "4")));

            String xml = service.generate(profile, data, xsdFilePath);
            long itemCount = xml.lines().filter(l -> l.contains("<item")).count();
            long customerCount = xml.lines().filter(l -> l.contains("<customer>")).count();
            assertEquals(4, itemCount, "item overridden to 4");
            assertEquals(1, customerCount, "customer keeps profile default of 1");
        }

        @Test
        @DisplayName("findRepeatOverride returns -1 when no rule matches")
        void noMatchingRuleReturnsNoOverride() {
            var rules = List.of(
                    new XPathRule("/a/b", GenerationStrategy.AUTO, Map.of("maxOccurrences", "5")));
            assertEquals(-1, ProfiledXmlGeneratorService.findRepeatOverride("/x/y", rules));
        }

        @Test
        @DisplayName("findRepeatOverride ignores disabled rules")
        void disabledRuleIgnored() {
            var disabled = new XPathRule("/a/b", GenerationStrategy.AUTO,
                    Map.of("maxOccurrences", "5"));
            disabled.setEnabled(false);
            assertEquals(-1, ProfiledXmlGeneratorService.findRepeatOverride("/a/b", List.of(disabled)));
        }

        @Test
        @DisplayName("findRepeatOverride ignores non-numeric and negative values")
        void invalidOverrideIgnored() {
            var bad = new XPathRule("/a/b", GenerationStrategy.AUTO,
                    Map.of("maxOccurrences", "not-a-number"));
            var negative = new XPathRule("/a/b", GenerationStrategy.AUTO,
                    Map.of("maxOccurrences", "-3"));
            assertEquals(-1, ProfiledXmlGeneratorService.findRepeatOverride("/a/b", List.of(bad)));
            assertEquals(-1, ProfiledXmlGeneratorService.findRepeatOverride("/a/b", List.of(negative)));
        }

        @Test
        @DisplayName("Override works with // descendant pattern")
        void overrideWithDescendantPattern() {
            var profile = new GenerationProfile("DescendantOverride");
            profile.setMaxOccurrences(1);
            profile.addRule(new XPathRule("//item", GenerationStrategy.AUTO,
                    Map.of("maxOccurrences", "2")));

            String xml = service.generate(profile, data, xsdFilePath);
            long itemCount = xml.lines().filter(l -> l.contains("<item")).count();
            assertEquals(2, itemCount, "Descendant rule //item should repeat items twice");
        }
    }

    @Nested
    @DisplayName("CHOICE selection via rule preference")
    class ChoiceSelectionTests {

        private ProfiledXmlGeneratorService choiceService;
        private XsdDocumentationData choiceData;
        private String choiceXsdPath;

        @BeforeEach
        void setUpChoice() throws Exception {
            choiceService = new ProfiledXmlGeneratorService();
            File xsdFile = new File("src/test/resources/choiceTest.xsd");
            assertTrue(xsdFile.exists(), "choiceTest.xsd must exist");
            choiceXsdPath = xsdFile.getAbsolutePath();
            XsdDocumentationService docService = new XsdDocumentationService();
            docService.setXsdFilePath(choiceXsdPath);
            docService.processXsd(false);
            choiceData = docService.xsdDocumentationData;
        }

        @Test
        @DisplayName("FIXED rule under one CHOICE option steers selection to that option")
        void fixedRuleStearsChoiceSelection() {
            var profile = new GenerationProfile("SteeredChoice");
            profile.addRule(new XPathRule("/Order/BankTransfer",
                    GenerationStrategy.FIXED, Map.of("value", "DE89...")));

            // Run multiple times — with random fallback we'd sometimes pick other options;
            // with preference the rule-targeted option must be chosen every time.
            for (int i = 0; i < 20; i++) {
                String xml = choiceService.generate(profile, choiceData, choiceXsdPath);
                assertTrue(xml.contains("<BankTransfer>DE89...</BankTransfer>"),
                        "Iteration " + i + " must select BankTransfer, got: " + xml);
                assertFalse(xml.contains("<CreditCard>"), "CreditCard must not appear");
                assertFalse(xml.contains("<Cash>"), "Cash must not appear");
                assertFalse(xml.contains("<Cheque>"), "Cheque must not appear");
            }
        }

        @Test
        @DisplayName("Descendant rule //name/field also steers CHOICE selection")
        void descendantRuleStearsChoice() {
            var profile = new GenerationProfile("DescendantChoice");
            profile.addRule(new XPathRule("//CreditCard",
                    GenerationStrategy.FIXED, Map.of("value", "4111-1111-1111-1111")));

            for (int i = 0; i < 20; i++) {
                String xml = choiceService.generate(profile, choiceData, choiceXsdPath);
                assertTrue(xml.contains("<CreditCard>4111-1111-1111-1111</CreditCard>"),
                        "Descendant rule //CreditCard must force CreditCard selection");
                assertFalse(xml.contains("<BankTransfer>"));
            }
        }

        @Test
        @DisplayName("No rule means random selection across all options")
        void noRuleMeansRandom() {
            var profile = new GenerationProfile("RandomChoice");
            java.util.Set<String> seenOptions = new java.util.HashSet<>();
            for (int i = 0; i < 40; i++) {
                String xml = choiceService.generate(profile, choiceData, choiceXsdPath);
                if (xml.contains("<CreditCard>")) seenOptions.add("CreditCard");
                if (xml.contains("<BankTransfer>")) seenOptions.add("BankTransfer");
                if (xml.contains("<Cash>")) seenOptions.add("Cash");
                if (xml.contains("<Cheque>")) seenOptions.add("Cheque");
            }
            assertTrue(seenOptions.size() >= 2,
                    "Without rule preference, multiple options should appear across runs; saw " + seenOptions);
        }

        @Test
        @DisplayName("Optional CHOICE under mandatoryOnly is still rendered when a rule targets one option")
        void optionalChoiceBumpedByRulePreference() throws Exception {
            // Uses a dedicated fixture where the CHOICE has minOccurs=0. Without the
            // rule-preference bump in the CHOICE handler, mandatoryOnly=true would
            // skip the CHOICE entirely and the user's rule would silently have no
            // effect. The fix bumps repeat=0 to 1 when any option is rule-targeted.
            File xsdFile = new File("src/test/resources/optionalChoiceTest.xsd");
            assertTrue(xsdFile.exists(), "optionalChoiceTest.xsd must exist");
            XsdDocumentationService docService = new XsdDocumentationService();
            docService.setXsdFilePath(xsdFile.getAbsolutePath());
            docService.processXsd(false);
            XsdDocumentationData data = docService.xsdDocumentationData;

            var profile = new GenerationProfile("OptionalChoiceBump");
            profile.setMandatoryOnly(true);
            profile.addRule(new XPathRule("/Order/BankTransfer",
                    GenerationStrategy.FIXED, Map.of("value", "DE89...")));

            var service = new ProfiledXmlGeneratorService();
            String xml = service.generate(profile, data, xsdFile.getAbsolutePath());

            assertTrue(xml.contains("<BankTransfer>DE89...</BankTransfer>"),
                    "Optional CHOICE must render because rule targets BankTransfer, got: " + xml);
            assertFalse(xml.contains("<CreditCard>"),
                    "CreditCard must not appear — rule only targets BankTransfer");
        }

        @Test
        @DisplayName("Optional CHOICE without rules stays skipped under mandatoryOnly")
        void optionalChoiceStaysSkippedWithoutRules() throws Exception {
            // Inverse of the bump test — without any rule on a CHOICE option the
            // default mandatoryOnly behaviour (skip optional CHOICE) must be kept.
            File xsdFile = new File("src/test/resources/optionalChoiceTest.xsd");
            XsdDocumentationService docService = new XsdDocumentationService();
            docService.setXsdFilePath(xsdFile.getAbsolutePath());
            docService.processXsd(false);
            XsdDocumentationData data = docService.xsdDocumentationData;

            var profile = new GenerationProfile("OptionalChoiceNoRules");
            profile.setMandatoryOnly(true);
            // Deliberately empty rules — ensure CHOICE stays out.

            var service = new ProfiledXmlGeneratorService();
            // Empty profile delegates to the plain generator, which under mandatoryOnly
            // also skips the optional CHOICE. Add a non-structural FIXED rule OUTSIDE
            // the CHOICE so the profiled path is actually active.
            profile.addRule(new XPathRule("/Order/OrderID",
                    GenerationStrategy.FIXED, Map.of("value", "ORD-001")));
            String xml = service.generate(profile, data, xsdFile.getAbsolutePath());

            assertTrue(xml.contains("<OrderID>ORD-001</OrderID>"),
                    "Sanity: OrderID FIXED rule must apply, got: " + xml);
            assertFalse(xml.contains("<CreditCard>"),
                    "Optional CHOICE without rule preference must stay skipped");
            assertFalse(xml.contains("<BankTransfer>"),
                    "Optional CHOICE without rule preference must stay skipped");
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
            if (xml.length() < 5000) {
                System.out.println("--- Generated XML ---");
                System.out.println(xml);
            }
            if (!validation.errors().isEmpty()) {
                java.util.Map<String, Long> errorCategories = validation.errors().stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                e -> {
                                    String normalized = e.message().replaceAll("'[^']*'", "'X'");
                                    return normalized.length() > 100 ? normalized.substring(0, 100) : normalized;
                                },
                                java.util.stream.Collectors.counting()));
                System.out.println("Error categories (top 15):");
                errorCategories.entrySet().stream()
                        .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(15)
                        .forEach(e -> System.out.println(String.format("  %5d × %s", e.getValue(), e.getKey())));
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
            assertTrue(xml.contains("DEMO BOND FUND"), "Fund name from profile must appear");

            // Portfolio / Position / AssetMasterData must be generated and cross-linked
            assertTrue(xml.contains("<Portfolio>"), "Portfolio must be generated");
            assertTrue(xml.contains("<Position>"), "Position must be generated");
            assertTrue(xml.contains("<AssetMasterData>"), "AssetMasterData must be generated");
            assertTrue(xml.contains("<Asset>"), "Asset must be generated");
            assertTrue(xml.contains("ASSET_BOND_001"),
                    "Position/UniqueID IDREF must resolve to Asset/UniqueID ID");
            assertTrue(xml.contains("DEMO BOND 2030"), "Asset name must appear");
            assertTrue(xml.contains("<AssetType>BO</AssetType>"), "Bond AssetType must appear");
            assertTrue(xml.contains("<Bond>"),
                    "Rule-driven CHOICE must select Bond subtype for Position");
            assertTrue(xml.contains("<Nominal>1000000</Nominal>"),
                    "Bond/Nominal FIXED value must appear");

            // maxOccurrences override: profile requests 3 Positions and 3 Assets
            long positionCount = xml.lines().filter(l -> l.contains("<Position>")).count();
            long assetCount = xml.lines().filter(l -> l.contains("<Asset>")).count();
            assertEquals(3, positionCount,
                    "Per-rule maxOccurrences override must produce 3 Positions");
            assertEquals(3, assetCount,
                    "Per-rule maxOccurrences override must produce 3 Assets");

            // SEQUENCE strategy yields unique but coordinated IDs for IDREF/ID linking
            assertTrue(xml.contains("ASSET_BOND_002"), "Second sequential ID must appear");
            assertTrue(xml.contains("ASSET_BOND_003"), "Third sequential ID must appear");

            // Amount values come from AUTO (fresh per iteration) — three positions should
            // have at least two distinct amounts. If all were identical, the generator
            // was using cached displaySampleData instead of resolving per call.
            java.util.regex.Matcher amountMatcher = java.util.regex.Pattern
                    .compile("<Amount[^>]*>([^<]+)</Amount>").matcher(xml);
            java.util.Set<String> amounts = new java.util.HashSet<>();
            while (amountMatcher.find()) {
                amounts.add(amountMatcher.group(1));
            }
            assertTrue(amounts.size() >= 2,
                    "AUTO-valued Amount must vary across the three positions; saw " + amounts);

            // Schema validity is the whole point of the profile after Phase-2 fixes.
            assertTrue(validation.isValid(),
                    "Generated XML must be schema-valid; got " + validation.errors().size() + " errors");

            // Fund volume on FundDynamicData — profile extension.
            assertTrue(xml.contains("<TotalNetAssetValue>"),
                    "Fund-level TotalNetAssetValue must be generated (Fondsvolumen)");
            assertTrue(xml.contains("25535819.05"),
                    "Fund volume FIXED Amount must appear in XML");

            // Two ShareClass siblings under SingleFund, driven out of optional CHOICE by
            // rule preferences. Each must carry differentiated SEQUENCE values.
            assertTrue(xml.contains("<SingleFund>"),
                    "SingleFund must be generated despite sitting inside an optional CHOICE");
            assertTrue(xml.contains("<ShareClasses>"),
                    "ShareClasses container must be generated");
            long shareClassCount = java.util.regex.Pattern.compile("<ShareClass>")
                    .matcher(xml).results().count();
            assertEquals(2, shareClassCount,
                    "Per-rule maxOccurrences=2 override must produce exactly 2 ShareClass elements");
            assertTrue(xml.contains("AT0000010001") && xml.contains("AT0000010002"),
                    "Both SEQUENCE-driven ISINs must appear");
            assertTrue(xml.contains("SHARECLASS 1") && xml.contains("SHARECLASS 2"),
                    "Both SEQUENCE-driven OfficialNames must appear");
            assertTrue(xml.contains("<NavPrice>137.924576</NavPrice>")
                            && xml.contains("<NavPrice>142.924576</NavPrice>"),
                    "Both SEQUENCE-driven NavPrice values must appear");

            // Bond master data was added as a profile extension — verify the representative
            // fields make it into every Asset/AssetDetails/Bond block.
            assertTrue(xml.contains("<ConvertibleFlag>false</ConvertibleFlag>"),
                    "Bond/ConvertibleFlag FIXED value must appear");
            assertTrue(xml.contains("<IssueDate>2020-06-15</IssueDate>"),
                    "Bond/IssueDate FIXED value must appear");
            assertTrue(xml.contains("<MaturityDate>2030-06-15</MaturityDate>"),
                    "Bond/MaturityDate FIXED value must appear");
            assertTrue(xml.contains("<InterestRate>3.25</InterestRate>"),
                    "Bond/InterestRate FIXED value must appear");
            assertTrue(xml.contains("<RedemptionRate>100</RedemptionRate>"),
                    "Bond/RedemptionRate FIXED value must appear");
            assertTrue(xml.contains("<Type>fix</Type>")
                            && xml.contains("<PaymentFrequency>YEAR</PaymentFrequency>"),
                    "Bond/Coupon fields must appear");
            assertTrue(xml.contains("Republic of Austria")
                            && xml.contains("529900S2SS5KMF9Z2G23"),
                    "Bond/Issuer Name and LEI must appear");
            assertTrue(xml.contains("Ballhausplatz 2")
                            && xml.contains("<CityName>Vienna</CityName>"),
                    "Bond/Issuer Address fields must appear");

            // Performance budget: regex-cache fix + Phase-2 structural alignment with the
            // plain generator brought generation down from 10+ minutes (pre-fix) → 57s
            // (regex cache) → ~30s (Phase 2: recursive wouldProduceEmptyContainer + plain's
            // CHOICE cardinality). The generous 180s budget catches regressions in either
            // direction without being flaky on slower CI runners.
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
