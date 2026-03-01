package org.fxt.freexmltoolkit.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSortOrder;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.xsd.*;
import org.fxt.freexmltoolkit.service.xsd.adapters.XsdModelAdapter;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.shared.XmlSyntaxHighlighter;

import java.io.File;
import java.nio.file.Files;

/**
 * Controller for the Flatten Tab.
 */
public class FlattenTabController {

    private static final Logger logger = LogManager.getLogger(FlattenTabController.class);

    @FXML
    private Tab flattenTab;
    @FXML
    private TextField xsdToFlattenPath;
    @FXML
    private TextField flattenedXsdPath;
    @FXML
    private Button flattenXsdButton;
    @FXML
    private Label flattenStatusLabel;
    @FXML
    private CodeArea flattenedXsdTextArea;
    @FXML
    private ProgressIndicator flattenProgress;
    @FXML
    public CheckBox flattenIncludesCheckBox;
    @FXML
    public CheckBox flattenImportsCheckBox;
    @FXML
    public CheckBox removeCommentsCheckBox;
    @FXML
    public CheckBox addSourceFileAsAppinfoCheckBox;
    @FXML
    public RadioButton flattenSortTypeBeforeName;
    @FXML
    public RadioButton flattenSortNameBeforeType;
    @FXML
    public ToggleGroup flattenSortOrderGroup;

    private XsdController parentController;
    private final PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);

    @FXML
    public void initialize() {
        initializeFlattenButton();
    }

    private void initializeFlattenButton() {
        if (flattenXsdButton == null || xsdToFlattenPath == null || flattenedXsdPath == null) {
            return;
        }

        flattenXsdButton.setDisable(true);

        Runnable updateButtonState = () -> {
            String source = xsdToFlattenPath.getText();
            String dest = flattenedXsdPath.getText();
            boolean hasSource = source != null && !source.isBlank();
            boolean hasDest = dest != null && !dest.isBlank();
            flattenXsdButton.setDisable(!(hasSource && hasDest));
        };

        xsdToFlattenPath.textProperty().addListener((obs, oldVal, newVal) -> updateButtonState.run());
        flattenedXsdPath.textProperty().addListener((obs, oldVal, newVal) -> updateButtonState.run());

        updateButtonState.run();

        if (propertiesService != null && flattenSortTypeBeforeName != null && flattenSortNameBeforeType != null) {
            String savedSortOrder = propertiesService.getXsdSortOrder();
            if ("TYPE_BEFORE_NAME".equals(savedSortOrder)) {
                flattenSortTypeBeforeName.setSelected(true);
            } else {
                flattenSortNameBeforeType.setSelected(true);
            }
        }
    }

    @FXML
    public void openXsdToFlattenChooser() {
        File selectedFile = parentController.openXsdFileChooser();
        if (selectedFile != null) {
            xsdToFlattenPath.setText(selectedFile.getAbsolutePath());
            flattenedXsdPath.setText(getFlattenedPath(selectedFile.getAbsolutePath()));
            flattenXsdButton.setDisable(false);
        }
    }

    @FXML
    public void selectFlattenedXsdPath() {
        File file = parentController.showSaveDialog("Save Flattened XSD", "Flattened XSD Files", "*.xsd");
        if (file != null) {
            flattenedXsdPath.setText(file.getAbsolutePath());
        }
    }

    @FXML
    public void flattenXsdAction() {
        String sourcePath = xsdToFlattenPath.getText();
        String destinationPath = flattenedXsdPath.getText();

        if (sourcePath == null || sourcePath.isBlank() || destinationPath == null || destinationPath.isBlank()) {
            parentController.showAlert(Alert.AlertType.ERROR, "Error", "Please specify both a source and a destination file.");
            return;
        }

        File sourceFile = new File(sourcePath);
        File destinationFile = new File(destinationPath);

        boolean flattenIncludes = flattenIncludesCheckBox == null || flattenIncludesCheckBox.isSelected();
        boolean flattenImports = flattenImportsCheckBox != null && flattenImportsCheckBox.isSelected();
        boolean removeComments = removeCommentsCheckBox != null && removeCommentsCheckBox.isSelected();
        boolean addSourceFileAsAppinfo = addSourceFileAsAppinfoCheckBox != null && addSourceFileAsAppinfoCheckBox.isSelected();
        XsdSortOrder sortOrder = (flattenSortTypeBeforeName != null && flattenSortTypeBeforeName.isSelected())
                ? XsdSortOrder.TYPE_BEFORE_NAME
                : XsdSortOrder.NAME_BEFORE_TYPE;

        flattenProgress.setVisible(true);
        flattenStatusLabel.setText("Flattening in progress...");
        flattenedXsdTextArea.clear();

        Task<String> flattenTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                XsdParseOptions options = XsdParseOptions.builder()
                        .includeMode(flattenIncludes ? XsdParseOptions.IncludeMode.FLATTEN : XsdParseOptions.IncludeMode.PRESERVE_STRUCTURE)
                        .resolveImports(flattenImports)
                        .removeComments(removeComments)
                        .addSourceFileAsAppinfo(addSourceFileAsAppinfo)
                        .build();

                XsdParsingService parsingService = new XsdParsingServiceImpl();
                ParsedSchema parsedSchema = parsingService.parse(sourceFile.toPath(), options);

                XsdModelAdapter modelAdapter = new XsdModelAdapter(options);
                XsdSchema xsdModel = modelAdapter.toXsdModel(parsedSchema);

                XsdSerializer serializer = new XsdSerializer();
                String flattenedContent = serializer.serialize(xsdModel, sortOrder);

                Files.writeString(destinationFile.toPath(), flattenedContent);
                return flattenedContent;
            }
        };

        flattenTask.setOnSucceeded(event -> {
            flattenProgress.setVisible(false);
            String flattenedContent = flattenTask.getValue();
            flattenedXsdTextArea.replaceText(flattenedContent);
            flattenedXsdTextArea.setStyleSpans(0, XmlSyntaxHighlighter.computeHighlighting(flattenedContent));
            flattenStatusLabel.setText("Successfully flattened and saved to: " + destinationFile.getAbsolutePath());
            parentController.showAlert(Alert.AlertType.INFORMATION, "Success", "XSD has been flattened successfully.");
        });

        flattenTask.setOnFailed(event -> {
            flattenProgress.setVisible(false);
            flattenStatusLabel.setText("Error during flattening process.");
            parentController.showAlert(Alert.AlertType.ERROR, "Flattening Failed", "An error occurred: " + flattenTask.getException().getMessage());
        });

        parentController.executeBackgroundTask(flattenTask);
    }

    private String getFlattenedPath(String originalPath) {
        if (originalPath == null || !originalPath.toLowerCase().endsWith(".xsd")) {
            return "";
        }
        return originalPath.substring(0, originalPath.length() - 4) + "_flattened.xsd";
    }

    public void setParentController(XsdController parentController) {
        this.parentController = parentController;
    }
    
    public void setSourcePath(String path) {
        if (xsdToFlattenPath != null) {
            xsdToFlattenPath.setText(path);
            flattenedXsdPath.setText(getFlattenedPath(path));
        }
    }
}
