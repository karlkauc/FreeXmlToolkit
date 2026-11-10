package org.fxt.freexmltoolkit.controls.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Density on the spacing set in FXML / code: scaled, never compounded, restored at Normal. */
@ExtendWith(ApplicationExtension.class)
class DensityScalerTest {

    private VBox root;
    private HBox row;
    private GridPane grid;
    private Label styled;
    private TabPane tabs;

    @Start
    void start(Stage stage) {
        row = new HBox(6);
        grid = new GridPane();
        grid.setHgap(8);
        styled = new Label("css");
        styled.setStyle("-fx-padding: 4;");
        tabs = new TabPane();
        root = new VBox(10, row, grid, styled, tabs);
        root.setPadding(new Insets(8, 12, 8, 12));
        stage.setScene(new Scene(root, 300, 200));
        stage.show();
    }

    @AfterEach
    void normal() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            DensityScaler.setFactor(1.0);
            DensityScaler.apply(root);
        });
    }

    @Test
    void scalesUserSpacingAndKeepsFollowingTheTree() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            DensityScaler.setFactor(0.5);
            DensityScaler.apply(root);
        });
        assertEquals(5, root.getSpacing());
        assertEquals(new Insets(4, 12, 4, 12), root.getPadding(), "vertical room only");
        assertEquals(6, row.getSpacing(), "horizontal spacing stays");
        assertEquals(8, grid.getHgap());
        assertEquals(new Insets(4), styled.getPadding(), "stylesheet / inline CSS values stay");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            DensityScaler.apply(root); // again: no compounding
            VBox later = new VBox(20);
            row.getChildren().add(later);
            tabs.getTabs().add(new Tab("t", new VBox(14)));
        });
        assertEquals(5, root.getSpacing());
        assertEquals(10, ((VBox) row.getChildren().getFirst()).getSpacing(), "added later");
        assertEquals(7, ((VBox) tabs.getTabs().getFirst().getContent()).getSpacing(), "a tab opened later");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            DensityScaler.setFactor(1.0);
            DensityScaler.apply(root);
        });
        assertEquals(10, root.getSpacing());
        assertEquals(new Insets(8, 12, 8, 12), root.getPadding());
        assertEquals(20, ((VBox) row.getChildren().getFirst()).getSpacing());
    }
}
