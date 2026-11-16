package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.finance.gleif.Gleif;
import org.fxt.freexmltoolkit.service.finance.gleif.LeiRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The LEI hover's texts and position lookup, and the GLEIF settings card. */
@ExtendWith(ApplicationExtension.class)
class GleifUiTest {

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        stage.setScene(new Scene(new StackPane(), 300, 200));
        stage.show();
    }

    @Test
    void hoverFindsTheLeiUnderTheMouseAndDescribesIt() throws Exception {
        String text = "<a>\n<LEI>506700GE1G29325QX363</LEI>\n</a>";
        CodeArea area = WaitForAsyncUtils.waitForAsyncFx(5000, () -> new CodeArea(text));
        int inside = text.indexOf("506700") + 10;
        assertEquals("506700GE1G29325QX363", LeiHover.leiAt(area, inside));
        assertNull(LeiHover.leiAt(area, 1));
        assertNull(LeiHover.leiAt(area, text.length() + 5));

        LeiRecord lapsed = new LeiRecord("506700GE1G29325QX363", true, "Example AG", "AT", "Wien", "ACTIVE", "LAPSED",
                LocalDate.of(2025, 3, 1), null, Instant.now());
        String[] shown = LeiHover.texts(lapsed);
        assertEquals("Example AG", shown[0]);
        assertTrue(shown[1].contains("Wien (AT)") && shown[1].contains("LAPSED") && shown[1].contains("has lapsed"), shown[1]);
        assertTrue(LeiHover.texts(LeiRecord.notFound("5299009FXTMMFCP00069", Instant.now()))[1].contains("not registered"));
    }

    @Test
    void settingsAreSavedAtOnce() throws Exception {
        Map<String, String> stored = new HashMap<>();
        PropertiesService properties = (PropertiesService) java.lang.reflect.Proxy.newProxyInstance(
                PropertiesService.class.getClassLoader(), new Class<?>[] {PropertiesService.class}, (proxy, method, args) -> {
                    if (method.getName().equals("set") && args.length == 2) {
                        stored.put((String) args[0], (String) args[1]);
                    }
                    return null;
                });
        GleifSettings settings = WaitForAsyncUtils.waitForAsyncFx(5000, () -> new GleifSettings(properties));
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            CheckBox validation = (CheckBox) settings.lookup("#settings-gleif-validation");
            validation.setSelected(true);
            settings.onValidationChanged();
            CheckBox hover = (CheckBox) settings.lookup("#settings-gleif-hover");
            hover.setSelected(true);
            settings.onHoverChanged();
            return null;
        });
        assertEquals("true", stored.get(Gleif.KEY_ON_VALIDATION));
        assertEquals("true", stored.get(Gleif.KEY_HOVER));
    }

    @Test
    void outcomesOfACheck() {
        var none = GleifDocumentCheck.outcomeOf(new org.fxt.freexmltoolkit.service.finance.gleif.GleifCheck.Result(0, Map.of(),
                java.util.List.of(), null));
        assertEquals(GleifDocumentCheck.Kind.NONE, none.kind());
        var failed = GleifDocumentCheck.outcomeOf(new org.fxt.freexmltoolkit.service.finance.gleif.GleifCheck.Result(2, Map.of(),
                java.util.List.of(), "GLEIF could not be reached: offline"));
        assertEquals(GleifDocumentCheck.Kind.FAILED, failed.kind());
        assertTrue(failed.message().contains("offline"));
        var ok = GleifDocumentCheck.outcomeOf(new org.fxt.freexmltoolkit.service.finance.gleif.GleifCheck.Result(3, Map.of(),
                java.util.List.of(), null));
        assertEquals(GleifDocumentCheck.Kind.OK, ok.kind());
        assertTrue(ok.message().contains("3 LEIs"), ok.message());
        var findings = GleifDocumentCheck.outcomeOf(new org.fxt.freexmltoolkit.service.finance.gleif.GleifCheck.Result(3, Map.of(),
                java.util.List.of(new org.fxt.freexmltoolkit.service.finance.gleif.GleifCheck.Finding(
                        LeiRecord.Severity.ERROR, 2, 5, "5299009FXTMMFCP00069", "not registered")), null));
        assertEquals(GleifDocumentCheck.Kind.FINDINGS, findings.kind());
        assertEquals(1, findings.findings());
    }
}
