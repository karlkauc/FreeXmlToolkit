package org.fxt.freexmltoolkit.controller.dialogs;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.controller.XsdController;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the XSD Overview Dialog.
 *
 * This dialog provides:
 * - Welcome message and feature overview
 * - Quick Start actions (New XSD, Load Favorites, Browse Files)
 * - Keyboard shortcuts reference
 * - "Don't show again" option
 *
 * @since 2.0
 */
public class XsdOverviewDialogController {

    private static final Logger logger = LoggerFactory.getLogger(XsdOverviewDialogController.class);
    private static final String PREF_SHOW_XSD_OVERVIEW = "showXsdOverviewOnStartup";

    @FXML
    private Button newXsdButton;

    @FXML
    private Button loadFavoritesButton;

    @FXML
    private Button browseFilesButton;

    @FXML
    private CheckBox dontShowAgainCheckbox;

    @FXML
    private Button closeButton;

    private XsdController xsdController;
    private Stage dialogStage;

    /**
     * Initialize the controller
     */
    @FXML
    public void initialize() {
        logger.debug("XsdOverviewDialog initialized");
    }

    /**
     * Set the XSD controller for callback actions
     */
    public void setXsdController(XsdController xsdController) {
        this.xsdController = xsdController;
    }

    /**
     * Set the dialog stage
     */
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    /**
     * Handle New XSD button click
     */
    @FXML
    private void handleNewXsd() {
        logger.info("New XSD requested from overview dialog");
        if (xsdController != null) {
            closeDialog();
            xsdController.handleCreateNewXsdFile();
        }
    }

    /**
     * Handle Load Favorites button click
     */
    @FXML
    private void handleLoadFavorites() {
        logger.info("Load Favorites requested from overview dialog");
        if (xsdController != null) {
            closeDialog();
            xsdController.handleShowFavorites();
        }
    }

    /**
     * Handle Browse Files button click
     */
    @FXML
    private void handleBrowseFiles() {
        logger.info("Browse Files requested from overview dialog");
        if (xsdController != null) {
            closeDialog();
            xsdController.handleOpenXsdFileChooser();
        }
    }

    /**
     * Handle Close button click
     */
    @FXML
    private void handleClose() {
        closeDialog();
    }

    /**
     * Close the dialog and save "don't show again" preference
     */
    private void closeDialog() {
        // Save "don't show again" preference
        if (dontShowAgainCheckbox.isSelected()) {
            ServiceRegistry.get(PropertiesService.class).set(PREF_SHOW_XSD_OVERVIEW, "false");
            logger.info("XSD Overview dialog will not show on startup anymore");
        }

        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    /**
     * Check if the overview dialog should be shown on startup
     */
    public static boolean shouldShowOnStartup() {
        String showOnStartup = ServiceRegistry.get(PropertiesService.class).get(PREF_SHOW_XSD_OVERVIEW);
        return showOnStartup == null || "true".equalsIgnoreCase(showOnStartup);
    }

    /**
     * Reset the "don't show again" preference (for testing or user request)
     */
    public static void resetShowOnStartup() {
        ServiceRegistry.get(PropertiesService.class).set(PREF_SHOW_XSD_OVERVIEW, "true");
        logger.info("XSD Overview dialog preference reset - will show on startup");
    }
}
