package org.fxt.freexmltoolkit.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Utility class for creating consistent, modern dialogs across the application.
 *
 * <p>Provides factory methods for:
 * <ul>
 *   <li>Standard Alerts (Information, Warning, Error, Confirmation) with icons</li>
 *   <li>Custom dialogs with styled headers</li>
 *   <li>Dialog content builders (sections, feature items, info boxes)</li>
 *   <li>Consistent CSS class application</li>
 * </ul>
 *
 * <p>All dialogs use the unified theme from dialog-theme.css for visual consistency.
 *
 * @since 2.0
 */
public class DialogHelper {

    private static final Logger logger = LoggerFactory.getLogger(DialogHelper.class);

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private DialogHelper() {
        // Utility class
    }

    /**
     * Enumeration of dialog header color themes.
     *
     * <p>Each theme provides a consistent color scheme for dialog headers,
     * including CSS style class and hex color code.
     */
    public enum HeaderTheme {
        /** Primary blue theme for main actions */
        PRIMARY("dialog-header-primary", "#007bff"),
        /** Success green theme for confirmations */
        SUCCESS("dialog-header-success", "#28a745"),
        /** Info cyan theme for informational dialogs */
        INFO("dialog-header-info", "#17a2b8"),
        /** Warning yellow theme for cautions */
        WARNING("dialog-header-warning", "#ffc107"),
        /** Danger red theme for errors and destructive actions */
        DANGER("dialog-header-danger", "#dc3545"),
        /** Purple theme for special dialogs */
        PURPLE("dialog-header-purple", "#9c27b0");

        private final String styleClass;
        private final String color;

        HeaderTheme(String styleClass, String color) {
            this.styleClass = styleClass;
            this.color = color;
        }

        /**
         * Gets the style class.
         *
         * @return The style class.
         */
        public String getStyleClass() { return styleClass; }

        /**
         * Gets the color.
         *
         * @return The color hex string.
         */
        public String getColor() { return color; }
    }

    /**
     * Enumeration of info box type variants.
     *
     * <p>Each type provides a visual style for information boxes including
     * CSS style class and icon literal.
     */
    public enum InfoBoxType {
        /** Info style with circle icon */
        INFO("dialog-info-box-info", "bi-info-circle"),
        /** Success style with check icon */
        SUCCESS("dialog-info-box-success", "bi-check-circle"),
        /** Warning style with exclamation icon */
        WARNING("dialog-info-box-warning", "bi-exclamation-triangle"),
        /** Danger style with X icon */
        DANGER("dialog-info-box-danger", "bi-x-circle");

        private final String styleClass;
        private final String iconLiteral;

        InfoBoxType(String styleClass, String iconLiteral) {
            this.styleClass = styleClass;
            this.iconLiteral = iconLiteral;
        }

        /**
         * Gets the style class.
         *
         * @return The style class.
         */
        public String getStyleClass() { return styleClass; }

        /**
         * Gets the icon literal.
         *
         * @return The icon literal.
         */
        public String getIconLiteral() { return iconLiteral; }
    }

    // ============================================
    // STANDARD ALERTS
    // ============================================

    /**
     * Shows an information alert with icon.
     *
     * @param title the alert title
     * @param header the alert header text
     * @param content the alert content text
     */
    public static void showInformation(String title, String header, String content) {
        showAlert(Alert.AlertType.INFORMATION, title, header, content, "bi-info-circle");
    }

    /**
     * Shows a warning alert with icon.
     *
     * @param title the alert title
     * @param header the alert header text
     * @param content the alert content text
     */
    public static void showWarning(String title, String header, String content) {
        showAlert(Alert.AlertType.WARNING, title, header, content, "bi-exclamation-triangle");
    }

    /**
     * Shows an error alert with icon.
     *
     * @param title the alert title
     * @param header the alert header text
     * @param content the alert content text
     */
    public static void showError(String title, String header, String content) {
        showAlert(Alert.AlertType.ERROR, title, header, content, "bi-x-circle");
    }

    /**
     * Shows a confirmation dialog with icon.
     *
     * @param title the dialog title
     * @param header the dialog header text
     * @param content the dialog content text
     * @return true if user clicked OK, false otherwise
     */
    public static boolean showConfirmation(String title, String header, String content) {
        Alert alert = createAlert(Alert.AlertType.CONFIRMATION, title, header, content, "bi-question-circle");
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /**
     * Shows a confirmation dialog with custom button labels.
     *
     * @param title the dialog title
     * @param header the dialog header text
     * @param content the dialog content text
     * @param yesText the text for the "yes" button
     * @param noText the text for the "no" button
     * @return true if user clicked yes, false otherwise
     */
    public static boolean showConfirmation(String title, String header, String content,
                                          String yesText, String noText) {
        Alert alert = createAlert(Alert.AlertType.CONFIRMATION, title, header, content, "bi-question-circle");

        ButtonType yesButton = new ButtonType(yesText, ButtonBar.ButtonData.YES);
        ButtonType noButton = new ButtonType(noText, ButtonBar.ButtonData.NO);
        alert.getButtonTypes().setAll(yesButton, noButton);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == yesButton;
    }

    /**
     * Shows an error alert for an exception.
     *
     * @param title the alert title
     * @param header the alert header
     * @param exception the exception to display
     */
    public static void showException(String title, String header, Exception exception) {
        Alert alert = createAlert(Alert.AlertType.ERROR, title, header,
                                  exception.getMessage(), "bi-bug");

        // Create expandable Exception content
        VBox expandableContent = new VBox(5);
        expandableContent.setPadding(new Insets(10));

        TextArea textArea = new TextArea(getStackTraceAsString(exception));
        textArea.setEditable(false);
        textArea.setWrapText(false);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);

        expandableContent.getChildren().add(textArea);
        alert.getDialogPane().setExpandableContent(expandableContent);

        alert.showAndWait();
    }

    // ============================================
    // CUSTOM DIALOGS
    // ============================================

    /**
     * Creates a custom dialog with styled header.
     *
     * @param title the window title
     * @param headerTitle the header title text
     * @param headerSubtitle the header subtitle text (can be null)
     * @param iconLiteral the Bootstrap icon literal (e.g., "bi-diagram-2")
     * @param theme the header color theme
     * @return the dialog
     */
    public static Dialog<ButtonType> createDialog(String title, String headerTitle,
                                                  String headerSubtitle, String iconLiteral,
                                                  HeaderTheme theme) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.initModality(Modality.APPLICATION_MODAL);

        DialogPane dialogPane = dialog.getDialogPane();

        // Create header
        VBox header = createDialogHeader(headerTitle, headerSubtitle, iconLiteral, theme);

        // Wrap in BorderPane
        BorderPane root = new BorderPane();
        root.setTop(header);

        // Content will be set by caller
        VBox contentContainer = new VBox(20);
        contentContainer.setPadding(new Insets(25));
        root.setCenter(contentContainer);

        dialogPane.setContent(root);
        dialogPane.getStylesheets().addAll(
            DialogHelper.class.getResource("/css/app-theme.css").toExternalForm(),
            DialogHelper.class.getResource("/css/dialog-theme.css").toExternalForm()
        );

        // Add standard buttons
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        return dialog;
    }

    /**
     * Creates a dialog header with icon and text.
     *
     * @param title the header title
     * @param subtitle the header subtitle (can be null)
     * @param iconLiteral the icon literal
     * @param theme the color theme
     * @return the header VBox
     */
    public static VBox createDialogHeader(String title, String subtitle,
                                         String iconLiteral, HeaderTheme theme) {
        VBox header = new VBox();
        header.getStyleClass().addAll("dialog-header", theme.getStyleClass());
        header.setPadding(new Insets(20));
        header.setSpacing(15);

        HBox headerContent = new HBox(15);
        headerContent.setAlignment(Pos.CENTER_LEFT);

        // Icon
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(48);
        icon.setIconColor(Color.WHITE);

        // Title and subtitle
        VBox textBox = new VBox(5);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dialog-header-title");
        textBox.getChildren().add(titleLabel);

        if (subtitle != null && !subtitle.isEmpty()) {
            Label subtitleLabel = new Label(subtitle);
            subtitleLabel.getStyleClass().add("dialog-header-subtitle");
            textBox.getChildren().add(subtitleLabel);
        }

        headerContent.getChildren().addAll(icon, textBox);
        header.getChildren().add(headerContent);

        return header;
    }

    // ============================================
    // CONTENT BUILDERS
    // ============================================

    /**
     * Creates a dialog section (card) with title.
     *
     * @param title the section title
     * @param subtitle the section subtitle (can be null)
     * @return the section VBox
     */
    public static VBox createSection(String title, String subtitle) {
        VBox section = new VBox(12);
        section.getStyleClass().add("dialog-section");
        section.setPadding(new Insets(20));

        if (title != null) {
            Label titleLabel = new Label(title);
            titleLabel.getStyleClass().add("dialog-section-title");
            section.getChildren().add(titleLabel);
        }

        if (subtitle != null) {
            Label subtitleLabel = new Label(subtitle);
            subtitleLabel.getStyleClass().add("dialog-section-subtitle");
            section.getChildren().add(subtitleLabel);
        }

        return section;
    }

    /**
     * Creates a feature item with icon, title, and description.
     *
     * @param iconLiteral the icon literal
     * @param iconColor the icon color (hex string)
     * @param title the feature title
     * @param description the feature description
     * @return the feature HBox
     */
    public static HBox createFeatureItem(String iconLiteral, String iconColor,
                                        String title, String description) {
        HBox feature = new HBox(15);
        feature.getStyleClass().add("dialog-feature-item");
        feature.setAlignment(Pos.TOP_LEFT);

        // Icon
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(24);
        icon.setIconColor(Color.web(iconColor));
        icon.getStyleClass().add("dialog-feature-icon");

        // Text
        VBox textBox = new VBox(5);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dialog-feature-title");

        Label descLabel = new Label(description);
        descLabel.getStyleClass().add("dialog-feature-description");
        descLabel.setWrapText(true);

        textBox.getChildren().addAll(titleLabel, descLabel);
        feature.getChildren().addAll(icon, textBox);

        return feature;
    }

    /**
     * Creates an info box (tip, note, warning, etc.).
     *
     * @param type the info box type
     * @param title the info box title
     * @param text the info box text
     * @return the info box VBox
     */
    public static VBox createInfoBox(InfoBoxType type, String title, String text) {
        VBox infoBox = new VBox(10);
        infoBox.getStyleClass().addAll("dialog-info-box", type.getStyleClass());
        infoBox.setPadding(new Insets(15));

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        FontIcon icon = new FontIcon(type.getIconLiteral());
        icon.setIconSize(20);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dialog-info-box-title");

        header.getChildren().addAll(icon, titleLabel);
        infoBox.getChildren().add(header);

        if (text != null && !text.isEmpty()) {
            Label textLabel = new Label(text);
            textLabel.getStyleClass().add("dialog-info-box-text");
            textLabel.setWrapText(true);
            infoBox.getChildren().add(textLabel);
        }

        return infoBox;
    }

    /**
     * Creates a group label for categorizing content.
     *
     * @param text the label text
     * @param styleClass the CSS style class (e.g., "dialog-group-label-primary")
     * @return the label
     */
    public static Label createGroupLabel(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().addAll("dialog-group-label", styleClass);
        return label;
    }

    /**
     * Creates a separator.
     *
     * @return the separator
     */
    public static Separator createSeparator() {
        Separator separator = new Separator();
        separator.getStyleClass().add("dialog-separator");
        return separator;
    }

    // ============================================
    // PRIVATE HELPERS
    // ============================================

    /**
     * Creates and shows a standard alert dialog.
     *
     * @param type        the alert type
     * @param title       the alert window title
     * @param header      the alert header text
     * @param content     the alert content text
     * @param iconLiteral the Bootstrap icon literal for the alert graphic
     */
    private static void showAlert(Alert.AlertType type, String title, String header,
                                 String content, String iconLiteral) {
        Alert alert = createAlert(type, title, header, content, iconLiteral);
        alert.showAndWait();
    }

    /**
     * Creates a standard alert with icon and themed styling.
     *
     * @param type        the alert type (INFORMATION, WARNING, ERROR, CONFIRMATION)
     * @param title       the alert window title
     * @param header      the alert header text
     * @param content     the alert content text
     * @param iconLiteral the Bootstrap icon literal for the alert graphic
     * @return the configured Alert instance
     */
    private static Alert createAlert(Alert.AlertType type, String title, String header,
                                    String content, String iconLiteral) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        // Set custom icon
        if (iconLiteral != null) {
            FontIcon icon = new FontIcon(iconLiteral);
            icon.setIconSize(32);

            // Set icon color based on alert type
            switch (type) {
                case INFORMATION -> icon.setIconColor(Color.web("#17a2b8"));
                case WARNING -> icon.setIconColor(Color.web("#ffc107"));
                case ERROR -> icon.setIconColor(Color.web("#dc3545"));
                case CONFIRMATION -> icon.setIconColor(Color.web("#007bff"));
            }

            alert.setGraphic(icon);
        }

        // Apply dialog theme stylesheet
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStylesheets().addAll(
            DialogHelper.class.getResource("/css/app-theme.css").toExternalForm(),
            DialogHelper.class.getResource("/css/dialog-theme.css").toExternalForm()
        );

        // Add type-specific style class
        String typeClass = switch (type) {
            case INFORMATION -> "information";
            case WARNING -> "warning";
            case ERROR -> "error";
            case CONFIRMATION -> "confirmation";
            default -> "";
        };
        if (!typeClass.isEmpty()) {
            dialogPane.getStyleClass().add(typeClass);
        }

        return alert;
    }

    // ============================================
    // HELP DIALOGS
    // ============================================

    /**
     * Creates a keyboard shortcut item for help dialogs.
     *
     * @param shortcut    the keyboard shortcut text (e.g., "Ctrl+Z")
     * @param description the shortcut description
     * @return the shortcut HBox
     */
    public static HBox createShortcutItem(String shortcut, String description) {
        HBox item = new HBox(15);
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("dialog-shortcut-item");

        // Shortcut key badge
        Label keyLabel = new Label(shortcut);
        keyLabel.getStyleClass().add("dialog-shortcut-key");
        keyLabel.setMinWidth(120);
        keyLabel.setStyle("""
                -fx-background-color: linear-gradient(to bottom, #f8f9fa, #e9ecef);
                -fx-border-color: #dee2e6;
                -fx-border-width: 1;
                -fx-border-radius: 6;
                -fx-background-radius: 6;
                -fx-padding: 6 12;
                -fx-font-family: 'SF Mono', 'Consolas', 'Monaco', monospace;
                -fx-font-size: 12px;
                -fx-font-weight: bold;
                -fx-text-fill: #495057;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 2, 0, 0, 1);
                """);

        // Description
        Label descLabel = new Label(description);
        descLabel.setStyle("-fx-text-fill: #495057; -fx-font-size: 14px;");

        item.getChildren().addAll(keyLabel, descLabel);
        return item;
    }

    /**
     * Creates a section title for help dialogs.
     *
     * @param iconLiteral the icon literal
     * @param iconColor   the icon color
     * @param title       the section title
     * @return the section title HBox
     */
    public static HBox createHelpSectionTitle(String iconLiteral, String iconColor, String title) {
        HBox titleBox = new HBox(10);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        titleBox.setPadding(new Insets(0, 0, 5, 0));

        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(20);
        icon.setIconColor(Color.web(iconColor));

        Label label = new Label(title);
        label.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + iconColor + ";");

        titleBox.getChildren().addAll(icon, label);
        return titleBox;
    }

    /**
     * Creates a styled help dialog with features and keyboard shortcuts.
     *
     * @param title          the window title
     * @param headerTitle    the header title
     * @param headerSubtitle the header subtitle
     * @param iconLiteral    the header icon
     * @param theme          the header theme
     * @param features       list of feature items (each String[3]: iconLiteral, title, description)
     * @param shortcuts      list of shortcut items (each String[2]: shortcut, description)
     * @return the configured dialog
     */
    public static Dialog<ButtonType> createHelpDialog(
            String title,
            String headerTitle,
            String headerSubtitle,
            String iconLiteral,
            HeaderTheme theme,
            java.util.List<String[]> features,
            java.util.List<String[]> shortcuts) {

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.initModality(Modality.APPLICATION_MODAL);

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStylesheets().addAll(
                DialogHelper.class.getResource("/css/app-theme.css").toExternalForm(),
                DialogHelper.class.getResource("/css/dialog-theme.css").toExternalForm()
        );

        // Create header
        VBox header = createDialogHeader(headerTitle, headerSubtitle, iconLiteral, theme);

        // Create content
        VBox content = new VBox(20);
        content.setPadding(new Insets(25));
        content.setStyle("-fx-background-color: #ffffff;");

        // Features section
        if (features != null && !features.isEmpty()) {
            VBox featuresSection = createSection("Features", null);
            featuresSection.setStyle("""
                    -fx-background-color: #f8f9fa;
                    -fx-border-color: #e9ecef;
                    -fx-border-width: 1;
                    -fx-border-radius: 10;
                    -fx-background-radius: 10;
                    -fx-padding: 20;
                    """);

            VBox featuresContent = new VBox(12);
            String[] colors = {"#007bff", "#28a745", "#17a2b8", "#fd7e14", "#6f42c1", "#e83e8c"};
            int colorIndex = 0;

            for (String[] feature : features) {
                if (feature.length >= 3) {
                    HBox featureItem = createFeatureItem(
                            feature[0],
                            colors[colorIndex % colors.length],
                            feature[1],
                            feature[2]
                    );
                    featuresContent.getChildren().add(featureItem);
                    colorIndex++;
                }
            }
            featuresSection.getChildren().add(featuresContent);
            content.getChildren().add(featuresSection);
        }

        // Keyboard shortcuts section
        if (shortcuts != null && !shortcuts.isEmpty()) {
            VBox shortcutsSection = new VBox(15);
            shortcutsSection.setStyle("""
                    -fx-background-color: #f8f9fa;
                    -fx-border-color: #e9ecef;
                    -fx-border-width: 1;
                    -fx-border-radius: 10;
                    -fx-background-radius: 10;
                    -fx-padding: 20;
                    """);

            // Section header
            HBox sectionHeader = createHelpSectionTitle("bi-keyboard", "#6f42c1", "Keyboard Shortcuts");
            shortcutsSection.getChildren().add(sectionHeader);

            // Shortcuts grid
            VBox shortcutsContent = new VBox(8);
            for (String[] shortcut : shortcuts) {
                if (shortcut.length >= 2) {
                    shortcutsContent.getChildren().add(
                            createShortcutItem(shortcut[0], shortcut[1])
                    );
                }
            }
            shortcutsSection.getChildren().add(shortcutsContent);
            content.getChildren().add(shortcutsSection);
        }

        // Wrap in ScrollPane
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-border-width: 0;");
        scrollPane.setPrefViewportHeight(400);

        // Main layout
        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(scrollPane);

        dialogPane.setContent(root);
        dialogPane.setPrefWidth(550);
        dialogPane.getButtonTypes().add(ButtonType.OK);

        // Style the OK button
        Button okButton = (Button) dialogPane.lookupButton(ButtonType.OK);
        if (okButton != null) {
            okButton.setStyle("""
                    -fx-background-color: linear-gradient(to bottom, #007bff, #0056b3);
                    -fx-text-fill: white;
                    -fx-font-weight: bold;
                    -fx-font-size: 14px;
                    -fx-padding: 8 24;
                    -fx-background-radius: 6;
                    -fx-cursor: hand;
                    """);
        }

        return dialog;
    }

    /**
     * Converts an exception's stack trace to a formatted string representation.
     *
     * <p>The output includes the exception class, message, full stack trace,
     * and any chained causes with their stack traces.
     *
     * @param exception the exception to convert
     * @return the formatted stack trace string
     */
    private static String getStackTraceAsString(Exception exception) {
        StringBuilder sb = new StringBuilder();
        sb.append(exception.getClass().getName()).append(": ")
          .append(exception.getMessage()).append("\n\n");

        for (StackTraceElement element : exception.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }

        Throwable cause = exception.getCause();
        if (cause != null) {
            sb.append("\nCaused by: ").append(cause.getClass().getName())
              .append(": ").append(cause.getMessage()).append("\n");
            for (StackTraceElement element : cause.getStackTrace()) {
                sb.append("\tat ").append(element.toString()).append("\n");
            }
        }

        return sb.toString();
    }
}
