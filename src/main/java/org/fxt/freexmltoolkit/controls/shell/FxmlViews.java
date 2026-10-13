package org.fxt.freexmltoolkit.controls.shell;

import java.io.IOException;
import java.net.URL;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

/**
 * Loads the static layout of a view from its FXML file under {@code /pages/}. The layout
 * (structure, texts, icons, style classes, ids) lives in FXML so it can be maintained apart
 * from the behaviour; the Java class keeps listeners, runners and data-driven content.
 *
 * <p>Convention: the file is named after the class ({@code pages/panels/FopPanel.fxml} for
 * {@code FopPanel}); {@code FxmlContractTest} checks {@code fx:id}s and handlers against it.
 */
public final class FxmlViews {

    private FxmlViews() {
    }

    /**
     * Loads an {@code fx:root} layout into {@code view}, which is both the root node and the
     * controller. Call it from the view's constructor once the fields the handlers need are
     * set; {@code @FXML} fields are injected when it returns.
     *
     * @param view     the custom control whose base type matches the {@code fx:root} type
     * @param resource absolute classpath path, e.g. {@code /pages/panels/FopPanel.fxml}
     */
    public static void load(Node view, String resource) {
        FXMLLoader loader = loader(resource);
        loader.setRoot(view);
        loader.setController(view);
        run(loader, resource);
    }

    /**
     * Loads a plain (non-{@code fx:root}) layout with {@code controller} as its controller and
     * returns the root node, e.g. the content of a dialog pane.
     *
     * @param controller the object receiving the {@code @FXML} fields and handlers
     * @param resource   absolute classpath path, e.g. {@code /pages/dialogs/NewFileDialog.fxml}
     */
    public static <T extends Node> T loadContent(Object controller, String resource) {
        FXMLLoader loader = loader(resource);
        loader.setController(controller);
        return run(loader, resource);
    }

    private static FXMLLoader loader(String resource) {
        URL url = FxmlViews.class.getResource(resource);
        if (url == null) {
            throw new IllegalStateException("FXML not found: " + resource);
        }
        return new FXMLLoader(url);
    }

    private static <T> T run(FXMLLoader loader, String resource) {
        try {
            return loader.load();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + resource, e);
        }
    }
}
