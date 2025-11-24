package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import javafx.geometry.Orientation;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.*;

import java.beans.PropertyChangeEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Hybrid view combining tree and grid views in XMLSpy style.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>TreeView as main navigation</li>
 *   <li>Automatic grid display for repeating elements</li>
 *   <li>Dynamic split pane with collapsible grid section</li>
 *   <li>Seamless integration with XmlEditorContext</li>
 * </ul>
 *
 * <p>Behavior:</p>
 * When a node with repeating child elements (e.g., &lt;catalog&gt; with multiple &lt;product&gt;)
 * is selected in the tree, the grid view automatically displays those repeating elements
 * in a table format below or beside the tree.
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlHybridView extends BorderPane {

    private final XmlEditorContext context;
    private final XmlTreeView treeView;
    private final XmlGridView gridView;
    private final SplitPane splitPane;

    private static final double DEFAULT_DIVIDER_POSITION = 0.6;
    private boolean gridVisible = false;

    // ==================== Constructor ====================

    public XmlHybridView(XmlEditorContext context) {
        this.context = context;
        this.treeView = new XmlTreeView(context);
        this.gridView = new XmlGridView(context);
        this.splitPane = new SplitPane();

        setupLayout();
        setupListeners();

        // Check if document is already loaded (listener would miss the initial load event)
        checkInitialDocument();
    }

    // ==================== Setup Methods ====================

    private void setupLayout() {
        // Configure split pane
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.setDividerPositions(DEFAULT_DIVIDER_POSITION);

        // Initially show only tree view
        splitPane.getItems().add(treeView);

        setCenter(splitPane);
    }

    private void setupListeners() {
        // Listen to selection changes in the context
        context.getSelectionModel().addPropertyChangeListener("selectedNode", this::onSelectionChanged);

        // Listen to document changes to trigger initial detection
        context.addPropertyChangeListener("document", this::onDocumentChanged);
    }

    /**
     * Checks if a document is already loaded when the view is created.
     * This handles the case where the document is loaded before the listeners are attached.
     */
    private void checkInitialDocument() {
        System.out.println("[XmlHybridView] Checking for initial document...");
        XmlDocument doc = context.getDocument();
        if (doc != null) {
            System.out.println("[XmlHybridView] Initial document found, processing...");
            processDocument(doc);
        } else {
            System.out.println("[XmlHybridView] No initial document found");
        }
    }

    /**
     * Called when document changes. Triggers initial repeating element detection.
     */
    private void onDocumentChanged(PropertyChangeEvent evt) {
        XmlDocument doc = (XmlDocument) evt.getNewValue();
        processDocument(doc);
    }

    /**
     * Processes a document to detect and display repeating elements.
     */
    private void processDocument(XmlDocument doc) {
        System.out.println("[XmlHybridView] Processing document...");
        // Check if root element has repeating children
        if (doc != null && doc.getChildCount() > 0) {
            List<XmlNode> children = doc.getChildren();
            System.out.println("[XmlHybridView] Document has " + children.size() + " children");

            // Find root element (skip comments, processing instructions, etc.)
            for (XmlNode child : children) {
                System.out.println("[XmlHybridView] Child: " + child.getClass().getSimpleName());
                if (child instanceof XmlElement) {
                    XmlElement root = (XmlElement) child;
                    System.out.println("[XmlHybridView] Root element: " + root.getName() + " with " + root.getChildCount() + " children");

                    // Check for repeating elements in root
                    Optional<RepeatingElementInfo> repeatingInfo = detectRepeatingElements(root);
                    if (repeatingInfo.isPresent()) {
                        System.out.println("[XmlHybridView] ✓ Found " + repeatingInfo.get().count + " repeating '" + repeatingInfo.get().elementName + "' elements");
                        System.out.println("[XmlHybridView] ✓ Calling showGridView()...");
                        // Show the grid view for repeating elements
                        showGridView(root, repeatingInfo.get().elementName, repeatingInfo.get().count);
                        System.out.println("[XmlHybridView] ✓ Grid view shown, gridVisible=" + gridVisible);
                    } else {
                        System.out.println("[XmlHybridView] ✗ No repeating elements found");
                        hideGridView();
                    }
                    break;
                }
            }
        } else {
            System.out.println("[XmlHybridView] Document is null or has no children");
            hideGridView();
        }
    }

    // ==================== Selection Handling ====================

    /**
     * Called when selection changes in the editor context.
     * Automatically detects repeating elements and shows/hides grid view.
     */
    private void onSelectionChanged(PropertyChangeEvent evt) {
        XmlNode selectedNode = (XmlNode) evt.getNewValue();

        if (selectedNode == null) {
            hideGridView();
            return;
        }

        // Check if selected node has repeating child elements
        if (selectedNode instanceof XmlElement || selectedNode instanceof XmlDocument) {
            Optional<RepeatingElementInfo> repeatingInfo = detectRepeatingElements(selectedNode);

            if (repeatingInfo.isPresent()) {
                RepeatingElementInfo info = repeatingInfo.get();
                showGridView(selectedNode, info.elementName, info.count);
            } else {
                hideGridView();
            }
        } else {
            hideGridView();
        }
    }

    /**
     * Detects if a node has repeating child elements.
     * Uses the shouldBeTable logic from XmlGridView.
     *
     * @param node the node to check
     * @return information about repeating elements, or empty if none found
     */
    private Optional<RepeatingElementInfo> detectRepeatingElements(XmlNode node) {
        // Use the static shouldBeTable method from XmlGridView
        if (!XmlGridView.shouldBeTable(node)) {
            return Optional.empty();
        }

        // Find the repeating element name and count
        List<XmlNode> children = getChildrenList(node);
        Map<String, Long> elementCounts = children.stream()
                .filter(child -> child instanceof XmlElement)
                .map(child -> ((XmlElement) child).getName())
                .collect(Collectors.groupingBy(name -> name, Collectors.counting()));

        // Find first repeating element (count >= 2)
        return elementCounts.entrySet().stream()
                .filter(entry -> entry.getValue() >= 2)
                .map(entry -> new RepeatingElementInfo(entry.getKey(), entry.getValue()))
                .findFirst();
    }

    /**
     * Shows the grid view for repeating elements.
     *
     * @param parent      the parent node
     * @param elementName the name of the repeating element
     * @param count       the number of repeating elements
     */
    private void showGridView(XmlNode parent, String elementName, long count) {
        if (!gridVisible) {
            // Add grid view to split pane
            splitPane.getItems().add(gridView);
            splitPane.setDividerPositions(DEFAULT_DIVIDER_POSITION);
            gridVisible = true;
        }

        // Load repeating elements into grid
        gridView.loadRepeatingElements(parent, elementName);
    }

    /**
     * Hides the grid view when no repeating elements are present.
     */
    private void hideGridView() {
        if (gridVisible) {
            splitPane.getItems().remove(gridView);
            gridVisible = false;
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Helper method to get children from XmlNode (handles both XmlElement and XmlDocument).
     */
    private List<XmlNode> getChildrenList(XmlNode node) {
        if (node instanceof XmlElement) {
            return ((XmlElement) node).getChildren();
        } else if (node instanceof XmlDocument) {
            return ((XmlDocument) node).getChildren();
        }
        return java.util.Collections.emptyList();
    }

    // ==================== Public API ====================

    /**
     * Refreshes both tree and grid views.
     */
    public void refresh() {
        treeView.refresh();
        if (gridVisible) {
            gridView.refresh();
        }
    }

    /**
     * Expands all tree nodes.
     */
    public void expandAll() {
        treeView.expandAll();
    }

    /**
     * Collapses all tree nodes.
     */
    public void collapseAll() {
        treeView.collapseAll();
    }

    /**
     * Gets the tree view component.
     *
     * @return the tree view
     */
    public XmlTreeView getTreeView() {
        return treeView;
    }

    /**
     * Gets the grid view component.
     *
     * @return the grid view
     */
    public XmlGridView getGridView() {
        return gridView;
    }

    /**
     * Checks if grid view is currently visible.
     *
     * @return true if grid is visible, false otherwise
     */
    public boolean isGridVisible() {
        return gridVisible;
    }

    /**
     * Manually shows the grid view (for testing/initialization).
     */
    public void showGridManually() {
        if (!gridVisible) {
            splitPane.getItems().add(gridView);
            splitPane.setDividerPositions(DEFAULT_DIVIDER_POSITION);
            gridVisible = true;
        }
    }

    /**
     * Sets the divider position between tree and grid.
     *
     * @param position position between 0.0 and 1.0
     */
    public void setDividerPosition(double position) {
        if (gridVisible) {
            splitPane.setDividerPositions(position);
        }
    }

    // ==================== Inner Class ====================

    /**
     * Information about detected repeating elements.
     */
    private static class RepeatingElementInfo {
        final String elementName;
        final long count;

        RepeatingElementInfo(String elementName, long count) {
            this.elementName = elementName;
            this.count = count;
        }
    }
}
