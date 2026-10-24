package org.fxt.freexmltoolkit.debugger.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.debugger.SymbolBreakpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The template / function breakpoint dialog offers the stylesheet's names per kind. */
@ExtendWith(ApplicationExtension.class)
class SymbolBreakpointDialogTest {

    @Start
    void start(Stage stage) {
    }

    @Test
    void suggestsPerKindAndReturnsTheChoice() {
        Object[] r = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            SymbolBreakpointDialog dialog = new SymbolBreakpointDialog(Map.of(
                    SymbolBreakpoint.Kind.TEMPLATE_MATCH, List.of("item", "order"),
                    SymbolBreakpoint.Kind.FUNCTION, List.of("f:twice")));
            List<String> templates = dialog.suggestions();
            dialog.set(SymbolBreakpoint.Kind.FUNCTION, "");
            List<String> functions = dialog.suggestions();
            SymbolBreakpoint none = dialog.current();
            dialog.set(SymbolBreakpoint.Kind.FUNCTION, "f:twice");
            return new Object[]{templates, functions, none, dialog.current()};
        });
        assertEquals(List.of("item", "order"), r[0]);
        assertEquals(List.of("f:twice"), r[1]);
        assertNull(r[2], "no name, no breakpoint");
        assertEquals(new SymbolBreakpoint(SymbolBreakpoint.Kind.FUNCTION, "f:twice", true), r[3]);
    }
}
