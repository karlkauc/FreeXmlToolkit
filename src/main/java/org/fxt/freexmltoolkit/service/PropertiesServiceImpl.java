package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.WatchService;
import java.util.Properties;

public class PropertiesServiceImpl implements PropertiesService {

    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    Properties properties = new Properties();

    WatchService watchService;

    public PropertiesServiceImpl() {
        logger.debug("BIM IM CONSTRUCTOR!!!");

    }

    @Override
    public Properties loadProperties() {
        BufferedInputStream stream = null;
        try {
            stream = new BufferedInputStream(new FileInputStream("FreeXmlToolkit.properties"));
            properties.load(stream);
            stream.close();

            logger.debug("Loaded Properties: {}", properties);
        } catch (IOException e) {
            logger.warn("No properties found!");
        }
        return properties;
    }
}
