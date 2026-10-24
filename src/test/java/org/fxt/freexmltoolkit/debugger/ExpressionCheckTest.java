package org.fxt.freexmltoolkit.debugger;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Static checks of conditions and logpoint messages. */
class ExpressionCheckTest {

    @Test
    void validConditionsPassEvenWithStylesheetVariablesPrefixesAndFunctions() {
        assertNull(ExpressionCheck.checkXPath(""));
        assertNull(ExpressionCheck.checkXPath("@id = '42' and $total > 3"));
        assertNull(ExpressionCheck.checkXPath("position() = last()"));
        assertNull(ExpressionCheck.checkXPath("f:total(.) gt 10"), "f: is declared in the stylesheet");
        assertNull(ExpressionCheck.checkXPath("xs:integer(@n) = 3"));
    }

    @Test
    void syntaxErrorsAreReported() {
        String error = ExpressionCheck.checkXPath("$x =");
        assertNotNull(error);
        assertTrue(error.startsWith("XPST0003"), error);
        assertNotNull(ExpressionCheck.checkXPath("count(item"));
    }

    @Test
    void logMessagesCheckEachPartAndTheBraces() {
        assertNull(ExpressionCheck.checkLogMessage("item {@n}: {{literal}} total {$total}"));
        assertNull(ExpressionCheck.checkLogMessage("no expressions at all"));
        assertTrue(ExpressionCheck.checkLogMessage("item {@n").contains("not closed"));
        assertTrue(ExpressionCheck.checkLogMessage("x {} y").startsWith("Empty {}"));
        assertTrue(ExpressionCheck.checkLogMessage("x {count(item}").startsWith("{count(item}"));
    }
}
