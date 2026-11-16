package org.fxt.freexmltoolkit.controls.shell.editor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.shell.ThemeManager;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.finance.gleif.Gleif;
import org.fxt.freexmltoolkit.service.finance.gleif.LeiRecord;
import org.fxt.freexmltoolkit.service.finance.gleif.LeiScanner;

/**
 * Shows what GLEIF knows about an LEI when the mouse rests on it in a text editor: legal name, country, registration
 * and entity status. Known answers (the shared cache) show at once; an unknown LEI is looked up only when the user
 * turned on {@link Gleif#KEY_HOVER}, otherwise the hover offers nothing. Installed on every code area while GLEIF
 * lookups are allowed; works in any text (XML, CSV, JSON).
 */
public final class LeiHover {

    private static final Map<CodeArea, LeiHover> INSTALLED = new WeakHashMap<>();

    private final CodeArea area;
    private final Popup popup = new Popup();
    private final VBox box = new VBox(2);
    private final Label title = new Label();
    private final Label body = new Label();
    private final PauseTransition hideDelay = new PauseTransition(javafx.util.Duration.millis(400));
    /** The LEI the mouse rests on (a lookup that returns later shows only when it is still this one). */
    private String current;

    private LeiHover(CodeArea area) {
        this.area = area;
        title.getStyleClass().add("fxt-debug-hover-title");
        body.getStyleClass().add("fxt-debug-hover-value");
        body.setWrapText(true);
        body.setMaxWidth(560);
        box.getChildren().addAll(title, body);
        box.getStyleClass().addAll("root", "fxt-debug-hover", "fxt-lei-hover");
        popup.getContent().add(box);
        popup.setAutoHide(true);
        box.setOnMouseEntered(e -> hideDelay.stop());
        box.setOnMouseExited(e -> hideDelay.playFromStart());
        hideDelay.setOnFinished(e -> {
            popup.hide();
            current = null;
        });
    }

    /** Installs the hover on a code area once; does nothing when the administrator turned GLEIF off. */
    public static synchronized void installOn(CodeArea area) {
        if (area == null || INSTALLED.containsKey(area) || !Gleif.allowed()) {
            return;
        }
        LeiHover hover = new LeiHover(area);
        INSTALLED.put(area, hover);
        if (area.getMouseOverTextDelay() == null) {
            area.setMouseOverTextDelay(Duration.ofMillis(500));
        }
        area.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_BEGIN, hover::onBegin);
        area.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_END, e -> hover.hideDelay.playFromStart());
    }

    private void onBegin(MouseOverTextEvent e) {
        String lei = leiAt(area, e.getCharacterIndex());
        if (lei == null) {
            return;
        }
        current = lei;
        double x = e.getScreenPosition().getX();
        double y = e.getScreenPosition().getY() + 14;
        var known = Gleif.cache().cached(lei);
        if (known.isPresent()) {
            show(known.get(), x, y);
            return;
        }
        if (!Gleif.hoverLookups()) {
            return;
        }
        FxtGui.executorService.submit(() -> {
            LeiRecord r;
            try {
                r = Gleif.cache().lookup(List.of(lei)).get(lei);
            } catch (Exception ex) {
                r = null;
            }
            LeiRecord found = r;
            Platform.runLater(() -> {
                if (found != null && lei.equals(current)) {
                    show(found, x, y);
                }
            });
        });
    }

    /** @return the LEI at a character of the area, or {@code null} (reads only the characters around it) */
    static String leiAt(CodeArea area, int index) {
        int length = area.getLength();
        if (index < 0 || index >= length) {
            return null;
        }
        int from = Math.max(0, index - 21);
        int to = Math.min(length, index + 22);
        return LeiScanner.at(area.getText(from, to), index - from);
    }

    /** @return title and body for a record */
    static String[] texts(LeiRecord r) {
        if (!r.found()) {
            return new String[] {"GLEIF: " + r.lei(), I18n.t("LeiHover.not-found")};
        }
        StringBuilder body = new StringBuilder();
        body.append(I18n.t("IndustryPanel.gleif-place", r.city() == null ? "" : r.city(), r.country() == null ? "" : r.country()))
                .append('\n').append(I18n.t("IndustryPanel.gleif-status", r.registrationStatus(), r.entityStatus()));
        if (r.nextRenewal() != null) {
            body.append('\n').append(I18n.t("IndustryPanel.gleif-renewal", r.nextRenewal().toString()));
        }
        LeiRecord.Problem p = r.problem();
        if (p != null) {
            body.append("\n\n").append(p.message());
        }
        return new String[] {r.legalName(), body.toString()};
    }

    private void show(LeiRecord r, double x, double y) {
        if (area.getScene() == null || area.getScene().getWindow() == null) {
            return;
        }
        hideDelay.stop();
        String[] t = texts(r);
        title.setText(t[0]);
        body.setText(t[1]);
        box.getStyleClass().removeAll("fxt-theme-dark", "fxt-theme-light");
        box.getStyleClass().add(ThemeManager.currentIsDark() ? "fxt-theme-dark" : "fxt-theme-light");
        if (box.getStylesheets().isEmpty()) {
            for (String sheet : new String[] {"/css/design-tokens.css", "/css/unified-shell.css"}) {
                var url = LeiHover.class.getResource(sheet);
                if (url != null) {
                    box.getStylesheets().add(url.toExternalForm());
                }
            }
        }
        ThemeManager.syncAppearanceSheet(box);
        popup.show(area.getScene().getWindow(), x, y);
    }
}
