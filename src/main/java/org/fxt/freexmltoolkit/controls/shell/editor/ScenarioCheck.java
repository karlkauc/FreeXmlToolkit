package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.service.XmlStructuralDiff;
import org.fxt.freexmltoolkit.service.project.FxtProject;

/**
 * Regression check of a project scenario ("golden file"): runs the scenario with
 * {@link ScenarioRunner} and compares its result with the scenario's {@code expected} file. XML results
 * are compared by structure ({@link XmlStructuralDiff}, with the scenario's
 * {@link FxtProject.CompareSettings}: ignored XPaths, placeholders in the expected file, numeric
 * tolerance, …); other results line by line. UI-free; used by the Transform panel, the Project menu
 * and {@code fxt-cli scenario --check}.
 */
public final class ScenarioCheck {

    /** The outcome of a check. */
    public enum Status {
        /** The result matches the expected file. */
        PASSED,
        /** The result differs from the expected file. */
        FAILED,
        /** The scenario names no expected file, or the file does not exist yet. */
        NO_EXPECTED,
        /** The scenario could not run, or its result cannot be compared (PDF). */
        ERROR
    }

    /** Text results: at most this many differing lines are listed. */
    static final int MAX_LINE_DIFFERENCES = 20;

    /**
     * A check's outcome.
     *
     * @param status      what came out
     * @param differences the differences (expected = left, result = right); empty unless {@link Status#FAILED}
     * @param actual      the scenario's result text, for {@link #accept} and a diff view; {@code null} on error
     * @param expected    the expected file, or {@code null} when the scenario names none
     * @param message     why it is {@link Status#ERROR} or {@link Status#NO_EXPECTED}, else {@code null}
     */
    public record Result(Status status, List<XmlStructuralDiff.Difference> differences, String actual,
                         Path expected, String message) {

        public boolean passed() {
            return status == Status.PASSED;
        }

        /** @return a one-line summary, e.g. {@code "3 differences"} */
        public String summary() {
            return switch (status) {
                case PASSED -> "matches the expected result";
                case FAILED -> differences.size() == 1 ? "1 difference" : differences.size() + " differences";
                case NO_EXPECTED, ERROR -> message;
            };
        }
    }

    /** One file's outcome of {@link #checkAll}. */
    public record FileResult(Path file, Result result) {
    }

    private ScenarioCheck() {
    }

    /**
     * Runs a scenario for one file (or for no file: {@code file == null}) and compares its result
     * with the expected file. Writes the scenario's output as a normal run does.
     */
    public static Result check(FxtProject project, FxtProject.Scenario scenario, Path file) {
        FxtProject.Scenario prepared = file == null ? scenario : project.scenarioForFile(scenario, file);
        if (!prepared.hasExpected()) {
            return new Result(Status.NO_EXPECTED, List.of(), null, null,
                    "The scenario names no expected result.");
        }
        if (prepared.isPdf()) {
            return new Result(Status.ERROR, List.of(), null, null, "PDF results cannot be compared.");
        }
        ScenarioRunner.Result run = ScenarioRunner.run(project, prepared);
        Path expected = project.resolve(prepared.expected);
        if (!run.ok()) {
            return new Result(Status.ERROR, List.of(), null, expected, run.error());
        }
        String actual = run.text() == null ? "" : run.text();
        if (!Files.isRegularFile(expected)) {
            return new Result(Status.NO_EXPECTED, List.of(), actual, expected,
                    "The expected result " + prepared.expected + " does not exist yet; accept this result to create it.");
        }
        String expectedText;
        try {
            expectedText = Files.readString(expected, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new Result(Status.ERROR, List.of(), actual, expected,
                    "Cannot read " + prepared.expected + ": " + e.getMessage());
        }
        return compare(expectedText, actual, expected,
                FxtProject.CompareSettings.toOptions(prepared.compare));
    }

    /**
     * Compares an expected text with a result: by structure when both are XML, else line by line.
     */
    static Result compare(String expectedText, String actual, Path expected, XmlStructuralDiff.Options options) {
        if (XmlStructuralDiff.looksLikeXml(expectedText) && XmlStructuralDiff.looksLikeXml(actual)) {
            XmlStructuralDiff.Result diff = XmlStructuralDiff.compare(expectedText, actual, options);
            if (!diff.compared()) {
                String why = diff.leftError() != null ? "The expected result is not well-formed: " + diff.leftError()
                        : "The result is not well-formed: " + diff.rightError();
                return new Result(Status.ERROR, List.of(), actual, expected, why);
            }
            return new Result(diff.equivalent() ? Status.PASSED : Status.FAILED, diff.differences(), actual,
                    expected, null);
        }
        List<XmlStructuralDiff.Difference> lines = lineDifferences(expectedText, actual);
        return new Result(lines.isEmpty() ? Status.PASSED : Status.FAILED, lines, actual, expected, null);
    }

    /** Text results: the differing lines (line endings and trailing whitespace do not count). */
    static List<XmlStructuralDiff.Difference> lineDifferences(String expected, String actual) {
        String[] left = expected.strip().split("\\R", -1);
        String[] right = actual.strip().split("\\R", -1);
        List<XmlStructuralDiff.Difference> differences = new ArrayList<>();
        int max = Math.max(left.length, right.length);
        for (int i = 0; i < max && differences.size() < MAX_LINE_DIFFERENCES; i++) {
            String l = i < left.length ? left[i].stripTrailing() : null;
            String r = i < right.length ? right[i].stripTrailing() : null;
            if (l != null && l.equals(r)) {
                continue;
            }
            XmlStructuralDiff.Change change = l == null ? XmlStructuralDiff.Change.ADDED
                    : r == null ? XmlStructuralDiff.Change.REMOVED : XmlStructuralDiff.Change.CHANGED;
            differences.add(new XmlStructuralDiff.Difference(change, XmlStructuralDiff.NodeKind.DOCUMENT,
                    "Line " + (i + 1), "", "", l == null ? "" : l, r == null ? "" : r,
                    l == null ? 0 : i + 1, r == null ? 0 : i + 1));
        }
        return differences;
    }

    /**
     * Checks a scenario for several files, one after the other (on the calling thread); a scenario
     * that does not take a file is checked once ({@code file == null}).
     *
     * @param progress  called with the number of checks done (may be {@code null})
     * @param cancelled polled before each file (may be {@code null})
     */
    public static List<FileResult> checkAll(FxtProject project, FxtProject.Scenario scenario, List<Path> files,
                                            java.util.function.IntConsumer progress,
                                            java.util.function.BooleanSupplier cancelled) {
        List<FileResult> results = new ArrayList<>();
        List<Path> targets = scenario.takesFile() ? files : java.util.Collections.singletonList(null);
        for (Path file : targets) {
            if (cancelled != null && cancelled.getAsBoolean()) {
                break;
            }
            results.add(new FileResult(file, check(project, scenario, file)));
            if (progress != null) {
                progress.accept(results.size());
            }
        }
        return results;
    }

    /**
     * Makes a result the expected one: writes {@code actual} to the scenario's expected file (inside
     * the project folder only). Placeholders of the previous expected file ({@code ${xmlunit.…}} as an
     * element's text or an attribute value) are kept where the same element is still there, so a
     * timestamp or an ID does not turn into a fixed value (see {@link #keepPlaceholders}).
     *
     * @return the file written
     * @throws IOException when the scenario names no expected file, it lies outside the project, or writing fails
     */
    public static Path accept(FxtProject project, FxtProject.Scenario scenario, Path file, String actual)
            throws IOException {
        FxtProject.Scenario prepared = file == null ? scenario : project.scenarioForFile(scenario, file);
        if (!prepared.hasExpected()) {
            throw new IOException("The scenario " + scenario.name + " names no expected result.");
        }
        Path target = project.resolveWritable(prepared.expected);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String text = actual == null ? "" : actual;
        if (Files.isRegularFile(target)) {
            text = keepPlaceholders(Files.readString(target, StandardCharsets.UTF_8), text);
        }
        Files.writeString(target, text, StandardCharsets.UTF_8);
        return target;
    }

    private static final String PLACEHOLDER = "${xmlunit.";

    /**
     * The new expected text with the placeholders of the old one carried over: each element of the old
     * expected document is found in the new one by its path (the position among element children,
     * checked by namespace and local name); an attribute value or the text of an element without
     * element children that holds a placeholder is written there again. When neither text holds XML,
     * or the old one has no placeholder, the new text is returned unchanged.
     */
    static String keepPlaceholders(String oldExpected, String actual) {
        if (oldExpected == null || !oldExpected.contains(PLACEHOLDER)
                || !XmlStructuralDiff.looksLikeXml(oldExpected) || !XmlStructuralDiff.looksLikeXml(actual)) {
            return actual;
        }
        try {
            var builder = org.fxt.freexmltoolkit.util.SecureXmlFactory.createSecureDocumentBuilder(true);
            org.w3c.dom.Document old = builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(oldExpected)));
            org.w3c.dom.Document fresh = builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(actual)));
            if (!carry(old.getDocumentElement(), fresh.getDocumentElement())) {
                return actual;
            }
            var transformer = org.fxt.freexmltoolkit.util.SecureXmlFactory.createSecureTransformerFactory().newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8");
            if (!actual.stripLeading().startsWith("<?xml")) {
                transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
            }
            java.io.StringWriter out = new java.io.StringWriter();
            transformer.transform(new javax.xml.transform.dom.DOMSource(fresh), new javax.xml.transform.stream.StreamResult(out));
            String text = out.toString();
            int declarationEnd = text.startsWith("<?xml") ? text.indexOf("?>") + 2 : -1;
            if (declarationEnd > 1 && declarationEnd < text.length() && text.charAt(declarationEnd) != '\n') {
                text = text.substring(0, declarationEnd) + "\n" + text.substring(declarationEnd);
            }
            return actual.endsWith("\n") && !text.endsWith("\n") ? text + "\n" : text;
        } catch (Exception e) {
            return actual;   // keep the result as it is rather than fail the acceptance
        }
    }

    /** Copies placeholders from {@code from} to the matching nodes below {@code to}; @return whether any was copied */
    private static boolean carry(org.w3c.dom.Element from, org.w3c.dom.Element to) {
        if (to == null || !java.util.Objects.equals(from.getNamespaceURI(), to.getNamespaceURI())
                || !java.util.Objects.equals(from.getLocalName(), to.getLocalName())) {
            return false;
        }
        boolean copied = false;
        var attributes = from.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            org.w3c.dom.Attr attribute = (org.w3c.dom.Attr) attributes.item(i);
            if (attribute.getValue().contains(PLACEHOLDER)) {
                org.w3c.dom.Attr target = to.getAttributeNodeNS(attribute.getNamespaceURI(),
                        attribute.getLocalName() != null ? attribute.getLocalName() : attribute.getName());
                if (target != null) {
                    target.setValue(attribute.getValue());
                    copied = true;
                } else if (attribute.getValue().strip().equals("${xmlunit.ignore}")) {
                    // an ignored attribute that is missing now: keep it ignored
                    to.setAttributeNS(attribute.getNamespaceURI(), attribute.getName(), attribute.getValue());
                    copied = true;
                }
            }
        }
        List<org.w3c.dom.Element> fromChildren = elements(from);
        List<org.w3c.dom.Element> toChildren = elements(to);
        if (fromChildren.isEmpty() && toChildren.isEmpty() && from.getTextContent().contains(PLACEHOLDER)) {
            to.setTextContent(from.getTextContent().strip());
            return true;
        }
        for (int i = 0; i < fromChildren.size() && i < toChildren.size(); i++) {
            copied |= carry(fromChildren.get(i), toChildren.get(i));
        }
        return copied;
    }

    private static List<org.w3c.dom.Element> elements(org.w3c.dom.Element parent) {
        List<org.w3c.dom.Element> list = new ArrayList<>();
        for (org.w3c.dom.Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof org.w3c.dom.Element e) {
                list.add(e);
            }
        }
        return list;
    }
}
