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

/**
 * Implementation of the PropertiesService interface.
 * This class handles loading, saving, and managing application properties.
 */
public class PropertiesServiceImpl implements PropertiesService {
    private static final Logger logger = LogManager.getLogger(PropertiesServiceImpl.class);
    private static final String FREE_XML_TOOLKIT_PROPERTIES = "FreeXmlToolkit.properties";
    private static final File propertiesFile = new File(FREE_XML_TOOLKIT_PROPERTIES);
    private static final PropertiesService instance = new PropertiesServiceImpl();
    private Properties properties = new Properties();

    /**
     * Returns the singleton instance of the PropertiesService.
     *
     * @return the singleton instance
     */
    public static PropertiesService getInstance() {
        return instance;
    }

    /**
     * Private constructor to initialize the properties service.
     * Creates a default properties file if it does not exist.
     */
    private PropertiesServiceImpl() {
        if (!propertiesFile.exists()) {
            logger.debug("Creating properties file...");
            createDefaultProperties();
        } else {
            logger.debug("Properties file already exists!");
            properties = loadProperties();
        }
    }

    /**
     * Loads properties from the properties file.
     *
     * @return the loaded properties
     */
    @Override
    public Properties loadProperties() {
        try (FileInputStream fis = new FileInputStream(propertiesFile)) {
            properties.load(fis);
            logger.debug("Loaded Properties: {}", properties);
        } catch (IOException e) {
            logger.warn("No properties found!");
        }
        return properties;
    }

    /**
     * Saves the given properties to the properties file.
     *
     * @param save the properties to save
     */
    @Override
    public void saveProperties(Properties save) {
        this.properties = save;
        try (FileOutputStream fos = new FileOutputStream(propertiesFile)) {
            this.properties.store(fos, null);
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    /**
     * Creates a default properties file with predefined properties.
     */
    @Override
    public void createDefaultProperties() {
        properties = new Properties();
        properties.setProperty("customTempFolder", System.getProperty("java.io.tmpdir"));
        properties.setProperty("font.family", "");
        properties.setProperty("font.size", "");
        properties.setProperty("http.proxy.user", "");
        properties.setProperty("http.proxy.password", "");
        properties.setProperty("https.proxy.host", "");
        properties.setProperty("https.proxy.port", "");
        properties.setProperty("https.proxy.user", "");
        properties.setProperty("https.proxy.password", "");
        properties.setProperty("language", "");
        properties.setProperty("sendUsageStatistics", "false");
        properties.setProperty("usageDuration", "0");
        properties.setProperty("useSystemTempFolder", "true");
        properties.setProperty("version", "20241209");

        try (FileOutputStream fos = new FileOutputStream(FREE_XML_TOOLKIT_PROPERTIES)) {
            properties.store(fos, null);
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    /**
     * Retrieves the list of last opened files from the properties.
     *
     * @return the list of last opened files
     */
    @Override
    public List<File> getLastOpenFiles() {
        Properties p = loadProperties();
        List<File> files = new LinkedList<>();
        for (int i = 0; i < 99; i++) {
            String filePath = p.getProperty("LastOpenFile." + i);
            if (filePath != null) {
                File f = new File(filePath);
                if (f.exists()) {
                    files.add(f);
                }
            } else {
                break;
            }
        }
        return files;
    }
}