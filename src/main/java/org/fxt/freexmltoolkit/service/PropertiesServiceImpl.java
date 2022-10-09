package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
}
