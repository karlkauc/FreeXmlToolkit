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

package org.fxt.freexmltoolkit.plugins.regreport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

/**
 * The rules of a reporting format: the paths of its record elements and the rules run on the
 * document header and on every record, in one streaming pass ({@link RecordReader}).
 */
public final class RegRuleSet {

    /** Severity of a finding: the report is rejected. */
    public static final String ERROR = "error";
    /** Severity of a finding: the report is accepted with a warning. */
    public static final String WARNING = "warning";

    /**
     * A finding.
     *
     * @param ruleId   the rule's id ({@code FMA-AIF-22}, {@code ESMA-MMF-130})
     * @param severity {@link #ERROR} or {@link #WARNING}
     * @param line     1-based line
     * @param message  what is wrong, with the field's id and name where known
     */
    public record Finding(String ruleId, String severity, int line, String message) {
    }

    /** What a rule sees besides the record: the root element and the document's name. */
    public record Context(RecordNode root, String documentName) {
    }

    /** A rule on one record (or on the header). */
    @FunctionalInterface
    public interface Rule {
        void check(RecordNode record, Context context, Findings out);
    }

    /** Collects the findings of a run. */
    public static final class Findings {
        private final List<Finding> list = new ArrayList<>();

        /** Adds an error. */
        public void error(String ruleId, int line, String message) {
            list.add(new Finding(ruleId, ERROR, line, message));
        }

        /** Adds a warning. */
        public void warning(String ruleId, int line, String message) {
            list.add(new Finding(ruleId, WARNING, line, message));
        }

        /** Adds a finding of the given severity. */
        public void add(String ruleId, String severity, int line, String message) {
            list.add(new Finding(ruleId, severity, line, message));
        }

        /** @return the findings so far */
        public List<Finding> list() {
            return Collections.unmodifiableList(list);
        }
    }

    private final Set<String> recordPaths;
    private final List<Rule> headerRules = new ArrayList<>();
    private final List<Rule> recordRules = new ArrayList<>();

    /** @param recordPaths the paths of the record elements from the root ({@code AIFReportingInfo/AIFRecordInfo}) */
    public RegRuleSet(String... recordPaths) {
        this.recordPaths = new LinkedHashSet<>(List.of(recordPaths));
    }

    /** Adds a rule on the root element (its attributes; the records are not its children). */
    public RegRuleSet header(Rule rule) {
        headerRules.add(rule);
        return this;
    }

    /** Adds a rule on every record. */
    public RegRuleSet record(Rule rule) {
        recordRules.add(rule);
        return this;
    }

    /**
     * @return the findings in document order
     * @throws XMLStreamException when the document is not well-formed
     */
    public List<Finding> run(String xml, String documentName) throws XMLStreamException {
        Findings out = new Findings();
        RecordNode[] root = new RecordNode[1];
        RecordReader.read(xml, recordPaths, header -> {
            root[0] = header;
            Context context = new Context(header, documentName);
            for (Rule rule : headerRules) {
                rule.check(header, context, out);
            }
        }, record -> {
            Context context = new Context(root[0], documentName);
            for (Rule rule : recordRules) {
                rule.check(record, context, out);
            }
        });
        List<Finding> sorted = new ArrayList<>(out.list());
        sorted.sort(java.util.Comparator.comparingInt(Finding::line));
        return sorted;
    }
}
