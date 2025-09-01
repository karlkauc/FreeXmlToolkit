package org.fxt.freexmltoolkit.controls;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.kordamp.ikonli.javafx.FontIcon;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

/**
 * UML-style diagram view of XSD content.
 * Displays schema elements as UML-like class diagrams with relationships.
 */
public class XsdUmlView extends BorderPane {
    private static final Logger logger = LogManager.getLogger(XsdUmlView.class);

    private final XsdDomManipulator domManipulator;
    private final Canvas diagramCanvas;
    private final ScrollPane canvasScrollPane;
    private final ObjectProperty<XsdNodeInfo> selectedNodeProperty = new SimpleObjectProperty<>();

    private final List<UmlElement> umlElements = new ArrayList<>();

    // Zoom and scaling variables
    private double currentScale = 1.0;
    private static final double ZOOM_FACTOR = 1.2;
    private static final double MIN_SCALE = 0.1;
    private static final double MAX_SCALE = 5.0;

    public XsdUmlView(XsdDomManipulator domManipulator) {
        this.domManipulator = domManipulator;
        this.diagramCanvas = new Canvas(800, 600);
        this.canvasScrollPane = new ScrollPane(diagramCanvas);

        initializeComponents();
        refreshView();
    }

    private void initializeComponents() {
        // Create toolbar
        ToolBar toolbar = new ToolBar();

        Button refreshButton = new Button("Refresh");
        refreshButton.setGraphic(new FontIcon("bi-arrow-clockwise"));
        refreshButton.setOnAction(e -> refreshView());

        Button zoomInButton = new Button("Zoom In");
        zoomInButton.setGraphic(new FontIcon("bi-zoom-in"));
        zoomInButton.setOnAction(e -> zoomIn());

        Button zoomOutButton = new Button("Zoom Out");
        zoomOutButton.setGraphic(new FontIcon("bi-zoom-out"));
        zoomOutButton.setOnAction(e -> zoomOut());

        Button fitToWindowButton = new Button("Fit to Window");
        fitToWindowButton.setGraphic(new FontIcon("bi-arrows-fullscreen"));
        fitToWindowButton.setOnAction(e -> fitToWindow());

        toolbar.getItems().addAll(refreshButton, new Separator(),
                zoomInButton, zoomOutButton, fitToWindowButton);

        // Configure canvas
        canvasScrollPane.setFitToWidth(false);
        canvasScrollPane.setFitToHeight(false);
        canvasScrollPane.getStyleClass().add("uml-canvas-scroll");

        // Add mouse zoom support
        setupMouseZoom();

        // Layout
        setTop(toolbar);
        setCenter(canvasScrollPane);
    }

    /**
     * Refresh the UML diagram with current XSD content
     */
    public void refreshView() {
        logger.info("Refreshing XSD UML view");

        umlElements.clear();

        if (domManipulator != null && domManipulator.getDocument() != null) {
            analyzeSchema();
            drawDiagram();
        } else {
            clearDiagram();
        }

        logger.info("UML view refreshed with {} elements", umlElements.size());
    }

    private void analyzeSchema() {
        Element root = domManipulator.getDocument().getDocumentElement();
        if (root != null) {
            // Extract global complex types
            NodeList complexTypes = root.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType");
            for (int i = 0; i < complexTypes.getLength(); i++) {
                Element complexType = (Element) complexTypes.item(i);
                if (complexType.getParentNode() == root) { // Only global types
                    analyzeComplexType(complexType);
                }
            }

            // Extract global elements
            NodeList elements = root.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element");
            for (int i = 0; i < elements.getLength(); i++) {
                Element element = (Element) elements.item(i);
                if (element.getParentNode() == root) { // Only global elements
                    analyzeElement(element);
                }
            }
        }
    }

    private void analyzeComplexType(Element complexType) {
        String name = complexType.getAttribute("name");
        if (!name.isEmpty()) {
            UmlElement umlElement = new UmlElement(name, UmlElement.Type.COMPLEX_TYPE);

            // Extract attributes
            NodeList attributes = complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "attribute");
            for (int i = 0; i < attributes.getLength(); i++) {
                Element attr = (Element) attributes.item(i);
                String attrName = attr.getAttribute("name");
                String attrType = attr.getAttribute("type");
                if (!attrName.isEmpty()) {
                    umlElement.addAttribute(attrName, attrType);
                }
            }

            // Extract child elements
            NodeList childElements = complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element");
            for (int i = 0; i < childElements.getLength(); i++) {
                Element elem = (Element) childElements.item(i);
                String elemName = elem.getAttribute("name");
                String elemType = elem.getAttribute("type");
                if (!elemName.isEmpty()) {
                    umlElement.addElement(elemName, elemType);
                }
            }

            umlElements.add(umlElement);
        }
    }

    private void analyzeElement(Element element) {
        String name = element.getAttribute("name");
        String type = element.getAttribute("type");
        if (!name.isEmpty()) {
            UmlElement umlElement = new UmlElement(name, UmlElement.Type.ELEMENT);
            if (!type.isEmpty()) {
                umlElement.setBaseType(type);
            }
            umlElements.add(umlElement);
        }
    }

    private void drawDiagram() {
        GraphicsContext gc = diagramCanvas.getGraphicsContext2D();
        clearCanvas(gc);

        if (umlElements.isEmpty()) {
            drawEmptyDiagramMessage(gc);
            return;
        }

        // Simple layout - arrange elements in a grid
        int cols = (int) Math.ceil(Math.sqrt(umlElements.size()));
        int elementWidth = 200;
        int elementHeight = 150;
        int margin = 50;

        // Resize canvas if needed
        int canvasWidth = cols * (elementWidth + margin) + margin;
        int canvasHeight = ((umlElements.size() + cols - 1) / cols) * (elementHeight + margin) + margin;

        if (diagramCanvas.getWidth() < canvasWidth) {
            diagramCanvas.setWidth(canvasWidth);
        }
        if (diagramCanvas.getHeight() < canvasHeight) {
            diagramCanvas.setHeight(canvasHeight);
        }

        // Draw elements
        for (int i = 0; i < umlElements.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int x = margin + col * (elementWidth + margin);
            int y = margin + row * (elementHeight + margin);

            drawUmlElement(gc, umlElements.get(i), x, y, elementWidth, elementHeight);
        }
    }

    private void clearCanvas(GraphicsContext gc) {
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, diagramCanvas.getWidth(), diagramCanvas.getHeight());
    }

    private void drawEmptyDiagramMessage(GraphicsContext gc) {
        gc.setFill(Color.GRAY);
        gc.fillText("No XSD elements to display", 50, 50);
    }

    private void drawUmlElement(GraphicsContext gc, UmlElement element, int x, int y, int width, int height) {
        // Draw element box
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(1);
        gc.strokeRect(x, y, width, height);

        // Fill background based on type
        if (element.getType() == UmlElement.Type.COMPLEX_TYPE) {
            gc.setFill(Color.LIGHTBLUE);
        } else {
            gc.setFill(Color.LIGHTGREEN);
        }
        gc.fillRect(x + 1, y + 1, width - 2, height - 2);

        // Draw title
        gc.setFill(Color.BLACK);
        gc.fillText(element.getName(), x + 10, y + 20);
        gc.fillText("(" + element.getType().getDisplayName() + ")", x + 10, y + 35);

        // Draw attributes
        int yOffset = 50;
        for (UmlElement.Attribute attr : element.getAttributes()) {
            if (yOffset < height - 20) {
                gc.fillText("+ " + attr.name() + ": " + attr.type(), x + 10, y + yOffset);
                yOffset += 15;
            }
        }

        // Draw elements
        if (yOffset < height - 20) {
            gc.fillText("--- Elements ---", x + 10, y + yOffset);
            yOffset += 15;
        }

        for (UmlElement.ElementInfo elem : element.getElements()) {
            if (yOffset < height - 20) {
                gc.fillText("• " + elem.name() + ": " + elem.type(), x + 10, y + yOffset);
                yOffset += 15;
            }
        }
    }

    private void clearDiagram() {
        GraphicsContext gc = diagramCanvas.getGraphicsContext2D();
        clearCanvas(gc);
        drawEmptyDiagramMessage(gc);
    }

    private void zoomIn() {
        logger.info("Zooming in UML diagram");
        if (currentScale < MAX_SCALE) {
            currentScale *= ZOOM_FACTOR;
            applyScale();
        }
    }

    private void zoomOut() {
        logger.info("Zooming out UML diagram");
        if (currentScale > MIN_SCALE) {
            currentScale /= ZOOM_FACTOR;
            applyScale();
        }
    }

    private void fitToWindow() {
        logger.info("Fitting UML diagram to window");

        // Calculate scale to fit canvas in viewport
        double viewportWidth = canvasScrollPane.getViewportBounds().getWidth();
        double viewportHeight = canvasScrollPane.getViewportBounds().getHeight();

        if (viewportWidth > 0 && viewportHeight > 0) {
            double canvasWidth = diagramCanvas.getWidth();
            double canvasHeight = diagramCanvas.getHeight();

            // Calculate scale factors for width and height
            double scaleX = viewportWidth / canvasWidth;
            double scaleY = viewportHeight / canvasHeight;

            // Use the smaller scale to ensure everything fits
            currentScale = Math.min(scaleX, scaleY) * 0.95; // 95% to leave some margin
            currentScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, currentScale));

            applyScale();

            // Center the canvas
            centerCanvas();
        }
    }

    /**
     * Apply the current scale to the canvas
     */
    private void applyScale() {
        diagramCanvas.getTransforms().clear();
        if (currentScale != 1.0) {
            javafx.scene.transform.Scale scale = new javafx.scene.transform.Scale(currentScale, currentScale);
            diagramCanvas.getTransforms().add(scale);
        }
        logger.debug("Applied scale: {}", currentScale);
    }

    /**
     * Center the canvas in the scroll pane
     */
    private void centerCanvas() {
        canvasScrollPane.setHvalue(0.5);
        canvasScrollPane.setVvalue(0.5);
    }

    /**
     * Setup mouse zoom support for the canvas
     */
    private void setupMouseZoom() {
        diagramCanvas.setOnScroll(event -> {
            if (event.isControlDown()) {
                event.consume();

                double deltaY = event.getDeltaY();
                if (deltaY > 0) {
                    // Zoom in
                    if (currentScale < MAX_SCALE) {
                        currentScale *= ZOOM_FACTOR;
                        applyScale();
                    }
                } else if (deltaY < 0) {
                    // Zoom out
                    if (currentScale > MIN_SCALE) {
                        currentScale /= ZOOM_FACTOR;
                        applyScale();
                    }
                }
            }
        });
    }

    // Property accessor
    public ObjectProperty<XsdNodeInfo> selectedNodeProperty() {
        return selectedNodeProperty;
    }

    /**
     * UML Element representation
     */
    private static class UmlElement {
        public enum Type {
            COMPLEX_TYPE("ComplexType"),
            SIMPLE_TYPE("SimpleType"),
            ELEMENT("Element");

            private final String displayName;

            Type(String displayName) {
                this.displayName = displayName;
            }

            public String getDisplayName() {
                return displayName;
            }
        }

        public record Attribute(String name, String type) {
        }

        public record ElementInfo(String name, String type) {
        }

        private final String name;
        private final Type type;
        private String baseType;
        private final List<Attribute> attributes = new ArrayList<>();
        private final List<ElementInfo> elements = new ArrayList<>();

        public UmlElement(String name, Type type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public Type getType() {
            return type;
        }

        public String getBaseType() {
            return baseType;
        }

        public void setBaseType(String baseType) {
            this.baseType = baseType;
        }

        public void addAttribute(String name, String type) {
            attributes.add(new Attribute(name, type != null ? type : "xs:string"));
        }

        public void addElement(String name, String type) {
            elements.add(new ElementInfo(name, type != null ? type : "xs:string"));
        }

        public List<Attribute> getAttributes() {
            return attributes;
        }

        public List<ElementInfo> getElements() {
            return elements;
        }
    }
}