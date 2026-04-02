package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.ui;

import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItemType;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Reusable cell renderer for {@link CompletionItem} in a ListView.
 * Used by both the IntelliSense popup and the properties sidebar "Possible Child Elements" section.
 *
 * <p>Layout:</p>
 * <pre>
 * +-------------------------------------------------------------+
 * | [icon] elementName    : xs:string   (0..1)  *  = "default"  |
 * |        list-check pattern | e.g.: "DE", "AT"                 |
 * +-------------------------------------------------------------+
 * </pre>
 */
public class CompletionItemCellRenderer extends ListCell<CompletionItem> {

    private static final String ICON_ELEMENT = "bi-code-slash";
    private static final String ICON_ATTRIBUTE = "bi-at";
    private static final String ICON_VALUE = "bi-chat-quote";
    private static final String ICON_SNIPPET = "bi-lightning";
    private static final String ICON_FUNCTION = "bi-gear";
    private static final String ICON_AXIS = "bi-arrows-angle-expand";
    private static final String ICON_OPERATOR = "bi-calculator";
    private static final String ICON_KEYWORD = "bi-key";
    private static final String ICON_NODE_TEST = "bi-bullseye";
    private static final String ICON_VARIABLE = "bi-braces";
    private static final String ICON_TYPE = "bi-diagram-3";
    private static final String ICON_DEFAULT = "bi-circle";

    @Override
    protected void updateItem(CompletionItem item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            setStyle("");
            setTooltip(null);
            getStyleClass().removeIf(s -> s.startsWith("completion-"));
        } else {
            setText(null);
            setGraphic(createCellContent(item));

            getStyleClass().removeIf(s -> s.startsWith("completion-"));
            getStyleClass().add("completion-item");
            if (item.getType() != null) {
                getStyleClass().add("completion-" + item.getType().name().toLowerCase().replace("_", "-"));
            }
            if (item.isRequired()) {
                getStyleClass().add("completion-required");
            }

            if (item.getDescription() != null && !item.getDescription().isEmpty()) {
                Tooltip tooltip = new Tooltip(item.getDescription());
                tooltip.setWrapText(true);
                tooltip.setMaxWidth(400);
                setTooltip(tooltip);
            }
        }
    }

    private VBox createCellContent(CompletionItem item) {
        VBox container = new VBox(2);
        container.setPadding(new Insets(4, 8, 4, 4));

        HBox line1 = createFirstLine(item);
        container.getChildren().add(line1);

        if (item.hasExtendedInfo()) {
            HBox line2 = createSecondLine(item);
            if (line2.getChildren().size() > 1) {
                container.getChildren().add(line2);
            }
        }

        return container;
    }

    private HBox createFirstLine(CompletionItem item) {
        HBox line = new HBox(6);
        line.setAlignment(Pos.CENTER_LEFT);

        FontIcon icon = createIcon(item.getType());
        icon.setIconSize(16);
        line.getChildren().add(icon);

        Label nameLabel = new Label(item.getLabel());
        nameLabel.getStyleClass().add("completion-name");
        line.getChildren().add(nameLabel);

        if (item.getDataType() != null && !item.getDataType().isEmpty()) {
            Label typeLabel = new Label(": " + item.getDataType());
            typeLabel.getStyleClass().add("completion-datatype");
            line.getChildren().add(typeLabel);
        }

        if (item.getCardinality() != null && !item.getCardinality().isEmpty()) {
            Label cardLabel = new Label("(" + item.getCardinality() + ")");
            cardLabel.getStyleClass().add("completion-cardinality");
            line.getChildren().add(cardLabel);
        }

        if (item.isRequired()) {
            Label reqLabel = new Label("*");
            reqLabel.getStyleClass().add("completion-required-marker");
            line.getChildren().add(reqLabel);
        }

        if (item.getDefaultValue() != null && !item.getDefaultValue().isEmpty()) {
            Label defaultLabel = new Label("= \"" + truncate(item.getDefaultValue(), 20) + "\"");
            defaultLabel.getStyleClass().add("completion-default");
            line.getChildren().add(defaultLabel);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        line.getChildren().add(spacer);

        if (item.getPrefix() != null && !item.getPrefix().isEmpty()) {
            Label nsLabel = new Label(item.getPrefix());
            nsLabel.getStyleClass().add("completion-namespace");
            line.getChildren().add(nsLabel);
        }

        return line;
    }

    private HBox createSecondLine(CompletionItem item) {
        HBox line = new HBox(8);
        line.setAlignment(Pos.CENTER_LEFT);
        line.setPadding(new Insets(0, 0, 0, 22));
        line.getStyleClass().add("completion-second-line");

        List<String> facets = item.getFacetHints();
        if (facets != null && !facets.isEmpty()) {
            FontIcon facetIcon = new FontIcon("bi-list-check");
            facetIcon.setIconSize(12);
            facetIcon.getStyleClass().add("completion-facet-icon");
            line.getChildren().add(facetIcon);

            String facetText = String.join(", ", facets);
            Label facetLabel = new Label(truncate(facetText, 30));
            facetLabel.getStyleClass().add("completion-facets");
            line.getChildren().add(facetLabel);
        }

        if (facets != null && !facets.isEmpty() && !item.getExamples().isEmpty()) {
            Label sep = new Label("|");
            sep.getStyleClass().add("completion-separator");
            line.getChildren().add(sep);
        }

        List<String> examples = item.getExamples();
        if (examples != null && !examples.isEmpty()) {
            Label exLabel = new Label("e.g.: ");
            exLabel.getStyleClass().add("completion-example-label");
            line.getChildren().add(exLabel);

            String exampleText = String.join(", ", examples);
            Label exValues = new Label(truncate(exampleText, 35));
            exValues.getStyleClass().add("completion-examples");
            line.getChildren().add(exValues);
        }

        List<String> reqAttrs = item.getRequiredAttributes();
        if (reqAttrs != null && !reqAttrs.isEmpty()) {
            if (!line.getChildren().isEmpty()) {
                Label sep = new Label("|");
                sep.getStyleClass().add("completion-separator");
                line.getChildren().add(sep);
            }
            FontIcon attrIcon = new FontIcon("bi-exclamation-triangle");
            attrIcon.setIconSize(12);
            attrIcon.getStyleClass().add("completion-attr-icon");
            line.getChildren().add(attrIcon);

            Label attrLabel = new Label("needs: " + truncate(String.join(", ", reqAttrs), 25));
            attrLabel.getStyleClass().add("completion-required-attrs");
            line.getChildren().add(attrLabel);
        }

        return line;
    }

    private FontIcon createIcon(CompletionItemType type) {
        String iconLiteral;
        String styleClass;

        if (type == null) {
            FontIcon icon = new FontIcon(ICON_DEFAULT);
            icon.getStyleClass().add("completion-icon-default");
            return icon;
        }

        switch (type) {
            case ELEMENT:
                iconLiteral = ICON_ELEMENT;
                styleClass = "completion-icon-element";
                break;
            case ATTRIBUTE:
                iconLiteral = ICON_ATTRIBUTE;
                styleClass = "completion-icon-attribute";
                break;
            case VALUE:
                iconLiteral = ICON_VALUE;
                styleClass = "completion-icon-value";
                break;
            case SNIPPET:
                iconLiteral = ICON_SNIPPET;
                styleClass = "completion-icon-snippet";
                break;
            case XPATH_FUNCTION:
                iconLiteral = ICON_FUNCTION;
                styleClass = "completion-icon-function";
                break;
            case XPATH_AXIS:
                iconLiteral = ICON_AXIS;
                styleClass = "completion-icon-axis";
                break;
            case XPATH_OPERATOR:
                iconLiteral = ICON_OPERATOR;
                styleClass = "completion-icon-operator";
                break;
            case XQUERY_KEYWORD:
                iconLiteral = ICON_KEYWORD;
                styleClass = "completion-icon-keyword";
                break;
            case XPATH_NODE_TEST:
                iconLiteral = ICON_NODE_TEST;
                styleClass = "completion-icon-nodetest";
                break;
            case XPATH_VARIABLE:
                iconLiteral = ICON_VARIABLE;
                styleClass = "completion-icon-variable";
                break;
            case TYPE:
                iconLiteral = ICON_TYPE;
                styleClass = "completion-icon-type";
                break;
            default:
                iconLiteral = ICON_DEFAULT;
                styleClass = "completion-icon-default";
                break;
        }

        FontIcon icon = new FontIcon(iconLiteral);
        icon.getStyleClass().add(styleClass);
        return icon;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}
