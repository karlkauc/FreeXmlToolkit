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

package org.fxt.freexmltoolkit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UpdateInfo")
class UpdateInfoTest {

    @Nested
    @DisplayName("Record Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Creates update available record")
        void createsUpdateAvailableRecord() {
            UpdateInfo info = new UpdateInfo(
                    "1.0.0",
                    "1.1.0",
                    "Release 1.1.0",
                    "## Changelog\n- New feature\n- Bug fix",
                    "https://github.com/example/project/releases/tag/v1.1.0",
                    "2024-01-15",
                    true
            );

            assertEquals("1.0.0", info.currentVersion());
            assertEquals("1.1.0", info.latestVersion());
            assertEquals("Release 1.1.0", info.releaseName());
            assertTrue(info.releaseNotes().contains("Changelog"));
            assertTrue(info.downloadUrl().contains("github.com"));
            assertEquals("2024-01-15", info.publishedDate());
            assertTrue(info.updateAvailable());
        }

        @Test
        @DisplayName("Creates no update available record")
        void createsNoUpdateAvailableRecord() {
            UpdateInfo info = new UpdateInfo(
                    "1.1.0",
                    "1.1.0",
                    "Release 1.1.0",
                    "Current release",
                    "https://github.com/example/project/releases/tag/v1.1.0",
                    "2024-01-15",
                    false
            );

            assertEquals("1.1.0", info.currentVersion());
            assertEquals("1.1.0", info.latestVersion());
            assertFalse(info.updateAvailable());
        }
    }

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodsTests {

        @Test
        @DisplayName("noUpdateAvailable creates correct record")
        void noUpdateAvailableCreatesCorrectRecord() {
            UpdateInfo info = UpdateInfo.noUpdateAvailable("1.2.0");

            assertEquals("1.2.0", info.currentVersion());
            assertEquals("1.2.0", info.latestVersion());
            assertNull(info.releaseName());
            assertNull(info.releaseNotes());
            assertNull(info.downloadUrl());
            assertNull(info.publishedDate());
            assertFalse(info.updateAvailable());
        }

        @Test
        @DisplayName("errorDuringCheck creates correct record")
        void errorDuringCheckCreatesCorrectRecord() {
            UpdateInfo info = UpdateInfo.errorDuringCheck("1.3.0");

            assertEquals("1.3.0", info.currentVersion());
            assertNull(info.latestVersion());
            assertNull(info.releaseName());
            assertNull(info.releaseNotes());
            assertNull(info.downloadUrl());
            assertNull(info.publishedDate());
            assertFalse(info.updateAvailable());
        }
    }

    @Nested
    @DisplayName("Equality")
    class EqualityTests {

        @Test
        @DisplayName("Equal records are equal")
        void equalRecordsAreEqual() {
            UpdateInfo info1 = new UpdateInfo("1.0", "1.1", "Release", "Notes", "url", "date", true);
            UpdateInfo info2 = new UpdateInfo("1.0", "1.1", "Release", "Notes", "url", "date", true);

            assertEquals(info1, info2);
            assertEquals(info1.hashCode(), info2.hashCode());
        }

        @Test
        @DisplayName("Different versions are not equal")
        void differentVersionsNotEqual() {
            UpdateInfo info1 = new UpdateInfo("1.0", "1.1", "Release", "Notes", "url", "date", true);
            UpdateInfo info2 = new UpdateInfo("1.0", "1.2", "Release", "Notes", "url", "date", true);

            assertNotEquals(info1, info2);
        }

        @Test
        @DisplayName("Different updateAvailable are not equal")
        void differentUpdateAvailableNotEqual() {
            UpdateInfo info1 = new UpdateInfo("1.0", "1.1", "Release", "Notes", "url", "date", true);
            UpdateInfo info2 = new UpdateInfo("1.0", "1.1", "Release", "Notes", "url", "date", false);

            assertNotEquals(info1, info2);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Handles null release notes")
        void handlesNullReleaseNotes() {
            UpdateInfo info = new UpdateInfo("1.0", "1.1", "Release", null, "url", "date", true);

            assertNull(info.releaseNotes());
            assertTrue(info.updateAvailable());
        }

        @Test
        @DisplayName("Handles empty version strings")
        void handlesEmptyVersionStrings() {
            UpdateInfo info = new UpdateInfo("", "", "Release", "Notes", "url", "date", false);

            assertEquals("", info.currentVersion());
            assertEquals("", info.latestVersion());
        }

        @Test
        @DisplayName("Factory methods with various version formats")
        void factoryMethodsWithVariousVersions() {
            // Semantic versioning
            UpdateInfo info1 = UpdateInfo.noUpdateAvailable("1.2.3");
            assertEquals("1.2.3", info1.currentVersion());

            // With pre-release tag
            UpdateInfo info2 = UpdateInfo.noUpdateAvailable("2.0.0-beta.1");
            assertEquals("2.0.0-beta.1", info2.currentVersion());

            // Simple version
            UpdateInfo info3 = UpdateInfo.errorDuringCheck("v3.0");
            assertEquals("v3.0", info3.currentVersion());
        }
    }
}
