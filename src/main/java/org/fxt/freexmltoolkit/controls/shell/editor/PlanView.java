package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.controls.shared.CodeAreaFontZoom;
import org.fxt.freexmltoolkit.controls.shared.PlainTextCodeArea;
import org.fxt.freexmltoolkit.i18n.I18n;

/**
 * A tool tab with Saxon's execution plan of a query or stylesheet ({@code explain()}): the expression tree after
 * optimization as read-only, highlighted XML, with a line that says what it is.
 */
public final class PlanView extends VBox {

    private final CodeArea area = new PlainTextCodeArea();

    /** @param plan the plan as XML text */
    public PlanView(String plan) {
        getStyleClass().add("fxt-plan-view");
        Label note = new Label(I18n.t("PlanView.note"));
        note.setWrapText(true);
        note.getStyleClass().addAll("fxt-output-status", "fxt-text-sm");
        note.setPadding(new Insets(6, 12, 6, 12));
        area.setEditable(false);
        area.setParagraphGraphicFactory(LineNumberFactory.get(area));
        area.getStyleClass().add("fxt-transform-output");
        area.setAccessibleText(I18n.t("PlanView.accessible"));
        CodeAreaFontZoom.install(area);
        String text = plan == null ? "" : plan;
        area.replaceText(text);
        area.moveTo(0);
        if (text.length() <= ResultHighlighter.HIGHLIGHT_LIMIT_CHARS) {
            ResultHighlighter.highlight(area, text, true, false);
        }
        VirtualizedScrollPane<CodeArea> scroll = new VirtualizedScrollPane<>(area);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().addAll(note, scroll);
    }

    /** @return the plan text shown (tests) */
    public String getText() {
        return area.getText();
    }
}
