package org.fxt.freexmltoolkit.cli;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * A JUnit XML report (the format CI servers read): one {@code testsuite} with test cases that passed,
 * failed, ran into an error or were skipped.
 */
final class JUnitReport {

    /** The outcome of one test case. */
    enum Outcome { PASSED, FAILED, ERROR, SKIPPED }

    /**
     * One test case.
     *
     * @param className the group (e.g. the scenario name)
     * @param name      the case (e.g. the file)
     * @param outcome   what came out
     * @param message   a one-line message (failure, error, skip reason), or {@code null}
     * @param detail    the full text (e.g. the differences), or {@code null}
     * @param millis    how long it took
     */
    record TestCase(String className, String name, Outcome outcome, String message, String detail, long millis) {
    }

    private final String suite;
    private final List<TestCase> cases = new ArrayList<>();

    JUnitReport(String suite) {
        this.suite = suite;
    }

    void add(TestCase testCase) {
        cases.add(testCase);
    }

    /** @return the report as XML */
    String toXml() {
        StringWriter text = new StringWriter();
        try {
            XMLStreamWriter xml = XMLOutputFactory.newInstance().createXMLStreamWriter(text);
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeCharacters("\n");
            xml.writeStartElement("testsuites");
            xml.writeCharacters("\n  ");
            xml.writeStartElement("testsuite");
            xml.writeAttribute("name", suite);
            xml.writeAttribute("tests", String.valueOf(cases.size()));
            xml.writeAttribute("failures", String.valueOf(count(Outcome.FAILED)));
            xml.writeAttribute("errors", String.valueOf(count(Outcome.ERROR)));
            xml.writeAttribute("skipped", String.valueOf(count(Outcome.SKIPPED)));
            xml.writeAttribute("time", seconds(cases.stream().mapToLong(TestCase::millis).sum()));
            for (TestCase c : cases) {
                xml.writeCharacters("\n    ");
                xml.writeStartElement("testcase");
                xml.writeAttribute("classname", c.className());
                xml.writeAttribute("name", c.name());
                xml.writeAttribute("time", seconds(c.millis()));
                if (c.outcome() != Outcome.PASSED) {
                    String element = switch (c.outcome()) {
                        case FAILED -> "failure";
                        case ERROR -> "error";
                        default -> "skipped";
                    };
                    xml.writeCharacters("\n      ");
                    xml.writeStartElement(element);
                    if (c.message() != null) {
                        xml.writeAttribute("message", c.message());
                    }
                    if (c.detail() != null && !c.detail().isEmpty()) {
                        xml.writeCharacters(c.detail());
                    }
                    xml.writeEndElement();
                    xml.writeCharacters("\n    ");
                }
                xml.writeEndElement();
            }
            xml.writeCharacters("\n  ");
            xml.writeEndElement();
            xml.writeCharacters("\n");
            xml.writeEndElement();
            xml.writeCharacters("\n");
            xml.writeEndDocument();
            xml.close();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("JUnit report could not be written", e);
        }
        return text.toString();
    }

    private long count(Outcome outcome) {
        return cases.stream().filter(c -> c.outcome() == outcome).count();
    }

    private static String seconds(long millis) {
        return String.format(Locale.ROOT, "%.3f", millis / 1000.0);
    }
}
