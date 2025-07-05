package org.fxt.freexmltoolkit.controls;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.fxt.freexmltoolkit.domain.XsdRootInfo;

/**
 * Erstellt die grafische Ansicht für das XSD-Wurzelelement und seine Kinder.
 * Diese Klasse entkoppelt die UI-Logik vom Controller.
 */
public class XsdDiagramView {

    private final XsdRootInfo rootInfo;

    // Stile als Konstanten für bessere Lesbarkeit und Wartbarkeit
    private static final String ROOT_LABEL_STYLE =
            "-fx-background-color: #eef4ff; " +
                    "-fx-border-color: #adc8ff; " +
                    "-fx-border-width: 1px; " +
                    "-fx-border-radius: 8px; " +
                    "-fx-background-radius: 8px; " +
                    "-fx-padding: 10px 15px; " +
                    "-fx-font-family: 'Segoe UI', sans-serif; " +
                    "-fx-font-size: 16px; " +
                    "-fx-font-weight: bold; " +
                    "-fx-text-fill: #0d47a1;";

    private static final String TOGGLE_BUTTON_STYLE =
            "-fx-font-size: 20px; " +
                    "-fx-font-weight: bold; " +
                    "-fx-text-fill: #0d47a1; " +
                    "-fx-padding: 0 10px; " +
                    "-fx-cursor: hand;";

    private static final String CHILD_LABEL_STYLE =
            "-fx-font-size: 14px; -fx-text-fill: #333;";

    private static final String CHILDREN_CONTAINER_STYLE =
            "-fx-background-color: #f8f9fa; " +
                    "-fx-border-color: #dee2e6; " +
                    "-fx-border-width: 1px; " +
                    "-fx-border-radius: 5px; " +
                    "-fx-background-radius: 5px;";

    // NEU: Stil für die Dokumentation
    private static final String DOC_LABEL_STYLE =
            "-fx-font-size: 12px; " +
                    "-fx-text-fill: #6c757d; " + // Ein dezentes Grau
                    "-fx-font-style: italic; " +
                    "-fx-padding: 5px 0 0 5px;"; // Etwas Abstand nach oben und links


    public XsdDiagramView(XsdRootInfo rootInfo) {
        this.rootInfo = rootInfo;
    }

    /**
     * Baut die vollständige UI-Komponente und gibt sie als Node zurück.
     *
     * @return Ein HBox-Node, der das Diagramm enthält.
     */
    public Node build() {
        // Hauptcontainer ist jetzt eine HBox für eine horizontale Anordnung
        HBox diagramContainer = new HBox(10);
        diagramContainer.setPadding(new Insets(10));
        diagramContainer.setAlignment(Pos.CENTER_LEFT); // Zentriert die Kinder vertikal

        // 1. Root-Element-Komponente (Name + Doku) erstellen und hinzufügen
        Node rootNodeComponent = createRootNodeComponent();
        diagramContainer.getChildren().add(rootNodeComponent);

        // 2. Button und Kind-Container nur erstellen, wenn Kinder vorhanden sind
        if (rootInfo != null && !rootInfo.childElementNames().isEmpty()) {
            VBox childrenContainer = createChildrenContainer();
            Label toggleButton = createToggleButton(childrenContainer);
            diagramContainer.getChildren().addAll(toggleButton, childrenContainer);
        }

        return diagramContainer;
    }

    /**
     * NEUE METHODE: Erstellt die Komponente für den Root-Knoten,
     * die den Namen und die Dokumentation enthält.
     */
    private Node createRootNodeComponent() {
        // Dieser VBox hält den Namen und die darunterliegende Dokumentation
        VBox rootDisplay = new VBox(2); // Kleiner Abstand zwischen Name und Doku

        // Das Label für den Namen des Wurzelelements
        Label rootElementLabel = new Label(rootInfo.name());
        rootElementLabel.setStyle(ROOT_LABEL_STYLE);
        rootDisplay.getChildren().add(rootElementLabel);

        // Prüfen, ob Dokumentation vorhanden ist und sie hinzufügen
        if (rootInfo.documentation() != null && !rootInfo.documentation().isBlank()) {
            Label docLabel = new Label(rootInfo.documentation());
            docLabel.setStyle(DOC_LABEL_STYLE);
            docLabel.setWrapText(true); // Wichtig für längere Texte
            docLabel.setMaxWidth(350);  // Verhindert, dass das Label zu breit wird
            rootDisplay.getChildren().add(docLabel);
        }

        return rootDisplay;
    }

    /**
     * Erstellt den Button zum Auf- und Zuklappen der Kind-Elemente.
     */
    private Label createToggleButton(VBox childrenContainer) {
        Label toggleButton = new Label("+");
        toggleButton.setStyle(TOGGLE_BUTTON_STYLE);

        // Klick-Logik zum Auf- und Zuklappen
        final boolean[] isExpanded = {false};
        toggleButton.setOnMouseClicked(event -> {
            isExpanded[0] = !isExpanded[0];
            childrenContainer.setVisible(isExpanded[0]);
            childrenContainer.setManaged(isExpanded[0]);
            toggleButton.setText(isExpanded[0] ? "−" : "+"); // U+2212 ist das echte Minus-Zeichen
        });

        return toggleButton;
    }

    /**
     * Erstellt den (anfangs unsichtbaren) Container für die Kind-Elemente.
     */
    private VBox createChildrenContainer() {
        VBox childrenContainer = new VBox(5);
        childrenContainer.setPadding(new Insets(8)); // Innenabstand
        childrenContainer.setVisible(false);
        childrenContainer.setManaged(false); // Nimmt keinen Platz ein, wenn unsichtbar
        childrenContainer.setStyle(CHILDREN_CONTAINER_STYLE);

        for (String childName : rootInfo.childElementNames()) {
            Label childLabel = new Label("• " + childName);
            childLabel.setStyle(CHILD_LABEL_STYLE);
            childrenContainer.getChildren().add(childLabel);
        }
        return childrenContainer;
    }
}