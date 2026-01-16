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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.PdfDocumentationConfig;
import org.fxt.freexmltoolkit.domain.WordDocumentationConfig;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Exports debug information about documentation generation to a JSON file.
 * This helps diagnose issues with documentation generation by capturing all
 * configuration parameters and XSD metadata.
 */
public class DocumentationDebugExporter {

    private static final Logger logger = LogManager.getLogger(DocumentationDebugExporter.class);
    private static final String GENERATOR_VERSION = "1.2.3";

    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    /**
     * Exports debug information for a documentation generation.
     *
     * @param outputFile        the generated documentation file
     * @param format            the format (HTML, WORD, PDF)
     * @param data              the XSD documentation data
     * @param config            the configuration object (PdfDocumentationConfig, WordDocumentationConfig, or null for HTML)
     * @param includedLanguages the set of included languages
     * @param useMarkdown       whether markdown rendering is enabled
     * @param showDocInSvg      whether to show documentation in SVG diagrams
     */
    public static void exportDebugInfo(
            File outputFile,
            String format,
            XsdDocumentationData data,
            Object config,
            Set<String> includedLanguages,
            boolean useMarkdown,
            boolean showDocInSvg) {

        File debugFile = new File(outputFile.getAbsolutePath() + ".debug.json");

        Map<String, Object> debugInfo = new LinkedHashMap<>();

        // Generation info
        Map<String, Object> generationInfo = new LinkedHashMap<>();
        generationInfo.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        generationInfo.put("format", format);
        generationInfo.put("outputFile", outputFile.getAbsolutePath());
        generationInfo.put("xsdFile", data != null ? data.getXsdFilePath() : null);
        generationInfo.put("generatorVersion", GENERATOR_VERSION);
        debugInfo.put("generationInfo", generationInfo);

        // XSD info
        if (data != null) {
            Map<String, Object> xsdInfo = new LinkedHashMap<>();
            xsdInfo.put("targetNamespace", data.getTargetNamespace());
            xsdInfo.put("version", data.getVersion());
            xsdInfo.put("elementFormDefault", data.getElementFormDefault());
            xsdInfo.put("attributeFormDefault", data.getAttributeFormDefault());
            xsdInfo.put("globalElements", data.getGlobalElements() != null ? data.getGlobalElements().size() : 0);
            xsdInfo.put("globalComplexTypes", data.getGlobalComplexTypes() != null ? data.getGlobalComplexTypes().size() : 0);
            xsdInfo.put("globalSimpleTypes", data.getGlobalSimpleTypes() != null ? data.getGlobalSimpleTypes().size() : 0);
            xsdInfo.put("totalElements", data.getExtendedXsdElementMap() != null ? data.getExtendedXsdElementMap().size() : 0);
            debugInfo.put("xsdInfo", xsdInfo);
        }

        // Configuration
        if (config instanceof PdfDocumentationConfig pdfConfig) {
            debugInfo.put("config", pdfConfigToMap(pdfConfig));
        } else if (config instanceof WordDocumentationConfig wordConfig) {
            debugInfo.put("config", wordConfigToMap(wordConfig));
        } else {
            // HTML has no specific config
            Map<String, Object> htmlConfig = new LinkedHashMap<>();
            htmlConfig.put("type", "HTML");
            htmlConfig.put("note", "HTML documentation uses XsdDocumentationService defaults");
            debugInfo.put("config", htmlConfig);
        }

        // Language settings
        Map<String, Object> languageSettings = new LinkedHashMap<>();
        languageSettings.put("includedLanguages", includedLanguages != null ? includedLanguages : Set.of());
        languageSettings.put("useMarkdownRenderer", useMarkdown);
        languageSettings.put("showDocumentationInDiagrams", showDocInSvg);
        debugInfo.put("languageSettings", languageSettings);

        // Write to file
        try (FileWriter writer = new FileWriter(debugFile)) {
            gson.toJson(debugInfo, writer);
            logger.info("Debug info exported to: {}", debugFile.getAbsolutePath());
        } catch (IOException e) {
            logger.warn("Failed to export debug info: {}", e.getMessage());
        }
    }

    /**
     * Converts PdfDocumentationConfig to a Map for JSON serialization.
     */
    private static Map<String, Object> pdfConfigToMap(PdfDocumentationConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "PDF");

        // Page Layout
        map.put("pageSize", config.getPageSize().name());
        map.put("orientation", config.getOrientation().name());
        map.put("margins", config.getMargins().name());
        map.put("pageWidth", config.getPageWidth());
        map.put("pageHeight", config.getPageHeight());
        map.put("marginSize", config.getMarginSize());

        // Content Sections
        map.put("includeCoverPage", config.isIncludeCoverPage());
        map.put("includeToc", config.isIncludeToc());
        map.put("includeSchemaOverview", config.isIncludeSchemaOverview());
        map.put("includeSchemaDiagram", config.isIncludeSchemaDiagram());
        map.put("includeElementDiagrams", config.isIncludeElementDiagrams());
        map.put("includeComplexTypes", config.isIncludeComplexTypes());
        map.put("includeSimpleTypes", config.isIncludeSimpleTypes());
        map.put("includeDataDictionary", config.isIncludeDataDictionary());

        // Typography
        map.put("fontSize", config.getFontSize().name());
        map.put("fontSizeValue", config.getFontSizeValue());
        map.put("fontFamily", config.getFontFamily().name());
        map.put("fontFamilyName", config.getFontFamilyName());
        map.put("headingStyle", config.getHeadingStyle().name());
        map.put("lineSpacing", config.getLineSpacing().name());
        map.put("lineHeightValue", config.getLineHeightValue());

        // Header & Footer
        map.put("headerStyle", config.getHeaderStyle().name());
        map.put("footerStyle", config.getFooterStyle().name());
        map.put("includePageNumbers", config.isIncludePageNumbers());
        map.put("pageNumberPosition", config.getPageNumberPosition().name());

        // Design & Colors
        map.put("colorScheme", config.getColorScheme().name());
        map.put("primaryColor", config.getPrimaryColor());
        map.put("tableStyle", config.getTableStyle().name());
        map.put("watermark", config.getWatermark().name());
        map.put("watermarkText", config.getWatermarkText());
        map.put("hasWatermark", config.hasWatermark());

        // PDF Metadata
        map.put("generateBookmarks", config.isGenerateBookmarks());
        map.put("title", config.getTitle());
        map.put("author", config.getAuthor());
        map.put("subject", config.getSubject());
        map.put("keywords", config.getKeywords());

        return map;
    }

    /**
     * Converts WordDocumentationConfig to a Map for JSON serialization.
     */
    private static Map<String, Object> wordConfigToMap(WordDocumentationConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "WORD");

        // Page Layout
        map.put("pageSize", config.getPageSize().name());
        map.put("orientation", config.getOrientation().name());

        // Content Sections
        map.put("includeCoverPage", config.isIncludeCoverPage());
        map.put("includeToc", config.isIncludeToc());
        map.put("includeDataDictionary", config.isIncludeDataDictionary());
        map.put("includeSchemaDiagram", config.isIncludeSchemaDiagram());
        map.put("includeElementDiagrams", config.isIncludeElementDiagrams());

        // Style
        map.put("headerStyle", config.getHeaderStyle().name());

        return map;
    }
}
