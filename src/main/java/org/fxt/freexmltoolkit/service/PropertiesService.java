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

import org.fxt.freexmltoolkit.domain.XmlParserType;

import java.io.File;
import java.util.List;
import java.util.Properties;

public interface PropertiesService {
    Properties loadProperties();

    void saveProperties(Properties save);

    void createDefaultProperties();

    List<File> getLastOpenFiles();

    void addLastOpenFile(File file);

    String getLastOpenDirectory();

    void setLastOpenDirectory(String path);

    String get(String key);

    void set(String key, String value);

    /**
     * Gets the number of spaces used for XML indentation during pretty printing.
     *
     * @return the number of indent spaces (default: 4)
     */
    int getXmlIndentSpaces();

    /**
     * Sets the number of spaces used for XML indentation during pretty printing.
     *
     * @param spaces the number of indent spaces
     */
    void setXmlIndentSpaces(int spaces);

    /**
     * Gets whether XML files should be automatically formatted after loading.
     *
     * @return true if autoformat is enabled, false otherwise (default: false)
     */
    boolean isXmlAutoFormatAfterLoading();

    /**
     * Sets whether XML files should be automatically formatted after loading.
     *
     * @param autoFormat true to enable autoformat, false to disable
     */
    void setXmlAutoFormatAfterLoading(boolean autoFormat);

    // XSD-specific settings

    /**
     * Gets whether XSD files should be auto-saved periodically.
     *
     * @return true if auto-save is enabled, false otherwise (default: false)
     */
    boolean isXsdAutoSaveEnabled();

    /**
     * Sets whether XSD files should be auto-saved periodically.
     *
     * @param enabled true to enable auto-save, false to disable
     */
    void setXsdAutoSaveEnabled(boolean enabled);

    /**
     * Gets the auto-save interval in minutes.
     *
     * @return the auto-save interval in minutes (default: 5)
     */
    int getXsdAutoSaveInterval();

    /**
     * Sets the auto-save interval in minutes.
     *
     * @param minutes the auto-save interval in minutes
     */
    void setXsdAutoSaveInterval(int minutes);

    /**
     * Gets whether backup files should be created when saving XSD files.
     *
     * @return true if backup is enabled, false otherwise (default: true)
     */
    boolean isXsdBackupEnabled();

    /**
     * Sets whether backup files should be created when saving XSD files.
     *
     * @param enabled true to enable backup, false to disable
     */
    void setXsdBackupEnabled(boolean enabled);

    /**
     * Gets the number of backup versions to keep.
     *
     * @return the number of backup versions (default: 3)
     */
    int getXsdBackupVersions();

    /**
     * Sets the number of backup versions to keep.
     *
     * @param versions the number of backup versions
     */
    void setXsdBackupVersions(int versions);

    /**
     * Gets whether XSD files should be pretty-printed on save.
     *
     * @return true if pretty-print is enabled, false otherwise (default: true)
     */
    boolean isXsdPrettyPrintOnSave();

    /**
     * Sets whether XSD files should be pretty-printed on save.
     *
     * @param enabled true to enable pretty-print, false to disable
     */
    void setXsdPrettyPrintOnSave(boolean enabled);

    /**
     * Gets whether Schematron files should be pretty-printed on load.
     *
     * @return true if pretty-print is enabled, false otherwise (default: false)
     */
    boolean isSchematronPrettyPrintOnLoad();

    /**
     * Sets whether Schematron files should be pretty-printed on load.
     *
     * @param enabled true to enable pretty-print, false to disable
     */
    void setSchematronPrettyPrintOnLoad(boolean enabled);

    /**
     * Gets the XML parser type to use for XSD validation.
     *
     * @return the XML parser type (default: SAXON)
     */
    XmlParserType getXmlParserType();

    /**
     * Sets the XML parser type to use for XSD validation.
     *
     * @param parserType the XML parser type
     */
    void setXmlParserType(XmlParserType parserType);

    // Update check settings

    /**
     * Gets whether automatic update checking is enabled.
     *
     * @return true if update checking is enabled, false otherwise (default: true)
     */
    boolean isUpdateCheckEnabled();

    /**
     * Sets whether automatic update checking is enabled.
     *
     * @param enabled true to enable update checking, false to disable
     */
    void setUpdateCheckEnabled(boolean enabled);

    /**
     * Gets the version that the user has chosen to skip for update notifications.
     *
     * @return the skipped version string, or null if no version is skipped
     */
    String getSkippedVersion();

    /**
     * Sets the version to skip for update notifications.
     *
     * @param version the version to skip, or null to clear
     */
    void setSkippedVersion(String version);
}