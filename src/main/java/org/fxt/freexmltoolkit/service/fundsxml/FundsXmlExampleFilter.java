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

package org.fxt.freexmltoolkit.service.fundsxml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import org.fxt.freexmltoolkit.util.SecureXmlFactory;

/**
 * Decides which files of the {@code fundsxml/examples} repository are real examples.
 *
 * <p>The repository also holds Java/.NET/Python/JavaScript projects (with Maven {@code pom.xml}
 * files) and may hold WebDAV leftovers ({@code .DAV/}). Only FundsXML documents, XSLT stylesheets, Schematron files
 * and XPath/XQuery snippets are kept; an {@code .xml} file counts only when its root element is
 * {@code FundsXML4} (or {@code FundsXML}).
 */
public final class FundsXmlExampleFilter {

    /** What a file of the examples repository is used for. */
    public enum Category { EXAMPLE_XML, XSLT, SCHEMATRON, QUERY, NONE }

    /** Folders holding program code or build output — never examples. */
    private static final Set<String> SKIPPED_FOLDERS = Set.of(
            "java", "dotnet", "csharp", "javascript", "python", "powershell", "cli", "invocation",
            "node_modules", "target", "bin", "obj", "build");

    private static final Set<String> FUNDSXML_ROOTS = Set.of("FundsXML4", "FundsXML");

    private FundsXmlExampleFilter() {
    }

    /**
     * Whether a path (relative to the repository root or the examples cache, {@code /} or
     * {@code \} separators) lies in a folder that never holds examples, or is a {@code pom.xml}.
     */
    public static boolean isSkippedPath(String relativePath) {
        String[] segments = relativePath.replace('\\', '/').split("/");
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i].toLowerCase(Locale.ROOT);
            if (segment.isEmpty()) {
                continue;
            }
            boolean last = i == segments.length - 1;
            if (last) {
                return segment.equals("pom.xml");
            }
            if (segment.startsWith(".") || SKIPPED_FOLDERS.contains(segment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the file is an XML document with a FundsXML root element. Reads only up to the root
     * element (no DTD, no external entities); unreadable or malformed files are not examples.
     */
    public static boolean isFundsXmlDocument(Path xml) {
        try (InputStream in = Files.newInputStream(xml)) {
            XMLStreamReader reader = SecureXmlFactory.createSecureXMLInputFactory().createXMLStreamReader(in);
            try {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                        return FUNDSXML_ROOTS.contains(reader.getLocalName());
                    }
                }
                return false;
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Classifies {@code file}; {@code root} is the folder its relative path is measured from (the
     * unpacked repository or the examples cache).
     */
    public static Category classify(Path root, Path file) {
        if (isSkippedPath(root.relativize(file).toString())) {
            return Category.NONE;
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".xml")) {
            return isFundsXmlDocument(file) ? Category.EXAMPLE_XML : Category.NONE;
        }
        if (name.endsWith(".xsl") || name.endsWith(".xslt")) {
            return Category.XSLT;
        }
        if (name.endsWith(".sch")) {
            return Category.SCHEMATRON;
        }
        if (name.endsWith(".xpath") || name.endsWith(".xq") || name.endsWith(".xquery")
                || name.equals("index.json")) {
            return Category.QUERY;
        }
        return Category.NONE;
    }
}
