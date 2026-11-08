package org.fxt.freexmltoolkit.a11y;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.shell.UnifiedShellView;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Runs the {@link AccessibilityAudit} over the shell: every activity, an XML and an XSD document. */
@ExtendWith(ApplicationExtension.class)
class AccessibilityAuditTest {

    private UnifiedShellView shell;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        shell = new UnifiedShellView();
        Scene scene = new Scene(shell, 1280, 800);
        scene.getStylesheets().addAll(getClass().getResource("/css/design-tokens.css").toExternalForm(),
                getClass().getResource("/css/unified-shell.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private List<AccessibilityAudit.Finding> auditNow(String where) throws Exception {
        WaitForAsyncUtils.waitForFxEvents();
        Thread.sleep(1200); // AccessibleNames walks the scene half a second after it settles
        return WaitForAsyncUtils.waitForAsyncFx(10_000, () -> AccessibilityAudit.audit(shell.getScene().getRoot(), where));
    }

    /** All findings of the tour (deduplicated by kind and node). */
    List<AccessibilityAudit.Finding> tour() throws Exception {
        List<AccessibilityAudit.Finding> all = new ArrayList<>(auditNow("welcome"));
        for (Activity activity : Activity.values()) {
            WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
                shell.showActivity(activity);
                return null;
            });
            all.addAll(auditNow("activity " + activity.id()));
        }
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> shell.getEditorHost().openGeneratedDocument(
                "<root><a x='1'>t</a></root>", EditorFileType.XML, "a.xml"));
        all.addAll(auditNow("XML text view"));
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            shell.getEditorHost().setActiveViewMode(ViewMode.TREE);
            return null;
        });
        all.addAll(auditNow("XML tree view"));
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            shell.getEditorHost().setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });
        all.addAll(auditNow("XML grid view"));
        Map<String, AccessibilityAudit.Finding> unique = new TreeMap<>();
        all.forEach(f -> unique.putIfAbsent(f.kind() + " " + f.what(), f));
        return List.copyOf(unique.values());
    }

    @Test
    void writeReport() throws Exception {
        List<AccessibilityAudit.Finding> findings = tour();
        String report = findings.stream().collect(Collectors.groupingBy(AccessibilityAudit.Finding::kind)).entrySet()
                .stream().map(e -> "## " + e.getKey() + " (" + e.getValue().size() + ")\n" + e.getValue().stream()
                        .map(f -> "- " + f.what() + "  [" + f.where() + "]").collect(Collectors.joining("\n")))
                .collect(Collectors.joining("\n\n"));
        Path out = Path.of("build", "a11y-audit.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report + "\n");

        // Every button and input has a name for screen readers
        List<String> unnamed = findings.stream().filter(f -> f.kind() == AccessibilityAudit.Kind.UNNAMED_BUTTON
                || f.kind() == AccessibilityAudit.Kind.UNLABELED_INPUT).map(f -> f.kind() + " " + f.what()).toList();
        org.junit.jupiter.api.Assertions.assertEquals(List.of(), unnamed, "see build/a11y-audit.md");
        // Controls outside the tab order need a keyboard alternative (docs/accessibility.md lists them)
        List<String> unreachable = findings.stream().filter(f -> f.kind() == AccessibilityAudit.Kind.NOT_KEYBOARD_REACHABLE)
                .map(AccessibilityAudit.Finding::what).filter(w -> KEYBOARD_ALTERNATIVES.stream().noneMatch(w::contains))
                .toList();
        org.junit.jupiter.api.Assertions.assertEquals(List.of(), unreachable,
                "new controls outside the tab order: make them focusable or document their keyboard alternative");
    }

    /** Controls outside the tab order whose action has a keyboard alternative (see docs/accessibility.md). */
    private static final List<String> KEYBOARD_ALTERNATIVES = List.of(
            "MenuBarButton#menu-",            // F10 / Alt
            "ToggleButton#activity-",         // View → Activities
            "Button#header-help",             // F1
            "Button#themeToggleButton",       // View → Theme, Settings
            "MenuButton#header-favorites", "MenuButton#explorer-favorites", // File → Open Favorite
            "ToggleButton#toggle-inspector", "ToggleButton#toggle-left-panel", // View → Properties / Side Panel
            ".fxt-view-seg",                  // View → Text / Tree / Graphic View
            "Button#grid-zoom-in", "Button#grid-zoom-out", // Ctrl + / Ctrl −
            // Mouse only today (documented as open): grid header options
            "Button#grid-optimal-widths", "MenuButton#grid-more", "ToggleButton#grid-entry-helpers-toggle",
            "ToggleButton#grid-word-wrap");
}
