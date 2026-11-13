package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.sf.saxon.s9api.SaxonApiException;
import org.junit.jupiter.api.Test;

/**
 * The debugger's Stop is recognized however Saxon wraps it (telemetry: a Stop wrapped in a plain
 * {@code RuntimeException} was logged and counted as a failed debug run).
 */
class XsltDebugStopUnwrapTest {

    @Test
    void theStopIsFoundAtAnyDepth() {
        XsltDebugStopException stop = new XsltDebugStopException();
        assertTrue(XsltTransformationEngine.isDebugStop(stop));
        assertTrue(XsltTransformationEngine.isDebugStop(new RuntimeException(stop)));
        assertTrue(XsltTransformationEngine.isDebugStop(new SaxonApiException(new RuntimeException(stop))));
    }

    @Test
    void otherFailuresAreNoStop() {
        assertFalse(XsltTransformationEngine.isDebugStop(new RuntimeException("boom")));
        assertFalse(XsltTransformationEngine.isDebugStop(new SaxonApiException("XTDE0640")));
        assertFalse(XsltTransformationEngine.isDebugStop(null));
    }
}
