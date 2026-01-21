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

import javafx.scene.Node;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Utility service for drag and drop functionality.
 * Provides consistent handling of file drag and drop across all controllers.
 */
public class DragDropService {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private DragDropService() {
        // Utility class
    }

    private static final Logger logger = LogManager.getLogger(DragDropService.class);

    // File extension constants
    /** XML file extensions. */
    public static final List<String> XML_EXTENSIONS = List.of(".xml");
    /** XSD file extensions. */
    public static final List<String> XSD_EXTENSIONS = List.of(".xsd");
    /** XSLT file extensions. */
    public static final List<String> XSLT_EXTENSIONS = List.of(".xsl", ".xslt");
    /** XQuery file extensions. */
    public static final List<String> XQUERY_EXTENSIONS = List.of(".xq", ".xquery", ".xqm");
    /** Schematron file extensions. */
    public static final List<String> SCHEMATRON_EXTENSIONS = List.of(".sch", ".schematron");
    /** Keystore file extensions. */
    public static final List<String> KEYSTORE_EXTENSIONS = List.of(".jks", ".keystore", ".p12", ".pfx");
    /** WSDL file extensions. */
    public static final List<String> WSDL_EXTENSIONS = List.of(".wsdl");

    // Combined extension lists for convenience
    /** All XML-related file extensions. */
    public static final List<String> ALL_XML_RELATED = Stream.of(
            XML_EXTENSIONS, XSD_EXTENSIONS, XSLT_EXTENSIONS, XQUERY_EXTENSIONS, SCHEMATRON_EXTENSIONS, WSDL_EXTENSIONS
    ).flatMap(List::stream).toList();

    /** XML and XSLT file extensions. */
    public static final List<String> XML_AND_XSLT = Stream.of(
            XML_EXTENSIONS, XSLT_EXTENSIONS
    ).flatMap(List::stream).toList();

    /** XML, XSLT, and XQuery file extensions. */
    public static final List<String> XML_AND_XSLT_AND_XQUERY = Stream.of(
            XML_EXTENSIONS, XSLT_EXTENSIONS, XQUERY_EXTENSIONS
    ).flatMap(List::stream).toList();

    /** XML and Schematron file extensions. */
    public static final List<String> XML_AND_SCHEMATRON = Stream.of(
            XML_EXTENSIONS, SCHEMATRON_EXTENSIONS
    ).flatMap(List::stream).toList();

    /**
     * Sets up drag and drop handling on a node.
     *
     * @param node              The node to enable drag and drop on
     * @param allowedExtensions List of allowed file extensions (e.g., ".xml", ".xsd")
     * @param fileHandler       Consumer that handles the dropped files
     */
    public static void setupDragDrop(Node node,
                                     List<String> allowedExtensions,
                                     Consumer<List<File>> fileHandler) {
        node.setOnDragOver(event -> handleDragOver(event, allowedExtensions));
        node.setOnDragDropped(event -> handleDragDropped(event, allowedExtensions, fileHandler));
    }

    /**
     * Handles drag over event - accepts the drag if files match allowed extensions.
     *
     * @param event The drag event.
     * @param allowedExtensions List of allowed file extensions.
     */
    public static void handleDragOver(DragEvent event, List<String> allowedExtensions) {
        Dragboard dragboard = event.getDragboard();
        if (dragboard.hasFiles() && hasFilesWithExtensions(dragboard.getFiles(), allowedExtensions)) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    /**
     * Handles drag dropped event - filters files and passes them to handler.
     *
     * @param event The drag event.
     * @param allowedExtensions List of allowed file extensions.
     * @param fileHandler Consumer that handles the dropped files.
     */
    public static void handleDragDropped(DragEvent event,
                                         List<String> allowedExtensions,
                                         Consumer<List<File>> fileHandler) {
        Dragboard dragboard = event.getDragboard();
        boolean success = false;

        if (dragboard.hasFiles()) {
            List<File> matchingFiles = filterByExtensions(dragboard.getFiles(), allowedExtensions);
            if (!matchingFiles.isEmpty()) {
                logger.debug("Dropped {} file(s) with extensions: {}", matchingFiles.size(), allowedExtensions);
                fileHandler.accept(matchingFiles);
                success = true;
            }
        }

        event.setDropCompleted(success);
        event.consume();
    }

    /**
     * Checks if any files in the list have the specified extensions.
     *
     * @param files      List of files to check
     * @param extensions List of allowed extensions (with dot, e.g., ".xml")
     * @return true if at least one file matches
     */
    public static boolean hasFilesWithExtensions(List<File> files, List<String> extensions) {
        return files.stream().anyMatch(file -> matchesExtension(file, extensions));
    }

    /**
     * Filters files by extension.
     *
     * @param files      List of files to filter
     * @param extensions List of allowed extensions (with dot, e.g., ".xml")
     * @return Filtered list of matching files
     */
    public static List<File> filterByExtensions(List<File> files, List<String> extensions) {
        return files.stream()
                .filter(file -> matchesExtension(file, extensions))
                .toList();
    }

    /**
     * Checks if a file matches any of the given extensions.
     *
     * @param file the file to check
     * @param extensions list of allowed extensions (with dot, e.g., ".xml")
     * @return true if the file matches any extension
     */
    public static boolean matchesExtension(File file, List<String> extensions) {
        String fileName = file.getName().toLowerCase();
        return extensions.stream().anyMatch(fileName::endsWith);
    }

    /**
     * Determines the file type based on extension.
     *
     * @param file The file to check
     * @return FileType enum value
     */
    public static FileType getFileType(File file) {
        String fileName = file.getName().toLowerCase();

        if (matchesExtension(file, XSD_EXTENSIONS)) {
            return FileType.XSD;
        } else if (matchesExtension(file, SCHEMATRON_EXTENSIONS)) {
            return FileType.SCHEMATRON;
        } else if (matchesExtension(file, XSLT_EXTENSIONS)) {
            return FileType.XSLT;
        } else if (matchesExtension(file, XQUERY_EXTENSIONS)) {
            return FileType.XQUERY;
        } else if (matchesExtension(file, WSDL_EXTENSIONS)) {
            return FileType.WSDL;
        } else if (matchesExtension(file, KEYSTORE_EXTENSIONS)) {
            return FileType.KEYSTORE;
        } else if (matchesExtension(file, XML_EXTENSIONS)) {
            return FileType.XML;
        }
        return FileType.UNKNOWN;
    }

    /**
     * File type enumeration for routing decisions.
     */
    public enum FileType {
        /** XML document file */
        XML,
        /** XSD schema file */
        XSD,
        /** XSLT transformation file */
        XSLT,
        /** XQuery file */
        XQUERY,
        /** Schematron validation file */
        SCHEMATRON,
        /** WSDL service definition file */
        WSDL,
        /** Java keystore file */
        KEYSTORE,
        /** Unknown or unsupported file type */
        UNKNOWN
    }
}
