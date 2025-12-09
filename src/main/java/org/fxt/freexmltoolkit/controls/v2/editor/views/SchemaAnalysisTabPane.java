package org.fxt.freexmltoolkit.controls.v2.editor.views;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.javafx.FontIcon;
import org.w3c.dom.Document;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Container TabPane for schema analysis views.
 * Contains tabs for Statistics, Identity Constraints, XPath Validation, and Quality Checks.
 *
 * @since 2.0
 */
public class SchemaAnalysisTabPane extends TabPane {

    private static final Logger logger = LogManager.getLogger(SchemaAnalysisTabPane.class);

    private final XsdSchema schema;

    // Tabs
    private Tab statisticsTab;
    private Tab identityConstraintsTab;
    private Tab xpathValidationTab;
    private Tab qualityChecksTab;

    // Views
    private SchemaStatisticsView statisticsView;
    private IdentityConstraintsView identityConstraintsView;
    private XPathValidationView xpathValidationView;
    private QualityChecksView qualityChecksView;

    // Sample XML for XPath testing
    private Document sampleXml;

    // Schema change listener
    private final PropertyChangeListener schemaChangeListener = this::onSchemaChanged;

    /**
     * Creates a new schema analysis tab pane.
     *
     * @param schema the XSD schema to analyze
     */
    public SchemaAnalysisTabPane(XsdSchema schema) {
        this.schema = schema;
        initializeTabs();
        registerSchemaListener();

        // Style
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        getStyleClass().add("schema-analysis-tabpane");

        logger.debug("SchemaAnalysisTabPane created");
    }

    /**
     * Initializes all tabs.
     */
    private void initializeTabs() {
        // Statistics Tab (reuse existing view)
        statisticsView = new SchemaStatisticsView(schema);
        statisticsTab = createTab("Statistics", BootstrapIcons.BAR_CHART, statisticsView);

        // Identity Constraints Tab
        identityConstraintsView = new IdentityConstraintsView(schema);
        identityConstraintsTab = createTab("Identity Constraints", BootstrapIcons.KEY, identityConstraintsView);

        // XPath Validation Tab
        xpathValidationView = new XPathValidationView(schema);
        xpathValidationTab = createTab("XPath Validation", BootstrapIcons.CHECK2_CIRCLE, xpathValidationView);

        // Quality Checks Tab
        qualityChecksView = new QualityChecksView(schema);
        qualityChecksTab = createTab("Quality", BootstrapIcons.AWARD, qualityChecksView);

        // Add tabs
        getTabs().addAll(statisticsTab, identityConstraintsTab, xpathValidationTab, qualityChecksTab);

        // Select first tab
        getSelectionModel().selectFirst();
    }

    /**
     * Creates a tab with icon.
     */
    private Tab createTab(String text, BootstrapIcons icon, javafx.scene.Node content) {
        Tab tab = new Tab(text);
        FontIcon fontIcon = new FontIcon(icon);
        fontIcon.setIconSize(14);
        tab.setGraphic(fontIcon);
        tab.setContent(content);
        tab.setClosable(false);
        return tab;
    }

    /**
     * Registers the schema change listener.
     */
    private void registerSchemaListener() {
        schema.addPropertyChangeListener(schemaChangeListener);
    }

    /**
     * Called when the schema changes.
     */
    private void onSchemaChanged(PropertyChangeEvent evt) {
        logger.debug("Schema changed, refreshing analysis views");
        refreshAll();
    }

    /**
     * Refreshes all analysis views.
     */
    public void refreshAll() {
        if (statisticsView != null) {
            statisticsView.refreshStatistics();
        }
        if (identityConstraintsView != null) {
            identityConstraintsView.refresh();
        }
        if (xpathValidationView != null) {
            xpathValidationView.refresh();
        }
        if (qualityChecksView != null) {
            qualityChecksView.refresh();
        }
    }

    /**
     * Sets the sample XML for XPath testing.
     *
     * @param sampleXml the sample XML document
     */
    public void setSampleXml(Document sampleXml) {
        this.sampleXml = sampleXml;
        if (xpathValidationView != null) {
            xpathValidationView.setSampleXml(sampleXml);
        }
    }

    /**
     * Gets the statistics view.
     */
    public SchemaStatisticsView getStatisticsView() {
        return statisticsView;
    }

    /**
     * Gets the identity constraints view.
     */
    public IdentityConstraintsView getIdentityConstraintsView() {
        return identityConstraintsView;
    }

    /**
     * Gets the XPath validation view.
     */
    public XPathValidationView getXPathValidationView() {
        return xpathValidationView;
    }

    /**
     * Gets the quality checks view.
     */
    public QualityChecksView getQualityChecksView() {
        return qualityChecksView;
    }

    /**
     * Selects the statistics tab.
     */
    public void selectStatisticsTab() {
        getSelectionModel().select(statisticsTab);
    }

    /**
     * Selects the identity constraints tab.
     */
    public void selectIdentityConstraintsTab() {
        getSelectionModel().select(identityConstraintsTab);
    }

    /**
     * Selects the XPath validation tab.
     */
    public void selectXPathValidationTab() {
        getSelectionModel().select(xpathValidationTab);
    }

    /**
     * Selects the quality checks tab.
     */
    public void selectQualityChecksTab() {
        getSelectionModel().select(qualityChecksTab);
    }

    /**
     * Cleanup resources.
     */
    public void dispose() {
        schema.removePropertyChangeListener(schemaChangeListener);
        if (statisticsView != null) {
            statisticsView.dispose();
        }
        if (identityConstraintsView != null) {
            identityConstraintsView.dispose();
        }
        if (xpathValidationView != null) {
            xpathValidationView.dispose();
        }
        if (qualityChecksView != null) {
            qualityChecksView.dispose();
        }
    }
}
