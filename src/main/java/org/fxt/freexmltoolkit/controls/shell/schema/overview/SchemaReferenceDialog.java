package org.fxt.freexmltoolkit.controls.shell.schema.overview;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.AddSchemaReferenceCommand.Kind;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * Asks for the namespace and location of a new xs:import / xs:include / xs:redefine /
 * xs:override. Choosing a file fills in a location relative to the edited schema and, for an
 * import, the chosen schema's target namespace.
 * <p>
 * Tests install {@link #setResponder} so no modal dialog opens.
 *
 * @since 2.6
 */
public final class SchemaReferenceDialog extends Dialog<SchemaReferenceDialog.Answer> {

    /** The user's answer. */
    public record Answer(String namespace, String location) {
    }

    /** A chosen schema file: its location relative to the edited schema and its target namespace. */
    public record Choice(String location, String targetNamespace) {
    }

    private static Function<Kind, Optional<Answer>> responder;

    @FXML private Label namespaceLabel;
    @FXML private TextField namespaceField;
    @FXML private TextField locationField;
    @FXML private Label errorLabel;

    private final Kind kind;
    private final Path documentPath;

    private SchemaReferenceDialog(Kind kind, Path documentPath) {
        this.kind = kind;
        this.documentPath = documentPath;
        String name = "xs:" + kind.name().toLowerCase(Locale.ROOT);
        setTitle(I18n.t("SchemaReferenceDialog.add", name));
        setHeaderText(switch (kind) {
            case IMPORT -> "Import the components of a schema with another target namespace.";
            case INCLUDE -> "Include a schema with the same (or no) target namespace.";
            case REDEFINE -> "Include a schema and redefine some of its components (XSD 1.0).";
            case OVERRIDE -> "Include a schema and override some of its components (XSD 1.1).";
        });
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/SchemaReferenceDialog.fxml"));
        getDialogPane().getStylesheets().addAll(DialogHelper.getThemeStylesheets());
        boolean isImport = kind == Kind.IMPORT;
        namespaceLabel.setVisible(isImport);
        namespaceLabel.setManaged(isImport);
        namespaceField.setVisible(isImport);
        namespaceField.setManaged(isImport);
        locationField.textProperty().addListener((obs, o, n) -> validate());
        validate();
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            if (validate() != null) {
                e.consume();
            }
        });
        setOnShown(e -> Platform.runLater(() -> (isImport ? namespaceField : locationField).requestFocus()));
        setResultConverter(button -> button == ButtonType.OK
                ? new Answer(isImport ? namespaceField.getText() : null, locationField.getText().trim()) : null);
    }

    /** @param testResponder answers instead of the dialog ({@code null} = dialog) */
    public static void setResponder(Function<Kind, Optional<Answer>> testResponder) {
        responder = testResponder;
    }

    /**
     * Shows the dialog (or asks the test responder).
     *
     * @param documentPath the edited schema's file (for relative locations), may be null
     * @return namespace and location, empty when cancelled
     */
    public static Optional<Answer> ask(Window owner, Kind kind, Path documentPath) {
        if (responder != null) {
            return responder.apply(kind);
        }
        SchemaReferenceDialog dialog = new SchemaReferenceDialog(kind, documentPath);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        return dialog.showAndWait();
    }

    private String validate() {
        String location = locationField.getText() == null ? "" : locationField.getText().trim();
        String error = location.isEmpty() && kind != Kind.IMPORT ? "Enter the schema location." : null;
        errorLabel.setText(error == null ? "" : error);
        errorLabel.setVisible(error != null);
        errorLabel.setManaged(error != null);
        return error;
    }

    @FXML
    public void onBrowse() {
        chooseSchemaFile(getDialogPane().getScene().getWindow(), documentPath).ifPresent(choice -> {
            locationField.setText(choice.location());
            if (kind == Kind.IMPORT && choice.targetNamespace() != null) {
                namespaceField.setText(choice.targetNamespace());
            }
        });
    }

    /**
     * Lets the user pick a schema file.
     *
     * @param documentPath the edited schema's file; the location is made relative to its folder
     * @return the location and the file's target namespace, empty when cancelled
     */
    public static Optional<Choice> chooseSchemaFile(Window owner, Path documentPath) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("SchemaReferenceDialog.choose-schema"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML Schema (*.xsd)", "*.xsd"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        if (documentPath != null && documentPath.getParent() != null) {
            chooser.setInitialDirectory(documentPath.getParent().toFile());
        }
        File file = chooser.showOpenDialog(owner);
        if (file == null) {
            return Optional.empty();
        }
        return Optional.of(new Choice(relativeLocation(documentPath, file.toPath()), targetNamespace(file.toPath())));
    }

    /** @return {@code target} relative to the folder of {@code documentPath} (forward slashes), else its URI */
    static String relativeLocation(Path documentPath, Path target) {
        if (documentPath != null && documentPath.getParent() != null) {
            try {
                Path relative = documentPath.toAbsolutePath().getParent().relativize(target.toAbsolutePath());
                return relative.toString().replace(File.separatorChar, '/');
            } catch (IllegalArgumentException e) {
                // different roots (Windows drives): fall back to the absolute URI
            }
        }
        return target.toUri().toString();
    }

    /** @return the {@code targetNamespace} of a schema file's root element, or null */
    static String targetNamespace(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            XMLStreamReader reader = factory.createXMLStreamReader(in);
            try {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                        String ns = reader.getAttributeValue(null, "targetNamespace");
                        return ns == null || ns.isBlank() ? null : ns;
                    }
                }
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
