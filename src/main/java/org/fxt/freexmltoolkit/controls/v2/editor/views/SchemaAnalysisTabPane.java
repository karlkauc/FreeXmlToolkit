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
 * <p>
 * Contains tabs for Statistics, Identity Constraints, XPath Validation, and Quality Checks.
 * This component provides a unified interface for analyzing XSD schemas from multiple perspectives,
 * including structural statistics, identity constraint visualization, XPath expression validation,
 * and schema quality assessments.
 * </p>
 * <p>
 * The tab pane automatically refreshes all contained views when the underlying schema changes,
 * using PropertyChangeListener pattern for reactive updates.
 * </p>
 *
 * @since 2.0
 */
public class SchemaAnalysisTabPane extends TabPane {

    /**
     * Logger for this class.
     */
    private static final Logger logger = LogManager.getLogger(SchemaAnalysisTabPane.class);

    /**
     * The XSD schema being analyzed.
     */
    private final XsdSchema schema;

    /**
     * Tab containing schema statistics.
     */
    private Tab statisticsTab;

    /**
     * Tab containing identity constraints analysis.
     */
    private Tab identityConstraintsTab;

    /**
     * Tab containing XPath validation tools.
     */
    private Tab xpathValidationTab;

    /**
     * Tab containing quality checks results.
     */
    private Tab qualityChecksTab;

    /**
     * View component for displaying schema statistics.
     */
    private SchemaStatisticsView statisticsView;

    /**
     * View component for displaying identity constraints.
     */
    private IdentityConstraintsView identityConstraintsView;

    /**
     * View component for XPath validation functionality.
     */
    private XPathValidationView xpathValidationView;

    /**
     * View component for displaying quality check results.
     */
    private QualityChecksView qualityChecksView;

    /**
     * Sample XML document used for XPath expression testing.
     */
    private Document sampleXml;

    /**
     * Listener that handles schema property changes and triggers view refresh.
     */
    private final PropertyChangeListener schemaChangeListener = this::onSchemaChanged;

    /**
     * Creates a new schema analysis tab pane for the specified XSD schema.
     * <p>
     * Initializes all analysis tabs (Statistics, Identity Constraints, XPath Validation,
     * and Quality Checks), registers schema change listeners, and applies styling.
     * The first tab (Statistics) is selected by default.
     * </p>
     *
     * @param schema the XSD schema to analyze; must not be null
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
     * Initializes all analysis tabs and their associated view components.
     * <p>
     * Creates tabs for Statistics, Identity Constraints, XPath Validation, and Quality Checks,
     * each with an appropriate icon and corresponding view component.
     * </p>
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
     * Creates a new tab with the specified text, icon, and content.
     *
     * @param text    the display text for the tab
     * @param icon    the Bootstrap icon to display in the tab header
     * @param content the JavaFX node to display as tab content
     * @return a new non-closable Tab instance configured with the specified properties
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
     * Registers the schema change listener to the underlying XSD schema.
     * <p>
     * When schema properties change, the listener triggers a refresh of all analysis views.
     * </p>
     */
    private void registerSchemaListener() {
        schema.addPropertyChangeListener(schemaChangeListener);
    }

    /**
     * Handles schema property change events by refreshing all analysis views.
     *
     * @param evt the property change event containing details about the schema modification
     */
    private void onSchemaChanged(PropertyChangeEvent evt) {
        logger.debug("Schema changed, refreshing analysis views");
        refreshAll();
    }

    /**
     * Refreshes all analysis views to reflect the current schema state.
     * <p>
     * This method updates the Statistics, Identity Constraints, XPath Validation,
     * and Quality Checks views. It is safe to call even if some views have not
     * been initialized (null checks are performed).
     * </p>
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
     * Sets the sample XML document for XPath expression testing.
     * <p>
     * The sample XML is used by the XPath Validation view to evaluate XPath expressions
     * against actual XML content. This allows users to test their XPath queries against
     * representative data.
     * </p>
     *
     * @param sampleXml the sample XML document to use for XPath testing; may be null to clear the sample
     */
    public void setSampleXml(Document sampleXml) {
        this.sampleXml = sampleXml;
        if (xpathValidationView != null) {
            xpathValidationView.setSampleXml(sampleXml);
        }
    }

    /**
     * Returns the statistics view component.
     * <p>
     * The statistics view displays counts and metrics about the XSD schema structure,
     * including element counts, type counts, and complexity metrics.
     * </p>
     *
     * @return the schema statistics view component
     */
    public SchemaStatisticsView getStatisticsView() {
        return statisticsView;
    }

    /**
     * Returns the identity constraints view component.
     * <p>
     * The identity constraints view displays key, keyref, and unique constraints
     * defined in the schema.
     * </p>
     *
     * @return the identity constraints view component
     */
    public IdentityConstraintsView getIdentityConstraintsView() {
        return identityConstraintsView;
    }

    /**
     * Returns the XPath validation view component.
     * <p>
     * The XPath validation view allows users to test XPath expressions against
     * sample XML documents and validates XPath expressions used in the schema.
     * </p>
     *
     * @return the XPath validation view component
     */
    public XPathValidationView getXPathValidationView() {
        return xpathValidationView;
    }

    /**
     * Returns the quality checks view component.
     * <p>
     * The quality checks view performs and displays schema quality assessments,
     * including best practice recommendations and potential issues.
     * </p>
     *
     * @return the quality checks view component
     */
    public QualityChecksView getQualityChecksView() {
        return qualityChecksView;
    }

    /**
     * Selects the statistics tab and brings it to focus.
     * <p>
     * This method programmatically switches the visible tab to the Statistics tab,
     * allowing users to view schema statistics and metrics.
     * </p>
     */
    public void selectStatisticsTab() {
        getSelectionModel().select(statisticsTab);
    }

    /**
     * Selects the identity constraints tab and brings it to focus.
     * <p>
     * This method programmatically switches the visible tab to the Identity Constraints tab,
     * allowing users to view key, keyref, and unique constraint definitions.
     * </p>
     */
    public void selectIdentityConstraintsTab() {
        getSelectionModel().select(identityConstraintsTab);
    }

    /**
     * Selects the XPath validation tab and brings it to focus.
     * <p>
     * This method programmatically switches the visible tab to the XPath Validation tab,
     * allowing users to test and validate XPath expressions.
     * </p>
     */
    public void selectXPathValidationTab() {
        getSelectionModel().select(xpathValidationTab);
    }

    /**
     * Selects the quality checks tab and brings it to focus.
     * <p>
     * This method programmatically switches the visible tab to the Quality Checks tab,
     * allowing users to view schema quality assessments and recommendations.
     * </p>
     */
    public void selectQualityChecksTab() {
        getSelectionModel().select(qualityChecksTab);
    }

    /**
     * Releases resources and cleans up listeners associated with this tab pane.
     * <p>
     * This method should be called when the tab pane is no longer needed to prevent
     * memory leaks. It unregisters the schema change listener and disposes all
     * contained view components.
     * </p>
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
