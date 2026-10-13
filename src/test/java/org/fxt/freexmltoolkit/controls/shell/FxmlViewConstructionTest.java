package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.SchematronTemplateLibrary;
import org.fxt.freexmltoolkit.controls.SchematronVisualBuilder;
import org.fxt.freexmltoolkit.controls.dialogs.ErrorReportDialog;
import org.fxt.freexmltoolkit.controls.dialogs.TelemetryInfoDialog;
import org.fxt.freexmltoolkit.controls.dialogs.UpdateNotificationDialog;
import org.fxt.freexmltoolkit.controls.dialogs.UpdateProgressDialog;
import org.fxt.freexmltoolkit.controls.shell.editor.NewFileDialog;
import org.fxt.freexmltoolkit.controls.shell.editor.SchemaLibraryEntryDialog;
import org.fxt.freexmltoolkit.controls.shell.editor.TemplateEditDialog;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.dialogs.TypeUsageDialog;
import org.fxt.freexmltoolkit.controls.v2.editor.panels.AppInfoEditorPanel;
import org.fxt.freexmltoolkit.controls.v2.editor.panels.TypeDocumentationPanel;
import org.fxt.freexmltoolkit.controls.v2.editor.panels.XsdPropertiesPanel;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.domain.UpdateInfo;
import org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl;
import org.fxt.freexmltoolkit.service.SchemaResourceCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * Constructs the FXML-backed views and dialogs that no other test instantiates, so a layout
 * that fails to load (wrong field type, a field name FXMLLoader reserves, a bad attribute) is
 * caught by the suite and not only when the user opens the dialog.
 */
@ExtendWith(ApplicationExtension.class)
class FxmlViewConstructionTest {

    @Start
    void start(Stage stage) {
        // The toolkit is all these constructors need.
    }

    @Test
    void viewsWithoutOtherCoverageLoadTheirLayout(FxRobot robot, @TempDir Path dir) {
        UpdateInfo update = new UpdateInfo("1.0.0", "1.1.0", "Release 1.1.0", "Notes",
                "https://example.org/download", "2026-01-01", true);
        Map<String, Callable<Object>> views = new LinkedHashMap<>();
        views.put("XsdPropertiesPanel", () -> new XsdPropertiesPanel(new XsdEditorContext(new XsdSchema())));
        views.put("TypeDocumentationPanel", () -> new TypeDocumentationPanel(new XsdEditorContext(new XsdSchema())));
        views.put("AppInfoEditorPanel", () -> new AppInfoEditorPanel(new XsdEditorContext(new XsdSchema())));
        views.put("SchematronVisualBuilder", SchematronVisualBuilder::new);
        views.put("SchematronTemplateLibrary", SchematronTemplateLibrary::new);
        views.put("TelemetryNotice", () -> new TelemetryNotice(() -> { }, () -> { }, () -> { }, false));
        views.put("TelemetryInfoDialog", () -> TelemetryInfoDialog.build(null));
        views.put("ErrorReportDialog", () -> ErrorReportDialog.build(null, new IllegalStateException("test")));
        views.put("UpdateNotificationDialog", () -> new UpdateNotificationDialog(update));
        views.put("UpdateProgressDialog", () -> new UpdateProgressDialog(update));
        views.put("TypeUsageDialog", () -> new TypeUsageDialog("MyType", List.of()));
        views.put("TemplateEditDialog", () -> new TemplateEditDialog(null));
        views.put("NewFileDialog", NewFileDialog::new);
        views.put("SchemaLibraryEntryDialog", () -> new SchemaLibraryEntryDialog(
                new SchemaLibraryServiceImpl(dir.resolve("lib.json"), new SchemaResourceCache(dir.resolve("cache")),
                        () -> SchemaLibraryServiceImpl.class.getResourceAsStream("/schema-library/bundled.json")),
                null));

        List<String> failures = new ArrayList<>();
        robot.interact(() -> views.forEach((name, constructor) -> {
            try {
                constructor.call();
            } catch (Exception | LinkageError e) {
                Throwable root = e;
                while (root.getCause() != null) {
                    root = root.getCause();
                }
                failures.add(name + ": " + root);
            }
        }));
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
    }
}
