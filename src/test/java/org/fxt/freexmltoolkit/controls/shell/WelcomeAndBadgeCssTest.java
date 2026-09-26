package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.fxt.freexmltoolkit.controls.theme.Workflow;
import org.junit.jupiter.api.Test;

/** Welcome tool-card chips and the status-bar badge have one CSS rule per workflow family. */
class WelcomeAndBadgeCssTest {

    @Test
    void everyWorkflowHasChipAndBadgeRules() throws IOException {
        String css = Files.readString(Path.of("src/main/resources/css/unified-shell.css"));
        for (Workflow wf : Workflow.values()) {
            assertTrue(css.contains(".fxt-card-icon." + wf.cssClass()), wf + " card chip rule");
            assertTrue(css.contains(".fxt-status-badge." + wf.cssClass()), wf + " badge rule");
        }
        assertFalse(Files.readString(Path.of(
                "src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorWelcomePane.java"))
                .contains("#e64980"), "hard-coded category colours are gone");
    }
}
