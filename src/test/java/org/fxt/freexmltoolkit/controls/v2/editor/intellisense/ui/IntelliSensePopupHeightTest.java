package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.IntStream;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** A short suggestion list shrinks to its rows; a long one keeps the full height and scrolls. */
@ExtendWith(ApplicationExtension.class)
class IntelliSensePopupHeightTest {

    private Stage stage;
    private IntelliSensePopup popup;

    @Start
    void start(Stage stage) {
        this.stage = stage;
        stage.setScene(new Scene(new StackPane(), 600, 400));
        stage.show();
        popup = new IntelliSensePopup();
    }

    private static List<CompletionItem> items(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new CompletionItem.Builder("element" + i, "element" + i, CompletionItemType.ELEMENT).build())
                .toList();
    }

    private double heightFor(int count) {
        return WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            popup.show(items(count), stage, stage.getX() + 20, stage.getY() + 20);
            return popup.listPrefHeight();
        });
    }

    @Test
    void shortListShrinksLongListKeepsMaximum() {
        double one = heightFor(1);
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            popup.hide();
            return null;
        });
        double three = heightFor(3);
        assertTrue(one > 0 && one < 80, "one suggestion is one row high: " + one);
        assertTrue(three > one && three < IntelliSensePopup.MAX_LIST_HEIGHT, "three rows: " + three);

        double many = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            popup.updateItems(items(40));
            return popup.listPrefHeight();
        });
        assertEquals(IntelliSensePopup.MAX_LIST_HEIGHT, many, 0.5, "40 suggestions scroll at the maximum height");

        double back = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            popup.updateItems(items(3));
            return popup.listPrefHeight();
        });
        assertEquals(three, back, 0.5, "filtering back to three rows shrinks again");
    }
}
