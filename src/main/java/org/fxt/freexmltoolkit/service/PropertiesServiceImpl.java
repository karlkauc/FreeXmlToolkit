package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.WatchService;
import java.util.Properties;

public class PropertiesServiceImpl implements PropertiesService {

    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
    public static final String FREE_XML_TOOLKIT_PROPERTIES = "FreeXmlToolkit.properties";

    Properties properties = new Properties();

    WatchService watchService;

    public PropertiesServiceImpl() {
        logger.debug("BIM IM CONSTRUCTOR!!!");

    }

    @Override
    public Properties loadProperties() {
        BufferedInputStream stream = null;
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