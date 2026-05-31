package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.io.IOException;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * In-app PDF preview: renders one page at a time (via {@link PdfPreviewRenderer})
 * with prev/next navigation. Pages are rasterised on demand off the UI thread,
 * so large documents stay responsive and memory-light.
 */
public class PdfPreview extends BorderPane {

    private static final float DPI = 120f;

    private final File pdf;
    private final int pageCount;
    private int pageIndex;
    private final ImageView imageView = new ImageView();
    private final Label pageLabel = new Label();
    private final Button prev;
    private final Button next;

    public PdfPreview(File pdf) throws IOException {
        this.pdf = pdf;
        this.pageCount = PdfPreviewRenderer.pageCount(pdf);
        getStyleClass().add("fxt-pdf-preview");

        imageView.setPreserveRatio(true);
        imageView.getStyleClass().add("fxt-pdf-page");
        ScrollPane scroll = new ScrollPane(imageView);
        scroll.setFitToWidth(true);
        scroll.setPannable(true);
        imageView.fitWidthProperty().bind(scroll.widthProperty().subtract(24));

        prev = navButton("bi-chevron-left", "Previous page", () -> showPage(pageIndex - 1));
        next = navButton("bi-chevron-right", "Next page", () -> showPage(pageIndex + 1));
        pageLabel.getStyleClass().add("fxt-toolbar-status");
        HBox toolbar = new HBox(8, prev, pageLabel, next);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("fxt-editor-toolbar");

        setTop(toolbar);
        setCenter(scroll);
        showPage(0);
    }

    /** @return the number of pages in the previewed document. */
    public int getPageCount() {
        return pageCount;
    }

    /** @return the current (zero-based) page index. */
    public int getPageIndex() {
        return pageIndex;
    }

    private void showPage(int index) {
        if (index < 0 || index >= pageCount) {
            return;
        }
        pageIndex = index;
        pageLabel.setText("Page " + (index + 1) + " / " + pageCount);
        prev.setDisable(index == 0);
        next.setDisable(index == pageCount - 1);
        FxtGui.executorService.submit(() -> {
            try {
                var fx = SwingFXUtils.toFXImage(PdfPreviewRenderer.renderPage(pdf, index, DPI), null);
                Platform.runLater(() -> imageView.setImage(fx));
            } catch (Exception e) {
                Platform.runLater(() -> pageLabel.setText("Error rendering page: " + e.getMessage()));
            }
        });
    }

    private Button navButton(String icon, String tooltip, Runnable action) {
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        Button button = new Button(null, graphic);
        button.getStyleClass().add("fxt-tool-button");
        button.setTooltip(new javafx.scene.control.Tooltip(tooltip));
        button.setOnAction(e -> action.run());
        return button;
    }
}
