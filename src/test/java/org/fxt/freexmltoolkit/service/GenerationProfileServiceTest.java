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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.domain.GenerationProfile;
import org.fxt.freexmltoolkit.domain.GenerationStrategy;
import org.fxt.freexmltoolkit.domain.XPathRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("GenerationProfileService")
class GenerationProfileServiceTest {

    @TempDir
    Path tempDir;

    private GenerationProfileService service;

    @BeforeEach
    void setUp() {
        service = new GenerationProfileService(tempDir);
    }

    private GenerationProfile createTestProfile(String name) {
        var profile = new GenerationProfile(name);
        profile.setDescription("Test profile");
        profile.setSchemaFile("test.xsd");
        profile.setBatchCount(3);
        profile.setMaxOccurrences(2);
        profile.setMandatoryOnly(true);
        profile.addRule(new XPathRule("/root/@id", GenerationStrategy.SEQUENCE,
                Map.of("pattern", "ID-{seq:4}", "start", "1")));
        profile.addRule(new XPathRule("/root/name", GenerationStrategy.FIXED,
                Map.of("value", "TestName")));
        return profile;
    }

    @Nested
    @DisplayName("Save and Load")
    class SaveLoadTests {

        @Test
        @DisplayName("Round-trip save and load preserves all fields")
        void roundTrip() {
            var profile = createTestProfile("RoundTrip");
            service.save(profile);

            var loaded = service.load("RoundTrip");
            assertNotNull(loaded);
            assertEquals("RoundTrip", loaded.getName());
            assertEquals("Test profile", loaded.getDescription());
            assertEquals("test.xsd", loaded.getSchemaFile());
            assertEquals(3, loaded.getBatchCount());
            assertEquals(2, loaded.getMaxOccurrences());
            assertTrue(loaded.isMandatoryOnly());
            assertEquals(2, loaded.getRules().size());

            var seqRule = loaded.getRules().get(0);
            assertEquals("/root/@id", seqRule.getXpath());
            assertEquals(GenerationStrategy.SEQUENCE, seqRule.getStrategy());
            assertEquals("ID-{seq:4}", seqRule.getConfigValue("pattern"));
        }

        @Test
        @DisplayName("Load returns null for non-existent profile")
        void loadNonExistent() {
            assertNull(service.load("NonExistent"));
        }

        @Test
        @DisplayName("Load returns null for null/blank name")
        void loadNullName() {
            assertNull(service.load(null));
            assertNull(service.load(""));
            assertNull(service.load("   "));
        }

        @Test
        @DisplayName("Save with null profile throws exception")
        void saveNull() {
            assertThrows(IllegalArgumentException.class, () -> service.save(null));
        }

        @Test
        @DisplayName("Save with blank name throws exception")
        void saveBlankName() {
            var profile = new GenerationProfile();
            profile.setName("");
            assertThrows(IllegalArgumentException.class, () -> service.save(profile));
        }

        @Test
        @DisplayName("Save overwrites existing profile")
        void overwrite() {
            var profile = createTestProfile("Overwrite");
            service.save(profile);

            profile.setDescription("Updated");
            profile.setBatchCount(10);
            service.save(profile);

            var loaded = service.load("Overwrite");
            assertEquals("Updated", loaded.getDescription());
            assertEquals(10, loaded.getBatchCount());
        }
    }

    @Nested
    @DisplayName("LoadAll")
    class LoadAllTests {

        @Test
        @DisplayName("Loads all profiles from directory")
        void loadsAll() {
            service.save(createTestProfile("Profile A"));
            service.save(createTestProfile("Profile B"));
            service.save(createTestProfile("Profile C"));

            List<GenerationProfile> all = service.loadAll();
            assertEquals(3, all.size());
        }

        @Test
        @DisplayName("Returns empty list when no profiles exist")
        void emptyList() {
            assertTrue(service.loadAll().isEmpty());
        }
    }

    @Nested
    @DisplayName("Delete")
    class DeleteTests {

        @Test
        @DisplayName("Deletes existing profile")
        void deleteExisting() {
            service.save(createTestProfile("ToDelete"));
            assertTrue(service.delete("ToDelete"));
            assertNull(service.load("ToDelete"));
        }

        @Test
        @DisplayName("Returns false for non-existent profile")
        void deleteNonExistent() {
            assertFalse(service.delete("NonExistent"));
        }

        @Test
        @DisplayName("Returns false for null/blank name")
        void deleteNull() {
            assertFalse(service.delete(null));
            assertFalse(service.delete(""));
        }
    }

    @Nested
    @DisplayName("Duplicate")
    class DuplicateTests {

        @Test
        @DisplayName("Duplicates profile with new name")
        void duplicate() {
            service.save(createTestProfile("Original"));
            var copy = service.duplicate("Original", "Copy");

            assertNotNull(copy);
            assertEquals("Copy", copy.getName());
            assertEquals("Test profile", copy.getDescription());
            assertEquals(2, copy.getRules().size());

            // Both should exist independently
            assertNotNull(service.load("Original"));
            assertNotNull(service.load("Copy"));
        }

        @Test
        @DisplayName("Returns null for non-existent source")
        void duplicateNonExistent() {
            assertNull(service.duplicate("Missing", "Copy"));
        }
    }

    @Nested
    @DisplayName("Export and Import")
    class ExportImportTests {

        @Test
        @DisplayName("Round-trip export and import")
        void exportImport() throws IOException {
            var profile = createTestProfile("ExportTest");
            File exportFile = tempDir.resolve("export.json").toFile();

            service.exportToFile(profile, exportFile);
            assertTrue(exportFile.exists());

            var imported = service.importFromFile(exportFile);
            assertNotNull(imported);
            assertEquals("ExportTest", imported.getName());
            assertEquals(2, imported.getRules().size());
        }

        @Test
        @DisplayName("Exported JSON is valid and readable")
        void exportedJsonIsValid() throws IOException {
            var profile = createTestProfile("JsonTest");
            File exportFile = tempDir.resolve("test.json").toFile();

            service.exportToFile(profile, exportFile);
            String json = Files.readString(exportFile.toPath());
            assertTrue(json.contains("\"name\": \"JsonTest\""));
            assertTrue(json.contains("\"strategy\": \"SEQUENCE\""));
        }
    }

    @Nested
    @DisplayName("File name sanitization")
    class SanitizationTests {

        @Test
        @DisplayName("Special characters are replaced")
        void specialCharsReplaced() {
            assertEquals("My_Profile_v1_0", GenerationProfileService.sanitizeFileName("My Profile v1.0"));
            assertEquals("test___", GenerationProfileService.sanitizeFileName("test/\\:"));
        }

        @Test
        @DisplayName("Safe characters are preserved")
        void safeCharsPreserved() {
            assertEquals("profile-name_123", GenerationProfileService.sanitizeFileName("profile-name_123"));
        }

        @Test
        @DisplayName("Profile with special name can be saved and loaded")
        void profileWithSpecialName() {
            var profile = createTestProfile("Mein Profil (v2.0)");
            service.save(profile);

            var loaded = service.load("Mein Profil (v2.0)");
            assertNotNull(loaded);
            assertEquals("Mein Profil (v2.0)", loaded.getName());
        }
    }

    @Nested
    @DisplayName("Bundled example profiles")
    class ExampleProfileTests {

        @Test
        @DisplayName("FundsXML Bond Fund EAM Demo profile is importable and well-formed")
        void bondFundProfileImportable() throws IOException {
            File profileFile = new File("release/examples/profiles/FundsXML_Bond_Fund_EAM_Demo.json");
            assertTrue(profileFile.exists(),
                    "Example profile must exist at " + profileFile.getAbsolutePath());

            GenerationProfile loaded = service.importFromFile(profileFile);
            assertNotNull(loaded, "Profile must deserialize successfully");
            assertEquals("FundsXML Bond Fund EAM Demo", loaded.getName());
            assertEquals("FundsXML4.xsd", loaded.getSchemaFile());
            assertFalse(loaded.getRules().isEmpty(), "Profile must contain rules");

            // Verify the key Erste Asset Management fingerprint rules are present
            assertRulePresent(loaded.getRules(), "//DataSupplier/Name", "Erste Asset Management GmbH");
            assertRulePresent(loaded.getRules(), "/FundsXML4/Funds/Fund/Identifiers/LEI", "PQOH26KWDF7CG10L6792");
            assertRulePresent(loaded.getRules(), "/FundsXML4/ControlData/ContentDate", "2021-11-30");
            assertRulePresent(loaded.getRules(), "/FundsXML4/Funds/Fund/FundStaticData/ListedLegalStructure", "UCITS");

            // SEQUENCE rule for position IDs must use the proper pattern
            XPathRule uniqueIdRule = loaded.getRules().stream()
                    .filter(r -> "//Position/UniqueID".equals(r.getXpath()))
                    .findFirst().orElseThrow();
            assertEquals(GenerationStrategy.SEQUENCE, uniqueIdRule.getStrategy());
            assertEquals("ID_{seq:8}", uniqueIdRule.getConfigValue("pattern"));
        }

        private void assertRulePresent(List<XPathRule> rules, String xpath, String expectedValue) {
            XPathRule rule = rules.stream()
                    .filter(r -> xpath.equals(r.getXpath()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No rule found for xpath: " + xpath));
            assertEquals(GenerationStrategy.FIXED, rule.getStrategy(),
                    "Rule for " + xpath + " must use FIXED strategy");
            assertEquals(expectedValue, rule.getConfigValue("value"),
                    "FIXED value for " + xpath + " mismatch");
            assertTrue(rule.isEnabled(), "Rule for " + xpath + " must be enabled");
        }
    }
}
