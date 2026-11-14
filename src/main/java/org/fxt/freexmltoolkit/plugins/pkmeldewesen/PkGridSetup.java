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

package org.fxt.freexmltoolkit.plugins.pkmeldewesen;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.service.SaxonResourceAccess;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.Xslt30Transformer;
import net.sf.saxon.s9api.XsltExecutable;

/**
 * Grid formulas and saved filters for the quarterly report: adds them as {@code fxt-formula} /
 * {@code fxt-filter} processing instructions (every table shows sums and checks with their live
 * result in the Graphic view; no formula stores a result, so no data changes) and removes them
 * again before the report is submitted. Both are idempotent.
 */
public final class PkGridSetup {

    static final String ADD = "/plugins/pkmeldewesen/grid/pk-pfq-grid-add.xslt";
    static final String REMOVE = "/plugins/pkmeldewesen/grid/pk-pfq-grid-remove.xslt";

    private static final Processor PROCESSOR = SaxonResourceAccess.restrictedProcessor();
    private static final Map<String, XsltExecutable> COMPILED = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Pattern ANNOTATION = Pattern.compile("<\\?fxt-(formula|filter)[\\s?]");

    private PkGridSetup() {
    }

    /**
     * @param formulas whether the formulas are added
     * @param filters  whether the saved filters are added
     * @return the report with the grid annotations (existing ones are replaced)
     * @throws IOException when the report is not well-formed or the stylesheet fails
     */
    public static String add(String xml, boolean formulas, boolean filters) throws IOException {
        return run(ADD, xml, Map.of(new QName("formulas"), new XdmAtomicValue(formulas),
                new QName("filters"), new XdmAtomicValue(filters)));
    }

    /** @return the report without grid formulas, filters and stored formula results */
    public static String remove(String xml) throws IOException {
        return run(REMOVE, xml, Map.of());
    }

    /** @return whether the text has grid formulas or filters (they must not be submitted) */
    public static boolean hasAnnotations(String xml) {
        return xml != null && ANNOTATION.matcher(xml).find();
    }

    private static String run(String resource, String xml, Map<QName, XdmAtomicValue> params) throws IOException {
        try {
            Xslt30Transformer transformer = compiled(resource).load30();
            transformer.setStylesheetParameters(params);
            StringWriter out = new StringWriter();
            transformer.transform(new StreamSource(new StringReader(xml)), transformer.newSerializer(out));
            return out.toString();
        } catch (SaxonApiException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private static XsltExecutable compiled(String resource) throws SaxonApiException, IOException {
        XsltExecutable executable = COMPILED.get(resource);
        if (executable == null) {
            try (InputStream in = PkGridSetup.class.getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IOException(resource + " is missing");
                }
                executable = PROCESSOR.newXsltCompiler().compile(new StreamSource(in));
                COMPILED.put(resource, executable);
            }
        }
        return executable;
    }
}
