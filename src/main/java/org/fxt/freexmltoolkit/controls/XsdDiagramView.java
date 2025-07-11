package org.fxt.freexmltoolkit.controls;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;

public class XsdDiagramView {

    private final XsdNodeInfo rootNode;

    // Stile (unverändert)
    // Kopiere hier deine Stil-Konstanten hinein
    private static final String NODE_LABEL_STYLE =
            "-fx-background-color: #eef4ff; -fx-border-color: #adc8ff; -fx-border-width: 1px; " +
                    "-fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 5px 10px; " +
                    "-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #0d47a1;";

    private static final String TOGGLE_BUTTON_STYLE =
            "-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #0d47a1; -fx-padding: 0 5px; -fx-cursor: hand;";

    private static final String DOC_LABEL_STYLE =
            "-fx-font-size: 11px; -fx-text-fill: #6c757d; -fx-font-style: italic; -fx-padding: 2px 0 0 5px;";

    public XsdDiagramView(XsdNodeInfo rootNode) {
        this.rootNode = rootNode;
    }

    /**
     * Startpunkt: Baut die Ansicht für den Wurzelknoten.
     */
    public Node build() {
        if (rootNode == null) {
            return new Label("Keine Element-Informationen gefunden.");
        }
        // Der Hauptcontainer, der das gesamte Diagramm aufnimmt.
        VBox diagramContainer = new VBox();
        diagramContainer.setPadding(new Insets(10));

        // Starte den rekursiven Aufbau
        Node rootNodeView = createNodeView(rootNode, 0);
        diagramContainer.getChildren().add(rootNodeView);

        return diagramContainer;
    }

    /**
     * Rekursive Methode zum Erstellen der Ansicht für einen einzelnen Knoten.
     * @param node Der Datenknoten, der visualisiert werden soll.
     * @param depth Die aktuelle Tiefe im Baum (für die Einrückung).
     * @return Ein JavaFX-Node, der diesen Baum-Teil repräsentiert.
     */
    private Node createNodeView(XsdNodeInfo node, int depth) {
        // Container für diesen Knoten: Label-Zeile + Doku + Container für Kinder
        VBox nodeContainer = new VBox(5);
        nodeContainer.setPadding(new Insets(0, 0, 0, depth * 20)); // Einrückung pro Ebene

        // HBox für die Zeile mit dem Namen und dem Toggle-Button
        HBox nameAndToggleRow = new HBox(5);
        nameAndToggleRow.setAlignment(Pos.CENTER_LEFT);

        // Container für die Kinder dieses Knotens (anfangs unsichtbar)
        VBox childrenContainer = new VBox(5);
        childrenContainer.setVisible(false);
        childrenContainer.setManaged(false);

        // Zuerst den Namen hinzufügen
        Label nameLabel = new Label(node.name());
        nameLabel.setStyle(NODE_LABEL_STYLE);
        nameAndToggleRow.getChildren().add(nameLabel);

        // Den Toggle-Button (+/-) direkt danach hinzufügen, falls Kinder vorhanden sind
        if (!node.children().isEmpty()) {
            Label toggleButton = new Label("+");
            toggleButton.setStyle(TOGGLE_BUTTON_STYLE);

            final boolean[] isExpanded = {false};
            toggleButton.setOnMouseClicked(event -> {
                isExpanded[0] = !isExpanded[0];
                childrenContainer.setVisible(isExpanded[0]);
                childrenContainer.setManaged(isExpanded[0]);
                toggleButton.setText(isExpanded[0] ? "−" : "+");
            });
            nameAndToggleRow.getChildren().add(toggleButton);
        }

        // Die Zeile mit Name/Toggle zum Hauptcontainer hinzufügen
        nodeContainer.getChildren().add(nameAndToggleRow);

        // Die Dokumentation als separates Element darunter hinzufügen, falls vorhanden
        if (node.documentation() != null && !node.documentation().isBlank()) {
            Label docLabel = new Label(node.documentation());
            docLabel.setStyle(DOC_LABEL_STYLE);
            docLabel.setWrapText(true);
            docLabel.setMaxWidth(350);
            nodeContainer.getChildren().add(docLabel);
        }

        // Rekursiver Aufruf für alle Kinder
        for (XsdNodeInfo childNode : node.children()) {
            childrenContainer.getChildren().add(createNodeView(childNode, depth + 1));
        }

        // Den Kinder-Container zum Schluss hinzufügen
        nodeContainer.getChildren().add(childrenContainer);

        return nodeContainer;
    }
}