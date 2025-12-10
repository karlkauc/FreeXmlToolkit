package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XmlParserType;

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
    private static final String LAST_OPEN_DIRECTORY_KEY = "last.open.directory";
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
            logger.debug("Loaded Properties '{}': {}", propertiesFile.getAbsolutePath(), properties);
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
        properties.setProperty(LAST_OPEN_DIRECTORY_KEY, System.getProperty("user.home"));
        properties.setProperty("language", "");
        properties.setProperty("sendUsageStatistics", "false");
        properties.setProperty("usageDuration", "0");
        properties.setProperty("useSystemTempFolder", "true");
        properties.setProperty("xml.editor.use.v2", "false"); // Feature flag for XmlCodeEditorV2
        properties.setProperty("ui.use.small.icons", "false"); // Feature flag for small toolbar icons
        properties.setProperty("version", "20241209");
        properties.setProperty("manualProxy", "false");
        properties.setProperty("useSystemProxy", "true");
        properties.setProperty("ssl.trustAllCerts", "false");

        // User information (optional)
        properties.setProperty("user.name", "");
        properties.setProperty("user.email", "");
        properties.setProperty("user.company", "");

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
        // KORREKTUR: Nur die letzten 5 Einträge laden
        for (int i = 0; i < 5; i++) {
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

    /**
     * NEU: Fügt eine Datei zur Liste der zuletzt geöffneten Dateien hinzu.
     * Die Liste wird auf 5 Einträge begrenzt und das neueste Element steht an erster Stelle.
     *
     * @param file die hinzuzufügende Datei
     */
    @Override
    public void addLastOpenFile(File file) {
        List<File> recentFiles = getLastOpenFiles();

        // Entferne die Datei, falls sie bereits in der Liste ist, um Duplikate zu vermeiden
        // und sie an die Spitze zu verschieben.
        recentFiles.removeIf(f -> f.getAbsolutePath().equals(file.getAbsolutePath()));

        // Füge die neue Datei am Anfang der Liste hinzu
        recentFiles.addFirst(file);

        // Kürze die Liste auf die maximale Größe von 5
        if (recentFiles.size() > 5) {
            recentFiles = new LinkedList<>(recentFiles.subList(0, 5));
        }

        // Entferne alle alten "LastOpenFile"-Einträge aus den Properties
        List<Object> keysToRemove = properties.keySet().stream()
                .filter(key -> ((String) key).startsWith("LastOpenFile."))
                .toList();
        keysToRemove.forEach(properties::remove);

        // Schreibe die neue, sortierte Liste in die Properties
        for (int i = 0; i < recentFiles.size(); i++) {
            properties.setProperty("LastOpenFile." + i, recentFiles.get(i).getAbsolutePath());
        }

        // Speichere die aktualisierten Properties
        saveProperties(properties);
        logger.debug("Updated last open files list: {}", recentFiles);
    }

    @Override
    public String getLastOpenDirectory() {
        return properties.getProperty(LAST_OPEN_DIRECTORY_KEY, System.getProperty("user.home"));
    }

    @Override
    public void setLastOpenDirectory(String path) {
        if (path != null) {
            properties.setProperty(LAST_OPEN_DIRECTORY_KEY, path);
            saveProperties(properties);
        }
    }

    @Override
    public String get(String key) {
        return properties.getProperty(key);
    }

    @Override
    public void set(String key, String value) {
        if (key != null && value != null) {
            properties.setProperty(key, value);
            saveProperties(properties);
        }
    }

    @Override
    public int getXmlIndentSpaces() {
        String spaces = properties.getProperty("xml.indent.spaces", "4");
        try {
            int value = Integer.parseInt(spaces);
            return Math.max(1, Math.min(value, 8)); // Clamp between 1 and 8
        } catch (NumberFormatException e) {
            logger.warn("Invalid xml.indent.spaces value '{}', using default 4", spaces);
            return 4;
        }
    }

    @Override
    public void setXmlIndentSpaces(int spaces) {
        int clampedSpaces = Math.max(1, Math.min(spaces, 8)); // Clamp between 1 and 8
        properties.setProperty("xml.indent.spaces", String.valueOf(clampedSpaces));
        saveProperties(properties);
        logger.debug("Set XML indent spaces to: {}", clampedSpaces);
    }

    @Override
    public boolean isXmlAutoFormatAfterLoading() {
        String autoFormat = properties.getProperty("xml.autoformat.after.loading", "false");
        try {
            return Boolean.parseBoolean(autoFormat);
        } catch (Exception e) {
            logger.warn("Invalid autoformat setting, defaulting to false: {}", autoFormat);
            return false;
        }
    }

    @Override
    public void setXmlAutoFormatAfterLoading(boolean autoFormat) {
        properties.setProperty("xml.autoformat.after.loading", String.valueOf(autoFormat));
        saveProperties(properties);
        logger.debug("Set XML autoformat after loading to: {}", autoFormat);
    }

    // XSD-specific settings implementation

    @Override
    public boolean isXsdAutoSaveEnabled() {
        return Boolean.parseBoolean(properties.getProperty("xsd.autoSave.enabled", "false"));
    }

    @Override
    public void setXsdAutoSaveEnabled(boolean enabled) {
        properties.setProperty("xsd.autoSave.enabled", String.valueOf(enabled));
        saveProperties(properties);
    }

    @Override
    public int getXsdAutoSaveInterval() {
        try {
            return Integer.parseInt(properties.getProperty("xsd.autoSave.interval", "5"));
        } catch (NumberFormatException e) {
            return 5;
        }
    }

    @Override
    public void setXsdAutoSaveInterval(int minutes) {
        properties.setProperty("xsd.autoSave.interval", String.valueOf(minutes));
        saveProperties(properties);
    }

    @Override
    public boolean isXsdBackupEnabled() {
        return Boolean.parseBoolean(properties.getProperty("xsd.backup.enabled", "true"));
    }

    @Override
    public void setXsdBackupEnabled(boolean enabled) {
        properties.setProperty("xsd.backup.enabled", String.valueOf(enabled));
        saveProperties(properties);
    }

    @Override
    public int getXsdBackupVersions() {
        try {
            return Integer.parseInt(properties.getProperty("xsd.backup.versions", "3"));
        } catch (NumberFormatException e) {
            return 3;
        }
    }

    @Override
    public void setXsdBackupVersions(int versions) {
        properties.setProperty("xsd.backup.versions", String.valueOf(versions));
        saveProperties(properties);
    }

    @Override
    public boolean isXsdPrettyPrintOnSave() {
        return Boolean.parseBoolean(properties.getProperty("xsd.prettyPrint.onSave", "true"));
    }

    @Override
    public void setXsdPrettyPrintOnSave(boolean enabled) {
        properties.setProperty("xsd.prettyPrint.onSave", String.valueOf(enabled));
        saveProperties(properties);
    }

    @Override
    public boolean isSchematronPrettyPrintOnLoad() {
        return Boolean.parseBoolean(properties.getProperty("schematron.prettyPrint.onLoad", "false"));
    }

    @Override
    public void setSchematronPrettyPrintOnLoad(boolean enabled) {
        properties.setProperty("schematron.prettyPrint.onLoad", String.valueOf(enabled));
        saveProperties(properties);
    }

    @Override
    public XmlParserType getXmlParserType() {
        String parserName = properties.getProperty("xml.parser.type", XmlParserType.XERCES.name());
        try {
            return XmlParserType.valueOf(parserName);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid parser type '{}', defaulting to XERCES", parserName);
            return XmlParserType.XERCES;
        }
    }

    @Override
    public void setXmlParserType(XmlParserType parserType) {
        if (parserType != null) {
            properties.setProperty("xml.parser.type", parserType.name());
            saveProperties(properties);
            logger.debug("Set XML parser type to: {}", parserType);
        }
    }

    // Update check settings implementation

    @Override
    public boolean isUpdateCheckEnabled() {
        return Boolean.parseBoolean(properties.getProperty("update.check.enabled", "true"));
    }

    @Override
    public void setUpdateCheckEnabled(boolean enabled) {
        properties.setProperty("update.check.enabled", String.valueOf(enabled));
        saveProperties(properties);
        logger.debug("Set update check enabled to: {}", enabled);
    }

    @Override
    public String getSkippedVersion() {
        String version = properties.getProperty("update.skipped.version", null);
        if (version != null && version.isBlank()) {
            return null;
        }
        return version;
    }

    @Override
    public void setSkippedVersion(String version) {
        if (version == null || version.isBlank()) {
            properties.remove("update.skipped.version");
        } else {
            properties.setProperty("update.skipped.version", version);
        }
        saveProperties(properties);
        logger.debug("Set skipped version to: {}", version);
    }

    // UI settings implementation

    @Override
    public boolean isUseSmallIcons() {
        return Boolean.parseBoolean(properties.getProperty("ui.use.small.icons", "false"));
    }

    @Override
    public void setUseSmallIcons(boolean useSmallIcons) {
        properties.setProperty("ui.use.small.icons", String.valueOf(useSmallIcons));
        saveProperties(properties);
        logger.debug("Set use small icons to: {}", useSmallIcons);
    }

    // XSD Serialization settings implementation

    @Override
    public String getXsdSortOrder() {
        return properties.getProperty("xsd.sort.order", "NAME_BEFORE_TYPE");
    }

    @Override
    public void setXsdSortOrder(String sortOrder) {
        if (sortOrder != null && (sortOrder.equals("TYPE_BEFORE_NAME") || sortOrder.equals("NAME_BEFORE_TYPE"))) {
            properties.setProperty("xsd.sort.order", sortOrder);
            saveProperties(properties);
            logger.debug("Set XSD sort order to: {}", sortOrder);
        } else {
            logger.warn("Invalid XSD sort order '{}', ignoring", sortOrder);
        }
    }
}