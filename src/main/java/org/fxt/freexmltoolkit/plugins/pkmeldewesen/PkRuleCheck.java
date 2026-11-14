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
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.SaxonResourceAccess;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.Xslt30Transformer;
import net.sf.saxon.s9api.XsltExecutable;

/**
 * Runs the FMA validation rules of the quarterly report (PK-QMV, module pfq) — the rules of the
 * validation workbook plus the additional plausibility checks — with the bundled rule stylesheet.
 *
 * <p>The stylesheet evaluates every check once into a small result tree ({@code results/vrg/grp/…/c});
 * {@link #run} reads that tree and places each finding on its line with {@link PkLocator},
 * {@link #htmlReport} renders the stylesheet's HTML report. Only the quarterly report has rules so
 * far ({@link #supports}).</p>
 */
public final class PkRuleCheck {

    private static final Processor PROCESSOR = SaxonResourceAccess.restrictedProcessor();
    private static volatile XsltExecutable pfqRules;

    private static final Pattern VRG_ROW = Pattern.compile("VRG (\\S+) · (R\\d{3}-\\d+)");

    private PkRuleCheck() {
    }

    /**
     * The parameters of a run.
     *
     * @param tolerance        amount differences above this value are errors (EUR)
     * @param toleranceWarning amount differences above this value are warnings (EUR)
     * @param lookThroughAsset not looked-through share of one fund in % of the VRG (warning above)
     * @param lookThroughTotal not looked-through share of the VRG in % (warning above)
     * @param maxLookThroughRows look-through findings listed per rule and VRG in the report
     * @param additional       whether the additional plausibility checks (INT_*) run
     */
    public record Options(BigDecimal tolerance, BigDecimal toleranceWarning, BigDecimal lookThroughAsset,
                          BigDecimal lookThroughTotal, int maxLookThroughRows, boolean additional) {

        /** @return the stylesheet's defaults */
        public static Options defaults() {
            return new Options(new BigDecimal("3"), new BigDecimal("0.99"), new BigDecimal("2"),
                    new BigDecimal("10"), 25, true);
        }

        /** @return a copy with the additional checks switched on or off */
        public Options withAdditional(boolean on) {
            return new Options(tolerance, toleranceWarning, lookThroughAsset, lookThroughTotal, maxLookThroughRows, on);
        }
    }

    /**
     * A finding (a check whose status is error or warning).
     *
     * @param rule     the rule (never {@code null}; unknown ids get a stand-in)
     * @param status   {@code E} or {@code W}
     * @param vrg      the VRG number, or {@code gesamt} for the checks across VRG and of the header
     * @param object   what the finding is about (row code, asset id, …); empty for the VRG as a whole
     * @param detail   the stylesheet's detail text, or the amounts of a comparison; may be empty
     * @param line     1-based line in the report, 0 when unknown
     */
    public record Finding(PkRuleCatalog.Rule rule, String status, String vrg, String object, String detail, int line) {

        /** @return {@code error}, {@code warning} or {@code info} */
        public String severity() {
            return switch (status) {
                case "E" -> "error";
                case "W" -> "warning";
                default -> "info";
            };
        }

        /** @return one line for the problem list: code, name, VRG, object and detail */
        public String message() {
            StringBuilder text = new StringBuilder(rule.code()).append(' ').append(rule.name()).append(" – ");
            text.append("gesamt".equals(vrg) ? I18n.t("PkRuleCheck.across-vrg") : "VRG " + vrg);
            if (!object.isEmpty()) {
                text.append(" · ").append(object);
            }
            if (!detail.isEmpty()) {
                text.append(": ").append(detail);
            } else if (!rule.message().isEmpty()) {
                text.append(": ").append(rule.message());
            }
            return text.toString();
        }
    }

    /** @return whether the module has rules (only the quarterly report so far) */
    public static boolean supports(PkModule module) {
        return module == PkModule.PFQ;
    }

    /**
     * Runs the rules.
     *
     * @param xml          the report
     * @param documentName the file name or path (the header checks compare it with the report), may be {@code null}
     * @return the findings in report order
     * @throws SaxonApiException when the report cannot be checked (not well-formed, …)
     */
    public static List<Finding> run(String xml, String documentName, Options options) throws SaxonApiException, IOException {
        XdmDestination destination = new XdmDestination();
        Xslt30Transformer transformer = stylesheet().load30();
        parameters(transformer, options, true);
        transformer.transform(source(xml, documentName), destination);
        return findings(destination.getXdmNode(), PkLocator.of(xml), PkRuleCatalog.pfq());
    }

    /** @return the HTML report of the rules (German, like the FMA rule texts) */
    public static String htmlReport(String xml, String documentName, Options options) throws SaxonApiException, IOException {
        Xslt30Transformer transformer = stylesheet().load30();
        parameters(transformer, options, false);
        StringWriter out = new StringWriter();
        Serializer serializer = PROCESSOR.newSerializer(out);
        transformer.transform(source(xml, documentName), serializer);
        return out.toString();
    }

    private static void parameters(Xslt30Transformer transformer, Options options, boolean resultsOnly) throws SaxonApiException {
        java.util.Map<QName, net.sf.saxon.s9api.XdmValue> params = new java.util.HashMap<>();
        params.put(new QName("resultsOnly"), new XdmAtomicValue(resultsOnly));
        params.put(new QName("internal"), new XdmAtomicValue(options.additional()));
        params.put(new QName("tolerance"), new XdmAtomicValue(options.tolerance()));
        params.put(new QName("toleranceWarning"), new XdmAtomicValue(options.toleranceWarning()));
        params.put(new QName("lookThroughLimitAsset"), new XdmAtomicValue(options.lookThroughAsset()));
        params.put(new QName("lookThroughLimitTotal"), new XdmAtomicValue(options.lookThroughTotal()));
        params.put(new QName("maxLookThroughRows"), new XdmAtomicValue(options.maxLookThroughRows()));
        transformer.setStylesheetParameters(params);
    }

    private static StreamSource source(String xml, String documentName) {
        StreamSource source = new StreamSource(new StringReader(xml));
        source.setSystemId(systemId(documentName));
        return source;
    }

    /** @return a URI whose last segment is the document's file name (the header checks read it) */
    static String systemId(String documentName) {
        String name = documentName == null || documentName.isBlank() ? "report.xml" : documentName;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        try {
            return new URI("file", null, "/" + name, null).toASCIIString();
        } catch (URISyntaxException e) {
            return "file:/report.xml";
        }
    }

    private static XsltExecutable stylesheet() throws SaxonApiException, IOException {
        XsltExecutable executable = pfqRules;
        if (executable == null) {
            synchronized (PkRuleCheck.class) {
                if (pfqRules == null) {
                    try (InputStream in = PkRuleCheck.class.getResourceAsStream(PkRuleCatalog.PFQ_RULES)) {
                        if (in == null) {
                            throw new IOException("missing resource " + PkRuleCatalog.PFQ_RULES);
                        }
                        StreamSource source = new StreamSource(in);
                        source.setSystemId(PkRuleCheck.class.getResource(PkRuleCatalog.PFQ_RULES).toExternalForm());
                        pfqRules = PROCESSOR.newXsltCompiler().compile(source);
                    }
                }
                executable = pfqRules;
            }
        }
        return executable;
    }

    /** Reads the findings out of the result tree. */
    static List<Finding> findings(XdmNode results, PkLocator locator, PkRuleCatalog catalog) {
        List<Finding> findings = new ArrayList<>();
        for (XdmNode root : results.children()) {
            if (root.getNodeKind() != XdmNodeKind.ELEMENT) {
                continue;
            }
            for (XdmNode vrg : elements(root, "vrg")) {
                String nr = attr(vrg, "nr");
                for (XdmNode grp : elements(vrg, "grp")) {
                    String group = attr(grp, "id");
                    for (XdmNode child : elements(grp, null)) {
                        String name = child.getNodeName().getLocalName();
                        if ("c".equals(name)) {
                            add(findings, child, null, nr, group, locator, catalog);
                        } else if ("row".equals(name)) {
                            for (XdmNode c : elements(child, "c")) {
                                add(findings, c, child, nr, group, locator, catalog);
                            }
                        }
                    }
                }
            }
        }
        return findings;
    }

    private static void add(List<Finding> findings, XdmNode c, XdmNode row, String vrg, String group,
                            PkLocator locator, PkRuleCatalog catalog) {
        String status = attr(c, "s");
        // "I" is a key figure without a limit (shown in the report), not a finding
        if (!("E".equals(status) || "W".equals(status))) {
            return;
        }
        String id = attr(c, "id");
        PkRuleCatalog.Rule rule = catalog.rule(id);
        if (rule == null) {
            rule = new PkRuleCatalog.Rule(id, "", group, status, id, "");
        }
        String key = attr(c, "key");
        String object = row != null ? attr(row, "key") : key;
        if (row != null && !key.isEmpty() && !key.equals(object)) {
            object = object + " · " + key;
        }
        findings.add(new Finding(rule, status, vrg, object, detail(c), line(c, row, vrg, group, locator)));
    }

    /** @return the detail text of a check: its {@code d} attribute, or actual/expected/difference */
    static String detail(XdmNode c) {
        String d = attr(c, "d");
        String unit = attr(c, "u");
        if (!attr(c, "soll").isEmpty()) {
            String amounts = I18n.t("PkRuleCheck.amounts", format(attr(c, "ist"), unit), format(attr(c, "soll"), unit),
                    format(attr(c, "diff"), unit));
            return d.isEmpty() ? amounts : d + " (" + amounts + ")";
        }
        if (d.isEmpty() && !attr(c, "ist").isEmpty() && "%".equals(unit)) {
            return format(attr(c, "ist"), unit);
        }
        return d;
    }

    /** @return a number in the German format of the FMA texts ({@code 1.234,56}, {@code 12,5 %}) */
    static String format(String value, String unit) {
        BigDecimal number;
        try {
            number = new BigDecimal(value.strip());
        } catch (NumberFormatException e) {
            return value;
        }
        DecimalFormat format = new DecimalFormat("n".equals(unit) ? "#,##0" : "#,##0.00",
                DecimalFormatSymbols.getInstance(Locale.GERMANY));
        String text = format.format(number);
        if ("%".equals(unit)) {
            return text + " %";
        }
        return "€".equals(unit) ? text + " €" : text;
    }

    /** @return the line a finding belongs to; the template, then the root when the object is not found */
    static int line(XdmNode c, XdmNode row, String vrg, String group, PkLocator locator) {
        String key = attr(c, "key");
        int line = 0;
        if ("gesamt".equals(vrg)) {
            Matcher m = VRG_ROW.matcher(key);
            if (m.matches()) {
                line = factOrTemplate(locator, m.group(1), m.group(2));
            } else if (PkLocator.templateOfRow(key) != null) {
                line = factOrTemplate(locator, "0", key);
            }
            return line > 0 ? line : locator.rootLine();
        }
        if (row != null) {
            String rowKey = attr(row, "key");
            if ("lt".equals(group)) {
                String fund = attr(row, "fund");
                line = fund.isEmpty() ? 0 : locator.lookThrough(vrg, fund, rowKey);
                if (line == 0) {
                    line = locator.entry("PK.006.03", vrg, fund.isEmpty() ? rowKey : fund.split(", ")[0]);
                }
                if (line == 0) {
                    line = locator.template("PK.006.03", vrg);
                }
            } else {
                String tpl = "true".equals(attr(row, "orphan")) ? "PK.006.02.01.02" : "PK.006.02.01.01";
                line = locator.entry(tpl, vrg, rowKey);
                if (line == 0) {
                    line = locator.template(tpl, vrg);
                }
            }
        } else if (PkLocator.templateOfRow(key) != null) {
            line = factOrTemplate(locator, vrg, key);
        }
        if (line == 0) {
            line = locator.template(templateOfGroup(group), vrg);
        }
        if (line == 0) {
            line = locator.template("PK.000.01", vrg);
        }
        return line > 0 ? line : locator.rootLine();
    }

    private static int factOrTemplate(PkLocator locator, String vrg, String row) {
        String tpl = PkLocator.templateOfRow(row);
        int line = locator.fact(tpl, vrg, row);
        return line > 0 ? line : locator.template(tpl, vrg);
    }

    private static String templateOfGroup(String group) {
        return switch (group) {
            case "fb800", "abgl", "alloc" -> "PK.800.01";
            case "fb900" -> "PK.900.01";
            case "assets" -> "PK.006.02.01.01";
            case "lt" -> "PK.006.03";
            default -> "PK.000.01";
        };
    }

    private static Iterable<XdmNode> elements(XdmNode parent, String name) {
        List<XdmNode> list = new ArrayList<>();
        for (XdmNode child : parent.children()) {
            if (child.getNodeKind() == XdmNodeKind.ELEMENT
                    && (name == null || name.equals(child.getNodeName().getLocalName()))) {
                list.add(child);
            }
        }
        return list;
    }

    private static String attr(XdmNode node, String name) {
        String value = node.getAttributeValue(new QName(name));
        return value == null ? "" : value;
    }
}
