package org.fxt.freexmltoolkit.controls.shell.inspector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdAppInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The inspector's structured appinfo editor writes the canonical tag form, carries an untouched
 * example-values block over verbatim (so a legacy {@code altova:} block is not rewritten by an
 * unrelated edit), and rewrites it as {@code fxt:exampleValues} once the values change.
 */
@ExtendWith(ApplicationExtension.class)
class AppInfoFieldsEditorTest {

    private static final String LEGACY_EXAMPLES =
            "<altova:exampleValues xmlns:altova=\"" + XsdAppInfo.ALTOVA_NS + "\">"
                    + "<altova:example value=\"WBAH\"/><altova:example value=\"XLON\"/>"
                    + "</altova:exampleValues>";

    private final AtomicReference<XsdAppInfo> committed = new AtomicReference<>();
    private AppInfoFieldsEditor editor;
    private Button focusStealer;

    @Start
    void start(Stage stage) {
        editor = new AppInfoFieldsEditor(() -> null, committed::set);
        focusStealer = new Button("elsewhere");
        stage.setScene(new Scene(new VBox(editor, focusStealer), 500, 800));
        stage.show();
        // Warm up: first setAppinfo builds the field state, keeping it out of the timed blocks.
        editor.setAppinfo(new XsdAppInfo());
    }

    /** Loads an appinfo carrying a legacy Altova example-values block plus a @since tag. */
    private XsdAppInfo loadBase() throws Exception {
        XsdAppInfo base = new XsdAppInfo();
        base.setSince("4.0.0");
        base.addEntry(null, "", LEGACY_EXAMPLES);
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            committed.set(null);
            editor.setAppinfo(base);
            return null;
        });
        return base;
    }

    /** Moves focus off the fields so the blur-triggered commit fires. */
    private XsdAppInfo commitViaBlur() throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            focusStealer.requestFocus();
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> committed.get() != null);
        return committed.get();
    }

    @SuppressWarnings("unchecked")
    private <T> T lookup(String id) {
        return (T) editor.lookup(id);
    }

    @Test
    @DisplayName("editing a tag keeps an untouched legacy example-values block verbatim")
    void untouchedExamplesAreCarriedOverVerbatim() throws Exception {
        loadBase();

        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            TextField since = lookup("#inspector-appinfo-since");
            since.requestFocus();
            since.setText("5.0.0");
            return null;
        });
        XsdAppInfo result = commitViaBlur();

        assertNotNull(result);
        assertEquals("5.0.0", result.getSince());
        assertEquals(List.of("WBAH", "XLON"), result.getExampleValues());
        String xml = String.join("\n", result.toXmlStrings());
        assertTrue(xml.contains("altova:exampleValues"),
                "an untouched block must not be rewritten, was:\n" + xml);
        assertTrue(xml.contains("<xs:appinfo source=\"@since\">5.0.0</xs:appinfo>"), xml);
    }

    @Test
    @DisplayName("changing the example values rewrites them as fxt:exampleValues")
    void changedExamplesAreRewrittenInTheFxtNamespace() throws Exception {
        loadBase();

        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            ListView<String> examples = lookup("#inspector-appinfo-examples");
            examples.getItems().add("TRX-0815");
            TextField since = lookup("#inspector-appinfo-since");
            since.requestFocus();
            return null;
        });
        XsdAppInfo result = commitViaBlur();

        assertNotNull(result);
        assertEquals(List.of("WBAH", "XLON", "TRX-0815"), result.getExampleValues());
        String xml = String.join("\n", result.toXmlStrings());
        assertTrue(xml.contains("fxt:exampleValues"), xml);
        assertFalse(xml.contains("altova"), xml);
    }

    @Test
    @DisplayName("the Markdown choice round-trips through the @markdown tag")
    void markdownChoiceRoundTrips() throws Exception {
        loadBase();
        assertNull(committed.get(), "loading must not commit");

        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            ComboBox<String> markdown = lookup("#inspector-appinfo-markdown");
            markdown.setValue("Markdown");
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> committed.get() != null);

        XsdAppInfo result = committed.get();
        assertEquals(Boolean.TRUE, result.getMarkdown());
        assertTrue(String.join("\n", result.toXmlStrings())
                .contains("<xs:appinfo source=\"@markdown\">true</xs:appinfo>"));

        // Reloading that appinfo must show the stored state again.
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            editor.setAppinfo(result);
            return null;
        });
        ComboBox<String> markdown = lookup("#inspector-appinfo-markdown");
        assertEquals("Markdown", markdown.getValue());
    }
}
