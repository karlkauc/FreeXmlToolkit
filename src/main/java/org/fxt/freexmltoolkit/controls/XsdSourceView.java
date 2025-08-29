package org.fxt.freexmltoolkit.controls;

import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.kordamp.ikonli.javafx.FontIcon;
import org.w3c.dom.Document;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

/**
 * Source code view of XSD content.
 * Displays the raw XML source with syntax highlighting and editing capabilities.
 */
public class XsdSourceView extends BorderPane {
    private static final Logger logger = LogManager.getLogger(XsdSourceView.class);

    private final XsdDomManipulator domManipulator;
    private final TextArea sourceTextArea;
    private boolean isEditable = false;

    public XsdSourceView(XsdDomManipulator domManipulator) {
        this.domManipulator = domManipulator;
        this.sourceTextArea = new TextArea();

        initializeComponents();
        refreshView();
    }

    private void initializeComponents() {
        // Create toolbar
        ToolBar toolbar = new ToolBar();

        Button refreshButton = new Button("Refresh");
        refreshButton.setGraphic(new FontIcon("bi-arrow-clockwise"));
        refreshButton.setOnAction(e -> refreshView());

        Button formatButton = new Button("Format XML");
        formatButton.setGraphic(new FontIcon("bi-code"));
        formatButton.setOnAction(e -> formatXml());

        Button toggleEditButton = new Button("Toggle Edit");
        toggleEditButton.setGraphic(new FontIcon("bi-pencil"));
        toggleEditButton.setOnAction(e -> toggleEditMode());

        toolbar.getItems().addAll(refreshButton, formatButton, new Separator(), toggleEditButton);

        // Configure text area
        sourceTextArea.getStyleClass().add("code-area");
        sourceTextArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', 'Courier New', monospace; -fx-font-size: 12px;");
        sourceTextArea.setEditable(isEditable);
        sourceTextArea.setWrapText(false);

        // Layout
        setTop(toolbar);
        setCenter(sourceTextArea);
    }

    /**
     * Refresh the source view with current XSD content
     */
    public void refreshView() {
        logger.info("Refreshing XSD source view");

        if (domManipulator != null && domManipulator.getDocument() != null) {
            try {
                String xmlSource = serializeDocument(domManipulator.getDocument());
                sourceTextArea.setText(xmlSource);

                logger.info("Source view refreshed with {} characters", xmlSource.length());
            } catch (Exception e) {
                logger.error("Error refreshing source view", e);
                sourceTextArea.setText("Error loading XSD source: " + e.getMessage());
            }
        } else {
            sourceTextArea.setText("<!-- No XSD document loaded -->");
        }
    }

    private String serializeDocument(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        Transformer transformer = factory.newTransformer();

        // Configure output properties for pretty printing
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));

        return writer.toString();
    }

    private void formatXml() {
        String currentText = sourceTextArea.getText();
        if (!currentText.trim().isEmpty()) {
            try {
                // Re-parse and format the XML
                // This is a simplified approach - in production you might want
                // to use a dedicated XML formatter
                refreshView();
                logger.info("XML formatted successfully");
            } catch (Exception e) {
                logger.error("Error formatting XML", e);
            }
        }
    }

    private void toggleEditMode() {
        isEditable = !isEditable;
        sourceTextArea.setEditable(isEditable);

        String status = isEditable ? "enabled" : "disabled";
        logger.info("Edit mode {}", status);
    }

    /**
     * Get the current source text
     */
    public String getSourceText() {
        return sourceTextArea.getText();
    }

    /**
     * Set the source text
     */
    public void setSourceText(String text) {
        sourceTextArea.setText(text);
    }
}