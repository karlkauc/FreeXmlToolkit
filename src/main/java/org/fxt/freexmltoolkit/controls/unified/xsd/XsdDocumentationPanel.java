package org.fxt.freexmltoolkit.controls.unified.xsd;

import java.io.File;

import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controller.DocumentationTabController;
import org.fxt.freexmltoolkit.controller.XsdToolHost;

/**
 * Panel embedding the full DocumentationTabController (via FXML) for XSD documentation generation.
 * Provides 1:1 feature parity with the standalone XSD Editor's Documentation tab.
 */
public class XsdDocumentationPanel extends StackPane implements XsdToolHost {

    private static final Logger logger = LogManager.getLogger(XsdDocumentationPanel.class);

    private DocumentationTabController documentationController;
    private boolean initialized = false;
    private File sourceFile;

    public XsdDocumentationPanel() {
        getChildren().add(new Label("Loading documentation tab..."));
    }

    /**
     * Sets the source XSD file and initializes the FXML controller if needed.
     */
    public void setSourceFile(File file) {
        this.sourceFile = file;

        if (!initialized) {
            initialize();
        }

        if (documentationController != null && file != null) {
            documentationController.setXsdFilePath(file.getAbsolutePath());
        }
    }

    /**
     * Loads the documentation_tab.fxml and wires the controller.
     */
    private void initialize() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/documentation_tab.fxml"));
            Node content = loader.load();
            documentationController = loader.getController();
            documentationController.setParentController(this);

            getChildren().clear();
            getChildren().add(content);
            initialized = true;

            logger.debug("Documentation panel initialized with FXML controller");
        } catch (Exception e) {
            logger.error("Failed to load documentation_tab.fxml: {}", e.getMessage(), e);
            getChildren().clear();
            getChildren().add(new Label("Error loading documentation tab: " + e.getMessage()));
        }
    }

    // ==================== XsdToolHost Implementation ====================

    @Override
    public <T> void executeBackgroundTask(Task<T> task) {
        Thread worker = new Thread(task, "DocGen-Worker");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void updateBackgroundTaskTimer(String time) {
        // Timer updates are handled internally by DocumentationTabController
    }

    @Override
    public File openXsdFileChooser() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select XSD File");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSD Files", "*.xsd"));
        return fc.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
    }

    @Override
    public File showSaveDialog(String title, String desc, String extension) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(desc, extension));
        return fc.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
    }

    @Override
    public void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        if (getScene() != null) {
            alert.initOwner(getScene().getWindow());
        }
        alert.showAndWait();
    }

    @Override
    public void openFolderInExplorer(File folder) {
        if (folder != null && folder.exists()) {
            try {
                java.awt.Desktop.getDesktop().open(folder);
            } catch (Exception e) {
                logger.warn("Failed to open folder: {}", e.getMessage());
            }
        }
    }
}
