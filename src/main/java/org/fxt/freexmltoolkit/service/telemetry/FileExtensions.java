/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2026.
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

package org.fxt.freexmltoolkit.service.telemetry;

import java.util.Locale;
import java.util.Set;

/**
 * The file extension of an opened file as a telemetry value: only extensions from a fixed
 * list of well-known formats are reported as they are, anything else as {@code other} and a
 * name without extension as {@code none}. A file name never reaches the telemetry - an
 * extension outside the list could be part of a name.
 */
public final class FileExtensions {

    /** Value for an extension outside {@link #KNOWN}. */
    public static final String OTHER = "other";
    /** Value for a file name without an extension. */
    public static final String NONE = "none";

    /** Well-known extensions: the supported ones and formats that may be worth supporting. */
    static final Set<String> KNOWN = Set.of(
            // supported
            "xml", "wsdl", "xspec", "xsd", "xsl", "xslt", "sch", "schematron", "json", "jsonc", "json5",
            "xq", "xquery", "xqm", "xqy", "xpath", "xpl", "xproc", "html", "htm", "xhtml", "yaml", "yml",
            "dtd", "rng", "rnc", "fo", "fxtproj",
            // XML vocabularies with their own extension
            "config", "xaml", "csproj", "vbproj", "fsproj", "props", "targets", "resx", "nuspec", "plist",
            "svg", "gpx", "kml", "gml", "xlf", "xliff", "tmx", "dita", "ditamap", "docbook", "rss", "atom",
            "rdf", "owl", "xmi", "bpmn", "dmn", "drawio", "xbrl", "xsr", "ofx", "pom", "jrxml", "fxml",
            "manifest", "vcxproj", "xul", "mxml", "graphml", "kmz", "odt", "docx", "xlsx", "pptx",
            "sitemap", "tei", "jats", "epub", "opf", "ncx", "smil", "mathml", "x3d", "dae", "cdxml",
            // JSON vocabularies
            "geojson", "jsonld", "har", "ipynb", "avsc", "webmanifest", "topojson", "ndjson", "jsonl",
            // other text formats
            "txt", "log", "csv", "tsv", "md", "markdown", "ini", "cfg", "conf", "properties", "toml",
            "env", "sql", "edi", "edifact", "x12", "hl7", "fix", "swift", "mt", "dat", "out", "err",
            "py", "java", "js", "ts", "css", "scss", "sh", "bat", "cmd", "ps1", "gradle", "kts",
            "proto", "graphql", "rtf", "tex", "adoc", "rst", "bak", "tmp", "orig");

    private FileExtensions() {
    }

    /**
     * @param fileName a file name (may be {@code null})
     * @return the lower-case extension when it is well known, {@link #OTHER}, or {@link #NONE}
     */
    public static String telemetryValue(String fileName) {
        if (fileName == null) {
            return NONE;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return NONE;
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return KNOWN.contains(extension) ? extension : OTHER;
    }
}
