package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.debugger.DebugStackFrame;
import org.fxt.freexmltoolkit.debugger.VariableBinding;
import org.fxt.freexmltoolkit.debugger.VariableScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class DebugPanelsTest {

    /** Generous FX-thread timeout: the full suite runs one JVM per class and is often under load. */
    private static final long FX_TIMEOUT_MS = 10_000;

    @Start
    void start(Stage stage) {
    }

    @Test
    void variablesViewShowsBindings() {
        int rows = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            VariablesView view = new VariablesView();
            view.setVariables(List.of(
                    new VariableBinding("count", "2", "xs:integer", VariableScope.LOCAL)));
            return view.getRowCount();
        });
        assertEquals(1, rows);
    }

    @Test
    void callStackViewShowsFrames() {
        int rows = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            CallStackView view = new CallStackView();
            view.setFrames(List.of(new DebugStackFrame("template match=/", "sheet.xslt", 4, List.of())));
            return view.getRowCount();
        });
        assertEquals(1, rows);
    }
}
