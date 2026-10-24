package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.xml.transform.stream.StreamSource;

import javafx.scene.input.Clipboard;
import javafx.stage.Stage;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;

import org.fxt.freexmltoolkit.debugger.PausedSnapshot;
import org.fxt.freexmltoolkit.debugger.WatchEvaluator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The value viewer shows the full value, highlights it, copies it and runs XPath against it. */
@ExtendWith(ApplicationExtension.class)
class ValueViewerWindowTest {

    private static final long FX_TIMEOUT_MS = 10_000;
    private static final Processor PROCESSOR = new Processor(false);

    private ValueViewerWindow window;

    @Start
    void start(Stage stage) {
    }

    @AfterEach
    void close() {
        if (window != null) {
            WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> window.close());
        }
    }

    private static XdmNode order() throws Exception {
        StringBuilder xml = new StringBuilder("<order id='4711'>");
        for (int i = 1; i <= 20; i++) {
            xml.append("<item n='").append(i).append("'><price>").append(i * 10).append(".50</price></item>");
        }
        xml.append("</order>");
        XdmNode doc = PROCESSOR.newDocumentBuilder().build(new StreamSource(new StringReader(xml.toString())));
        return (XdmNode) PROCESSOR.newXPathCompiler().evaluateSingle("/order", doc);
    }

    private static PausedSnapshot snapshot(XdmNode order) {
        return new PausedSnapshot("file:/tmp/a.xsl", 12, List.of(), List.of(), "<order>", order,
                Map.of(new QName("order"), order), Map.of(new QName("total"), new XdmAtomicValue(20)), 1, 1);
    }

    private ValueViewerWindow open(ValueSource source) {
        WatchEvaluator evaluator = new WatchEvaluator(PROCESSOR, Map.of());
        window = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            ValueViewerWindow w = new ValueViewerWindow(null, source, evaluator);
            w.show();
            return w;
        });
        return window;
    }

    @Test
    void showsTheFullHighlightedValueAndCopiesIt() throws Exception {
        XdmNode order = order();
        ValueViewerWindow w = open(new ValueSource("$order", "element(order)", "LOCAL", order, null, snapshot(order)));
        String expected = new WatchEvaluator(PROCESSOR, Map.of()).serialize(order, true);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> expected.equals(w.getValueText()));
        assertTrue(w.getValueText().length() > 200, "not cut to the table's 200 characters");
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> w.valueStyleClassesForTest().contains("anytag")));
        assertEquals("$order — element(order)", w.getTitle());

        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, w::onCopy);
        String copied = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> Clipboard.getSystemClipboard().getString());
        assertEquals(expected, copied);

        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            w.prettyToggle().setSelected(false);
            w.onPrettyPrint();
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !w.getValueText().contains("\n"));
    }

    @Test
    void xpathRunsAgainstTheValue() throws Exception {
        XdmNode order = order();
        ValueViewerWindow w = open(new ValueSource("$order", "element(order)", "LOCAL", order, null, snapshot(order)));
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> w.runXPath("count(item)"));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> "20".equals(w.getResultText()));
        assertTrue(w.getResultStatus().startsWith("1 item"), w.getResultStatus());

        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> w.runXPath("item[@n='3']/price"));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> "<price>30.50</price>".equals(w.getResultText()));

        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> w.runXPath("$total + 1"));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> "21".equals(w.getResultText()));

        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> w.runXPath("item["));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> w.getResultText().startsWith("ERROR:"));
        assertEquals("XPath error", w.getResultStatus());
    }

    @Test
    void fallbackShowsTheRowText() throws Exception {
        ValueViewerWindow w = open(new ValueSource("position()", "xs:integer", "LOCAL", null, "3", null));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> "3".equals(w.getValueText()));
        assertTrue(w.prettyToggle().isDisabled(), "nothing to pretty-print");
    }

    @Test
    void sequenceShowsItsItemCount() throws Exception {
        XdmValue seq = new XdmValue(List.of(new XdmAtomicValue(1), new XdmAtomicValue(2), new XdmAtomicValue(3)));
        ValueViewerWindow w = open(new ValueSource("$ids", "3 items", "LOCAL", seq, null, null));
        // Text and size label are set in the same FX pulse; poll both on the FX thread.
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS,
                () -> "1\n2\n3".equals(w.getValueText()) && w.getSizeText().startsWith("3 items")));
    }
}
