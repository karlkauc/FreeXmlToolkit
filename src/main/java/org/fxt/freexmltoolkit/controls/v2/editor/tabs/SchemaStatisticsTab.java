package org.fxt.freexmltoolkit.controls.v2.editor.tabs;

import javafx.scene.control.Tab;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.views.SchemaStatisticsView;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Tab displaying comprehensive statistics about an XSD schema.
 * Singleton pattern - only one statistics tab per schema.
 * Read-only tab, no dirty state.
 *
 * @since 2.0
 */
public class SchemaStatisticsTab extends Tab {

    private static final Logger logger = LogManager.getLogger(SchemaStatisticsTab.class);

    private final XsdSchema schema;
    private final SchemaStatisticsView statisticsView;

    /**
     * Creates a new schema statistics tab.
     *
     * @param schema the schema to display statistics for
     */
    public SchemaStatisticsTab(XsdSchema schema) {
        super("Schema Statistics");
        this.schema = schema;

        // Set tab icon
        FontIcon icon = new FontIcon(BootstrapIcons.BAR_CHART);
        icon.setIconSize(14);
        setGraphic(icon);

        // Create statistics view
        statisticsView = new SchemaStatisticsView(schema);
        setContent(statisticsView);

        // Tab cannot be closed while editing - but it's read-only so allow closing
        setClosable(true);

        // Cleanup on close
        setOnClosed(e -> dispose());

        logger.debug("SchemaStatisticsTab created for schema");
    }

    /**
     * Gets the schema this tab displays statistics for.
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
        return statisticsView;
    }

    /**
     * Refreshes the statistics display.
     */
    public void refresh() {
        statisticsView.refreshStatistics();
    }

    /**
     * Cleanup resources when the tab is closed.
     */
    public void dispose() {
        logger.debug("Disposing SchemaStatisticsTab");
        statisticsView.dispose();
    }
}
