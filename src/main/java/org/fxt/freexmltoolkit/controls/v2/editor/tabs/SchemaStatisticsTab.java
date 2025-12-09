package org.fxt.freexmltoolkit.controls.v2.editor.tabs;

import javafx.scene.control.Tab;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.views.SchemaAnalysisTabPane;
import org.fxt.freexmltoolkit.controls.v2.editor.views.SchemaStatisticsView;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.javafx.FontIcon;
import org.w3c.dom.Document;

/**
 * Tab displaying comprehensive schema analysis including statistics,
 * identity constraints, XPath validation, and quality checks.
 * Singleton pattern - only one analysis tab per schema.
 * Read-only tab, no dirty state.
 *
 * @since 2.0
 */
public class SchemaStatisticsTab extends Tab {

    private static final Logger logger = LogManager.getLogger(SchemaStatisticsTab.class);

    private final XsdSchema schema;
    private final SchemaAnalysisTabPane analysisTabPane;

    /**
     * Creates a new schema analysis tab.
     *
     * @param schema the schema to display analysis for
     */
    public SchemaStatisticsTab(XsdSchema schema) {
        super("Schema Analysis");
        this.schema = schema;

        // Set tab icon
        FontIcon icon = new FontIcon(BootstrapIcons.CLIPBOARD_DATA);
        icon.setIconSize(14);
        setGraphic(icon);

        // Create analysis tab pane with all sub-tabs
        analysisTabPane = new SchemaAnalysisTabPane(schema);
        setContent(analysisTabPane);

        // Tab cannot be closed while editing - but it's read-only so allow closing
        setClosable(true);

        // Cleanup on close
        setOnClosed(e -> dispose());

        logger.debug("SchemaStatisticsTab created with full analysis for schema");
    }

    /**
     * Gets the schema this tab displays analysis for.
     *
     * @return the schema
     */
    public XsdSchema getSchema() {
        return schema;
    }

    /**
     * Gets the statistics view.
     *
     * @return the statistics view
     */
    public SchemaStatisticsView getStatisticsView() {
        return analysisTabPane.getStatisticsView();
    }

    /**
     * Gets the analysis tab pane containing all sub-tabs.
     *
     * @return the analysis tab pane
     */
    public SchemaAnalysisTabPane getAnalysisTabPane() {
        return analysisTabPane;
    }

    /**
     * Sets the sample XML document for XPath validation testing.
     *
     * @param sampleXml the sample XML document
     */
    public void setSampleXml(Document sampleXml) {
        analysisTabPane.setSampleXml(sampleXml);
    }

    /**
     * Refreshes all analysis views.
     */
    public void refresh() {
        analysisTabPane.refreshAll();
    }

    /**
     * Cleanup resources when the tab is closed.
     */
    public void dispose() {
        logger.debug("Disposing SchemaStatisticsTab");
        analysisTabPane.dispose();
    }
}
