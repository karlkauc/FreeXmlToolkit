package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import org.fxt.freexmltoolkit.domain.TemplateParameter;
import org.fxt.freexmltoolkit.domain.XmlTemplate;

/**
 * Prompts for an {@link XmlTemplate}'s parameter values before insertion: one row per parameter
 * (a checkbox for booleans, a dropdown for enums, a text field otherwise), pre-filled with the
 * parameter's default. Returns the entered values on OK, or empty on Cancel.
 */
public class TemplateParameterDialog extends Dialog<Map<String, String>> {

    /** Per-parameter value readers, in declaration order. */
    private final Map<String, Supplier<String>> readers = new LinkedHashMap<>();

    public TemplateParameterDialog(XmlTemplate template) {
        setTitle("Template Parameters");
        setHeaderText("Enter values for \"" + template.getName() + "\"");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));

        int row = 0;
        for (TemplateParameter param : template.getParameters()) {
            String name = param.getName();
            String labelText = (param.getDisplayName() != null && !param.getDisplayName().isBlank()
                    ? param.getDisplayName() : name) + (param.isRequired() ? " *" : "");
            String defaultValue = param.getDefaultValue() == null ? "" : param.getDefaultValue();

            Node control;
            switch (param.getType()) {
                case BOOLEAN -> {
                    CheckBox checkBox = new CheckBox();
                    checkBox.setSelected("true".equalsIgnoreCase(defaultValue));
                    readers.put(name, () -> Boolean.toString(checkBox.isSelected()));
                    control = checkBox;
                }
                case ENUM -> {
                    ComboBox<String> combo = new ComboBox<>();
                    if (param.getAllowedValues() != null) {
                        combo.getItems().setAll(param.getAllowedValues());
                    }
                    if (!defaultValue.isBlank()) {
                        combo.setValue(defaultValue);
                    } else if (!combo.getItems().isEmpty()) {
                        combo.setValue(combo.getItems().get(0));
                    }
                    readers.put(name, () -> combo.getValue() == null ? "" : combo.getValue());
                    control = combo;
                }
                default -> {
                    TextField field = new TextField(defaultValue);
                    if (param.getPlaceholder() != null) {
                        field.setPromptText(param.getPlaceholder());
                    }
                    readers.put(name, () -> field.getText() == null ? "" : field.getText());
                    control = field;
                }
            }
            grid.add(new Label(labelText), 0, row);
            grid.add(control, 1, row);
            row++;
        }

        getDialogPane().setContent(grid);
        setResultConverter(button -> button == ButtonType.OK ? readValues() : null);
    }

    /** @return the currently entered parameter values (parameter name → string value). */
    Map<String, String> readValues() {
        Map<String, String> values = new LinkedHashMap<>();
        readers.forEach((name, reader) -> values.put(name, reader.get()));
        return values;
    }
}
