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
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.plugin.DocumentValidator;
import org.fxt.freexmltoolkit.plugin.XmlScan;

/**
 * A document validator over rule sets chosen by the root element: the findings appear in the
 * Validation panel, with F8 and in {@code fxt-cli validate}, each on its line, as
 * {@code "<rule id> <message>"}.
 */
public final class RegValidator implements DocumentValidator {

    private static final Logger logger = LogManager.getLogger(RegValidator.class);

    private final String name;
    private final BooleanSupplier enabled;
    private final java.util.function.BiFunction<Root, String, RegRuleSet> rulesByRoot;

    /** The root element of a document: local name and namespace (empty for none). */
    public record Root(String localName, String namespace) {
    }

    /**
     * @param name        the source column of the problems
     * @param enabled     whether the extension is on
     * @param rulesByRoot the rule set of a root element, {@code null} for foreign documents
     */
    public RegValidator(String name, BooleanSupplier enabled, Function<Root, RegRuleSet> rulesByRoot) {
        this(name, enabled, (root, content) -> rulesByRoot.apply(root));
    }

    /**
     * @param rules the rule set of a document by its root element and content (for formats that share a
     *              root element, such as ISO 20022 envelopes), {@code null} for foreign documents
     */
    public RegValidator(String name, BooleanSupplier enabled, java.util.function.BiFunction<Root, String, RegRuleSet> rules) {
        this.name = name;
        this.enabled = enabled;
        this.rulesByRoot = rules;
    }

    @Override
    public String name() {
        return name;
    }

    /** @return the root element, or {@code null} for a broken document */
    public static Root rootOf(String content) {
        if (content == null) {
            return null;
        }
        return XmlScan.rootOf(content).map(r -> new Root(r[0], r[1])).orElse(null);
    }

    @Override
    public boolean accepts(String content, String documentName) {
        if (!enabled.getAsBoolean()) {
            return false;
        }
        Root root = rootOf(content);
        return root != null && rulesByRoot.apply(root, content) != null;
    }

    @Override
    public List<Problem> validate(String content, String documentName) {
        List<Problem> problems = new ArrayList<>();
        Root root = rootOf(content);
        RegRuleSet rules = root == null ? null : rulesByRoot.apply(root, content);
        if (rules == null) {
            return problems;
        }
        try {
            for (RegRuleSet.Finding f : rules.run(content, documentName)) {
                problems.add(new Problem(f.severity(), f.line(), f.ruleId() + " " + f.message()));
            }
        } catch (Exception e) {
            logger.debug("{} checks failed", name, e);
            problems.add(new Problem("error", 0, "The " + name + " checks could not run: " + e.getMessage()));
        }
        return problems;
    }
}
