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
        assertEquals("506700GE1G29325QX363", IdentifierHover.leiAt(area, inside));
        assertNull(IdentifierHover.leiAt(area, 1));
        assertNull(IdentifierHover.leiAt(area, text.length() + 5));

        LeiRecord lapsed = new LeiRecord("506700GE1G29325QX363", true, "Example AG", "AT", "Wien", "ACTIVE", "LAPSED",
                LocalDate.of(2025, 3, 1), null, Instant.now());
        String[] shown = IdentifierHover.texts(lapsed);
        assertEquals("Example AG", shown[0]);
        assertTrue(shown[1].contains("Wien (AT)") && shown[1].contains("LAPSED") && shown[1].contains("has lapsed"), shown[1]);
        assertTrue(IdentifierHover.texts(LeiRecord.notFound("5299009FXTMMFCP00069", Instant.now()))[1].contains("not registered"));
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
        assertEquals(IdentifierDocumentCheck.Kind.NONE, IdentifierDocumentCheck.outcomeOf(result(0, 0, 0)).kind());
        var failed = IdentifierDocumentCheck.outcomeOf(new org.fxt.freexmltoolkit.service.finance.IdentifierCheck.Result(2, 0,
                java.util.List.of(), java.util.List.of(new org.fxt.freexmltoolkit.service.finance.IdentifierCheck.Failure("GLEIF",
                "GLEIF could not be reached: offline")), Map.of(), Map.of(), 0));
        assertEquals(IdentifierDocumentCheck.Kind.FAILED, failed.kind());
        assertTrue(failed.message().contains("offline"));
        // one service failed, the other ran: the outcome counts, the failure is named
        var half = IdentifierDocumentCheck.outcomeOf(new org.fxt.freexmltoolkit.service.finance.IdentifierCheck.Result(2, 3,
                java.util.List.of(), java.util.List.of(new org.fxt.freexmltoolkit.service.finance.IdentifierCheck.Failure("ISIN",
                "ESMA FIRDS could not be reached")), Map.of(), Map.of(), 0));
        assertEquals(IdentifierDocumentCheck.Kind.FINDINGS, half.kind());
        assertTrue(half.message().contains("ESMA FIRDS could not be reached"), half.message());
        var ok = IdentifierDocumentCheck.outcomeOf(result(3, 2, 0));
        assertEquals(IdentifierDocumentCheck.Kind.OK, ok.kind());
        assertTrue(ok.message().contains("3 LEIs, 2 ISINs"), ok.message());
        var findings = IdentifierDocumentCheck.outcomeOf(result(3, 0, 1));
        assertEquals(IdentifierDocumentCheck.Kind.FINDINGS, findings.kind());
        assertEquals(1, findings.findings());
        // synthetic example identifiers (XMPL) are not looked up: named in the outcome
        var withExamples = IdentifierDocumentCheck.outcomeOf(result(3, 2, 0, 5));
        assertEquals(IdentifierDocumentCheck.Kind.OK, withExamples.kind());
        assertTrue(withExamples.message().contains("5 example identifiers (XMPL) not looked up"), withExamples.message());
        var onlyExamples = IdentifierDocumentCheck.outcomeOf(result(0, 0, 0, 4));
        assertEquals(IdentifierDocumentCheck.Kind.NONE, onlyExamples.kind());
        assertTrue(onlyExamples.message().contains("only example identifiers"), onlyExamples.message());
    }

    private static org.fxt.freexmltoolkit.service.finance.IdentifierCheck.Result result(int leis, int isins, int findings) {
        return result(leis, isins, findings, 0);
    }

    private static org.fxt.freexmltoolkit.service.finance.IdentifierCheck.Result result(int leis, int isins, int findings,
                                                                                         int examples) {
        java.util.List<org.fxt.freexmltoolkit.service.finance.IdentifierCheck.Finding> list = new java.util.ArrayList<>();
        for (int i = 0; i < findings; i++) {
            list.add(new org.fxt.freexmltoolkit.service.finance.IdentifierCheck.Finding("GLEIF", "GLEIF-LEI",
                    org.fxt.freexmltoolkit.service.finance.IdentifierCheck.Severity.ERROR, 2, 5, "5299009FXTMMFCP00069", "not registered"));
        }
        return new org.fxt.freexmltoolkit.service.finance.IdentifierCheck.Result(leis, isins, list, java.util.List.of(),
                Map.of(), Map.of(), examples);
    }
}
