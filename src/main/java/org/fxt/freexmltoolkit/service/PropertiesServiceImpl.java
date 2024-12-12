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

package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

public class PropertiesServiceImpl implements PropertiesService {
    private final static Logger logger = LogManager.getLogger(PropertiesServiceImpl.class);

    public static final String FREE_XML_TOOLKIT_PROPERTIES = "FreeXmlToolkit.properties";
    public static final File propertiesFile = new File(FREE_XML_TOOLKIT_PROPERTIES);

    Properties properties = new Properties();

    private static final PropertiesService instance = new PropertiesServiceImpl();

    public static PropertiesService getInstance() {
        return instance;
    }

    private PropertiesServiceImpl() {
        if (propertiesFile != null && !propertiesFile.exists()) {
            logger.debug("creating properties file...");
            createDefaultProperties();
        } else {
            logger.debug("properties file already exists!");
            properties = loadProperties();
        }
    }

    @Override
    public Properties loadProperties() {
        try {
            properties.load(new FileInputStream(propertiesFile));
            logger.debug("Loaded Properties: {}", properties);
        } catch (IOException e) {
            logger.warn("No properties found!");
        }
        return properties;
    }

    @Override
    public void saveProperties(Properties save) {
        this.properties = save;
        try {
            this.properties.store(new FileOutputStream(propertiesFile), null);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    public void createDefaultProperties() {
        try {
            this.properties = new Properties();

            ConnectionService connectionService = ConnectionServiceImpl.getInstance();
            /*
            var proxy = connectionService.getSystemProxy();
            if (proxy != null && proxy.type() == Proxy.Type.HTTP) {
                this.properties.setProperty("http.proxy.host", ((InetSocketAddress) proxy.address()).getHostName());
                this.properties.setProperty("http.proxy.port", String.valueOf(((InetSocketAddress) proxy.address()).getPort()));
            } else {
                this.properties.setProperty("http.proxy.host", "");
                this.properties.setProperty("http.proxy.port", "");
            }
             */

            this.properties.setProperty("customTempFolder", System.getProperty("java.io.tmpdir"));
            this.properties.setProperty("font.family", "");
            this.properties.setProperty("font.size", "");
            this.properties.setProperty("http.proxy.user", "");
            this.properties.setProperty("http.proxy.password", "");
            this.properties.setProperty("https.proxy.host", "");
            this.properties.setProperty("https.proxy.port", "");
            this.properties.setProperty("https.proxy.user", "");
            this.properties.setProperty("https.proxy.password", "");
            this.properties.setProperty("language", "");
            this.properties.setProperty("sendUsageStatistics", "false");
            this.properties.setProperty("usageDuration", "0");
            this.properties.setProperty("useSystemTempFolder", "true");
            this.properties.setProperty("version", "20241209");

            this.properties.store(new FileOutputStream(FREE_XML_TOOLKIT_PROPERTIES), null);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    public List<File> getLastOpenFiles() {
        var p = instance.loadProperties();

        if (p != null) {
            List<File> returns = new LinkedList<>();

            for (int i = 0; i < 99; i++) {
                String filePath = p.getProperty("LastOpenFile." + i);
                if (filePath != null) {
                    File f = new File(filePath);
                    if (f.exists()) {
                        returns.add(f);
                    }
                } else {
                    break;
                }
            }

            return returns;
        } else {
            return List.of();
        }
    }
}