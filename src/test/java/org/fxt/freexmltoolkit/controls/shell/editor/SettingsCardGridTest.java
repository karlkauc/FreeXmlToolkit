package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The Settings card grid: regular rows, wrapped texts fully inside, never overlapping the next heading. */
@ExtendWith(ApplicationExtension.class)
class SettingsCardGridTest {

    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
    }

    private static SettingsCard card(String title, String text) {
        SettingsCard card = new SettingsCard();
        card.setTitle(title);
        Label label = new Label(text);
        label.setWrapText(true);
        card.getChildren().add(label);
        return card;
    }

    private record Laid(SettingsCardGrid grid, SettingsCard shortCard, SettingsCard longCard, Label next) {
    }

    private Laid layout(double width) {
        return WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            SettingsCard shortCard = card("SHORT", "One line.");
            SettingsCard longCard = card("LONG", "A long help text that has to wrap over several lines at this width. ".repeat(6));
            SettingsCardGrid grid = new SettingsCardGrid();
            grid.getChildren().addAll(shortCard, longCard);
            Label next = new Label("Next group");
            VBox page = new VBox(12, grid, next);
            stage.setScene(new Scene(page, width, 300)); // too short: the page must not squeeze the grid
            stage.show();
            page.applyCss();
            page.layout();
            return new Laid(grid, shortCard, longCard, next);
        });
    }

    @Test
    void cardsOfARowShareTheTallestHeightAndNothingOverlapsTheNextHeading() {
        Laid laid = layout(900);

        assertEquals(laid.shortCard().getLayoutY(), laid.longCard().getLayoutY(), "two columns: one row");
        assertTrue(laid.longCard().getLayoutX() > laid.shortCard().getLayoutX());
        assertEquals(laid.longCard().getHeight(), laid.shortCard().getHeight(), 0.5, "a row's cards are equally tall");
        double needed = laid.longCard().prefHeight(laid.longCard().getWidth());
        assertTrue(laid.longCard().getHeight() >= needed - 0.5, "the wrapped text fits: " + laid.longCard().getHeight());
        assertTrue(laid.next().getLayoutY() >= laid.grid().getLayoutY() + laid.grid().getHeight(),
                "the next heading starts below the cards");
    }

    @Test
    void narrowPagesStackTheCards() {
        Laid laid = layout(500);

        assertEquals(laid.shortCard().getLayoutX(), laid.longCard().getLayoutX(), 0.5, "one column");
        assertTrue(laid.longCard().getLayoutY() >= laid.shortCard().getLayoutY() + laid.shortCard().getHeight());
    }

    @Test
    void theGridColourTintsEveryCard() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            SettingsCardGrid grid = new SettingsCardGrid();
            grid.setColor("#1373D9");
            SettingsCard card = card("A", "x");
            grid.getChildren().add(card);
            assertEquals("#1373D9", card.getColor());
            return null;
        });
    }
}
