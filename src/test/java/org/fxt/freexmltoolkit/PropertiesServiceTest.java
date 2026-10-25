/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2023.
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

package org.fxt.freexmltoolkit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PropertiesServiceTest {

    @Test
    void propertiesServiceSingletonInstance() {
        PropertiesService instance1 = PropertiesServiceImpl.getInstance();
        PropertiesService instance2 = PropertiesServiceImpl.getInstance();
        Assertions.assertSame(instance1, instance2);
    }

    @Test
    void loadPropertiesFromFile() {
        PropertiesService propertiesService = PropertiesServiceImpl.getInstance();
        Properties properties = propertiesService.loadProperties();
        Assertions.assertNotNull(properties);
        Assertions.assertFalse(properties.isEmpty());
    }

    @Test
    void savePropertiesToFile() {
        PropertiesService propertiesService = PropertiesServiceImpl.getInstance();
        Properties properties = new Properties();
        properties.setProperty("testKey", "testValue");
        propertiesService.saveProperties(properties);

        Properties loadedProperties = propertiesService.loadProperties();
        Assertions.assertEquals("testValue", loadedProperties.getProperty("testKey"));
    }

    @Test
    void createDefaultPropertiesFile() {
        PropertiesService propertiesService = PropertiesServiceImpl.getInstance();
        propertiesService.createDefaultProperties();

        Properties properties = propertiesService.loadProperties();
        Assertions.assertEquals(System.getProperty("java.io.tmpdir"), properties.getProperty("customTempFolder"));
        Assertions.assertNull(properties.getProperty("sendUsageStatistics"));
    }

    @Test
    void getLastOpenFilesList() {
        PropertiesService propertiesService = PropertiesServiceImpl.getInstance();
        var fileList = propertiesService.getLastOpenFiles();
        Assertions.assertNotNull(fileList);
        Assertions.assertTrue(fileList.isEmpty() || fileList.stream().allMatch(File::exists));
    }

    @Test
    void schemaLibraryAutoBindDefaultsToTrueAndRoundTrips() {
        PropertiesService service = PropertiesServiceImpl.getInstance();
        Assertions.assertTrue(service.isSchemaLibraryAutoBindEnabled());
        service.setSchemaLibraryAutoBindEnabled(false);
        Assertions.assertFalse(service.isSchemaLibraryAutoBindEnabled());
        service.setSchemaLibraryAutoBindEnabled(true);
    }

    @Test
    void recentFilesKeepTwentyByDefaultNewestFirst(@TempDir Path tmp) throws IOException {
        PropertiesService service = PropertiesServiceImpl.getInstance();
        int previousLimit = service.getRecentFilesLimit();
        try {
            service.clearLastOpenFiles();
            service.setRecentFilesLimit(20);
            List<File> files = createFiles(tmp, 25);
            files.forEach(service::addLastOpenFile);

            List<File> recent = service.getLastOpenFiles();
            Assertions.assertEquals(20, recent.size());
            Assertions.assertEquals(files.get(24).getAbsolutePath(), recent.getFirst().getAbsolutePath());
            Assertions.assertEquals(files.get(5).getAbsolutePath(), recent.getLast().getAbsolutePath());
        } finally {
            service.clearLastOpenFiles();
            service.setRecentFilesLimit(previousLimit);
        }
    }

    @Test
    void reopeningARecentFileMovesItToTheTopWithoutDuplicate(@TempDir Path tmp) throws IOException {
        PropertiesService service = PropertiesServiceImpl.getInstance();
        try {
            service.clearLastOpenFiles();
            List<File> files = createFiles(tmp, 3);
            files.forEach(service::addLastOpenFile);
            service.addLastOpenFile(files.getFirst());

            List<String> recent = service.getLastOpenFiles().stream().map(File::getAbsolutePath).toList();
            Assertions.assertEquals(List.of(files.get(0).getAbsolutePath(), files.get(2).getAbsolutePath(),
                    files.get(1).getAbsolutePath()), recent);
        } finally {
            service.clearLastOpenFiles();
        }
    }

    @Test
    void lowerLimitTrimsOnTheNextAdd(@TempDir Path tmp) throws IOException {
        PropertiesService service = PropertiesServiceImpl.getInstance();
        int previousLimit = service.getRecentFilesLimit();
        try {
            service.clearLastOpenFiles();
            service.setRecentFilesLimit(20);
            List<File> files = createFiles(tmp, 6);
            files.subList(0, 5).forEach(service::addLastOpenFile);
            service.setRecentFilesLimit(3);
            service.addLastOpenFile(files.get(5));

            Assertions.assertEquals(3, service.getLastOpenFiles().size());
            Assertions.assertNull(service.loadProperties().getProperty("LastOpenFile.3"));
        } finally {
            service.clearLastOpenFiles();
            service.setRecentFilesLimit(previousLimit);
        }
    }

    @Test
    void recentFilesLimitIsClamped() {
        PropertiesService service = PropertiesServiceImpl.getInstance();
        int previousLimit = service.getRecentFilesLimit();
        try {
            service.setRecentFilesLimit(0);
            Assertions.assertEquals(1, service.getRecentFilesLimit());
            service.setRecentFilesLimit(1000);
            Assertions.assertEquals(100, service.getRecentFilesLimit());
        } finally {
            service.setRecentFilesLimit(previousLimit);
        }
    }

    @Test
    void unreachableRecentFileSurvivesAnotherAdd(@TempDir Path tmp) throws IOException {
        PropertiesService service = PropertiesServiceImpl.getInstance();
        try {
            service.clearLastOpenFiles();
            File missing = tmp.resolve("offline-share/missing.xml").toFile();
            service.addLastOpenFile(missing);
            service.addLastOpenFile(createFiles(tmp, 1).getFirst());

            Assertions.assertEquals(missing.getAbsolutePath(),
                    service.loadProperties().getProperty("LastOpenFile.1"));
        } finally {
            service.clearLastOpenFiles();
        }
    }

    private static List<File> createFiles(Path dir, int count) throws IOException {
        List<File> files = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            files.add(Files.writeString(dir.resolve("recent-" + i + ".xml"), "<r/>").toFile());
        }
        return files;
    }
}
