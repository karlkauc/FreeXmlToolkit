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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.plugin.DocumentValidator;

/**
 * The FMA rules of the quarterly report as a document validator: their findings appear in the
 * Validation panel, with F8 and in {@code fxt-cli validate}, each on its line.
 */
final class PkRuleValidator implements DocumentValidator {

    private static final Logger logger = LogManager.getLogger(PkRuleValidator.class);

    private final Supplier<Boolean> enabled;
    private final Supplier<PkRuleCheck.Options> options;

    /** The validator over the extension's settings. */
    PkRuleValidator() {
        this(PkSettings::isEnabled, PkSettings::ruleOptions);
    }

    PkRuleValidator(Supplier<Boolean> enabled, Supplier<PkRuleCheck.Options> options) {
        this.enabled = enabled;
        this.options = options;
    }

    @Override
    public String name() {
        return "PK-Meldewesen";
    }

    /**
     * @return the finding as a problem with its FMA code, the category of its rule group and what it is about: the
     *         position (asset key and title) or, for checks of a whole risk community, the VRG
     */
    static Problem problem(PkRuleCheck.Finding finding) {
        Problem problem = new Problem(finding.severity(), finding.line(), finding.message())
                .withRule(finding.rule().code())
                .withCategory(org.fxt.freexmltoolkit.controls.shell.editor.ProblemCategory
                        .ofFmaGroup(finding.rule().group(), finding.rule().additional()).name());
        if (finding.asset() != null && !finding.asset().isBlank()) {
            return problem.withSubject("ASSET", finding.asset(), finding.title());
        }
        if (finding.vrg() != null && !finding.vrg().isBlank()) {
            return problem.withSubject("RECORD", "gesamt".equals(finding.vrg()) ? "VRG –" : "VRG " + finding.vrg(), null);
        }
        return problem;
    }

    @Override
    public boolean accepts(String content, String documentName) {
        if (!enabled.get()) {
            return false;
        }
        PkReport report = PkReport.detect(content);
        return report != null && PkRuleCheck.supports(report.module());
    }

    @Override
    public List<Problem> validate(String content, String documentName) {
        List<Problem> problems = new ArrayList<>();
        try {
            for (PkRuleCheck.Finding finding : PkRuleCheck.run(content, documentName, options.get())) {
                problems.add(problem(finding));
            }
        } catch (Exception e) {
            logger.debug("PK rule check failed", e);
            problems.add(new Problem("error", 0, "The FMA rules could not be run: " + e.getMessage()));
        }
        return problems;
    }
}
