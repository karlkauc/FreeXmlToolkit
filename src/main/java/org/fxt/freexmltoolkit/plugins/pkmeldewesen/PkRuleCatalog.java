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
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * The rule catalogue of the bundled QMV rule stylesheet: id, FMA code, group, severity, name and
 * message of every rule, read once from the {@code rule} elements of the stylesheet itself so the
 * two never disagree.
 */
public final class PkRuleCatalog {

    /** The bundled rule stylesheet of the quarterly report (module pfq). */
    public static final String PFQ_RULES = "/plugins/pkmeldewesen/rules/pk-pfq-rules.xslt";

    /** Prefix of the additional plausibility checks, which are not FMA rules. */
    public static final String ADDITIONAL_PREFIX = "INT_";

    /**
     * A rule.
     *
     * @param id       stylesheet id ({@code PK_207}, {@code PBV38}, {@code INT_LT2})
     * @param fmaCode  the FMA validation code ({@code VDL010}, {@code VA00033}); empty when there is none
     * @param group    result group ({@code stamm}, {@code fb800}, {@code abgl}, {@code assets}, {@code lt},
     *                 {@code alloc}, {@code fb900}, {@code gesamt})
     * @param severity {@code E}, {@code W} or {@code I}
     * @param name     short name
     * @param message  the FMA message text
     */
    public record Rule(String id, String fmaCode, String group, String severity, String name, String message) {

        /** @return the code shown to the user: the FMA code when there is one, else the id */
        public String code() {
            return fmaCode.isEmpty() ? id : fmaCode;
        }

        /** @return whether this is one of the additional plausibility checks */
        public boolean additional() {
            return id.startsWith(ADDITIONAL_PREFIX);
        }
    }

    private static volatile PkRuleCatalog pfq;

    private final Map<String, Rule> rules;

    private PkRuleCatalog(Map<String, Rule> rules) {
        this.rules = Collections.unmodifiableMap(rules);
    }

    /** @return the catalogue of the quarterly report's rules */
    public static PkRuleCatalog pfq() {
        PkRuleCatalog catalog = pfq;
        if (catalog == null) {
            synchronized (PkRuleCatalog.class) {
                if (pfq == null) {
                    pfq = load(PFQ_RULES);
                }
                catalog = pfq;
            }
        }
        return catalog;
    }

    static PkRuleCatalog load(String resource) {
        Map<String, Rule> rules = new LinkedHashMap<>();
        try (InputStream in = PkRuleCatalog.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing resource " + resource);
            }
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            XMLStreamReader reader = factory.createXMLStreamReader(in);
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT && "rule".equals(reader.getLocalName())
                        && reader.getAttributeValue(null, "id") != null) {
                    String id = reader.getAttributeValue(null, "id");
                    String old = attr(reader, "old");
                    rules.putIfAbsent(id, new Rule(id, "extra".equals(old) ? "" : old, attr(reader, "grp"),
                            attr(reader, "sev"), attr(reader, "name"), attr(reader, "msg")));
                }
            }
            reader.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (XMLStreamException e) {
            throw new IllegalStateException("rule catalogue unreadable: " + e.getMessage(), e);
        }
        return new PkRuleCatalog(rules);
    }

    private static String attr(XMLStreamReader reader, String name) {
        String value = reader.getAttributeValue(null, name);
        return value == null ? "" : value;
    }

    /** @return the rule, or {@code null} for an unknown id */
    public Rule rule(String id) {
        return rules.get(id);
    }

    /** @return all rules in catalogue order */
    public Collection<Rule> rules() {
        return rules.values();
    }
}
