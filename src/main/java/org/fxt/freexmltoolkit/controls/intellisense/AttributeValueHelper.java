package org.fxt.freexmltoolkit.controls.intellisense;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Type-aware attribute value helpers that provide intelligent input assistance
 * based on XSD data types. Includes widgets for:
 * - Boolean values (toggle buttons)
 * - Date/Time values (date picker)
 * - Numeric values (spinner with constraints)
 * - Enumeration values (dropdown)
 * - Pattern-constrained strings (validation)
 * - URIs and references
 */
public class AttributeValueHelper {

    private static final Logger logger = LogManager.getLogger(AttributeValueHelper.class);

    private Popup popup;
    private VBox container;
    private Consumer<String> onValueSelected;

    // Common XSD type patterns
    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?\\d+");
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern DATETIME_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
    private static final Pattern TIME_PATTERN = Pattern.compile("\\d{2}:\\d{2}:\\d{2}");
    private static final Pattern URI_PATTERN = Pattern.compile("https?://[^\\s]+");

    public AttributeValueHelper() {
        initializePopup();
    }

    private void initializePopup() {
        popup = new Popup();
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);

        container = new VBox(5);
        container.setPadding(new Insets(10));
        container.setStyle(
                "-fx-background-color: white; " +
                        "-fx-border-color: #4a90e2; " +
                        "-fx-border-width: 1; " +
                        "-fx-border-radius: 6; " +
                        "-fx-background-radius: 6; " +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 8, 0, 2, 2);"
        );

        popup.getContent().add(container);
    }

    /**
     * Shows appropriate helper widget based on attribute info
     */
    public void showHelper(Point2D position, AttributeInfo attributeInfo, String currentValue) {
        container.getChildren().clear();

        // Title
        Label titleLabel = new Label("Value Helper: " + attributeInfo.name);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #2c5aa0;");
        container.getChildren().add(titleLabel);

        if (attributeInfo.documentation != null) {
            Label docLabel = new Label(attributeInfo.documentation);
            docLabel.setWrapText(true);
            docLabel.setMaxWidth(300);
            docLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #6c757d;");
            container.getChildren().add(docLabel);
        }

        container.getChildren().add(new Separator());

        // Create appropriate widget based on type
        createValueWidget(attributeInfo, currentValue);

        popup.show((javafx.scene.Node) null, position.getX(), position.getY());
    }

    private void createValueWidget(AttributeInfo attributeInfo, String currentValue) {
        String dataType = normalizeXsdType(attributeInfo.type);

        switch (dataType) {
            case "boolean":
                createBooleanWidget(currentValue);
                break;
            case "date":
                createDateWidget(currentValue);
                break;
            case "dateTime":
                createDateTimeWidget(currentValue);
                break;
            case "time":
                createTimeWidget(currentValue);
                break;
            case "int":
            case "integer":
            case "long":
            case "short":
                createIntegerWidget(attributeInfo, currentValue);
                break;
            case "decimal":
            case "double":
            case "float":
                createDecimalWidget(attributeInfo, currentValue);
                break;
            case "anyURI":
                createUriWidget(currentValue);
                break;
            default:
                if (attributeInfo.enumerationValues != null && !attributeInfo.enumerationValues.isEmpty()) {
                    createEnumerationWidget(attributeInfo.enumerationValues, currentValue);
                } else if (attributeInfo.pattern != null) {
                    createPatternWidget(attributeInfo, currentValue);
                } else {
                    createStringWidget(attributeInfo, currentValue);
                }
                break;
        }
    }

    private void createBooleanWidget(String currentValue) {
        HBox buttonContainer = new HBox(10);
        buttonContainer.setStyle("-fx-alignment: center;");

        ToggleGroup group = new ToggleGroup();

        ToggleButton trueButton = new ToggleButton("true");
        trueButton.setToggleGroup(group);
        trueButton.setPrefWidth(80);
        trueButton.setStyle("-fx-background-color: #28a745; -fx-text-fill: white;");

        ToggleButton falseButton = new ToggleButton("false");
        falseButton.setToggleGroup(group);
        falseButton.setPrefWidth(80);
        falseButton.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white;");

        // Set current value
        if ("true".equals(currentValue)) {
            trueButton.setSelected(true);
        } else if ("false".equals(currentValue)) {
            falseButton.setSelected(true);
        }

        trueButton.setOnAction(e -> acceptValue("true"));
        falseButton.setOnAction(e -> acceptValue("false"));

        buttonContainer.getChildren().addAll(trueButton, falseButton);
        container.getChildren().add(buttonContainer);
    }

    private void createDateWidget(String currentValue) {
        DatePicker datePicker = new DatePicker();
        datePicker.setPromptText("Select date...");

        if (currentValue != null && !currentValue.isEmpty() && DATE_PATTERN.matcher(currentValue).matches()) {
            try {
                datePicker.setValue(LocalDate.parse(currentValue));
            } catch (Exception e) {
                logger.debug("Could not parse date: {}", currentValue);
            }
        }

        datePicker.setOnAction(e -> {
            LocalDate date = datePicker.getValue();
            if (date != null) {
                acceptValue(date.toString());
            }
        });

        Button todayButton = new Button("Today");
        todayButton.setOnAction(e -> {
            datePicker.setValue(LocalDate.now());
            acceptValue(LocalDate.now().toString());
        });

        HBox dateContainer = new HBox(5);
        dateContainer.getChildren().addAll(datePicker, todayButton);
        container.getChildren().add(dateContainer);
    }

    private void createDateTimeWidget(String currentValue) {
        VBox dateTimeContainer = new VBox(5);

        DatePicker datePicker = new DatePicker();
        Spinner<Integer> hourSpinner = new Spinner<>(0, 23, 12);
        Spinner<Integer> minuteSpinner = new Spinner<>(0, 59, 0);
        Spinner<Integer> secondSpinner = new Spinner<>(0, 59, 0);

        // Parse current value if provided
        if (currentValue != null && !currentValue.isEmpty()) {
            try {
                LocalDateTime dateTime = LocalDateTime.parse(currentValue);
                datePicker.setValue(dateTime.toLocalDate());
                hourSpinner.getValueFactory().setValue(dateTime.getHour());
                minuteSpinner.getValueFactory().setValue(dateTime.getMinute());
                secondSpinner.getValueFactory().setValue(dateTime.getSecond());
            } catch (Exception e) {
                logger.debug("Could not parse datetime: {}", currentValue);
            }
        }

        HBox timeContainer = new HBox(5);
        timeContainer.getChildren().addAll(
                new Label("Time:"),
                hourSpinner,
                new Label(":"),
                minuteSpinner,
                new Label(":"),
                secondSpinner
        );

        Button acceptButton = new Button("Accept");
        acceptButton.setOnAction(e -> {
            try {
                LocalDate date = datePicker.getValue();
                if (date != null) {
                    LocalDateTime dateTime = LocalDateTime.of(
                            date,
                            java.time.LocalTime.of(
                                    hourSpinner.getValue(),
                                    minuteSpinner.getValue(),
                                    secondSpinner.getValue()
                            )
                    );
                    acceptValue(dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                }
            } catch (Exception ex) {
                logger.error("Error creating datetime", ex);
            }
        });

        Button nowButton = new Button("Now");
        nowButton.setOnAction(e -> {
            LocalDateTime now = LocalDateTime.now();
            acceptValue(now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        });

        HBox buttonContainer = new HBox(5);
        buttonContainer.getChildren().addAll(acceptButton, nowButton);

        dateTimeContainer.getChildren().addAll(datePicker, timeContainer, buttonContainer);
        container.getChildren().add(dateTimeContainer);
    }

    private void createTimeWidget(String currentValue) {
        HBox timeContainer = new HBox(5);

        Spinner<Integer> hourSpinner = new Spinner<>(0, 23, 12);
        Spinner<Integer> minuteSpinner = new Spinner<>(0, 59, 0);
        Spinner<Integer> secondSpinner = new Spinner<>(0, 59, 0);

        if (currentValue != null && TIME_PATTERN.matcher(currentValue).matches()) {
            try {
                String[] parts = currentValue.split(":");
                hourSpinner.getValueFactory().setValue(Integer.parseInt(parts[0]));
                minuteSpinner.getValueFactory().setValue(Integer.parseInt(parts[1]));
                if (parts.length > 2) {
                    secondSpinner.getValueFactory().setValue(Integer.parseInt(parts[2]));
                }
            } catch (Exception e) {
                logger.debug("Could not parse time: {}", currentValue);
            }
        }

        Button acceptButton = new Button("Accept");
        acceptButton.setOnAction(e -> {
            String time = String.format("%02d:%02d:%02d",
                    hourSpinner.getValue(),
                    minuteSpinner.getValue(),
                    secondSpinner.getValue());
            acceptValue(time);
        });

        timeContainer.getChildren().addAll(
                hourSpinner, new Label(":"),
                minuteSpinner, new Label(":"),
                secondSpinner,
                acceptButton
        );

        container.getChildren().add(timeContainer);
    }

    private void createIntegerWidget(AttributeInfo attributeInfo, String currentValue) {
        int min = attributeInfo.minInclusive != null ?
                Integer.parseInt(attributeInfo.minInclusive) : Integer.MIN_VALUE;
        int max = attributeInfo.maxInclusive != null ?
                Integer.parseInt(attributeInfo.maxInclusive) : Integer.MAX_VALUE;

        int initial = 0;
        if (currentValue != null && INTEGER_PATTERN.matcher(currentValue).matches()) {
            try {
                initial = Integer.parseInt(currentValue);
                initial = Math.max(min, Math.min(max, initial));
            } catch (NumberFormatException e) {
                initial = 0;
            }
        }

        Spinner<Integer> spinner = new Spinner<>(min, max, initial);
        spinner.setEditable(true);

        Button acceptButton = new Button("Accept");
        acceptButton.setOnAction(e -> acceptValue(spinner.getValue().toString()));

        HBox intContainer = new HBox(5);
        intContainer.getChildren().addAll(spinner, acceptButton);

        if (attributeInfo.minInclusive != null || attributeInfo.maxInclusive != null) {
            Label rangeLabel = new Label("Range: " + min + " to " + max);
            rangeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #6c757d;");
            container.getChildren().add(rangeLabel);
        }

        container.getChildren().add(intContainer);
    }

    private void createDecimalWidget(AttributeInfo attributeInfo, String currentValue) {
        TextField textField = new TextField();
        textField.setPromptText("Enter decimal value...");
        textField.setPrefWidth(200);

        if (currentValue != null) {
            textField.setText(currentValue);
        }

        // Real-time validation
        textField.textProperty().addListener((obs, oldText, newText) -> {
            if (!newText.isEmpty() && !DECIMAL_PATTERN.matcher(newText).matches()) {
                textField.setStyle("-fx-border-color: #dc3545;");
            } else {
                textField.setStyle("-fx-border-color: #28a745;");
            }
        });

        Button acceptButton = new Button("Accept");
        acceptButton.setOnAction(e -> {
            String text = textField.getText();
            if (DECIMAL_PATTERN.matcher(text).matches()) {
                acceptValue(text);
            }
        });

        HBox decimalContainer = new HBox(5);
        decimalContainer.getChildren().addAll(textField, acceptButton);
        container.getChildren().add(decimalContainer);
    }

    private void createEnumerationWidget(List<String> enumerationValues, String currentValue) {
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.getItems().addAll(enumerationValues);
        comboBox.setPrefWidth(250);

        if (currentValue != null) {
            comboBox.setValue(currentValue);
        }

        comboBox.setOnAction(e -> {
            String selected = comboBox.getValue();
            if (selected != null) {
                acceptValue(selected);
            }
        });

        container.getChildren().add(comboBox);
    }

    private void createPatternWidget(AttributeInfo attributeInfo, String currentValue) {
        VBox patternContainer = new VBox(5);

        TextField textField = new TextField();
        textField.setPromptText("Enter value...");
        textField.setPrefWidth(250);

        if (currentValue != null) {
            textField.setText(currentValue);
        }

        Label patternLabel = new Label("Pattern: " + attributeInfo.pattern);
        patternLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #6c757d; -fx-font-family: monospace;");

        Pattern pattern = Pattern.compile(attributeInfo.pattern);

        textField.textProperty().addListener((obs, oldText, newText) -> {
            if (!newText.isEmpty() && !pattern.matcher(newText).matches()) {
                textField.setStyle("-fx-border-color: #dc3545;");
            } else {
                textField.setStyle("-fx-border-color: #28a745;");
            }
        });

        Button acceptButton = new Button("Accept");
        acceptButton.setOnAction(e -> {
            String text = textField.getText();
            if (pattern.matcher(text).matches()) {
                acceptValue(text);
            } else {
                // Show validation error
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Invalid Value");
                alert.setContentText("Value does not match required pattern: " + attributeInfo.pattern);
                alert.showAndWait();
            }
        });

        HBox inputContainer = new HBox(5);
        inputContainer.getChildren().addAll(textField, acceptButton);

        patternContainer.getChildren().addAll(patternLabel, inputContainer);
        container.getChildren().add(patternContainer);
    }

    private void createUriWidget(String currentValue) {
        VBox uriContainer = new VBox(5);

        TextField textField = new TextField();
        textField.setPromptText("Enter URI...");
        textField.setPrefWidth(300);

        if (currentValue != null) {
            textField.setText(currentValue);
        }

        // Common URI prefixes
        FlowPane prefixPane = new FlowPane(5, 5);
        String[] commonPrefixes = {"http://", "https://", "ftp://", "file://", "mailto:"};

        for (String prefix : commonPrefixes) {
            Button prefixButton = new Button(prefix);
            prefixButton.setStyle("-fx-font-size: 9px; -fx-padding: 2 6 2 6;");
            prefixButton.setOnAction(e -> {
                if (textField.getText().isEmpty()) {
                    textField.setText(prefix);
                    textField.positionCaret(prefix.length());
                }
            });
            prefixPane.getChildren().add(prefixButton);
        }

        Button acceptButton = new Button("Accept");
        acceptButton.setOnAction(e -> acceptValue(textField.getText()));

        HBox inputContainer = new HBox(5);
        inputContainer.getChildren().addAll(textField, acceptButton);

        uriContainer.getChildren().addAll(new Label("Common prefixes:"), prefixPane, inputContainer);
        container.getChildren().add(uriContainer);
    }

    private void createStringWidget(AttributeInfo attributeInfo, String currentValue) {
        TextField textField = new TextField();
        textField.setPromptText("Enter value...");
        textField.setPrefWidth(250);

        if (currentValue != null) {
            textField.setText(currentValue);
        }

        // Add constraints info if available
        if (attributeInfo.minLength != null || attributeInfo.maxLength != null) {
            String lengthInfo = "Length: ";
            if (attributeInfo.minLength != null) {
                lengthInfo += "min " + attributeInfo.minLength;
            }
            if (attributeInfo.maxLength != null) {
                if (attributeInfo.minLength != null) lengthInfo += ", ";
                lengthInfo += "max " + attributeInfo.maxLength;
            }

            Label lengthLabel = new Label(lengthInfo);
            lengthLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #6c757d;");
            container.getChildren().add(lengthLabel);

            // Real-time validation
            textField.textProperty().addListener((obs, oldText, newText) -> {
                boolean valid = attributeInfo.minLength == null || newText.length() >= Integer.parseInt(attributeInfo.minLength);
                if (attributeInfo.maxLength != null && newText.length() > Integer.parseInt(attributeInfo.maxLength)) {
                    valid = false;
                }
                textField.setStyle("-fx-border-color: " + (valid ? "#28a745" : "#dc3545") + ";");
            });
        }

        Button acceptButton = new Button("Accept");
        acceptButton.setOnAction(e -> acceptValue(textField.getText()));

        HBox stringContainer = new HBox(5);
        stringContainer.getChildren().addAll(textField, acceptButton);
        container.getChildren().add(stringContainer);

        // Focus on text field
        Platform.runLater(() -> textField.requestFocus());
    }

    private String normalizeXsdType(String xsdType) {
        if (xsdType == null) return "string";

        // Remove namespace prefix if present
        int colonIndex = xsdType.lastIndexOf(':');
        if (colonIndex >= 0) {
            xsdType = xsdType.substring(colonIndex + 1);
        }

        return xsdType.toLowerCase();
    }

    private void acceptValue(String value) {
        if (onValueSelected != null) {
            onValueSelected.accept(value);
        }
        hide();
    }

    public void hide() {
        if (popup != null) {
            popup.hide();
        }
    }

    public boolean isShowing() {
        return popup != null && popup.isShowing();
    }

    public void setOnValueSelected(Consumer<String> handler) {
        this.onValueSelected = handler;
    }

    /**
     * Attribute information class for type-aware helpers
     */
    public static class AttributeInfo {
        public String name;
        public String type;
        public String documentation;
        public List<String> enumerationValues;
        public String pattern;
        public String minLength;
        public String maxLength;
        public String minInclusive;
        public String maxInclusive;
        public String defaultValue;

        public AttributeInfo(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }
}