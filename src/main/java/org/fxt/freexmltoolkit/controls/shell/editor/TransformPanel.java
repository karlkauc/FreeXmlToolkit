package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The Transform activity side panel: transforms the active XML with a chosen
 * XSLT, and evaluates XPath expressions against it. Reuses {@link TransformRunner}
 * (Saxon / XmlService); runs off the UI thread and shows the result in a panel
 * output area.
 * <p>
 * Full XSLT-developer features (parameters, live preview, saved XQueries) are
 * follow-up increments.
 */
public class TransformPanel extends VBox {

    private final EditorHost editorHost;
    private final TextArea output = new TextArea();
    private final TextField xpathField = new TextField();
    private final Label pathLabel = new Label("XPATH");
    private final Label xsltStatus = new Label("XSLT: none");
    private File xsltFile;

    public TransformPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("TRANSFORM");
        title.getStyleClass().add("fxt-side-panel-title");

        Button setXslt = button("Set XSLT…", "bi-file-earmark-code", this::chooseXslt);
        Button transform = button("Transform", "bi-arrow-repeat", this::transform);
        xsltStatus.getStyleClass().add("fxt-placeholder-text");

        pathLabel.getStyleClass().add("fxt-side-panel-title");
        xpathField.getStyleClass().add("fxt-xpath-field");
        Button runXPath = button("Run", "bi-lightning-charge", this::runXPath);
        xpathField.setOnAction(e -> runXPath());
        updatePathMode();
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> updatePathMode());

        Label resultLabel = new Label("RESULT");
        resultLabel.getStyleClass().add("fxt-side-panel-title");
        output.setEditable(false);
        output.getStyleClass().add("fxt-transform-output");
        VBox.setVgrow(output, Priority.ALWAYS);

        getChildren().addAll(title, new HBox(6, setXslt, transform), xsltStatus,
                pathLabel, new HBox(6, xpathField, runXPath),
                resultLabel, output);
    }

    /** Sets the stylesheet used by {@link #transform()} (also from the file chooser). */
    public void setXsltFile(File file) {
        this.xsltFile = file;
        xsltStatus.setText(file != null ? "XSLT: " + file.getName() : "XSLT: none");
    }

    /** Transforms the active XML with the selected XSLT (async). */
    public void transform() {
        if (xsltFile == null) {
            output.setText("Select an XSLT stylesheet first.");
            return;
        }
        if (editorHost.getActiveDocument().isEmpty()) {
            output.setText("No document open.");
            return;
        }
        String xml = editorHost.getActiveText().orElse("");
        File xslt = xsltFile;
        output.setText("Transforming…");
        FxtGui.executorService.submit(() -> {
            String result;
            try {
                result = TransformRunner.xsltTransform(xml, Files.readString(xslt.toPath(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                result = "ERROR: " + e.getMessage();
            }
            String finalResult = result;
            Platform.runLater(() -> output.setText(finalResult));
        });
    }

    /** Evaluates the XPath field against the active XML (async). */
    public void runXPath() {
        if (editorHost.getActiveDocument().isEmpty()) {
            output.setText("No document open.");
            return;
        }
        String content = editorHost.getActiveText().orElse("");
        String path = xpathField.getText();
        if (path == null || path.isBlank()) {
            return;
        }
        boolean json = isJsonActive();
        output.setText("Running…");
        FxtGui.executorService.submit(() -> {
            String result = json ? TransformRunner.runJsonPath(content, path)
                    : TransformRunner.runXPath(content, path);
            Platform.runLater(() -> output.setText(result));
        });
    }

    private boolean isJsonActive() {
        return editorHost.getActiveDocument()
                .map(d -> d.getFileType() == EditorFileType.JSON).orElse(false);
    }

    private void updatePathMode() {
        boolean json = isJsonActive();
        pathLabel.setText(json ? "JSONPATH" : "XPATH");
        xpathField.setPromptText(json ? "$.root.element" : "/root/element");
    }

    /** @return the current output text (for tests/observers). */
    public String getOutputText() {
        return output.getText();
    }

    /** Sets the XPath expression (for tests/observers). */
    public void setXPathExpression(String expression) {
        xpathField.setText(expression);
    }

    private void chooseXslt() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select XSLT");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSLT", "*.xsl", "*.xslt"));
        File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            setXsltFile(file);
        }
    }

    private Button button(String text, String icon, Runnable action) {
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        Button button = new Button(text, graphic);
        button.getStyleClass().add("fxt-tool-button");
        button.setOnAction(e -> action.run());
        return button;
    }
}
