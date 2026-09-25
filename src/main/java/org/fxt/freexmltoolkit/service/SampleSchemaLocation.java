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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The schema reference ({@code xsi:schemaLocation} / {@code xsi:noNamespaceSchemaLocation})
 * written into a generated sample document.
 *
 * <p>A sample is meant to be saved, moved and shared, so it must never carry an absolute
 * {@code file:} URI of the generating machine. Instead the generators reference the schema
 * <em>relatively</em>: by its bare file name for a document that has no location yet, or
 * relative to the output folder when that is known (batch generation). When such a document
 * is saved for the first time, {@link #relocate(String, File, Path)} turns the bare name into
 * a path relative to the chosen location, so the reference keeps resolving.</p>
 */
public final class SampleSchemaLocation {

    /** Matches the first element start tag (skips the XML declaration, comments and PIs). */
    private static final Pattern ROOT_START_TAG = Pattern.compile("<(?![?!/])[^>]*>");
    /** {@code prefix:noNamespaceSchemaLocation="…"} — any prefix, either quote style. */
    private static final Pattern NO_NAMESPACE_ATTR =
            Pattern.compile("(\\b[\\w.-]+:noNamespaceSchemaLocation\\s*=\\s*)([\"'])([^\"']*)\\2");
    /** {@code prefix:schemaLocation="ns loc ns loc…"} — any prefix, either quote style. */
    private static final Pattern SCHEMA_LOCATION_ATTR =
            Pattern.compile("(\\b[\\w.-]+:schemaLocation\\s*=\\s*)([\"'])([^\"']*)\\2");

    private SampleSchemaLocation() {
    }

    /**
     * The location to write into a freshly generated sample.
     *
     * @param xsdFilePath the schema the sample was generated from (may be unnormalized)
     * @param outputDir   the folder the sample will be written to, or {@code null} when the
     *                    document is opened unsaved in the editor
     * @return the schema's file name when {@code outputDir} is null, otherwise the path from
     *         {@code outputDir} to the schema with forward slashes; an absolute {@code file:} URI
     *         only when the two lie on different roots (drives) and cannot be related
     */
    public static String forSample(String xsdFilePath, Path outputDir) {
        Path xsd = Path.of(xsdFilePath).toAbsolutePath().normalize();
        if (outputDir == null) {
            return xsd.getFileName().toString();
        }
        try {
            Path relative = outputDir.toAbsolutePath().normalize().relativize(xsd);
            return relative.toString().replace(File.separatorChar, '/');
        } catch (IllegalArgumentException differentRoots) {
            return xsd.toUri().toString();
        }
    }

    /**
     * Rewrites a bare-file-name schema reference for a document that is about to be saved
     * into {@code targetDir}: the generators write {@code xsi:noNamespaceSchemaLocation="X.xsd"}
     * (or {@code xsi:schemaLocation="ns X.xsd"}), which only resolves next to the schema.
     * Any reference that is not exactly the schema's file name is left untouched — that is
     * the user's own declaration, not the generator's.
     *
     * @param xml       the document text
     * @param xsd       the schema bound to the document, or {@code null}
     * @param targetDir the folder the document is saved into
     * @return the rewritten document, or empty when nothing needs to (or can) change
     */
    public static Optional<String> relocate(String xml, File xsd, Path targetDir) {
        if (xml == null || xsd == null || targetDir == null) {
            return Optional.empty();
        }
        String name = xsd.getName();
        if (name.isEmpty()) {
            return Optional.empty();
        }
        Path schema = xsd.toPath().toAbsolutePath().normalize();
        Path resolvedFromTarget = targetDir.toAbsolutePath().normalize().resolve(name);
        if (resolvedFromTarget.equals(schema) || isSameFile(resolvedFromTarget, schema)) {
            return Optional.empty(); // saved next to the schema: the bare name is already right
        }
        Matcher root = ROOT_START_TAG.matcher(xml);
        if (!root.find()) {
            return Optional.empty();
        }
        String startTag = root.group();
        String replacement = forSample(schema.toString(), targetDir);
        String rewritten = rewriteNoNamespace(startTag, name, replacement);
        rewritten = rewriteSchemaLocationPairs(rewritten, name, replacement);
        if (rewritten.equals(startTag)) {
            return Optional.empty();
        }
        return Optional.of(xml.substring(0, root.start()) + rewritten + xml.substring(root.end()));
    }

    private static String rewriteNoNamespace(String startTag, String name, String replacement) {
        Matcher m = NO_NAMESPACE_ATTR.matcher(startTag);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = m.group(3).trim().equals(name) ? replacement : m.group(3);
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + m.group(2) + value + m.group(2)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String rewriteSchemaLocationPairs(String startTag, String name, String replacement) {
        Matcher m = SCHEMA_LOCATION_ATTR.matcher(startTag);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String[] tokens = m.group(3).trim().split("\\s+");
            // pairs of (namespace, location): only the location halves may be the bare name
            for (int i = 1; i < tokens.length; i += 2) {
                if (tokens[i].equals(name)) {
                    tokens[i] = replacement;
                }
            }
            String value = String.join(" ", tokens);
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + m.group(2) + value + m.group(2)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static boolean isSameFile(Path a, Path b) {
        try {
            return Files.exists(a) && Files.exists(b) && Files.isSameFile(a, b);
        } catch (Exception e) {
            return false;
        }
    }
}
