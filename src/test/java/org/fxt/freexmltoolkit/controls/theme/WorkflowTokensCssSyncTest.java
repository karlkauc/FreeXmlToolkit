package org.fxt.freexmltoolkit.controls.theme;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.scene.paint.Color;

import org.junit.jupiter.api.Test;

/**
 * Lock-step guard: every workflow token exists in design-tokens.css for light AND dark with the
 * same hex as {@link DesignTokens.ColorToken}, and the action aliases exist. Editing only one side
 * fails the build.
 */
class WorkflowTokensCssSyncTest {

    private static final Path CSS = Path.of("src/main/resources/css/design-tokens.css");

    private static String block(String css, String selector) {
        int start = css.indexOf(selector + " {");
        assertTrue(start >= 0, "selector not found: " + selector);
        int end = css.indexOf("}", start);
        return css.substring(start, end);
    }

    private static String value(String block, String variable) {
        Matcher m = Pattern.compile(Pattern.quote(variable) + "\\s*:\\s*([^;]+);").matcher(block);
        assertTrue(m.find(), () -> variable + " missing in block");
        return m.group(1).trim();
    }

    @Test
    void workflowTokensMatchJavaInBothThemes() throws IOException {
        String css = Files.readString(CSS);
        String light = block(css, ".root");
        String dark = block(css, ".root.fxt-theme-dark");
        for (Workflow wf : Workflow.values()) {
            for (DesignTokens.ColorToken t : new DesignTokens.ColorToken[] {
                    wf.accent(), wf.fg(), wf.fill(), wf.bg(), wf.border(), wf.rail() }) {
                assertEquals(t.color(DesignTokens.Theme.LIGHT), Color.web(value(light, t.cssVariable())),
                        () -> t.cssVariable() + " light differs between CSS and Java");
                assertEquals(t.color(DesignTokens.Theme.DARK), Color.web(value(dark, t.cssVariable())),
                        () -> t.cssVariable() + " dark differs between CSS and Java");
            }
        }
    }

    @Test
    void actionAliasesPointAtSemanticTokens() throws IOException {
        String css = Files.readString(CSS);
        String light = block(css, ".root");
        String dark = block(css, ".root.fxt-theme-dark");
        for (ActionColor a : ActionColor.values()) {
            String alias = "-fxt-action-" + a.name().toLowerCase();
            assertEquals(a.token().cssVariable(), value(light, alias), alias + " (light)");
            assertEquals(a.token().cssVariable(), value(dark, alias), alias + " (dark)");
        }
    }
}
