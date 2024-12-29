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

import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Properties;

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
        Assertions.assertEquals("false", properties.getProperty("sendUsageStatistics"));
    }

    @Test
    void getLastOpenFilesList() {
        PropertiesService propertiesService = PropertiesServiceImpl.getInstance();
        var fileList = propertiesService.getLastOpenFiles();
        Assertions.assertNotNull(fileList);
        Assertions.assertTrue(fileList.isEmpty() || fileList.stream().allMatch(File::exists));
    }
}
