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

package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

public class PropertiesServiceImpl implements PropertiesService {
    private final static Logger logger = LogManager.getLogger(PropertiesService.class);
    public static final String FREE_XML_TOOLKIT_PROPERTIES = "FreeXmlToolkit.properties";

    Properties properties = new Properties();

    private static final PropertiesService instance = new PropertiesServiceImpl();

    public static PropertiesService getInstance() {
        return instance;
    }

    private PropertiesServiceImpl() {
        logger.debug("BIM IM PropertiesServiceImpl CONSTRUCTOR!!!");
    }

    @Override
    public Properties loadProperties() {
        BufferedInputStream stream;
        try {
            stream = new BufferedInputStream(new FileInputStream(FREE_XML_TOOLKIT_PROPERTIES));
            properties.load(stream);
            stream.close();

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
            this.properties.store(new FileOutputStream(FREE_XML_TOOLKIT_PROPERTIES), null);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    public List<File> getLastOpenFiles() {
        var p = instance.loadProperties();
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
    }
}
